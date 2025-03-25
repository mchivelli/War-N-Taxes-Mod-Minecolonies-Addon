package net.machiavelli.minecolonytax;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import dev.ftb.mods.ftbteams.FTBTeamsAPIImpl;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.event.WarEconomyHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.Scoreboard;

import net.minecraftforge.server.ServerLifecycleHooks;

import net.minecraft.server.level.ServerPlayer;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static dev.ftb.mods.ftbteams.FTBTeams.LOGGER;


public class WarSystem {

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

    // For debugging
    public static final long WAR_PHASE_DURATION_SECONDS = 60;

    public static void initiateWar(ServerPlayer attacker, UUID defender, Team attackerTeam, Team defenderTeam, IColony colony, IColony attackerColony) {
        // Determine fallback team IDs if necessary:
        UUID attackerTeamID = (FTB_TEAMS_INSTALLED && attackerTeam != null)
                ? attackerTeam.getId() : attacker.getUUID();
        UUID defenderTeamID = (FTB_TEAMS_INSTALLED && defenderTeam != null)
                ? defenderTeam.getId() : colony.getPermissions().getOwner();

        // Create the boss event for the join-phase.
        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("War for " + colony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bossEvent.setProgress(1.0f);
        bossEvent.setVisible(true);

        long now = System.currentTimeMillis();
        WarData data = new WarData(attacker.getUUID(), defender, attackerTeamID, defenderTeamID, now, bossEvent, colony, attackerColony);
        if (FTB_TEAMS_INSTALLED) {
            // For the attacker side: only auto-add if the player is an officer/manager in the attacker's colony.
            if (attackerTeam != null) {
                attackerTeam.getMembers().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null && p.isAlive()) {
                        // Check rank in the attacker's colony (attackerColony)
                        if (attackerColony.getPermissions().getRank(uuid) != null &&
                                attackerColony.getPermissions().getRank(uuid).isColonyManager()) {
                            data.getAttackerLives().put(uuid, 5);
                        }
                        // Do not auto-add allies – they must join manually.
                    }
                });
            } else {
                data.getAttackerLives().put(attacker.getUUID(), 5);
            }

