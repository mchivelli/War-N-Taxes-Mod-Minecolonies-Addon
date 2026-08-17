package net.machiavelli.minecolonytax.occupation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Colony Occupation system.
 *
 * When an attacker wins a war and colony transfer is enabled, the defeated colony
 * enters an "OCCUPIED" state instead of immediately transferring ownership.
 *
 * During occupation:
 * - The occupier can collect taxes from the occupied colony
 * - The occupier CANNOT interact with colony buildings, items, or citizens
 * - The original owner retains full colony interaction rights
 * - The original owner/officers have X real-time days to wage a reclamation war
 * - If no reclamation attempt is made within the deadline, full ownership transfers
 */
public class OccupationManager {

    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/occupations.json";

    /** key = occupied colony ID */
    private static final Map<Integer, OccupationData> ACTIVE_OCCUPATIONS = new ConcurrentHashMap<>();

    private static MinecraftServer serverInstance;

    // ======================== Data Classes ========================

    /**
     * Represents an active occupation of a colony.
     */
    public static class OccupationData {
        public final int colonyId;
        public final String occupierUUID;
        public final String originalOwnerUUID;
        public final int occupierColonyId;
        public final long startTime;
        public final long expirationTime;
        public final String colonyName;
        public boolean reclamationAttempted;
        public long lastTaxCollectionTime;

        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
                              int occupierColonyId, String colonyName,
                              long startTime, long expirationTime) {
            this.colonyId = colonyId;
            this.occupierUUID = occupierUUID.toString();
            this.originalOwnerUUID = originalOwnerUUID.toString();
            this.occupierColonyId = occupierColonyId;
            this.colonyName = colonyName;
            this.startTime = startTime;
            this.expirationTime = expirationTime;
            this.reclamationAttempted = false;
            this.lastTaxCollectionTime = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public long getRemainingTimeMs() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }

        public int getRemainingDays() {
            long remainingMs = getRemainingTimeMs();
            return (int) (remainingMs / (24L * 60L * 60L * 1000L));
        }

        public int getRemainingHours() {
            long remainingMs = getRemainingTimeMs();
            return (int) (remainingMs / (60L * 60L * 1000L));
        }

        public UUID getOccupierUUID() {
            return UUID.fromString(occupierUUID);
        }

