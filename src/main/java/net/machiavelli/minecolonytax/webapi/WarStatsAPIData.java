package net.machiavelli.minecolonytax.webapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.capability.PlayerWarDataCapability;
import net.machiavelli.minecolonytax.data.PlayerWarData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data collection and JSON serialization for the Web API.
 * This class runs SERVER-SIDE ONLY and gathers war statistics from player data.
 */
public class WarStatsAPIData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftServer server;
    private final PlayerDataCache cache;

    public WarStatsAPIData(MinecraftServer server, PlayerDataCache cache) {
        this.server = server;
        this.cache = cache;
    }

    /**
     * Get all player war statistics as JSON
     */
    public String getAllPlayersStatsJSON(boolean includeOffline) {
        JsonObject response = new JsonObject();
        response.addProperty("generated", Instant.now().toString());
        response.addProperty("server", server.getServerModName());
        response.addProperty("includeOffline", includeOffline);
        
        JsonArray players = new JsonArray();
        Set<String> onlineUUIDs = new HashSet<>();

        // Get stats for online players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JsonObject playerData = getPlayerStatsObject(player);
            if (playerData != null) {
                playerData.addProperty("online", true);
                players.add(playerData);
                onlineUUIDs.add(player.getStringUUID());
            }
        }

        // Add offline players if requested and enabled
        if (includeOffline && cache != null) {
            for (PlayerDataCache.CachedPlayerData cachedData : cache.getAllPlayers()) {
                // Skip if player is already included (online)
                if (onlineUUIDs.contains(cachedData.uuid)) {
                    continue;
                }

                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("uuid", cachedData.uuid);
                playerObj.addProperty("name", cachedData.name);
                playerObj.addProperty("playersKilled", cachedData.playersKilled);
                playerObj.addProperty("coloniesRaided", cachedData.coloniesRaided);
                playerObj.addProperty("amountRaided", cachedData.amountRaided);
                playerObj.addProperty("warsWon", cachedData.warsWon);
                playerObj.addProperty("warStalemates", cachedData.warStalemates);
                playerObj.addProperty("online", false);
                players.add(playerObj);
            }

            response.addProperty("cacheLastRefresh", cache.getLastRefresh().toString());
            response.addProperty("cacheRefreshing", cache.isRefreshing());
        }

        response.addProperty("totalPlayers", players.size());
        response.add("players", players);

        return GSON.toJson(response);
    }

    /**
     * Get leaderboard data as JSON
     */
    public String getLeaderboardJSON(String sortBy, int limit, boolean includeOffline) {
        JsonObject response = new JsonObject();
        response.addProperty("generated", Instant.now().toString());
        response.addProperty("sortBy", sortBy);
        response.addProperty("limit", limit);
        response.addProperty("includeOffline", includeOffline);

        // Collect all player data
        List<PlayerStatsEntry> entries = new ArrayList<>();
        Set<String> onlineUUIDs = new HashSet<>();
        
        // Add online players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                // Use resolve() to get actual capability, not a temporary empty instance
                var capability = player.getCapability(PlayerWarDataCapability.CAPABILITY);
                PlayerWarData data = capability.resolve().orElse(null);
                
                if (data != null) {
                    entries.add(new PlayerStatsEntry(
                        player.getStringUUID(),
                        player.getName().getString(),
                        data.getPlayersKilledInWar(),
                        data.getRaidedColonies(),
                        data.getAmountRaided(),
                        data.getWarsWon(),
                        data.getWarStalemates(),
                        true // online
                    ));
                    onlineUUIDs.add(player.getStringUUID());
                } else {
                    MineColonyTax.LOGGER.warn("Capability not loaded for player {} in leaderboard", 
                        player.getName().getString());
                }
            } catch (Exception e) {
                MineColonyTax.LOGGER.warn("Error reading stats for player {} in leaderboard: {}", 
                    player.getName().getString(), e.getMessage());
            }
        }
        
        // Add offline players if requested
        if (includeOffline && cache != null) {
            for (PlayerDataCache.CachedPlayerData cachedData : cache.getAllPlayers()) {
                // Skip if already included (online)
                if (onlineUUIDs.contains(cachedData.uuid)) {
                    continue;
                }
                
                entries.add(new PlayerStatsEntry(
                    cachedData.uuid,
                    cachedData.name,
                    cachedData.playersKilled,
                    cachedData.coloniesRaided,
                    cachedData.amountRaided,
                    cachedData.warsWon,
                    cachedData.warStalemates,
                    false // offline
                ));
            }
            
            response.addProperty("cacheLastRefresh", cache.getLastRefresh().toString());
            response.addProperty("cacheRefreshing", cache.isRefreshing());
        }

        // Sort based on requested field
        Comparator<PlayerStatsEntry> comparator = getComparator(sortBy);
        entries.sort(comparator.reversed());

        // Limit results
        List<PlayerStatsEntry> limitedEntries = entries.stream()
            .limit(limit)
            .collect(Collectors.toList());

        // Build JSON
        JsonArray leaderboard = new JsonArray();
        int rank = 1;
        for (PlayerStatsEntry entry : limitedEntries) {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("rank", rank++);
            playerObj.addProperty("uuid", entry.uuid);
            playerObj.addProperty("name", entry.name);
            playerObj.addProperty("value", getSortValue(entry, sortBy));
            playerObj.addProperty("online", entry.online);
            
            // Include all stats for reference
            JsonObject stats = new JsonObject();
            stats.addProperty("playersKilled", entry.playersKilled);
            stats.addProperty("coloniesRaided", entry.coloniesRaided);
            stats.addProperty("amountRaided", entry.amountRaided);
            stats.addProperty("warsWon", entry.warsWon);
            stats.addProperty("warStalemates", entry.warStalemates);
            playerObj.add("stats", stats);
            
            leaderboard.add(playerObj);
        }

        response.add("leaderboard", leaderboard);
        return GSON.toJson(response);
    }

    /**
     * Get stats for a specific player by UUID
     */
    public String getPlayerStatsJSON(String playerUUID) {
        JsonObject response = new JsonObject();
        response.addProperty("generated", Instant.now().toString());
        response.addProperty("uuid", playerUUID);

        // Try to find online player first
        ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(playerUUID));
        if (player != null) {
            JsonObject playerData = getPlayerStatsObject(player);
            if (playerData != null) {
                playerData.addProperty("online", true);
                response.add("player", playerData);
                response.addProperty("found", true);
            } else {
                response.addProperty("found", false);
                response.addProperty("error", "Player data not available");
            }
        } else {
            // Try to find in offline cache
            if (cache != null) {
                PlayerDataCache.CachedPlayerData cachedData = cache.getPlayer(playerUUID);
                if (cachedData != null) {
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("uuid", cachedData.uuid);
                    playerObj.addProperty("name", cachedData.name);
                    playerObj.addProperty("playersKilled", cachedData.playersKilled);
                    playerObj.addProperty("coloniesRaided", cachedData.coloniesRaided);
                    playerObj.addProperty("amountRaided", cachedData.amountRaided);
                    playerObj.addProperty("warsWon", cachedData.warsWon);
                    playerObj.addProperty("warStalemates", cachedData.warStalemates);
                    playerObj.addProperty("online", false);
                    response.add("player", playerObj);
                    response.addProperty("found", true);
                    response.addProperty("cacheLastRefresh", cache.getLastRefresh().toString());
                } else {
                    response.addProperty("found", false);
                    response.addProperty("error", "Player not found");
                    response.addProperty("online", false);
                }
            } else {
                response.addProperty("found", false);
                response.addProperty("error", "Player not online");
                response.addProperty("online", false);
            }
        }

        return GSON.toJson(response);
    }

    /**
     * Get server statistics summary
     */
    public String getServerStatsJSON() {
        JsonObject response = new JsonObject();
        response.addProperty("generated", Instant.now().toString());
        response.addProperty("serverName", server.getServerModName());
        response.addProperty("onlinePlayers", server.getPlayerList().getPlayerCount());
        response.addProperty("maxPlayers", server.getPlayerList().getMaxPlayers());

        // Calculate totals from online players
        int totalWarsWon = 0;
        int totalColoniesRaided = 0;
        int totalPlayersKilled = 0;
        long totalAmountRaided = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                // Use get() with resolve() to ensure we have a real attached capability
                // Don't use getOrCreate() as it can return unattached empty instances
                var capability = player.getCapability(PlayerWarDataCapability.CAPABILITY);
                PlayerWarData data = capability.resolve().orElse(null);
                
                if (data != null) {
                    totalWarsWon += data.getWarsWon();
                    totalColoniesRaided += data.getRaidedColonies();
                    totalPlayersKilled += data.getPlayersKilledInWar();
                    totalAmountRaided += data.getAmountRaided();
                    
                    MineColonyTax.LOGGER.debug("Read stats for {}: wars={}, raids={}, killed={}, amount={}",
                        player.getName().getString(), data.getWarsWon(), data.getRaidedColonies(),
                        data.getPlayersKilledInWar(), data.getAmountRaided());
                } else {
                    MineColonyTax.LOGGER.warn("Capability not attached or not loaded for player {}", 
                        player.getName().getString());
                }
            } catch (Exception e) {
                MineColonyTax.LOGGER.warn("Error reading stats for player {}: {}", 
                    player.getName().getString(), e.getMessage());
            }
        }

        JsonObject totals = new JsonObject();
        totals.addProperty("warsWon", totalWarsWon);
        totals.addProperty("coloniesRaided", totalColoniesRaided);
        totals.addProperty("playersKilled", totalPlayersKilled);
        totals.addProperty("amountRaided", totalAmountRaided);

        response.add("totals", totals);

        return GSON.toJson(response);
    }

    /**
     * Helper method to build a player stats JSON object
     */
    private JsonObject getPlayerStatsObject(ServerPlayer player) {
        try {
            // Use resolve() to get the actual attached capability
            var capability = player.getCapability(PlayerWarDataCapability.CAPABILITY);
            PlayerWarData data = capability.resolve().orElse(null);
            
            if (data == null) {
                MineColonyTax.LOGGER.warn("Capability not loaded for player {}", 
                    player.getName().getString());
                return null;
            }
            
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("uuid", player.getStringUUID());
            playerObj.addProperty("name", player.getName().getString());
            playerObj.addProperty("playersKilled", data.getPlayersKilledInWar());
            playerObj.addProperty("coloniesRaided", data.getRaidedColonies());
            playerObj.addProperty("amountRaided", data.getAmountRaided());
            playerObj.addProperty("warsWon", data.getWarsWon());
            playerObj.addProperty("warStalemates", data.getWarStalemates());
            
            return playerObj;
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error getting stats for player {}: {}", 
                player.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Get comparator for sorting
     */
    private Comparator<PlayerStatsEntry> getComparator(String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "playerskilled":
                return Comparator.comparingInt(e -> e.playersKilled);
            case "coloniesraided":
                return Comparator.comparingInt(e -> e.coloniesRaided);
            case "amountraided":
                return Comparator.comparingLong(e -> e.amountRaided);
            case "warswon":
                return Comparator.comparingInt(e -> e.warsWon);
            case "warstalemates":
                return Comparator.comparingInt(e -> e.warStalemates);
            default:
                return Comparator.comparingInt(e -> e.warsWon); // default to wars won
        }
    }

    /**
     * Get the sorted value for a player entry
     */
    private long getSortValue(PlayerStatsEntry entry, String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "playerskilled":
                return entry.playersKilled;
            case "coloniesraided":
                return entry.coloniesRaided;
            case "amountraided":
                return entry.amountRaided;
            case "warswon":
                return entry.warsWon;
            case "warstalemates":
                return entry.warStalemates;
            default:
                return entry.warsWon;
        }
    }

    /**
     * Internal class for storing player stats
     */
    private static class PlayerStatsEntry {
        final String uuid;
        final String name;
        final int playersKilled;
        final int coloniesRaided;
        final long amountRaided;
        final int warsWon;
        final int warStalemates;
        final boolean online;

        PlayerStatsEntry(String uuid, String name, int playersKilled, int coloniesRaided,
                        long amountRaided, int warsWon, int warStalemates, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.playersKilled = playersKilled;
            this.coloniesRaided = coloniesRaided;
            this.amountRaided = amountRaided;
            this.warsWon = warsWon;
            this.warStalemates = warStalemates;
            this.online = online;
        }
    }
}
