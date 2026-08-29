package net.machiavelli.minecolonytax.permissions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures the MineColonies Hostile rank permissionData before WNT modifies it,
 * then restores the exact original value when the conflict ends.
 *
 * This ensures war/raid end returns the Hostile rank to its pre-conflict state rather
 * than blindly clearing bits. Admin-configured Hostile rank permissions are preserved.
 * Snapshots are persisted to disk so they survive server crashes.
 *
 * Usage pattern:
 *   1. Call snapshotBefore(colony) BEFORE any setPermission(hostile, action, true) call.
 *   2. Call restoreIfNoConflict(colony) AFTER removing the conflict from its active-set
 *      (i.e., after ACTIVE_WARS.remove() or activeRaids.remove()).
 *   3. On server start: call loadFromFile(), then restoreAllStale() after wars/raids reload.
 */
public class PermissionSnapshot {

    private static final Logger LOGGER = LogManager.getLogger(PermissionSnapshot.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SNAPSHOT_FILE = Paths.get("config", "warntax", "permission_snapshots.json");

    /** colonyId -> Hostile rank permissionData captured before the first WNT modification */
    private static final Map<Integer, Long> SNAPSHOTS = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Snapshot capture
    // -----------------------------------------------------------------------

    /**
     * Records the Hostile rank's current permissionData for {@code colony} if no
     * snapshot is already held for it. Call this BEFORE setting any conflict-time
     * permissions on the Hostile rank.
     *
     * Safe to call multiple times for the same colony — subsequent calls are no-ops.
     */
    public static void snapshotBefore(IColony colony) {
        if (colony == null) return;
        int colonyId = colony.getID();
        if (SNAPSHOTS.containsKey(colonyId)) return; // already snapshotted
        try {
            Rank hostile = colony.getPermissions().getRankHostile();
            if (hostile == null) return;
            long data = hostile.getPermissions();
            SNAPSHOTS.put(colonyId, data);
            if (TaxConfig.isNormalLogging())
                LOGGER.info("[PermSnapshot] Captured Hostile rank snapshot for colony '{}' (id={}) data={}",
                        colony.getName(), colonyId, data);
            saveToFile();
        } catch (Exception e) {
            LOGGER.error("[PermSnapshot] Failed to snapshot Hostile rank for colony {}", colonyId, e);
        }
    }

    // -----------------------------------------------------------------------
    // Restore
    // -----------------------------------------------------------------------

    /**
     * If no active war or raid still involves {@code colony}, restores the Hostile rank
     * permissionData to the snapshotted value and clears the snapshot entry.
     *
     * MUST be called AFTER the conflict has been removed from its active-set
     * (after ACTIVE_WARS.remove() or activeRaids.remove()), otherwise the conflict
     * check will see the colony as still active and skip the restore.
     */
    public static void restoreIfNoConflict(IColony colony) {
        if (colony == null) return;
        int colonyId = colony.getID();
        if (!SNAPSHOTS.containsKey(colonyId)) return;
        if (hasActiveConflict(colonyId)) return;
        doRestore(colony);
    }

    /**
     * Unconditionally restores the snapshot for {@code colony} to its pre-conflict state
     * and removes the snapshot entry. No-op if no snapshot is held.
     */
    static void doRestore(IColony colony) {
        int colonyId = colony.getID();
        Long snapshot = SNAPSHOTS.remove(colonyId);
        if (snapshot == null) return;

        try {
            IPermissions perms = colony.getPermissions();
            Rank hostile = perms.getRankHostile();
            if (hostile == null) {
                LOGGER.warn("[PermSnapshot] Colony '{}' (id={}) has no Hostile rank — cannot restore snapshot",
                        colony.getName(), colonyId);
                saveToFile();
                return;
            }

            // Restore only WNT-managed actions so unrelated bits are never touched
            for (Action action : getManagedActions()) {
                boolean wasSet = (snapshot & action.getFlag()) != 0;
                perms.setPermission(hostile, action, wasSet);
            }

            if (TaxConfig.isNormalLogging())
                LOGGER.info("[PermSnapshot] Restored Hostile rank for colony '{}' (id={}) to pre-conflict state (snapshot={})",
                        colony.getName(), colonyId, snapshot);
            saveToFile();
        } catch (Exception e) {
            LOGGER.error("[PermSnapshot] Failed to restore snapshot for colony {}", colonyId, e);
        }
    }

    // -----------------------------------------------------------------------
    // Startup cleanup
    // -----------------------------------------------------------------------

    /**
     * For every colony with a persisted snapshot that has no active conflict, restores
     * the Hostile rank to its pre-conflict state. Call this AFTER active wars/raids have
     * been reloaded so colonies still in conflict are correctly skipped.
     *
     * Handles the case where the server crashed during an active conflict, leaving the
     * snapshot file on disk but the conflict no longer active on the next restart.
     */
    public static void restoreAllStale(MinecraftServer server) {
        if (SNAPSHOTS.isEmpty()) return;
        try {
            int restored = 0;
            for (Level level : server.getAllLevels()) {
                for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
                    if (colony == null) continue;
                    int colonyId = colony.getID();
                    if (SNAPSHOTS.containsKey(colonyId) && !hasActiveConflict(colonyId)) {
                        doRestore(colony);
                        restored++;
                    }
                }
            }
            if (restored > 0)
                LOGGER.info("[PermSnapshot] Restored Hostile rank for {} stale-snapshot colonies on startup", restored);
            else if (TaxConfig.isNormalLogging())
                LOGGER.info("[PermSnapshot] No stale permission snapshots required restoration on startup");
        } catch (Exception e) {
            LOGGER.error("[PermSnapshot] Error during startup snapshot restoration", e);
        }
    }

