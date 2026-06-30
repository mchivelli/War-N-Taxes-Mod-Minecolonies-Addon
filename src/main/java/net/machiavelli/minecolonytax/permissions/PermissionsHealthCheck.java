package net.machiavelli.minecolonytax.permissions;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scans all colonies for permission state that should not persist after wars, raids, or
 * besiegements end.
 *
 * What it fixes:
 *   1. Players stuck in the Hostile rank on a colony they are no longer at war/raid with
 *      are demoted back to Neutral.
 *   2. Hostile rank permission nodes that should be disabled (no active conflict) are
 *      cleared.
 *   3. Neutral rank combat nodes left over from a claiming raid are cleared.
 *
 * Run on server start (deferred, after war restoration) and on demand via /wnt permcheck.
 *
 * API notes from MineColonies source audit:
 *  - getPlayers()      returns Collections.unmodifiableMap(players) — live view, not a copy.
 *                      We snapshot keySet to a List before iterating to avoid iterator issues
 *                      if any internal structural changes occur during setPlayerRank().
 *  - getRankNeutral()  returns ranks.get(NEUTRAL_RANK_ID) — can return null if the colony's
 *                      rank map is corrupted. Null-checked before use.
 *  - getRankHostile()  same — can return null. Null-checked before use.
 *  - getRank(UUID)     returns @NotNull; falls back to Neutral rank for unknown players.
 *  - setPlayerRank()   uses world.getServer() unconditionally — NPE if world is null.
 *                      Guarded by colony.getWorld() != null check before calling.
 *  - hasPermission()   never throws; pure bitwise flag test.
 *  - setPermission()   never throws; pure bitwise flag mutation.
 *  - All operations are single-threaded (Minecraft server thread) — no concurrency concern.
 */
public class PermissionsHealthCheck {

    private static final Logger LOGGER = LogManager.getLogger(PermissionsHealthCheck.class);

    // Neutral rank combat actions granted during claiming raids that must always be
    // cleared after the raid ends. Kept in sync with restoreClaimingPermissionsToDefaults().
    private static final Action[] CLAIMING_ATTACK_ACTIONS = {
        Action.HURT_CITIZEN,
        Action.ATTACK_CITIZEN,
        Action.HURT_VISITOR,
        Action.ATTACK_ENTITY,
        Action.SHOOT_ARROW,
        Action.THROW_POTION,
        Action.RIGHTCLICK_ENTITY,
        Action.FILL_BUCKET
    };

    // ──────────────────────────────────────────────────────────────────────────
    // Result
    // ──────────────────────────────────────────────────────────────────────────

    public static final class Result {
        public final int coloniesScanned;
        public final int strayPlayersRestored;
        public final int unexpectedPermissionsFixed;

        Result(int coloniesScanned, int strayPlayersRestored, int unexpectedPermissionsFixed) {
            this.coloniesScanned = coloniesScanned;
            this.strayPlayersRestored = strayPlayersRestored;
            this.unexpectedPermissionsFixed = unexpectedPermissionsFixed;
        }

        public boolean hasIssues() {
            return strayPlayersRestored > 0 || unexpectedPermissionsFixed > 0;
        }

