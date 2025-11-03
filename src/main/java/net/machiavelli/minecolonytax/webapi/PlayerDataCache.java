package net.machiavelli.minecolonytax.webapi;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for offline player war statistics.
 * Scans player data files and caches the results to avoid expensive file I/O on every request.
 * 
 * Thread-safe for concurrent access.
 */
public class PlayerDataCache {

    private final MinecraftServer server;
    private final Map<String, CachedPlayerData> cache = new ConcurrentHashMap<>();
    private volatile Instant lastRefresh = Instant.EPOCH;
    private volatile boolean refreshing = false;

    public PlayerDataCache(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Get all cached player data (online and offline)
     */
    public Collection<CachedPlayerData> getAllPlayers() {
        return new ArrayList<>(cache.values());
    }

    /**
     * Get cached data for a specific player by UUID
     */
    public CachedPlayerData getPlayer(String uuid) {
        return cache.get(uuid);
    }

    /**
     * Get the last refresh timestamp
     */
    public Instant getLastRefresh() {
        return lastRefresh;
    }

    /**
     * Check if cache is currently refreshing
     */
    public boolean isRefreshing() {
        return refreshing;
    }

    /**
     * Get total cached player count
     */
    public int getCachedPlayerCount() {
        return cache.size();
    }

    /**
     * Refresh the cache by scanning player data files
     * This is an expensive operation and should be called periodically, not on every request
     */
    public synchronized void refresh() {
        if (refreshing) {
            MineColonyTax.LOGGER.debug("Cache refresh already in progress, skipping");
            return;
        }

        refreshing = true;
        long startTime = System.currentTimeMillis();
        
        try {
            MineColonyTax.LOGGER.info("Starting offline player data cache refresh...");
            
            // Get the playerdata directory
            File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            File playerDataDir = new File(worldDir, "playerdata");

            if (!playerDataDir.exists() || !playerDataDir.isDirectory()) {
                MineColonyTax.LOGGER.warn("Player data directory not found: {}", playerDataDir.getAbsolutePath());
                return;
            }

            // Scan all .dat files
            File[] playerFiles = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
            if (playerFiles == null || playerFiles.length == 0) {
                MineColonyTax.LOGGER.info("No player data files found");
                return;
            }

            int successCount = 0;
            int errorCount = 0;

            for (File playerFile : playerFiles) {
                try {
                    String uuid = playerFile.getName().replace(".dat", "");
                    CachedPlayerData data = loadPlayerData(uuid, playerFile);
                    
                    if (data != null) {
                        cache.put(uuid, data);
                        successCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    MineColonyTax.LOGGER.debug("Error loading player data from {}: {}", 
                        playerFile.getName(), e.getMessage());
                }
            }

            lastRefresh = Instant.now();
            long duration = System.currentTimeMillis() - startTime;

            MineColonyTax.LOGGER.info("Cache refresh complete: {} players loaded, {} errors, took {}ms", 
                successCount, errorCount, duration);

        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error refreshing player data cache: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            refreshing = false;
        }
    }

    /**
     * Load player war data from a .dat file
     */
    private CachedPlayerData loadPlayerData(String uuid, File playerFile) {
        try {
            // Read the player's NBT data
            CompoundTag playerNBT = NbtIo.readCompressed(playerFile);
            
            // Get player name from NBT (stored in "bukkit" section for some servers, or custom data)
            String playerName = uuid.substring(0, 8); // Fallback to UUID prefix
            
            // Try to get actual player name if available
            if (playerNBT.contains("bukkit")) {
                CompoundTag bukkit = playerNBT.getCompound("bukkit");
                if (bukkit.contains("lastKnownName")) {
                    playerName = bukkit.getString("lastKnownName");
                }
            }

            // Get ForgeData section which contains our war stats
            if (!playerNBT.contains("ForgeData")) {
                return null; // No Forge data, player never had war stats
            }

            CompoundTag forgeData = playerNBT.getCompound("ForgeData");
            String warDataKey = MineColonyTax.MOD_ID + "_war_data";
            
            if (!forgeData.contains(warDataKey)) {
                return null; // No war stats data
            }

            CompoundTag warData = forgeData.getCompound(warDataKey);

            // Extract war statistics
            int playersKilled = warData.getInt("playersKilledInWar");
            int coloniesRaided = warData.getInt("raidedColonies");
            long amountRaided = warData.getLong("amountRaided");
            int warsWon = warData.getInt("warsWon");
            int warStalemates = warData.getInt("warStalemates");

            // Only cache if player has at least some stats
            if (playersKilled == 0 && coloniesRaided == 0 && amountRaided == 0 && 
                warsWon == 0 && warStalemates == 0) {
                return null; // No stats to report
            }

            return new CachedPlayerData(
                uuid,
                playerName,
                playersKilled,
                coloniesRaided,
                amountRaided,
                warsWon,
                warStalemates,
                false // offline
            );

        } catch (IOException e) {
            MineColonyTax.LOGGER.debug("IO error reading player file {}: {}", playerFile.getName(), e.getMessage());
            return null;
        } catch (Exception e) {
            MineColonyTax.LOGGER.debug("Error parsing player data from {}: {}", playerFile.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Clear the cache
     */
    public void clear() {
        cache.clear();
        lastRefresh = Instant.EPOCH;
        MineColonyTax.LOGGER.info("Player data cache cleared");
    }

    /**
     * Cached player data entry
     */
    public static class CachedPlayerData {
        public final String uuid;
        public final String name;
        public final int playersKilled;
        public final int coloniesRaided;
        public final long amountRaided;
        public final int warsWon;
        public final int warStalemates;
        public final boolean online;

        public CachedPlayerData(String uuid, String name, int playersKilled, int coloniesRaided,
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