    // -----------------------------------------------------------------------
    // Conflict detection
    // -----------------------------------------------------------------------

    private static boolean hasActiveConflict(int colonyId) {
        // Check active wars (colony can be defender or attacker)
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (war.getColony() != null && war.getColony().getID() == colonyId) return true;
            if (war.getAttackerColony() != null && war.getAttackerColony().getID() == colonyId) return true;
        }
        // Check active player raids (colony is the raid target)
        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
            if (raid == null) continue;
            if (raid.getColony() != null && raid.getColony().getID() == colonyId) return true;
        }
        // Check active besieges (colony is besieged). Besiege shares the Hostile-rank machinery,
        // so without this an ending war/raid on the same colony would restore (clear) the
        // besiege's Hostile nodes and consume its snapshot, silently neutering the live siege.
        if (net.machiavelli.minecolonytax.besiege.BesiegeManager.isActiveRaidOnColony(colonyId)) {
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    /**
     * Loads persisted snapshots from disk. Call this early in server startup, BEFORE
     * restoreAllColonyPermissionsToDefaults() and loadAndResumeActiveWars(), so that
     * existing snapshots are available when snapshotBefore() is called during war restore.
     */
    public static void loadFromFile() {
        if (!Files.exists(SNAPSHOT_FILE)) return;
        try (Reader reader = new FileReader(SNAPSHOT_FILE.toFile())) {
            Type type = new TypeToken<Map<Integer, Long>>() {}.getType();
            Map<Integer, Long> loaded = GSON.fromJson(reader, type);
            if (loaded != null && !loaded.isEmpty()) {
                SNAPSHOTS.clear();
                SNAPSHOTS.putAll(loaded);
                if (TaxConfig.isNormalLogging())
                    LOGGER.info("[PermSnapshot] Loaded {} permission snapshots from disk", SNAPSHOTS.size());
            }
        } catch (Exception e) {
            LOGGER.error("[PermSnapshot] Failed to load permission snapshots from {}", SNAPSHOT_FILE, e);
        }
    }

    private static void saveToFile() {
        try {
            Files.createDirectories(SNAPSHOT_FILE.getParent());
            net.machiavelli.minecolonytax.util.SafeFileIO.writeStringAtomic(SNAPSHOT_FILE.toFile(), GSON.toJson(new HashMap<>(SNAPSHOTS)));
        } catch (Exception e) {
            LOGGER.error("[PermSnapshot] Failed to save permission snapshots to {}", SNAPSHOT_FILE, e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** The set of Actions that WNT may grant on the Hostile rank during conflicts. */
    private static Set<Action> getManagedActions() {
        Set<Action> actions = new HashSet<>();
        actions.addAll(TaxConfig.getWarActions());
        actions.addAll(TaxConfig.getRaidActions());
        return actions;
    }

    /** Returns the current number of held snapshots (for diagnostics). */
    public static int snapshotCount() {
        return SNAPSHOTS.size();
    }
}