        public String summary() {
            if (!hasIssues()) {
                return coloniesScanned + " colonies scanned — no stale hostile permissions found.";
            }
            return coloniesScanned + " colonies scanned — "
                    + strayPlayersRestored + " stray hostile player rank(s) restored to neutral, "
                    + unexpectedPermissionsFixed + " unexpected permission node(s) cleared.";
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Run the full health check against all loaded colonies on this server.
     */
    public static Result run(MinecraftServer server) {
        int coloniesScanned = 0;
        int strayPlayersRestored = 0;
        int unexpectedPermissionsFixed = 0;

        for (Level level : server.getAllLevels()) {
            for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getColonies(level)) {
                if (colony == null) continue;
                coloniesScanned++;
                try {
                    strayPlayersRestored      += demoteStrayHostilePlayers(colony);
                    unexpectedPermissionsFixed += clearUnexpectedPermissionNodes(colony);
                } catch (Exception e) {
                    LOGGER.warn("[PermissionsHealthCheck] Error scanning colony {} (id={}): {}",
                            colony.getName(), colony.getID(), e.getMessage());
                }
            }
        }

        Result result = new Result(coloniesScanned, strayPlayersRestored, unexpectedPermissionsFixed);
        if (result.hasIssues()) {
            LOGGER.warn("[PermissionsHealthCheck] {}", result.summary());
        } else {
            LOGGER.info("[PermissionsHealthCheck] {}", result.summary());
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Stray player demotion
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Demotes any player in the Hostile rank on {@code colony} who is not a legitimate
     * participant in an active war or raid against that colony.
     *
     * Scans every player with any explicit rank stored in the colony (Officers, Friends,
     * Neutrals, Hostile). getRankNeutral() null-checked — if Neutral rank is missing the
     * colony's permission data is corrupted and we skip rather than crash.
     *
     * @return Number of players demoted.
     */
    private static int demoteStrayHostilePlayers(IColony colony) {
        IPermissions perms = colony.getPermissions();

        // Null-guard: getRankNeutral() returns ranks.get(NEUTRAL_RANK_ID) which can be null
        // if the colony's rank map is corrupted (e.g. partially written save).
        Rank neutral = perms.getRankNeutral();
        if (neutral == null) {
            LOGGER.warn("[PermissionsHealthCheck] Colony {} (id={}) has no Neutral rank — skipping stray player demotion. Colony permission data may be corrupted.",
                    colony.getName(), colony.getID());
            return 0;
        }

        UUID colonyOwner = perms.getOwner();
        int fixed = 0;

        // Snapshot keySet to a new List before iterating.
        // getPlayers() returns an unmodifiable *live view* backed by the internal HashMap.
        // Snapshotting ensures the iterator is stable even if setPlayerRank() causes any
        // internal structural changes (e.g. adding a GameProfile cache entry).
        List<UUID> playerIds = new ArrayList<>(perms.getPlayers().keySet());

        for (UUID playerUUID : playerIds) {
            Rank rank = perms.getRank(playerUUID);
            // getRank() returns @NotNull (falls back to Neutral for unknowns) but we
            // defensively null-check, then skip anyone not currently in Hostile rank.
            if (rank == null || !rank.isHostile()) continue;

            // The colony owner should never be in Hostile rank on their own colony.
            // If they are, something is seriously wrong — log it but do not auto-fix,
            // since changing the owner's rank could break colony management.
            if (playerUUID.equals(colonyOwner)) {
                LOGGER.warn("[PermissionsHealthCheck] Colony owner {} is in Hostile rank on their own colony {} (id={}) — investigate manually. Will not auto-demote.",
                        playerUUID, colony.getName(), colony.getID());
                continue;
            }

            // If they're a tracked participant in an active war or raid, leave them alone.
            if (isLegitimatelyHostile(playerUUID, colony)) continue;

            // setPlayerRank() requires a non-null Level (uses world.getServer() internally).
            if (colony.getWorld() == null) {
                LOGGER.warn("[PermissionsHealthCheck] Cannot demote player {} on colony {} — world is null.",
                        playerUUID, colony.getName());
                continue;
            }

            try {
                perms.setPlayerRank(playerUUID, neutral, colony.getWorld());
                LOGGER.warn("[PermissionsHealthCheck] Demoted stray hostile player {} → neutral on colony {} (id={}).",
                        playerUUID, colony.getName(), colony.getID());
                fixed++;
            } catch (Exception e) {
                LOGGER.warn("[PermissionsHealthCheck] Failed to demote player {} on colony {} (id={}): {}",
                        playerUUID, colony.getName(), colony.getID(), e.getMessage());
            }
        }
        return fixed;
    }

    /**
     * Returns true if the player should currently be in the Hostile rank on this colony
     * because of an ongoing war or raid.
     */
    private static boolean isLegitimatelyHostile(UUID playerUUID, IColony colony) {
        int colonyId = colony.getID();

        // Defender colony: attackers are assigned Hostile here
        WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
        if (war != null) {
            if (war.getAttackerLives() != null && war.getAttackerLives().containsKey(playerUUID)) return true;
            if (war.getDefenderLives() != null && war.getDefenderLives().containsKey(playerUUID)) return true;
        }

        // Attacker colony: defenders are assigned Hostile here
        for (WarData activeWar : WarSystem.ACTIVE_WARS.values()) {
            if (activeWar.getAttackerColony() != null
                    && activeWar.getAttackerColony().getID() == colonyId) {
                if (activeWar.getDefenderLives() != null
                        && activeWar.getDefenderLives().containsKey(playerUUID)) return true;
            }
        }

        // Player raid: the raider is assigned Hostile on the target colony
        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
            if (raid.isActive()
                    && raid.getColony() != null
                    && raid.getColony().getID() == colonyId
                    && raid.getRaider().equals(playerUUID)) {
                return true;
            }
        }

        // Active besiege: the besieging player and their registered allies are assigned Hostile
        // on the besieged colony. Without this they'd be demoted to Neutral mid-siege.
        if (net.machiavelli.minecolonytax.besiege.BesiegeManager.isBesiegeCombatant(playerUUID, colonyId)) {
            return true;
        }

        // Claiming raids do not assign Hostile rank — they use Neutral rank attack nodes instead.
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Unexpected permission node cleanup
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that Hostile and Neutral rank permission nodes match the colony's current
     * conflict state, clearing anything that should not be enabled.
     *
     * Hostile rank : all nodes must be false unless a war or player-raid is active.
     * Neutral rank : CLAIMING_ATTACK_ACTIONS must be false unless a claiming raid is active.
     *
     * Both getRankHostile() and getRankNeutral() can return null on corrupted colony data;
     * each section is independently null-guarded so a missing rank skips only that section.
     *
     * @return Number of permission nodes cleared.
     */
    private static int clearUnexpectedPermissionNodes(IColony colony) {
        int colonyId = colony.getID();
        IPermissions perms = colony.getPermissions();
        int fixed = 0;

        // --- Hostile rank: WNT-managed nodes must be off when no war or player-raid is active ---
        // Only WNT-managed actions are checked — other bits on the Hostile rank may have been
        // intentionally configured by the server admin and must not be cleared.
        boolean warOrRaidActive = WarSystem.ACTIVE_WARS.containsKey(colonyId)
                || RaidManager.getActiveRaidForColony(colonyId) != null
                || net.machiavelli.minecolonytax.besiege.BesiegeManager.isActiveRaidOnColony(colonyId);

        if (!warOrRaidActive) {
            Rank hostile = perms.getRankHostile();
            if (hostile == null) {
                LOGGER.warn("[PermissionsHealthCheck] Colony {} (id={}) has no Hostile rank — skipping hostile node cleanup. Permission data may be corrupted.",
                        colony.getName(), colonyId);
            } else {
                // Build the union of war + raid actions that WNT manages
                java.util.Set<Action> managedActions = new java.util.HashSet<>();
                managedActions.addAll(net.machiavelli.minecolonytax.TaxConfig.getWarActions());
                managedActions.addAll(net.machiavelli.minecolonytax.TaxConfig.getRaidActions());
                for (Action action : managedActions) {
                    try {
                        if (perms.hasPermission(hostile, action)) {
                            perms.setPermission(hostile, action, false);
                            LOGGER.warn("[PermissionsHealthCheck] Cleared unexpected Hostile rank permission {} on colony {} (id={}).",
                                    action.name(), colony.getName(), colonyId);
                            fixed++;
                        }
                    } catch (Exception e) {
                        LOGGER.debug("[PermissionsHealthCheck] Could not check/clear Hostile action {} on colony {}: {}",
                                action.name(), colony.getName(), e.getMessage());
                    }
                }
            }
        }

        // --- Neutral rank: claiming-raid attack nodes must be off when no claiming raid active ---
        boolean claimingRaidActive = ColonyClaimingRaidManager.isColonyBeingClaimed(colonyId);

        if (!claimingRaidActive) {
            Rank neutral = perms.getRankNeutral();
            if (neutral == null) {
                LOGGER.warn("[PermissionsHealthCheck] Colony {} (id={}) has no Neutral rank — skipping neutral node cleanup.",
                        colony.getName(), colonyId);
            } else {
                for (Action action : CLAIMING_ATTACK_ACTIONS) {
                    try {
                        if (perms.hasPermission(neutral, action)) {
                            perms.setPermission(neutral, action, false);
                            LOGGER.warn("[PermissionsHealthCheck] Cleared unexpected Neutral rank permission {} on colony {} (id={}).",
                                    action.name(), colony.getName(), colonyId);
                            fixed++;
                        }
                    } catch (Exception e) {
                        LOGGER.debug("[PermissionsHealthCheck] Could not check/clear Neutral action {} on colony {}: {}",
                                action.name(), colony.getName(), e.getMessage());
                    }
                }
            }
        }

        return fixed;
    }
}
