package net.machiavelli.minecolonytax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.machiavelli.minecolonytax.TaxConfig;

/**
 * Tracks the first (primary) colony for each player and the creation order of
 * their colonies.
 * When a player creates multiple colonies, their first colony remains their
 * primary one where they are owner.
 * In subsequent colonies, they are set to officer rank.
 *
 * If the first colony is deleted, the next-oldest colony automatically becomes
 * the new primary colony.
 */
public class FirstColonyTracker {

    private static final Logger LOGGER = LogManager.getLogger(FirstColonyTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/warntax/firstColonyData.json";

    // Maps player UUID -> list of colony IDs in creation order (oldest first)
    private static final Map<UUID, List<Integer>> playerColoniesMap = new ConcurrentHashMap<>();

    /**
     * Adds a colony to a player's tracked colonies.
     * If this is their first colony, it becomes their primary colony.
     *
     * @param playerUUID The player's UUID
     * @param colonyID   The colony ID to add
     * @return true if this is the player's first colony, false otherwise
     */
    public static boolean addColony(UUID playerUUID, int colonyID) {
        if (playerUUID == null) {
            LOGGER.warn("Attempted to add colony with null player UUID");
            return false;
        }

        List<Integer> colonies = playerColoniesMap.computeIfAbsent(playerUUID, k -> new ArrayList<>());

        // Prevent duplicate entries
        if (colonies.contains(colonyID)) {
            LOGGER.debug("Colony {} already tracked for player {}", colonyID, playerUUID);
            return colonies.size() == 1 && colonies.get(0) == colonyID;
        }

        colonies.add(colonyID);
        boolean isFirst = colonies.size() == 1;

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Added colony {} for player {} ({})",
                    colonyID, playerUUID, isFirst ? "PRIMARY" : "SECONDARY #" + colonies.size());
        }

