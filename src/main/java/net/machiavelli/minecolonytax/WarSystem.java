package net.machiavelli.minecolonytax;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.compat.FtbTeamsCompat;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.event.WarEconomyHandler;
import net.machiavelli.minecolonytax.event.WarEventHandler;
import net.machiavelli.minecolonytax.event.WarVictoryEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import net.machiavelli.minecolonytax.util.TickScheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class WarSystem {

    private static final Logger WARSYSTEM_LOGGER = LogManager.getLogger(WarSystem.class);
    public static final Map<Integer, Object> pendingWarRequests = new ConcurrentHashMap<>();

    // Track extortion immunity (colonyId -> immunity expiration timestamp)
    private static final Map<Integer, Long> extortionImmunity = new ConcurrentHashMap<>();

    public record WarRequest(UUID attacker, int colonyId) {
    }

    public record WarRequestWithExtortion(UUID attacker, int colonyId, int extortionPercent) {
    }

    private static boolean isOfficerOrFriendly(IColony colony, UUID playerUUID) {
        if (colony == null || playerUUID == null) {
            return false;
        }

        Rank rank = colony.getPermissions().getRank(playerUUID);
        if (rank == null) {
            return false;
        }

        // isColonyManager checks for officers, and !rank.isHostile() includes any
        // friendly non-enemy rank.
        return rank.isColonyManager() || !rank.isHostile();
    }

    /**
     * Legacy boolean flag — preserved for external callers that read it directly.
     * Routes through {@link FtbTeamsCompat#isInstalled()} which is classloader-safe.
     * Do NOT add new typed FTB Teams statics here — use {@link FtbTeamsCompat}.
     */
    public static final boolean FTB_TEAMS_INSTALLED = FtbTeamsCompat.isInstalled();
    public static final Map<Integer, WarData> ACTIVE_WARS = new ConcurrentHashMap<>();

    private static final Component JOIN_MSG = Component.literal("[Join War]")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt joinwar"))
                    .withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to join the war!")
                                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)))));

    private static final Component LEAVE_MSG = Component.literal("[Leave War]")
            .withStyle(style -> style.withColor(ChatFormatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt leavewar"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to leave the war!").withStyle(ChatFormatting.AQUA))));

    public static final long WAR_PHASE_DURATION_SECONDS = 60;

    public static void initiateWar(ServerPlayer attacker, UUID defender,
            FtbTeamsCompat.TeamHandle attackerTeam, FtbTeamsCompat.TeamHandle defenderTeam,
            IColony colony, IColony attackerColony) {
        UUID attackerTeamID = (FTB_TEAMS_INSTALLED && attackerTeam != null)
                ? FtbTeamsCompat.getTeamId(attackerTeam) : attacker.getUUID();
        UUID defenderTeamID = (FTB_TEAMS_INSTALLED && defenderTeam != null)
                ? FtbTeamsCompat.getTeamId(defenderTeam)
                : colony.getPermissions().getOwner();

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("War for " + colony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bossEvent.setProgress(1.0f);
        bossEvent.setVisible(true);

        long now = System.currentTimeMillis();
        WarData data = new WarData(attacker.getUUID(), defender, attackerTeamID, defenderTeamID, now, bossEvent, colony,
                attackerColony);

        int playerLives = TaxConfig.PLAYER_LIVES_IN_WAR.get(); // Use config

        data.getAttackerLives().put(attacker.getUUID(), playerLives);
        data.getDefenderLives().put(colony.getPermissions().getOwner(), playerLives);

        // Assign hostile rank to the main attacker on defender's colony
        assignWarParticipantRanks(attacker.getUUID(), colony, attackerColony, true);

        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Adding attacker colony members from " + attackerColony.getName());

            attackerPerms.getPlayers().forEach((uuid, player) -> {
                if (!uuid.equals(attacker.getUUID())) { // Don't add attacker twice
                    Rank rank = attackerPerms.getRank(uuid);
                    if (rank != null && (rank.equals(attackerPerms.getRankOfficer())
                            || rank.equals(attackerPerms.getRankFriend()))) {
                        data.getAttackerLives().put(uuid, playerLives);
                        if (TaxConfig.isDebugLogging())
                            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                                    "[DEBUG] Added attacker colony member " + uuid + " with rank " + rank.getName());

                        // Assign hostile rank to this attacker on defender's colony
                        assignWarParticipantRanks(uuid, colony, attackerColony, true);

                        // Send comprehensive join prompt to eligible players
                        if (colony.getWorld() != null) {
                            MinecraftServer server = colony.getWorld().getServer();
                            if (server != null) {
                                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                                if (p != null && p.isAlive()) {
                                    Component warNotification = Component.literal("⚔️ WAR DECLARED ⚔️")
                                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                            .append(Component
                                                    .literal("\nYour colony (" + attackerColony.getName()
                                                            + ") is attacking " + colony.getName() + "!")
                                                    .withStyle(ChatFormatting.YELLOW))
                                            .append(Component
                                                    .literal("\nAs an " + rank.getName()
                                                            + ", you are eligible to join as an ATTACKER!")
                                                    .withStyle(ChatFormatting.GREEN))
                                            .append(Component.literal("\nClick below to join the war:")
                                                    .withStyle(ChatFormatting.AQUA));
                                    p.sendSystemMessage(warNotification);
                                    p.sendSystemMessage(JOIN_MSG);
                                }
                            }
                        }
                    }
                }
            });
        }

        IPermissions defenderPerms = colony.getPermissions();
        if (TaxConfig.isDebugLogging())
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Adding defender colony members from " + colony.getName());

        defenderPerms.getPlayers().forEach((uuid, player) -> {
            if (!uuid.equals(colony.getPermissions().getOwner())) { // Don't add owner twice
                Rank rank = defenderPerms.getRank(uuid);
                if (rank != null && (rank.equals(defenderPerms.getRankOfficer())
                        || rank.equals(defenderPerms.getRankFriend()))) {
                    data.getDefenderLives().put(uuid, playerLives);
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                                "[DEBUG] Added defender colony member " + uuid + " with rank " + rank.getName());

                    // Assign hostile rank to this defender on attacker's colony (if it exists)
                    assignWarParticipantRanks(uuid, colony, attackerColony, false);

                    // Send comprehensive join prompt to eligible players
                    if (colony.getWorld() != null) {
                        MinecraftServer server = colony.getWorld().getServer();
                        if (server != null) {
                            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                Component warNotification = Component.literal("🛡️ COLONY UNDER ATTACK 🛡️")
                                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                                        .append(Component
                                                .literal("\nYour colony (" + colony.getName() + ") is being attacked!")
                                                .withStyle(ChatFormatting.RED))
                                        .append(Component
                                                .literal("\nAs an " + rank.getName()
                                                        + ", you are eligible to join as a DEFENDER!")
                                                .withStyle(ChatFormatting.GREEN))
                                        .append(Component.literal("\nClick below to join the defense:")
                                                .withStyle(ChatFormatting.AQUA));
                                p.sendSystemMessage(warNotification);
                                p.sendSystemMessage(JOIN_MSG);
                            }
                        }
                    }
                }
            }
        });

        if (FTB_TEAMS_INSTALLED) {
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] FTB Teams detected, adding team members as additional participants");

            if (attackerTeam != null) {
                FtbTeamsCompat.getTeamMembers(attackerTeam).forEach(uuid -> {
                    if (!data.getAttackerLives().containsKey(uuid)) { // Don't add if already added via colony
                        data.getAttackerLives().put(uuid, playerLives);
                        if (TaxConfig.isDebugLogging())
                            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Added FTB team member to attackers: " + uuid);

                        // Assign hostile rank to this attacker on defender's colony
                        assignWarParticipantRanks(uuid, colony, attackerColony, true);

                        // Send comprehensive join prompt
                        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                            ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                Component teamWarNotification = Component.literal("⚔️ TEAM WAR DECLARED ⚔️")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                        .append(Component.literal("\nYour team is attacking " + colony.getName() + "!")
                                                .withStyle(ChatFormatting.YELLOW))
                                        .append(Component
                                                .literal("\nAs a team member, you are eligible to join as an ATTACKER!")
                                                .withStyle(ChatFormatting.GREEN))
                                        .append(Component.literal("\nClick below to join the war:")
                                                .withStyle(ChatFormatting.AQUA));
                                p.sendSystemMessage(teamWarNotification);
                                p.sendSystemMessage(JOIN_MSG);
                            }
                        }
                    }
                });
            }

            if (defenderTeam != null) {
                FtbTeamsCompat.getTeamMembers(defenderTeam).forEach(uuid -> {
                    if (!data.getDefenderLives().containsKey(uuid)) { // Don't add if already added via colony
                        data.getDefenderLives().put(uuid, playerLives);
                        if (TaxConfig.isDebugLogging())
                            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Added FTB team member to defenders: " + uuid);

                        // Assign hostile rank to this defender on attacker's colony (if it exists)
                        assignWarParticipantRanks(uuid, colony, attackerColony, false);

                        // Send comprehensive join prompt
                        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                            ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                Component teamDefenseNotification = Component
                                        .literal("🛡️ TEAM COLONY UNDER ATTACK 🛡️")
                                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                                        .append(Component
                                                .literal("\nYour team's colony (" + colony.getName()
                                                        + ") is being attacked!")
                                                .withStyle(ChatFormatting.RED))
                                        .append(Component
                                                .literal("\nAs a team member, you are eligible to join as a DEFENDER!")
                                                .withStyle(ChatFormatting.GREEN))
                                        .append(Component.literal("\nClick below to join the defense:")
                                                .withStyle(ChatFormatting.AQUA));
                                p.sendSystemMessage(teamDefenseNotification);
                                p.sendSystemMessage(JOIN_MSG);
                            }
                        }
                    }
                });
            }
        }

        data.initialAttackerTotalLives = data.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
        data.initialDefenderTotalLives = data.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
        ACTIVE_WARS.put(colony.getID(), data);

        // Mark defender for home-field drain advantage
        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(colony.getID());

        // Schedule treasury drain every 60 seconds for both sides
        scheduleTreasuryDrain(data, colony, attackerColony);

        // Apply War Exhaustion - both colonies generate less tax during war
        net.machiavelli.minecolonytax.economy.WarExhaustionManager.applyWarStatus(colony.getID());
        if (attackerColony != null) {
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.applyWarStatus(attackerColony.getID());
        }

        // Record war declaration in DB
        net.machiavelli.minecolonytax.db.WarStatsDB.recordWarDeclared(
                attacker.getUUID(), attacker.getName().getString(), defender);
    }

    /**
     * Schedule a repeating treasury drain task for both attacker and defender.
     * Drains every 60 seconds; also does a periodic save every 5 minutes.
     */
    private static void scheduleTreasuryDrain(WarData data, IColony defenderColony, IColony attackerColony) {
        if (!TaxConfig.isTreasuryEnabled()) return;

        final int defenderColonyId = defenderColony.getID();
        final int attackerColonyId = attackerColony != null ? attackerColony.getID() : -1;
        final long[] tickCount = {0}; // mutable counter for periodic save

        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
            // "Still my war" identity guard (same pattern as checkForVictory /
            // handleTimeExpiry). A drain task that outlives its war must not keep
            // draining treasury — and must never end the *replacement* war that a
            // later declaration registered under the same defender colony id.
            if (ACTIVE_WARS.get(defenderColonyId) != data) {
                TickScheduler.cancel(data.warChestDrainTaskId);
                data.warChestDrainTaskId = -1;
                return;
            }

            tickCount[0]++;

            int defenderResult = net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(defenderColonyId);
            int attackerResult = attackerColonyId >= 0
                    ? net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(attackerColonyId)
                    : Integer.MAX_VALUE;

            // Periodic save every 5 drain ticks (5 minutes) — async + coalesced now,
            // so concurrent wars no longer each fire a synchronous treasury write (audit H2).
            if (tickCount[0] % 5 == 0) {
                net.machiavelli.minecolonytax.economy.TreasuryManager.save();
            }

            // Auto-surrender if either side is depleted.
            //
            // ACTIVE_WARS is keyed by the DEFENDER colony id, and endWar() resolves the
            // war via ACTIVE_WARS.remove(colony.getID()). Passing the ATTACKER colony
            // here (the old behaviour when the attacker ran dry) therefore did not end
            // this war at all: it either found nothing and returned early — leaving the
            // war running and re-firing this branch every 60s forever — or, if the
            // attacker happened to be the defender of a *different* war, it ended that
            // unrelated war instead. Always end the war this task belongs to.
            if (defenderResult == -1 || attackerResult == -1) {
                // Record WHY the war ended before endWar reads the report — otherwise a
                // depleted war chest was logged as a bare "Stalemate" with no explanation.
                if (defenderResult == -1 && attackerResult == -1) {
                    data.resolvedOutcome = "STALEMATE";
                    data.setPenaltyReport("Stalemate - both war chests ran dry; the war collapsed.");
                } else if (defenderResult == -1) {
                    data.resolvedOutcome = "ATTACKER_VICTORY";
                    data.setPenaltyReport("Surrender - " + defenderColony.getName()
                            + "'s war chest ran dry and the defenders capitulated.");
                } else {
                    data.resolvedOutcome = "DEFENDER_VICTORY";
                    data.setPenaltyReport("Surrender - the attackers' war chest ran dry; "
                            + defenderColony.getName() + " holds the field.");
                }
                endWar(defenderColony);
            }
        }, 60_000, 60_000);
    }

    public static void setWarInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
            return;
        if (allowed) {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
        }
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        for (Action a : TaxConfig.getWarActions()) {
            perms.setPermission(hostile, a, allowed);
        }
    }

    /**
     * Assigns appropriate ranks to war participants so they can interact with
     * opposing colonies during war.
     * Attackers get hostile rank on defender colony, defenders get hostile rank on
     * attacker colony.
     * 
     * @param playerUUID     The UUID of the war participant
     * @param defenderColony The defending colony
     * @param attackerColony The attacking colony (can be null)
     * @param isAttacker     True if the player is on attacking side, false if
     *                       defending
     */
    private static void assignWarParticipantRanks(UUID playerUUID, IColony defenderColony, IColony attackerColony,
            boolean isAttacker) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
            return;

        try {
            if (isAttacker) {
                // Attackers get hostile rank on defender colony
                IPermissions defenderPerms = defenderColony.getPermissions();
                defenderPerms.setPlayerRank(playerUUID, defenderPerms.getRankHostile(), defenderColony.getWorld());
                if (TaxConfig.isDebugLogging())
                    System.out
                            .println("[DEBUG] Assigned hostile rank to attacker " + playerUUID + " on defender colony "
                                    + defenderColony.getName());
            } else {
                // Defenders get hostile rank on attacker colony (if it exists)
                if (attackerColony != null) {
                    IPermissions attackerPerms = attackerColony.getPermissions();
                    attackerPerms.setPlayerRank(playerUUID, attackerPerms.getRankHostile(), attackerColony.getWorld());
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Assigned hostile rank to defender " + playerUUID
                                + " on attacker colony " + attackerColony.getName());
                }
            }
        } catch (Exception e) {
            WARSYSTEM_LOGGER.error("Failed to assign war participant ranks for player " + playerUUID, e);
        }
    }

    public static void setRaidInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
            return;
        if (allowed) {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
        }
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        for (Action a : TaxConfig.getRaidActions()) {
            perms.setPermission(hostile, a, allowed);
        }
    }

    /**
     * Restore all colonies' war, raid, and claiming permissions to their config defaults
     * (disabled).
     * Should be called on server startup to clean up any leftover permissions from
     * crashes/restarts. This includes:
     * - War actions on Hostile rank
     * - Raid actions on Hostile rank
     * - Claiming raid actions on Hostile rank (OPEN_CONTAINER etc.)
     * - Claiming raid attack permissions on Neutral rank (HURT_CITIZEN, ATTACK_CITIZEN, etc.)
     */
    public static void restoreAllColonyPermissionsToDefaults() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                WARSYSTEM_LOGGER.warn("Cannot restore colony permissions: Server not available");
                return;
            }

            int coloniesRestored = 0;
            for (Level level : server.getAllLevels()) {
                for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
                    if (colony != null) {
                        // Disable war actions on Hostile rank
                        setWarInteractionPermissions(colony, false);
                        // Disable raid actions on Hostile rank
                        setRaidInteractionPermissions(colony, false);
                        // Disable claiming raid actions on Hostile rank + Neutral rank attack perms
                        restoreClaimingPermissionsToDefaults(colony);
                        coloniesRestored++;
                    }
                }
            }

            WARSYSTEM_LOGGER.info("Restored war/raid/claiming permissions to config defaults for {} colonies", coloniesRestored);
        } catch (Exception e) {
            WARSYSTEM_LOGGER.error("Failed to restore colony permissions to defaults", e);
        }
    }

    /**
     * Revoke any leftover claiming-raid permissions from a colony.
     * Called unconditionally on startup (does not check whether claiming system is enabled,
     * because leftover permissions from a previous config must still be cleaned up).
     */
    private static void restoreClaimingPermissionsToDefaults(IColony colony) {
        try {
            IPermissions perms = colony.getPermissions();

            // Clean up Hostile rank claiming actions (OPEN_CONTAINER is not in WarActions/RaidActions)
            Rank hostile = perms.getRankHostile();
            perms.setPermission(hostile, Action.OPEN_CONTAINER, false);

            // Clean up Neutral rank attack permissions that claiming raids grant
            Rank neutral = perms.getRankNeutral();
            perms.setPermission(neutral, Action.HURT_CITIZEN, false);
            perms.setPermission(neutral, Action.ATTACK_CITIZEN, false);
            perms.setPermission(neutral, Action.HURT_VISITOR, false);
            perms.setPermission(neutral, Action.ATTACK_ENTITY, false);
            perms.setPermission(neutral, Action.SHOOT_ARROW, false);
            perms.setPermission(neutral, Action.THROW_POTION, false);
            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, false);
            perms.setPermission(neutral, Action.FILL_BUCKET, false);
        } catch (Exception e) {
            WARSYSTEM_LOGGER.debug("Error restoring claiming permissions for colony {}", colony.getID(), e);
        }
    }

    /**
     * Demote a set of players from the Hostile rank to Neutral on the given colony.
     * Called at war end to clean up rank assignments made by assignWarParticipantRanks().
     * Skips the colony owner and players who are not currently in the Hostile rank.
     */
    public static void demoteParticipantsFromHostile(IColony colony, java.util.Set<UUID> participants) {
        if (colony == null || colony.getWorld() == null || participants == null) return;
        IPermissions perms = colony.getPermissions();
        Rank neutral = perms.getRankNeutral();
        UUID colonyOwner = perms.getOwner();

        for (UUID uuid : participants) {
            if (uuid.equals(colonyOwner)) continue; // Never demote the colony owner
            try {
                Rank current = perms.getRank(uuid);
                if (current != null && current.isHostile()) {
                    perms.setPlayerRank(uuid, neutral, colony.getWorld());
                    if (TaxConfig.isDebugLogging())
                        WARSYSTEM_LOGGER.debug("Demoted {} from Hostile to Neutral on colony {} after war end.", uuid, colony.getName());
                }
            } catch (Exception e) {
                WARSYSTEM_LOGGER.warn("Failed to demote player {} from hostile rank on colony {} at war end", uuid, colony.getName(), e);
            }
        }
    }

    public static void updateBossBar(WarData war) {
        long now = System.currentTimeMillis();
        if (now < war.getJoinPhaseEndTime()) {
            long remainingMillis = war.getJoinPhaseEndTime() - now;
            String timeStr = String.format("%02d:%02d", remainingMillis / 60000, (remainingMillis / 1000) % 60);
            String joinText = Component.translatable("war.siege.status", war.getColony().getName(), timeStr)
                    .getString();
            war.bossEvent.setName(Component.literal(joinText));
            long joinDuration = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
            war.bossEvent.setProgress((float) remainingMillis / joinDuration);
            if (war.alliesBossEvent != null) {
                war.alliesBossEvent.setName(Component.literal(joinText));
                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
            }
        } else {
            long elapsedSeconds = (now - war.warStartTime) / 1000;
            long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
            long remainingSeconds = Math.max(0, warDurationSeconds - elapsedSeconds);
            int attackerLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
            int defenderLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
            String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            String warText = "§6§lWar for " + war.getColony().getName() +
                    " - Time Remaining: " + timeStr +
                    " | Attackers: " + attackerLives +
                    " | Defenders: " + defenderLives;
            war.bossEvent.setName(Component.literal(warText));
            war.bossEvent.setProgress((float) remainingSeconds / warDurationSeconds);
            if (war.alliesBossEvent != null) {
                war.alliesBossEvent.removeAllPlayers();
                war.alliesBossEvent.setVisible(false);
            }
        }
    }

    public static void finalizeWarStart(WarData war) {
        int attackerPlayerCount = war.getAttackerLives().size();
        int defenderPlayerCount = war.getDefenderLives().size();

        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component cancelMsg = Component.literal("War cancelled due to lack of participants.")
                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
                broadcastToServer(cancelMsg);
            }
            endWar(war.getColony());
            return;
        }

        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component ratioMsg = Component.literal(
                        "Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
                broadcastToServer(ratioMsg);
            }
            endWar(war.getColony()); // clean up — bare return would leak the war in ACTIVE_WARS
            return;
        }

        war.bossEvent.removeAllPlayers();
        war.getAttackerLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null)
                    war.bossEvent.addPlayer(p);
            }
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null)
                    war.bossEvent.addPlayer(p);
            }
        });

        if (war.alliesBossEvent != null) {
            war.alliesBossEvent.removeAllPlayers();
            war.alliesBossEvent.setVisible(false);
        }

        war.getAttackerLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null)
                    assignWarGroup(p);
            }
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null)
                    assignWarGroup(p);
            }
        });

        war.warStartTime = System.currentTimeMillis();
        war.setStatus(WarData.WarStatus.INWAR);
        updateBossBar(war);
        // Apply glow to both defender and attacker guards for clear visibility
        applyGuardGlow(war.getColony());
        if (war.getAttackerColony() != null) {
            applyGuardGlow(war.getAttackerColony());
        }
        applyWarGlowToParticipants(war);

        // Step 11 — when experimental objectives are enabled, give each attacker
        // a Siege Banner at INWAR transition. They can plant it inside the
        // defender's Town Hall to start the capture timer.
        if (TaxConfig.isExperimentalSiegeObjectivesEnabled()) {
            try {
                if (war.getColony() != null && war.getColony().getWorld() != null
                        && war.getColony().getWorld().getServer() != null) {
                    var server = war.getColony().getWorld().getServer();
                    var bannerItem = net.machiavelli.minecolonytax.siege.ModSiegeBlocks.SIEGE_BANNER_ITEM.get();
                    for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(attackerUUID);
                        if (p != null && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(bannerItem))) {
                            p.getInventory().add(new net.minecraft.world.item.ItemStack(bannerItem, 1));
                        }
                    }
                }
            } catch (Exception e) {
                WARSYSTEM_LOGGER.warn("Failed to hand Siege Banners to attackers: {}", e.getMessage());
            }
        }

        // Militia upgrade reinforcements — spawn on BOTH sides if either colony
        // has the upgrade. Defender side primarily (per design), attacker side
        // optionally so an upgraded attacker colony also gets the boost.
        // Idempotent — re-entry checks the existing set is empty first.
        if (war.militiaSupport.isEmpty()) {
            try {
                // Defender militia
                if (war.getColony() != null) {
                    int defenderGuardCount = (int) war.getColony().getCitizenManager().getCitizens().stream()
                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
                            .count();
                    // No specific attacker-target — let the militia find via vanilla aggro
                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
                            war.getColony(), defenderGuardCount, null,
                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
                }
                // Attacker militia (their colony also benefits from the upgrade)
                if (war.getAttackerColony() != null) {
                    int attackerGuardCount = (int) war.getAttackerColony().getCitizenManager().getCitizens().stream()
                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
                            .count();
                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
                            war.getAttackerColony(), attackerGuardCount, null,
                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
                }
            } catch (Exception e) {
                WARSYSTEM_LOGGER.warn("Militia spawn during war start failed: {}", e.getMessage());
            }
        }

        // Apply resistance effects to defending guards during war
        GuardResistanceHandler.applyResistanceToGuardsForWar(war.getColony());
        if (war.getAttackerColony() != null) {
            GuardResistanceHandler.applyResistanceToGuardsForWar(war.getAttackerColony());
        }

        // Initialize militia system for guard tracking and citizen conversion in BOTH
        // colonies
        initializeWarMilitiaSystem(war);
        activateWarMilitia(war);
        if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "Attacking Forces";
            String defenderColonyName = war.getColony().getName();

            Component warBeginMsg = Component.empty()
                    .append(Component.translatable("war.begin.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.begin.body", attackerColonyName, defenderColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastToServer(warBeginMsg);
        }
        long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
        scheduleTimerWarnings(war, warDurationMillis);
    }

    private static void assignWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null)
            return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set war";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void resetWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null)
            return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set default";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    /**
     * Returns true when {@code playerUuid} is an enemy of {@code colony} in any active war.
     * <p>
     * "Enemy" means the player is on the OPPOSING side of a war that involves {@code colony}:
     * <ul>
     *   <li>If {@code colony} is the defending colony of a war, an attacker (an entry in
     *       attackerLives, or the attacking colony's owner) is an enemy.</li>
     *   <li>If {@code colony} is the attacking colony of a war, a defender (an entry in
     *       defenderLives, or the defending colony's owner) is an enemy.</li>
     * </ul>
     * Purely additive helper used by militia/guard targeting so war participants are
     * autonomously attacked the way PvE raiders are. Fully null-safe; never throws.
     *
     * @param playerUuid the player to test
     * @param colony     the colony the citizen/guard belongs to
     * @return true if the player is an active war enemy of the colony
     */
    public static boolean isEnemyWarParticipant(UUID playerUuid, IColony colony) {
        if (playerUuid == null || colony == null) {
            return false;
        }
        for (WarData war : ACTIVE_WARS.values()) {
            if (war == null) {
                continue;
            }
            // Only actively-fought wars should mark enemies. During the JOINING
            // countdown nobody is hostile yet, so skip non-INWAR wars (matches the
            // INWAR gating used in the tick/expiry paths).
            if (war.getStatus() != WarData.WarStatus.INWAR) {
                continue;
            }
            IColony defenderColony = war.getColony();
            IColony attackerColony = war.getAttackerColony();

            int defenderColonyId = defenderColony != null ? defenderColony.getID() : Integer.MIN_VALUE;
            int attackerColonyId = attackerColony != null ? attackerColony.getID() : Integer.MIN_VALUE;
            int colonyId = colony.getID();

            // A player listed on BOTH rosters is treated as friendly (never flag),
            // so a citizen of one side cannot be attacked by its own side.
            boolean onDefenderSide = war.getDefenderLives() != null && war.getDefenderLives().containsKey(playerUuid);
            boolean onAttackerSide = war.getAttackerLives() != null && war.getAttackerLives().containsKey(playerUuid);

            // This colony is defending — attackers are enemies.
            if (colonyId == defenderColonyId) {
                if (onAttackerSide && !onDefenderSide) {
                    return true;
                }
                if (attackerColony != null && attackerColony.getPermissions() != null
                        && playerUuid.equals(attackerColony.getPermissions().getOwner())
                        && !onDefenderSide) {
                    return true;
                }
            }

            // This colony is attacking — defenders are enemies.
            if (attackerColony != null && colonyId == attackerColonyId) {
                if (onDefenderSide && !onAttackerSide) {
                    return true;
                }
                if (defenderColony != null && defenderColony.getPermissions() != null
                        && playerUuid.equals(defenderColony.getPermissions().getOwner())
                        && !onAttackerSide) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void checkForVictory(WarData war) {
        // Re-entry guard: the economic transfers and the vassalization money-grab below
        // run BEFORE the idempotent endWar() call, and several death/objective events can
        // call this within a single tick. If this war was already resolved (removed from
        // ACTIVE_WARS, or its slot replaced), bail so spoils/grabs cannot double-fire.
        if (war == null || ACTIVE_WARS.get(war.getColony().getID()) != war) {
            return;
        }
        boolean allAttackersDead = war.getAttackerLives().values().stream().allMatch(lives -> lives <= 0);
        boolean allDefendersDead = war.getDefenderLives().values().stream().allMatch(lives -> lives <= 0);
        boolean allDefenderGuardsDead = war.getRemainingDefenderGuards() <= 0;
        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;

        // Check if we have any participants at all
        boolean hasAttackers = !war.getAttackerLives().isEmpty();
        boolean hasDefenders = !war.getDefenderLives().isEmpty();

        // If no participants, don't end the war
        if (!hasAttackers && !hasDefenders) {
            return;
        }

        // Victory conditions:
        // - Attackers win if all defenders are dead (0 lives) OR if all defender guards
        // are dead
        // - Defenders win if all attackers are dead (0 lives) OR if all attacker guards
        // are dead
        // - Priority: Player deaths take precedence over guard deaths for ending wars
        boolean attackersWin = (hasDefenders && allDefendersDead) || (!hasDefenders && allDefenderGuardsDead);
        boolean defendersWin = (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);

        // Only proceed if there's a clear victory condition
        if (!attackersWin && !defendersWin) {
            return;
        }

        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;

        if (TaxConfig.isDebugLogging()) {
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                    "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: "
                            + defendersWin);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                    "[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: "
                    + war.getRemainingDefenderGuards());
        }

        if (defendersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component victoryMsg = Component.empty()
                    .append(Component.translatable("war.defenders.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.defenders.win.body", defenderColonyName, attackerColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), victoryMsg);
            // Count the win/loss here — this kill-based defender victory previously
            // counted NOTHING (the loop body below was empty), so defenders never
            // accumulated wars-won from successful defenses.
            for (UUID defenderUUID : war.getDefenderLives().keySet()) {
                ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
                if (defender != null) {
                    PlayerWarDataManager.incrementWarsWon(defender);
                }
            }
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(attackerUUID);
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsLost(attackerPlayer);
                }
            }
            war.resolvedOutcome = "DEFENDER_VICTORY";
            // Record war loss BEFORE economic transfers so immunity check uses pre-war
            // balance
            if (war.getAttackerColony() != null) {
                net.machiavelli.minecolonytax.economy.WarExhaustionManager
                        .recordWarLoss(war.getAttackerColony().getID());
            }
            applyWarEconomyTransfers(war, false);

        } else if (attackersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component conquestMsg = Component.empty()
                    .append(Component.translatable("war.attackers.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.attackers.win.body", attackerColonyName, defenderColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), conquestMsg);
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(attackerUUID);
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsWon(attackerPlayer);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                }
            }
            // Count the defeat for the defending players too (only winners were counted
            // before, so wars-lost never moved on kill-based losses).
            for (UUID defenderUUID : war.getDefenderLives().keySet()) {
                ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
                if (defender != null) {
                    PlayerWarDataManager.incrementWarsLost(defender);
                }
            }
            war.resolvedOutcome = "ATTACKER_VICTORY";
            // Record war loss for defender (they lost when attackers won)
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
            // Apply victory/defeat balance transfers - attackers win, defenders pay
            applyWarEconomyTransfers(war, true);

            if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                if (TaxConfig.isOccupationSystemEnabled()) {
                    // Occupation phase: occupier collects taxes but can't interact, original owner
                    // has time to reclaim
                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                            war.getColony(), war.getAttacker(), war.getAttackerColony());
                    WARSYSTEM_LOGGER.info("Colony {} is now occupied by {} (occupation system active)",
                            war.getColony().getName(), war.getAttacker());
                } else {
                    // Direct transfer (legacy behavior)
                    transferOwnership(war.getColony(), war.getAttacker());
                }
            } else if (TaxConfig.isWarVassalizationEnabled()) {
                // Vassalize the losing colony instead of transferring ownership
                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                int durationHours = TaxConfig.getWarVassalizationDurationHours();
                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                        war.getColony(),
                        war.getAttacker(),
                        tributePercent,
                        durationHours);
                if (vassalized) {
                    // "Vassalize only + take the huge money": one-time war-spoils grab from the
                    // losing colony's treasury and the losing player's wallet, paid to the victor.
                    applyWarVassalizationMoneyGrab(war);
                    WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} for {} hours at {}% tribute",
                            war.getColony().getName(), war.getAttacker(), durationHours, tributePercent);
                }
            }
        }
        endWar(war.getColony());
    }

    /**
     * Applies the attacker-wins "take over colony" outcome to the defender colony
     * ({@code war.getColony()}) as an immediate, unconditional takeover. This is the
     * <em>total-victory</em> path used when the defender fully capitulates (surrender),
     * as opposed to a normal timed victory which routes through the occupation reclaim
     * window in {@link #checkForVictory}:
     * <ul>
     *   <li>Nth (non-primary) colony -> full conquest: the deed moves to the victor via
     *       {@link #transferOwnership}.</li>
     *   <li>Protected primary colony -> {@code transferOwnership()} refuses (via
     *       ColonyTierGuard) and vassalizes the loser instead, so a home base is never
     *       seized outright.</li>
     *   <li>Colony transfer disabled entirely -> vassalize-only outcome plus the one-time
     *       war-chest/wallet money grab.</li>
     * </ul>
     * Does not call {@link #endWar}; the caller ends the war after this returns.
     *
     * @return true if the deed was actually transferred (full conquest), false if the
     *         colony was vassalized / nothing changed hands.
     */
    public static boolean applyAttackerVictoryTakeover(WarData war) {
        if (war == null || war.getColony() == null) return false;

        boolean conquered = false;
        if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
            // transferOwnership() enforces primary-colony protection itself and will
            // vassalize the loser (returning false) when the deed cannot legally move.
            conquered = transferOwnership(war.getColony(), war.getAttacker());
        } else if (TaxConfig.isWarVassalizationEnabled()) {
            // Colony transfer disabled: vassalize-only outcome + one-time war-spoils grab.
            int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
            int durationHours = TaxConfig.getWarVassalizationDurationHours();
            boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                    war.getColony(), war.getAttacker(), tributePercent, durationHours);
            if (vassalized) {
                applyWarVassalizationMoneyGrab(war);
                WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} at {}% tribute (surrender)",
                        war.getColony().getName(), war.getAttacker(), tributePercent);
            }
        }
        return conquered;
    }

    /**
     * One-time "huge money" grab applied when a war victory results in vassalization
     * (the vassalize-only path, used when colony deed transfer is disabled). Moves a
     * configurable percentage of the losing colony's treasury and the losing player's
     * wallet to the victor. Both percentages are independently configurable and either
     * can be disabled by setting it to 0.
     */
    private static void applyWarVassalizationMoneyGrab(WarData war) {
        try {
            IColony loserColony = war.getColony();
            IColony winnerColony = war.getAttackerColony();
            if (loserColony == null || loserColony.getWorld() == null) return;
            MinecraftServer server = loserColony.getWorld().getServer();
            if (server == null) return;

            // --- Colony treasury grab ---
            int treasuryPct = TaxConfig.getWarVassalizationTreasuryGrabPercent();
            if (treasuryPct > 0 && winnerColony != null) {
                int loserBal = net.machiavelli.minecolonytax.economy.TreasuryManager
                        .getTreasuryBalance(loserColony.getID());
                if (loserBal > 0) {
                    int requested = (int) Math.floor(loserBal * (treasuryPct / 100.0));
                    int winnerBal = net.machiavelli.minecolonytax.economy.TreasuryManager
                            .getTreasuryBalance(winnerColony.getID());
                    int cap = net.machiavelli.minecolonytax.economy.TreasuryManager
                            .getEffectiveMaxCapacity(winnerColony.getID());
                    int headroom = Math.max(0, cap - winnerBal);
                    int actual = Math.min(requested, headroom);
                    if (actual > 0) {
                        net.machiavelli.minecolonytax.economy.TreasuryManager
                                .deductFromTreasury(loserColony.getID(), actual);
                        net.machiavelli.minecolonytax.economy.TreasuryManager
                                .addToTreasury(winnerColony.getID(), actual);
                        WARSYSTEM_LOGGER.info("War vassalization treasury grab ({}%): {} coins {} -> {}",
                                treasuryPct, actual, loserColony.getName(), winnerColony.getName());
                    }
                }
            }

            // --- Losing player's wallet grab ---
            int walletPct = TaxConfig.getWarVassalizationPlayerBalanceGrabPercent();
            if (walletPct > 0) {
                UUID loserOwner = loserColony.getPermissions().getOwner();
                ServerPlayer loserPlayer = loserOwner != null ? server.getPlayerList().getPlayer(loserOwner) : null;
                if (loserPlayer != null) {
                    long bal = net.machiavelli.minecolonytax.integration.CurrencyService.getAvailableBalance(
                            loserPlayer, loserColony,
                            net.machiavelli.minecolonytax.integration.CurrencyService.Source.WALLET);
                    if (bal > 0) {
                        int requested = (int) Math.floor(bal * (walletPct / 100.0));
                        if (requested > 0) {
                            int taken = net.machiavelli.minecolonytax.integration.CurrencyService.takeFromPlayer(
                                    loserPlayer, loserColony, requested,
                                    net.machiavelli.minecolonytax.integration.CurrencyService.Source.WALLET);
                            if (taken > 0) {
                                UUID winnerUUID = war.getAttacker();
                                ServerPlayer winnerPlayer = winnerUUID != null
                                        ? server.getPlayerList().getPlayer(winnerUUID) : null;
                                if (winnerPlayer != null) {
                                    net.machiavelli.minecolonytax.integration.CurrencyService.giveToPlayer(
                                            winnerPlayer, winnerColony, taken,
                                            net.machiavelli.minecolonytax.integration.CurrencyService.Source.WALLET);
                                }
                                loserPlayer.sendSystemMessage(Component.literal("War tribute: " + taken
                                        + " coins seized from your wallet as the price of defeat.")
                                        .withStyle(ChatFormatting.RED));
                                WARSYSTEM_LOGGER.info("War vassalization wallet grab ({}%): {} coins from {}",
                                        walletPct, taken, loserOwner);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            WARSYSTEM_LOGGER.error("Failed to apply war vassalization money grab", e);
        }
    }

    /**
     * Applies economic penalties to both sides during a stalemate.
     *
     * @param war The war data containing information about the conflict
     */
    private static void applyStalematePenalties(WarData war) {
        double stalematePenalty = TaxConfig.getWarStalematePercentage();
        if (stalematePenalty > 0) {
            // Apply stalemate penalties to both sides
            long attackerDeducted = 0;
            long defenderDeducted = 0;

            // Process attacker side
            if (war.getAttackerTeamID() != null) {
                attackerDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getAttackerTeamID(),
                        stalematePenalty);
            } else {
                for (UUID uuid : war.getAttackerLives().keySet()) {
                    attackerDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }

            // Process defender side
            if (war.getDefenderTeamID() != null) {
                defenderDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getDefenderTeamID(),
                        stalematePenalty);
            } else {
                for (UUID uuid : war.getDefenderLives().keySet()) {
                    defenderDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }

            war.setPenaltyReport("Stalemate penalties applied: " + (stalematePenalty * 100)
                    + "% deducted from all participants (Attackers lost: "
                    + attackerDeducted + ", Defenders lost: " + defenderDeducted + ")");

            // Send message to all participants about the economic penalties
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component ecoMsg = Component.literal("War Stalemate: Both sides have been penalized economically!")
                        .withStyle(ChatFormatting.GOLD);
                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), ecoMsg);
            }
        }

        int freezeCycles = TaxConfig.getWarTaxFreezeCycles();
        if (freezeCycles > 0) {
            TaxManager.freezeColonyTax(war.getColony().getID(), freezeCycles);
            if (war.getAttackerColony() != null) {
                TaxManager.freezeColonyTax(war.getAttackerColony().getID(), freezeCycles);
            }
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                String freezeMsg = "Tax generation frozen for " + freezeCycles + " cycles due to war stalemate!";
                Component notification = Component.literal(freezeMsg).withStyle(ChatFormatting.GOLD);
                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), notification);
            }
        }
    }

    /**
     * Handles economic transfers after a war is won or lost.
     * Transfers funds based on the configured percentages in TaxConfig.
     * 
     * @param war          The war data containing information about the conflict
     * @param attackersWon True if attackers won, false if defenders won
     */
    private static void applyWarEconomyTransfers(WarData war, boolean attackersWon) {
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;

        // Get the appropriate percentage based on who won
        double transferPercentage = attackersWon ? TaxConfig.getWarVictoryPercentage()
                : TaxConfig.getWarDefeatPercentage();

        if (transferPercentage <= 0) {
            // No economic penalties configured. Still leave a non-empty penalty report:
            // an empty report makes endWar() record the war as "Stalemate" (and count
            // stalemate stats) even though one side clearly won.
            war.economyTransferTotal = 0L;
            war.setPenaltyReport("Victory - war reparations are disabled on this server (transfer percentage 0).");
            return;
        }

        // Identify winner and loser colonies
        IColony winnerColony = attackersWon ? war.getAttackerColony() : war.getColony();
        IColony loserColony = attackersWon ? war.getColony() : war.getAttackerColony();

        UUID winnerTeamID = attackersWon ? war.getAttackerTeamID() : war.getDefenderTeamID();
        UUID loserTeamID = attackersWon ? war.getDefenderTeamID() : war.getAttackerTeamID();

        long totalTransferred = 0;

        // Check if SDMShop is enabled
        if (TaxConfig.isSDMShopConversionEnabled()) {
            // === SDMShop ENABLED - Use SDMShop balance system ===
            if (winnerTeamID != null && loserTeamID != null) {
                // Team-based battle - Select SINGLE winner to receive ALL rewards

                // Determine winning participants and colony
                Map<UUID, Integer> winningParticipants = attackersWon ? war.getAttackerLives() : war.getDefenderLives();
                IColony winningColony = attackersWon ? war.getAttackerColony() : war.getColony();
                Map<UUID, Integer> losingParticipants = attackersWon ? war.getDefenderLives() : war.getAttackerLives();

                // Select single winner (prioritizes owner > officers > participants)
                UUID singleWinnerUUID = selectSingleWarWinner(winningColony, winningParticipants.keySet());
                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(singleWinnerUUID);

                // Apply team economic penalties - transfer from ALL losers to SINGLE winner
                long totalCollected = 0;
                List<String> transactionDetails = new ArrayList<>();

                // Collect from all losing participants
                for (UUID loserUUID : losingParticipants.keySet()) {
                    ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                    if (loser != null) {
                        long loserBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration
                                .getMoney(loser);
                        long transferAmount = Math.max(1, (long) (loserBalance * transferPercentage));

                        if (transferAmount > 0 && loserBalance >= transferAmount) {
                            net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(loser,
                                    loserBalance - transferAmount);
                            totalCollected += transferAmount;

                            // Notify losing participant
                            loser.sendSystemMessage(Component.literal("⚔️ WAR DEFEAT PENALTY ⚔️")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                    .append(Component.literal("\nYou lost $" + transferAmount + " due to war defeat!")
                                            .withStyle(ChatFormatting.RED)));

                            transactionDetails.add(loser.getName().getString() + " lost $" + transferAmount);
                        }
                    }
                }

                // Award all collected funds to single winner. If the selected winner is
                // offline (wars can expire while players are logged out), the losers have
                // already been debited above — queue the payout instead of dropping it,
                // otherwise the collected coins are destroyed.
                if (totalCollected > 0 && singleWinner == null && singleWinnerUUID != null) {
                    net.machiavelli.minecolonytax.pvp.PendingWagerPayouts.queue(
                            singleWinnerUUID, (int) Math.min(Integer.MAX_VALUE, totalCollected),
                            "war reparations (winner offline at war end)");
                    WARSYSTEM_LOGGER.info(
                            "War reparations: winner {} offline, queued {} coins for delivery on next login",
                            singleWinnerUUID, totalCollected);
                }
                if (totalCollected > 0 && singleWinner != null) {
                    long currentBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration
                            .getMoney(singleWinner);
                    net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(singleWinner,
                            currentBalance + totalCollected);

                    // Notify winner
                    singleWinner.sendSystemMessage(Component.literal("🏆 WAR VICTORY REWARD 🏆")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("\nYou received $" + totalCollected + " as war reparations!")
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));

                    // Send transaction summary to all participants only
                    Component transactionSummary = Component.literal("💰 WAR ECONOMY TRANSACTIONS 💰")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component.literal("\n" + String.join("\n", transactionDetails))
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component
                                    .literal("\nTotal awarded to " + singleWinner.getName().getString() + ": $"
                                            + totalCollected)
                                    .withStyle(ChatFormatting.GREEN));

                    sendMessageToWarParticipants(war, transactionSummary);
                }

                totalTransferred = totalCollected;
            } else {
                // Individual transfers - ensure single winner selection
                Map<UUID, Integer> winningParticipants = attackersWon ? war.getAttackerLives() : war.getDefenderLives();
                IColony winningColony = attackersWon ? war.getAttackerColony() : war.getColony();
                UUID loserUUID = attackersWon ? war.getColony().getPermissions().getOwner() : war.getAttacker();

                // Select single winner (prioritizes owner > officers > participants)
                UUID singleWinnerUUID = selectSingleWarWinner(winningColony, winningParticipants.keySet());

                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(singleWinnerUUID);
                ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);

                if (singleWinner != null && loser != null) {
                    totalTransferred = (long) WarEconomyHandler.transferBalanceToPlayer(loserUUID, singleWinnerUUID,
                            transferPercentage);

                    // Send participant-only notification
                    Component individualTransferMsg = Component.literal("💰 WAR VICTORY TRANSFER 💰")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component.literal("\n" + loser.getName().getString() + " lost $" + totalTransferred)
                                    .withStyle(ChatFormatting.RED))
                            .append(Component.literal("\nAwarded to " + singleWinner.getName().getString())
                                    .withStyle(ChatFormatting.GREEN));

                    sendMessageToWarParticipants(war, individualTransferMsg);
                }
            }
        } else {
            // === SDMShop DISABLED - Use colony tax system ===
            // Calculate war reparations based on colony taxes
            int reparationsAmount = 0;

            // Use the colony tax system to determine reparations amount
            if (loserColony != null && winnerColony != null) {
                // Check stored tax in the loser's colony
                int loserColonyTax = TaxManager.getStoredTaxForColony(loserColony);

                // Calculate reparations as a percentage of stored tax
                reparationsAmount = (int) (loserColonyTax * transferPercentage);

                // If loser has no tax or not enough, calculate based on winner's expected
                // revenue
                if (reparationsAmount <= 0) {
                    // We need to determine the expected tax revenue based on buildings
                    int expectedTaxRevenue = 0;

                    // Calculate an expected tax based on the attacker's colony revenue potential
                    for (IBuilding building : ColonyBuildingUtil.getBuildings(winnerColony)) {
                        String buildingType = building.getBuildingDisplayName();
                        double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                        double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType)
                                * building.getBuildingLevel();
                        expectedTaxRevenue += (int) (baseTax + upgradeTax);
                    }

                    // Set reparations amount based on a percentage of expected tax revenue
                    reparationsAmount = (int) (expectedTaxRevenue * transferPercentage);

                    // Ensure minimum reparations amount if any buildings exist
                    if (reparationsAmount <= 0 && !ColonyBuildingUtil.getBuildings(winnerColony).isEmpty()) {
                        reparationsAmount = TaxConfig.getDebtLimit() / 10; // A minimum reparation amount
                    }
                }

                // Ensure reparations don't exceed debt limit if creating debt
                if (reparationsAmount > 0) {
                    // Cap reparations to debt limit if it would create too much debt
                    if (loserColonyTax - reparationsAmount < -TaxConfig.getDebtLimit()) {
                        reparationsAmount = loserColonyTax + TaxConfig.getDebtLimit();
                    }

                    // Transfer reparations between colonies using tax system
                    // Deduct from loser colony (potentially creating debt)
                    if (loserColony != null) {
                        // Deduct from loser colony tax (can go negative as debt)
                        TaxManager.payTaxDebt(loserColony, -reparationsAmount); // Negative to remove tax
                    }

                    // Add to winner colony
                    if (winnerColony != null) {
                        TaxManager.payTaxDebt(winnerColony, reparationsAmount);
                    }

                    totalTransferred = reparationsAmount;

                    // Send detailed colony tax transfer notification to participants only
                    Component colonyTaxTransferMsg = Component.literal("🏛️ COLONY TAX REPARATIONS 🏛️")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component
                                    .literal("\n" + loserColony.getName() + " colony tax reduced by "
                                            + reparationsAmount)
                                    .withStyle(ChatFormatting.RED))
                            .append(Component
                                    .literal("\n" + winnerColony.getName() + " colony tax increased by "
                                            + reparationsAmount)
                                    .withStyle(ChatFormatting.GREEN))
                            .append(Component
                                    .literal("\nLoser colony tax: " + TaxManager.getStoredTaxForColony(loserColony))
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component
                                    .literal("\nWinner colony tax: " + TaxManager.getStoredTaxForColony(winnerColony))
                                    .withStyle(ChatFormatting.GRAY));

                    sendMessageToWarParticipants(war, colonyTaxTransferMsg);
                }
            } else {
                // Backup to player inventory transfers if colonies are not available
                // Team-based transfers using inventory currency
                long amountTransferred = 0;
                List<UUID> losers = new ArrayList<>(
                        attackersWon ? war.getDefenderLives().keySet() : war.getAttackerLives().keySet());

                // Select single winner using priority system (owner > officers > participants)
                Map<UUID, Integer> winningParticipants = attackersWon ? war.getAttackerLives() : war.getDefenderLives();
                IColony winningColony = attackersWon ? war.getAttackerColony() : war.getColony();
                UUID singleWinnerUUID = selectSingleWarWinner(winningColony, winningParticipants.keySet());
                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(singleWinnerUUID);

                if (singleWinner != null) {
                    List<String> transactionDetails = new ArrayList<>();

                    for (UUID loserUUID : losers) {
                        ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList()
                                .getPlayer(loserUUID);
                        if (loser != null) {
                            long transferred = (long) WarEconomyHandler.transferBalanceToPlayer(loserUUID,
                                    singleWinner.getUUID(), transferPercentage);
                            amountTransferred += transferred;
                            transactionDetails.add(loser.getName().getString() + " lost " + transferred + " coins");
                        }
                    }

                    // Send transaction summary to participants only
                    if (amountTransferred > 0) {
                        Component inventoryTransferMsg = Component.literal("💰 WAR INVENTORY TRANSFERS 💰")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal("\n" + String.join("\n", transactionDetails))
                                        .withStyle(ChatFormatting.GRAY))
                                .append(Component
                                        .literal("\nTotal awarded to " + singleWinner.getName().getString() + ": "
                                                + amountTransferred + " coins")
                                        .withStyle(ChatFormatting.GREEN));

                        sendMessageToWarParticipants(war, inventoryTransferMsg);
                    }
                }

                totalTransferred = amountTransferred;
            }
        }

        // Log and announce the economic impact
        String winnerColonyName = attackersWon
                ? (war.getAttackerColony() != null ? war.getAttackerColony().getName() : "attackers")
                : war.getColony().getName();
        String loserColonyName = attackersWon ? war.getColony().getName()
                : (war.getAttackerColony() != null ? war.getAttackerColony().getName() : "attackers");

        war.economyTransferTotal = totalTransferred;
        war.setPenaltyReport("War reparations: " + totalTransferred + " transferred from " + loserColonyName + " to "
                + winnerColonyName);

        // Send economy summary to participants only (not global broadcast)
        if (totalTransferred > 0) {
            Component ecoMsg = Component.literal("🏆 WAR ECONOMIC RESULT 🏆")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("\n" + loserColonyName).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" has paid ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.valueOf(totalTransferred)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" in war reparations to ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(winnerColonyName).withStyle(ChatFormatting.GREEN));

            // Send only to war participants, not the whole server
            sendMessageToWarParticipants(war, ecoMsg);
        }
    }

    /**
     * Selects a single winner from the winning side to receive all rewards.
     * Prioritizes colony owner, then officers, then any participant.
     * 
     * @param winningColony The winning colony
     * @param participants  Set of winning participants
     * @return UUID of the selected winner
     */
    private static UUID selectSingleWarWinner(IColony winningColony, Set<UUID> participants) {
        // First priority: Colony owner if they participated
        UUID owner = winningColony.getPermissions().getOwner();
        if (participants.contains(owner)) {
            return owner;
        }

        // Second priority: Any officer who participated
        for (UUID participantUUID : participants) {
            Rank rank = winningColony.getPermissions().getRank(participantUUID);
            if (rank != null && rank.isColonyManager()) {
                return participantUUID;
            }
        }

        // Third priority: Any participant (shouldn't happen if owner/officers exist)
        if (!participants.isEmpty()) {
            return participants.iterator().next();
        }

        // Fallback: Colony owner even if they didn't participate
        return owner;
    }

    /**
     * Sends a message to all war participants only.
     * 
     * @param war     The war data
     * @param message The message to send
     */
    private static void sendMessageToWarParticipants(WarData war, Component message) {
        Set<UUID> allParticipants = new HashSet<>();
        allParticipants.addAll(war.getAttackerLives().keySet());
        allParticipants.addAll(war.getDefenderLives().keySet());

        for (UUID participantUUID : allParticipants) {
            ServerPlayer participant = war.getColony().getWorld().getServer().getPlayerList()
                    .getPlayer(participantUUID);
            if (participant != null) {
                participant.sendSystemMessage(message);
            }
        }
    }

    /**
     * Transfers a colony's deed to a new owner, OR routes the action through
     * the appropriate fallback per the Siege SMP ruleset.
     *
     * @return true if the deed actually moved; false if the transfer was blocked
     *         (e.g. primary colony protection), vassalized as fallback, or failed
     *         for any other reason. Callers MUST inspect this so they don't
     *         broadcast "permanently claimed" when the deed never moved.
     */
    public static boolean transferOwnership(IColony colony, UUID newOwnerUUID) {
        if (colony == null) {
            return false;
        }
        if (colony.getWorld() == null || colony.getWorld().getServer() == null) {
            return false;
        }

        // Siege SMP ruleset: primary colonies are protected from ownership transfer
        // by default. Fall back to vassalization if enabled, so the war still has
        // a meaningful consequence for the loser.
        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
            WARSYSTEM_LOGGER.info("Ownership transfer denied for colony {}: {}",
                    colony.getID(),
                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
            if (TaxConfig.isWarVassalizationEnabled()) {
                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                int durationHours = TaxConfig.getWarVassalizationDurationHours();
                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                        colony, newOwnerUUID, tributePercent, durationHours);
                if (vassalized) {
                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
                    WarData war = ACTIVE_WARS.get(colony.getID());
                    Component msg = Component.literal(colony.getName()
                            + " is a Primary colony — vassalized instead of conquered.")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
                    sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
                }
            }
            return false;
        }

        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null) {
            return false;
        }
        if (colony.getPermissions().setOwner(newOwner)) {
            colony.markDirty();
            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));
            WarData war = ACTIVE_WARS.get(colony.getID());
            sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
            return true;
        } else {
            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
            return false;
        }
    }

    public static void endWar(IColony colony) {
        if (colony == null) return;
        // Finding 10: make endWar idempotent. Atomically remove the WarData from
        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
        // different code path), bail out — re-running the rest of this method
        // would double-fire demotions, history records, treasury cleanup, etc.
        WarData warData = ACTIVE_WARS.remove(colony.getID());
        if (warData == null) {
            // Already ended — nothing to do. (Previously this code re-ran all
            // cleanup with warData == null, producing best-effort no-ops scattered
            // with NPE risk.)
            return;
        }

        // Remove resistance effects from guards in both colonies
        if (warData != null) {
            GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getColony());
            if (warData.getAttackerColony() != null) {
                GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getAttackerColony());
            }

            // Clean up militia system for both colonies
            cleanupWarMilitiaSystem(warData);

            // Restore all explosion-damaged blocks ledgered for this war.
            // Bug #8 fix: previously the ledger only accumulated and never restored.
            try {
                if (warData.getColony() != null && warData.getColony().getWorld() != null) {
                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
                            warData.getWarID(), warData.getColony().getWorld());
                }
            } catch (Exception e) {
                WARSYSTEM_LOGGER.error("Failed to restore war block ledger for war {}", warData.getWarID(), e);
            }

            // Drop any Town Hall demolition hit state so it doesn't leak between wars.
            try {
                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
            } catch (Exception ignored) {}

            // Drop any Plant-the-Banner capture state for the same reason.
            try {
                net.machiavelli.minecolonytax.siege.PlantTheBannerObjective.onWarEnded(warData.getWarID());
            } catch (Exception ignored) {}

            // Despawn militia-upgrade reinforcements (NOT victory-counted, just combat extenders).
            try {
                net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(warData.militiaSupport);
            } catch (Exception e) {
                WARSYSTEM_LOGGER.warn("Failed to despawn war militia: {}", e.getMessage());
            }
        }

        // Disable war actions for both sides
        setWarInteractionPermissions(colony, false);

        // Also disable for attacker colony if it exists
        if (warData != null && warData.getAttackerColony() != null) {
            setWarInteractionPermissions(warData.getAttackerColony(), false);
        }

        // Demote war participants out of the Hostile rank on both colonies.
        // Attackers were assigned hostile on the defender colony; defenders on the attacker colony.
        if (warData != null) {
            if (warData.getAttackerLives() != null) {
                demoteParticipantsFromHostile(colony, warData.getAttackerLives().keySet());
            }
            if (warData.getAttackerColony() != null && warData.getDefenderLives() != null) {
                demoteParticipantsFromHostile(warData.getAttackerColony(), warData.getDefenderLives().keySet());
            }
        }

        // (Removed from ACTIVE_WARS at the top of this method as part of the
        // Finding 10 idempotency fix — no further read/remove needed.)

        // Restore Hostile rank to pre-war state now that the war is no longer active
        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(warData.getAttackerColony());
        }

        // Remove War Exhaustion status and start recovery period
        net.machiavelli.minecolonytax.economy.WarExhaustionManager.removeWarStatus(colony.getID());
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.economy.WarExhaustionManager
                    .removeWarStatus(warData.getAttackerColony().getID());
        }

        // Clear war chest roles for both sides
        net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(colony.getID());
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(warData.getAttackerColony().getID());
        }

        if (warData != null) {
            if (warData.countdownTaskId >= 0) {
                TickScheduler.cancel(warData.countdownTaskId);
                warData.countdownTaskId = -1;
            }
            if (warData.warChestDrainTaskId >= 0) {
                TickScheduler.cancel(warData.warChestDrainTaskId);
                warData.warChestDrainTaskId = -1;
            }
            if (warData.joinCountdownTaskId >= 0) {
                TickScheduler.cancel(warData.joinCountdownTaskId);
                warData.joinCountdownTaskId = -1;
            }
            if (warData.joinStartTaskId >= 0) {
                TickScheduler.cancel(warData.joinStartTaskId);
                warData.joinStartTaskId = -1;
            }
            if (warData.bossEvent != null) {
                warData.bossEvent.removeAllPlayers();
                warData.bossEvent.setVisible(false);
            }
            // Also tear down the allies boss bar. It is normally removed when the join phase
            // finalizes, but a war cancelled DURING the join phase (too few participants,
            // unbalanced teams, peace, or /wnt war end) routes straight here and would otherwise
            // leave ally players with a phantom boss bar until they relog.
            if (warData.alliesBossEvent != null) {
                warData.alliesBossEvent.removeAllPlayers();
                warData.alliesBossEvent.setVisible(false);
            }
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                colony.getPermissions().getPlayers().forEach((uuid, pdata) -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null)
                        p.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                });
                if (colony.getCitizenManager() != null) { // Null check for citizen manager
                    colony.getCitizenManager().getCitizens().forEach(citizen -> {
                        citizen.getEntity().ifPresent(
                                entity -> entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING));
                    });
                }
                warData.getAttackerLives().keySet().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null)
                        resetWarGroup(p);
                });
                warData.getDefenderLives().keySet().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null)
                        resetWarGroup(p);
                });
                // Handle players in spectator mode (teleport to spawn, restore inventory, set
                // to survival)
                Set<UUID> allParticipants = new HashSet<>();
                if (warData.getAttackerLives() != null)
                    allParticipants.addAll(warData.getAttackerLives().keySet());
                if (warData.getDefenderLives() != null)
                    allParticipants.addAll(warData.getDefenderLives().keySet());

                for (UUID participantUUID : allParticipants) {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(participantUUID);
                    if (p != null && p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        WarInventoryHandler.restoreInventory(p);

                        BlockPos respawnPos = p.getRespawnPosition();
                        float respawnAngle = p.getRespawnAngle();
                        net.minecraft.server.level.ServerLevel respawnLevel = p.server
                                .getLevel(p.getRespawnDimension());

                        // Try to use personal respawn point first
                        boolean hasValidPersonalSpawn = respawnPos != null && respawnLevel != null
                                && p.isRespawnForced();

                        if (hasValidPersonalSpawn) {
                            p.teleportTo(respawnLevel, respawnPos.getX() + 0.5, respawnPos.getY() + 0.1,
                                    respawnPos.getZ() + 0.5, respawnAngle, 0F);
                            WARSYSTEM_LOGGER.info("Player {} teleported to personal respawn point: {} in dimension {}",
                                    p.getName().getString(), respawnPos, respawnLevel.dimension().location());
                        } else {
                            // Fallback: Teleport to surface at current X/Z in their current dimension
                            BlockPos currentPos = p.blockPosition();
                            // Player's current level is already a ServerLevel in server-side code
                            net.minecraft.server.level.ServerLevel currentLevel = (net.minecraft.server.level.ServerLevel) p
                                    .level();
                            int topY = currentLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    currentPos.getX(), currentPos.getZ());
                            p.teleportTo(currentLevel, currentPos.getX() + 0.5, topY + 1.0, currentPos.getZ() + 0.5,
                                    p.getYRot(), p.getXRot());
                            WARSYSTEM_LOGGER.info(
                                    "Player {} has no valid personal respawn, teleported to surface at current X/Z: {} in dimension {}",
                                    p.getName().getString(), new BlockPos(currentPos.getX(), topY, currentPos.getZ()),
                                    currentLevel.dimension().location());
                        }

                        p.setGameMode(GameType.SURVIVAL);
                        p.sendSystemMessage(
                                Component.translatable("war.end.inventory.restored")
                                        .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)));
                    }
                }
            }

            // Determine winner for history record (this part seems okay, might need
            // adjustment based on actual war outcome logic)
            UUID winnerUuid = colony.getPermissions().getOwner(); // This might not always be the "winner"
            String winnerName = "Unknown";
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                winnerName = Optional.ofNullable(colony.getWorld().getServer().getPlayerList().getPlayer(winnerUuid))
                        .map(p -> p.getName().getString())
                        .orElse(winnerUuid.toString());
            }

            String outcome;
            long amountTransferred = 0L;

            // A war is a stalemate when the resolving path SAID so (timer stalemates,
            // white peace, both war chests dry) or when nothing resolved it at all (empty
            // report: cancelled / administratively ended). The old check keyed on the
            // empty report only, so real timer stalemates — which carry a report — never
            // counted toward the players' stalemate stat while cancelled wars did.
            boolean recordedStalemate = "STALEMATE".equals(warData.resolvedOutcome)
                    || (warData.resolvedOutcome == null && warData.getPenaltyReport().isEmpty());
            if (recordedStalemate) {
                outcome = warData.getPenaltyReport().isEmpty() ? "Stalemate" : warData.getPenaltyReport();
                amountTransferred = warData.economyTransferTotal;
                if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                    for (UUID uuid : warData.getAttackerLives().keySet()) {
                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (player != null)
                            PlayerWarDataManager.incrementWarStalemates(player);
                    }
                    for (UUID uuid : warData.getDefenderLives().keySet()) {
                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (player != null)
                            PlayerWarDataManager.incrementWarStalemates(player);
                    }
                }
            } else {
                // endWar records — it must never MOVE money. All live resolution paths
                // (applyWarEconomyTransfers, the vassalization money grab, occupation)
                // settle the economy BEFORE calling endWar. The previous "TOTAL VICTORY"
                // branch here deducted the loser colony a SECOND time (the first deduct
                // happened in handleVictoryRewards moments earlier), reported an amount
                // computed with a third, unrelated formula (max(1000, balance*3/4)), and
                // re-incremented wars-won/lost that handleTimeExpiry had already counted.
                outcome = warData.getPenaltyReport();
                amountTransferred = warData.economyTransferTotal;
            }

            // Record war outcome in DB. "Led to occupation" is a fact about the world, not
            // about the report text: ask OccupationManager whether either colony is occupied
            // now (kill-based attacker victories write a "War reparations" report and start
            // the occupation separately, so the old "TOTAL VICTORY" text check missed them).
            boolean ledToOccupation = false;
            try {
                ledToOccupation = TaxConfig.isOccupationSystemEnabled()
                        && (net.machiavelli.minecolonytax.occupation.OccupationManager.isOccupied(colony.getID())
                            || (warData.getAttackerColony() != null
                                && net.machiavelli.minecolonytax.occupation.OccupationManager
                                        .isOccupied(warData.getAttackerColony().getID())));
            } catch (Throwable ignored) {
                // Occupation state is best-effort telemetry; never let it break endWar.
            }
            net.machiavelli.minecolonytax.db.WarStatsDB.recordWarEnd(
                    warData,
                    net.machiavelli.minecolonytax.db.WarStatsDB.determineOutcome(warData),
                    amountTransferred,
                    ledToOccupation);

            String attackerName = warData.getAttackerColony() != null ? warData.getAttackerColony().getName()
                    : "Unknown Attacker";

            // Determine outcome from each colony's perspective. Prefer the explicit
            // resolvedOutcome set by the code path that decided the war — the old
            // report-string heuristic could only recognize a defender victory when the
            // report contained "TOTAL VICTORY", so every kill-based defender win was
            // recorded as a defender DEFEAT in colony history. The heuristic remains as
            // fallback for administratively ended wars (no resolvedOutcome set).
            boolean isStalemate = warData.resolvedOutcome != null
                    ? "STALEMATE".equals(warData.resolvedOutcome)
                    : (warData.getPenaltyReport().isEmpty()
                            || warData.getPenaltyReport().toLowerCase().contains("stalemate"));
            boolean defenderWon = warData.resolvedOutcome != null
                    ? "DEFENDER_VICTORY".equals(warData.resolvedOutcome)
                    : (!isStalemate && warData.getPenaltyReport().contains("TOTAL VICTORY")
                            && warData.getRemainingDefenderGuards() > 0);
            String defenderOutcome = isStalemate ? "STALEMATE" : (defenderWon ? "VICTORY" : "DEFEAT");
            String attackerOutcome = isStalemate ? "STALEMATE" : (defenderWon ? "DEFEAT" : "VICTORY");

            // addWarEntry also writes a legacy string for WarHistoryCommand compatibility
            int _defBefore = TaxManager.getStoredTaxForColonyId(colony.getID());
            int _defAfter  = _defBefore - (int) amountTransferred;
            int _atkBefore = warData.getAttackerColony() != null
                    ? TaxManager.getStoredTaxForColonyId(warData.getAttackerColony().getID()) : 0;
            int _atkAfter  = _atkBefore + (int) amountTransferred;
            HistoryManager.getColonyHistory(colony.getID())
                    .addWarEntry(attackerName, defenderOutcome, amountTransferred, _defBefore, _defAfter);
            if (warData.getAttackerColony() != null) {
                HistoryManager.getColonyHistory(warData.getAttackerColony().getID())
                        .addWarEntry(colony.getName(), attackerOutcome, amountTransferred, _atkBefore, _atkAfter);
            }
            HistoryManager.saveHistory();

            // Reconcile permission ranks now instead of waiting for the next startup / manual health
            // check. endWar cleared the Hostile-rank action nodes but never demoted the participants
            // OUT of the Hostile rank, so they'd linger as "hostile" members of the opposing colony.
            // run() demotes stray hostiles while keeping anyone still legitimately hostile via another
            // active conflict (this war is already removed from ACTIVE_WARS).
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                try {
                    net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.run(colony.getWorld().getServer());
                } catch (Throwable t) {
                    WARSYSTEM_LOGGER.warn("Post-endWar permission reconciliation failed: {}", t.toString());
                }
            }
            WARSYSTEM_LOGGER.info("War ended for colony {}", colony.getName());
        }
    }

    public static class WarInventoryHandler {
        private static final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
        private static final Map<UUID, ItemStack[]> savedArmors = new ConcurrentHashMap<>();

        public static boolean hasSavedInventory(ServerPlayer player) {
            return savedInventories.containsKey(player.getUUID());
        }

        public static void saveAndClearInventory(ServerPlayer player) {
            ItemStack[] main = new ItemStack[player.getInventory().getContainerSize()];
            for (int i = 0; i < main.length; i++) {
                main[i] = player.getInventory().getItem(i).copy();
            }
            savedInventories.put(player.getUUID(), main);
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                armor[i] = player.getInventory().armor.get(i).copy();
            }
            savedArmors.put(player.getUUID(), armor);
            player.getInventory().clearContent();
        }

        public static void restoreInventory(ServerPlayer player) {
            UUID uuid = player.getUUID();
            ItemStack[] main = savedInventories.remove(uuid);
            if (main != null) {
                for (int i = 0; i < main.length; i++) {
                    player.getInventory().setItem(i, main[i]);
                }
            }
            ItemStack[] armor = savedArmors.remove(uuid);
            if (armor != null) {
                for (int i = 0; i < 4; i++) {
                    player.getInventory().armor.set(i, armor[i]);
                }
            }
            player.containerMenu.broadcastChanges();
        }

        /**
         * On server stop, hand every stored inventory back to its (online) owner. savedInventories is
         * only in-memory, so without this a player still connected as a last-life spectator when the
         * server stops would lose their war-held items. ServerStoppingEvent fires before the player list
         * is removed and before saveAll(), so the restored inventory is persisted with the player's data.
         * Offline players have no live inventory to write into — those entries are simply dropped.
         */
        public static void restoreAllOnServerStop(MinecraftServer server) {
            if (server == null) return;
            for (UUID uuid : new ArrayList<>(savedInventories.keySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    // Per-player guard: one player's restore failing (e.g. a closing connection) must not
                    // abort the remaining players' inventory restores.
                    try {
                        restoreInventory(player);
                    } catch (Exception e) {
                        WARSYSTEM_LOGGER.warn("Failed to restore inventory for {} on server stop: {}", uuid, e.toString());
                    }
                }
            }
        }
    }

    public static void handleTimeExpiry(WarData war) {
        // Re-entry guard (mirrors checkForVictory): the economic stalemate/transfer paths
        // below run before the idempotent endWar() call, and timer warnings + tick checks
        // can both fire this for the same war. If this war was already resolved (removed
        // from ACTIVE_WARS, or its slot replaced), bail so penalties cannot double-fire.
        if (war == null || ACTIVE_WARS.get(war.getColony().getID()) != war) {
            return;
        }
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;

        // Handle disconnected players - set their lives to zero
        Map<UUID, Integer> disconnectedPlayers = WarEventHandler.getDisconnectedWarParticipants();

        // Process disconnected attackers
        for (UUID uuid : new ArrayList<>(war.getAttackerLives().keySet())) {
            if (disconnectedPlayers.containsKey(uuid) && disconnectedPlayers.get(uuid) == 1) { // 1 = attacker
                // Player is disconnected and part of this war, set lives to zero
                war.getAttackerLives().put(uuid, 0);
                WARSYSTEM_LOGGER.info("[MineColonyTax] Setting disconnected attacker {} to 0 lives on war expiry",
                        uuid);
            }
        }

        // Process disconnected defenders
        for (UUID uuid : new ArrayList<>(war.getDefenderLives().keySet())) {
            if (disconnectedPlayers.containsKey(uuid) && disconnectedPlayers.get(uuid) == 2) { // 2 = defender
                // Player is disconnected and part of this war, set lives to zero
                war.getDefenderLives().put(uuid, 0);
                WARSYSTEM_LOGGER.info("[MineColonyTax] Setting disconnected defender {} to 0 lives on war expiry",
                        uuid);
            }
        }

        int attackerTotalLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
        int defenderTotalLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
        String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                : "The Attackers";
        String defenderColonyName = war.getColony().getName();

        MutableComponent timeExpiredMsgBase = Component.translatable("war.time.expired.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));

        if (attackerTotalLives == 0 && war.getRemainingAttackerGuards() == 0) {
            MutableComponent defenderVictoryMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.defenders.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.defenders.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, defenderVictoryMsg);
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsWon(p);
            }
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsLost(p);
            }
            war.resolvedOutcome = "DEFENDER_VICTORY";
            handleVictoryRewards(war, true); // true for defender victory
            endWar(war.getColony());
            return;
        } else if (defenderTotalLives == 0 && war.getRemainingDefenderGuards() == 0) {
            MutableComponent attackerVictoryMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.attackers.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.attackers.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, attackerVictoryMsg);
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsWon(p);
            }
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsLost(p);
            }
            war.resolvedOutcome = "ATTACKER_VICTORY";
            handleVictoryRewards(war, false); // false for attacker victory
            endWar(war.getColony());
            return;
        }

        // Check for stalemate due to no losses on either side by timeout
        if (attackerTotalLives == war.initialAttackerTotalLives && // No player lives lost by attackers
                defenderTotalLives == war.initialDefenderTotalLives && // No player lives lost by defenders
                war.getRemainingAttackerGuards() == war.initialAttackerGuards && // No attacker guards lost
                war.getRemainingDefenderGuards() == war.initialDefenderGuards) { // No defender guards lost

            MutableComponent stalemateNoLossesMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.stalemate.timeout.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.stalemate.timeout.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.translatable("war.stalemate.timeout.part3").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.stalemate.timeout.penalties").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, stalemateNoLossesMsg);
            // Stalemate penalty: deduct the configured WarStalematePercentage from both
            // sides' team balances (was a hardcoded 0.25 here — now honors the config so
            // the player-balance deduction matches the colony-tax deduction and the
            // penalty report below).
            double stalematePct = TaxConfig.getWarStalematePercentage();
            war.getAttackerLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePct));
            war.getDefenderLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePct));
            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage()); // Defender colony
            if (war.getAttackerColony() != null)
                TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage()); // Attacker
                                                                                                            // colony
            war.resolvedOutcome = "STALEMATE";
            war.setPenaltyReport(
                    "Stalemate (Timeout - No Losses): Both sides lose " + (TaxConfig.getWarStalematePercentage() * 100)
                            + "% of their balances and colony revenue is reduced by "
                            + (TaxConfig.getWarStalematePercentage() * 100) + "%.");
            endWar(war.getColony());
            return;
        }

        // Strategic victory/loss based on proportional strength remaining
        double attackerNormalizedStrength = (double) (attackerTotalLives + war.getRemainingAttackerGuards())
                / (war.initialAttackerTotalLives + war.initialAttackerGuards);
        double defenderNormalizedStrength = (double) (defenderTotalLives + war.getRemainingDefenderGuards())
                / (war.initialDefenderTotalLives + war.initialDefenderGuards);
        double epsilon = 0.01; // To handle floating point comparisons
        String reportOutcome;
        MutableComponent strategicMsg; // Changed to MutableComponent

        if (attackerNormalizedStrength + epsilon < defenderNormalizedStrength) { // Attackers lost proportionally more
            war.resolvedOutcome = "DEFENDER_VICTORY";
            reportOutcome = "Strategic Victory: Defenders win! Attackers lost proportionally more strength.";
            strategicMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.strategic.defender.victory.part1")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.translatable("war.strategic.defender.victory.part2")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.strategic.defender.victory.part3")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.strategic.defender.victory.part4")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            WarEconomyHandler.transferTeamBalanceToSinglePlayer(war.getAttackerTeamID(), war.getDefender(),
                    TaxConfig.getWarStalematePercentage());
            if (war.getAttackerColony() != null)
                TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage());
            broadcastComponent(war, strategicMsg);
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsWon(p);
            }
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsLost(p);
            }
        } else if (defenderNormalizedStrength + epsilon < attackerNormalizedStrength) { // Defenders lost proportionally
                                                                                        // more
            war.resolvedOutcome = "ATTACKER_VICTORY";
            reportOutcome = "Strategic Victory: Attackers win! Defenders lost proportionally more strength.";
            strategicMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.strategic.attacker.victory.part1")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.strategic.attacker.victory.part2")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.strategic.attacker.victory.part3")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.translatable("war.strategic.attacker.victory.part4")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            WarEconomyHandler.transferTeamBalanceToSinglePlayer(war.getDefenderTeamID(), war.getAttacker(),
                    TaxConfig.getWarStalematePercentage());
            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage());
            broadcastComponent(war, strategicMsg);
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsWon(p);
            }
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsLost(p);
            }
        } else { // Proportional losses are too close - stalemate
            war.resolvedOutcome = "STALEMATE";
            reportOutcome = "Stalemate (Timeout - Proportional Losses): Both sides fought hard but neither gained a clear advantage. Penalties apply.";
            strategicMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.stalemate.proportional.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.stalemate.proportional.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.translatable("war.stalemate.proportional.part3").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.stalemate.proportional.penalties")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            war.getAttackerLives().keySet().forEach(
                    uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, TaxConfig.getWarStalematePercentage()));
            war.getDefenderLives().keySet().forEach(
                    uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, TaxConfig.getWarStalematePercentage()));
            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage());
            if (war.getAttackerColony() != null)
                TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage());
            broadcastComponent(war, strategicMsg);
        }
        war.setPenaltyReport(reportOutcome);
        endWar(war.getColony());
    }

    // Helper to broadcast war results to entire server
    private static void broadcastComponent(WarData war, Component message) {
        broadcastToServer(message);
    }

    // NOTE: guard-kill counting is owned by WarEventHandler.onCitizenDeath (deduped via guardIDs).
    // This method is retained as reusable API; the previous RaidKillTracker caller was removed
    // because it double-counted. Math.max(0, ...) guards against ever driving the counter negative
    // if this is wired up again.
    public static void handleGuardKilled(WarData war, boolean isDefenderGuard) {
        if (isDefenderGuard) {
            war.remainingDefenderGuards = Math.max(0, war.remainingDefenderGuards - 1);
            Component message = Component.translatable("war.guard.killed.defender", war.getRemainingDefenderGuards())
                    .withStyle(style -> style.withColor(ChatFormatting.RED));
            notifyWarParticipants(war, message);
        } else {
            war.remainingAttackerGuards = Math.max(0, war.remainingAttackerGuards - 1);
            Component message = Component.translatable("war.guard.killed.attacker", war.getRemainingAttackerGuards())
                    .withStyle(style -> style.withColor(ChatFormatting.BLUE));
            notifyWarParticipants(war, message);
        }
        checkForVictory(war);
    }

    private static void notifyWarParticipants(WarData war, Component notification) {
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;
        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player != null)
                player.sendSystemMessage(notification);
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player != null)
                player.sendSystemMessage(notification);
        });
    }

    // Keep the original method for backward compatibility
    private static void notifyWarParticipants(WarData war, String message, ChatFormatting color) {
        Component notification = Component.literal(message).withStyle(style -> style.withColor(color));
        notifyWarParticipants(war, notification);
    }

    private static void handleVictoryRewards(WarData war, boolean defendersWon) {
        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // ATTACKER WINS - Target colony enters occupied
                                                                       // state
            if (TaxConfig.isOccupationSystemEnabled()) {
                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                        war.getColony(), war.getAttacker(), war.getAttackerColony());
                war.setPenaltyReport("TOTAL VICTORY - Colony is now OCCUPIED! Original owner has " +
                        TaxConfig.getOccupationDurationDays() + " days to reclaim.");
            } else {
                transferOwnership(war.getColony(), war.getAttacker());
                war.setPenaltyReport("TOTAL VICTORY - Colony transferred to attackers!");
            }
        } else if (TaxConfig.isColonyWagerEnabled() && defendersWon && war.getAttackerColony() != null) {
            // DEFENDER WINS with COLONY WAGER enabled - Attacker's wagered colony enters
            // occupied state!
            if (TaxConfig.isOccupationSystemEnabled()) {
                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                        war.getAttackerColony(), war.getDefender(), war.getColony());
                war.setPenaltyReport("⚔ COUNTER-CONQUEST! The attacker's colony is now OCCUPIED by the defenders! " +
                        "Attacker has " + TaxConfig.getOccupationDurationDays() + " days to reclaim.");

                // Notify both sides about the wager outcome
                if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                    Component wagerLostMsg = Component.empty()
                            .append(Component.literal("⚔ WAGER LOST! ⚔").withStyle(ChatFormatting.DARK_RED,
                                    ChatFormatting.BOLD))
                            .append(Component.literal("\nYou attacked and LOST! Your colony ")
                                    .withStyle(ChatFormatting.RED))
                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
                                    ChatFormatting.BOLD))
                            .append(Component.literal(" is now OCCUPIED by the defenders!")
                                    .withStyle(ChatFormatting.RED))
                            .append(Component
                                    .literal("\nYou have " + TaxConfig.getOccupationDurationDays()
                                            + " days to wage a reclamation war.")
                                    .withStyle(ChatFormatting.YELLOW));

                    Component wagerWonMsg = Component.empty()
                            .append(Component.literal("⚔ COUNTER-CONQUEST! ⚔").withStyle(ChatFormatting.GOLD,
                                    ChatFormatting.BOLD))
                            .append(Component.literal(
                                    "\nYou successfully defended your colony and captured the attacker's wagered colony ")
                                    .withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
                                    ChatFormatting.BOLD))
                            .append(Component.literal("!").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal("\nYou can now collect "
                                    + (int) (TaxConfig.getOccupationTaxPercentage() * 100) + "% of their taxes!")
                                    .withStyle(ChatFormatting.YELLOW));

                    // Notify attacker
                    ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                            .getPlayer(war.getAttacker());
                    if (attackerPlayer != null) {
                        attackerPlayer.sendSystemMessage(wagerLostMsg);
                    }

                    // Notify defender
                    ServerPlayer defenderPlayer = war.getColony().getWorld().getServer().getPlayerList()
                            .getPlayer(war.getDefender());
                    if (defenderPlayer != null) {
                        defenderPlayer.sendSystemMessage(wagerWonMsg);
                    }
                }
            } else {
                // No occupation system - direct transfer of attacker's colony to defender
                transferOwnership(war.getAttackerColony(), war.getDefender());
                war.setPenaltyReport("⚔ COUNTER-CONQUEST! Attacker's colony transferred to the defenders!");
            }
        } else {
            // Economic spoils: route through the same paired debit+credit settlement as
            // kill-based victories. The previous code here deducted WarDefeatPercentage
            // from the loser colony and then only TOLD the winners they had received a
            // share computed with a DIFFERENT percentage — no credit ever happened — and
            // its "TOTAL VICTORY" penalty report made endWar() deduct the loser a second
            // time. applyWarEconomyTransfers debits and credits atomically (SDMShop,
            // colony ledger, or inventory), messages participants with the real numbers,
            // and records the amount in war.economyTransferTotal for the history entry.
            applyWarEconomyTransfers(war, !defendersWon);
            if (war.getPenaltyReport().isEmpty()) {
                war.setPenaltyReport("Victory - no war reparations were transferable.");
            }
        }
    }

    public static Map<UUID, Integer> getLivesForPlayer(WarData war, ServerPlayer player) {
        if (TaxConfig.isDebugLogging()) {
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] getLivesForPlayer called for player " + player.getName().getString() + " ("
                    + player.getUUID() + ")");
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] FTB_TEAMS_INSTALLED: " + FTB_TEAMS_INSTALLED);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker lives: " + war.getAttackerLives());
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Defender lives: " + war.getDefenderLives());
        }

        // First check if player is directly in the lives maps
        UUID playerUUID = player.getUUID();
        if (TaxConfig.isDebugLogging()) {
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking if attacker lives contains player UUID: "
                    + war.getAttackerLives().containsKey(playerUUID));
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking if defender lives contains player UUID: "
                    + war.getDefenderLives().containsKey(playerUUID));
        }

        if (war.getAttackerLives().containsKey(playerUUID)) {
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in attacker lives, returning attacker lives");
            return war.getAttackerLives();
        } else if (war.getDefenderLives().containsKey(playerUUID)) {
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in defender lives, returning defender lives");
            return war.getDefenderLives();
        }

        // Check if player is in attacker or defender allies
        if (war.getAttackerAllies().contains(playerUUID)) {
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in attacker allies, returning attacker lives");
            return war.getAttackerLives();
        } else if (war.getDefenderAllies().contains(playerUUID)) {
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in defender allies, returning defender lives");
            return war.getDefenderLives();
        }

        if (FTB_TEAMS_INSTALLED) {
            Optional<FtbTeamsCompat.TeamHandle> teamOpt = FtbTeamsCompat.getTeamForPlayer(playerUUID);
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team found: " + teamOpt.isPresent());
            if (teamOpt.isPresent()) {
                FtbTeamsCompat.TeamHandle team = teamOpt.get();
                UUID teamId = FtbTeamsCompat.getTeamId(team);
                if (TaxConfig.isDebugLogging()) {
                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team ID: " + teamId);
                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] War attacker team ID: " + war.getAttackerTeamID());
                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] War defender team ID: " + war.getDefenderTeamID());
                }

                if (teamId != null && teamId.equals(war.getAttackerTeamID())) {
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is on attacker team, returning attacker lives");
                    return war.getAttackerLives();
                } else if (teamId != null && teamId.equals(war.getDefenderTeamID())) {
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is on defender team, returning defender lives");
                    return war.getDefenderLives();
                }

                // Check if player is allied to any participating team
                FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                        : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
                if (atkTeam != null && FtbTeamsCompat.partyTeamContains(atkTeam, playerUUID)) {
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to attacker team, returning attacker lives");
                    return war.getAttackerLives();
                }

                FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                        : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
                if (defTeam != null && FtbTeamsCompat.partyTeamContains(defTeam, playerUUID)) {
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to defender team, returning defender lives");
                    return war.getDefenderLives();
                }

                if (TaxConfig.isDebugLogging())
                    System.out
                            .println("[DEBUG] Player team not participating in war, checking Minecolonies membership");
            } else {
                if (TaxConfig.isDebugLogging())
                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player has no FTB team, checking Minecolonies membership");
            }
        }

        // Check Minecolonies colony membership and ranks
        IColony attackerColony = war.getAttackerColony();
        IColony defenderColony = war.getColony();

        if (TaxConfig.isDebugLogging()) {
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking Minecolonies membership - Attacker colony: "
                    + (attackerColony != null ? attackerColony.getName() : "null"));
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking Minecolonies membership - Defender colony: "
                    + (defenderColony != null ? defenderColony.getName() : "null"));
        }

        // Check if player is in attacker colony (owner, officer, or friend)
        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player in attacker colony players list: "
                        + attackerPerms.getPlayers().containsKey(playerUUID));
            if (attackerPerms.getPlayers().containsKey(playerUUID)) {
                Rank playerRank = attackerPerms.getRank(playerUUID);
                if (TaxConfig.isDebugLogging())
                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player rank in attacker colony: "
                            + (playerRank != null ? playerRank.getName() : "null"));
                if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) ||
                        playerRank.equals(attackerPerms.getRankOfficer()) ||
                        playerRank.equals(attackerPerms.getRankFriend()))) {
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is in attacker colony with rank " + playerRank.getName()
                                + ", returning attacker lives");
                    return war.getAttackerLives();
                }
            }
        }

        // Check if player is in defender colony (owner, officer, or friend)
        if (defenderColony != null) {
            IPermissions defenderPerms = defenderColony.getPermissions();
            if (TaxConfig.isDebugLogging())
                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player in defender colony players list: "
                        + defenderPerms.getPlayers().containsKey(playerUUID));
            if (defenderPerms.getPlayers().containsKey(playerUUID)) {
                Rank playerRank = defenderPerms.getRank(playerUUID);
                if (TaxConfig.isDebugLogging())
                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player rank in defender colony: "
                            + (playerRank != null ? playerRank.getName() : "null"));
                if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
                        playerRank.equals(defenderPerms.getRankOfficer()) ||
                        playerRank.equals(defenderPerms.getRankFriend()))) {
                    if (TaxConfig.isDebugLogging())
                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is in defender colony with rank " + playerRank.getName()
                                + ", returning defender lives");
                    return war.getDefenderLives();
                }
            }
        }

        if (TaxConfig.isDebugLogging())
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player not participating in war, returning empty map");
        return new HashMap<>(); // Return mutable map instead of Collections.emptyMap()
    }

    public static WarData getActiveWarForPlayer(ServerPlayer player) {
        for (WarData war : ACTIVE_WARS.values()) {
            // First check if player is directly in the lives maps
            if (war.getAttackerLives().containsKey(player.getUUID())
                    || war.getDefenderLives().containsKey(player.getUUID())) {
                return war;
            }

            // Check if player is in attacker or defender allies
            if (war.getAttackerAllies().contains(player.getUUID())
                    || war.getDefenderAllies().contains(player.getUUID())) {
                return war;
            }

            // Check FTB Teams
            if (FTB_TEAMS_INSTALLED) {
                Optional<FtbTeamsCompat.TeamHandle> teamOpt = FtbTeamsCompat.getTeamForPlayer(player.getUUID());
                if (teamOpt.isPresent()) {
                    FtbTeamsCompat.TeamHandle team = teamOpt.get();
                    UUID teamId = FtbTeamsCompat.getTeamId(team);
                    if (teamId != null
                            && (teamId.equals(war.getAttackerTeamID()) || teamId.equals(war.getDefenderTeamID()))) {
                        return war;
                    }

                    // Check if player is allied to any participating team
                    FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                            : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
                    if (atkTeam != null && FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())) {
                        return war;
                    }

                    FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                            : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
                    if (defTeam != null && FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
                        return war;
                    }
                }
            }

            // Check Minecolonies colony membership and ranks
            IColony attackerColony = war.getAttackerColony();
            IColony defenderColony = war.getColony();

            // Check if player is in attacker colony (owner, officer, or friend)
            if (attackerColony != null) {
                IPermissions attackerPerms = attackerColony.getPermissions();
                if (attackerPerms.getPlayers().containsKey(player.getUUID())) {
                    Rank playerRank = attackerPerms.getRank(player.getUUID());
                    if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) ||
                            playerRank.equals(attackerPerms.getRankOfficer()) ||
                            playerRank.equals(attackerPerms.getRankFriend()))) {
                        return war;
                    }
                }
            }

            // Check if player is in defender colony (owner, officer, or friend)
            if (defenderColony != null) {
                IPermissions defenderPerms = defenderColony.getPermissions();
                if (defenderPerms.getPlayers().containsKey(player.getUUID())) {
                    Rank playerRank = defenderPerms.getRank(player.getUUID());
                    if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
                            playerRank.equals(defenderPerms.getRankOfficer()) ||
                            playerRank.equals(defenderPerms.getRankFriend()))) {
                        return war;
                    }
                }
            }
        }
        return null;
    }

    public static void scheduleTimerWarnings(WarData war, long warDurationMillis) {
        long quarter = warDurationMillis / 4;
        for (int i = 1; i <= 3; i++) {
            long delay = quarter * i;
            if (delay <= 0)
                continue;
            TickScheduler.scheduleDelayed(() -> {
                // Check if war still exists in active wars or if the colony world/server is
                // null
                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
                        war.getColony().getWorld() == null ||
                        war.getColony().getWorld().getServer() == null ||
                        war.bossEvent == null) {
                    return;
                }

                // Check war status - don't process for ended wars
                if (war.getStatus() != WarData.WarStatus.INWAR) {
                    return;
                }

                long elapsedSeconds = (System.currentTimeMillis() - war.warStartTime) / 1000;
                long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
                long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
                String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
                        war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
                        war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
                        remaining / 60, remaining % 60);
                Component newName = Component.literal(bossText);
                float newProgress = (float) remaining / warDurationSeconds;
                war.bossEvent.setName(newName);
                war.bossEvent.setProgress(newProgress);
                war.bossEvent.setVisible(true);
                if (remaining <= 0) {
                    handleTimeExpiry(war);
                }
            }, delay);
        }
    }

    private static void applyWarGlowToParticipants(WarData war) {
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;
        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null)
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null)
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
        });
    }

    public static void applyGuardGlow(IColony colony) {
        if (colony.getCitizenManager() == null)
            return;
        colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .forEach(citizen -> citizen.getEntity().ifPresent(entity -> {
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
                }));
    }

    public static void onPlayerKilledInWar(ServerPlayer killer, ServerPlayer killed, WarData war) {
        if (killer != null && killed != null && war != null) {
            PlayerWarDataManager.incrementPlayersKilledInWar(killer);
            PlayerWarDataManager.incrementTimesKilledInWar(killed);
            net.machiavelli.minecolonytax.db.WarStatsDB.recordWarKill(
                    killer.getUUID(), killer.getName().getString(),
                    killed.getUUID(), killed.getName().getString());
        }
    }

    public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
                : null;
        FtbTeamsCompat.TeamHandle defenderTeam = FTB_TEAMS_INSTALLED
                ? FtbTeamsCompat.getTeamForPlayer(owner.getUUID()).orElse(null)
                : null;

        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                .findFirst().orElse(null);
        if (attackerColony == null) {
            attacker.sendSystemMessage(Component.literal("You must own a colony to declare war.")
                    .withStyle(style -> style.withColor(ChatFormatting.RED)));
            return;
        }

        initiateWar(attacker, owner.getUUID(), attackerTeam, defenderTeam, colony, attackerColony);
        WarData war = getActiveWarForPlayer(owner);

        int configuredMinutes = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get();
        if (TaxConfig.isDebugLogging()) {
            WARSYSTEM_LOGGER.info("[DEBUG] JOIN_PHASE_DURATION_MINUTES config value: {} minutes", configuredMinutes);
            WARSYSTEM_LOGGER.info("[DEBUG] Config spec: {}", TaxConfig.CONFIG.getClass().getName());
            WARSYSTEM_LOGGER.info("[DEBUG] Config default value: {}",
                    TaxConfig.JOIN_PHASE_DURATION_MINUTES.getDefault());
            WARSYSTEM_LOGGER.info("[DEBUG] Config is loaded: {}", TaxConfig.CONFIG.isLoaded());
        }

        if (ServerLifecycleHooks.getCurrentServer() != null) {
            // Get the time remaining in a readable format
            String timeRemaining = configuredMinutes + " minutes";

            // Send join phase announcement only to war participants
            Component joinPhaseMsg = Component.translatable("war.join.phase.declared", colony.getName(), timeRemaining);
            sendNotificationToWarParticipants(colony, attackerColony, joinPhaseMsg);
        }
        WARSYSTEM_LOGGER.info("Join phase started for colony {}. Waiting for participants for {} seconds.",
                colony.getName(), configuredMinutes * 60);

        if (war == null)
            return;

        long joinDurationMillis = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
        war.setJoinPhaseEndTime(System.currentTimeMillis() + joinDurationMillis);

        war.alliesBossEvent = new ServerBossEvent(
                Component.literal("Joining War - " + colony.getName()),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS);
        war.alliesBossEvent.setProgress(1.0f);
        war.alliesBossEvent.setVisible(true);

        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
            ServerPlayer ownerPlayer = colony.getWorld().getServer().getPlayerList()
                    .getPlayer(war.getColony().getPermissions().getOwner());
            if (ownerPlayer != null) {
                war.bossEvent.addPlayer(ownerPlayer);
            }
            for (UUID uuid : war.getAttackerLives().keySet()) {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null)
                    war.bossEvent.addPlayer(p);
            }
            for (UUID uuid : war.getDefenderLives().keySet()) {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null)
                    war.bossEvent.addPlayer(p);
            }
        }

        Component joinAnnouncement = Component.empty()
                .append(JOIN_MSG)
                .append(Component.literal(" "))
                .append(LEAVE_MSG);

        if (FTB_TEAMS_INSTALLED) {
            if (attackerTeam != null) {
                sendNotificationToColonyParticipants(attackerColony, joinAnnouncement);
            }
            if (defenderTeam != null) {
                sendNotificationToColonyParticipants(colony, joinAnnouncement);
            }
        } else {
            sendNotificationToColonyParticipants(attackerColony, joinAnnouncement);
            sendNotificationToColonyParticipants(colony, joinAnnouncement);
        }

        // Calculate actual remaining time instead of total duration
        long remainingMillis = war.getJoinPhaseEndTime() - System.currentTimeMillis();
        remainingMillis = Math.max(0, remainingMillis); // Ensure non-negative

        Component joinPhaseInfo = Component.translatable("war.siege.status", colony.getName(),
                String.format("%02d:%02d", remainingMillis / (60 * 1000), (remainingMillis / 1000) % 60))
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true));

        if (FTB_TEAMS_INSTALLED) {
            if (attackerTeam != null)
                sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
            if (defenderTeam != null)
                sendNotificationToColonyParticipants(colony, joinPhaseInfo);
        } else {
            sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
            sendNotificationToColonyParticipants(colony, joinPhaseInfo);
        }

        // Add countdown sound timer for the last 6 seconds of join phase, but only if
        // join phase is at least 6 seconds long
        if (joinDurationMillis >= 6000) {
            final int[] secondsLeft = { 6 };
            war.joinCountdownTaskId = TickScheduler.scheduleRepeating(() -> {
                try {
                    if (war == null || war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
                        // Self-cancel: without this the repeating task re-arms forever,
                        // leaking the task + retaining the whole WarData (audit C4).
                        if (war != null && war.joinCountdownTaskId >= 0) {
                            TickScheduler.cancel(war.joinCountdownTaskId);
                            war.joinCountdownTaskId = -1;
                        }
                        return;
                    }

                    // Play countdown sound to all war participants
                    Set<UUID> allParticipants = new HashSet<>();
                    allParticipants.addAll(war.getAttackerLives().keySet());
                    allParticipants.addAll(war.getDefenderLives().keySet());

                    // Only play sound if there are participants
                    if (!allParticipants.isEmpty()) {
                        // Play countdown sound using Minecraft's bell sound
                        for (UUID uuid : allParticipants) {
                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
                                    : null;
                            if (player != null) {
                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
                                        1.0F);
                            }
                        }
                    }

                    // Notify remaining seconds
                    notifyWarParticipants(war,
                            Component
                                    .literal("⏱ " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds")
                                            + " until war starts!")
                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));

                    secondsLeft[0]--;
                } catch (Exception ex) {
                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
                }
            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1
                                                              // second
        }

        // Main timer to start the war when join phase ends
        war.joinStartTaskId = TickScheduler.scheduleDelayed(() -> {
            if (war == null || war.getColony() == null) {
                return;
            }
            // Guard: the war may have ended during the JOINING phase (operator /warstop,
            // finalize abort, etc.). endWar() removes it from ACTIVE_WARS, so if this
            // delayed task is no longer the active war for its colony, do NOT start it —
            // otherwise it would resurrect an ended war and re-enable war permissions
            // (codex HIGH).
            if (ACTIVE_WARS.get(war.getColony().getID()) != war) {
                return;
            }
            war.setStatus(WarData.WarStatus.INWAR);
            war.warStartTime = System.currentTimeMillis();
            finalizeWarStart(war);
            // finalizeWarStart() itself ends the war (calls endWar) when there are no valid
            // participants / a bad ratio. If it did, the war is gone from ACTIVE_WARS — do
            // NOT enable war permissions or start the countdown on an ended war (codex HIGH).
            if (ACTIVE_WARS.get(war.getColony().getID()) != war) {
                return;
            }
            // Enable war actions for both sides
            setWarInteractionPermissions(war.getColony(), true);
            if (war.getAttackerColony() != null) {
                setWarInteractionPermissions(war.getAttackerColony(), true);
            }
            startWarCountdown(war);
        }, joinDurationMillis);
        war.setAccepted(true);
    }

    private static void sendNotificationToColonyParticipants(IColony colony, Component message) {
        if (colony == null || colony.getWorld() == null || colony.getWorld().getServer() == null) {
            return;
        }
        colony.getPermissions().getPlayers().keySet().stream()
                .filter(uuid -> isOfficerOrFriendly(colony, uuid))
                .forEach(uuid -> {
                    ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(message);
                    }
                });
    }

    /**
     * Sends a notification to all war participants from both colonies (attacker and
     * defender),
     * including officers, friends, and FTB team members if applicable.
     */
    // Helper to broadcast to entire server
    private static void broadcastToServer(Component message) {
        if (ServerLifecycleHooks.getCurrentServer() == null)
            return;
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void sendNotificationToWarParticipants(IColony defenderColony, IColony attackerColony,
            Component message) {
        if (defenderColony == null || defenderColony.getWorld() == null
                || defenderColony.getWorld().getServer() == null) {
            return;
        }

        Set<UUID> notifiedPlayers = new HashSet<>();
        MinecraftServer server = defenderColony.getWorld().getServer();

        // Notify defender colony officers and friends
        defenderColony.getPermissions().getPlayers().keySet().stream()
                .filter(uuid -> isOfficerOrFriendly(defenderColony, uuid))
                .forEach(uuid -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(message);
                        notifiedPlayers.add(uuid);
                    }
                });

        // Notify attacker colony officers and friends
        if (attackerColony != null) {
            attackerColony.getPermissions().getPlayers().keySet().stream()
                    .filter(uuid -> isOfficerOrFriendly(attackerColony, uuid))
                    .forEach(uuid -> {
                        if (!notifiedPlayers.contains(uuid)) {
                            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                            if (player != null) {
                                player.sendSystemMessage(message);
                                notifiedPlayers.add(uuid);
                            }
                        }
                    });
        }

        // If FTB Teams is installed, also notify team members
        if (FTB_TEAMS_INSTALLED) {
            WarData war = ACTIVE_WARS.get(defenderColony.getID());
            if (war != null) {
                // Notify attacker team members
                if (war.getAttackerTeamID() != null) {
                    FtbTeamsCompat.TeamHandle attackerTeam = FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
                    if (attackerTeam != null && FtbTeamsCompat.isPartyTeam(attackerTeam)) {
                        FtbTeamsCompat.getPartyMembers(attackerTeam).forEach(uuid -> {
                            if (!notifiedPlayers.contains(uuid)) {
                                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                                if (player != null) {
                                    player.sendSystemMessage(message);
                                    notifiedPlayers.add(uuid);
                                }
                            }
                        });
                    }
                }

                // Notify defender team members
                if (war.getDefenderTeamID() != null) {
                    FtbTeamsCompat.TeamHandle defenderTeam = FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
                    if (defenderTeam != null && FtbTeamsCompat.isPartyTeam(defenderTeam)) {
                        FtbTeamsCompat.getPartyMembers(defenderTeam).forEach(uuid -> {
                            if (!notifiedPlayers.contains(uuid)) {
                                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                                if (player != null) {
                                    player.sendSystemMessage(message);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private static void startWarCountdown(WarData warData) {
        if (warData.getColony().getWorld() == null) {
            WARSYSTEM_LOGGER.error("Cannot start war countdown, world is null for colony {}",
                    warData.getColony().getID());
            return;
        }
        final long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
            // Check if war still exists in active wars or if the colony world/server is
            // null
            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
                    warData.getColony().getWorld() == null ||
                    warData.getColony().getWorld().getServer() == null ||
                    warData.bossEvent == null) {
                TickScheduler.cancel(warData.countdownTaskId);
                warData.countdownTaskId = -1;
                return;
            }

            // Check war status - don't process for ended wars
            if (warData.getStatus() != WarData.WarStatus.INWAR) {
                TickScheduler.cancel(warData.countdownTaskId);
                warData.countdownTaskId = -1;
                return;
            }

            // Finding 9: defensive guard against wall-clock skew (NTP, manual
            // clock change, container restart). If now < warStartTime the war
            // was "born in the future" — almost certainly a backwards clock
            // adjustment. Reset warStartTime to the current wall clock so the
            // war doesn't appear to never expire (or instantly expire). This is
            // a soft repair, not a monotonic rewrite — sufficient to avoid
            // every-war-killed-on-NTP-skew bugs.
            long nowMs = System.currentTimeMillis();
            if (nowMs < warData.warStartTime) {
                WARSYSTEM_LOGGER.warn("War {}: wall clock went backwards (now={} < warStartTime={}). "
                        + "Resetting warStartTime to now; war will continue from the new clock value.",
                        warData.getWarID(), nowMs, warData.warStartTime);
                warData.warStartTime = nowMs;
            }
            long elapsedSeconds = (nowMs - warData.warStartTime) / 1000;
            long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
            String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
                    warData.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
                    warData.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
                    remaining / 60, remaining % 60);
            Component newName = Component.literal(bossText);
            float newProgress = (float) remaining / warDurationSeconds;
            warData.bossEvent.setName(newName);
            warData.bossEvent.setProgress(newProgress);
            warData.bossEvent.setVisible(true);
            if (remaining <= 0) {
                handleTimeExpiry(warData);
                TickScheduler.cancel(warData.countdownTaskId);
                warData.countdownTaskId = -1;
            }
        }, 1000, 1000);
    }

    public static void sendColonyMessage(IColony colony, Component message) {
        if (colony == null || colony.getWorld() == null)
            return;
        IPermissions perms = colony.getPermissions();
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            // Only send to colony allies: Owner, Officers, and Friends
            // Excludes: Hostile and Neutral players
            Rank rank = perms.getRank(uuid);
            if (rank != null && (rank.equals(perms.getRankOwner()) ||
                    rank.equals(perms.getRankOfficer()) ||
                    rank.equals(perms.getRankFriend()))) {
                ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                if (p != null)
                    p.sendSystemMessage(message);
            }
        });
    }

    public static void sendMessageToTeam(FtbTeamsCompat.TeamHandle team, Component msg) {
        if (team == null || ServerLifecycleHooks.getCurrentServer() == null)
            return;
        for (UUID member : FtbTeamsCompat.getTeamMembers(team)) {
            ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
            if (sp != null)
                sp.sendSystemMessage(msg);
        }
    }

    /**
     * Finds a valid colony owned by the player that meets the requirements to
     * declare war on the target.
     * Checks building requirements, guard counts, and treasury status.
     * 
     * SPECIAL CASE: Occupied colonies can be used for RECLAMATION wars against the
     * occupier's colony.
     * This allows players whose only colony is occupied to still fight back!
     * 
     * @param player        The player attempting to declare war
     * @param targetColony  The target colony
     * @param checkTreasury Whether to check treasury requirements (usually true)
     * @return The first valid IColony found, or null if none meet requirements
     */
    public static IColony findValidAttackerColony(ServerPlayer player, IColony targetColony, boolean checkTreasury) {
        if (player == null || targetColony == null)
            return null;

        UUID playerUUID = player.getUUID();
        // getFirstColony() returns a nullable Integer — unboxing it straight into an int NPEs
        // for any player without a tracked colony, which is now reachable because officers
        // (who need not own anything) can get here.
        Integer trackedPrimary = FirstColonyTracker.getFirstColony(playerUUID);
        final int primaryColonyId = trackedPrimary != null ? trackedPrimary : -1;

        List<IColony> playerColonies = IColonyManager.getInstance().getColonies(player.level()).stream()
                .filter(c -> {
                    IPermissions perms = c.getPermissions();
                    // Null-safe: getOwner() can be null on a colony with damaged permissions.
                    if (playerUUID.equals(perms.getOwner())) return true;

                    // An officer may wage war on the colony's behalf only when the owner granted
                    // DECLARE_WAR in the Officers tab. That permission is off by default, so
                    // ownership remains the only route unless someone explicitly opts in.
                    Rank rank = perms.getRank(playerUUID);
                    if (rank == null || !rank.isColonyManager()) return false;
                    boolean isOfficer = rank.equals(perms.getRankOfficer());
                    return net.machiavelli.minecolonytax.permissions.TaxPermissionManager.can(
                            c.getID(), playerUUID,
                            net.machiavelli.minecolonytax.permissions.ColonyPermission.DECLARE_WAR,
                            false, isOfficer);
                })
                .sorted((a, b) -> {
                    // Colonies the player actually owns outrank colonies they merely officer.
                    boolean aOwned = playerUUID.equals(a.getPermissions().getOwner());
                    boolean bOwned = playerUUID.equals(b.getPermissions().getOwner());
                    if (aOwned != bOwned) return aOwned ? -1 : 1;
                    if (a.getID() == primaryColonyId) return -1;
                    if (b.getID() == primaryColonyId) return 1;
                    return 0;
                })
                .collect(java.util.stream.Collectors.toList());

        for (IColony potentialAttacker : playerColonies) {
            // Cannot attack yourself
            if (potentialAttacker.getID() == targetColony.getID())
                continue;

            // RECLAMATION WAR EXCEPTION: Relax requirements when fighting to reclaim an
            // occupied colony.
            // Two scenarios are covered:
            // A) Player targets their OWN occupied colony - any attacker colony gets relaxed reqs
            // B) Player targets the OCCUPIER's colony using their occupied colony as attacker
            boolean isReclamationWar = false;
            if (TaxConfig.isOccupationSystemEnabled()) {
                // Scenario B: This attacker colony is occupied - check if targeting the occupier
                net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData attackerOccData = 
                    net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(potentialAttacker.getID());
                if (attackerOccData != null && attackerOccData.getOriginalOwnerUUID().equals(player.getUUID())) {
                    UUID occupierUUID = attackerOccData.getOccupierUUID();
                    if (occupierUUID.equals(targetColony.getPermissions().getOwner())) {
                        isReclamationWar = true;
                        WARSYSTEM_LOGGER.info(
                                "Reclamation war (B): {} using occupied colony {} to attack occupier's colony {}",
                                player.getName().getString(), potentialAttacker.getName(), targetColony.getName());
                    }
                }

                // Scenario A: The TARGET colony is the player's own occupied colony
                if (!isReclamationWar) {
                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData = 
                        net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(targetColony.getID());
                    if (targetOccData != null && targetOccData.getOriginalOwnerUUID().equals(player.getUUID())) {
                        isReclamationWar = true;
                        WARSYSTEM_LOGGER.info(
                                "Reclamation war (A): {} using colony {} to reclaim their occupied colony {}",
                                player.getName().getString(), potentialAttacker.getName(), targetColony.getName());
                    }
                }
            }

            // Check building/guard requirements (skip for reclamation wars - desperation
            // allows it!)
            if (!isReclamationWar) {
                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                            .checkWarRequirements(potentialAttacker);
                    if (!reqs.meetsRequirements)
                        continue;
                } else {
                    int guardCount = countGuards(potentialAttacker);
                    if (guardCount < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get())
                        continue;
                }

                // Check treasury (skip for reclamation wars - fighting for freedom!)
                if (checkTreasury) {
                    if (!net.machiavelli.minecolonytax.economy.TreasuryManager.canDeclareWar(potentialAttacker.getID(),
                            targetColony.getID())) {
                        continue;
                    }
                }
            }

            return potentialAttacker;
        }

        return null;
    }

    public static int processWageWarRequest(ServerPlayer attacker, IColony targetColony, CommandSourceStack source) {
        Level level = source.getLevel();

        int targetGuards = countGuards(targetColony);
        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) {
            source.sendFailure(Component.literal("Target colony must have at least "
                    + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
            return 0;
        }

        // Check if this is a reclamation war (two scenarios):
        // A) Player targets their OWN occupied colony directly
        // B) Player targets the OCCUPIER's colony while their own colony is occupied
        if (TaxConfig.isOccupationSystemEnabled()) {
            // Scenario A: Target IS the player's occupied colony
            net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData =
                    net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(targetColony.getID());
            if (targetOccData != null && targetOccData.getOriginalOwnerUUID().equals(attacker.getUUID())) {
                net.machiavelli.minecolonytax.occupation.OccupationManager
                        .markReclamationAttempted(targetColony.getID());
                attacker.sendSystemMessage(Component.literal(
                        "\u2694 RECLAMATION WAR! You are fighting to reclaim your occupied colony!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                WARSYSTEM_LOGGER.info("Reclamation war (A) initiated by {} for occupied colony {}",
                        attacker.getName().getString(), targetColony.getName());
            } else {
                // Scenario B: Player's colony is occupied and they're attacking the occupier
                // Find if any of the attacker's colonies are occupied by the target colony's owner
                UUID targetOwner = targetColony.getPermissions().getOwner();
                List<IColony> attackerColonies = IColonyManager.getInstance().getColonies(source.getLevel()).stream()
                        .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                        .toList();
                for (IColony ac : attackerColonies) {
                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData acOccData =
                            net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(ac.getID());
                    if (acOccData != null && acOccData.getOriginalOwnerUUID().equals(attacker.getUUID())
                            && acOccData.getOccupierUUID().equals(targetOwner)) {
                        net.machiavelli.minecolonytax.occupation.OccupationManager
                                .markReclamationAttempted(ac.getID());
                        attacker.sendSystemMessage(Component.literal(
                                "\u2694 RECLAMATION WAR! You are attacking the occupier of your colony " + ac.getName() + "!")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                        WARSYSTEM_LOGGER.info("Reclamation war (B) initiated by {} - attacking occupier's colony {}, reclaiming {}",
                                attacker.getName().getString(), targetColony.getName(), ac.getName());
                        break;
                    }
                }
            }
        }

        // Find a valid attacker colony using the new helper
        IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);

        if (attackerColony == null) {
            // Retained specific error messaging logic for better user feedback if they have
            // at least one colony
            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
                    .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                    .findFirst().orElse(null);

            if (anyColony == null) {
                source.sendFailure(Component.literal("You must own a colony to declare war."));
            } else {
                // If they have colonies but none were valid, give a generic failure or try to
                // diagnose the first one
                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                            .checkWarRequirements(anyColony);
                    source.sendFailure(Component.literal("None of your colonies meet the war requirements. Example ("
                            + anyColony.getName() + "): " + reqs.message));
                } else {
                    source.sendFailure(
                            Component.literal("None of your colonies have enough guards or resources to declare war."));
                }
            }
            return 0;
        }

        // Block war if attacker colony is at maximum debt
        if (TaxConfig.isDebtBlocksWar() && TaxConfig.getDebtLimit() > 0) {
            int attackerBalance = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(attackerColony);
            if (attackerBalance <= -TaxConfig.getDebtLimit()) {
                source.sendFailure(Component.literal("Your colony (" + attackerColony.getName()
                        + ") is bankrupt! Pay off your tax debt before declaring war.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        // Block if the chosen attacker colony is already in a war
        if (ACTIVE_WARS.containsKey(attackerColony.getID())) {
            source.sendFailure(Component.literal("Your colony " + attackerColony.getName()
                    + " is already under attack — cannot start another war!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean attackerAlreadyWaging = ACTIVE_WARS.values().stream()
                .anyMatch(wd -> wd.getAttackerColony() != null
                        && wd.getAttackerColony().getID() == attackerColony.getID());
        if (attackerAlreadyWaging) {
            source.sendFailure(Component.literal("Your colony " + attackerColony.getName()
                    + " is already engaged in a war — end it before starting another!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());

        // Check if this is a PRIMARY or SECONDARY colony for offline attack rules
        UUID targetOwnerUUID = targetColony.getPermissions().getOwner();
        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());

        if (owner == null) {
            // Owner is offline - check if we can still attack based on colony type
            if (TaxConfig.isOutpostVulnerabilityEnabled() && !isPrimaryColony) {
                // SECONDARY COLONY (Outpost) - Can be attacked while owner is offline
                WARSYSTEM_LOGGER.info("Outpost attack initiated on {} (owner offline) by {}",
                        targetColony.getName(), attacker.getName().getString());
                source.sendSuccess(() -> Component.literal(
                        "⚔ OUTPOST ASSAULT! Target colony owner is offline, but this is a secondary colony (outpost). Attack proceeds!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                // Continue with auto-accept flow for offline outpost attacks
                return processOfflineOutpostAttack(attacker, targetColony, attackerColony, source);
            } else {
                // PRIMARY COLONY - Owner must be online
                if (isPrimaryColony) {
                    source.sendFailure(Component.literal(
                            "Target colony is a PRIMARY colony (capital). The owner must be online to defend!"));
                } else {
                    source.sendFailure(
                            Component.literal("Target colony owner is offline! (Outpost vulnerability is disabled)"));
                }
                return 0;
            }
        }

        if (!TaxConfig.WAR_ACCEPTANCE_REQUIRED.get()) {
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                Component autoAcceptMsg = Component.empty()
                        .append(Component.literal("⚔️ WAR INITIATED ⚔️").withStyle(ChatFormatting.GOLD,
                                ChatFormatting.BOLD))
                        .append(Component.literal("\n----------------------------------------")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("\nColony ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
                                ChatFormatting.BOLD))
                        .append(Component.literal(" is now at WAR with ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(attackerColony.getName()).withStyle(ChatFormatting.DARK_RED,
                                ChatFormatting.BOLD))
                        .append(Component.literal("! (Auto-Accepted)").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\nThe drums of war sound! Join phase starting immediately!")
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("\n----------------------------------------")
                                .withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(autoAcceptMsg);
            }
            startJoinPhase(targetColony, attacker, owner);
            return 1;
        }

        WARSYSTEM_LOGGER.info("Adding pending war request for colony {} from attacker {}", targetColony.getID(),
                attacker.getUUID());
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            String attackerColonyName = attackerColony != null ? attackerColony.getName()
                    : attacker.getName().getString() + "'s forces";
            Component warDeclarationMsg = Component.empty()
                    .append(Component.translatable("war.declare.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.forces.valiant").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.declare.body", "", targetColony.getName())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastToServer(warDeclarationMsg);

            // Grant advancement
            try {
                net.minecraft.advancements.Advancement adv = attacker.getServer().getAdvancements().getAdvancement(
                        new net.minecraft.resources.ResourceLocation("minecolonytax:codex/declare_war"));
                if (adv != null) {
                    attacker.getAdvancements().award(adv, "check");
                }
            } catch (Exception e) {
            }
        }
        pendingWarRequests.put(targetColony.getID(), new WarRequest(attacker.getUUID(), targetColony.getID()));
        TickScheduler.scheduleDelayed(() -> {
            Object removedRequest = pendingWarRequests.remove(targetColony.getID());
            if (removedRequest != null) {
                if (targetColony.getWorld() != null && targetColony.getWorld().getServer() != null) {
                    ServerPlayer targetOwner = targetColony.getWorld().getServer().getPlayerList()
                            .getPlayer(targetColony.getPermissions().getOwner());
                    if (targetOwner != null) {
                        targetOwner.sendSystemMessage(
                                Component.translatable("war.request.expired.defender")
                                        .withStyle(style -> style.withColor(ChatFormatting.RED)));
                    }
                    ServerPlayer attackerPlayer = targetColony.getWorld().getServer().getPlayerList()
                            .getPlayer(attacker.getUUID());
                    if (attackerPlayer != null) {
                        attackerPlayer.sendSystemMessage(
                                Component.translatable("war.request.expired.attacker", targetColony.getName())
                                        .withStyle(style -> style.withColor(ChatFormatting.RED)));
                    }
                }
            }
        }, 30000);

        Rank playerRank = targetColony.getPermissions().getRank(attacker.getUUID());
        if (playerRank == null) {
            Rank hostileRank = targetColony.getPermissions().getRankHostile();
            targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
        } else {
            targetColony.getPermissions().setPlayerRank(attacker.getUUID(),
                    targetColony.getPermissions().getRankHostile(), level);
        }
        Rank currentRank = targetColony.getPermissions().getRank(attacker.getUUID());
        if (currentRank != null)
            currentRank.setHostile(true);

        Component message = Component.literal("⚔️ WAR DECLARATION ⚔️")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true))
                .append(Component.literal("\n"))
                .append(Component.literal(attacker.getName().getString())
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)))
                .append(Component.literal(" seeks to wage war against your colony!")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal("\n\nDo you accept this challenge?")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                .append(Component.literal("\n"))
                .append(createAcceptButton(targetColony))
                .append(" ")
                .append(createDeclineButton(targetColony));
        owner.sendSystemMessage(message);
        attacker.sendSystemMessage(
                Component.translatable("war.request.sent", targetColony.getName()).withStyle(ChatFormatting.YELLOW));
        WARSYSTEM_LOGGER.info("[War] Attacker UUID: {}", attacker.getUUID());
        WARSYSTEM_LOGGER.info("[War] Target Colony Owner: {}", targetColony.getPermissions().getOwner());
        return 1;
    }

    /**
     * Handles attacks on secondary colonies (outposts) when the owner is offline.
     * The war proceeds with auto-accept and the attacker fights against the
     * colony's guards.
     * The defender can still win if the attacker runs out of lives or time expires.
     */
    private static int processOfflineOutpostAttack(ServerPlayer attacker, IColony targetColony,
            IColony attackerColony, CommandSourceStack source) {

        if (ServerLifecycleHooks.getCurrentServer() != null) {
            Component outpostAssaultMsg = Component.empty()
                    .append(Component.literal("⚔️ OUTPOST ASSAULT ⚔️").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.literal("\n----------------------------------------")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColony.getName()).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.literal(" is assaulting the outpost ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
                            ChatFormatting.BOLD))
                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n⚠ Owner is OFFLINE - Guards will defend automatically!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\n----------------------------------------")
                            .withStyle(ChatFormatting.DARK_GRAY));
            broadcastToServer(outpostAssaultMsg);
        }

        // Start the war immediately with the attacker - no join phase needed since
        // owner is offline
        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
                : null;

        // Initiate war without a defender player - guards will fight
        initiateOfflineOutpostWar(attacker, targetColony, attackerColony, attackerTeam);

        return 1;
    }

    /**
     * Initiates a war against an outpost when the owner is offline.
     * Special handling: No defender players, only guards defend.
     */
    private static void initiateOfflineOutpostWar(ServerPlayer attacker, IColony targetColony,
            IColony attackerColony, FtbTeamsCompat.TeamHandle attackerTeam) {

        int attackerGuards = countGuards(attackerColony);
        int defenderGuards = countGuards(targetColony);

        WarData warData = new WarData(
                attacker.getUUID(),
                targetColony.getPermissions().getOwner(), // Defender owner UUID (offline)
                attackerTeam != null ? FtbTeamsCompat.getTeamId(attackerTeam) : null,
                null, // No defender team
                System.currentTimeMillis(),
                null, // No boss event yet - created below
                targetColony,
                attackerColony);

        // Mark this as an offline outpost war
        warData.setOfflineOutpostWar(true);

        // IMPORTANT: Set attacker guards to 0 for offline outpost wars.
        // The attacker's guards are at their HOME colony, not on the battlefield.
        // Without this, the victory check (attackerLives==0 && attackerGuards==0)
        // would never trigger - making the attacker invincible!
        warData.remainingAttackerGuards = 0;

        ACTIVE_WARS.put(targetColony.getID(), warData);
        int _defStartBal = TaxManager.getStoredTaxForColonyId(targetColony.getID());
        HistoryManager.logWithBalance(targetColony.getID(), "WAR",
                "War started — attacked by " + attacker.getName().getString(),
                _defStartBal, _defStartBal);
        if (attackerColony != null) {
            int _atkStartBal = TaxManager.getStoredTaxForColonyId(attackerColony.getID());
            HistoryManager.logWithBalance(attackerColony.getID(), "WAR",
                    "War started — attacking " + targetColony.getName(),
                    _atkStartBal, _atkStartBal);
        }

        // Mark defender for home-field drain advantage + schedule drain
        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(targetColony.getID());
        scheduleTreasuryDrain(warData, targetColony, attackerColony);

        // Add attacker to the war
        warData.getAttackerLives().put(attacker.getUUID(), TaxConfig.PLAYER_LIVES_IN_WAR.get());

        // Create boss bar for the attacker
        warData.bossEvent = new ServerBossEvent(
                Component.literal("Outpost Assault - " + targetColony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        warData.bossEvent.setProgress(1.0f);
        warData.bossEvent.setVisible(true);
        warData.bossEvent.addPlayer(attacker);

        // Set war status to IN WAR immediately (no join phase for offline attacks)
        warData.setStatus(WarData.WarStatus.INWAR);
        warData.warStartTime = System.currentTimeMillis();

        // Enable war interactions
        setWarInteractionPermissions(targetColony, true);
        setWarInteractionPermissions(attackerColony, true);

        // Assign hostile rank to attacker in target colony
        Rank hostileRank = targetColony.getPermissions().getRankHostile();
        targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);

        // Apply glow effects and resistance buffs
        // TODO: Implement applyGlowEffect and applyGuardResistance for offline outpost
        // wars
        // applyGlowEffect(attacker, targetColony);
        // applyGuardResistance(targetColony);

        // Start the war countdown
        startWarCountdown(warData);

        // Notify the attacker
        attacker.sendSystemMessage(
                Component.literal("⚔ ASSAULT BEGUN! Defeat the colony guards or hold the outpost until time expires!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        attacker.sendSystemMessage(Component
                .literal("⚠ You have " + TaxConfig.PLAYER_LIVES_IN_WAR.get()
                        + " lives. Lose them all and the outpost's owner wins!")
                .withStyle(ChatFormatting.RED));

        WARSYSTEM_LOGGER.info("Offline outpost war started: {} vs {} (defender offline)",
                attackerColony.getName(), targetColony.getName());
    }

    public static int processWageWarRequestWithExtortion(ServerPlayer attacker, IColony targetColony,
            CommandSourceStack source, int extortionPercent) {
        Level level = source.getLevel();

        int targetGuards = countGuards(targetColony);
        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) {
            source.sendFailure(Component.literal("Target colony must have at least "
                    + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
            return 0;
        }

        // Find a valid attacker colony using the new helper
        IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);

        if (attackerColony == null) {
            // Retained specific error messaging logic for better user feedback
            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
                    .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                    .findFirst().orElse(null);

            if (anyColony == null) {
                source.sendFailure(Component.literal("You must own a colony to declare war."));
            } else {
                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                            .checkWarRequirements(anyColony);
                    source.sendFailure(Component.literal("None of your colonies meet the war requirements. Example ("
                            + anyColony.getName() + "): " + reqs.message));
                } else {
                    source.sendFailure(
                            Component.literal("None of your colonies have enough guards or resources to declare war."));
                }
            }
            return 0;
        }

        if (targetColony.getID() == attackerColony.getID()) {
            source.sendFailure(Component.literal("Cannot declare war on your own colony!"));
            return 0;
        }

        // Block extortion/war if attacker colony is at maximum debt
        if (TaxConfig.isDebtBlocksWar() && TaxConfig.getDebtLimit() > 0) {
            int attackerBalance = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(attackerColony);
            if (attackerBalance <= -TaxConfig.getDebtLimit()) {
                source.sendFailure(Component.literal("Your colony (" + attackerColony.getName()
                        + ") is bankrupt! Pay off your tax debt before declaring war.")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
        if (owner == null) {
            source.sendFailure(Component.literal("Target colony owner is offline!"));
            return 0;
        }

        // Check if extortion system is enabled
        if (!TaxConfig.ENABLE_EXTORTION_SYSTEM.get()) {
            source.sendFailure(Component.literal("Extortion system is disabled. Use regular war declaration."));
            return 0;
        }

        // Check if target colony has extortion immunity
        if (hasExtortionImmunity(targetColony.getID())) {
            long immunityExpiration = extortionImmunity.get(targetColony.getID());
            long hoursRemaining = (immunityExpiration - System.currentTimeMillis()) / (60 * 60 * 1000L);
            source.sendFailure(Component.literal("Colony " + targetColony.getName() + " has extortion immunity for "
                    + hoursRemaining + " more hours. Use regular war declaration."));
            return 0;
        }

        if (!TaxConfig.WAR_ACCEPTANCE_REQUIRED.get()) {
            // Auto-accept is enabled, show extortion choice to defender with timer
            showExtortionChoiceWithTimer(attacker, targetColony, owner, extortionPercent);
            return 1;
        } else {
            // Manual acceptance is required, add extortion to pending request
            WARSYSTEM_LOGGER.info("Adding pending war request with extortion for colony {} from attacker {}",
                    targetColony.getID(), attacker.getUUID());
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                String attackerColonyName = attackerColony != null ? attackerColony.getName()
                        : attacker.getName().getString() + "'s forces";
                Component warDeclarationMsg = Component.empty()
                        .append(Component.translatable("war.declare.title").withStyle(ChatFormatting.GOLD,
                                ChatFormatting.BOLD))
                        .append(Component.translatable("war.time.expired.separator")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("war.forces.valiant").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                                ChatFormatting.BOLD))
                        .append(Component.translatable("war.declare.body", "", targetColony.getName())
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n💰 Extortion Demand: " + extortionPercent + "% of your balance")
                                .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("war.time.expired.separator")
                                .withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(warDeclarationMsg);
            }
            pendingWarRequests.put(targetColony.getID(),
                    new WarRequestWithExtortion(attacker.getUUID(), targetColony.getID(), extortionPercent));

            Component message = Component.literal("⚔️ WAR DECLARATION WITH EXTORTION ⚔️")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true))
                    .append(Component.literal("\n"))
                    .append(Component.literal(attacker.getName().getString())
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)))
                    .append(Component.literal(" seeks to wage war against your colony!")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                    .append(Component.literal("\n💰 Extortion Demand: " + extortionPercent + "% of your balance")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
                    .append(Component.literal("\n\nChoose your response:")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                    .append(Component.literal("\n"))
                    .append(createAcceptButton(targetColony))
                    .append(" ")
                    .append(createDeclineButton(targetColony))
                    .append(" ")
                    .append(createPayExtortionButton(targetColony, extortionPercent));
            owner.sendSystemMessage(message);
            attacker.sendSystemMessage(Component.literal(
                    "War declaration with " + extortionPercent + "% extortion demand sent to " + targetColony.getName())
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }
    }

    public static int processWarResponse(ServerPlayer executor, int colonyId, boolean accepted,
            CommandSourceStack source) {
        Object requestObj = pendingWarRequests.get(colonyId);
        java.util.UUID attackerUUID = null;
        if (requestObj instanceof WarRequest wr) {
            attackerUUID = wr.attacker();
        } else if (requestObj instanceof WarRequestWithExtortion wre) {
            attackerUUID = wre.attacker();
        }
        if (attackerUUID == null) {
            source.sendFailure(Component.literal("No active war request found for colony ID " + colonyId +
                    ". Only an authorized officer or the colony owner may accept.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn(
                    "No pending war or extortion war request found for colony ID {} when {} attempted to respond.",
                    colonyId, executor.getName().getString());
            return 0;
        }

        IColony targetColony = IColonyManager.getInstance().getColonyByDimension(colonyId,
                source.getLevel().dimension());
        if (targetColony == null) {
            source.sendFailure(Component.literal("Target colony not found.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.error("Target colony (ID {}) not found during war response by {}.", colonyId,
                    executor.getName().getString());
            return 0;
        }

        Rank executorRank = targetColony.getPermissions().getRank(executor.getUUID());
        boolean isAuthorized = executor.getUUID().equals(targetColony.getPermissions().getOwner()) ||
                (executorRank != null && executorRank.isColonyManager());
        if (!isAuthorized) {
            source.sendFailure(Component.literal("You are not authorized to accept/decline this war request.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn("{} is not authorized to respond to war request for colony {}.",
                    executor.getName().getString(), targetColony.getName());
            return 0;
        }

        final ServerPlayer attacker; // Declared final
        if (source.getServer() != null) {
            attacker = source.getServer().getPlayerList().getPlayer(attackerUUID);
        } else {
            attacker = null; // Ensure attacker is initialized if server is null
        }

        if (attacker == null) {
            source.sendFailure(Component.literal("Attacker is offline!")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn("Attacker {} is offline when {} tried to respond to war request for colony {}.",
                    attackerUUID, executor.getName().getString(), targetColony.getName());
            return 0;
        }
        pendingWarRequests.remove(colonyId);

        if (accepted) {
            WARSYSTEM_LOGGER.info("War request for colony {} accepted by {}.", targetColony.getID(),
                    executor.getName().getString());
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                        .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                        .findFirst().orElse(null);
                String attackerColonyName = attackerColony != null ? attackerColony.getName()
                        : attacker.getName().getString() + "'s forces";

                MutableComponent warAcceptedMsg = Component.literal("✅ WAR ACCEPTED! ✅")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\n----------------------------------------")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("\nThe colony of ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
                                ChatFormatting.BOLD))
                        .append(Component.literal(" (led by ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(executor.getName().getString()).withStyle(ChatFormatting.BLUE))
                        .append(Component.literal(") has accepted the challenge! War against ")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                                ChatFormatting.BOLD))
                        .append(Component.literal(" will now proceed to the join phase!")
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("\n----------------------------------------")
                                .withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(warAcceptedMsg);
            }
            startJoinPhase(targetColony, attacker, executor);
        } else {
            WARSYSTEM_LOGGER.info("War request for colony {} declined by {}.", targetColony.getID(),
                    executor.getName().getString());
            executor.sendSystemMessage(Component.literal("❌ War declaration declined!").withStyle(ChatFormatting.RED,
                    ChatFormatting.BOLD));
            attacker.sendSystemMessage(Component.literal("❌ " + targetColony.getName() + " declined your war request!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                Component warDeclinedMsg = Component.empty()
                        .append(Component.literal("❌ WAR DECLINED ❌").withStyle(ChatFormatting.RED,
                                ChatFormatting.BOLD))
                        .append(Component.literal("\n----------------------------------------")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("\nThe colony of ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
                                ChatFormatting.BOLD))
                        .append(Component.literal(" (led by ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(executor.getName().getString()).withStyle(ChatFormatting.BLUE))
                        .append(Component.literal(") has declined the war declaration.")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n----------------------------------------")
                                .withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(warDeclinedMsg);
            }
        }
        return 1;
    }

    private static Component createAcceptButton(IColony colony) {
        return Component.literal("[Accept]")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format("/wnt war accept %d", colony.getID()))));
    }

    private static Component createDeclineButton(IColony colony) {
        return Component.literal("[Decline]")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format("/wnt war decline %d", colony.getID()))));
    }

    private static Component createStartWarButton(IColony colony) {
        return Component.literal("[⚔️ START WAR NOW]")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format("/wnt war accept %d", colony.getID())))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to start the war immediately")
                                        .withStyle(ChatFormatting.RED))));
    }

    /**
     * Checks if a player can join a specific war based on their team membership,
     * colony membership, and ranks.
     */
    private static boolean canPlayerJoinWar(ServerPlayer player, WarData war) {
        // Don't allow primary participants to join via this method
        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
                player.getUUID().equals(war.getAttacker()) ||
                (war.getAttackerColony() != null
                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
            return false;
        }

        // Check if war is in join phase
        if (!war.isJoinPhaseActive() || System.currentTimeMillis() >= war.getJoinPhaseEndTime()) {
            return false;
        }

        // Check FTB Teams
        if (FTB_TEAMS_INSTALLED) {
            FtbTeamsCompat.TeamHandle playerTeam = FtbTeamsCompat.getTeamForPlayer(player.getUUID()).orElse(null);
            FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
            FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);

            // Direct team membership
            UUID playerTeamId = playerTeam == null ? null : FtbTeamsCompat.getTeamId(playerTeam);
            if (playerTeamId != null && (playerTeamId.equals(war.getAttackerTeamID()) ||
                    playerTeamId.equals(war.getDefenderTeamID()))) {
                return true;
            }

            // Allied team membership
            if (FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())
                    || FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
                return true;
            }
        }

        // Check Minecolonies colony membership and ranks
        IColony attackerColony = war.getAttackerColony();
        IColony defenderColony = war.getColony();

        // Check if player is in attacker colony with appropriate rank
        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            if (attackerPerms.getPlayers().containsKey(player.getUUID())) {
                Rank playerRank = attackerPerms.getRank(player.getUUID());
                if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) ||
                        playerRank.equals(attackerPerms.getRankOfficer()) ||
                        playerRank.equals(attackerPerms.getRankFriend()))) {
                    return true;
                }
            }
        }

        // Check if player is in defender colony with appropriate rank
        if (defenderColony != null) {
            IPermissions defenderPerms = defenderColony.getPermissions();
            if (defenderPerms.getPlayers().containsKey(player.getUUID())) {
                Rank playerRank = defenderPerms.getRank(player.getUUID());
                if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
                        playerRank.equals(defenderPerms.getRankOfficer()) ||
                        playerRank.equals(defenderPerms.getRankFriend()))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static int processJoinWar(ServerPlayer player, CommandSourceStack source) {
        // Find any active war that the player might be eligible to join
        WarData war = null;
        for (WarData activeWar : ACTIVE_WARS.values()) {
            if (canPlayerJoinWar(player, activeWar)) {
                war = activeWar;
                break;
            }
        }

        if (war == null) {
            source.sendFailure(Component.literal("No active war to join."));
            return 0;
        }

        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
                player.getUUID().equals(war.getAttacker()) ||
                (war.getAttackerColony() != null
                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
            source.sendFailure(Component.literal("Primary war participants cannot use this command to join/leave."));
            return 0;
        }

        if (System.currentTimeMillis() >= war.getJoinPhaseEndTime()) {
            source.sendFailure(Component.literal("Join phase is over."));
            return 0;
        }

        if (!war.isJoinPhaseActive()) {
            source.sendFailure(Component.literal("Join phase is over."));
            return 0;
        }

        int playerLives = TaxConfig.PLAYER_LIVES_IN_WAR.get();

        // Check if already joined
        if (war.getAttackerLives().containsKey(player.getUUID())
                || war.getDefenderLives().containsKey(player.getUUID())) {
            source.sendFailure(Component.literal("You are already registered in this war."));
            return 0;
        }

        // Determine which side the player should join based on various criteria
        boolean canJoinAttackers = false;
        boolean canJoinDefenders = false;

        // Check FTB Teams first
        if (FTB_TEAMS_INSTALLED) {
            FtbTeamsCompat.TeamHandle playerTeam = FtbTeamsCompat.getTeamForPlayer(player.getUUID()).orElse(null);
            FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
            FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);

            // Direct team membership
            UUID playerTeamId = playerTeam == null ? null : FtbTeamsCompat.getTeamId(playerTeam);
            if (playerTeamId != null && playerTeamId.equals(war.getAttackerTeamID())) {
                canJoinAttackers = true;
            }
            if (playerTeamId != null && playerTeamId.equals(war.getDefenderTeamID())) {
                canJoinDefenders = true;
            }

            // Allied team membership
            if (FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())) {
                canJoinAttackers = true;
            }
            if (FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
                canJoinDefenders = true;
            }
        }

        // Check Minecolonies colony membership and ranks
        IColony attackerColony = war.getAttackerColony();
        IColony defenderColony = war.getColony();

        // Check if player is in attacker colony with appropriate rank
        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            if (attackerPerms.getPlayers().containsKey(player.getUUID())) {
                Rank playerRank = attackerPerms.getRank(player.getUUID());
                if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) ||
                        playerRank.equals(attackerPerms.getRankOfficer()) ||
                        playerRank.equals(attackerPerms.getRankFriend()))) {
                    canJoinAttackers = true;
                }
            }
        }

        // Check if player is in defender colony with appropriate rank
        if (defenderColony != null) {
            IPermissions defenderPerms = defenderColony.getPermissions();
            if (defenderPerms.getPlayers().containsKey(player.getUUID())) {
                Rank playerRank = defenderPerms.getRank(player.getUUID());
                if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
                        playerRank.equals(defenderPerms.getRankOfficer()) ||
                        playerRank.equals(defenderPerms.getRankFriend()))) {
                    canJoinDefenders = true;
                }
            }
        }

        // Handle the case where player can join both sides
        if (canJoinAttackers && canJoinDefenders) {
            MutableComponent message = Component
                    .literal("You are eligible to join both sides. Please choose which side to join:\n")
                    .withStyle(ChatFormatting.GOLD);

            Component joinAttackers = Component.literal("[Join Attackers]")
                    .withStyle(style -> style.withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt choosewarside attacker"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to join the attacking side")
                                            .withStyle(ChatFormatting.GOLD))));

            Component joinDefenders = Component.literal("[Join Defenders]")
                    .withStyle(style -> style.withColor(ChatFormatting.BLUE)
                            .withBold(true)
                            .withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt choosewarside defender"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to join the defending side")
                                            .withStyle(ChatFormatting.GOLD))));

            player.sendSystemMessage(message.append(" ")
                    .append(joinAttackers).append(" ")
                    .append(joinDefenders));
            return 1;
        }

        // Join the appropriate side
        if (canJoinAttackers) {
            war.getAttackerLives().put(player.getUUID(), playerLives);
            war.getAttackerAllies().add(player.getUUID());

            // Assign hostile rank to this attacker on defender's colony
            assignWarParticipantRanks(player.getUUID(), war.getColony(), war.getAttackerColony(), true);

            player.sendSystemMessage(
                    Component.literal("You have joined the attacking side!").withStyle(ChatFormatting.GREEN));
            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                war.alliesBossEvent.addPlayer(player);
            } else {
                war.bossEvent.addPlayer(player);
            }
            return 1;
        } else if (canJoinDefenders) {
            war.getDefenderLives().put(player.getUUID(), playerLives);
            war.getDefenderAllies().add(player.getUUID());

            // Assign hostile rank to this defender on attacker's colony
            assignWarParticipantRanks(player.getUUID(), war.getColony(), war.getAttackerColony(), false);

            player.sendSystemMessage(
                    Component.literal("You have joined the defending side!").withStyle(ChatFormatting.GREEN));
            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                war.alliesBossEvent.addPlayer(player);
            } else {
                war.bossEvent.addPlayer(player);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal(
                    "You are not eligible to join this war. Only colony owners, officers, and friends can participate."));
            return 0;
        }
    }

    // --- Logic moved from WarCommands.leaveWar ---
    public static int processLeaveWar(ServerPlayer player, CommandSourceStack source) {
        WarData war = getActiveWarForPlayer(player);
        if (war == null) {
            source.sendFailure(Component.literal("No active war to leave."));
            return 0;
        }

        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
                player.getUUID().equals(war.getAttacker()) ||
                (war.getAttackerColony() != null
                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
            source.sendFailure(Component.literal("Primary war participants cannot leave the war."));
            return 0;
        }

        if (System.currentTimeMillis() >= war.getJoinPhaseEndTime()) {
            source.sendFailure(Component.literal("Join phase is over; you cannot leave now."));
            return 0;
        }

        boolean removedFromAttackers = war.getAttackerLives().remove(player.getUUID()) != null;
        if (removedFromAttackers)
            war.getAttackerAllies().remove(player.getUUID());

        boolean removedFromDefenders = war.getDefenderLives().remove(player.getUUID()) != null;
        if (removedFromDefenders)
            war.getDefenderAllies().remove(player.getUUID());

        if (removedFromAttackers || removedFromDefenders) {
            source.sendSuccess(() -> Component.literal("You have left the war."), false);
            if (war.alliesBossEvent != null)
                war.alliesBossEvent.removePlayer(player);
            if (war.bossEvent != null)
                war.bossEvent.removePlayer(player);
            return 1;
        } else {
            source.sendFailure(Component.literal("You were not registered in the war to leave."));
            return 0;
        }
    }

    public static IColony findColonyByName(String name, Level level) {
        return IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /**
     * Counts the number of guard citizens in a colony.
     * 
     * @param colony The colony to count guards for
     * @return The number of guards in the colony, or 0 if the colony is invalid
     */
    public static int countGuards(IColony colony) {
        if (colony == null || colony.getCitizenManager() == null)
            return 0;
        return (int) colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getJob() != null && c.getJob().isGuard())
                .count();
    }

    /**
     * Initialize the militia system for both colonies in a war for proper
     * guard/militia tracking.
     * 
     * @param war The war data containing both colonies
     */
    private static void initializeWarMilitiaSystem(WarData war) {
        // Initialize tracking for defending colony
        net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                .initializeColonyMilitia(war.getColony().getID());

        // Initialize tracking for attacking colony (if available)
        if (war.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .initializeColonyMilitia(war.getAttackerColony().getID());
        }

        if (TaxConfig.isNormalLogging())
            WARSYSTEM_LOGGER.info("Initialized militia tracking system for war between {} and {}",
                    war.getAttackerColony() != null ? war.getAttackerColony().getName() : "Unknown",
                    war.getColony().getName());
    }

    /**
     * Activate militia for both colonies in a war if militia system is enabled.
     * 
     * @param war The war data containing both colonies
     */
    private static void activateWarMilitia(WarData war) {
        if (!TaxConfig.ENABLE_CITIZEN_MILITIA.get()) {
            // Even if militia is disabled, we need to set the total defender count for kill
            // tracking
            setWarDefenderCounts(war);
            if (TaxConfig.isNormalLogging())
                WARSYSTEM_LOGGER.info("Militia disabled - Set defender counts for war without militia activation");
            return;
        }

        // Activate militia for defending colony
        int defenderMilitia = net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                .activateMilitia(war.getColony());

        if (defenderMilitia > 0) {
            sendColonyMessage(war.getColony(), Component.literal("⚔ WAR MILITIA ACTIVATED ⚔")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component
                            .literal("\n" + defenderMilitia
                                    + " citizens have joined the militia to defend against the war!")
                            .withStyle(ChatFormatting.YELLOW)));
        }

        // Activate militia for attacking colony (if available)
        int attackerMilitia = 0;
        if (war.getAttackerColony() != null) {
            attackerMilitia = net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .activateMilitia(war.getAttackerColony());

            if (attackerMilitia > 0) {
                sendColonyMessage(war.getAttackerColony(), Component.literal("⚔ WAR MILITIA ACTIVATED ⚔")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component
                                .literal("\n" + attackerMilitia
                                        + " citizens have joined the militia for the war effort!")
                                .withStyle(ChatFormatting.YELLOW)));
            }
        }

        if (TaxConfig.isNormalLogging())
            WARSYSTEM_LOGGER.info("Activated war militia - Defenders: {} militia, Attackers: {} militia",
                    defenderMilitia, attackerMilitia);
    }

    /**
     * Set defender counts for war when militia is disabled but tracking is still
     * needed.
     * 
     * @param war The war data containing both colonies
     */
    private static void setWarDefenderCounts(WarData war) {
        // Count guards in defending colony
        int defenderGuards = countGuards(war.getColony());
        net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                .setTotalDefenders(war.getColony().getID(), defenderGuards);

        // Count guards in attacking colony (if available)
        if (war.getAttackerColony() != null) {
            int attackerGuards = countGuards(war.getAttackerColony());
            net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .setTotalDefenders(war.getAttackerColony().getID(), attackerGuards);
        }

        WARSYSTEM_LOGGER.info("Set war defender counts - Defending guards: {}, Attacking guards: {}",
                defenderGuards, war.getAttackerColony() != null ? countGuards(war.getAttackerColony()) : 0);
    }

    /**
     * Clean up the militia system for both colonies when a war ends.
     * 
     * @param war The war data containing both colonies
     */
    private static void cleanupWarMilitiaSystem(WarData war) {
        // Deactivate and cleanup militia for defending colony
        net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                .deactivateMilitia(war.getColony());
        net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                .clearColonyMilitia(war.getColony().getID());

        // Deactivate and cleanup militia for attacking colony (if available)
        if (war.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .deactivateMilitia(war.getAttackerColony());
            net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .clearColonyMilitia(war.getAttackerColony().getID());
        }

        WARSYSTEM_LOGGER.info("Cleaned up militia system for war between {} and {}",
                war.getAttackerColony() != null ? war.getAttackerColony().getName() : "Unknown",
                war.getColony().getName());
    }

    /**
     * Counts the number of guard towers in a colony.
     * 
     * @param colony The colony to count guard towers for
     * @return The number of guard towers in the colony, or 0 if the colony is
     *         invalid
     */
    public static int countGuardTowers(IColony colony) {
        if (colony == null)
            return 0;
        return (int) ColonyBuildingUtil.getBuildings(colony).stream()
                .filter(WarSystem::isGuardTower)
                .count();
    }

    /**
     * Determines if a building is a guard tower using multiple identification
     * methods.
     * 
     * @param building The building to check
     * @return true if the building is a guard tower, false otherwise
     */
    // Cache: building Class -> whether it's a guard tower. There are only a
    // handful of distinct building classes, so this stabilises after a few
    // ticks and removes per-call string allocations from the hot path.
    private static final java.util.Map<Class<?>, Boolean> GUARD_TOWER_CLASS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isGuardTower(IBuilding building) {
        if (building == null)
            return false;

        // Display name can be a PER-INSTANCE custom name (a player can rename a building),
        // so it must be evaluated per building — never cached by class. It's a cheap getter.
        String displayName = building.getBuildingDisplayName();
        if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) {
            return true;
        }

        // Class name and the schematic/structure name (from toString) are CLASS-stable —
        // cache that determination by class so the expensive building.toString() runs at
        // most once per building TYPE, not once per building each tax cycle (audit H8 +
        // codex correctness follow-up: the previous code cached the per-instance displayName
        // result by class, which could mis-count custom-named buildings).
        Boolean cached = GUARD_TOWER_CLASS_CACHE.get(building.getClass());
        if (cached != null) {
            return cached;
        }
        boolean result = computeClassIsGuardTower(building);
        GUARD_TOWER_CLASS_CACHE.put(building.getClass(), result);
        return result;
    }

    private static boolean computeClassIsGuardTower(IBuilding building) {
        // Method 2: Check if class name contains "guardtower"
        String className = building.getClass().getName().toLowerCase();
        if (className.contains("guardtower")) {
            return true;
        }

        // Method 3: Fallback on the schematic name in case the class structure changes.
        // The "guardtower" substring comes from the structure type (class-level), so the
        // match is class-stable even though toString may also contain instance data.
        try {
            String toString = building.toString().toLowerCase();
            if (toString.contains("guardtower") || toString.contains("guard_tower")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore any reflection exceptions
        }

        return false;
    }

    /**
     * Check if a colony is currently involved in any active war
     */
    public static boolean isColonyInWar(int colonyId) {
        for (WarData warData : ACTIVE_WARS.values()) {
            if (warData.getColony().getID() == colonyId) {
                return true;
            }
            // Also check if it's the attacker's colony
            if (warData.getAttackerColony() != null && warData.getAttackerColony().getID() == colonyId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a colony has extortion immunity
     */
    private static boolean hasExtortionImmunity(int colonyId) {
        Long immunityExpiration = extortionImmunity.get(colonyId);
        if (immunityExpiration == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime > immunityExpiration) {
            extortionImmunity.remove(colonyId); // Cleanup expired immunity
            return false;
        }

        return true;
    }

    /**
     * Grant extortion immunity to a colony
     */
    public static void grantExtortionImmunity(int colonyId) {
        long immunityDuration = TaxConfig.EXTORTION_IMMUNITY_HOURS.get() * 60 * 60 * 1000L; // Convert hours to
                                                                                            // milliseconds
        long immunityExpiration = System.currentTimeMillis() + immunityDuration;
        extortionImmunity.put(colonyId, immunityExpiration);

        WARSYSTEM_LOGGER.info("Colony {} granted extortion immunity for {} hours", colonyId,
                TaxConfig.EXTORTION_IMMUNITY_HOURS.get());
    }

    /**
     * Shows the extortion choice prompt to the defender with enhanced clickable
     * buttons and 5-minute timer
     */
    private static void showExtortionChoiceWithTimer(ServerPlayer attacker, IColony targetColony, ServerPlayer owner,
            int extortionPercent) {
        // Add the extortion request to pending requests during timer period
        pendingWarRequests.put(targetColony.getID(),
                new WarRequestWithExtortion(attacker.getUUID(), targetColony.getID(), extortionPercent));

        // Calculate time limit
        int timeLimitMinutes = TaxConfig.EXTORTION_RESPONSE_TIME_MINUTES.get();
        long timeLimitMs = timeLimitMinutes * 60 * 1000L;

        MutableComponent message = Component
                .literal("🏛️ URGENT: Colony " + targetColony.getName() + " is under siege! 🏛️")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component
                        .literal("\n\n" + attacker.getName().getString() + " has declared war but offers terms:")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n💰 Pay " + extortionPercent + "% of your balance to avoid war")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n⚔️ Or let the war begin immediately (auto-accepted)")
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal("\n⏰ You have " + timeLimitMinutes + " minutes to decide!")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal("\n\nChoose quickly:\n").withStyle(ChatFormatting.WHITE))
                .append(createStartWarButton(targetColony));
    }

    /**
     * Shows the extortion choice prompt to the defender with enhanced clickable
     * buttons
     */
    private static void showExtortionChoice(ServerPlayer attacker, IColony targetColony, ServerPlayer owner,
            int extortionPercent) {
        MutableComponent message = Component.literal("🏛️ Colony " + targetColony.getName() + " is under siege! 🏛️\n")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component
                        .literal("\n" + attacker.getName().getString() + " has declared war but offers terms:\n")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("💰 Pay " + extortionPercent + "% of your balance to avoid war\n")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("⚔️ Or accept the war and fight for your colony's honor\n")
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal("\nChoose wisely:\n").withStyle(ChatFormatting.WHITE))
                .append(createAcceptButton(targetColony))
                .append(" ")
                .append(createDeclineButton(targetColony))
                .append(" ")
                .append(createPayExtortionButton(targetColony, extortionPercent));

        owner.sendSystemMessage(message);
    }

    /**
     * Creates a clickable button to pay extortion
     */
    private static MutableComponent createPayExtortionButton(IColony colony, int extortionPercent) {
        return Component.literal("[💰 PAY EXTORTION " + extortionPercent + "%]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/wnt payextortion " + colony.getID() + " " + extortionPercent))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to pay " + extortionPercent + "% of your balance to avoid war")
                                        .withStyle(ChatFormatting.YELLOW))));
    }

    // ==================== WAR PERSISTENCE ====================

    private static final Gson WAR_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String WAR_STORAGE_FILE = "config/warntax/active_wars.json";

    private static class WarSaveEntry {
        String warID;
        String attacker;
        String defender;
        String attackerTeamID;
        String defenderTeamID;
        int defenderColonyId;
        int attackerColonyId;
        long warStartTime;
        long joinPhaseEndTime;
        String status;
        boolean accepted;
        boolean stalemateTriggered;
        Map<String, Integer> attackerLives;
        Map<String, Integer> defenderLives;
        List<Integer> defenderGuardIDs;
        List<Integer> attackerGuardIDs;
        List<String> attackerAllies;
        List<String> defenderAllies;
        List<String> spectators;
        List<String> lastLifeInventoryPreservation;
        int initialAttackerGuards;
        int remainingAttackerGuards;
        int initialDefenderGuards;
        int remainingDefenderGuards;
        int initialAttackerTotalLives;
        int initialDefenderTotalLives;
        String penaltyReport;
        // Added 2026-05-25 (audit fix): previously these fields were silently dropped
        // on save/restore — see WarData restoration constructor docstring.
        Map<String, Boolean> originalHostilePerms;            // Action.name() -> boolean
        Map<String, Boolean> originalHostilePermsForAttacker;
        List<String> acceptedAllies;
        List<String> declinedAllies;
        boolean offlineOutpostWar;
        ProposalSaveEntry activeProposal; // null if no proposal in flight
    }

    private static class ProposalSaveEntry {
        String type;     // PeaceProposal.Type.name()
        int amount;
        String proposer; // UUID.toString()
        long createdTime;
    }

    private static class WarSaveData {
        List<WarSaveEntry> wars;
    }

    public static void saveActiveWars() {
        try {
            Path path = Paths.get(WAR_STORAGE_FILE);
            Files.createDirectories(path.getParent());

            WarSaveData saveData = new WarSaveData();
            saveData.wars = new ArrayList<>();

            for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
                WarData war = entry.getValue();

                // Finding 11 (audit CRIT — CRASH-3b): defenderTeamID can be null for
                // wars against abandoned colonies (no FTB Teams + colony owner null).
                // Previously war.getDefenderTeamID().toString() NPE'd here, aborting
                // the save loop and dropping ALL subsequent wars from disk.
                // Write a sentinel UUID instead and log a warning so the war still
                // round-trips. (Loader treats sentinel as "no defender team".)
                UUID atkTid = war.getAttackerTeamID();
                UUID defTid = war.getDefenderTeamID();
                if (atkTid == null) {
                    WARSYSTEM_LOGGER.warn("War {} for colony {} has null attackerTeamID; persisting with sentinel UUID.",
                            war.getWarID(), entry.getKey());
                    atkTid = NULL_TEAM_ID_SENTINEL;
                }
                if (defTid == null) {
                    WARSYSTEM_LOGGER.warn("War {} for colony {} has null defenderTeamID (abandoned colony?); persisting with sentinel UUID.",
                            war.getWarID(), entry.getKey());
                    defTid = NULL_TEAM_ID_SENTINEL;
                }

                WarSaveEntry e = new WarSaveEntry();
                e.warID = war.getWarID().toString();
                e.attacker = war.getAttacker().toString();
                e.defender = war.getDefender().toString();
                e.attackerTeamID = atkTid.toString();
                e.defenderTeamID = defTid.toString();
                e.defenderColonyId = entry.getKey();
                e.attackerColonyId = war.getAttackerColony() != null ? war.getAttackerColony().getID() : -1;
                e.warStartTime = war.warStartTime;
                e.joinPhaseEndTime = war.joinPhaseEndTime;
                e.status = war.getStatus().name();
                e.accepted = war.isAccepted();
                e.stalemateTriggered = war.isStalemateTriggered();
                e.penaltyReport = war.getPenaltyReport();
                e.initialAttackerGuards = war.initialAttackerGuards;
                e.remainingAttackerGuards = war.remainingAttackerGuards;
                e.initialDefenderGuards = war.initialDefenderGuards;
                e.remainingDefenderGuards = war.remainingDefenderGuards;
                e.initialAttackerTotalLives = war.initialAttackerTotalLives;
                e.initialDefenderTotalLives = war.initialDefenderTotalLives;

                e.attackerLives = new HashMap<>();
                war.getAttackerLives().forEach((uuid, lives) -> e.attackerLives.put(uuid.toString(), lives));
                e.defenderLives = new HashMap<>();
                war.getDefenderLives().forEach((uuid, lives) -> e.defenderLives.put(uuid.toString(), lives));

                e.defenderGuardIDs = new ArrayList<>(war.getDefenderGuardIDs());
                e.attackerGuardIDs = new ArrayList<>(war.getAttackerGuardIDs());
                e.attackerAllies = new ArrayList<>();
                war.getAttackerAllies().forEach(uuid -> e.attackerAllies.add(uuid.toString()));
                e.defenderAllies = new ArrayList<>();
                war.getDefenderAllies().forEach(uuid -> e.defenderAllies.add(uuid.toString()));
                e.spectators = new ArrayList<>();
                war.getSpectators().forEach(uuid -> e.spectators.add(uuid.toString()));
                e.lastLifeInventoryPreservation = new ArrayList<>();
                war.getLastLifeInventoryPreservation()
                        .forEach(uuid -> e.lastLifeInventoryPreservation.add(uuid.toString()));

                // Previously-dropped fields. Stored as Action.name() -> Boolean so the
                // serialized form is forward/backward-compat with Action enum changes.
                if (war.originalHostilePerms != null) {
                    e.originalHostilePerms = new HashMap<>();
                    war.originalHostilePerms.forEach((a, b) -> e.originalHostilePerms.put(a.name(), b));
                }
                if (war.originalHostilePermsForAttacker != null) {
                    e.originalHostilePermsForAttacker = new HashMap<>();
                    war.originalHostilePermsForAttacker.forEach((a, b) -> e.originalHostilePermsForAttacker.put(a.name(), b));
                }
                e.acceptedAllies = new ArrayList<>();
                war.getAcceptedAllies().forEach(uuid -> e.acceptedAllies.add(uuid.toString()));
                e.declinedAllies = new ArrayList<>();
                war.getDeclinedAllies().forEach(uuid -> e.declinedAllies.add(uuid.toString()));
                e.offlineOutpostWar = war.isOfflineOutpostWar();

                net.machiavelli.minecolonytax.peace.PeaceProposal pp = war.getActiveProposal();
                if (pp != null) {
                    ProposalSaveEntry pe = new ProposalSaveEntry();
                    pe.type = pp.getType().name();
                    pe.amount = pp.getAmount();
                    pe.proposer = pp.getProposer() != null ? pp.getProposer().toString() : null;
                    pe.createdTime = pp.getCreatedTime();
                    e.activeProposal = pe;
                }

                saveData.wars.add(e);
            }

            // Finding 3: atomic write — write to a tmp file, then atomic-move it
            // over the live file. Falls back to a plain replace on Windows builds
            // that lack ATOMIC_MOVE support (catches AtomicMoveNotSupportedException).
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = new FileWriter(tmp.toFile())) {
                WAR_GSON.toJson(saveData, writer);
            }
            try {
                Files.move(tmp, path,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException windowsFallback) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            WARSYSTEM_LOGGER.info("Saved {} active wars to {}", saveData.wars.size(), WAR_STORAGE_FILE);
        } catch (Exception ex) {
            WARSYSTEM_LOGGER.error("Failed to save active wars", ex);
        }
    }

    /** Sentinel UUID used in serialized form when an attacker/defender team ID was null at save time. */
    private static final UUID NULL_TEAM_ID_SENTINEL = new UUID(0L, 0L);

    public static void loadAndResumeActiveWars() {
        Path path = Paths.get(WAR_STORAGE_FILE);
        if (!Files.exists(path)) {
            WARSYSTEM_LOGGER.info("No saved wars file found, skipping war restoration");
            return;
        }

        try (Reader reader = new FileReader(path.toFile())) {
            WarSaveData saveData = WAR_GSON.fromJson(reader, WarSaveData.class);
            if (saveData == null || saveData.wars == null || saveData.wars.isEmpty()) {
                WARSYSTEM_LOGGER.info("No wars to restore from save file");
                Files.deleteIfExists(path);
                return;
            }

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                WARSYSTEM_LOGGER.warn("Cannot restore wars: server not available");
                return;
            }

            int restored = 0;
            int skipped = 0;
            int total = saveData.wars.size();

            for (WarSaveEntry e : saveData.wars) {
                try {
                    if (resumeWarFromSave(e, server)) {
                        restored++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    WARSYSTEM_LOGGER.error("Failed to restore war {} for colony {}", e.warID, e.defenderColonyId, ex);
                    skipped++;
                }
            }

            WARSYSTEM_LOGGER.info("War restoration complete: {} restored, {} skipped", restored, skipped);

            // Finding 4: only delete the source file when EVERY war was successfully
            // restored. On partial failure, rename the file to active_wars.json.failed-<ts>
            // for forensic recovery — never silently drop unrestored entries.
            if (skipped == 0) {
                Files.deleteIfExists(path);
            } else {
                Path failedPath = path.resolveSibling(
                        path.getFileName() + ".failed-" + System.currentTimeMillis());
                try {
                    Files.move(path, failedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    WARSYSTEM_LOGGER.warn("Partial war restore ({} of {} skipped). Original save preserved at {}",
                            skipped, total, failedPath);
                } catch (Exception moveEx) {
                    WARSYSTEM_LOGGER.error("Could not move active_wars.json to .failed-<ts>; leaving it in place at {}",
                            path, moveEx);
                }
            }

        } catch (Exception ex) {
            WARSYSTEM_LOGGER.error("Failed to load active wars from disk", ex);
        }
    }

    private static boolean resumeWarFromSave(WarSaveEntry e, MinecraftServer server) {
        IColony defenderColony = null;
        IColony attackerColony = null;
        for (Level level : server.getAllLevels()) {
            if (defenderColony == null) {
                defenderColony = IColonyManager.getInstance().getColonies(level).stream()
                        .filter(c -> c.getID() == e.defenderColonyId).findFirst().orElse(null);
            }
            if (e.attackerColonyId > 0 && attackerColony == null) {
                attackerColony = IColonyManager.getInstance().getColonies(level).stream()
                        .filter(c -> c.getID() == e.attackerColonyId).findFirst().orElse(null);
            }
            if (defenderColony != null && (e.attackerColonyId <= 0 || attackerColony != null))
                break;
        }

        if (defenderColony == null) {
            WARSYSTEM_LOGGER.warn("Cannot restore war {}: defender colony {} no longer exists", e.warID,
                    e.defenderColonyId);
            return false;
        }
        if (e.attackerColonyId > 0 && attackerColony == null) {
            WARSYSTEM_LOGGER.warn("Cannot restore war {}: attacker colony {} no longer exists", e.warID,
                    e.attackerColonyId);
            return false;
        }

        WarData.WarStatus status;
        try {
            status = WarData.WarStatus.valueOf(e.status);
        } catch (IllegalArgumentException ex) {
            WARSYSTEM_LOGGER.warn("Cannot restore war {}: invalid status '{}'", e.warID, e.status);
            return false;
        }

        Map<UUID, Integer> attackerLives = new HashMap<>();
        if (e.attackerLives != null) {
            e.attackerLives.forEach((k, v) -> attackerLives.put(UUID.fromString(k), v));
        }
        Map<UUID, Integer> defenderLives = new HashMap<>();
        if (e.defenderLives != null) {
            e.defenderLives.forEach((k, v) -> defenderLives.put(UUID.fromString(k), v));
        }
        Set<Integer> defenderGuardIDSet = e.defenderGuardIDs != null ? new HashSet<>(e.defenderGuardIDs) : new HashSet<>();
        Set<Integer> attackerGuardIDSet = e.attackerGuardIDs != null ? new HashSet<>(e.attackerGuardIDs) : new HashSet<>();
        Set<UUID> attackerAlliesSet = parseUUIDList(e.attackerAllies);
        Set<UUID> defenderAlliesSet = parseUUIDList(e.defenderAllies);
        Set<UUID> spectatorsSet = parseUUIDList(e.spectators);
        Set<UUID> lastLifeSet = parseUUIDList(e.lastLifeInventoryPreservation);

        long now = System.currentTimeMillis();
        // Finding 5: wars that ran out their clock while the server was down
        // previously logged "expired during server downtime, skipping" and gave
        // the victor zero rewards / no occupation / no rank cleanup. Instead,
        // construct the WarData, register it in ACTIVE_WARS, then immediately
        // run the normal end-of-war handler so reparations, ranks, occupation
        // hooks, and history all fire. handleTimeExpiry() is the canonical
        // end-of-time-elapsed entry point used by the countdown tick.
        boolean expiredDuringDowntime = false;
        if (status == WarData.WarStatus.INWAR) {
            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
            if (now >= e.warStartTime + warDurationMs) {
                WARSYSTEM_LOGGER.info("War {} expired during server downtime — resolving via handleTimeExpiry", e.warID);
                expiredDuringDowntime = true;
            }
        } else if (status == WarData.WarStatus.JOINING) {
            if (now >= e.joinPhaseEndTime) {
                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
                status = WarData.WarStatus.INWAR;
                e.warStartTime = now;
            }
        }

        // Convert sentinel team IDs (written by saveActiveWars for wars whose
        // team IDs were null at save time) back into null so callers see the
        // same invariant they had before the save.
        UUID atkTid;
        UUID defTid;
        try {
            atkTid = UUID.fromString(e.attackerTeamID);
            if (NULL_TEAM_ID_SENTINEL.equals(atkTid)) atkTid = null;
        } catch (IllegalArgumentException iae) { atkTid = null; }
        try {
            defTid = UUID.fromString(e.defenderTeamID);
            if (NULL_TEAM_ID_SENTINEL.equals(defTid)) defTid = null;
        } catch (IllegalArgumentException iae) { defTid = null; }

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("War for " + defenderColony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bossEvent.setProgress(1.0f);
        bossEvent.setVisible(true);

        // Reconstruct restored fields (formerly silently dropped — see WarData.java).
        Map<Action, Boolean> restoredHostilePerms = null;
        if (e.originalHostilePerms != null) {
            restoredHostilePerms = new HashMap<>();
            for (Map.Entry<String, Boolean> en : e.originalHostilePerms.entrySet()) {
                try { restoredHostilePerms.put(Action.valueOf(en.getKey()), en.getValue()); }
                catch (IllegalArgumentException ignored) {} // forward-compat: skip unknown Action names
            }
        }
        Map<Action, Boolean> restoredHostilePermsAtk = null;
        if (e.originalHostilePermsForAttacker != null) {
            restoredHostilePermsAtk = new HashMap<>();
            for (Map.Entry<String, Boolean> en : e.originalHostilePermsForAttacker.entrySet()) {
                try { restoredHostilePermsAtk.put(Action.valueOf(en.getKey()), en.getValue()); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        Set<UUID> acceptedAlliesSet = parseUUIDList(e.acceptedAllies);
        Set<UUID> declinedAlliesSet = parseUUIDList(e.declinedAllies);
        net.machiavelli.minecolonytax.peace.PeaceProposal restoredProposal = null;
        if (e.activeProposal != null && e.activeProposal.type != null && e.activeProposal.proposer != null) {
            try {
                restoredProposal = new net.machiavelli.minecolonytax.peace.PeaceProposal(
                        net.machiavelli.minecolonytax.peace.PeaceProposal.Type.valueOf(e.activeProposal.type),
                        e.activeProposal.amount,
                        UUID.fromString(e.activeProposal.proposer));
                // PeaceProposal.createdTime defaults to "now" on construction — close
                // enough for restored proposals; the timeout check is a relative delta.
            } catch (IllegalArgumentException ignored) {}
        }

        WarData warData = new WarData(
                UUID.fromString(e.warID),
                UUID.fromString(e.attacker),
                UUID.fromString(e.defender),
                atkTid,
                defTid,
                e.warStartTime, e.joinPhaseEndTime,
                bossEvent, defenderColony, attackerColony,
                status, e.accepted,
                e.initialAttackerGuards, e.remainingAttackerGuards,
                e.initialDefenderGuards, e.remainingDefenderGuards,
                e.initialAttackerTotalLives, e.initialDefenderTotalLives,
                attackerLives, defenderLives,
                defenderGuardIDSet, attackerGuardIDSet, attackerAlliesSet, defenderAlliesSet,
                spectatorsSet, lastLifeSet,
                e.penaltyReport, e.stalemateTriggered,
                restoredHostilePerms, restoredHostilePermsAtk,
                acceptedAlliesSet, declinedAlliesSet,
                e.offlineOutpostWar,
                restoredProposal);

        ACTIVE_WARS.put(e.defenderColonyId, warData);

        // Finding 5 cont'd: war ran past its clock while we were down. Register
        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
        // then immediately resolve. handleTimeExpiry takes care of victor logic,
        // reparations, ranks, etc. If the canonical end-of-time path lives at
        // a different entrypoint in this codebase, this still has the WarData
        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
        // TODO: if handleTimeExpiry isn't safe at boot (e.g. needs world ticks),
        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
        if (expiredDuringDowntime) {
            try {
                handleTimeExpiry(warData);
            } catch (Throwable t) {
                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
                try { endWar(defenderColony); } catch (Throwable t2) {
                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
                    ACTIVE_WARS.remove(e.defenderColonyId);
                }
            }
            // True for the loader: we processed this war successfully.
            return true;
        }

        // Restore defender tracking + drain scheduling
        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(e.defenderColonyId);
        scheduleTreasuryDrain(warData, defenderColony, attackerColony);

        for (UUID uuid : warData.getAttackerLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null)
                bossEvent.addPlayer(p);
        }
        for (UUID uuid : warData.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null)
                bossEvent.addPlayer(p);
        }

        setWarInteractionPermissions(defenderColony, true);
        if (attackerColony != null) {
            setWarInteractionPermissions(attackerColony, true);
        }

        final IColony finalDefenderColony = defenderColony;

        if (warData.getStatus() == WarData.WarStatus.INWAR) {
            applyWarGlowToParticipants(warData);
            applyGuardGlow(defenderColony);
            if (attackerColony != null) {
                applyGuardGlow(attackerColony);
            }

            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
            if (attackerColony != null) {
                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
            }

            startWarCountdown(warData);

            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
            long elapsed = now - warData.warStartTime;
            long remaining = warDurationMs - elapsed;
            if (remaining > 0) {
                scheduleTimerWarnings(warData, remaining);
            }

            updateBossBar(warData);
            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
                    e.warID, defenderColony.getName(), remaining);
        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
            warData.alliesBossEvent = new ServerBossEvent(
                    Component.literal("Joining War - " + defenderColony.getName()),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
            warData.alliesBossEvent.setProgress(1.0f);
            warData.alliesBossEvent.setVisible(true);

            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
            if (remainingJoinMs > 0) {
                final int colonyId = e.defenderColonyId;
                TickScheduler.scheduleDelayed(() -> {
                    if (!ACTIVE_WARS.containsKey(colonyId))
                        return;
                    WarData w = ACTIVE_WARS.get(colonyId);
                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
                        return;
                    w.setStatus(WarData.WarStatus.INWAR);
                    w.warStartTime = System.currentTimeMillis();
                    finalizeWarStart(w);
                    // finalizeWarStart() can end the war (no valid participants / bad ratio),
                    // removing it from ACTIVE_WARS. Don't enable permissions/countdown on an
                    // ended war (codex HIGH — same guard as the live join-start path).
                    if (ACTIVE_WARS.get(colonyId) != w) {
                        return;
                    }
                    setWarInteractionPermissions(w.getColony(), true);
                    if (w.getAttackerColony() != null) {
                        setWarInteractionPermissions(w.getAttackerColony(), true);
                    }
                    startWarCountdown(w);
                    // finalizeWarStart already scheduled the timer warnings on success (full WAR_DURATION),
                    // and the main start path doesn't re-call it either — drop the duplicate schedule here.
                }, remainingJoinMs);
            }

            updateBossBar(warData);
            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",
                    e.warID, defenderColony.getName(), remainingJoinMs);
        }

        Component restoreMsg = Component.literal("⚔ War Restored: ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component
                        .literal("The war for " + finalDefenderColony.getName()
                                + " has been resumed after server restart.")
                        .withStyle(ChatFormatting.YELLOW));
        broadcastToServer(restoreMsg);

        return true;
    }

    private static Set<UUID> parseUUIDList(List<String> list) {
        Set<UUID> result = new HashSet<>();
        if (list != null) {
            for (String s : list) {
                try {
                    result.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return result;
    }
}