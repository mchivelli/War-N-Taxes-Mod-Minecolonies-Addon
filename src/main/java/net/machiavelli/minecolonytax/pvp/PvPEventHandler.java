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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class PvPEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final PvPManager pvpManager = PvPManager.INSTANCE;
    private static final PvPMapManager mapManager = new PvPMapManager();
    private static final PvPBattleManager battleManager = new PvPBattleManager();
    private static final Map<UUID, Long> lastCommandBlockMessage = new HashMap<>();
    private static final long COMMAND_BLOCK_MESSAGE_COOLDOWN = 5000; // 5 seconds

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        mapManager.loadArenaData();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // PvP Arena Commands
        new PvPArenaCommand().register(event.getDispatcher(), battleManager, mapManager);
        
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
} 