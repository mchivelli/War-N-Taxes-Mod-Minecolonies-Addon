package net.machiavelli.minecolonytax.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager
{
    private static final File HISTORY_FILE = new File("config/colony_history.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static List<Record> records;

    static
    {
        load();
    }

    private static void load()
    {
        // If file doesn’t exist or is empty, start with an empty list:
        if (!HISTORY_FILE.exists() || HISTORY_FILE.length() == 0L)
        {
            records = new ArrayList<>();
            return;
        }

        try (Reader reader = new FileReader(HISTORY_FILE))
        {
            Type listType = new TypeToken<List<Record>>(){}.getType();
            records = GSON.fromJson(reader, listType);
            if (records == null)
                records = new ArrayList<>();
        }
        catch (Exception e) // catches JsonSyntaxException, EOFException, IOException, etc.
        {
            // If anything goes wrong, log it and start fresh
            System.err.println("Failed to load history; starting with empty list.");
            e.printStackTrace();
            records = new ArrayList<>();
        }
    }

    public static synchronized void addRecord(Record r)
    {
        records.add(r);
        save();
    }

    private static void save()
    {
        try (Writer writer = new FileWriter(HISTORY_FILE))
        {
            GSON.toJson(records, writer);
        }
        catch (IOException e)
        {
            System.err.println("Failed to save history:");
            e.printStackTrace();
        }
    }

    public static synchronized List<Record> getRecordsForColony(int colonyId)
    {
        var out = new ArrayList<Record>();
        for (var r : records)
            if (r.colonyId == colonyId)
                out.add(r);
        return out;
    }



    public static class Record {
        public enum Type { WAR, RAID }
        public Type   type;
        public int    colonyId;
        public String colonyName;

        public String actorUuid;

        public String actorName;
        public long   timestamp;
        public long   amountTransferred;
        public String outcome;
    }
}