        public UUID getOriginalOwnerUUID() {
            return UUID.fromString(originalOwnerUUID);
        }
    }

    /**
     * JSON wrapper for persistence.
     */
    private static class OccupationSaveData {
        public List<OccupationData> occupations = new ArrayList<>();
    }

    // ======================== Lifecycle ========================

    public static void initialize(MinecraftServer server) {
        serverInstance = server;
        loadData();
        LOGGER.info("OccupationManager initialized with {} active occupations", ACTIVE_OCCUPATIONS.size());
    }

    public static void shutdown() {
        saveData();
        LOGGER.info("OccupationManager shutdown complete");
    }

    // ======================== Core Operations ========================

    /**
     * Start an occupation of a colony after a war victory.
     * The occupier can collect taxes but cannot interact with colony items.
     * After the occupation period expires, full ownership transfers automatically.
     *
     * @param colony          The defeated colony to occupy
     * @param occupierUUID    The UUID of the war victor
     * @param attackerColony  The attacker's colony (may be null)
     */
    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
        if (colony == null || occupierUUID == null) {
            LOGGER.warn("startOccupation called with null colony or occupier");
            return;
        }

        int colonyId = colony.getID();

        // Don't double-occupy
        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
            return;
        }

        UUID originalOwner = colony.getPermissions().getOwner();
        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;

        int durationDays = TaxConfig.getOccupationDurationDays();
        long now = System.currentTimeMillis();
        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);

        OccupationData data = new OccupationData(
                colonyId, occupierUUID, originalOwner,
                occupierColonyId, colony.getName(),
                now, expirationTime
        );
        ACTIVE_OCCUPATIONS.put(colonyId, data);
        saveData();

        LOGGER.info("Colony {} is now OCCUPIED by {} for {} days",
                colony.getName(), occupierUUID, durationDays);

        // Notify both sides
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Notify occupier
            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
            if (occupier != null) {
                Component occupierMsg = Component.literal("COLONY OCCUPIED")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("You now occupy " + colony.getName() + "!")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You can collect taxes with /wnt collectoccupation " + colonyId)
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You cannot interact with colony buildings or items.")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n"))
                        .append(Component.literal("If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!")
                                .withStyle(ChatFormatting.AQUA));
                occupier.sendSystemMessage(occupierMsg);
            }

            // Notify original owner
            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
            if (owner != null) {
                Component ownerMsg = Component.literal("COLONY OCCUPIED")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("Your colony " + colony.getName() + " has been occupied!")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n"))
                        .append(Component.literal("The occupier will collect taxes from your colony.")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You have " + durationDays + " days to wage a reclamation war with /wnt wagewar " + colonyId)
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\n"))
                        .append(Component.literal("If you do not reclaim, ownership will permanently transfer!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                owner.sendSystemMessage(ownerMsg);
            }
        }
    }

    /**
     * Check if a colony is currently occupied.
     */
    public static boolean isOccupied(int colonyId) {
        return ACTIVE_OCCUPATIONS.containsKey(colonyId);
    }

    /**
     * Get the occupation data for a colony, or null if not occupied.
     */
    public static OccupationData getOccupation(int colonyId) {
        return ACTIVE_OCCUPATIONS.get(colonyId);
    }

    /**
     * Get all active occupations.
     */
    public static Map<Integer, OccupationData> getActiveOccupations() {
        return Collections.unmodifiableMap(ACTIVE_OCCUPATIONS);
    }

    /**
     * Check if a player is the occupier of a specific colony.
     */
    public static boolean isOccupier(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        return data != null && data.occupierUUID.equals(playerUUID.toString());
    }

    /**
     * Get all colonies that a player is occupying.
     */
    public static List<OccupationData> getOccupiedByPlayer(UUID playerUUID) {
        List<OccupationData> result = new ArrayList<>();
        String uuid = playerUUID.toString();
        for (OccupationData data : ACTIVE_OCCUPATIONS.values()) {
            if (data.occupierUUID.equals(uuid)) {
                result.add(data);
            }
        }
        return result;
    }

    // ======================== Tax Collection ========================

    /**
     * Collect occupation taxes from an occupied colony.
     * The occupier receives a percentage of the colony's stored tax.
     *
     * @param colonyId The occupied colony ID
     * @param occupier The occupier player collecting taxes
     * @return The amount collected, or 0 if collection failed
     */
    public static int collectOccupationTax(int colonyId, ServerPlayer occupier) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) {
            occupier.sendSystemMessage(Component.literal("This colony is not occupied by you.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!data.occupierUUID.equals(occupier.getUUID().toString())) {
            occupier.sendSystemMessage(Component.literal("You are not the occupier of this colony.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Find the colony
        IColony colony = findColonyById(colonyId);
        if (colony == null) {
            occupier.sendSystemMessage(Component.literal("Colony not found.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int storedTax = TaxManager.getStoredTaxForColony(colony);
        if (storedTax <= 0) {
            occupier.sendSystemMessage(Component.literal("No tax available to collect from " + colony.getName() + ".")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
        int taxToCollect = (int) (storedTax * occupationTaxRate);
        if (taxToCollect <= 0) {
            occupier.sendSystemMessage(Component.literal("Tax amount too small to collect.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        // Deduct from the occupied colony and give to occupier's colony (or directly)
        // MONEY CONSERVATION: resolve the RECIPIENT before debiting the occupied colony.
        // This deducted first and credited only "if the occupier has a colony", so an occupier
        // whose colony was deleted, abandoned or never recorded (occupierColonyId is -1 when the
        // war had no attacker colony) silently destroyed the tax — taken from the victim,
        // credited to nobody, while the player was still told "Collected X occupation tax".
        IColony occupierColony = data.occupierColonyId > 0 ? findColonyById(data.occupierColonyId) : null;
        if (occupierColony == null) {
            occupier.sendSystemMessage(Component.literal(
                    "You have no colony to receive the occupation tax, so nothing was collected.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        TaxManager.adjustTax(colony, -taxToCollect);
        TaxManager.incrementTaxRevenue(occupierColony, taxToCollect);

        data.lastTaxCollectionTime = System.currentTimeMillis();
        saveData();

        occupier.sendSystemMessage(Component.literal("Collected " + taxToCollect + " occupation tax from " + colony.getName())
                .withStyle(ChatFormatting.GOLD));

        LOGGER.info("Occupier {} collected {} occupation tax from colony {}",
                occupier.getName().getString(), taxToCollect, colony.getName());

        return taxToCollect;
    }

    /**
     * Called during tax cycles to automatically divert a portion of occupied colony taxes
     * to the occupier's colony.
     */
    public static int processAutomaticOccupationTax(int colonyId, int generatedTax) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null || generatedTax <= 0) {
            return 0;
        }

        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
        int diverted = (int) (generatedTax * occupationTaxRate);
        if (diverted <= 0) return 0;

        // The caller debits the occupied colony by exactly our return value, so returning a
        // non-zero amount without crediting anyone DESTROYS it. Resolve the recipient first and
        // return 0 (no debit at all) when there is none.
        IColony occupierColony = data.occupierColonyId > 0 ? findColonyById(data.occupierColonyId) : null;
        if (occupierColony == null) {
            return 0;
        }

        TaxManager.incrementTaxRevenue(occupierColony, diverted);
        if (TaxConfig.isDebugLogging()) {
            LOGGER.info("Auto-diverted {} occupation tax from colony {} to occupier colony {}",
                    diverted, colonyId, data.occupierColonyId);
        }

        return diverted;
    }

    // ======================== Reclamation ========================

    /**
     * Mark that a reclamation attempt has been made against an occupied colony.
     * Called when the original owner (or their colony's officers) declare war on the occupied colony.
     */
    public static void markReclamationAttempted(int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data != null) {
            data.reclamationAttempted = true;
            saveData();
            LOGGER.info("Reclamation attempt recorded for occupied colony {}", colonyId);
        }
    }

    /**
     * End an occupation, either because the original owner reclaimed it via war
     * or because the occupation was revoked by an admin.
     */
    public static void endOccupation(int colonyId, String reason) {
        OccupationData data = ACTIVE_OCCUPATIONS.remove(colonyId);
        if (data == null) return;

        saveData();
        LOGGER.info("Occupation ended for colony {} ({}): {}", data.colonyName, colonyId, reason);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Notify occupier
            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
            if (occupier != null) {
                occupier.sendSystemMessage(Component.literal("Your occupation of " + data.colonyName + " has ended: " + reason)
                        .withStyle(ChatFormatting.RED));
            }

            // Notify original owner
            ServerPlayer owner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("The occupation of " + data.colonyName + " has ended: " + reason)
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    // ======================== Expiration Check ========================

    /**
     * Periodic check for expired occupations.
     * Called from server tick or scheduled task.
     * If an occupation expires without reclamation, full ownership transfers to the occupier.
     */
    public static void checkExpiredOccupations() {
        if (ACTIVE_OCCUPATIONS.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<Integer> toTransfer = new ArrayList<>();

        for (Map.Entry<Integer, OccupationData> entry : ACTIVE_OCCUPATIONS.entrySet()) {
            OccupationData data = entry.getValue();
            if (data.isExpired()) {
                // Transfer in both cases:
                // - reclamationAttempted=false: deadline passed with no attempt
                // - reclamationAttempted=true:  owner tried to reclaim but failed (lost the war),
                //   occupation expired while still in effect -> occupier wins
                toTransfer.add(entry.getKey());
            }
        }

        for (int colonyId : toTransfer) {
            OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
            if (data == null) continue;

            IColony colony = findColonyById(colonyId);
            if (colony == null) {
                LOGGER.warn("Occupied colony {} no longer exists, removing occupation", colonyId);
                ACTIVE_OCCUPATIONS.remove(colonyId);
                continue;
            }

            // Transfer full ownership to the occupier
            UUID occupierUUID = data.getOccupierUUID();
            LOGGER.info("Occupation expired for colony {} - transferring full ownership to {}",
                    colony.getName(), occupierUUID);

            boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);

            if (transferred) {
                // Broadcast the permanent claim
                Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(broadcastMsg);
                }

                // Notify original owner specifically
                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
                if (originalOwner != null) {
                    originalOwner.sendSystemMessage(
                            Component.literal("You failed to reclaim " + colony.getName() + " within the deadline. Ownership has been permanently transferred!")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            } else {
                // Primary-colony protection (or colony transfer disabled): the deed cannot move.
                // Keep the occupier in control via vassalization rather than a permanent seizure.
                LOGGER.info("Occupation of colony {} could not transfer the deed (primary protected) — vassalizing the occupier in instead.",
                        colony.getName());
                try {
                    int tributePercent = net.machiavelli.minecolonytax.TaxConfig.getWarVassalizationTributePercentage();
                    int durationHours = net.machiavelli.minecolonytax.TaxConfig.getWarVassalizationDurationHours();
                    net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                            colony, occupierUUID, tributePercent, durationHours);
                } catch (Throwable t) {
                    LOGGER.warn("Vassalize fallback on occupation of {} failed: {}", colony.getName(), t.toString());
                }
                Component broadcastMsg = Component.literal(
                        colony.getName() + " remains a protected Primary colony — it has been vassalized to its occupier rather than seized.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(broadcastMsg);
                }
            }

            ACTIVE_OCCUPATIONS.remove(colonyId);
        }

        if (!toTransfer.isEmpty()) {
            saveData();
        }
    }

    // ======================== Interaction Blocking ========================

    /**
     * Check if a player should be blocked from interacting with a colony's items/buildings.
     * The occupier can see the colony but CANNOT interact with anything.
     *
     * @param playerUUID The player trying to interact
     * @param colonyId   The colony being interacted with
     * @return true if the interaction should be BLOCKED
     */
    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;

        // Block the occupier from interacting with the occupied colony's items
        return data.occupierUUID.equals(playerUUID.toString());
    }

    /**
     * Check if a player is the original owner of an occupied colony.
     * Used to ensure original owners retain their interaction rights.
     */
    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;
        return data.originalOwnerUUID.equals(playerUUID.toString());
    }

    // ======================== Persistence ========================

    public static void saveData() {
        try {
            Path dir = Paths.get("config/warntax");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            OccupationSaveData saveData = new OccupationSaveData();
            saveData.occupations = new ArrayList<>(ACTIVE_OCCUPATIONS.values());

            try (Writer writer = new FileWriter(STORAGE_FILE)) {
                GSON.toJson(saveData, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save occupation data: {}", e.getMessage());
        }
    }

    public static void loadData() {
        try {
            Path path = Paths.get(STORAGE_FILE);
            if (!Files.exists(path)) return;

            try (Reader reader = new FileReader(STORAGE_FILE)) {
                OccupationSaveData saveData = GSON.fromJson(reader, OccupationSaveData.class);
                if (saveData != null && saveData.occupations != null) {
                    ACTIVE_OCCUPATIONS.clear();
                    for (OccupationData data : saveData.occupations) {
                        ACTIVE_OCCUPATIONS.put(data.colonyId, data);
                    }
                    LOGGER.info("Loaded {} occupations from disk", ACTIVE_OCCUPATIONS.size());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load occupation data: {}", e.getMessage());
        }
    }

    // ======================== Utility ========================

    private static IColony findColonyById(int colonyId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;

        for (Level level : server.getAllLevels()) {
            for (IColony c : IColonyManager.getInstance().getColonies(level)) {
                if (c.getID() == colonyId) return c;
            }
        }
        return null;
    }
}
