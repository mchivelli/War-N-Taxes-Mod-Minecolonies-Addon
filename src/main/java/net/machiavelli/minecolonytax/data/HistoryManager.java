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
     * Append a compact one-liner to the colony's activity log.
     * In-memory only; flushed to disk on server stop alongside the rest of colony_history.json.
     */
    public static void log(int colonyId, String category, String summary) {
        getColonyHistory(colonyId).addLogEntry(category, summary);
    }

    /**
     * Like {@link #log} but appends {@code [bal: before→after]} to make every balance
     * transition explicit for debugging.
     */
    public static void logWithBalance(int colonyId, String category, String summary,
                                      int balBefore, int balAfter) {
        String full = summary + String.format(" [bal: %d→%d]", balBefore, balAfter);
        getColonyHistory(colonyId).addLogEntry(category, full);
    }

    /** Return an immutable snapshot of the activity log for a colony, newest-last. */
    public static List<ColonyLogEntry> getLog(int colonyId) {
        ColonyHistory h = colonyHistories.get(colonyId);
        return h == null ? new ArrayList<>() : h.getActivityLog();
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
    
    /**
     * Structured war entry recorded at the end of each war, from the perspective of the
     * colony that owns this history (VICTORY = this colony won, DEFEAT = this colony lost).
     */
    public static class WarEntry {
        private final long timestamp;
        private final String opponentColonyName;
        private final String outcome; // "VICTORY", "DEFEAT", "STALEMATE"
        private final long amountTransferred;

        public WarEntry(long timestamp, String opponentColonyName, String outcome, long amountTransferred) {
            this.timestamp = timestamp;
            this.opponentColonyName = opponentColonyName;
            this.outcome = outcome;
            this.amountTransferred = amountTransferred;
        }

        public long getTimestamp() { return timestamp; }
        public String getOpponentColonyName() { return opponentColonyName; }
        public String getOutcome() { return outcome; }
        public long getAmountTransferred() { return amountTransferred; }

        public String getFormattedTimestamp() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
            return formatter.format(Instant.ofEpochMilli(timestamp));
        }
    }

    // -----------------------------------------------------------------------
    // Colony Activity Log — compact chronological record for /wnt colonylog
    // -----------------------------------------------------------------------

    public static class ColonyLogEntry {
        private final long timestamp;
        private final String category;
        private final String summary;

        public ColonyLogEntry(String category, String summary) {
            this.timestamp = System.currentTimeMillis();
            this.category = category;
            this.summary = summary;
        }

        public long   getTimestamp() { return timestamp; }
        public String getCategory()  { return category; }
        public String getSummary()   { return summary; }

        public String getFormattedTimestamp() {
            return DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(timestamp));
        }

        public String format() {
            return "[" + getFormattedTimestamp() + "] [" + category + "] " + summary;
        }
    }

    public static class ColonyHistory {
        private static final int MAX_LOG_ENTRIES = 300;

        private final List<String> events = new ArrayList<>();
        private final List<String> raidEvents = new ArrayList<>(); // Legacy string events
        private final List<RaidEntry> structuredRaids = new ArrayList<>(); // New structured raid data
        private final List<WarEntry> structuredWars = new ArrayList<>(); // Structured war data
        private final List<ColonyLogEntry> activityLog = new ArrayList<>();

        public void addEvent(String event) {
            events.add(event);
            if (events.size() > 100) {
                events.remove(0);
            }
        }

        /** @deprecated Use addRaidEntry. Kept for backward compatibility. */
        @Deprecated
        public void addRaidEvent(String event) {
            raidEvents.add(event);
            if (raidEvents.size() > 100) {
                raidEvents.remove(0);
            }
        }
        
        public void addRaidEntry(UUID raiderUUID, String raiderName, int amountStolen, boolean successful,
                                  String failureReason) {
            addRaidEntry(raiderUUID, raiderName, amountStolen, successful, failureReason, -1, -1);
        }

        public void addRaidEntry(UUID raiderUUID, String raiderName, int amountStolen, boolean successful,
                                  String failureReason, int balBefore, int balAfter) {
            RaidEntry entry = new RaidEntry(
                System.currentTimeMillis(),
                raiderUUID.toString(),
                raiderName,
                amountStolen,
                successful,
                failureReason
            );
            structuredRaids.add(entry);

            addRaidEvent(entry.toChatMessage());
            if (structuredRaids.size() > 100) {
                structuredRaids.remove(0);
            }

            String balSuffix = (balBefore >= 0) ? String.format(" [bal: %d→%d]", balBefore, balAfter) : "";
            if (successful) {
                addLogEntry("RAID", String.format("Raided by %s: stole %d coins%s", raiderName, amountStolen, balSuffix));
            } else {
                String reason = (failureReason != null && !failureReason.isEmpty()) ? failureReason : "repelled";
                addLogEntry("RAID", String.format("Raid by %s repelled (%s)%s", raiderName, reason, balSuffix));
            }

            LOGGER.debug("Added raid entry for {} - successful: {}, amount: {}", raiderName, successful, amountStolen);
        }

        public List<String> getEvents() {
            return new ArrayList<>(events);
        }

        public List<String> getRaidEvents() {
            return new ArrayList<>(raidEvents);
        }
        
        public List<RaidEntry> getStructuredRaids() {
            return new ArrayList<>(structuredRaids);
        }

        /**
         * Records a war outcome from this colony's perspective.
         * Also adds a legacy string entry for backward compat with WarHistoryCommand.
         *
         * @param opponentColonyName name of the opposing colony
         * @param outcome "VICTORY", "DEFEAT", or "STALEMATE"
         * @param amountTransferred coins transferred as result of the war
         */
        public void addWarEntry(String opponentColonyName, String outcome, long amountTransferred) {
            addWarEntry(opponentColonyName, outcome, amountTransferred, -1, -1);
        }

        public void addWarEntry(String opponentColonyName, String outcome, long amountTransferred,
                                int balBefore, int balAfter) {
            WarEntry entry = new WarEntry(System.currentTimeMillis(), opponentColonyName, outcome, amountTransferred);
            structuredWars.add(entry);
            if (structuredWars.size() > 50) {
                structuredWars.remove(0);
            }
            String legacyStr = String.format("[WAR] vs '%s'. Outcome: %s. Transferred: %d",
                    opponentColonyName, outcome, amountTransferred);
            addEvent(legacyStr);
            String sign = amountTransferred > 0 ? "+" : "";
            String balSuffix = (balBefore >= 0) ? String.format(" [bal: %d→%d]", balBefore, balAfter) : "";
            addLogEntry("WAR", String.format("%s vs %s | %s%d coins%s",
                    outcome, opponentColonyName, sign, amountTransferred, balSuffix));
        }

        public List<WarEntry> getStructuredWars() {
            return new ArrayList<>(structuredWars);
        }
        
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
        
        public int getTotalAmountStolen() {
            return structuredRaids.stream()
                .filter(RaidEntry::isSuccessful)
                .mapToInt(RaidEntry::getAmountStolen)
                .sum();
        }
        
        public int getSuccessfulRaidCount() {
            return (int) structuredRaids.stream()
                .filter(RaidEntry::isSuccessful)
                .count();
        }
        
        public int getFailedRaidCount() {
            return (int) structuredRaids.stream()
                .filter(entry -> !entry.isSuccessful())
                .count();
        }

        // --- Activity log (unified chronological) ---

        public void addLogEntry(String category, String summary) {
            activityLog.add(new ColonyLogEntry(category, summary));
            if (activityLog.size() > MAX_LOG_ENTRIES) {
                activityLog.remove(0);
            }
        }

        public List<ColonyLogEntry> getActivityLog() {
            return new ArrayList<>(activityLog);
        }
    }
}
