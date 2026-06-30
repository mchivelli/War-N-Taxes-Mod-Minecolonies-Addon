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

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OfficerColonyVisitTracker {

    private static final Logger LOGGER = LogManager.getLogger(OfficerColonyVisitTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/warntax/officerVisitData.json";

    private static final Map<Integer, Long> lastOfficerVisit = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerLastColony = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> playerLastChunk = new ConcurrentHashMap<>();
    private static volatile boolean isDirty = false;
    private static final int SAVE_INTERVAL_TICKS = 6000;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadData();

        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
            LOGGER.info("OfficerColonyVisitTracker initialized - tracking {} colonies", lastOfficerVisit.size());
        }

        // Delayed migration: MineColonies needs time to finish loading all colonies
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(
                () -> migrateExistingColonies(event.getServer()), 5000);
    }

    private static void migrateExistingColonies(net.minecraft.server.MinecraftServer server) {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int coloniesMigrated = 0;
            int coloniesAlreadyTracked = 0;

            for (IColony colony : colonyManager.getAllColonies()) {
                int colonyId = colony.getID();

                if (lastOfficerVisit.containsKey(colonyId)) {
                    coloniesAlreadyTracked++;
                    continue;
                }

                int mcLastContactHours = colony.getLastContactInHours();
                long hoursInMillis = mcLastContactHours * 60L * 60L * 1000L;
                long initialTimestamp = System.currentTimeMillis() - hoursInMillis;

                lastOfficerVisit.put(colonyId, initialTimestamp);
                coloniesMigrated++;

                LOGGER.debug("Migrated colony {} (ID: {}) - initialized WnT timer to {} hours based on MC timer",
                        colony.getName(), colonyId, mcLastContactHours);
            }

            if (coloniesMigrated > 0) {
                markDirty();
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info(
                            "MIGRATION: Initialized WnT tracking for {} colonies (MC timer -> WnT timer). {} colonies already tracked.",
                            coloniesMigrated, coloniesAlreadyTracked);
                }
            } else {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("MIGRATION: No migration needed - all {} colonies already have WnT tracking",
                            coloniesAlreadyTracked);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error during colony migration: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (isDirty) {
            saveDataInternal();
        }
        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
            LOGGER.info("OfficerColonyVisitTracker data saved on shutdown");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!TaxConfig.isColonyAutoAbandonEnabled()) {
            return;
        }

        if (!TaxConfig.shouldResetTimerOnOfficerLogin()) {
            return;
        }

        UUID playerId = player.getUUID();
        int coloniesReset = 0;

        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();

            for (IColony colony : colonyManager.getAllColonies()) {
                if (isOwnerOrOfficer(colony, playerId)) {
                    resetColonyContactTime(colony);
                    coloniesReset++;
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.debug("{} logged in - reset timer for colony '{}'",
                                player.getName().getString(), colony.getName());
                    }
                }
            }

            if (coloniesReset > 0) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("{} logged in - reset abandonment timers for {} colonies",
                            player.getName().getString(), coloniesReset);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error resetting colony timers on player login: {}", e.getMessage());
        }
    }

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

        if (isDirty) {
            saveDataInternal();
            isDirty = false;
            LOGGER.debug("Periodic save: {} colony visit records", lastOfficerVisit.size());
        }
    }

    private static void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<Integer, Long>>() {
                    }.getType();
                    Map<Integer, Long> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) {
                        lastOfficerVisit.clear();
                        lastOfficerVisit.putAll(loaded);
                        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                            LOGGER.info("Loaded {} officer visit records from {}", loaded.size(), DATA_FILE);
                        }
                    }
                }
            } else {
                LOGGER.debug("No officer visit data file found at {} - starting fresh", DATA_FILE);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load officer visit data: {}", e.getMessage());
        }
    }

    private static void saveDataInternal() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();

            // Atomic write: serialize to a temp file then move it into place, so a crash mid-write
            // cannot truncate officerVisitData.json. A truncated file would lose every colony's
            // visit timestamp on the next load (degrading abandonment timers to the MC fallback).
            // Mirrors FirstColonyTracker.saveData().
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(lastOfficerVisit, writer);
            }
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.debug("Saved {} officer visit records to {}", lastOfficerVisit.size(), DATA_FILE);

        } catch (IOException e) {
            LOGGER.error("Failed to save officer visit data: {}", e.getMessage());
        }
    }

    private static void markDirty() {
        isDirty = true;
    }

    private static void resetColonyContactTime(IColony colony) {
        long now = System.currentTimeMillis();
        lastOfficerVisit.put(colony.getID(), now);
        markDirty();

        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            LOGGER.debug("Reset abandonment timer for colony '{}' (will save on next batch)",
                    colony.getName());
        }
    }

    public static boolean hasRecentOfficerVisit(int colonyId) {
        Long lastVisit = lastOfficerVisit.get(colonyId);
        if (lastVisit == null) {
            return false;
        }

        long hoursSinceVisit = (System.currentTimeMillis() - lastVisit) / (1000 * 60 * 60);
        return hoursSinceVisit < 24;
    }

    public static long getHoursSinceOfficerVisit(int colonyId) {
        Long lastVisit = lastOfficerVisit.get(colonyId);
        if (lastVisit == null) {
            return -1;
        }

        return (System.currentTimeMillis() - lastVisit) / (1000 * 60 * 60);
    }

    public static long getLastOfficerVisitTimestamp(int colonyId) {
        Long lastVisit = lastOfficerVisit.get(colonyId);
        return lastVisit != null ? lastVisit : -1;
    }

    public static void clearColonyVisitData(int colonyId) {
        lastOfficerVisit.remove(colonyId);
        markDirty();
    }

    public static boolean isOwnerOrOfficer(IColony colony, UUID playerId) {
        if (colony == null || colony.getPermissions() == null || playerId == null) {
            return false;
        }

        UUID owner = colony.getPermissions().getOwner();
        if (owner != null && owner.equals(playerId)) {
            return true;
        }

        ColonyPlayer colonyPlayer = colony.getPermissions().getPlayers().get(playerId);
        if (colonyPlayer != null && colonyPlayer.getRank().isColonyManager()) {
            return true;
        }

        return false;
    }

    public static void forceSave() {
        if (isDirty) {
            saveDataInternal();
            isDirty = false;
        }
    }

    public static int getTrackedColonyCount() {
        return lastOfficerVisit.size();
    }

    private static long encodeChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (!TaxConfig.isColonyAutoAbandonEnabled()) {
            return;
        }

        UUID playerId = player.getUUID();

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        long currentChunk = encodeChunkPos(chunkX, chunkZ);

        Long lastChunk = playerLastChunk.get(playerId);
        if (lastChunk != null && lastChunk == currentChunk) {
            return;
        }

        playerLastChunk.put(playerId, currentChunk);

        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony currentColony = colonyManager.getColonyByPosFromWorld(player.level(), player.blockPosition());
            int currentColonyId = (currentColony != null) ? currentColony.getID() : -1;

            Integer lastColonyId = playerLastColony.get(playerId);

            if (currentColonyId != -1) {
                playerLastColony.put(playerId, currentColonyId);
            } else {
                playerLastColony.remove(playerId);
            }

            boolean isEntry = (currentColonyId != -1) &&
                    (lastColonyId == null || lastColonyId != currentColonyId);

            if (isEntry && currentColony != null) {
                if (isOwnerOrOfficer(currentColony, playerId)) {
                    resetColonyContactTime(currentColony);
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.debug("{} entered their colony '{}' - timer reset",
                                player.getName().getString(), currentColony.getName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking colony entry: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            playerLastColony.remove(playerId);
            playerLastChunk.remove(playerId);
        }
    }
}
