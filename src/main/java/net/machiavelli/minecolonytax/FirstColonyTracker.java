package net.machiavelli.minecolonytax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the first (primary) colony for each player and the creation order of their colonies.
 * When a player creates multiple colonies, their first colony remains their primary one where they are owner.
 * In subsequent colonies, they are set to officer rank.
 *
 * If the first colony is deleted, the next-oldest colony automatically becomes the new primary colony.
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
     * @param colonyID The colony ID to add
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

        LOGGER.info("Added colony {} for player {} ({})",
            colonyID, playerUUID, isFirst ? "PRIMARY" : "SECONDARY #" + colonies.size());

        saveData();
        return isFirst;
    }

    /**
     * Removes a colony from tracking.
     * If this was the first colony, the next-oldest colony becomes the new first.
     *
     * @param playerUUID The player's UUID
     * @param colonyID The colony ID to remove
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

        LOGGER.info("Removed colony {} from player {} tracking", colonyID, playerUUID);

        if (colonies.isEmpty()) {
            playerColoniesMap.remove(playerUUID);
            LOGGER.info("Player {} has no more tracked colonies", playerUUID);
            saveData();
            return null;
        }

        saveData();

        // If the removed colony was the first, return the new first colony ID
        if (wasFirst && !colonies.isEmpty()) {
            Integer newFirstColony = colonies.get(0);
            LOGGER.info("Colony {} is now the PRIMARY colony for player {} (promoted after deletion)",
                newFirstColony, playerUUID);
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
     * @param colonyID The colony ID to check
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
     * @return Unmodifiable list of colony IDs in creation order, or empty list if none
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
     * Saves the tracking data to disk.
     */
    private static void saveData() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(playerColoniesMap, writer);
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
            Type type = new TypeToken<ConcurrentHashMap<UUID, List<Integer>>>(){}.getType();
            Map<UUID, List<Integer>> loadedData = GSON.fromJson(reader, type);

            if (loadedData != null) {
                playerColoniesMap.clear();
                playerColoniesMap.putAll(loadedData);
                LOGGER.info("Loaded first colony data for {} players", playerColoniesMap.size());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load first colony data", e);
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