            // For the defender side: only auto-add if the player is an officer/manager in the defender colony.
            if (defenderTeam != null) {
                defenderTeam.getMembers().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null && p.isAlive()) {
                        // Check rank in the defender colony (using colony.getPermissions())
                        if (colony.getPermissions().getRank(uuid) != null &&
                                colony.getPermissions().getRank(uuid).isColonyManager()) {
                            data.getDefenderLives().put(uuid, 5);
                        }
                        // Allies are NOT auto-added.
                    }
                });
            } else {
                data.getDefenderLives().put(colony.getPermissions().getOwner(), 5);
            }
        } else {
            // Fallback if FTBTeams is not installed.
            data.getAttackerLives().put(attacker.getUUID(), 5);
            data.getDefenderLives().put(colony.getPermissions().getOwner(), 5);
        }

        data.initialAttackerTotalLives = data.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
        data.initialDefenderTotalLives = data.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
        ACTIVE_WARS.put(colony.getID(), data);
    }





    public static void updateBossBar(WarData war) {
        long now = System.currentTimeMillis();
        if (now < war.getJoinPhaseEndTime()) {
            // JOIN PHASE: Calculate remaining join-phase time.
            long remainingMillis = war.getJoinPhaseEndTime() - now;
            String timeStr = String.format("%02d:%02d", remainingMillis / 60000, (remainingMillis / 1000) % 60);
            // Build the join-phase message as requested.
            String joinText = war.getColony().getName() + " is Under Siege! Joining Phase initiated. Time left: " + timeStr;
            // Update the main boss bar (for primary participants) with yellow style.
            war.bossEvent.setName(Component.literal(joinText));
            // Optionally, force the boss bar color to yellow by recreating it if needed.
            long joinDuration = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
            war.bossEvent.setProgress((float) remainingMillis / joinDuration);

            // Update the allies boss bar similarly.
            if (war.alliesBossEvent != null) {
                war.alliesBossEvent.setName(Component.literal(joinText));
                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
            }
        } else {
            // WAR PHASE: Now the join phase is over, so display the active war timer.
            long elapsedSeconds = (now - war.warStartTime) / 1000;
            long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
            long remainingSeconds = Math.max(0, warDurationSeconds - elapsedSeconds);
            String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            String warText = "§6§lWar for " + war.getColony().getName() + " - Time Remaining: " + timeStr;
            war.bossEvent.setName(Component.literal(warText));
            war.bossEvent.setProgress((float) remainingSeconds / warDurationSeconds);
            // Remove allies boss bar (to avoid overlapping)
            if (war.alliesBossEvent != null) {
                war.alliesBossEvent.removeAllPlayers();
                war.alliesBossEvent.setVisible(false);
            }
        }
    }






    // Finalizes the join phase after 5 minutes. Checks that the ratio is acceptable.
    public static void finalizeWarStart(WarData war) {
        int attackerPlayerCount = war.getAttackerLives().size();
        int defenderPlayerCount = war.getDefenderLives().size();

        // If either side has no participants, cancel the war.
        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
            war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("War cancelled due to lack of participants.")
                            .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)),
                    false
            );
            endWar(war.getColony());
            return;
        }


        // Ensure the difference between team counts is at most 1.
        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
            war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
                                    + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
                            .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)),
                    false);
            return; // Do not start the war if ratio condition is not met.
        }

        long joinDurationMillis = TimeUnit.MINUTES.toMillis(TaxConfig.JOIN_PHASE_DURATION_MINUTES.get());
        // (Optionally, update joinPhaseEndTime if needed)

        // First clear any players from the boss bar
        war.bossEvent.removeAllPlayers();

        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                war.bossEvent.addPlayer(p);
            }
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                war.bossEvent.addPlayer(p);
            }
        });

        // Remove the allies boss bar to avoid overlapping.
        if (war.alliesBossEvent != null) {
            war.alliesBossEvent.removeAllPlayers();
            war.alliesBossEvent.setVisible(false);
        }


        // Assign war groups to all participants.
        // Change each participating player's permission group.
        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) assignWarGroup(p);
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) assignWarGroup(p);
        });

        war.warStartTime = System.currentTimeMillis();
        war.setStatus(WarData.WarStatus.INWAR);

        updateBossBar(war);
        applyGuardGlow(war.getColony());
        applyWarGlowToParticipants(war);

        war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("War has officially begun!")
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)), false);

        long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
        scheduleTimerWarnings(war, warDurationMillis);
    }


    private static void assignWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        // Execute the command: lp user <playerName> parent set war
        String command = "lp user " + player.getName().getString() + " parent set war";
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    public static void resetWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        // Execute the command: lp user <playerName> parent set default
        String command = "lp user " + player.getName().getString() + " parent set default";
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }


    public static void checkForVictory(WarData war) {
        int attackerTotalLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
        int defenderTotalLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();

        // For total victory, the losing side must have zero players AND zero guards on that same side.
        if (attackerTotalLives == 0 && war.remainingAttackerGuards == 0) {
            // Defender side achieves total victory
            broadcast("Defender team achieved TOTAL VICTORY!", ChatFormatting.DARK_GREEN);
            transferOwnership(war.getColony(), war.getDefender());
            endWar(war.getColony());
        }
        else if (defenderTotalLives == 0 && war.remainingDefenderGuards == 0) {
            // Attacker side achieves total victory
            broadcast("Attacker team achieved TOTAL VICTORY!", ChatFormatting.DARK_GREEN);
            transferOwnership(war.getColony(), war.getAttacker());
            endWar(war.getColony());
        }
    }


    public static void handleStalemate(WarData war) {
        applyStalematePenalties(war);
        war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("Stalemate reached. Economic penalties applied."), false);
        // End the war and remove the boss bar.
        endWar(war.getColony());
    }

    public static void processPeaceVotes(WarData war) {
        // For simplicity, if a peace proposal exists and expires, we assume a vote failure (or success) here.
        // TODO: Implement proper vote tallying.
        war.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("Peace proposal expired. No consensus reached (stub)."),
                false
        );
        // Alternatively, if consensus is reached, you could end the war peacefully.
    }

    private static void applyStalematePenalties(WarData war) {
        war.getAttackerLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
        war.getDefenderLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
        TaxManager.freezeColonyTax(war.getColony().getID(), 2);
    }

    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null) return;
        // Use the colony permissions to set the new owner.
        if (colony.getPermissions().setOwner(newOwner)) {
            colony.markDirty(); // Save changes
            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));

            colony.getWorld().getServer().getPlayerList().broadcastSystemMessage(msg, false);
        } else {
            LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
        }
    }

    public static void endWar(IColony colony) {
        WarData warData = ACTIVE_WARS.remove(colony.getID());
        if (warData != null) {
            // Cancel join-phase or war timer tasks if present
            if (warData.timerTask != null) {
                warData.timerTask.cancel();
                warData.timerTask = null;
            }

            // Remove the boss bar
            warData.bossEvent.removeAllPlayers();
            warData.bossEvent.setVisible(false);
            if (warData.timerTask != null) {
                warData.timerTask.cancel();
            }

            // Remove glow from participants
            colony.getPermissions().getPlayers().forEach((uuid, pdata) -> {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                }
            });

            // Remove glow from all citizen guards
            colony.getCitizenManager().getCitizens().forEach(citizen -> {
                citizen.getEntity().ifPresent(entity -> {
                    entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                });
            });

            // Reset war groups for all participants
            warData.getAttackerLives().keySet().forEach(uuid -> {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) resetWarGroup(p);
            });
            warData.getDefenderLives().keySet().forEach(uuid -> {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) resetWarGroup(p);
            });

            // Restore inventory + bring back from spectator
            colony.getPermissions().getPlayers().forEach((uuid, pdata) -> {
                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null && p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    // Restore items
                    WarInventoryHandler.restoreInventory(p);

                    // Check if they're underground
                    BlockPos pos = p.blockPosition();
                    // Use motion-blocking, ignoring leaves
                    int topY = p.level().getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            pos.getX(), pos.getZ()
                    );
                    // Only teleport them up if they are actually below the top
                    if (pos.getY() < topY) {
                        p.teleportTo(
                                pos.getX() + 0.5,
                                topY + 1,
                                pos.getZ() + 0.5
                        );
                    }
                    warData.bossEvent = null;
                    p.setGameMode(GameType.SURVIVAL);
                    p.sendSystemMessage(
                            Component.literal("The war has ended. Your inventory has been restored, and you are now in Survival!")
                                    .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true))
                    );

                }
            });

            LOGGER.info("War ended for colony {}", colony.getName());
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
        int attackerTotal = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
        int defenderTotal = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();

        // Outcome 1: Total Victory – if one side is completely annihilated.
        if (attackerTotal == 0 && war.remainingAttackerGuards == 0) {
            broadcast("Time expired! Defender team achieved TOTAL VICTORY!", ChatFormatting.DARK_GREEN);
            transferOwnership(war.getColony(), war.getDefender());
            endWar(war.getColony());
            return;
        } else if (defenderTotal == 0 && war.remainingDefenderGuards == 0) {
            broadcast("Time expired! Attacker team achieved TOTAL VICTORY!", ChatFormatting.DARK_GREEN);
            transferOwnership(war.getColony(), war.getAttacker());
            endWar(war.getColony());
            return;
        }

        // Outcome 2: Stalemate – if both sides are at full strength.
        if (attackerTotal == war.getAttackerLives().size() * 5 &&
                defenderTotal == war.getDefenderLives().size() * 5 &&
                war.remainingAttackerGuards == war.initialAttackerGuards &&
                war.remainingDefenderGuards == war.initialDefenderGuards) {

            String report = "Stalemate: Both sides lose 25% of their balances and colony revenue is reduced by 25%.";
            broadcast(report, ChatFormatting.GOLD);
            war.getAttackerLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
            war.getDefenderLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
            TaxManager.deductColonyTax(war.getColony(), 0.25);
            war.setPenaltyReport(report);
            endWar(war.getColony());
            return;
        }

        // Outcome 3: Strategic Victory – compare normalized strengths.
        // Normalized strength = (current lives + remaining guards) / (initial lives + initial guards)
        double attackerNormalized = (double) (attackerTotal + war.remainingAttackerGuards)
                / (war.initialAttackerTotalLives + war.initialAttackerGuards);
        double defenderNormalized = (double) (defenderTotal + war.remainingDefenderGuards)
                / (war.initialDefenderTotalLives + war.initialDefenderGuards);
        double epsilon = 0.01; // tolerance threshold

        String report;
        if (attackerNormalized + epsilon < defenderNormalized) {
            report = "Strategic Victory: Attacker side lost proportionally more strength! Defender wins.";
            WarEconomyHandler.transferTeamBalanceToSinglePlayer(
                    war.getAttackerTeamID(),
                    war.getDefender(),
                    0.25
            );
            TaxManager.deductColonyTax(war.getColony(), 0.25);
            broadcast(report, ChatFormatting.RED);
        } else if (defenderNormalized + epsilon < attackerNormalized) {
            report = "Strategic Victory: Defender side lost proportionally more strength! Attacker wins.";
            WarEconomyHandler.transferTeamBalanceToSinglePlayer(
                    war.getDefenderTeamID(),
                    war.getAttacker(),
                    0.25
            );
            TaxManager.deductColonyTax(war.getColony(), 0.25);
            broadcast(report, ChatFormatting.RED);
        } else {
            report = "Stalemate: Both sides have lost equal proportions of strength. Both sides incur penalties.";
            war.getAttackerLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
            war.getDefenderLives().keySet().forEach(uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, 0.25));
            TaxManager.deductColonyTax(war.getColony(), 0.25);
            broadcast(report, ChatFormatting.GOLD);
        }
        war.setPenaltyReport(report);
        endWar(war.getColony());
    }



    public static Map<UUID, Integer> getLivesForPlayer(WarData war, ServerPlayer player) {
        if (FTB_TEAMS_INSTALLED) {
            var teamOpt = FTB_TEAM_MANAGER.getPlayerTeamForPlayerID(player.getUUID());
            if (teamOpt.isEmpty()) return new HashMap<>();
            var team = teamOpt.get();
            if (team.getId().equals(war.getAttackerTeamID())) {
                return war.getAttackerLives();
            } else if (team.getId().equals(war.getDefenderTeamID())) {
                return war.getDefenderLives();
            }
            return new HashMap<>();
        } else {
            // Fallback: if player's UUID is already in one of the maps, return that map.
            if (war.getAttackerLives().containsKey(player.getUUID())) {
                return war.getAttackerLives();
            } else if (war.getDefenderLives().containsKey(player.getUUID())) {
                return war.getDefenderLives();
            }
            return new HashMap<>();
        }
    }

    public static WarData getActiveWarForPlayer(ServerPlayer player) {
        if (FTB_TEAMS_INSTALLED) {
            UUID teamId;
            Optional<Team> teamOpt = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID());
            teamId = teamOpt.map(team -> team.getId()).orElse(player.getUUID());
            for (WarData war : ACTIVE_WARS.values()) {
                // Check if player's team (or solo UUID) is directly involved
                if (teamId.equals(war.getAttackerTeamID()) || teamId.equals(war.getDefenderTeamID())) {
                    return war;
                }
                // **Also check if the player is an ally of either side’s team**
                Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
                if (atkTeam != null && atkTeam.isPartyTeam() && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) {
                    return war;
                }
                Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
                if (defTeam != null && defTeam.isPartyTeam() && ((PartyTeam) defTeam).getMembers().contains(player.getUUID())) {
                    return war;
                }
            }
        } else {
            // Fallback: check if player’s UUID is in any active war participant lists
            for (WarData war : ACTIVE_WARS.values()) {
                if (war.getAttackerLives().containsKey(player.getUUID()) || war.getDefenderLives().containsKey(player.getUUID())) {
                    return war;
                }
            }
        }
        return null;
    }


    public static void scheduleTimerWarnings(WarData war, long warDurationMillis) {
        Timer warningTimer = new Timer();
        // We want warnings at 3/4, 1/2, and 1/4 of the total war duration.
        long quarter = warDurationMillis / 4;
        for (int i = 1; i <= 3; i++) {
            long delay = quarter * i;
            warningTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Calculate remaining time
                    long remainingMillis = warDurationMillis - delay;
                    long remainingSeconds = remainingMillis / 1000;
                    String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
                    Component warning = Component.literal("Warning: War ending in " + timeStr + "!")
                            .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
                    // Send warning to all participants (attacker and defender sides)
                    war.getAttackerLives().keySet().forEach(uuid -> {
                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) {
                            p.sendSystemMessage(warning);
                        }
                    });
                    war.getDefenderLives().keySet().forEach(uuid -> {
                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) {
                            p.sendSystemMessage(warning);
                        }
                    });
                }
            }, delay);
        }
    }



    private static void applyWarGlowToParticipants(WarData war) {
        // Apply glow to all attacker players.
        war.getAttackerLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
            }
        });
        // Apply glow to all defender players.
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
            }
        });
    }

    public static void applyGuardGlow(IColony colony) {
        colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .forEach(citizen -> citizen.getEntity().ifPresent(entity -> {
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
                }));
    }
    //TODO: ADD Glow to Players

    private static void broadcast(String message, ChatFormatting color) {
        Component msg = Component.literal(message)
                .withStyle(Style.EMPTY.withColor(color).withBold(true));
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

}