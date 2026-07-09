package net.machiavelli.minecolonytax.deletion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent, construction-scaled colony deletion for the "take over colony" flow.
 *
 * <p>When a player conquers a colony they may be its only officer, which makes MineColonies'
 * own delete unavailable. This manager lets the owner schedule a deletion through
 * {@link IColonyManager#deleteColonyByWorld(int, boolean, ServerLevel)} on a timer that scales
 * with how developed the colony is — an anti-griefing brake so a conqueror can't instantly wipe
 * a heavily-built town, while a bare town-hall-spam claim can be cleared right away.
 *
 * <p>Timer tiers (see {@link #computeScaledDelayMillis}):
 * <ul>
 *   <li>only a town-hall block (nothing built) → instant</li>
 *   <li>town hall built, no other buildings → 1 day</li>
 *   <li>at least one other building → 3 days</li>
 *   <li>multiple buildings + enough guard towers → 7 days</li>
 * </ul>
 *
 * <p>Entries persist to disk so day-long timers survive restarts. On the due tick the deletion is
 * cancelled automatically if the colony changed hands since it was scheduled (safety against a
 * reclaim making the timer stale).
 */
public final class ColonyDeletionManager {

    private static final Logger LOGGER = LogManager.getLogger(ColonyDeletionManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/warntax/pendingColonyDeletions.json";

    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    /** colonyId -> scheduled deletion. */
    private static final Map<Integer, PendingDeletion> PENDING = new ConcurrentHashMap<>();

    private ColonyDeletionManager() {}

    /** Persisted record of a scheduled deletion. */
    public static final class PendingDeletion {
        public int colonyId;
        public long executeAt;      // epoch millis
        public String requestedBy;  // UUID string of the player who scheduled it
        public String dimension;    // ResourceLocation string of the colony's level

        public PendingDeletion() {}

        public PendingDeletion(int colonyId, long executeAt, UUID requestedBy, String dimension) {
            this.colonyId = colonyId;
            this.executeAt = executeAt;
            this.requestedBy = requestedBy != null ? requestedBy.toString() : null;
            this.dimension = dimension;
        }
    }

    // ---------------------------------------------------------------- scheduling

    /**
     * Schedules (or immediately performs, if the delay is zero) deletion of the given colony.
     * The requester should be the colony owner; ownership is re-verified when the timer fires.
     *
     * @return the delay in milliseconds before deletion (0 = deleted immediately)
     */
    public static long schedule(IColony colony, ServerPlayer requester) {
        if (colony == null || colony.getWorld() == null) {
            return -1;
        }
        long delay = computeScaledDelayMillis(colony);
        String dim = colony.getDimension() != null ? colony.getDimension().location().toString() : null;

        if (delay <= 0) {
            // Instant path — bare town-hall claim.
            boolean deleted = performDeletion(colony.getID(), dim, requester.server);
            if (deleted) {
                requester.sendSystemMessage(Component.literal(
                        "Colony deleted immediately (only a town-hall block was present).")
                        .withStyle(ChatFormatting.GREEN));
            }
            return 0;
        }

        PENDING.put(colony.getID(), new PendingDeletion(colony.getID(), nowPlus(requester.server, delay),
                requester.getUUID(), dim));
        save();

        long hours = Math.max(1, delay / (60L * 60L * 1000L));
        requester.sendSystemMessage(Component.literal(
                colony.getName() + " is scheduled for deletion in ~" + formatDuration(delay)
              + " (" + hours + "h). Use /wnt deletecolony " + quoteIfNeeded(colony.getName())
              + " cancel to abort.").withStyle(ChatFormatting.GOLD));
        LOGGER.info("Scheduled deletion of colony {} ('{}') in {} ms by {}",
                colony.getID(), colony.getName(), delay, requester.getName().getString());
        return delay;
    }

    /** Cancels a pending deletion. Returns true if one existed. */
    public static boolean cancel(int colonyId) {
        PendingDeletion removed = PENDING.remove(colonyId);
        if (removed != null) {
            save();
            LOGGER.info("Cancelled pending deletion of colony {}", colonyId);
            return true;
        }
        return false;
    }

    /** Returns the pending deletion for a colony, or null. */
    public static PendingDeletion getPending(int colonyId) {
        return PENDING.get(colonyId);
    }

    // ---------------------------------------------------------------- tick / execute

    /** Called periodically; executes any due deletions. */
    public static void tick(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) {
            return;
        }
        long now = nowPlus(server, 0);
        List<Integer> due = new ArrayList<>();
        for (PendingDeletion p : PENDING.values()) {
            if (p.executeAt <= now) {
                due.add(p.colonyId);
            }
        }
        for (int colonyId : due) {
            PendingDeletion p = PENDING.get(colonyId);
            if (p == null) {
                continue;
            }
            // Safety: if the colony changed owner since scheduling, the deletion is stale — drop it.
            IColony colony = findColony(colonyId, server);
            if (colony != null && p.requestedBy != null) {
                UUID owner = colony.getPermissions() != null ? colony.getPermissions().getOwner() : null;
                if (owner != null && !owner.toString().equals(p.requestedBy)) {
                    LOGGER.info("Skipping stale deletion of colony {} — owner changed since scheduling.", colonyId);
                    PENDING.remove(colonyId);
                    save();
                    continue;
                }
            }
            performDeletion(colonyId, p.dimension, server);
            PENDING.remove(colonyId);
            save();
        }
    }

    private static boolean performDeletion(int colonyId, String dimension, MinecraftServer server) {
        try {
            IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
            ServerLevel level = resolveLevel(dimension, server);
            if (level == null) {
                // Fall back to dimension-based deletion if we couldn't resolve the level.
                ResourceKey<Level> key = dimensionKey(dimension);
                if (key != null) {
                    mgr.deleteColonyByDimension(colonyId, false, key);
                    LOGGER.info("Deleted colony {} via dimension key {}", colonyId, dimension);
                    return true;
                }
                LOGGER.warn("Could not resolve level/dimension '{}' to delete colony {}", dimension, colonyId);
                return false;
            }
            mgr.deleteColonyByWorld(colonyId, false, level);
            LOGGER.info("Deleted colony {} in dimension {}", colonyId, dimension);
            return true;
        } catch (Throwable t) {
            LOGGER.error("Failed to delete colony {}: {}", colonyId, t.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------- scaled delay

    /**
     * Computes the deletion delay based on how developed the colony is.
     * @return delay in milliseconds (0 = instant)
     */
    public static long computeScaledDelayMillis(IColony colony) {
        if (colony == null) {
            return 0;
        }
        java.util.Collection<IBuilding> buildings = ColonyBuildingUtil.getBuildings(colony);
        int total = buildings.size();
        int guardTowers = 0;
        int townHallLevel = 0;
        int nonTownHall = 0;
        for (IBuilding b : buildings) {
            if (isTownHall(b)) {
                townHallLevel = Math.max(townHallLevel, safeLevel(b));
            } else {
                nonTownHall++;
                if (isGuardTower(b)) {
                    guardTowers++;
                }
            }
        }

        // No town hall at all, or a town hall that is only a placed block (level 0) with nothing
        // else built → instant.
        if (!colony.hasTownHall() || total == 0 || (nonTownHall == 0 && townHallLevel <= 0)) {
            return 0;
        }
        // Town hall built but no other buildings → 1 day.
        if (nonTownHall == 0) {
            return ONE_DAY_MS;
        }
        // A developed colony (multiple other buildings) defended by guard towers → 7 days.
        int minGuardTowers = Math.max(1, TaxConfig.getMinGuardTowersToBeRaided());
        if (nonTownHall >= 2 && guardTowers >= minGuardTowers) {
            return 7L * ONE_DAY_MS;
        }
        // At least one other building → 3 days.
        return 3L * ONE_DAY_MS;
    }

    private static boolean isTownHall(IBuilding building) {
        if (building == null) return false;
        String name = building.getBuildingDisplayName();
        if (name != null && name.toLowerCase().contains("town hall")) return true;
        return building.getClass().getName().toLowerCase().contains("townhall");
    }

    /** Guard-tower detection mirroring ColonyEventListener#isGuardTower. */
    private static boolean isGuardTower(IBuilding building) {
        if (building == null) return false;
        String displayName = building.getBuildingDisplayName();
        if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) return true;
        String className = building.getClass().getName().toLowerCase();
        if (className.contains("guardtower")) return true;
        try {
            String s = building.toString().toLowerCase();
            if (s.contains("guardtower") || s.contains("guard_tower")) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static int safeLevel(IBuilding b) {
        try {
            return b.getBuildingLevel();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static long nowPlus(MinecraftServer server, long addMs) {
        // Wall-clock is fine here (day-scale timers); System.currentTimeMillis mirrors other managers.
        return System.currentTimeMillis() + addMs;
    }

    private static IColony findColony(int colonyId, MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                IColony c = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId, level);
                if (c != null) return c;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ServerLevel resolveLevel(String dimension, MinecraftServer server) {
        ResourceKey<Level> key = dimensionKey(dimension);
        if (key == null || server == null) return null;
        return server.getLevel(key);
    }

    private static ResourceKey<Level> dimensionKey(String dimension) {
        if (dimension == null) return null;
        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
    }

    private static String quoteIfNeeded(String name) {
        return name != null && name.contains(" ") ? "\"" + name + "\"" : name;
    }

    public static String formatDuration(long ms) {
        long totalMinutes = ms / 60000L;
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes % (60 * 24)) / 60;
        long minutes = totalMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || sb.length() == 0) sb.append(minutes).append("m");
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- persistence

    public static void load() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<Integer, PendingDeletion>>() {}.getType();
            Map<Integer, PendingDeletion> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                PENDING.clear();
                PENDING.putAll(loaded);
                LOGGER.info("Loaded {} pending colony deletion(s)", PENDING.size());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load pending colony deletions", e);
        }
    }

    public static void save() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(PENDING, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save pending colony deletions", e);
        }
    }
}
