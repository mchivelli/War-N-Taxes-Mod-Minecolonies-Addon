package net.machiavelli.minecolonytax.pvp;

import com.mojang.brigadier.ParseResults;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.pvp.model.ActiveBattle;
import net.machiavelli.minecolonytax.pvp.model.TeamBattle;
import net.machiavelli.minecolonytax.pvp.model.TeamBattleState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

@Mod.EventBusSubscriber
public class PvPEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final PvPManager pvpManager = PvPManager.INSTANCE;
    private static final PvPMapManager mapManager = new PvPMapManager();
    private static final PvPBattleManager battleManager = new PvPBattleManager();
    private static final Map<UUID, Long> lastCommandBlockMessage = new HashMap<>();
    private static final long COMMAND_BLOCK_MESSAGE_COOLDOWN = 5000; // 5 seconds
    
    // Track which abandoned colonies players have been notified about
    private static final Map<UUID, Set<Integer>> notifiedAbandonedColonies = new ConcurrentHashMap<>();
    private static final long COLONY_NOTIFICATION_COOLDOWN = 60000; // 1 minute cooldown
    private static final Map<String, Long> lastColonyNotifications = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        mapManager.loadArenaData();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // PvP Arena Commands
        new PvPArenaCommand().register(event.getDispatcher(), battleManager, mapManager);
        
        // Colony Activity Commands
        net.machiavelli.minecolonytax.commands.ColonyActivityCommand.register(event.getDispatcher());

        // Abandonment Check Command (WnT timer status)
        net.machiavelli.minecolonytax.commands.AbandonmentCheckCommand.register(event.getDispatcher());
        
        // CRITICAL FIX: Register all WNT and core commands
        // NOTE: WarCommands should NOT be registered separately - they should be part of WntCommands structure
        net.machiavelli.minecolonytax.commands.WntCommands.register(event.getDispatcher());
        // REMOVED: WarCommands registration to prevent duplicate standalone commands
        // net.machiavelli.minecolonytax.commands.WarCommands.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.EntityRaidCommands.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.ClaimTaxCommand.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.CheckTaxRevenueCommand.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.TaxDebtCommand.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.AdminTaxGenCommand.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.WarStatsCommand.register(event.getDispatcher());
        net.machiavelli.minecolonytax.commands.WarHistoryCommand.register(event.getDispatcher());
        
        // Only register GUI command on client side to prevent server crashes
        if (FMLEnvironment.dist.isClient()) {
            net.machiavelli.minecolonytax.commands.TaxGUICommand.register(event.getDispatcher());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            pvpManager.pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());

            List<TeamBattle> battlesToStart = new java.util.ArrayList<>();
            pvpManager.pendingTeamBattles.values().forEach(battle -> {
                if (battle.getState() == TeamBattleState.COUNTDOWN) {
                    long elapsedMillis = System.currentTimeMillis() - battle.countdownStartTime;
                    int countdownSeconds = TaxConfig.TEAM_BATTLE_START_COUNTDOWN_SECONDS.get();
                    int secondsRemaining = countdownSeconds - (int) (elapsedMillis / 1000);

                    int lastNotified = pvpManager.teamBattleCountdownNotifiers.getOrDefault(battle.getBattleId(), -1);

                    if (secondsRemaining != lastNotified && secondsRemaining >= 0) {
                        boolean shouldNotify = (secondsRemaining <= 5) || (secondsRemaining % 10 == 0);

                        if (shouldNotify) {
                            MutableComponent countdownMessage = Component.literal("Battle starts in: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(secondsRemaining)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                            if (secondsRemaining == 0) {
                                countdownMessage = Component.literal("Battle starting now!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                            }

                            battleManager.notifyTeamBattlePlayers(battle, countdownMessage);
                            LOGGER.info("Team battle {} starting in {} seconds...", battle.getBattleId(), secondsRemaining);
                        }
                        pvpManager.teamBattleCountdownNotifiers.put(battle.getBattleId(), secondsRemaining);
                    }

                    if (elapsedMillis >= countdownSeconds * 1000L) {
                        battlesToStart.add(battle);
                    }
                }
            });

            battlesToStart.forEach(battle -> {
                battleManager.startTeamBattle(battle);
                pvpManager.teamBattleCountdownNotifiers.remove(battle.getBattleId());
            });

            Iterator<Map.Entry<String, Integer>> iterator = pvpManager.battleTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Integer> entry = iterator.next();
                String battleId = entry.getKey();
                int remaining = entry.getValue() - 1;

                // Debug logging for timer processing
                if (remaining % 60 == 0) { // Log every minute
                    LOGGER.info("Battle {} timer: {} seconds remaining", battleId, remaining / 20);
                }

                if (remaining <= 0) {
                    LOGGER.info("Battle {} timer expired, ending battle", battleId);
                    battleManager.handleBattleTimerExpiry(battleId);
                    iterator.remove();
                } else {
                    pvpManager.battleTimers.put(battleId, remaining);
                    battleManager.sendBattleTimerNotifications(battleId, remaining);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCommandExecution(CommandEvent event) {
        ParseResults<CommandSourceStack> parseResults = event.getParseResults();
        CommandSourceStack source = parseResults.getContext().getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!TaxConfig.PVP_COMMANDS_IN_BATTLE_ENABLED.get()) {
            if (pvpManager.getActiveBattle(player) != null) {
                // Allow operators (permission level 2 or higher) to use commands
                if (source.hasPermission(2)) {
                    return; // Allow the command to proceed
                }
                
                // Check cooldown to prevent spam
                long currentTime = System.currentTimeMillis();
                Long lastMessageTime = lastCommandBlockMessage.get(player.getUUID());
                if (lastMessageTime == null || currentTime - lastMessageTime >= COMMAND_BLOCK_MESSAGE_COOLDOWN) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("You cannot execute commands while in a PvP battle!")
                            .withStyle(ChatFormatting.RED));
                    lastCommandBlockMessage.put(player.getUUID(), currentTime);
                } else {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            battleManager.handlePlayerDisconnect(player);
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Send any pending colony abandonment notifications
            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.sendPendingNotifications(player);
            
            // Clear any previous abandoned colony notifications for this player
            notifiedAbandonedColonies.remove(player.getUUID());
        }
    }
    
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            // Check if player entered an abandoned colony (every 20 ticks = 1 second)
            if (player.tickCount % 20 == 0) {
                checkForAbandonedColonyEntry(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ActiveBattle battle = pvpManager.getActiveBattle(player);
        if (battle == null) {
            return;
        }
        
        // Check for friendly fire in team battles
        if (TaxConfig.PVP_DISABLE_FRIENDLY_FIRE.get()) {
            // Get the battle ID and check if it's a team battle in the PvPManager
            String battleId = battle.getBattleId();
            TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);
            
            if (teamBattle != null && event.getSource().getEntity() instanceof ServerPlayer attacker) {
                // Check if both players are in the battle and on the same team
                if (teamBattle.arePlayersOnSameTeam(player.getUUID(), attacker.getUUID())) {
                    // Cancel friendly fire damage
                    event.setCanceled(true);
                    
                    // Notify the attacker once every 2 seconds to prevent spam
                    long currentTime = System.currentTimeMillis();
                    UUID attackerUUID = attacker.getUUID();
                    Long lastNotifyTime = pvpManager.lastFriendlyFireNotifications.getOrDefault(attackerUUID, 0L);
                    
                    if (currentTime - lastNotifyTime > 2000) { // 2 seconds cooldown
                        attacker.sendSystemMessage(Component.literal("Cannot damage teammates when friendly fire is disabled!")
                                .withStyle(ChatFormatting.RED));
                        pvpManager.lastFriendlyFireNotifications.put(attackerUUID, currentTime);
                    }
                    
                    return;
                }
            }
        }

        if (event.getAmount() >= player.getHealth()) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.clearFire();
            player.setGameMode(GameType.SPECTATOR);

            battleManager.handlePlayerDefeat(player, battle, event.getSource());
            return;
        }

        pvpManager.battleDamage.computeIfAbsent(battle.getBattleId(), k -> new ConcurrentHashMap<>())
                .merge(player.getUUID(), event.getAmount(), Float::sum);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ActiveBattle battle = pvpManager.getActiveBattle(player);
            if (battle != null) {
                battleManager.handlePlayerDefeat(player, battle, event.getSource());
            }
        }
    }
    
    /**
     * Check if a player has entered an abandoned colony and show them claimable status.
     */
    private static void checkForAbandonedColonyEntry(ServerPlayer player) {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            BlockPos playerPos = player.blockPosition();
            
            // Find any colony the player is currently in
            IColony nearbyColony = colonyManager.getColonyByPosFromWorld(player.level(), playerPos);
            if (nearbyColony == null) {
                return; // Player is not in any colony
            }
            
            // Check if this colony is abandoned
            if (!net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.isColonyAbandoned(nearbyColony)) {
                return; // Colony is not abandoned
            }
            
            UUID playerId = player.getUUID();
            int colonyId = nearbyColony.getID();
            
            // Check if we've already notified this player about this colony recently
            Set<Integer> playerNotifiedColonies = notifiedAbandonedColonies.computeIfAbsent(playerId, k -> new HashSet<>());
            String notificationKey = playerId + ":" + colonyId;
            long currentTime = System.currentTimeMillis();
            
            Long lastNotification = lastColonyNotifications.get(notificationKey);
            if (lastNotification != null && (currentTime - lastNotification) < COLONY_NOTIFICATION_COOLDOWN) {
                return; // Already notified recently
            }
            
            if (playerNotifiedColonies.contains(colonyId)) {
                return; // Already notified about this colony in this session
            }
            
            // Mark as notified
            playerNotifiedColonies.add(colonyId);
            lastColonyNotifications.put(notificationKey, currentTime);
            
            // Check claiming requirements
            net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult requirements = 
                    net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.checkClaimingRequirements(player);
            
            // Create notification message
            Component titleMessage = Component.literal("🏰 ABANDONED COLONY DETECTED 🏰")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            
            Component colonyInfo = Component.literal("Colony: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(nearbyColony.getName())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" (ID: " + nearbyColony.getID() + ")")
                            .withStyle(ChatFormatting.GRAY));
            
            Component citizenInfo = Component.literal("Citizens: " + nearbyColony.getCitizenManager().getCurrentCitizenCount() + 
                                                   ", Guards: " + net.machiavelli.minecolonytax.WarSystem.countGuards(nearbyColony))
                    .withStyle(ChatFormatting.WHITE);
            
            // Send notifications
            player.sendSystemMessage(titleMessage);
            player.sendSystemMessage(colonyInfo);
            player.sendSystemMessage(citizenInfo);
            
            if (requirements.canClaim) {
                player.sendSystemMessage(Component.literal("✓ You can claim this colony!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("Use '/wnt claimcolony " + nearbyColony.getName() + "' to start a claiming raid!")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.literal("✗ You cannot claim this colony: " + requirements.message)
                        .withStyle(ChatFormatting.RED));
                player.sendSystemMessage(Component.literal("Meet the requirements first to claim abandoned colonies.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            
        } catch (Exception e) {
            // Don't spam logs, just silently handle errors
        }
    }
} 