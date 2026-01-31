package net.machiavelli.minecolonytax.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks owner/officer activity to reset colony abandonment timers.
 *
 * DEFAULT BEHAVIOR: Timer resets when an owner or officer physically ENTERS their colony (chunk-based detection).
 * This ensures officers must actually visit their colonies to prevent abandonment.
 *
 * OPTIONAL: Login tracking can be enabled via config (ResetTimerOnOfficerLogin = true).
 * When enabled, timer also resets when officers log into the server (for ALL colonies they manage).
 * NOT RECOMMENDED: This defeats the purpose of requiring actual visits.
 *
 * This replaces MineColonies' internal timer system to provide more accurate
 * abandonment tracking that includes all officers, not just owners.
 *
 * Efficiency guarantees:
 * - Physical visit detection only checks on chunk change (not every tick)
 * - Optional login tracking iterates colonies only on login (rare event)
 * - File I/O is batched: saves every 5 minutes OR on shutdown
 * - Uses dirty flag to avoid unnecessary writes
 * - Minimal memory: only tracks online players' chunk positions
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OfficerColonyVisitTracker {
    
    private static final Logger LOGGER = LogManager.getLogger(OfficerColonyVisitTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/warntax/officerVisitData.json";
    
    // Track last login time for each colony by officers/owners (colonyId -> timestamp in millis)
    private static final Map<Integer, Long> lastOfficerVisit = new ConcurrentHashMap<>();
    
    // Track each player's last known colony ID for entry detection (playerUUID -> colonyId, -1 if not in colony)
    private static final Map<UUID, Integer> playerLastColony = new ConcurrentHashMap<>();
    
    // Track each player's last known chunk position to detect chunk changes
    private static final Map<UUID, Long> playerLastChunk = new ConcurrentHashMap<>();
    
    // Dirty flag for batched saves - avoid constant file I/O
    private static volatile boolean isDirty = false;
    
    // Save interval: 5 minutes = 6000 ticks (at 20 TPS)
    private static final int SAVE_INTERVAL_TICKS = 6000;
    private static int tickCounter = 0;
    
    /**
     * Load persisted data on server start and migrate existing colonies.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadData();
        LOGGER.info("✅ OfficerColonyVisitTracker initialized - tracking {} colonies", lastOfficerVisit.size());

        // Perform data migration: initialize tracking for existing colonies without WnT data
        // This is delayed to ensure MineColonies has fully loaded
        event.getServer().execute(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds for full server initialization
                migrateExistingColonies(event.getServer());
            } catch (InterruptedException e) {
                LOGGER.warn("Migration thread interrupted: {}", e.getMessage());
            }
        });
    }

    /**
     * Migrate existing colonies to WnT tracking system.
     * For colonies without WnT data, initializes their timer based on MineColonies' lastContactInHours.
     * This ensures existing colonies aren't prematurely abandoned due to missing WnT data.
     */
    private static void migrateExistingColonies(net.minecraft.server.MinecraftServer server) {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int coloniesMigrated = 0;
            int coloniesAlreadyTracked = 0;

            for (IColony colony : colonyManager.getAllColonies()) {
                int colonyId = colony.getID();

                // Check if this colony already has WnT tracking data
                if (lastOfficerVisit.containsKey(colonyId)) {
                    coloniesAlreadyTracked++;
                    continue;
                }

                // Colony needs migration - initialize with MineColonies timer
                int mcLastContactHours = colony.getLastContactInHours();

                // Convert hours to milliseconds and backdate the timestamp
                long hoursInMillis = mcLastContactHours * 60L * 60L * 1000L;
                long initialTimestamp = System.currentTimeMillis() - hoursInMillis;

                lastOfficerVisit.put(colonyId, initialTimestamp);
                coloniesMigrated++;

                LOGGER.debug("Migrated colony {} (ID: {}) - initialized WnT timer to {} hours based on MC timer",
                           colony.getName(), colonyId, mcLastContactHours);
            }

            if (coloniesMigrated > 0) {
                markDirty(); // Save migrated data
                LOGGER.info("✅ MIGRATION: Initialized WnT tracking for {} colonies (MC timer -> WnT timer). {} colonies already tracked.",
                           coloniesMigrated, coloniesAlreadyTracked);
            } else {
                LOGGER.info("✅ MIGRATION: No migration needed - all {} colonies already have WnT tracking",
                           coloniesAlreadyTracked);
            }

        } catch (Exception e) {
            LOGGER.error("Error during colony migration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save data when server stops.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (isDirty) {
            saveDataInternal();
        }
        LOGGER.info("✅ OfficerColonyVisitTracker data saved on shutdown");
    }

    /**
     * Track officer/owner logins - resets abandonment timer for all colonies they manage.
     *
     * OPTIONAL FEATURE (disabled by default): Controlled by ResetTimerOnOfficerLogin config.
     * When disabled (default), timers only reset on physical colony visits (chunk-based detection).
     * When enabled, timers reset for ALL colonies an officer manages just by logging in.
     *
     * RECOMMENDED: Keep this disabled to force officers to actually visit their colonies.
     *
     * This is efficient because:
     * - Login events are rare (only happens when player joins server)
     * - Colony iteration is fast (typically < 100 colonies even on large servers)
     * - Only updates colonies where player has officer/owner rank
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Skip if feature is disabled
        if (!TaxConfig.isColonyAutoAbandonEnabled()) {
            return;
        }

        // Skip if login tracking is disabled (default behavior)
        if (!TaxConfig.shouldResetTimerOnOfficerLogin()) {
            return;
        }

        UUID playerId = player.getUUID();
        int coloniesReset = 0;

        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();

            // Check all colonies in all dimensions
            for (IColony colony : colonyManager.getAllColonies()) {
                // Only reset timer if player is owner or officer of this colony
                if (isOwnerOrOfficer(colony, playerId)) {
                    resetColonyContactTime(colony);
                    coloniesReset++;
                    LOGGER.debug("✅ {} logged in - reset timer for colony '{}'",
                                player.getName().getString(), colony.getName());
                }
            }

            if (coloniesReset > 0) {
                LOGGER.info("✅ {} logged in - reset abandonment timers for {} colonies",
                           player.getName().getString(), coloniesReset);
            }

        } catch (Exception e) {
            LOGGER.error("Error resetting colony timers on player login: {}", e.getMessage());
        }
    }

    /**
     * Periodic save handler - saves dirty data every 5 minutes.
     * This batches I/O to avoid constant file writes.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        tickCounter++;
        if (tickCounter < SAVE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        
        // Only save if data changed
        if (isDirty) {
            saveDataInternal();
            isDirty = false;
            LOGGER.debug("Periodic save: {} colony visit records", lastOfficerVisit.size());
        }
    }
    
    /**
     * Load visit data from JSON file.
     */
    private static void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<Integer, Long>>(){}.getType();
                    Map<Integer, Long> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) {
                        lastOfficerVisit.clear();
                        lastOfficerVisit.putAll(loaded);
                        LOGGER.info("Loaded {} officer visit records from {}", loaded.size(), DATA_FILE);
                    }
                }
            } else {
                LOGGER.debug("No officer visit data file found at {} - starting fresh", DATA_FILE);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load officer visit data: {}", e.getMessage());
        }
    }
    
    /**
     * Internal save - actually writes to disk.
     * Called by periodic save and shutdown handler.
     */
    private static void saveDataInternal() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(lastOfficerVisit, writer);
            }
            
            LOGGER.debug("Saved {} officer visit records to {}", lastOfficerVisit.size(), DATA_FILE);
            
        } catch (IOException e) {
            LOGGER.error("Failed to save officer visit data: {}", e.getMessage());
        }
    }
    
    /**
     * Mark data as dirty - will be saved on next periodic save or shutdown.
     */
    private static void markDirty() {
        isDirty = true;
    }
    
    /**
     * Reset the colony's last contact time by recording it in our local tracking system.
     * Does NOT immediately write to disk - uses dirty flag for batched saves.
     */
    private static void resetColonyContactTime(IColony colony) {
        long now = System.currentTimeMillis();
        lastOfficerVisit.put(colony.getID(), now);
        markDirty(); // Will be saved on next periodic save
        
        LOGGER.debug("✅ Reset abandonment timer for colony '{}' (will save on next batch)", 
                    colony.getName());
    }
    
    /**
     * Check if a colony has been recently visited by an officer.
     * 
     * @param colonyId The colony ID to check
     * @return true if an officer has logged in within the last 24 hours
     */
    public static boolean hasRecentOfficerVisit(int colonyId) {
        Long lastVisit = lastOfficerVisit.get(colonyId);
        if (lastVisit == null) {
            return false;
        }
        
        long hoursSinceVisit = (System.currentTimeMillis() - lastVisit) / (1000 * 60 * 60);
        return hoursSinceVisit < 24;
    }
    
    /**
     * Get hours since last officer login for a colony.
     * Returns -1 if no visit has been tracked.
     */
    public static long getHoursSinceOfficerVisit(int colonyId) {
        Long lastVisit = lastOfficerVisit.get(colonyId);
        if (lastVisit == null) {
            return -1;
        }
        
        return (System.currentTimeMillis() - lastVisit) / (1000 * 60 * 60);
    }
    
    /**
     * Get the raw timestamp of last officer login for a colony.
     * Returns -1 if no visit has been tracked.
     */
    public static long getLastOfficerVisitTimestamp(int colonyId) {
        Long lastVisit = lastOfficerVisit.get(colonyId);
        return lastVisit != null ? lastVisit : -1;
    }
    
    /**
     * Clear visit tracking for a specific colony (used when colony is deleted/claimed).
     */
    public static void clearColonyVisitData(int colonyId) {
        lastOfficerVisit.remove(colonyId);
        markDirty();
    }
    
    /**
     * Check if a player is an owner or officer of a colony.
     */
    public static boolean isOwnerOrOfficer(IColony colony, UUID playerId) {
        if (colony == null || colony.getPermissions() == null || playerId == null) {
            return false;
        }
        
        // Check if player is the owner
        UUID owner = colony.getPermissions().getOwner();
        if (owner != null && owner.equals(playerId)) {
            return true;
        }
        
        // Check if player is an officer (has colony manager rank)
        ColonyPlayer colonyPlayer = colony.getPermissions().getPlayers().get(playerId);
        if (colonyPlayer != null && colonyPlayer.getRank().isColonyManager()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Force save data immediately (for admin commands, etc.).
     */
    public static void forceSave() {
        if (isDirty) {
            saveDataInternal();
            isDirty = false;
        }
    }
    
    /**
     * Get the number of tracked colonies.
     */
    public static int getTrackedColonyCount() {
        return lastOfficerVisit.size();
    }
    
    /**
     * Helper to encode chunk position as a single long for efficient comparison.
     */
    private static long encodeChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Player tick handler - detects colony entry when player changes chunks.
     * This is efficient because:
     * - Only fires per player (not per server tick)
     * - Only checks colony when player changes chunks (not every tick)
     * - Colony lookup only happens on chunk change
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only process at END phase, server side
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        
        // Skip if feature is disabled
        if (!TaxConfig.isColonyAutoAbandonEnabled()) {
            return;
        }
        
        UUID playerId = player.getUUID();
        
        // Get current chunk position
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        long currentChunk = encodeChunkPos(chunkX, chunkZ);
        
        // Check if chunk changed
        Long lastChunk = playerLastChunk.get(playerId);
        if (lastChunk != null && lastChunk == currentChunk) {
            // Same chunk, no need to check colony
            return;
        }
        
        // Update chunk tracking
        playerLastChunk.put(playerId, currentChunk);
        
        // Chunk changed - now check if colony changed
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony currentColony = colonyManager.getColonyByPosFromWorld(player.level(), player.blockPosition());
            int currentColonyId = (currentColony != null) ? currentColony.getID() : -1;
            
            // Get last known colony for this player
            Integer lastColonyId = playerLastColony.get(playerId);
            
            // Update colony tracking
            if (currentColonyId != -1) {
                playerLastColony.put(playerId, currentColonyId);
            } else {
                playerLastColony.remove(playerId);
            }
            
            // Detect ENTRY: player wasn't in this colony before, but is now
            boolean isEntry = (currentColonyId != -1) && 
                             (lastColonyId == null || lastColonyId != currentColonyId);
            
            if (isEntry && currentColony != null) {
                // Only reset timer if player is owner or officer of this colony
                if (isOwnerOrOfficer(currentColony, playerId)) {
                    resetColonyContactTime(currentColony);
                    LOGGER.debug("✅ {} entered their colony '{}' - timer reset", 
                                player.getName().getString(), currentColony.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking colony entry: {}", e.getMessage());
        }
    }
    
    /**
     * Clean up player tracking on logout.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            playerLastColony.remove(playerId);
            playerLastChunk.remove(playerId);
        }
    }
}