        saveData();
        return isFirst;
    }

    /**
     * Removes a colony from tracking.
     * If this was the first colony, the next-oldest colony becomes the new first.
     *
     * @param playerUUID The player's UUID
     * @param colonyID   The colony ID to remove
     * @return The new first colony ID if it changed, or null if no change
     */
    public static Integer removeColony(UUID playerUUID, int colonyID) {
        if (playerUUID == null) {
            return null;
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        if (colonies == null || colonies.isEmpty()) {
            return null;
        }

        boolean wasFirst = !colonies.isEmpty() && colonies.get(0) == colonyID;
        colonies.remove(Integer.valueOf(colonyID));

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Removed colony {} from player {} tracking", colonyID, playerUUID);
        }

        if (colonies.isEmpty()) {
            playerColoniesMap.remove(playerUUID);
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Player {} has no more tracked colonies", playerUUID);
            }
            saveData();
            return null;
        }

        saveData();

        // If the removed colony was the first, return the new first colony ID
        if (wasFirst && !colonies.isEmpty()) {
            Integer newFirstColony = colonies.get(0);
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Colony {} is now the PRIMARY colony for player {} (promoted after deletion)",
                        newFirstColony, playerUUID);
            }
            return newFirstColony;
        }

        return null;
    }

    /**
     * Gets the first (primary) colony ID for a player.
     *
     * @param playerUUID The player's UUID
     * @return The first colony ID, or null if the player has no colonies
     */
    public static Integer getFirstColony(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        if (colonies == null || colonies.isEmpty()) {
            return null;
        }

        return colonies.get(0);
    }

    /**
     * Checks if a colony is a player's first (primary) colony.
     *
     * @param playerUUID The player's UUID
     * @param colonyID   The colony ID to check
     * @return true if this is the player's first colony
     */
    public static boolean isFirstColony(UUID playerUUID, int colonyID) {
        Integer firstColony = getFirstColony(playerUUID);
        return firstColony != null && firstColony == colonyID;
    }

    /**
     * Gets all colonies for a player in creation order.
     *
     * @param playerUUID The player's UUID
     * @return Unmodifiable list of colony IDs in creation order, or empty list if
     *         none
     */
    public static List<Integer> getPlayerColonies(UUID playerUUID) {
        if (playerUUID == null) {
            return Collections.emptyList();
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        if (colonies == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(new ArrayList<>(colonies));
    }

    /**
     * Reverse lookup: finds which player UUID has the given colony as their first (primary) colony.
     * More reliable than calling getOwner() on a colony whose permissions may be corrupted.
     *
     * @param colonyId The colony ID to look up
     * @return The player UUID who registered this as their first colony, or null if not found
     */
    public static UUID getFirstColonyOwner(int colonyId) {
        for (Map.Entry<UUID, List<Integer>> entry : playerColoniesMap.entrySet()) {
            List<Integer> cols = entry.getValue();
            if (!cols.isEmpty() && cols.get(0).equals(colonyId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the number of colonies a player has.
     *
     * @param playerUUID The player's UUID
     * @return The number of colonies
     */
    public static int getColonyCount(UUID playerUUID) {
        if (playerUUID == null) {
            return 0;
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        return colonies == null ? 0 : colonies.size();
    }

    /**
     * Bootstraps tracking data from an already-running server's colonies.
     *
     * Called once after MineColonies finishes loading on first install (or when
     * firstColonyData.json is absent/incomplete). Uses colony ID as a proxy for
     * creation order: a lower colony ID was created earlier, so it becomes the
     * player's primary colony.
     *
     * Rules:
     * - Players with NO existing FCT data: all their colonies are added sorted by
     *   ID ascending, making the lowest-ID colony their primary.
     * - Players with PARTIAL FCT data: only missing colony IDs are appended at the
     *   end; the existing primary is not disturbed.
     * - Colonies whose MC owner entry has a placeholder name ([AUTO_OWNER],
     *   [abandoned], etc.) are skipped — those are system artifacts, not real owners.
     *
     * @param colonyManager The MineColonies colony manager
     * @return The number of colony-player records that were newly registered
     */
    public static int bootstrapFromExistingColonies(IColonyManager colonyManager) {
        // Group real colony IDs by their owner UUID
        Map<UUID, List<Integer>> ownerToColonyIds = new HashMap<>();

        for (IColony colony : colonyManager.getAllColonies()) {
            UUID ownerUUID = colony.getPermissions().getOwner();
            if (ownerUUID == null) continue;

            // Skip placeholder system-owner entries created by the abandonment system
            ColonyPlayer ownerEntry = colony.getPermissions().getPlayers().get(ownerUUID);
            if (ownerEntry != null) {
                String name = ownerEntry.getName();
                if (name != null && (name.contains("[abandoned]")
                        || name.contains("[AUTO_OWNER]")
                        || name.toLowerCase().contains("abandoned"))) {
                    LOGGER.debug("Bootstrap: skipping colony {} — owner entry is a placeholder ({})",
                            colony.getID(), name);
                    continue;
                }
            }

            ownerToColonyIds.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(colony.getID());
        }

        int seeded = 0;
        for (Map.Entry<UUID, List<Integer>> entry : ownerToColonyIds.entrySet()) {
            UUID ownerUUID = entry.getKey();
            List<Integer> allColonyIds = entry.getValue();

            // Sort by colony ID ascending — lower ID means created earlier
            allColonyIds.sort(Integer::compare);

            List<Integer> alreadyTracked = playerColoniesMap.getOrDefault(ownerUUID, Collections.emptyList());

            if (alreadyTracked.isEmpty()) {
                // Fresh player: register all their colonies in creation order
                List<Integer> newList = new ArrayList<>(allColonyIds);
                playerColoniesMap.put(ownerUUID, newList);
                seeded += newList.size();
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Bootstrap: registered {} colonies for player {} (primary={})",
                            newList.size(), ownerUUID, newList.get(0));
                }
            } else {
                // Player already has FCT data — only append truly missing colony IDs
                List<Integer> tracked = playerColoniesMap.get(ownerUUID);
                for (int colonyId : allColonyIds) {
                    if (!tracked.contains(colonyId)) {
                        tracked.add(colonyId);
                        seeded++;
                        if (TaxConfig.isNormalLogging()) {
                            LOGGER.info("Bootstrap: added missing colony {} to existing FCT entry for player {}",
                                    colonyId, ownerUUID);
                        }
                    }
                }
            }
        }

        if (seeded > 0) {
            saveData();
            LOGGER.info("FirstColonyTracker bootstrap complete: {} colony-player records seeded", seeded);
        } else {
            LOGGER.debug("FirstColonyTracker bootstrap: all colonies already tracked, no changes");
        }

        return seeded;
    }

    /**
     * Saves the tracking data to disk.
     */
    private static void saveData() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();

            // Atomic write: serialize to a temp file then move it into place, so a crash
            // mid-write cannot truncate firstColonyData.json. A truncated registry would
            // make getFirstColonyOwner() return null for every colony, which makes
            // ColonyTierGuard treat every protected home base as a transferable secondary.
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(playerColoniesMap, writer);
            }
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.debug("First colony data saved successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to save first colony data", e);
        }
    }

    /**
     * Loads the tracking data from disk.
     */
    public static void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            LOGGER.info("No first colony data file found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<UUID, List<Integer>>>() {
            }.getType();
            Map<UUID, List<Integer>> loadedData = GSON.fromJson(reader, type);

            if (loadedData != null) {
                playerColoniesMap.clear();
                playerColoniesMap.putAll(loadedData);
                LOGGER.info("Loaded first colony data for {} players", playerColoniesMap.size());
            }
        } catch (Exception e) {
            // Broadened from IOException: a corrupt/truncated file throws
            // JsonSyntaxException (a RuntimeException), which must not propagate past
            // load. Degrade to "start fresh" so bootstrap can re-seed the registry.
            LOGGER.error("Failed to load first colony data (starting fresh)", e);
        }
    }

    /**
     * Clears all tracking data (for testing/debugging).
     */
    public static void clearAll() {
        playerColoniesMap.clear();
        saveData();
        LOGGER.info("First colony tracker cleared");
    }
}
