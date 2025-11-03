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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class HistoryManager
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final File HISTORY_FILE = new File("config/warntax/colony_history.json");
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

    /**
     * Structured raid entry with full details
     */
    public static class RaidEntry {
        private final long timestamp;
        private final String raiderUUID;
        private final String raiderName;
        private final int amountStolen;
        private final boolean successful;
        private final String failureReason; // Only set if successful = false
        
        public RaidEntry(long timestamp, String raiderUUID, String raiderName, int amountStolen, boolean successful, String failureReason) {
            this.timestamp = timestamp;
            this.raiderUUID = raiderUUID;
            this.raiderName = raiderName;
            this.amountStolen = amountStolen;
            this.successful = successful;
            this.failureReason = failureReason;
        }
        
        public long getTimestamp() { return timestamp; }
        public String getRaiderUUID() { return raiderUUID; }
        public String getRaiderName() { return raiderName; }
        public int getAmountStolen() { return amountStolen; }
        public boolean isSuccessful() { return successful; }
        public String getFailureReason() { return failureReason; }
        
        /**
         * Format timestamp as readable date/time
         */
        public String getFormattedTimestamp() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
            return formatter.format(Instant.ofEpochMilli(timestamp));
        }
        
        /**
         * Get colored chat message for this raid entry
         */
        public String toChatMessage() {
            if (successful) {
                return String.format("§c[Raid] §7[%s] §f%s §7- §a%d §7stolen by §f%s",
                    getFormattedTimestamp(),
                    "SUCCESS",
                    amountStolen,
                    raiderName);
            } else {
                return String.format("§e[Raid] §7[%s] §f%s §7- §c%s §7by §f%s",
                    getFormattedTimestamp(),
                    "FAILED",
                    failureReason != null ? failureReason : "Unknown reason",
                    raiderName);
            }
        }
    }
    
    public static class ColonyHistory {
        private final List<String> events = new ArrayList<>();
        private final List<String> raidEvents = new ArrayList<>(); // Legacy string events
        private final List<RaidEntry> structuredRaids = new ArrayList<>(); // New structured raid data

        public void addEvent(String event) {
            events.add(event);
            // Optional: Limit history size
            if (events.size() > 100) {
                events.remove(0);
            }
        }

        /**
         * Add legacy string-based raid event (deprecated, kept for backward compatibility)
         */
        public void addRaidEvent(String event) {
            raidEvents.add(event);
            // Optional: Limit history size
            if (raidEvents.size() > 100) {
                raidEvents.remove(0);
            }
        }
        
        /**
         * Add structured raid entry with full details
         */
        public void addRaidEntry(UUID raiderUUID, String raiderName, int amountStolen, boolean successful, String failureReason) {
            RaidEntry entry = new RaidEntry(
                System.currentTimeMillis(),
                raiderUUID.toString(),
                raiderName,
                amountStolen,
                successful,
                failureReason
            );
            structuredRaids.add(entry);
            
            // Also add to legacy format for backward compatibility
            addRaidEvent(entry.toChatMessage());
            
            // Limit history size
            if (structuredRaids.size() > 100) {
                structuredRaids.remove(0);
            }
            
            LOGGER.debug("Added raid entry for {} - successful: {}, amount: {}", raiderName, successful, amountStolen);
        }

        public List<String> getEvents() {
            return new ArrayList<>(events);
        }

        public List<String> getRaidEvents() {
            return new ArrayList<>(raidEvents);
        }
        
        /**
         * Get all structured raid entries
         */
        public List<RaidEntry> getStructuredRaids() {
            return new ArrayList<>(structuredRaids);
        }
        
        /**
         * Get raids filtered by raider UUID
         */
        public List<RaidEntry> getRaidsByPlayer(UUID playerUUID) {
            List<RaidEntry> filtered = new ArrayList<>();
            String uuidStr = playerUUID.toString();
            for (RaidEntry entry : structuredRaids) {
                if (entry.getRaiderUUID().equals(uuidStr)) {
                    filtered.add(entry);
                }
            }
            return filtered;
        }
        
        /**
         * Get total amount stolen from this colony across all raids
         */
        public int getTotalAmountStolen() {
            return structuredRaids.stream()
                .filter(RaidEntry::isSuccessful)
                .mapToInt(RaidEntry::getAmountStolen)
                .sum();
        }
        
        /**
         * Get number of successful raids
         */
        public int getSuccessfulRaidCount() {
            return (int) structuredRaids.stream()
                .filter(RaidEntry::isSuccessful)
                .count();
        }
        
        /**
         * Get number of failed raids
         */
        public int getFailedRaidCount() {
            return (int) structuredRaids.stream()
                .filter(entry -> !entry.isSuccessful())
                .count();
        }
    }
}
