package net.machiavelli.minecolonytax;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding; // Corrected import
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.FTBTeamsAPIImpl;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.data.PartyTeam;
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
import net.minecraftforge.server.ServerLifecycleHooks;
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

public class WarSystem {

    private static final Logger WARSYSTEM_LOGGER = LogManager.getLogger(WarSystem.class);
    private static final Map<Integer, WarRequest> pendingWarRequests = new ConcurrentHashMap<>();
    public record WarRequest(UUID attacker, int colonyId) { }

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

    private static boolean isFTBTeamsLoaded() {
        try {
            Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static final boolean FTB_TEAMS_INSTALLED = isFTBTeamsLoaded();
    public static final TeamManager FTB_TEAM_MANAGER = FTB_TEAMS_INSTALLED ? FTBTeamsAPIImpl.INSTANCE.getManager() : null;
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

    public static void initiateWar(ServerPlayer attacker, UUID defender, Team attackerTeam, Team defenderTeam, IColony colony, IColony attackerColony) {
        UUID attackerTeamID = (FTB_TEAMS_INSTALLED && attackerTeam != null) ? attackerTeam.getId() : attacker.getUUID();
        UUID defenderTeamID = (FTB_TEAMS_INSTALLED && defenderTeam != null) ? defenderTeam.getId() : colony.getPermissions().getOwner();

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
            System.out.println("[DEBUG] Adding attacker colony members from " + attackerColony.getName());
            
            attackerPerms.getPlayers().forEach((uuid, player) -> {
                if (!uuid.equals(attacker.getUUID())) { // Don't add attacker twice
                    Rank rank = attackerPerms.getRank(uuid);
                    if (rank != null && (rank.equals(attackerPerms.getRankOfficer()) || rank.equals(attackerPerms.getRankFriend()))) {
                        data.getAttackerLives().put(uuid, playerLives);
                        System.out.println("[DEBUG] Added attacker colony member " + uuid + " with rank " + rank.getName());
                        
                        // Assign hostile rank to this attacker on defender's colony
                        assignWarParticipantRanks(uuid, colony, attackerColony, true);
                        
                        // Send join prompt to eligible players
                        if (colony.getWorld() != null) {
                            MinecraftServer server = colony.getWorld().getServer();
                            if (server != null) {
                                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                                if (p != null && p.isAlive()) {
                                    p.sendSystemMessage(Component.literal("War has been declared! You are eligible to join as an attacker.").withStyle(ChatFormatting.YELLOW));
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
        System.out.println("[DEBUG] Adding defender colony members from " + colony.getName());
        
        defenderPerms.getPlayers().forEach((uuid, player) -> {
            if (!uuid.equals(colony.getPermissions().getOwner())) { // Don't add owner twice
                Rank rank = defenderPerms.getRank(uuid);
                if (rank != null && (rank.equals(defenderPerms.getRankOfficer()) || rank.equals(defenderPerms.getRankFriend()))) {
                    data.getDefenderLives().put(uuid, playerLives);
                    System.out.println("[DEBUG] Added defender colony member " + uuid + " with rank " + rank.getName());
                    
                    // Assign hostile rank to this defender on attacker's colony (if it exists)
                    assignWarParticipantRanks(uuid, colony, attackerColony, false);
                    
                    // Send join prompt to eligible players
                    if (colony.getWorld() != null) {
                        MinecraftServer server = colony.getWorld().getServer();
                        if (server != null) {
                            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                p.sendSystemMessage(Component.literal("Your colony is under attack! You are eligible to join as a defender.").withStyle(ChatFormatting.RED));
                                p.sendSystemMessage(JOIN_MSG);
                            }
                        }
                    }
                }
            }
        });
        
        // Optional: Add FTB Team members if FTB Teams is installed
        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
            System.out.println("[DEBUG] FTB Teams detected, adding team members as additional participants");
            
            if (attackerTeam != null) {
                attackerTeam.getMembers().forEach(uuid -> {
                    if (!data.getAttackerLives().containsKey(uuid)) { // Don't add if already added via colony
                        data.getAttackerLives().put(uuid, playerLives);
                        System.out.println("[DEBUG] Added FTB team member to attackers: " + uuid);
                        
                        // Assign hostile rank to this attacker on defender's colony
                        assignWarParticipantRanks(uuid, colony, attackerColony, true);
                        
                        // Send join prompt
                        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                            ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                p.sendSystemMessage(Component.literal("Your team is at war! You are eligible to join as an attacker.").withStyle(ChatFormatting.YELLOW));
                                p.sendSystemMessage(JOIN_MSG);
                            }
                        }
                    }
                });
            }
            
            if (defenderTeam != null) {
                defenderTeam.getMembers().forEach(uuid -> {
                    if (!data.getDefenderLives().containsKey(uuid)) { // Don't add if already added via colony
                        data.getDefenderLives().put(uuid, playerLives);
                        System.out.println("[DEBUG] Added FTB team member to defenders: " + uuid);
                        
                        // Assign hostile rank to this defender on attacker's colony (if it exists)
                        assignWarParticipantRanks(uuid, colony, attackerColony, false);
                        
                        // Send join prompt
                        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                            ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                            if (p != null && p.isAlive()) {
                                p.sendSystemMessage(Component.literal("Your team's colony is under attack! You are eligible to join as a defender.").withStyle(ChatFormatting.RED));
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
                System.out.println("[DEBUG] Assigned hostile rank to attacker " + playerUUID + " on defender colony " + defenderColony.getName());
            } else {
                // Defenders get hostile rank on attacker colony (if it exists)
                if (attackerColony != null) {
                    IPermissions attackerPerms = attackerColony.getPermissions();
                    attackerPerms.setPlayerRank(playerUUID, attackerPerms.getRankHostile(), attackerColony.getWorld());
                    System.out.println("[DEBUG] Assigned hostile rank to defender " + playerUUID + " on attacker colony " + attackerColony.getName());
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

    public static void finalizeWarStart(WarData war) {
        int attackerPlayerCount = war.getAttackerLives().size();
        int defenderPlayerCount = war.getDefenderLives().size();

        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("War cancelled due to lack of participants.")
                                .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)), false);
            }
            endWar(war.getColony());
            return;
        }

        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
                                .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)), false);
            }
            return;
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
                if (p != null) assignWarGroup(p);
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
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(warBeginMsg, false);
        }
        long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
        scheduleTimerWarnings(war, warDurationMillis);
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
        
        System.out.println("[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: " + defendersWin);
        System.out.println("[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
        System.out.println("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: " + war.getRemainingDefenderGuards());

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
        war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(victoryMsg, false);
        for (UUID defenderUUID : war.getDefenderLives().keySet()) {
            ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
            if (defender != null) {
                }
            }
            // Apply victory/defeat balance transfers - defenders win, attackers pay
            applyWarEconomyTransfers(war, false);
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
            war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(conquestMsg, false);
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList().getPlayer(attackerUUID); 
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsWon(attackerPlayer);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                }
            }
            // Apply victory/defeat balance transfers - attackers win, defenders pay
            applyWarEconomyTransfers(war, true);
            if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                transferOwnership(war.getColony(), war.getAttacker());
            }
        }
        endWar(war.getColony());
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
                war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(ecoMsg, false);
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
                war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(notification, false);
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
                // Team-based battle
                
                // Get a representative from winner team to show notification to
                ServerPlayer winnerRepresentative = null;
                for (UUID uuid : attackersWon ? war.getAttackerLives().keySet() : war.getDefenderLives().keySet()) {
                    winnerRepresentative = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (winnerRepresentative != null) break;
                }
                
                // Apply team economic penalties
                long amountTransferred = 0;
                // Get all losers' UUIDs
                List<UUID> losers = new ArrayList<>(attackersWon ? war.getDefenderLives().keySet() : war.getAttackerLives().keySet());
                
                // For each loser, transfer a percentage of their balance to the winner team
                for (UUID loserUUID : losers) {
                    ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                    if (loser != null) {
                        if (winnerRepresentative != null) {
                            // Transfer from loser to winner representative
                            amountTransferred += WarEconomyHandler.transferBalanceToPlayer(loserUUID, winnerRepresentative.getUUID(), transferPercentage);
                        } else {
                            // Just deduct from loser if no winner is online
                            amountTransferred += WarEconomyHandler.deductTeamBalanceWithReport(loserUUID, transferPercentage);
                        }
                    }
                }
                
                totalTransferred = amountTransferred;
            } else {
                // Individual transfers between colony owners or war initiators
                UUID winnerUUID = attackersWon ? war.getAttacker() : war.getColony().getPermissions().getOwner();
                UUID loserUUID = attackersWon ? war.getColony().getPermissions().getOwner() : war.getAttacker();
                
                ServerPlayer winner = war.getColony().getWorld().getServer().getPlayerList().getPlayer(winnerUUID);
                ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                
                if (winner != null && loser != null) {
                    totalTransferred = (long) WarEconomyHandler.transferBalanceToPlayer(loserUUID, winnerUUID, transferPercentage);
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
                    for (IBuilding building : winnerColony.getBuildingManager().getBuildings().values()) {
                        String buildingType = building.getBuildingDisplayName();
                        double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                        double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * building.getBuildingLevel();
                        expectedTaxRevenue += (int) (baseTax + upgradeTax);
                    }
                    
                    // Set reparations amount based on a percentage of expected tax revenue
                    reparationsAmount = (int)(expectedTaxRevenue * transferPercentage);
                    
                    // Ensure minimum reparations amount if any buildings exist
                    if (reparationsAmount <= 0 && !winnerColony.getBuildingManager().getBuildings().isEmpty()) {
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
                    
                    // Announce to both colony owners if they are online
                    if (loserColony.getPermissions() != null && loserColony.getPermissions().getOwner() != null) {
                        ServerPlayer loserOwner = loserColony.getWorld().getServer().getPlayerList()
                            .getPlayer(loserColony.getPermissions().getOwner());
                        if (loserOwner != null) {
                            Component message = Component.literal("Your colony has paid " + reparationsAmount + 
                                    " in war reparations! Colony tax: " + TaxManager.getStoredTaxForColony(loserColony))
                                    .withStyle(ChatFormatting.RED);
                            loserOwner.sendSystemMessage(message);
                        }
                    }
                    
                    if (winnerColony.getPermissions() != null && winnerColony.getPermissions().getOwner() != null) {
                        ServerPlayer winnerOwner = winnerColony.getWorld().getServer().getPlayerList()
                            .getPlayer(winnerColony.getPermissions().getOwner());
                        if (winnerOwner != null) {
                            Component message = Component.literal("Your colony has received " + reparationsAmount + 
                                    " in war reparations! Colony tax: " + TaxManager.getStoredTaxForColony(winnerColony))
                                    .withStyle(ChatFormatting.GREEN);
                            winnerOwner.sendSystemMessage(message);
                        }
                    }
                }
            } else {
                // Backup to player inventory transfers if colonies are not available
                // Team-based transfers using inventory currency
                long amountTransferred = 0;
                List<UUID> losers = new ArrayList<>(attackersWon ? war.getDefenderLives().keySet() : war.getAttackerLives().keySet());
                
                // Choose a representative from the winners to receive the funds
                ServerPlayer winnerRepresentative = null;
                for (UUID uuid : attackersWon ? war.getAttackerLives().keySet() : war.getDefenderLives().keySet()) {
                    winnerRepresentative = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (winnerRepresentative != null) break;
                }
                
                if (winnerRepresentative != null) {
                    for (UUID loserUUID : losers) {
                        ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                        if (loser != null) {
                            amountTransferred += WarEconomyHandler.transferBalanceToPlayer(loserUUID, winnerRepresentative.getUUID(), transferPercentage);
                        }
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
        
        // Announce the economic transfer
        if (totalTransferred > 0) {
            Component ecoMsg = Component.literal("War Reparations: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(loserColonyName).withStyle(ChatFormatting.RED))
                .append(Component.literal(" has paid ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(totalTransferred)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" in war reparations to ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(winnerColonyName).withStyle(ChatFormatting.GREEN));
                
            war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(ecoMsg, false);
        }
    }

    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
        if (colony.getWorld() == null || colony.getWorld().getServer() == null) return;
        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null) return;
        if (colony.getPermissions().setOwner(newOwner)) {
            colony.markDirty();
            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));
            colony.getWorld().getServer().getPlayerList().broadcastSystemMessage(msg, false);
        } else {
            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
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
        }
        
        // Disable war actions for both sides
        setWarInteractionPermissions(colony, false);
        
        // Also disable for attacker colony if it exists
        if (warData != null && warData.getAttackerColony() != null) {
            setWarInteractionPermissions(warData.getAttackerColony(), false);
        }
        
        // Now remove from active wars
        warData = ACTIVE_WARS.remove(colony.getID());
        if (warData != null) {
            if (warData.timerTask != null) {
                warData.timerTask.cancel();
                warData.timerTask = null;
            }
            if (warData.bossEvent != null) {
                warData.bossEvent.removeAllPlayers();
                warData.bossEvent.setVisible(false);
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
            broadcastComponent(defenderVictoryMsg);
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
            broadcastComponent(attackerVictoryMsg);
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
            broadcastComponent(stalemateNoLossesMsg);
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
            broadcastComponent(strategicMsg);
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
            broadcastComponent(strategicMsg);
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
            broadcastComponent(strategicMsg);
        }
        war.setPenaltyReport(reportOutcome);
        endWar(war.getColony());
    }

    // Helper to broadcast component messages
    private static void broadcastComponent(Component message) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    public static void handleGuardKilled(WarData war, boolean isDefenderGuard) {
        if (isDefenderGuard) {
            war.remainingDefenderGuards--;
            Component message = Component.translatable("war.guard.killed.defender", war.getRemainingDefenderGuards())
                    .withStyle(style -> style.withColor(ChatFormatting.RED));
            notifyWarParticipants(war, message);
        } else {
            war.remainingAttackerGuards--;
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

        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // Attackers win and transfer is on
            transferOwnership(war.getColony(), war.getAttacker());
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
        System.out.println("[DEBUG] getLivesForPlayer called for player " + player.getName().getString() + " (" + player.getUUID() + ")");
        System.out.println("[DEBUG] FTB_TEAMS_INSTALLED: " + FTB_TEAMS_INSTALLED);
        System.out.println("[DEBUG] Attacker lives: " + war.getAttackerLives());
        System.out.println("[DEBUG] Defender lives: " + war.getDefenderLives());
        
        // First check if player is directly in the lives maps
        UUID playerUUID = player.getUUID();
        System.out.println("[DEBUG] Checking if attacker lives contains player UUID: " + war.getAttackerLives().containsKey(playerUUID));
        System.out.println("[DEBUG] Checking if defender lives contains player UUID: " + war.getDefenderLives().containsKey(playerUUID));
        
        if (war.getAttackerLives().containsKey(playerUUID)) {
            System.out.println("[DEBUG] Player found in attacker lives, returning attacker lives");
            return war.getAttackerLives();
        } else if (war.getDefenderLives().containsKey(playerUUID)) {
            System.out.println("[DEBUG] Player found in defender lives, returning defender lives");
            return war.getDefenderLives();
        }
        
        // Check if player is in attacker or defender allies
        if (war.getAttackerAllies().contains(playerUUID)) {
            System.out.println("[DEBUG] Player found in attacker allies, returning attacker lives");
            return war.getAttackerLives();
        } else if (war.getDefenderAllies().contains(playerUUID)) {
            System.out.println("[DEBUG] Player found in defender allies, returning defender lives");
            return war.getDefenderLives();
        }
        
        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
            Optional<Team> teamOpt = FTB_TEAM_MANAGER.getPlayerTeamForPlayerID(playerUUID);
            System.out.println("[DEBUG] Player team found: " + teamOpt.isPresent());
            if (teamOpt.isPresent()) {
                Team team = teamOpt.get();
                System.out.println("[DEBUG] Player team ID: " + team.getId());
                System.out.println("[DEBUG] War attacker team ID: " + war.getAttackerTeamID());
                System.out.println("[DEBUG] War defender team ID: " + war.getDefenderTeamID());
                
                if (team.getId().equals(war.getAttackerTeamID())) {
                    System.out.println("[DEBUG] Player is on attacker team, returning attacker lives");
                    return war.getAttackerLives();
                } else if (team.getId().equals(war.getDefenderTeamID())) {
                    System.out.println("[DEBUG] Player is on defender team, returning defender lives");
                    return war.getDefenderLives();
                }
                
                // Check if player is allied to any participating team
                Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
                if (atkTeam != null && atkTeam.isPartyTeam() && ((PartyTeam) atkTeam).getMembers().contains(playerUUID)) {
                    System.out.println("[DEBUG] Player is allied to attacker team, returning attacker lives");
                    return war.getAttackerLives();
                }
                
                Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
                if (defTeam != null && defTeam.isPartyTeam() && ((PartyTeam) defTeam).getMembers().contains(playerUUID)) {
                    System.out.println("[DEBUG] Player is allied to defender team, returning defender lives");
                    return war.getDefenderLives();
                }
                
                System.out.println("[DEBUG] Player team not participating in war, checking Minecolonies membership");
            } else {
                System.out.println("[DEBUG] Player has no FTB team, checking Minecolonies membership");
            }
        }
        
        // Check Minecolonies colony membership and ranks
        IColony attackerColony = war.getAttackerColony();
        IColony defenderColony = war.getColony();
        
        System.out.println("[DEBUG] Checking Minecolonies membership - Attacker colony: " + (attackerColony != null ? attackerColony.getName() : "null"));
        System.out.println("[DEBUG] Checking Minecolonies membership - Defender colony: " + (defenderColony != null ? defenderColony.getName() : "null"));
        
        // Check if player is in attacker colony (owner, officer, or friend)
        if (attackerColony != null) {
            IPermissions attackerPerms = attackerColony.getPermissions();
            System.out.println("[DEBUG] Player in attacker colony players list: " + attackerPerms.getPlayers().containsKey(playerUUID));
            if (attackerPerms.getPlayers().containsKey(playerUUID)) {
                Rank playerRank = attackerPerms.getRank(playerUUID);
                System.out.println("[DEBUG] Player rank in attacker colony: " + (playerRank != null ? playerRank.getName() : "null"));
                if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) || 
                                          playerRank.equals(attackerPerms.getRankOfficer()) ||
                                          playerRank.equals(attackerPerms.getRankFriend()))) {
                    System.out.println("[DEBUG] Player is in attacker colony with rank " + playerRank.getName() + ", returning attacker lives");
                    return war.getAttackerLives();
                }
            }
        }
        
