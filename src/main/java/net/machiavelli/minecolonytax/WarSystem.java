package net.machiavelli.minecolonytax;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding; // Corrected import
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.compat.FtbTeamsCompat;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.event.WarEconomyHandler;
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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.machiavelli.minecolonytax.event.WarEventHandler;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.machiavelli.minecolonytax.peace.PeaceProposal;

public class WarSystem {

    private static final Logger WARSYSTEM_LOGGER = LogManager.getLogger(WarSystem.class);
    public static final Map<Integer, Object> pendingWarRequests = new ConcurrentHashMap<>();
    
    // Track extortion immunity (colonyId -> immunity expiration timestamp)
    private static final Map<Integer, Long> extortionImmunity = new ConcurrentHashMap<>();
    public record WarRequest(UUID attacker, int colonyId) { }
    public record WarRequestWithExtortion(UUID attacker, int colonyId, int extortionPercent) { }

    private static boolean isOfficerOrFriendly(IColony colony, UUID playerUUID) {
        if (colony == null || playerUUID == null) {
            return false;
        }
        
        Rank rank = colony.getPermissions().getRank(playerUUID);
        if (rank == null) {
            return false;
        }

        // isColonyManager checks for officers, and !rank.isHostile() includes any friendly non-enemy rank.
        return rank.isColonyManager() || !rank.isHostile();
    }

    /**
     * FTB Teams is OPTIONAL. This used to be a {@code public static final TeamManager} field, i.e.
     * an FTB type in WarSystem's own signature, initialised in its static block. That made loading
     * WarSystem - the class every war path runs through - depend on FTB Teams being present, so a
     * server without it hit NoClassDefFoundError instead of simply running without team support.
     * All FTB access now goes through the classloader-safe shim; no FTB type appears here.
     */
    public static final boolean FTB_TEAMS_INSTALLED = FtbTeamsCompat.isInstalled();
    public static final Map<Integer, WarData> ACTIVE_WARS = new ConcurrentHashMap<>();

