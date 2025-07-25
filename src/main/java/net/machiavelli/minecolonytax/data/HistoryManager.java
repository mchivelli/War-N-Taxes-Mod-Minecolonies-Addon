package net.machiavelli.minecolonytax.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class HistoryManager
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final File HISTORY_FILE = new File("config/warntaxmod/colony_history.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Map<Integer, ColonyHistory> colonyHistories = new ConcurrentHashMap<>();

    public static void saveHistory() {
        try {
            File parentDir = HISTORY_FILE.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                GSON.toJson(colonyHistories, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save colony history", e);
        }
    }

    public static void loadHistory() {
        if (!HISTORY_FILE.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(HISTORY_FILE)) {
            Type type = new TypeToken<ConcurrentHashMap<Integer, ColonyHistory>>(){}.getType();
            Map<Integer, ColonyHistory> loadedHistories = GSON.fromJson(reader, type);
            if (loadedHistories != null) {
                colonyHistories.putAll(loadedHistories);
            }
        } catch (IOException e) {
            LOGGER.error("Could not load colony history", e);
        }
    }

    public static ColonyHistory getColonyHistory(int colonyId) {
        return colonyHistories.computeIfAbsent(colonyId, id -> new ColonyHistory());
    }

    public static class ColonyHistory {
        private final List<String> events = new ArrayList<>();

        public void addEvent(String event) {
            events.add(event);
            // Optional: Limit history size
            if (events.size() > 100) {
                events.remove(0);
            }
        }

        public List<String> getEvents() {
            return new ArrayList<>(events);
        }
    }
}