        // Check if player is in defender colony (owner, officer, or friend)
        if (defenderColony != null) {
            IPermissions defenderPerms = defenderColony.getPermissions();
            System.out.println("[DEBUG] Player in defender colony players list: " + defenderPerms.getPlayers().containsKey(playerUUID));
            if (defenderPerms.getPlayers().containsKey(playerUUID)) {
                Rank playerRank = defenderPerms.getRank(playerUUID);
                System.out.println("[DEBUG] Player rank in defender colony: " + (playerRank != null ? playerRank.getName() : "null"));
                if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) || 
                                          playerRank.equals(defenderPerms.getRankOfficer()) ||
                                          playerRank.equals(defenderPerms.getRankFriend()))) {
                    System.out.println("[DEBUG] Player is in defender colony with rank " + playerRank.getName() + ", returning defender lives");
                    return war.getDefenderLives();
                }
            }
        }
        
        System.out.println("[DEBUG] Player not participating in war, returning empty map");
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
            if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
                Optional<Team> teamOpt = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID());
                if (teamOpt.isPresent()) {
                    Team team = teamOpt.get();
                    if (team.getId().equals(war.getAttackerTeamID()) || team.getId().equals(war.getDefenderTeamID())) {
                        return war;
                    }
                    
                    // Check if player is allied to any participating team
                    Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
                    if (atkTeam != null && atkTeam.isPartyTeam() && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) {
                        return war;
                    }
                    
                    Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
                    if (defTeam != null && defTeam.isPartyTeam() && ((PartyTeam) defTeam).getMembers().contains(player.getUUID())) {
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
        Timer warningTimer = new Timer();
        long quarter = warDurationMillis / 4;
        for (int i = 1; i <= 3; i++) {
            long delay = quarter * i;
            if (delay <= 0) continue;
            warningTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Check if war still exists in active wars or if the colony world/server is null
                    if (!ACTIVE_WARS.containsKey(war.getColony().getID()) || 
                        war.getColony().getWorld() == null || 
                        war.getColony().getWorld().getServer() == null || 
                        war.bossEvent == null) {
                        this.cancel();
                        return;
                    }
                    
                    // Check war status - don't process for ended wars
                    if (war.getStatus() != WarData.WarStatus.INWAR) {
                        this.cancel();
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
                        this.cancel();
                    }
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

    private static void broadcast(String message, ChatFormatting color) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        Component msg = Component.literal(message).withStyle(Style.EMPTY.withColor(color).withBold(true));
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    public static void onPlayerKilledInWar(ServerPlayer killer, ServerPlayer killed, WarData war) {
        if (killer != null && killed != null && war != null) {
            PlayerWarDataManager.incrementPlayersKilledInWar(killer);
        }
    }
    
    public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
        Team attackerTeam = FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null
                ? FTB_TEAM_MANAGER.getTeamForPlayerID(attacker.getUUID()).orElse(null)
                : null;
        Team defenderTeam = FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null
                ? FTB_TEAM_MANAGER.getTeamForPlayerID(owner.getUUID()).orElse(null)
                : null;

        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
                .findFirst().orElse(null);
        if (attackerColony == null) {
            attacker.sendSystemMessage(Component.literal("You must own a colony to declare war.")
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
            
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("war.join.phase.declared", colony.getName(), timeRemaining),
                    false);
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

        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
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

        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
            if (attackerTeam != null) sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
            if (defenderTeam != null) sendNotificationToColonyParticipants(colony, joinPhaseInfo);
        } else {
            sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
            sendNotificationToColonyParticipants(colony, joinPhaseInfo);
        }

        // Add countdown sound timer for the last 6 seconds of join phase, but only if join phase is at least 6 seconds long
        if (joinDurationMillis >= 6000) {
            new Timer().schedule(new TimerTask() {
                int secondsLeft = 6;
                @Override
                public void run() {
                    try {
                        if (war == null || war.getColony() == null || !war.isJoinPhaseActive()) { 
                            this.cancel(); 
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
                                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
                                if (player != null) {
                                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F, 1.0F);
                                }
                            }
                        }
                        
                        // Notify remaining seconds
                        notifyWarParticipants(war, 
                            Component.literal("⏱ " + secondsLeft + (secondsLeft == 1 ? " second" : " seconds") + " until war starts!")
                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
                        
                        secondsLeft--;
                        if (secondsLeft < 0) {
                            this.cancel();
                        }
                    } catch (Exception ex) {
                        // Catch any exceptions to prevent timer from crashing
                        WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
                        this.cancel();
                    }
                }
            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1 second
        }
        
        // Main timer to start the war when join phase ends
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (war == null || war.getColony() == null) { this.cancel(); return;} // Null check for war
                war.setStatus(WarData.WarStatus.INWAR);
                war.warStartTime = System.currentTimeMillis();
                finalizeWarStart(war);
                // Enable war actions for both sides
                setWarInteractionPermissions(war.getColony(), true);
                if (war.getAttackerColony() != null) {
                    setWarInteractionPermissions(war.getAttackerColony(), true);
                }
                startWarCountdown(war);
            }
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

    private static void startWarCountdown(WarData warData) {
        if (warData.getColony().getWorld() == null) {
            WARSYSTEM_LOGGER.error("Cannot start war countdown, world is null for colony {}", warData.getColony().getID());
            return;
        }
        final long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
        warData.timerTask = new TimerTask() {
            @Override
            public void run() {
                // Check if war still exists in active wars or if the colony world/server is null
                if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) || 
                    warData.getColony().getWorld() == null || 
                    warData.getColony().getWorld().getServer() == null || 
                    warData.bossEvent == null) {
                    this.cancel();
                    return;
                }
                
                // Check war status - don't process for ended wars
                if (warData.getStatus() != WarData.WarStatus.INWAR) {
                    this.cancel();
                    return;
                }
                
                long elapsedSeconds = (System.currentTimeMillis() - warData.warStartTime) / 1000;
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
                    this.cancel();
                }
            }
        };
        new Timer().scheduleAtFixedRate(warData.timerTask, 1000, 1000);
    }

    public static void sendColonyMessage(IColony colony, Component message) {
        if (colony == null || colony.getWorld() == null) return;
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
            if (p != null) p.sendSystemMessage(message);
        });
    }

    public static void sendMessageToTeam(Team team, Component msg) {
        if (team == null || ServerLifecycleHooks.getCurrentServer() == null) return;
        for (UUID member : team.getMembers()) {
            ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
            if (sp != null) sp.sendSystemMessage(msg);
        }
    }
    
    public static int processWageWarRequest(ServerPlayer attacker, IColony targetColony, CommandSourceStack source) {
        Level level = source.getLevel(); 

        int targetGuards = countGuards(targetColony); 
        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) { 
            source.sendFailure(Component.literal("Target colony must have at least " + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
            return 0;
        }

        IColony attackerColony = IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
                .findFirst().orElse(null);
        if (attackerColony == null) {
            source.sendFailure(Component.literal("You must own a colony to declare war."));
            return 0;
        }
        int attackerGuards = countGuards(attackerColony);
        if (attackerGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) { 
            source.sendFailure(Component.literal("Your colony must have at least " + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + attackerGuards + ")"));
            return 0;
        }
        if (targetColony.getID() == attackerColony.getID()) {
            source.sendFailure(Component.literal("Cannot declare war on your own colony!"));
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
                ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(autoAcceptMsg, false);
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
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(warDeclarationMsg, false);
        }
        pendingWarRequests.put(targetColony.getID(), new WarRequest(attacker.getUUID(), targetColony.getID()));
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                WarRequest removedRequest = pendingWarRequests.remove(targetColony.getID());
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

    public static int processWarResponse(ServerPlayer executor, int colonyId, boolean accepted, CommandSourceStack source) {
        WarRequest request = pendingWarRequests.get(colonyId);
        if (request == null) {
            source.sendFailure(Component.literal("No active war request found for colony ID " + colonyId +
                            ". Only an authorized officer or the colony owner may accept.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn("No pending war request found for colony ID {} when {} attempted to respond.", colonyId, executor.getName().getString());
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
        boolean isAuthorized = targetColony.getPermissions().getOwner().equals(executor.getUUID()) ||
                (executorRank != null && executorRank.isColonyManager());
        if (!isAuthorized) {
            source.sendFailure(Component.literal("You are not authorized to accept/decline this war request.")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn("{} is not authorized to respond to war request for colony {}.", executor.getName().getString(), targetColony.getName());
            return 0;
        }
        
        final ServerPlayer attacker; // Declared final
        if (source.getServer() != null) {
            attacker = source.getServer().getPlayerList().getPlayer(request.attacker());
        } else {
            attacker = null; // Ensure attacker is initialized if server is null
        }

        if (attacker == null) {
            source.sendFailure(Component.literal("Attacker is offline!")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
            WARSYSTEM_LOGGER.warn("Attacker {} is offline when {} tried to respond to war request for colony {}.", request.attacker(), executor.getName().getString(), targetColony.getName());
            return 0;
        }
        pendingWarRequests.remove(colonyId); 

        if (accepted) {
            WARSYSTEM_LOGGER.info("War request for colony {} accepted by {}.", targetColony.getID(), executor.getName().getString());
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                    .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
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
                ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(warAcceptedMsg, false);
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
                ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(warDeclinedMsg, false);
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
        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
            Team playerTeam = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID()).orElse(null);
            Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
            Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);

            // Direct team membership
            if (playerTeam != null && (playerTeam.getId().equals(war.getAttackerTeamID()) || 
                                      playerTeam.getId().equals(war.getDefenderTeamID()))) {
                return true;
            }
            
            // Allied team membership
            if ((atkTeam != null && atkTeam.isPartyTeam() && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) ||
                (defTeam != null && defTeam.isPartyTeam() && ((PartyTeam) defTeam).getMembers().contains(player.getUUID()))) {
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
        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
            Team playerTeam = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID()).orElse(null);
            Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
            Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);

            // Direct team membership
            if (playerTeam != null && playerTeam.getId().equals(war.getAttackerTeamID())) {
                canJoinAttackers = true;
            }
            if (playerTeam != null && playerTeam.getId().equals(war.getDefenderTeamID())) {
                canJoinDefenders = true;
            }
            
            // Allied team membership
            if (atkTeam != null && atkTeam.isPartyTeam() && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) {
                canJoinAttackers = true;
            }
            if (defTeam != null && defTeam.isPartyTeam() && ((PartyTeam) defTeam).getMembers().contains(player.getUUID())) {
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
     * Counts the number of guard towers in a colony.
     * @param colony The colony to count guard towers for
     * @return The number of guard towers in the colony, or 0 if the colony is invalid
     */
    public static int countGuardTowers(IColony colony) {
        if (colony == null || colony.getBuildingManager() == null) return 0;
        return (int) colony.getBuildingManager().getBuildings().values().stream()
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
}