    private static final Component JOIN_MSG = Component.literal("[Join War]")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt joinwar"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to join the war!").withStyle(style -> style.withColor(ChatFormatting.AQUA)))));

    private static final Component LEAVE_MSG = Component.literal("[Leave War]")
            .withStyle(style -> style.withColor(ChatFormatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt leavewar"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to leave the war!").withStyle(ChatFormatting.AQUA))));

    public static final long WAR_PHASE_DURATION_SECONDS = 60; // For debugging

    public static void initiateWar(ServerPlayer attacker, UUID defender, FtbTeamsCompat.TeamHandle attackerTeam,
            FtbTeamsCompat.TeamHandle defenderTeam, IColony colony, IColony attackerColony) {
        UUID attackerTeamID = attackerTeam != null ? FtbTeamsCompat.getTeamId(attackerTeam) : null;
        if (attackerTeamID == null) attackerTeamID = attacker.getUUID();
        UUID defenderTeamID = defenderTeam != null ? FtbTeamsCompat.getTeamId(defenderTeam) : null;
        if (defenderTeamID == null) defenderTeamID = colony.getPermissions().getOwner();

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("War for " + colony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bossEvent.setProgress(1.0f);
        bossEvent.setVisible(true);

        long now = System.currentTimeMillis();
        WarData data = new WarData(attacker.getUUID(), defender, attackerTeamID, defenderTeamID, now, bossEvent, colony, attackerColony);
        
        int playerLives = TaxConfig.PLAYER_LIVES_IN_WAR.get(); // Use config

        // Always start with the primary participants
        data.getAttackerLives().put(attacker.getUUID(), playerLives);
        data.getDefenderLives().put(colony.getPermissions().getOwner(), playerLives);
        
        // Assign hostile rank to the main attacker on defender's colony
        assignWarParticipantRanks(attacker.getUUID(), colony, attackerColony, true);
        
        // Add attacker colony members (Officers and Friends) using Minecolonies API
        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            WARSYSTEM_LOGGER.debug("[DEBUG] Adding attacker colony members from " + attackerColony.getName());
            
            attackerPerms.getPlayers().forEach((uuid, player) -> {
                if (!uuid.equals(attacker.getUUID())) { // Don't add attacker twice
                    Rank rank = attackerPerms.getRank(uuid);
                    if (rank != null && (rank.equals(attackerPerms.getRankOfficer()) || rank.equals(attackerPerms.getRankFriend()))) {
                        data.getAttackerLives().put(uuid, playerLives);
                        WARSYSTEM_LOGGER.debug("[DEBUG] Added attacker colony member " + uuid + " with rank " + rank.getName());
                        
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
                                        .append(Component.literal("\nYour colony (" + attackerColony.getName() + ") is attacking " + colony.getName() + "!")
                                               .withStyle(ChatFormatting.YELLOW))
                                        .append(Component.literal("\nAs an " + rank.getName() + ", you are eligible to join as an ATTACKER!")
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
        
        // Add defender colony members (Officers and Friends) using Minecolonies API
        IPermissions defenderPerms = colony.getPermissions();
        WARSYSTEM_LOGGER.debug("[DEBUG] Adding defender colony members from " + colony.getName());
        
        defenderPerms.getPlayers().forEach((uuid, player) -> {
            if (!uuid.equals(colony.getPermissions().getOwner())) { // Don't add owner twice
                Rank rank = defenderPerms.getRank(uuid);
                if (rank != null && (rank.equals(defenderPerms.getRankOfficer()) || rank.equals(defenderPerms.getRankFriend()))) {
                    data.getDefenderLives().put(uuid, playerLives);
                    WARSYSTEM_LOGGER.debug("[DEBUG] Added defender colony member " + uuid + " with rank " + rank.getName());
                    
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
                                    .append(Component.literal("\nYour colony (" + colony.getName() + ") is being attacked!")
                                           .withStyle(ChatFormatting.RED))
                                    .append(Component.literal("\nAs an " + rank.getName() + ", you are eligible to join as a DEFENDER!")
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
        
        // Optional: Add FTB Team members if FTB Teams is installed
        if (FTB_TEAMS_INSTALLED) {
            WARSYSTEM_LOGGER.debug("[DEBUG] FTB Teams detected, adding team members as additional participants");
            
            if (attackerTeam != null) {
                FtbTeamsCompat.getTeamMembers(attackerTeam).forEach(uuid -> {
                    if (!data.getAttackerLives().containsKey(uuid)) { // Don't add if already added via colony
                        data.getAttackerLives().put(uuid, playerLives);
                        WARSYSTEM_LOGGER.debug("[DEBUG] Added FTB team member to attackers: " + uuid);
                        
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
                                    .append(Component.literal("\nAs a team member, you are eligible to join as an ATTACKER!")
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
                        WARSYSTEM_LOGGER.debug("[DEBUG] Added FTB team member to defenders: " + uuid);
                        
                        // Assign hostile rank to this defender on attacker's colony (if it exists)
                        assignWarParticipantRanks(uuid, colony, attackerColony, false);
                        
                        // Send comprehensive join prompt
                        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                            ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                Component teamDefenseNotification = Component.literal("🛡️ TEAM COLONY UNDER ATTACK 🛡️")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                                    .append(Component.literal("\nYour team's colony (" + colony.getName() + ") is being attacked!")
                                           .withStyle(ChatFormatting.RED))
                                    .append(Component.literal("\nAs a team member, you are eligible to join as a DEFENDER!")
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

        // Mark the defender for the home-field drain reduction and start the war chest drain.
        // Neither was wired up on this branch: WarChestManager.drainWarChest had no callers at
        // all, so war chests never drained during a war and the configured auto-surrender could
        // never fire, while setColonyAsDefender was only ever called on the server-restart
        // restore path (so a freshly declared war gave the defender no drain reduction either).
        net.machiavelli.minecolonytax.economy.WarChestManager.setColonyAsDefender(colony.getID());
        scheduleWarChestDrain(data, colony, data.getAttackerColony());
    }

    /**
     * Schedule a repeating war chest drain for both sides. Drains every 60 seconds and saves
     * every 5 ticks; when either side runs dry the war ends (auto-surrender).
     */
    private static void scheduleWarChestDrain(WarData data, IColony defenderColony, IColony attackerColony) {
        if (!TaxConfig.isWarChestEnabled()) return;
        if (defenderColony == null) return;

        final int defenderColonyId = defenderColony.getID();
        final int attackerColonyId = attackerColony != null ? attackerColony.getID() : -1;
        final long[] tickCount = {0};

        data.warChestDrainTaskId = net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
            // "Still my war" identity guard. A drain task that outlives its war must not keep
            // draining, and must never end a replacement war registered under the same defender.
            if (ACTIVE_WARS.get(defenderColonyId) != data) {
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(data.warChestDrainTaskId);
                data.warChestDrainTaskId = -1L;
                return;
            }

            tickCount[0]++;

            int defenderResult = net.machiavelli.minecolonytax.economy.WarChestManager.drainWarChest(defenderColonyId);
            int attackerResult = attackerColonyId >= 0
                    ? net.machiavelli.minecolonytax.economy.WarChestManager.drainWarChest(attackerColonyId)
                    : Integer.MAX_VALUE;

            if (tickCount[0] % 5 == 0) {
                net.machiavelli.minecolonytax.economy.WarChestManager.save();
            }

            // ACTIVE_WARS is keyed by the DEFENDER colony id and endWar() resolves the war via
            // ACTIVE_WARS.remove(colony.getID()), so the war that ran out of funds is ended by
            // passing its DEFENDER — never the attacker, which would either find nothing (war
            // runs on forever, re-firing this branch every minute) or end an unrelated war in
            // which that attacker happens to be the defender.
            if (defenderResult == -1 || attackerResult == -1) {
                endWar(defenderColony);
            }
        }, 60_000, 60_000);
    }

    public static void setWarInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        for (Action a : TaxConfig.getWarActions()) {
            perms.setPermission(hostile, a, allowed);
        }
    }
    
    /**
     * Assigns appropriate ranks to war participants so they can interact with opposing colonies during war.
     * Attackers get hostile rank on defender colony, defenders get hostile rank on attacker colony.
     * 
     * @param playerUUID The UUID of the war participant
     * @param defenderColony The defending colony
     * @param attackerColony The attacking colony (can be null)
     * @param isAttacker True if the player is on attacking side, false if defending
     */
    private static void assignWarParticipantRanks(UUID playerUUID, IColony defenderColony, IColony attackerColony, boolean isAttacker) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        
        try {
            if (isAttacker) {
                // Attackers get hostile rank on defender colony
                IPermissions defenderPerms = defenderColony.getPermissions();
                defenderPerms.setPlayerRank(playerUUID, defenderPerms.getRankHostile(), defenderColony.getWorld());
                WARSYSTEM_LOGGER.debug("[DEBUG] Assigned hostile rank to attacker " + playerUUID + " on defender colony " + defenderColony.getName());
            } else {
                // Defenders get hostile rank on attacker colony (if it exists)
                if (attackerColony != null) {
                    IPermissions attackerPerms = attackerColony.getPermissions();
                    attackerPerms.setPlayerRank(playerUUID, attackerPerms.getRankHostile(), attackerColony.getWorld());
                    WARSYSTEM_LOGGER.debug("[DEBUG] Assigned hostile rank to defender " + playerUUID + " on attacker colony " + attackerColony.getName());
                }
            }
        } catch (Exception e) {
            WARSYSTEM_LOGGER.error("Failed to assign war participant ranks for player " + playerUUID, e);
        }
    }

    public static void setRaidInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        for (Action a : TaxConfig.getRaidActions()) {
            perms.setPermission(hostile, a, allowed);
        }
    }
    
    /**
     * Restore all colonies' war and raid permissions to their config defaults (disabled).
     * Should be called on server startup to clean up any leftover permissions from crashes/restarts.
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
                        // Disable war actions (set to false)
                        setWarInteractionPermissions(colony, false);
                        // Disable raid actions (set to false)
                        setRaidInteractionPermissions(colony, false);
                        coloniesRestored++;
                    }
                }
            }
            
            WARSYSTEM_LOGGER.info("Restored war/raid permissions to config defaults for {} colonies", coloniesRestored);
        } catch (Exception e) {
            WARSYSTEM_LOGGER.error("Failed to restore colony permissions to defaults", e);
        }
    }

    public static void updateBossBar(WarData war) {
        long now = System.currentTimeMillis();
        if (now < war.getJoinPhaseEndTime()) {
            long remainingMillis = war.getJoinPhaseEndTime() - now;
            String timeStr = String.format("%02d:%02d", remainingMillis / 60000, (remainingMillis / 1000) % 60);
            String joinText = Component.translatable("war.siege.status", war.getColony().getName(), timeStr).getString();
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

    /**
     * Send every participant a plain-language briefing of their role (attacker or defender) and how
     * the war is won, so the boss bar's shared numbers are backed by a clear "what am I and what do I
     * do" for each side. Called once when the war goes live.
     */
    private static void sendWarRoleBriefings(WarData war) {
        if (war.getColony() == null || war.getColony().getWorld() == null
                || war.getColony().getWorld().getServer() == null) return;
        MinecraftServer server = war.getColony().getWorld().getServer();
        String name = war.getColony().getName();

        Component attackerBrief = Component.literal("")
            .append(Component.literal("⚔ You are ATTACKING " + name + " — how it's won:\n")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("• You WIN by killing the colony's citizens or draining the defenders' lives before time runs out.\n")
                    .withStyle(ChatFormatting.GREEN))
            .append(Component.literal("• You LOSE if the timer runs out or your side's lives are spent.\n")
                    .withStyle(ChatFormatting.RED))
            .append(Component.literal("Press the assault AT the colony — retreating far past its border forfeits the war.")
                    .withStyle(ChatFormatting.GRAY));
        Component defenderBrief = Component.literal("")
            .append(Component.literal("🛡 You are DEFENDING " + name + " — how it's won:\n")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("• You WIN by outlasting the timer or draining the attackers' lives.\n")
                    .withStyle(ChatFormatting.GREEN))
            .append(Component.literal("• You LOSE if your citizens fall or your side's lives are spent.\n")
                    .withStyle(ChatFormatting.RED))
            .append(Component.literal("Hold your ground and cut the attackers down!")
                    .withStyle(ChatFormatting.GRAY));

        for (UUID uuid : war.getAttackerLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(attackerBrief);
        }
        for (UUID uuid : war.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(defenderBrief);
        }
    }

    /**
     * @return true if the war actually proceeded into INWAR; false if it was aborted here (no
     *         participants / unbalanced teams). The caller MUST NOT enable war permissions or start
     *         the countdown when this returns false — otherwise a war that failed its start checks
     *         keeps running as a half-initialized INWAR (no militia, no glow, no briefings) with a
     *         live countdown, corrupting guard-kill detection.
     */
    public static boolean finalizeWarStart(WarData war) {
        int attackerPlayerCount = war.getAttackerLives().size();
        int defenderPlayerCount = war.getDefenderLives().size();

        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component cancelMsg = Component.literal("War cancelled due to lack of participants.")
                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
                broadcastToServer(cancelMsg);
            }
            endWar(war.getColony());
            return false;
        }

        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component ratioMsg = Component.literal("Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
                        + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
                broadcastToServer(ratioMsg);
            }
            // End the war cleanly rather than returning into a half-initialized INWAR state.
            endWar(war.getColony());
            return false;
        }

        war.bossEvent.removeAllPlayers();
        war.getAttackerLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) war.bossEvent.addPlayer(p);
            }
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) war.bossEvent.addPlayer(p);
            }
        });

        if (war.alliesBossEvent != null) {
            war.alliesBossEvent.removeAllPlayers();
            war.alliesBossEvent.setVisible(false);
        }

        war.getAttackerLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    assignWarGroup(p);
                    // Hand out the Siege Banner to online attackers now that the war has actually
                    // started. This was previously wired only to login/reconnect, so an attacker
                    // online at war start (the normal case) never got one without relogging, making
                    // the Plant-the-Banner objective unplayable. No-op unless experimental objectives
                    // are enabled (the handout method self-gates).
                    net.machiavelli.minecolonytax.siege.PlantTheBannerObjective.giveSiegeBannerIfNeeded(p, war);
                }
            }
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) assignWarGroup(p);
            }
        });

        war.warStartTime = System.currentTimeMillis();
        war.setStatus(WarData.WarStatus.INWAR);
        updateBossBar(war);
        // Role briefings: tell each participant plainly which side they are and how the war is won.
        sendWarRoleBriefings(war);
        // Apply glow to both defender and attacker guards for clear visibility
        applyGuardGlow(war.getColony());
        if (war.getAttackerColony() != null) {
            applyGuardGlow(war.getAttackerColony());
        }
        applyWarGlowToParticipants(war);
        
        // Apply resistance effects to defending guards during war
        GuardResistanceHandler.applyResistanceToGuardsForWar(war.getColony());
        if (war.getAttackerColony() != null) {
            GuardResistanceHandler.applyResistanceToGuardsForWar(war.getAttackerColony());
        }
        
        // Initialize militia system for guard tracking and citizen conversion in BOTH colonies
        initializeWarMilitiaSystem(war);
        activateWarMilitia(war);
        if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName() : "Attacking Forces";
            String defenderColonyName = war.getColony().getName();

            Component warBeginMsg = Component.empty()
                .append(Component.translatable("war.begin.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.begin.body", attackerColonyName, defenderColonyName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastToServer(warBeginMsg);
        }
        long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
        scheduleTimerWarnings(war, warDurationMillis);
        return true;
    }

    private static void assignWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set war";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void resetWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set default";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void checkForVictory(WarData war) {
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
        // - Attackers win if all defenders are dead (0 lives) OR if all defender guards are dead
        // - Defenders win if all attackers are dead (0 lives) OR if all attacker guards are dead
        // - Priority: Player deaths take precedence over guard deaths for ending wars
        boolean attackersWin = (hasDefenders && allDefendersDead) || (!hasDefenders && allDefenderGuardsDead);
        boolean defendersWin = (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);

        // Only proceed if there's a clear victory condition
        if (!attackersWin && !defendersWin) {
            return;
        }
        
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null) return;
        
        WARSYSTEM_LOGGER.debug("[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: " + defendersWin);
        WARSYSTEM_LOGGER.debug("[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
        WARSYSTEM_LOGGER.debug("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: " + war.getRemainingDefenderGuards());

        if (defendersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName() : "The Attackers";
        Component victoryMsg = Component.empty()
            .append(Component.translatable("war.defenders.win.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("\n"))
            .append(Component.translatable("war.defenders.win.body", defenderColonyName, attackerColonyName).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\n"))
            .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
        sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), victoryMsg);
        for (UUID defenderUUID : war.getDefenderLives().keySet()) {
            ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
            if (defender != null) {
                }
            }
            // Apply victory/defeat balance transfers - defenders win, attackers pay
            applyWarEconomyTransfers(war, false);
            // War-history rows for the colony Events view (defender won, attacker lost)
            HistoryManager.getColonyHistory(war.getColony().getID()).addWarEntry(attackerColonyName, "VICTORY", 0);
            if (war.getAttackerColony() != null) {
                HistoryManager.getColonyHistory(war.getAttackerColony().getID()).addWarEntry(defenderColonyName, "DEFEAT", 0);
            }
            HistoryManager.saveHistory();
        } else if (attackersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName() : "The Attackers";
            Component conquestMsg = Component.empty()
                .append(Component.translatable("war.attackers.win.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.attackers.win.body", attackerColonyName, defenderColonyName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), conquestMsg);
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList().getPlayer(attackerUUID); 
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsWon(attackerPlayer);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                }
            }
            // Apply victory/defeat balance transfers - attackers win, defenders pay
            applyWarEconomyTransfers(war, true);
            // War-history rows for the colony Events view (attacker won, defender lost)
            HistoryManager.getColonyHistory(war.getColony().getID()).addWarEntry(attackerColonyName, "DEFEAT", 0);
            if (war.getAttackerColony() != null) {
                HistoryManager.getColonyHistory(war.getAttackerColony().getID()).addWarEntry(defenderColonyName, "VICTORY", 0);
            }
            HistoryManager.saveHistory();
            // --- Phase 4: per-colony "take over colony" outcome ---
            applyAttackerVictoryTakeover(war);
        }
        endWar(war.getColony());
    }

    /**
     * Applies the attacker-wins "take over colony" outcome to the defender colony
     * ({@code war.getColony()}):
     * <ul>
     *   <li>Nth (non-primary) colony → full conquest: the deed moves to the victor.</li>
     *   <li>Protected primary colony → vassalize-only: transferOwnership() refuses (via
     *       ColonyTierGuard) so a home base is never seized in open war, and the loser is
     *       vassalized + pays the one-time war-chest/wallet grab instead.</li>
     * </ul>
     * {@code EnablePrimaryColonyTransfer} overrides the protection. Shared by war-victory
     * resolution ({@link #checkForVictory}) and defender surrender.
     *
     * @return true if the deed was transferred (full conquest), false if vassalized/none.
     */
    public static boolean applyAttackerVictoryTakeover(WarData war) {
        if (war == null || war.getColony() == null) return false;

        boolean conquered = false;
        if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
            conquered = transferOwnership(war.getColony(), war.getAttacker());
        }
        if (!conquered && TaxConfig.isWarVassalizationEnabled()) {
            // Vassalize-only outcome: deed never moves; the loser pays tribute and a one-time
            // "huge money" grab (war chest % + player wallet %) goes to the victor.
            int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
            int durationHours = TaxConfig.getWarVassalizationDurationHours();
            boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                    war.getColony(), war.getAttacker(), tributePercent, durationHours);
            if (vassalized) {
                applyWarVassalizationMoneyGrab(war);
                WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} at {}% tribute",
                        war.getColony().getName(), war.getAttacker(), tributePercent);
                // If open-war conquest was enabled but this target was a protected primary,
                // tell the victor the deed can't be seized in open war — besiege to occupy.
                if (TaxConfig.ENABLE_COLONY_TRANSFER.get()
                        && war.getColony().getWorld() != null
                        && war.getColony().getWorld().getServer() != null) {
                    ServerPlayer victor = war.getColony().getWorld().getServer()
                            .getPlayerList().getPlayer(war.getAttacker());
                    if (victor != null) {
                        victor.sendSystemMessage(Component.literal(
                                war.getColony().getName() + " is a Primary colony — its deed cannot be seized in "
                              + "open war. It has been vassalized instead. Lay a besiege to occupy it permanently.")
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
        }
        return conquered;
    }

    /**
     * One-time "huge money" grab applied when a war victory results in vassalization (the
     * vassalize-only path, used when colony deed transfer is disabled). Moves a configurable
     * percentage of the losing colony's war chest and the losing player's wallet to the victor.
     * Both percentages are independently configurable; either can be disabled by setting it to 0.
     */
    private static void applyWarVassalizationMoneyGrab(WarData war) {
        try {
            IColony loserColony = war.getColony();
            IColony winnerColony = war.getAttackerColony();
            if (loserColony == null || loserColony.getWorld() == null) return;
            var server = loserColony.getWorld().getServer();
            if (server == null) return;

            // --- Colony war chest grab (cap-safe: credit first, then deduct exactly what landed) ---
            int treasuryPct = TaxConfig.getWarVassalizationTreasuryGrabPercent();
            if (treasuryPct > 0 && winnerColony != null) {
                int loserBal = net.machiavelli.minecolonytax.economy.WarChestManager
                        .getWarChestBalance(loserColony.getID());
                if (loserBal > 0) {
                    int requested = (int) Math.floor(loserBal * (treasuryPct / 100.0));
                    if (requested > 0) {
                        int winnerBefore = net.machiavelli.minecolonytax.economy.WarChestManager
                                .getWarChestBalance(winnerColony.getID());
                        int winnerAfter = net.machiavelli.minecolonytax.economy.WarChestManager
                                .addToWarChest(winnerColony.getID(), requested); // caps to WarChestMaxCapacity
                        int credited = winnerAfter - winnerBefore;
                        if (credited > 0) {
                            net.machiavelli.minecolonytax.economy.WarChestManager
                                    .deductFromWarChest(loserColony.getID(), credited);
                            WARSYSTEM_LOGGER.info("War vassalization war-chest grab ({}%): {} coins {} -> {}",
                                    treasuryPct, credited, loserColony.getName(), winnerColony.getName());
                        }
                    }
                }
            }

            // --- Losing player's wallet grab ---
            int walletPct = TaxConfig.getWarVassalizationPlayerBalanceGrabPercent();
            if (walletPct > 0 && net.machiavelli.minecolonytax.integration.SDMShopCompat.isAvailable()) {
                UUID loserOwner = loserColony.getPermissions().getOwner();
                ServerPlayer loserPlayer = loserOwner != null ? server.getPlayerList().getPlayer(loserOwner) : null;
                if (loserPlayer != null) {
                    long bal = net.machiavelli.minecolonytax.integration.SDMShopCompat.getMoney(loserPlayer);
                    if (bal > 0) {
                        long taken = (long) Math.floor(bal * (walletPct / 100.0));
                        if (taken > 0
                                && net.machiavelli.minecolonytax.integration.SDMShopCompat.removeMoney(loserPlayer, taken)) {
                            UUID winnerUUID = war.getAttacker();
                            ServerPlayer winnerPlayer = winnerUUID != null
                                    ? server.getPlayerList().getPlayer(winnerUUID) : null;
                            if (winnerPlayer != null) {
                                net.machiavelli.minecolonytax.integration.SDMShopCompat.addMoney(winnerPlayer, taken);
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
                attackerDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getAttackerTeamID(), stalematePenalty);
            } else {
                for (UUID uuid : war.getAttackerLives().keySet()) {
                    attackerDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }
            
            // Process defender side
            if (war.getDefenderTeamID() != null) {
                defenderDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getDefenderTeamID(), stalematePenalty);
            } else {
                for (UUID uuid : war.getDefenderLives().keySet()) {
                    defenderDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }
            
            war.setPenaltyReport("Stalemate penalties applied: " + (stalematePenalty * 100) + "% deducted from all participants (Attackers lost: " 
                + attackerDeducted + ", Defenders lost: " + defenderDeducted + ")");
            
            // Send message to all participants about the economic penalties
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component ecoMsg = Component.literal("War Stalemate: Both sides have been penalized economically!").withStyle(ChatFormatting.GOLD);
                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), ecoMsg);
            }
        }
        
        int freezeHours = TaxConfig.getWarTaxFreezeHours();
        if (freezeHours > 0) {
            TaxManager.freezeColonyTax(war.getColony().getID(), freezeHours);
            if (war.getAttackerColony() != null) {
                TaxManager.freezeColonyTax(war.getAttackerColony().getID(), freezeHours);
            }
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                String freezeMsg = "Tax generation frozen for " + freezeHours + " hours due to war stalemate!";
                Component notification = Component.literal(freezeMsg).withStyle(ChatFormatting.GOLD);
                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), notification);
            }
        }
    }

    /**
     * Handles economic transfers after a war is won or lost.
     * Transfers funds based on the configured percentages in TaxConfig.
     * 
     * @param war The war data containing information about the conflict
     * @param attackersWon True if attackers won, false if defenders won
     */
    private static void applyWarEconomyTransfers(WarData war, boolean attackersWon) {
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null) return;
        
        // Get the appropriate percentage based on who won
        double transferPercentage = attackersWon ? 
            TaxConfig.getWarVictoryPercentage() : TaxConfig.getWarDefeatPercentage();
        
        if (transferPercentage <= 0) {
            // No economic penalties configured
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
                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList().getPlayer(singleWinnerUUID);
                
                // Apply team economic penalties - transfer from ALL losers to SINGLE winner
                long totalCollected = 0;
                List<String> transactionDetails = new ArrayList<>();
                
                // Collect from all losing participants
                for (UUID loserUUID : losingParticipants.keySet()) {
                    ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                    if (loser != null) {
                        long loserBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getMoney(loser);
                        long transferAmount = Math.max(1, (long)(loserBalance * transferPercentage));
                        
                        if (transferAmount > 0 && loserBalance >= transferAmount) {
                            net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(loser, loserBalance - transferAmount);
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
                
                // Award all collected funds to single winner
                if (totalCollected > 0 && singleWinner != null) {
                    long currentBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getMoney(singleWinner);
                    net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(singleWinner, currentBalance + totalCollected);
                    
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
                        .append(Component.literal("\nTotal awarded to " + singleWinner.getName().getString() + ": $" + totalCollected)
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
                
                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList().getPlayer(singleWinnerUUID);
                ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                
                if (singleWinner != null && loser != null) {
                    totalTransferred = (long) WarEconomyHandler.transferBalanceToPlayer(loserUUID, singleWinnerUUID, transferPercentage);
                    
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
                reparationsAmount = (int)(loserColonyTax * transferPercentage);
                
                // If loser has no tax or not enough, calculate based on winner's expected revenue
                if (reparationsAmount <= 0) {
                    // We need to determine the expected tax revenue based on buildings
                    int expectedTaxRevenue = 0;
                    
                    // Calculate an expected tax based on the attacker's colony revenue potential
                    for (IBuilding building : ColonyBuildingUtil.getBuildings(winnerColony)) {
                        String buildingType = building.getBuildingDisplayName();
                        double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                        double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * building.getBuildingLevel();
                        expectedTaxRevenue += (int) (baseTax + upgradeTax);
                    }
                    
                    // Set reparations amount based on a percentage of expected tax revenue
                    reparationsAmount = (int)(expectedTaxRevenue * transferPercentage);
                    
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
                        .append(Component.literal("\n" + loserColony.getName() + " colony tax reduced by " + reparationsAmount)
                               .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n" + winnerColony.getName() + " colony tax increased by " + reparationsAmount)
                               .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\nLoser colony tax: " + TaxManager.getStoredTaxForColony(loserColony))
                               .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\nWinner colony tax: " + TaxManager.getStoredTaxForColony(winnerColony))
                               .withStyle(ChatFormatting.GRAY));
                    
                    sendMessageToWarParticipants(war, colonyTaxTransferMsg);
                }
            } else {
                // Backup to player inventory transfers if colonies are not available
                // Team-based transfers using inventory currency
                long amountTransferred = 0;
                List<UUID> losers = new ArrayList<>(attackersWon ? war.getDefenderLives().keySet() : war.getAttackerLives().keySet());
                
                // Select single winner using priority system (owner > officers > participants)
                Map<UUID, Integer> winningParticipants = attackersWon ? war.getAttackerLives() : war.getDefenderLives();
                IColony winningColony = attackersWon ? war.getAttackerColony() : war.getColony();
                UUID singleWinnerUUID = selectSingleWarWinner(winningColony, winningParticipants.keySet());
                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList().getPlayer(singleWinnerUUID);
                
                if (singleWinner != null) {
                    List<String> transactionDetails = new ArrayList<>();
                    
                    for (UUID loserUUID : losers) {
                        ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                        if (loser != null) {
                            long transferred = (long) WarEconomyHandler.transferBalanceToPlayer(loserUUID, singleWinner.getUUID(), transferPercentage);
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
                            .append(Component.literal("\nTotal awarded to " + singleWinner.getName().getString() + ": " + amountTransferred + " coins")
                                   .withStyle(ChatFormatting.GREEN));
                        
                        sendMessageToWarParticipants(war, inventoryTransferMsg);
                    }
                }
                
                totalTransferred = amountTransferred;
            }
        }
        
        // Log and announce the economic impact
        String winnerColonyName = attackersWon ? 
            (war.getAttackerColony() != null ? war.getAttackerColony().getName() : "attackers") : 
            war.getColony().getName();
        String loserColonyName = attackersWon ? 
            war.getColony().getName() : 
            (war.getAttackerColony() != null ? war.getAttackerColony().getName() : "attackers");
        
        war.setPenaltyReport("War reparations: " + totalTransferred + " transferred from " + loserColonyName + " to " + winnerColonyName);
        
        // Send economy summary to participants only (not global broadcast)
        if (totalTransferred > 0) {
            Component ecoMsg = Component.literal("🏆 WAR ECONOMIC RESULT 🏆").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
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
     * @param winningColony The winning colony
     * @param participants Set of winning participants
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
     * @param war The war data
     * @param message The message to send
     */
    private static void sendMessageToWarParticipants(WarData war, Component message) {
        Set<UUID> allParticipants = new HashSet<>();
        allParticipants.addAll(war.getAttackerLives().keySet());
        allParticipants.addAll(war.getDefenderLives().keySet());
        
        for (UUID participantUUID : allParticipants) {
            ServerPlayer participant = war.getColony().getWorld().getServer().getPlayerList().getPlayer(participantUUID);
            if (participant != null) {
                participant.sendSystemMessage(message);
            }
        }
    }

    /**
     * Moves a colony's deed to a new owner. This is the single chokepoint for permanent
     * ownership transfer — the primary-colony protection lives here, so a player's first
     * colony can never be seized in open war (only vassalized) unless
     * {@code EnablePrimaryColonyTransfer} is set.
     *
     * @return true if the deed actually moved; false if blocked (primary protection,
     *         missing world/owner, or MineColonies rejected the change). Callers should
     *         fall back to vassalization / occupation when this returns false.
     */
    public static boolean transferOwnership(IColony colony, UUID newOwnerUUID) {
        if (colony == null || colony.getWorld() == null || colony.getWorld().getServer() == null) return false;

        // Central primary-colony protection: every deed-moving path routes through here.
        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
            WARSYSTEM_LOGGER.info("Ownership transfer denied for colony {}: {}",
                    colony.getID(),
                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
            return false;
        }

        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null) return false;
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
        // Get war data before removing it from active wars
        WarData warData = ACTIVE_WARS.get(colony.getID());
        
        // Remove resistance effects from guards in both colonies
        if (warData != null) {
            GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getColony());
            if (warData.getAttackerColony() != null) {
                GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getAttackerColony());
            }
            
            // Clean up militia system for both colonies
            cleanupWarMilitiaSystem(warData);

            // Siege objectives + persistent war damage cleanup (experimental siege system)
            try {
                if (warData.getColony() != null && warData.getColony().getWorld() != null) {
                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
                            warData.getWarID(), warData.getColony().getWorld());
                }
            } catch (Throwable t) {
                WARSYSTEM_LOGGER.warn("WarBlockLedger restore on endWar failed: {}", t.toString());
            }
            net.machiavelli.minecolonytax.siege.PlantTheBannerObjective.onWarEnded(warData.getWarID());
            net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
        }
        
        // Disable war actions for both sides
        setWarInteractionPermissions(colony, false);
        
        // Also disable for attacker colony if it exists
        if (warData != null && warData.getAttackerColony() != null) {
            setWarInteractionPermissions(warData.getAttackerColony(), false);
        }
        
        // Now remove from active wars
        warData = ACTIVE_WARS.remove(colony.getID());

        // Drop the home-field defender flag for both sides. Without this the flag leaked and a
        // colony kept its drain reduction forever after its first war.
        net.machiavelli.minecolonytax.economy.WarChestManager.clearColonyRole(colony.getID());
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.economy.WarChestManager.clearColonyRole(warData.getAttackerColony().getID());
        }

        if (warData != null) {
            if (warData.warTimerTaskId != -1L) {
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(warData.warTimerTaskId);
                warData.warTimerTaskId = -1L;
            }
            if (warData.warChestDrainTaskId != -1L) {
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(warData.warChestDrainTaskId);
                warData.warChestDrainTaskId = -1L;
            }
            if (warData.bossEvent != null) {
                warData.bossEvent.removeAllPlayers();
                warData.bossEvent.setVisible(false);
            }
            // Also tear down the allies boss bar. During a normal war it is hidden at the JOINING→INWAR
            // transition, but a war that ends DURING the join phase (0-participant abort, /wnt stopwar,
            // or a restored JOINING war) never reaches that transition, so without this the yellow
            // "Joining War" bar stays stuck on clients until they relog.
            if (warData.alliesBossEvent != null) {
                warData.alliesBossEvent.removeAllPlayers();
                warData.alliesBossEvent.setVisible(false);
            }
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                colony.getPermissions().getPlayers().forEach((uuid, pdata) -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) p.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                });
                if (colony.getCitizenManager() != null) { // Null check for citizen manager
                    colony.getCitizenManager().getCitizens().forEach(citizen -> {
                        citizen.getEntity().ifPresent(entity -> entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING));
                    });
                }
                warData.getAttackerLives().keySet().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) resetWarGroup(p);
                });
                warData.getDefenderLives().keySet().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) resetWarGroup(p);
                });
                // Handle players in spectator mode (teleport to spawn, restore inventory, set to survival)
                Set<UUID> allParticipants = new HashSet<>();
                if (warData.getAttackerLives() != null) allParticipants.addAll(warData.getAttackerLives().keySet());
                if (warData.getDefenderLives() != null) allParticipants.addAll(warData.getDefenderLives().keySet());

                for (UUID participantUUID : allParticipants) {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(participantUUID);
                    if (p != null && p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        WarInventoryHandler.restoreInventory(p);

                        BlockPos respawnPos = p.getRespawnPosition();
                        float respawnAngle = p.getRespawnAngle();
                        net.minecraft.server.level.ServerLevel respawnLevel = p.server.getLevel(p.getRespawnDimension());

                        // Try to use personal respawn point first
                        boolean hasValidPersonalSpawn = respawnPos != null && respawnLevel != null && p.isRespawnForced();

                        if (hasValidPersonalSpawn) {
                            p.teleportTo(respawnLevel, respawnPos.getX() + 0.5, respawnPos.getY() + 0.1, respawnPos.getZ() + 0.5, respawnAngle, 0F);
                            WARSYSTEM_LOGGER.info("Player {} teleported to personal respawn point: {} in dimension {}", p.getName().getString(), respawnPos, respawnLevel.dimension().location());
                        } else {
                            // Fallback: Teleport to surface at current X/Z in their current dimension
                            BlockPos currentPos = p.blockPosition();
                            // Player's current level is already a ServerLevel in server-side code
                            net.minecraft.server.level.ServerLevel currentLevel = (net.minecraft.server.level.ServerLevel) p.level();
                            int topY = currentLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, currentPos.getX(), currentPos.getZ());
                            p.teleportTo(currentLevel, currentPos.getX() + 0.5, topY + 1.0, currentPos.getZ() + 0.5, p.getYRot(), p.getXRot());
                            WARSYSTEM_LOGGER.info("Player {} has no valid personal respawn, teleported to surface at current X/Z: {} in dimension {}", p.getName().getString(), new BlockPos(currentPos.getX(), topY, currentPos.getZ()), currentLevel.dimension().location());
                        }

                        p.setGameMode(GameType.SURVIVAL);
                        p.sendSystemMessage(
                                Component.translatable("war.end.inventory.restored")
                                        .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)));
                    }
                }
            }

            // Reconcile permission ranks NOW rather than waiting up to 60s for the periodic health
            // check. endWar only cleared the Hostile-rank action NODES above (setWarInteractionPermissions
            // false); it never demoted the participants OUT of the Hostile rank, so they'd otherwise
            // linger as "hostile" members of the opposing colony until a restart or manual /wnt permcheck.
            // PermissionsHealthCheck.run demotes stray hostiles while keeping anyone still legitimately
            // hostile via a concurrent war/raid/besiege (this war is already removed from ACTIVE_WARS above).
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                try {
                    net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.run(colony.getWorld().getServer());
                } catch (Throwable t) {
                    WARSYSTEM_LOGGER.warn("Post-endWar permission reconciliation failed: {}", t.toString());
                }
            }

            // Determine winner for history record (this part seems okay, might need adjustment based on actual war outcome logic)
            UUID winnerUuid = colony.getPermissions().getOwner(); // This might not always be the "winner"
            String winnerName = "Unknown";
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                winnerName = Optional.ofNullable(colony.getWorld().getServer().getPlayerList().getPlayer(winnerUuid))
                        .map(p -> p.getName().getString())
                        .orElse(winnerUuid.toString());
            }

            String outcome;
            long amountTransferred = 0L;

            if (warData.getPenaltyReport().isEmpty()) {
                outcome = "Stalemate";
                if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                    for (UUID uuid : warData.getAttackerLives().keySet()) {
                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (player != null) PlayerWarDataManager.incrementWarStalemates(player);
                    }
                    for (UUID uuid : warData.getDefenderLives().keySet()) {
                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (player != null) PlayerWarDataManager.incrementWarStalemates(player);
                    }
                }
            } else if (warData.getPenaltyReport().contains("TOTAL VICTORY")) {
                boolean isDefenderVictory = warData.getRemainingDefenderGuards() > 0;
                Map<UUID, Integer> winnerLivesMap = isDefenderVictory ? warData.getDefenderLives() : warData.getAttackerLives();
                if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                    for (UUID uuid : winnerLivesMap.keySet()) {
                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (player != null) PlayerWarDataManager.incrementWarsWon(player);
                    }
                }
                if (!TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                    IColony loserColonyActual = isDefenderVictory ? warData.getAttackerColony() : warData.getColony();
                    int colonyBalance = 0;
                    if(loserColonyActual != null) colonyBalance = TaxManager.getStoredTaxForColony(loserColonyActual);

                    long transferAmount = Math.max(1000, colonyBalance * 3 / 4);
                    if(loserColonyActual != null) TaxManager.deductColonyTax(loserColonyActual, TaxConfig.getWarDefeatPercentage());
                    amountTransferred = transferAmount;
                    outcome = "Victory! Colony funds transferred: " + transferAmount;
                    WARSYSTEM_LOGGER.info("[MineColonyTax] War victory funds transfer: {} from colony {}", transferAmount, loserColonyActual != null ? loserColonyActual.getName() : "Unknown");
                } else {
                    outcome = "Complete Victory! Colony ownership transferred.";
                    WARSYSTEM_LOGGER.info("[MineColonyTax] War victory colony transfer for colony {}", colony.getName());
                }
            } else {
                outcome = warData.getPenaltyReport();
            }

            String attackerName = warData.getAttackerColony() != null ? warData.getAttackerColony().getName() : "Unknown Attacker";
            String eventString = String.format("[WAR] Colony '%s' was attacked by '%s'. Outcome: %s. Amount Transferred: %d",
                colony.getName(),
                attackerName,
                outcome,
                amountTransferred);

            HistoryManager.getColonyHistory(colony.getID()).addEvent(eventString);
            HistoryManager.saveHistory();
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
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null) return;
        
        // Handle disconnected players - set their lives to zero
        Map<UUID, Integer> disconnectedPlayers = WarEventHandler.getDisconnectedWarParticipants();
        
        // Process disconnected attackers
        for (UUID uuid : new ArrayList<>(war.getAttackerLives().keySet())) {
            if (disconnectedPlayers.containsKey(uuid) && disconnectedPlayers.get(uuid) == 1) { // 1 = attacker
                // Player is disconnected and part of this war, set lives to zero
                war.getAttackerLives().put(uuid, 0);
                WARSYSTEM_LOGGER.info("[MineColonyTax] Setting disconnected attacker {} to 0 lives on war expiry", uuid);
            }
        }
        
        // Process disconnected defenders
        for (UUID uuid : new ArrayList<>(war.getDefenderLives().keySet())) {
            if (disconnectedPlayers.containsKey(uuid) && disconnectedPlayers.get(uuid) == 2) { // 2 = defender
                // Player is disconnected and part of this war, set lives to zero
                war.getDefenderLives().put(uuid, 0);
                WARSYSTEM_LOGGER.info("[MineColonyTax] Setting disconnected defender {} to 0 lives on war expiry", uuid);
            }
        }
        
        int attackerTotalLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
        int defenderTotalLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
        String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName() : "The Attackers";
        String defenderColonyName = war.getColony().getName();

        MutableComponent timeExpiredMsgBase = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));

        if (attackerTotalLives == 0 && war.getRemainingAttackerGuards() == 0) {
            MutableComponent defenderVictoryMsg = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
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
                if (p != null) PlayerWarDataManager.incrementWarsWon(p);
            }
            handleVictoryRewards(war, true); // true for defender victory
            endWar(war.getColony());
            return;
        }
        else if (defenderTotalLives == 0 && war.getRemainingDefenderGuards() == 0) {
            MutableComponent attackerVictoryMsg = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.attackers.part1").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.translatable("war.time.expired.attackers.part2").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, attackerVictoryMsg);
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null) PlayerWarDataManager.incrementWarsWon(p);
            }
            handleVictoryRewards(war, false); // false for attacker victory
            endWar(war.getColony());
            return;
        }

        // Check for stalemate due to no losses on either side by timeout
        if (attackerTotalLives == war.initialAttackerTotalLives && // No player lives lost by attackers
            defenderTotalLives == war.initialDefenderTotalLives && // No player lives lost by defenders
            war.getRemainingAttackerGuards() == war.initialAttackerGuards && // No attacker guards lost
            war.getRemainingDefenderGuards() == war.initialDefenderGuards) { // No defender guards lost
            
            MutableComponent stalemateNoLossesMsg = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.stalemate.timeout.part1").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.translatable("war.stalemate.timeout.part2").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                .append(Component.translatable("war.stalemate.timeout.part3").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.stalemate.timeout.penalties").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, stalemateNoLossesMsg);
            // Original penalty logic for stalemate:
            war.getAttackerLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
            war.getDefenderLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage()); // Defender colony
            if (war.getAttackerColony() != null) TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage()); // Attacker colony
            war.setPenaltyReport("Stalemate (Timeout - No Losses): Both sides lose " + (TaxConfig.getWarStalematePercentage() * 100) + "% of their balances and colony revenue is reduced by " + (TaxConfig.getWarStalematePercentage() * 100) + "%.");
            endWar(war.getColony());
            return;
        }

        // Strategic victory/loss based on proportional strength remaining
        double attackerNormalizedStrength = (double)(attackerTotalLives + war.getRemainingAttackerGuards()) / (war.initialAttackerTotalLives + war.initialAttackerGuards);
        double defenderNormalizedStrength = (double)(defenderTotalLives + war.getRemainingDefenderGuards()) / (war.initialDefenderTotalLives + war.initialDefenderGuards);
        double epsilon = 0.01; // To handle floating point comparisons
        String reportOutcome;
        MutableComponent strategicMsg; // Changed to MutableComponent

        if (attackerNormalizedStrength + epsilon < defenderNormalizedStrength) { // Attackers lost proportionally more
            reportOutcome = "Strategic Victory: Defenders win! Attackers lost proportionally more strength.";
            strategicMsg = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.strategic.defender.victory.part1").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                .append(Component.translatable("war.strategic.defender.victory.part2").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.strategic.defender.victory.part3").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.translatable("war.strategic.defender.victory.part4").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            WarEconomyHandler.transferTeamBalanceToSinglePlayer(war.getAttackerTeamID(), war.getDefender(), TaxConfig.getWarStalematePercentage());
            if (war.getAttackerColony() != null) TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage());
            broadcastComponent(war, strategicMsg);
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null) PlayerWarDataManager.incrementWarsWon(p);
            }
        }
        else if (defenderNormalizedStrength + epsilon < attackerNormalizedStrength) { // Defenders lost proportionally more
            reportOutcome = "Strategic Victory: Attackers win! Defenders lost proportionally more strength.";
            strategicMsg = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.strategic.attacker.victory.part1").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.translatable("war.strategic.attacker.victory.part2").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.strategic.attacker.victory.part3").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                .append(Component.translatable("war.strategic.attacker.victory.part4").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            WarEconomyHandler.transferTeamBalanceToSinglePlayer(war.getDefenderTeamID(), war.getAttacker(), TaxConfig.getWarStalematePercentage());
            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage());
            broadcastComponent(war, strategicMsg);
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null) PlayerWarDataManager.incrementWarsWon(p);
            }
        }
        else { // Proportional losses are too close - stalemate
            reportOutcome = "Stalemate (Timeout - Proportional Losses): Both sides fought hard but neither gained a clear advantage. Penalties apply.";
            strategicMsg = Component.translatable("war.time.expired.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.stalemate.proportional.part1").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.translatable("war.stalemate.proportional.part2").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                .append(Component.translatable("war.stalemate.proportional.part3").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.stalemate.proportional.penalties").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            war.getAttackerLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, TaxConfig.getWarStalematePercentage()));
            war.getDefenderLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, TaxConfig.getWarStalematePercentage()));
            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage());
            if (war.getAttackerColony() != null) TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage());
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
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null) return;
        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.sendSystemMessage(notification);
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player != null) player.sendSystemMessage(notification);
        });
    }
    
    // Keep the original method for backward compatibility
    private static void notifyWarParticipants(WarData war, String message, ChatFormatting color) {
        Component notification = Component.literal(message).withStyle(style -> style.withColor(color));
        notifyWarParticipants(war, notification);
    }

    private static void handleVictoryRewards(WarData war, boolean defendersWon) {
        Map<UUID, Integer> winnerLives = defendersWon ? war.getDefenderLives() : war.getAttackerLives();
        IColony loserColony = defendersWon ? war.getAttackerColony() : war.getColony();

        // Attackers win + transfer on: try to seize the deed. A protected primary makes
        // transferOwnership() return false, so we fall through to economic spoils instead
        // (the dedicated vassalize handling lives in checkForVictory).
        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon
                && transferOwnership(war.getColony(), war.getAttacker())) {
            war.setPenaltyReport("TOTAL VICTORY - Colony transferred to attackers!");
        } else {
            if (loserColony == null) {
                 war.setPenaltyReport("TOTAL VICTORY - Loser colony not found for economic penalties.");
                 return;
            }
            int colonyBalance = TaxManager.getStoredTaxForColony(loserColony);
            double victoryPercentage = TaxConfig.WAR_VICTORY_PERCENTAGE.get();
            double defeatPercentage = TaxConfig.WAR_DEFEAT_PERCENTAGE.get();
            long transferAmount = Math.max(100, (long)(colonyBalance * victoryPercentage));
            TaxManager.deductColonyTax(loserColony, defeatPercentage);

            if (!winnerLives.isEmpty() && war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                int sharePerPlayer = winnerLives.size() > 0 ? (int) (transferAmount / winnerLives.size()) : 0; // Avoid division by zero
                for (UUID uuid : winnerLives.keySet()) {
                    ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("You received " + sharePerPlayer + " as war spoils!")
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            war.setPenaltyReport("TOTAL VICTORY - " + transferAmount + " transferred from " + loserColony.getName() + "!");
        }
    }

    public static Map<UUID, Integer> getLivesForPlayer(WarData war, ServerPlayer player) {
        WARSYSTEM_LOGGER.debug("[DEBUG] getLivesForPlayer called for player " + player.getName().getString() + " (" + player.getUUID() + ")");
        WARSYSTEM_LOGGER.debug("[DEBUG] FTB_TEAMS_INSTALLED: " + FTB_TEAMS_INSTALLED);
        WARSYSTEM_LOGGER.debug("[DEBUG] Attacker lives: " + war.getAttackerLives());
        WARSYSTEM_LOGGER.debug("[DEBUG] Defender lives: " + war.getDefenderLives());
        
        // First check if player is directly in the lives maps
        UUID playerUUID = player.getUUID();
        WARSYSTEM_LOGGER.debug("[DEBUG] Checking if attacker lives contains player UUID: " + war.getAttackerLives().containsKey(playerUUID));
        WARSYSTEM_LOGGER.debug("[DEBUG] Checking if defender lives contains player UUID: " + war.getDefenderLives().containsKey(playerUUID));
        
        if (war.getAttackerLives().containsKey(playerUUID)) {
            WARSYSTEM_LOGGER.debug("[DEBUG] Player found in attacker lives, returning attacker lives");
            return war.getAttackerLives();
        } else if (war.getDefenderLives().containsKey(playerUUID)) {
            WARSYSTEM_LOGGER.debug("[DEBUG] Player found in defender lives, returning defender lives");
            return war.getDefenderLives();
        }
        
        // Check if player is in attacker or defender allies
        if (war.getAttackerAllies().contains(playerUUID)) {
            WARSYSTEM_LOGGER.debug("[DEBUG] Player found in attacker allies, returning attacker lives");
            return war.getAttackerLives();
        } else if (war.getDefenderAllies().contains(playerUUID)) {
            WARSYSTEM_LOGGER.debug("[DEBUG] Player found in defender allies, returning defender lives");
            return war.getDefenderLives();
        }
        
        if (FTB_TEAMS_INSTALLED) {
            Optional<FtbTeamsCompat.TeamHandle> teamOpt = FtbTeamsCompat.getTeamForPlayer(playerUUID);
            WARSYSTEM_LOGGER.debug("[DEBUG] Player team found: " + teamOpt.isPresent());
            if (teamOpt.isPresent()) {
                UUID teamId = FtbTeamsCompat.getTeamId(teamOpt.get());
                WARSYSTEM_LOGGER.debug("[DEBUG] Player team ID: " + teamId);
                WARSYSTEM_LOGGER.debug("[DEBUG] War attacker team ID: " + war.getAttackerTeamID());
                WARSYSTEM_LOGGER.debug("[DEBUG] War defender team ID: " + war.getDefenderTeamID());
                
                if (teamId != null && teamId.equals(war.getAttackerTeamID())) {
                    WARSYSTEM_LOGGER.debug("[DEBUG] Player is on attacker team, returning attacker lives");
                    return war.getAttackerLives();
                } else if (teamId != null && teamId.equals(war.getDefenderTeamID())) {
                    WARSYSTEM_LOGGER.debug("[DEBUG] Player is on defender team, returning defender lives");
                    return war.getDefenderLives();
                }
                
                // Check if player is allied to any participating team
                FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                        : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
                if (FtbTeamsCompat.partyTeamContains(atkTeam, playerUUID)) {
                    WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to attacker team, returning attacker lives");
                    return war.getAttackerLives();
                }
                
                FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                        : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
                if (FtbTeamsCompat.partyTeamContains(defTeam, playerUUID)) {
                    WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to defender team, returning defender lives");
                    return war.getDefenderLives();
                }
                
                WARSYSTEM_LOGGER.debug("[DEBUG] Player team not participating in war, checking Minecolonies membership");
            } else {
                WARSYSTEM_LOGGER.debug("[DEBUG] Player has no FTB team, checking Minecolonies membership");
            }
        }
        
        // Check Minecolonies colony membership and ranks
        IColony attackerColony = war.getAttackerColony();
        IColony defenderColony = war.getColony();
        
        WARSYSTEM_LOGGER.debug("[DEBUG] Checking Minecolonies membership - Attacker colony: " + (attackerColony != null ? attackerColony.getName() : "null"));
        WARSYSTEM_LOGGER.debug("[DEBUG] Checking Minecolonies membership - Defender colony: " + (defenderColony != null ? defenderColony.getName() : "null"));
        
        // Check if player is in attacker colony (owner, officer, or friend)
        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            WARSYSTEM_LOGGER.debug("[DEBUG] Player in attacker colony players list: " + attackerPerms.getPlayers().containsKey(playerUUID));
            if (attackerPerms.getPlayers().containsKey(playerUUID)) {
                Rank playerRank = attackerPerms.getRank(playerUUID);
                WARSYSTEM_LOGGER.debug("[DEBUG] Player rank in attacker colony: " + (playerRank != null ? playerRank.getName() : "null"));
                if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) || 
                                          playerRank.equals(attackerPerms.getRankOfficer()) ||
                                          playerRank.equals(attackerPerms.getRankFriend()))) {
                    WARSYSTEM_LOGGER.debug("[DEBUG] Player is in attacker colony with rank " + playerRank.getName() + ", returning attacker lives");
                    return war.getAttackerLives();
                }
            }
        }
        
        // Check if player is in defender colony (owner, officer, or friend)
        if (defenderColony != null) {
            IPermissions defenderPerms = defenderColony.getPermissions();
            WARSYSTEM_LOGGER.debug("[DEBUG] Player in defender colony players list: " + defenderPerms.getPlayers().containsKey(playerUUID));
            if (defenderPerms.getPlayers().containsKey(playerUUID)) {
                Rank playerRank = defenderPerms.getRank(playerUUID);
                WARSYSTEM_LOGGER.debug("[DEBUG] Player rank in defender colony: " + (playerRank != null ? playerRank.getName() : "null"));
                if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) || 
                                          playerRank.equals(defenderPerms.getRankOfficer()) ||
                                          playerRank.equals(defenderPerms.getRankFriend()))) {
                    WARSYSTEM_LOGGER.debug("[DEBUG] Player is in defender colony with rank " + playerRank.getName() + ", returning defender lives");
                    return war.getDefenderLives();
                }
            }
        }
        
        WARSYSTEM_LOGGER.debug("[DEBUG] Player not participating in war, returning empty map");
        return new HashMap<>(); // Return mutable map instead of Collections.emptyMap()
    }

    public static WarData getActiveWarForPlayer(ServerPlayer player) {
        for (WarData war : ACTIVE_WARS.values()) {
            // First check if player is directly in the lives maps
            if (war.getAttackerLives().containsKey(player.getUUID()) || war.getDefenderLives().containsKey(player.getUUID())) {
                return war;
            }
            
            // Check if player is in attacker or defender allies
            if (war.getAttackerAllies().contains(player.getUUID()) || war.getDefenderAllies().contains(player.getUUID())) {
                return war;
            }
            
            // Check FTB Teams
            if (FTB_TEAMS_INSTALLED) {
                Optional<FtbTeamsCompat.TeamHandle> teamOpt = FtbTeamsCompat.getTeamForPlayer(player.getUUID());
                if (teamOpt.isPresent()) {
                    UUID teamId = FtbTeamsCompat.getTeamId(teamOpt.get());
                    if (teamId != null && (teamId.equals(war.getAttackerTeamID())
                            || teamId.equals(war.getDefenderTeamID()))) {
                        return war;
                    }
                    
                    // Check if player is allied to any participating team
                    FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                            : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
                    if (FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())) {
                        return war;
                    }
                    
                    FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                            : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
                    if (FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
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
            if (delay <= 0) continue;
            // One-shot warning on the MAIN server thread (was java.util.Timer).
            net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
                    war.getColony().getWorld() == null ||
                    war.getColony().getWorld().getServer() == null ||
                    war.bossEvent == null) {
                    return;
                }
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
                war.bossEvent.setName(Component.literal(bossText));
                war.bossEvent.setProgress((float) remaining / warDurationSeconds);
                war.bossEvent.setVisible(true);
                if (remaining <= 0) {
                    handleTimeExpiry(war);
                }
            }, delay);
        }
    }

    private static void applyWarGlowToParticipants(WarData war) {
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null) return;
        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
        });
    }

    public static void applyGuardGlow(IColony colony) {
        if (colony.getCitizenManager() == null) return;
        colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .forEach(citizen -> citizen.getEntity().ifPresent(entity -> {
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
                }));
    }

    public static void onPlayerKilledInWar(ServerPlayer killer, ServerPlayer killed, WarData war) {
        if (killer != null && killed != null && war != null) {
            PlayerWarDataManager.incrementPlayersKilledInWar(killer);
        }
    }
    
    public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
        FtbTeamsCompat.TeamHandle attackerTeam = FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null);
        FtbTeamsCompat.TeamHandle defenderTeam = FtbTeamsCompat.getTeamForPlayer(owner.getUUID()).orElse(null);

        // Colonies the player owns rank ahead of colonies they merely officer. An officer may
        // wage war on the colony's behalf when the owner granted DECLARE_WAR in the Officers
        // tab; that permission is enforced here rather than only hidden in the interface.
        final java.util.UUID attackerId = attacker.getUUID();
        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                .filter(c -> {
                    com.minecolonies.api.colony.permissions.IPermissions perms = c.getPermissions();
                    if (attackerId.equals(perms.getOwner())) return true;
                    com.minecolonies.api.colony.permissions.Rank rank = perms.getRank(attackerId);
                    if (rank == null || !rank.isColonyManager()) return false;
                    boolean isOfficer = rank.equals(perms.getRankOfficer());
                    return net.machiavelli.minecolonytax.permissions.TaxPermissionManager.can(
                            c.getID(), attackerId,
                            net.machiavelli.minecolonytax.permissions.ColonyPermission.DECLARE_WAR,
                            false, isOfficer);
                })
                .sorted((a, b) -> {
                    boolean aOwned = attackerId.equals(a.getPermissions().getOwner());
                    boolean bOwned = attackerId.equals(b.getPermissions().getOwner());
                    if (aOwned != bOwned) return aOwned ? -1 : 1;
                    return 0;
                })
                .findFirst().orElse(null);
        if (attackerColony == null) {
            attacker.sendSystemMessage(Component.literal(
                    "You must own a colony, or hold war rights in one, to declare war.")
                    .withStyle(style -> style.withColor(ChatFormatting.RED)));
            return;
        }

        initiateWar(attacker, owner.getUUID(), attackerTeam, defenderTeam, colony, attackerColony);
        WarData war = getActiveWarForPlayer(owner);

        int configuredMinutes = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get();
        WARSYSTEM_LOGGER.info("[DEBUG] JOIN_PHASE_DURATION_MINUTES config value: {} minutes", configuredMinutes);
        WARSYSTEM_LOGGER.info("[DEBUG] Config spec: {}", TaxConfig.CONFIG.getClass().getName());
        WARSYSTEM_LOGGER.info("[DEBUG] Config default value: {}", TaxConfig.JOIN_PHASE_DURATION_MINUTES.getDefault());
        WARSYSTEM_LOGGER.info("[DEBUG] Config is loaded: {}", TaxConfig.CONFIG.isLoaded());
        
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            // Get the time remaining in a readable format
            String timeRemaining = configuredMinutes + " minutes";
            
            // Send join phase announcement only to war participants
            Component joinPhaseMsg = Component.translatable("war.join.phase.declared", colony.getName(), timeRemaining);
            sendNotificationToWarParticipants(colony, attackerColony, joinPhaseMsg);
        }
        WARSYSTEM_LOGGER.info("Join phase started for colony {}. Waiting for participants for {} seconds.", colony.getName(), configuredMinutes * 60);

        if (war == null) return;

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
                if (p != null) war.bossEvent.addPlayer(p);
            }
            for (UUID uuid : war.getDefenderLives().keySet()) {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) war.bossEvent.addPlayer(p);
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
            if (attackerTeam != null) sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
            if (defenderTeam != null) sendNotificationToColonyParticipants(colony, joinPhaseInfo);
        } else {
            sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
            sendNotificationToColonyParticipants(colony, joinPhaseInfo);
        }

        // Add countdown sound timer for the last 6 seconds of join phase, but only if join phase is at least 6 seconds long
        if (joinDurationMillis >= 6000) {
            final int[] secondsLeft = {6};
            final long[] soundTaskId = {-1L};
            soundTaskId[0] = net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
                try {
                    if (war == null || war.getColony() == null || !war.isJoinPhaseActive()) {
                        net.machiavelli.minecolonytax.util.TickScheduler.cancel(soundTaskId[0]);
                        return;
                    }
                    // Play countdown sound to all war participants
                    Set<UUID> allParticipants = new HashSet<>();
                    allParticipants.addAll(war.getAttackerLives().keySet());
                    allParticipants.addAll(war.getDefenderLives().keySet());
                    if (!allParticipants.isEmpty()) {
                        for (UUID uuid : allParticipants) {
                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
                            if (player != null) {
                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
                            }
                        }
                    }
                    notifyWarParticipants(war,
                        Component.literal("⏱ " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds") + " until war starts!")
                                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
                    secondsLeft[0]--;
                    if (secondsLeft[0] < 0) {
                        net.machiavelli.minecolonytax.util.TickScheduler.cancel(soundTaskId[0]);
                    }
                } catch (Exception ex) {
                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
                    net.machiavelli.minecolonytax.util.TickScheduler.cancel(soundTaskId[0]);
                }
            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6s before join ends, repeat every second
        }
        
        // Main task to start the war when join phase ends (MAIN server thread)
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
            if (war == null || war.getColony() == null) return;
            war.setStatus(WarData.WarStatus.INWAR);
            war.warStartTime = System.currentTimeMillis();
            // If the start checks fail, finalizeWarStart already ended the war — do NOT enable
            // permissions or start the countdown, which would resurrect it as a half-init INWAR.
            if (!finalizeWarStart(war)) return;
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
     * Sends a notification to all war participants from both colonies (attacker and defender),
     * including officers, friends, and FTB team members if applicable.
     */
    // Helper to broadcast to entire server
    private static void broadcastToServer(Component message) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);
    }
    
    private static void sendNotificationToWarParticipants(IColony defenderColony, IColony attackerColony, Component message) {
        if (defenderColony == null || defenderColony.getWorld() == null || defenderColony.getWorld().getServer() == null) {
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
                    if (FtbTeamsCompat.isPartyTeam(attackerTeam)) {
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
                    if (FtbTeamsCompat.isPartyTeam(defenderTeam)) {
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

    /**
     * Conquest-war retreat check, run once a second from the war countdown. Mirrors the besiege
     * retreat rule so the same "you must show up and fight" pressure applies to wars that claim an
     * Nth colony. The attacking side (primary attacker + allies) must keep at least one player within
     * {@code BesiegePlayerStayRadius} of the target colony's nearest claimed border. If NO attacker is inside the
     * boundary, a grace countdown ({@code BesiegeRetreatGraceSeconds}) starts with an on-screen
     * warning; returning inside cancels it. If the grace elapses, the war is forfeited to the
     * defenders. Offline-only attacking sides do not trip this (that is handled separately on expiry).
     *
     * @return true if the war was ended by this retreat (caller must stop ticking it).
     */
    /**
     * Suspend the retreat countdown of every war this player attacks in, because they just died and
     * have to walk back from their respawn point.
     *
     * <p>Called from the death handler. Without it the retreat rule and the multi-life rule
     * contradict each other: dying teleports the attacker to their bed or world spawn — normally
     * far beyond {@code BesiegePlayerStayRadius} — so the ordinary
     * {@code BesiegeRetreatGraceSeconds} window (30s by default, nowhere near enough to cross a
     * thousand blocks) would forfeit the war on the first death, and lives 2..N could never be
     * spent. Length of the exemption is {@code BesiegeRespawnReturnSeconds}; 0 disables it.</p>
     */
    public static void notifyCombatantDeath(UUID playerUUID) {
        if (playerUUID == null) return;
        int windowSec = TaxConfig.getBesiegeRespawnReturnSeconds();
        if (windowSec <= 0) return;
        long until = System.currentTimeMillis() + windowSec * 1000L;

        for (WarData war : ACTIVE_WARS.values()) {
            if (war == null) continue;
            boolean onAttackingSide = playerUUID.equals(war.getAttacker())
                    || war.getAttackerAllies().contains(playerUUID)
                    || war.getAttackerLives().containsKey(playerUUID);
            if (onAttackingSide) {
                war.respawnGraceUntilMs = until;
                war.retreatingSinceMs = 0L;
            }
        }
    }

    private static boolean checkAttackerRetreat(WarData war) {
        IColony target = war.getColony();
        if (target == null || target.getWorld() == null) return false;
        MinecraftServer server = target.getWorld().getServer();
        if (server == null) return false;

        // Distance is measured from the target colony's nearest claimed BORDER (claims are irregular
        // polygons, not rectangles), not from the center — see ColonyGeometry.
        int maxRadius = TaxConfig.getBesiegePlayerStayRadius();

        // The attacking side: primary attacker + declared allies + anyone still holding attacker lives.
        Set<UUID> attackers = new HashSet<>();
        if (war.getAttacker() != null) attackers.add(war.getAttacker());
        attackers.addAll(war.getAttackerAllies());
        attackers.addAll(war.getAttackerLives().keySet());

        boolean anyPresent = false;
        boolean anyOnline = false;
        for (UUID uuid : attackers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null || p.level() != target.getWorld()) continue;
            anyOnline = true;
            if (net.machiavelli.minecolonytax.util.ColonyGeometry.isWithinBattleRange(target, p, maxRadius)) {
                anyPresent = true;
                break;
            }
        }

        // Someone is holding the line — mark engaged and clear any retreat in progress.
        if (anyPresent) {
            war.hasEngagedTarget = true;
            war.retreatingSinceMs = 0L;
            return false;
        }
        // Nobody online on the attacking side: don't run the retreat timer (offline is resolved on
        // expiry via disconnected-lives handling), just clear any partial countdown.
        if (!anyOnline) {
            war.retreatingSinceMs = 0L;
            return false;
        }
        // Attackers are online but away, and have not yet reached the target: no retreat until they
        // have engaged at least once (a no-show is resolved by the war timer, not a forfeit).
        if (!war.hasEngagedTarget) {
            war.retreatingSinceMs = 0L;
            return false;
        }
        // An attacker died recently and is walking back from their respawn point. Respawning puts a
        // player at their bed or world spawn — practically always outside the boundary — so counting
        // that as a retreat would forfeit the war on the FIRST death and make the remaining attacker
        // lives unreachable. Hold the countdown until the return window expires.
        if (System.currentTimeMillis() < war.respawnGraceUntilMs) {
            war.retreatingSinceMs = 0L;
            return false;
        }

        if (war.retreatingSinceMs == 0L) war.retreatingSinceMs = System.currentTimeMillis();
        long graceMs = TaxConfig.getBesiegeRetreatGraceSeconds() * 1000L;
        long elapsed = System.currentTimeMillis() - war.retreatingSinceMs;

        if (elapsed >= graceMs) {
            handleAttackerRetreat(war, attackers, server);
            return true;
        }

        // Live countdown on the action bar (bottom of screen) for every online attacker.
        long remainingSec = Math.max(1, (graceMs - elapsed + 999) / 1000);
        Component warn = Component.literal("⚠ Your assault on " + target.getName()
                + " is faltering! Return to the battle in " + remainingSec + "s or forfeit the war.")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (UUID uuid : attackers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.displayClientMessage(warn, true);
        }
        return false;
    }

    /** Forfeit a war because the attacking side retreated: tell both sides ("enemy retreated"), award
     *  the defenders the victory, and end the war. */
    private static void handleAttackerRetreat(WarData war, Set<UUID> attackers, MinecraftServer server) {
        String defenderColonyName = war.getColony().getName();
        Component attackerMsg = Component.literal("You retreated from " + defenderColonyName
                + " — your war is abandoned and the defenders hold the field.")
                .withStyle(ChatFormatting.RED);
        Component defenderMsg = Component.literal("The enemy retreated — " + defenderColonyName
                + " has repelled the assault!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);

        for (UUID uuid : attackers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(attackerMsg);
        }
        sendColonyMessage(war.getColony(), defenderMsg);
        for (UUID defUUID : war.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(defUUID);
            if (p != null) PlayerWarDataManager.incrementWarsWon(p);
        }
        if (TaxConfig.isNormalLogging())
            WARSYSTEM_LOGGER.info("Attacker side retreated from colony {} — war forfeited, defenders win",
                    war.getColony().getName());
        handleVictoryRewards(war, true); // true = defender victory
        endWar(war.getColony());
    }

    private static void startWarCountdown(WarData warData) {
        if (warData.getColony().getWorld() == null) {
            WARSYSTEM_LOGGER.error("Cannot start war countdown, world is null for colony {}", warData.getColony().getID());
            return;
        }
        final long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
        // Runs every second on the MAIN server thread via TickScheduler (was java.util.Timer,
        // which mutated war/boss state off-thread). Timing is wall-clock based so behaviour is unchanged.
        warData.warTimerTaskId = net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
            // Stop if war no longer active or colony/world/server/boss bar is gone
            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
                warData.getColony().getWorld() == null ||
                warData.getColony().getWorld().getServer() == null ||
                warData.bossEvent == null) {
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(warData.warTimerTaskId);
                return;
            }
            // Don't process for ended wars
            if (warData.getStatus() != WarData.WarStatus.INWAR) {
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(warData.warTimerTaskId);
                return;
            }
            long elapsedSeconds = (System.currentTimeMillis() - warData.warStartTime) / 1000;
            long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
            String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
                    warData.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
                    warData.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
                    remaining / 60, remaining % 60);
            warData.bossEvent.setName(Component.literal(bossText));
            warData.bossEvent.setProgress((float) remaining / warDurationSeconds);
            warData.bossEvent.setVisible(true);
            // Conquest-war retreat: the aggressor must press the assault at the target colony, just
            // like a besiege. If the whole attacking side strays past the retreat boundary for the
            // grace period, the war is forfeited to the defenders ("enemy retreated").
            if (checkAttackerRetreat(warData)) {
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(warData.warTimerTaskId);
                return;
            }
            if (remaining <= 0) {
                handleTimeExpiry(warData);
                net.machiavelli.minecolonytax.util.TickScheduler.cancel(warData.warTimerTaskId);
            }
        }, 1000, 1000);
    }

    public static void sendColonyMessage(IColony colony, Component message) {
        if (colony == null || colony.getWorld() == null) return;
        IPermissions perms = colony.getPermissions();
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            // Only send to colony allies: Owner, Officers, and Friends
            // Excludes: Hostile and Neutral players
            Rank rank = perms.getRank(uuid);
            if (rank != null && (rank.equals(perms.getRankOwner()) || 
                                rank.equals(perms.getRankOfficer()) || 
                                rank.equals(perms.getRankFriend()))) {
                ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                if (p != null) p.sendSystemMessage(message);
            }
        });
    }

    public static void sendMessageToTeam(FtbTeamsCompat.TeamHandle team, Component msg) {
        if (team == null || ServerLifecycleHooks.getCurrentServer() == null) return;
        for (UUID member : FtbTeamsCompat.getTeamMembers(team)) {
            ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
            if (sp != null) sp.sendSystemMessage(msg);
        }
    }
    
    /**
     * War rank gate: a colony may not declare WAR on one that OUTRANKS it militarily.
     * Rank = guard count (the same strength yardstick used for war eligibility). This keeps war a
     * peer-vs-peer contest; challenging a stronger power is what Besiege is for. Gated by config
     * {@code EnableWarRankRestriction} (default true).
     *
     * @return true if the war may proceed; false if blocked (a failure message is sent to source).
     */
    private static boolean passesWarRankGate(IColony attackerColony, IColony targetColony, CommandSourceStack source) {
        if (!TaxConfig.isWarRankRestrictionEnabled()) {
            return true;
        }
        int attackerRank = countGuards(attackerColony);
        int targetRank = countGuards(targetColony);
        if (targetRank > attackerRank) {
            source.sendFailure(Component.literal(
                    "You cannot declare war on a colony that outranks you. " + targetColony.getName()
                            + " fields " + targetRank + " guards to your " + attackerRank
                            + ". Besiege them instead to challenge a stronger power.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    public static int processWageWarRequest(ServerPlayer attacker, IColony targetColony, CommandSourceStack source) {
        Level level = source.getLevel();

        int targetGuards = countGuards(targetColony); 
        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) { 
            source.sendFailure(Component.literal("Target colony must have at least " + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
            return 0;
        }

        IColony attackerColony = IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                .findFirst().orElse(null);
        if (attackerColony == null) {
            source.sendFailure(Component.literal("You must own a colony to declare war."));
            return 0;
        }
        // Check requirements: Building requirements take priority over simple guard count
        if (TaxConfig.isWarBuildingRequirementsEnabled()) {
            // Use new building requirements system (includes guard towers and other buildings)
            net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult warRequirements = 
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.checkWarRequirements(attackerColony);
            
            if (!warRequirements.meetsRequirements) {
                source.sendFailure(Component.literal("Cannot declare war: " + warRequirements.message)
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        } else {
            // Fall back to legacy guard count system
        int attackerGuards = countGuards(attackerColony);
        if (attackerGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) { 
            source.sendFailure(Component.literal("Your colony must have at least " + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + attackerGuards + ")"));
            return 0;
            }
        }
        if (targetColony.getID() == attackerColony.getID()) {
            source.sendFailure(Component.literal("Cannot declare war on your own colony!"));
            return 0;
        }
        if (!passesWarRankGate(attackerColony, targetColony, source)) {
            return 0;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
        if (owner == null) {
            source.sendFailure(Component.literal("Target colony owner is offline!"));
            return 0;
        }

        if (!TaxConfig.WAR_ACCEPTANCE_REQUIRED.get()) {
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                Component autoAcceptMsg = Component.empty()
                    .append(Component.literal("⚔️ WAR INITIATED ⚔️").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\nColony ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.literal(" is now at WAR with ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColony.getName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                    .append(Component.literal("! (Auto-Accepted)").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\nThe drums of war sound! Join phase starting immediately!").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(autoAcceptMsg);
            }
            startJoinPhase(targetColony, attacker, owner);
            return 1;
        }

        WARSYSTEM_LOGGER.info("Adding pending war request for colony {} from attacker {}", targetColony.getID(), attacker.getUUID());
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            String attackerColonyName = attackerColony != null ? attackerColony.getName() : attacker.getName().getString() + "'s forces";
            Component warDeclarationMsg = Component.empty()
                .append(Component.translatable("war.declare.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.forces.valiant").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.translatable("war.declare.body", "", targetColony.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastToServer(warDeclarationMsg);
        }
        pendingWarRequests.put(targetColony.getID(), new WarRequest(attacker.getUUID(), targetColony.getID()));
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
            Object removedRequest = pendingWarRequests.remove(targetColony.getID());
            if (removedRequest != null) {
                if (targetColony.getWorld() != null && targetColony.getWorld().getServer() != null) {
                    ServerPlayer targetOwner = targetColony.getWorld().getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
                    if (targetOwner != null) {
                        targetOwner.sendSystemMessage(
                                Component.translatable("war.request.expired.defender")
                                        .withStyle(style -> style.withColor(ChatFormatting.RED))
                        );
                    }
                    ServerPlayer attackerPlayer = targetColony.getWorld().getServer().getPlayerList().getPlayer(attacker.getUUID());
                    if (attackerPlayer != null) {
                        attackerPlayer.sendSystemMessage(
                                Component.translatable("war.request.expired.attacker", targetColony.getName())
                                        .withStyle(style -> style.withColor(ChatFormatting.RED))
                        );
                    }
                }
            }
        }, 30000);

        Rank playerRank = targetColony.getPermissions().getRank(attacker.getUUID());
        if (playerRank == null) {
            Rank hostileRank = targetColony.getPermissions().getRankHostile();
            targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
        } else {
            targetColony.getPermissions().setPlayerRank(attacker.getUUID(), targetColony.getPermissions().getRankHostile(), level);
        }
        Rank currentRank = targetColony.getPermissions().getRank(attacker.getUUID());
        if (currentRank != null) currentRank.setHostile(true);

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
        attacker.sendSystemMessage(Component.translatable("war.request.sent", targetColony.getName()).withStyle(ChatFormatting.YELLOW));
        WARSYSTEM_LOGGER.info("[War] Attacker UUID: {}", attacker.getUUID());
        WARSYSTEM_LOGGER.info("[War] Target Colony Owner: {}", targetColony.getPermissions().getOwner());
        return 1;
    }
    
    public static int processWageWarRequestWithExtortion(ServerPlayer attacker, IColony targetColony, CommandSourceStack source, int extortionPercent) {
        Level level = source.getLevel(); 

        int targetGuards = countGuards(targetColony); 
        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) { 
            source.sendFailure(Component.literal("Target colony must have at least " + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
            return 0;
        }

        IColony attackerColony = IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                .findFirst().orElse(null);
        if (attackerColony == null) {
            source.sendFailure(Component.literal("You must own a colony to declare war."));
            return 0;
        }
        // Check requirements: Building requirements take priority over simple guard count
        if (TaxConfig.isWarBuildingRequirementsEnabled()) {
            // Use new building requirements system (includes guard towers and other buildings)
            net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult warRequirements = 
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.checkWarRequirements(attackerColony);
            
            if (!warRequirements.meetsRequirements) {
                source.sendFailure(Component.literal("Cannot declare war: " + warRequirements.message)
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        } else {
            // Fall back to legacy guard count system
        int attackerGuards = countGuards(attackerColony);
        if (attackerGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) { 
            source.sendFailure(Component.literal("Your colony must have at least " + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + attackerGuards + ")"));
            return 0;
            }
        }
        if (targetColony.getID() == attackerColony.getID()) {
            source.sendFailure(Component.literal("Cannot declare war on your own colony!"));
            return 0;
        }
        if (!passesWarRankGate(attackerColony, targetColony, source)) {
            return 0;
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
            source.sendFailure(Component.literal("Colony " + targetColony.getName() + " has extortion immunity for " + hoursRemaining + " more hours. Use regular war declaration."));
            return 0;
        }

        if (!TaxConfig.WAR_ACCEPTANCE_REQUIRED.get()) {
            // Auto-accept is enabled, show extortion choice to defender with timer
            showExtortionChoiceWithTimer(attacker, targetColony, owner, extortionPercent);
            return 1;
        } else {
            // Manual acceptance is required, add extortion to pending request
            WARSYSTEM_LOGGER.info("Adding pending war request with extortion for colony {} from attacker {}", targetColony.getID(), attacker.getUUID());
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                String attackerColonyName = attackerColony != null ? attackerColony.getName() : attacker.getName().getString() + "'s forces";
                Component warDeclarationMsg = Component.empty()
                    .append(Component.translatable("war.declare.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.forces.valiant").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                    .append(Component.translatable("war.declare.body", "", targetColony.getName()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n💰 Extortion Demand: " + extortionPercent + "% of your balance").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(warDeclarationMsg);
            }
            pendingWarRequests.put(targetColony.getID(), new WarRequestWithExtortion(attacker.getUUID(), targetColony.getID(), extortionPercent));
            
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
            attacker.sendSystemMessage(Component.literal("War declaration with " + extortionPercent + "% extortion demand sent to " + targetColony.getName()).withStyle(ChatFormatting.YELLOW));
            return 1;
        }
    }

    public static int processWarResponse(ServerPlayer executor, int colonyId, boolean accepted, CommandSourceStack source) {
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
            WARSYSTEM_LOGGER.warn("No pending war or extortion war request found for colony ID {} when {} attempted to respond.", colonyId, executor.getName().getString());
            return 0;
        }

        IColony targetColony = IColonyManager.getInstance().getColonyByDimension(colonyId, source.getLevel().dimension());
        if (targetColony == null) {
            source.sendFailure(Component.literal("Target colony not found.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.error("Target colony (ID {}) not found during war response by {}.", colonyId, executor.getName().getString());
            return 0;
        }

        Rank executorRank = targetColony.getPermissions().getRank(executor.getUUID());
        boolean isAuthorized = executor.getUUID().equals(targetColony.getPermissions().getOwner()) ||
                (executorRank != null && executorRank.isColonyManager());
        if (!isAuthorized) {
            source.sendFailure(Component.literal("You are not authorized to accept/decline this war request.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn("{} is not authorized to respond to war request for colony {}.", executor.getName().getString(), targetColony.getName());
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
            WARSYSTEM_LOGGER.warn("Attacker {} is offline when {} tried to respond to war request for colony {}.", attackerUUID, executor.getName().getString(), targetColony.getName());
            return 0;
        }
        pendingWarRequests.remove(colonyId); 

        if (accepted) {
            WARSYSTEM_LOGGER.info("War request for colony {} accepted by {}.", targetColony.getID(), executor.getName().getString());
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                    .filter(c -> attacker.getUUID().equals(c.getPermissions().getOwner()))
                    .findFirst().orElse(null);
                String attackerColonyName = attackerColony != null ? attackerColony.getName() : attacker.getName().getString() + "'s forces";

                MutableComponent warAcceptedMsg = Component.literal("✅ WAR ACCEPTED! ✅")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\nThe colony of ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.literal(" (led by ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(executor.getName().getString()).withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(") has accepted the challenge! War against ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                    .append(Component.literal(" will now proceed to the join phase!").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY));
                broadcastToServer(warAcceptedMsg);
            }
            startJoinPhase(targetColony, attacker, executor);
        } else {
            WARSYSTEM_LOGGER.info("War request for colony {} declined by {}.", targetColony.getID(), executor.getName().getString());
            executor.sendSystemMessage(Component.literal("❌ War declaration declined!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            attacker.sendSystemMessage(Component.literal("❌ " + targetColony.getName() + " declined your war request!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                 Component warDeclinedMsg = Component.empty()
                    .append(Component.literal("❌ WAR DECLINED ❌").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\nThe colony of ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.literal(" (led by ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(executor.getName().getString()).withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(") has declined the war declaration.").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY));
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
     * Checks if a player can join a specific war based on their team membership, colony membership, and ranks.
     */
    private static boolean canPlayerJoinWar(ServerPlayer player, WarData war) {
        // Don't allow primary participants to join via this method
        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) || 
            player.getUUID().equals(war.getAttacker()) ||
            (war.getAttackerColony() != null && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
            return false;
        }
        
        // Check if war is in join phase
        if (!war.isJoinPhaseActive() || System.currentTimeMillis() >= war.getJoinPhaseEndTime()) {
            return false;
        }
        
        // Check FTB Teams
        if (FTB_TEAMS_INSTALLED) {
            UUID playerTeamId = FtbTeamsCompat.getTeamForPlayer(player.getUUID())
                    .map(FtbTeamsCompat::getTeamId).orElse(null);
            FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
            FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);

            // Direct team membership
            if (playerTeamId != null && (playerTeamId.equals(war.getAttackerTeamID())
                    || playerTeamId.equals(war.getDefenderTeamID()))) {
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
            (war.getAttackerColony() != null && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner())) ) {
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
        if (war.getAttackerLives().containsKey(player.getUUID()) || war.getDefenderLives().containsKey(player.getUUID())) {
            source.sendFailure(Component.literal("You are already registered in this war."));
            return 0;
        }

        // Determine which side the player should join based on various criteria
        boolean canJoinAttackers = false;
        boolean canJoinDefenders = false;
        
        // Check FTB Teams first
        if (FTB_TEAMS_INSTALLED) {
            UUID playerTeamId = FtbTeamsCompat.getTeamForPlayer(player.getUUID())
                    .map(FtbTeamsCompat::getTeamId).orElse(null);
            FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
            FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
                    : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);

            // Direct team membership
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
            MutableComponent message = Component.literal("You are eligible to join both sides. Please choose which side to join:\n")
                    .withStyle(ChatFormatting.GOLD);
            
            Component joinAttackers = Component.literal("[Join Attackers]")
                    .withStyle(style -> style.withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt choosewarside attacker"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                    Component.literal("Click to join the attacking side").withStyle(ChatFormatting.GOLD))));
            
            Component joinDefenders = Component.literal("[Join Defenders]")
                    .withStyle(style -> style.withColor(ChatFormatting.BLUE)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt choosewarside defender"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                    Component.literal("Click to join the defending side").withStyle(ChatFormatting.GOLD))));
            
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
            
            player.sendSystemMessage(Component.literal("You have joined the attacking side!").withStyle(ChatFormatting.GREEN));
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
            
            player.sendSystemMessage(Component.literal("You have joined the defending side!").withStyle(ChatFormatting.GREEN));
            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                war.alliesBossEvent.addPlayer(player);
            } else {
                war.bossEvent.addPlayer(player);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("You are not eligible to join this war. Only colony owners, officers, and friends can participate."));
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
            (war.getAttackerColony() != null && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner())) ) {
            source.sendFailure(Component.literal("Primary war participants cannot leave the war."));
            return 0;
        }

        if (System.currentTimeMillis() >= war.getJoinPhaseEndTime()) {
            source.sendFailure(Component.literal("Join phase is over; you cannot leave now."));
            return 0;
        }
        
        boolean removedFromAttackers = war.getAttackerLives().remove(player.getUUID()) != null;
        if (removedFromAttackers) war.getAttackerAllies().remove(player.getUUID());

        boolean removedFromDefenders = war.getDefenderLives().remove(player.getUUID()) != null;
        if (removedFromDefenders) war.getDefenderAllies().remove(player.getUUID());

        if (removedFromAttackers || removedFromDefenders) {
            source.sendSuccess(() -> Component.literal("You have left the war."), false);
            if (war.alliesBossEvent != null) war.alliesBossEvent.removePlayer(player);
            if (war.bossEvent != null) war.bossEvent.removePlayer(player); 
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
     * @param colony The colony to count guards for
     * @return The number of guards in the colony, or 0 if the colony is invalid
     */
    public static int countGuards(IColony colony) {
        if (colony == null || colony.getCitizenManager() == null) return 0;
        return (int) colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getJob() != null && c.getJob().isGuard())
                .count();
    }
    
    /**
     * Initialize the militia system for both colonies in a war for proper guard/militia tracking.
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
        
        WARSYSTEM_LOGGER.info("Initialized militia tracking system for war between {} and {}", 
            war.getAttackerColony() != null ? war.getAttackerColony().getName() : "Unknown",
            war.getColony().getName());
    }
    
    /**
     * Activate militia for both colonies in a war if militia system is enabled.
     * @param war The war data containing both colonies
     */
    private static void activateWarMilitia(WarData war) {
        if (!TaxConfig.ENABLE_CITIZEN_MILITIA.get()) {
            // Even if militia is disabled, we need to set the total defender count for kill tracking
            setWarDefenderCounts(war);
            WARSYSTEM_LOGGER.info("Militia disabled - Set defender counts for war without militia activation");
            return;
        }
        
        // Activate militia for defending colony
        int defenderMilitia = net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
            .activateMilitia(war.getColony());
        
        if (defenderMilitia > 0) {
            sendColonyMessage(war.getColony(), Component.literal("⚔ WAR MILITIA ACTIVATED ⚔")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("\n" + defenderMilitia + " citizens have joined the militia to defend against the war!")
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
                    .append(Component.literal("\n" + attackerMilitia + " citizens have joined the militia for the war effort!")
                           .withStyle(ChatFormatting.YELLOW)));
            }
        }
        
        WARSYSTEM_LOGGER.info("Activated war militia - Defenders: {} militia, Attackers: {} militia", 
            defenderMilitia, attackerMilitia);
    }
    
    /**
     * Set defender counts for war when militia is disabled but tracking is still needed.
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
     * @param colony The colony to count guard towers for
     * @return The number of guard towers in the colony, or 0 if the colony is invalid
     */
    public static int countGuardTowers(IColony colony) {
        if (colony == null) return 0;
        return (int) ColonyBuildingUtil.getBuildings(colony).stream()
                .filter(WarSystem::isGuardTower)
                .count();
    }

    /**
     * Determines if a building is a guard tower using multiple identification methods.
     * @param building The building to check
     * @return true if the building is a guard tower, false otherwise
     */
    public static boolean isGuardTower(IBuilding building) {
        if (building == null) return false;
        
        // Method 1: Check display name (current approach)
        String displayName = building.getBuildingDisplayName();
        if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) {
            return true;
        }
        
        // Method 2: Check if class name contains "guardtower"
        String className = building.getClass().getName().toLowerCase();
        if (className.contains("guardtower")) {
            return true;
        }
        
        // Method 3: Check if the building has guard-related functionality
        // This is a fallback in case the building class structure changes
        try {
            // Try to get the schematic name if available
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
        long immunityDuration = TaxConfig.EXTORTION_IMMUNITY_HOURS.get() * 60 * 60 * 1000L; // Convert hours to milliseconds
        long immunityExpiration = System.currentTimeMillis() + immunityDuration;
        extortionImmunity.put(colonyId, immunityExpiration);
        
        WARSYSTEM_LOGGER.info("Colony {} granted extortion immunity for {} hours", colonyId, TaxConfig.EXTORTION_IMMUNITY_HOURS.get());
    }
    
    /**
     * Shows the extortion choice prompt to the defender with enhanced clickable buttons and 5-minute timer
     */
    private static void showExtortionChoiceWithTimer(ServerPlayer attacker, IColony targetColony, ServerPlayer owner, int extortionPercent) {
        // Add the extortion request to pending requests during timer period
        pendingWarRequests.put(targetColony.getID(), new WarRequestWithExtortion(attacker.getUUID(), targetColony.getID(), extortionPercent));
        
        // Calculate time limit
        int timeLimitMinutes = TaxConfig.EXTORTION_RESPONSE_TIME_MINUTES.get();
        long timeLimitMs = timeLimitMinutes * 60 * 1000L;
        
        MutableComponent message = Component.literal("🏛️ URGENT: Colony " + targetColony.getName() + " is under siege! 🏛️")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("\n\n" + attacker.getName().getString() + " has declared war but offers terms:")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n💰 Pay " + extortionPercent + "% of your balance to avoid war")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n⚔️ Or let the war begin immediately (auto-accepted)")
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal("\n⏰ You have " + timeLimitMinutes + " minutes to decide!")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal("\n\nChoose quickly:\n").withStyle(ChatFormatting.WHITE))
                .append(createStartWarButton(targetColony))
                .append("  ")
                .append(createPayExtortionButton(targetColony, extortionPercent));

        owner.sendSystemMessage(message);
        
        // Auto-war start when the extortion window expires (MAIN server thread)
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
            Object pendingRequest = pendingWarRequests.remove(targetColony.getID());
            if (pendingRequest instanceof WarRequestWithExtortion) {
                // Time expired, start war automatically
                WARSYSTEM_LOGGER.info("Extortion time limit expired for colony {}. Starting war automatically.", targetColony.getID());
                if (targetColony.getWorld() != null && targetColony.getWorld().getServer() != null) {
                    ServerPlayer targetOwner = targetColony.getWorld().getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
                    if (targetOwner != null) {
                        targetOwner.sendSystemMessage(
                                Component.literal("⏰ Time expired! War begins automatically!")
                                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        );
                    }
                    ServerPlayer attackerPlayer = targetColony.getWorld().getServer().getPlayerList().getPlayer(attacker.getUUID());
                    if (attackerPlayer != null) {
                        attackerPlayer.sendSystemMessage(
                                Component.literal("⏰ " + targetColony.getName() + " failed to respond in time. War begins!")
                                        .withStyle(ChatFormatting.GOLD)
                        );
                    }
                    // Start the war join phase
                    startJoinPhase(targetColony, attacker, targetOwner);
                }
            }
        }, timeLimitMs);
        
        attacker.sendSystemMessage(Component.literal("War declaration with " + extortionPercent + "% extortion demand sent to " + targetColony.getName() + ". They have " + timeLimitMinutes + " minutes to respond.")
                .withStyle(ChatFormatting.YELLOW));
    }
    
    /**
     * Shows the extortion choice prompt to the defender with enhanced clickable buttons
     */
    private static void showExtortionChoice(ServerPlayer attacker, IColony targetColony, ServerPlayer owner, int extortionPercent) {
        MutableComponent message = Component.literal("🏛️ Colony " + targetColony.getName() + " is under siege! 🏛️\n")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("\n" + attacker.getName().getString() + " has declared war but offers terms:\n")
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

    // ==================== WAR PERSISTENCE ====================
    // Saves in-progress wars on server stop and resumes them on start, so wars survive
    // restarts. Ported from the 1.20.1 line but adapted to this NeoForge model: a single
    // guardIDs set (no attacker/defender split), no offlineOutpostWar flag, and the
    // WarChestManager (not TreasuryManager) defender-role API.

    private static final Gson WAR_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String WAR_STORAGE_FILE = "config/warntax/active_wars.json";
    /** Sentinel UUID written when an attacker/defender team ID was null at save time. */
    private static final UUID NULL_TEAM_ID_SENTINEL = new UUID(0L, 0L);

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
        /** Whether the attacking side ever reached the target. Durable fact about the war, so it is
         *  persisted; the retreat timers themselves deliberately are NOT (see restore). */
        boolean hasEngagedTarget;
        Map<String, Integer> attackerLives;
        Map<String, Integer> defenderLives;
        List<Integer> guardIDs;
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
        Map<String, Boolean> originalHostilePerms;            // Action.name() -> boolean
        Map<String, Boolean> originalHostilePermsForAttacker;
        List<String> acceptedAllies;
        List<String> declinedAllies;
        ProposalSaveEntry activeProposal;                     // null if no proposal in flight
    }

    private static class ProposalSaveEntry {
        String type;     // PeaceProposal.Type.name()
        int amount;
        String proposer; // UUID.toString()
    }

    private static class WarSaveData {
        List<WarSaveEntry> wars;
    }

    /** Persists all in-progress wars to {@code config/warntax/active_wars.json}. Called on server stop. */
    public static void saveActiveWars() {
        try {
            Path path = Paths.get(WAR_STORAGE_FILE);
            Files.createDirectories(path.getParent());

            WarSaveData saveData = new WarSaveData();
            saveData.wars = new ArrayList<>();

            for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
                WarData war = entry.getValue();

                // defenderTeamID can be null for wars against abandoned colonies; persist a
                // sentinel UUID instead of NPE-ing and dropping every subsequent war.
                UUID atkTid = war.getAttackerTeamID();
                UUID defTid = war.getDefenderTeamID();
                if (atkTid == null) atkTid = NULL_TEAM_ID_SENTINEL;
                if (defTid == null) defTid = NULL_TEAM_ID_SENTINEL;

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
                e.hasEngagedTarget = war.hasEngagedTarget;
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

                e.guardIDs = new ArrayList<>(war.getGuardIDs());
                e.attackerAllies = new ArrayList<>();
                war.getAttackerAllies().forEach(uuid -> e.attackerAllies.add(uuid.toString()));
                e.defenderAllies = new ArrayList<>();
                war.getDefenderAllies().forEach(uuid -> e.defenderAllies.add(uuid.toString()));
                e.spectators = new ArrayList<>();
                war.getSpectators().forEach(uuid -> e.spectators.add(uuid.toString()));
                e.lastLifeInventoryPreservation = new ArrayList<>();
                war.getLastLifeInventoryPreservation().forEach(uuid -> e.lastLifeInventoryPreservation.add(uuid.toString()));

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

                PeaceProposal pp = war.getActiveProposal();
                if (pp != null) {
                    ProposalSaveEntry pe = new ProposalSaveEntry();
                    pe.type = pp.getType().name();
                    pe.amount = pp.getAmount();
                    pe.proposer = pp.getProposer() != null ? pp.getProposer().toString() : null;
                    e.activeProposal = pe;
                }

                saveData.wars.add(e);
            }

            // Atomic write: write to a tmp file then atomic-move over the live file
            // (falls back to a plain replace on filesystems without ATOMIC_MOVE).
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

    /** Restores wars saved by {@link #saveActiveWars()}. Called on server start; deletes the file on full success. */
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

            int restored = 0, skipped = 0, total = saveData.wars.size();
            for (WarSaveEntry e : saveData.wars) {
                try {
                    if (resumeWarFromSave(e, server)) restored++; else skipped++;
                } catch (Exception ex) {
                    WARSYSTEM_LOGGER.error("Failed to restore war {} for colony {}", e.warID, e.defenderColonyId, ex);
                    skipped++;
                }
            }
            WARSYSTEM_LOGGER.info("War restoration complete: {} restored, {} skipped", restored, skipped);

            // Only delete the save when EVERY war restored; otherwise preserve it for recovery.
            if (skipped == 0) {
                Files.deleteIfExists(path);
            } else {
                Path failedPath = path.resolveSibling(path.getFileName() + ".failed-" + System.currentTimeMillis());
                try {
                    Files.move(path, failedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    WARSYSTEM_LOGGER.warn("Partial war restore ({} of {} skipped). Original save preserved at {}", skipped, total, failedPath);
                } catch (Exception moveEx) {
                    WARSYSTEM_LOGGER.error("Could not move active_wars.json to .failed-<ts>; leaving it at {}", path, moveEx);
                }
            }
        } catch (Exception ex) {
            WARSYSTEM_LOGGER.error("Failed to load active wars from disk", ex);
        }
    }

    private static Set<UUID> parseUUIDList(List<String> list) {
        Set<UUID> out = new HashSet<>();
        if (list != null) {
            for (String s : list) {
                try { out.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
        }
        return out;
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
            if (defenderColony != null && (e.attackerColonyId <= 0 || attackerColony != null)) break;
        }
        if (defenderColony == null) {
            WARSYSTEM_LOGGER.warn("Cannot restore war {}: defender colony {} no longer exists", e.warID, e.defenderColonyId);
            return false;
        }
        if (e.attackerColonyId > 0 && attackerColony == null) {
            WARSYSTEM_LOGGER.warn("Cannot restore war {}: attacker colony {} no longer exists", e.warID, e.attackerColonyId);
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
        if (e.attackerLives != null) e.attackerLives.forEach((k, v) -> attackerLives.put(UUID.fromString(k), v));
        Map<UUID, Integer> defenderLives = new HashMap<>();
        if (e.defenderLives != null) e.defenderLives.forEach((k, v) -> defenderLives.put(UUID.fromString(k), v));
        Set<Integer> guardIDSet = e.guardIDs != null ? new HashSet<>(e.guardIDs) : new HashSet<>();
        Set<UUID> attackerAlliesSet = parseUUIDList(e.attackerAllies);
        Set<UUID> defenderAlliesSet = parseUUIDList(e.defenderAllies);
        Set<UUID> spectatorsSet = parseUUIDList(e.spectators);
        Set<UUID> lastLifeSet = parseUUIDList(e.lastLifeInventoryPreservation);
        Set<UUID> acceptedAlliesSet = parseUUIDList(e.acceptedAllies);
        Set<UUID> declinedAlliesSet = parseUUIDList(e.declinedAllies);

        long now = System.currentTimeMillis();
        // Wars that ran out their clock while the server was down: construct + register,
        // then immediately resolve via the canonical end-of-time handler so rewards/ranks fire.
        boolean expiredDuringDowntime = false;
        if (status == WarData.WarStatus.INWAR) {
            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60L * 1000L;
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

        // Convert sentinel team IDs back into null so callers see the same invariant.
        UUID atkTid;
        UUID defTid;
        try { atkTid = UUID.fromString(e.attackerTeamID); if (NULL_TEAM_ID_SENTINEL.equals(atkTid)) atkTid = null; }
        catch (IllegalArgumentException iae) { atkTid = null; }
        try { defTid = UUID.fromString(e.defenderTeamID); if (NULL_TEAM_ID_SENTINEL.equals(defTid)) defTid = null; }
        catch (IllegalArgumentException iae) { defTid = null; }

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("War for " + defenderColony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bossEvent.setProgress(1.0f);
        bossEvent.setVisible(true);

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

        PeaceProposal restoredProposal = null;
        if (e.activeProposal != null && e.activeProposal.type != null && e.activeProposal.proposer != null) {
            try {
                restoredProposal = new PeaceProposal(
                        PeaceProposal.Type.valueOf(e.activeProposal.type),
                        e.activeProposal.amount,
                        UUID.fromString(e.activeProposal.proposer));
            } catch (IllegalArgumentException ignored) {}
        }

        WarData warData = new WarData(
                UUID.fromString(e.warID), UUID.fromString(e.attacker), UUID.fromString(e.defender),
                atkTid, defTid,
                e.warStartTime, e.joinPhaseEndTime, bossEvent, defenderColony, attackerColony,
                status, e.accepted,
                e.initialAttackerGuards, e.remainingAttackerGuards,
                e.initialDefenderGuards, e.remainingDefenderGuards,
                e.initialAttackerTotalLives, e.initialDefenderTotalLives,
                attackerLives, defenderLives, guardIDSet,
                attackerAlliesSet, defenderAlliesSet, spectatorsSet, lastLifeSet,
                e.penaltyReport, e.stalemateTriggered,
                restoredHostilePerms, restoredHostilePermsAtk,
                acceptedAlliesSet, declinedAlliesSet,
                restoredProposal);

        // Retreat state. hasEngagedTarget is a durable fact — an attacking side that already reached
        // the target before the restart is still "engaged", and dropping it would silently disable
        // the retreat rule for the rest of the war.
        warData.hasEngagedTarget = e.hasEngagedTarget;
        // The two wall-clock timers are deliberately NOT restored. They measure "how long has this
        // player been away RIGHT NOW", and the server was down in between: reinstating a stale
        // retreatingSinceMs would make the very first tick after startup see an elapsed grace and
        // forfeit the war before anyone could log in and return. Both restart from zero.
        warData.retreatingSinceMs = 0L;
        warData.respawnGraceUntilMs = 0L;

        ACTIVE_WARS.put(e.defenderColonyId, warData);

        if (expiredDuringDowntime) {
            try {
                handleTimeExpiry(warData);
            } catch (Throwable t) {
                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
                try { endWar(defenderColony); }
                catch (Throwable t2) {
                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for war {}; removing without rewards.", e.warID, t2);
                    ACTIVE_WARS.remove(e.defenderColonyId);
                }
            }
            return true;
        }

        // Restore the WarChest defender role (drain bookkeeping) and restart the drain, which
        // does not survive a restart because TickScheduler tasks are in-memory only.
        try {
            net.machiavelli.minecolonytax.economy.WarChestManager.setColonyAsDefender(e.defenderColonyId);
            scheduleWarChestDrain(warData, defenderColony, warData.getAttackerColony());
        }
        catch (Throwable t) { WARSYSTEM_LOGGER.warn("Could not restore WarChest defender role for colony {}: {}", e.defenderColonyId, t.toString()); }

        for (UUID uuid : warData.getAttackerLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) bossEvent.addPlayer(p);
        }
        for (UUID uuid : warData.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) bossEvent.addPlayer(p);
        }

        setWarInteractionPermissions(defenderColony, true);
        if (attackerColony != null) setWarInteractionPermissions(attackerColony, true);

        if (warData.getStatus() == WarData.WarStatus.INWAR) {
            applyWarGlowToParticipants(warData);
            applyGuardGlow(defenderColony);
            if (attackerColony != null) applyGuardGlow(attackerColony);
            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
            if (attackerColony != null) GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);

            startWarCountdown(warData);

            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60L * 1000L;
            long remaining = warDurationMs - (now - warData.warStartTime);
            if (remaining > 0) scheduleTimerWarnings(warData, remaining);

            updateBossBar(warData);
            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)", e.warID, defenderColony.getName(), remaining);
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
                net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
                    if (!ACTIVE_WARS.containsKey(colonyId)) return;
                    WarData w = ACTIVE_WARS.get(colonyId);
                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING) return;
                    w.setStatus(WarData.WarStatus.INWAR);
                    w.warStartTime = System.currentTimeMillis();
                    // Abort the resume too if the start checks fail (finalizeWarStart ended the war).
                    if (!finalizeWarStart(w)) return;
                    setWarInteractionPermissions(w.getColony(), true);
                    if (w.getAttackerColony() != null) setWarInteractionPermissions(w.getAttackerColony(), true);
                    startWarCountdown(w);
                    // finalizeWarStart already scheduled the timer warnings on success (full WAR_DURATION),
                    // and the main start path doesn't re-call it either — drop the duplicate schedule here.
                }, remainingJoinMs);
            }
            updateBossBar(warData);
            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)", e.warID, defenderColony.getName(), remainingJoinMs);
        }
        return true;
    }

    /**
     * Creates a clickable button to pay extortion
     */
    private static MutableComponent createPayExtortionButton(IColony colony, int extortionPercent) {
        return Component.literal("[💰 PAY EXTORTION " + extortionPercent + "%]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt payextortion " + colony.getID() + " " + extortionPercent))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                Component.literal("Click to pay " + extortionPercent + "% of your balance to avoid war")
                                        .withStyle(ChatFormatting.YELLOW)))
                );
    }
}