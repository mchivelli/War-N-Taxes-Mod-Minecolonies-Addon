package net.machiavelli.minecolonytax.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.network.chat.*;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.damagesource.DamageSource;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import com.mojang.brigadier.ParseResults;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.ParseResults;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;




import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Mod.EventBusSubscriber
public class PvPArenaCommand {
    private static final Logger LOGGER = LogManager.getLogger();
    private static GlobalPos arenaPos1;
    private static GlobalPos arenaPos2;
    private static final Map<UUID, DuelRequest> pendingRequests = new HashMap<>();
    private static final Map<String, ActiveDuel> activeDuels = new HashMap<>();
    private static final Map<UUID, ItemStack[]> playerInventories = new HashMap<>();
    private static final Map<UUID, ItemStack[]> playerArmor = new HashMap<>();
    private static final File ARENA_DATA_FILE = new File("config/pvp_arena_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, SpectatorData> spectatorData = new HashMap<>();
    private static final Map<String, List<UUID>> activeSpectators = new HashMap<>();
    private static final Map<String, Integer> duelTimers = new ConcurrentHashMap<>();
    private static final Map<String, Map<UUID, Float>> duelDamage = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastNotificationTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> challengeCooldown = new HashMap<>();
    // Define a cooldown duration (e.g., 10 seconds)
    private static final long CHALLENGE_COOLDOWN_MS = 5_000;



    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("pvparena")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("p1").executes(context -> setArenaPosition(context, 1)))
                .then(Commands.literal("p2").executes(context -> setArenaPosition(context, 2))));

        dispatcher.register(Commands.literal("pvp")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> handleChallenge(context, true)))
                        .executes(context -> handleChallenge(context, false)))
                .then(Commands.literal("accept").executes(PvPArenaCommand::handleAccept))
                .then(Commands.literal("decline").executes(PvPArenaCommand::handleDecline)));
        dispatcher.register(Commands.literal("pvp")
                // ... existing commands
                .then(Commands.literal("spectate")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(PvPArenaCommand::handleSpectateStart))
                        .then(Commands.literal("stop")
                                .executes(PvPArenaCommand::handleSpectateStop))
                ));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Iterator<Map.Entry<String, Integer>> iterator = duelTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Integer> entry = iterator.next();
                String duelKey = entry.getKey();
                int remaining = entry.getValue() - 1;

                if (remaining <= 0) {
                    handleTimerExpiry(duelKey);
                    iterator.remove();
                } else {
                    duelTimers.put(duelKey, remaining);
                    sendTimerNotifications(duelKey, remaining);
                }
            }
        }
    }




    public static boolean isPlayerInArena(ServerPlayer player) {
        if (arenaPos1 == null || arenaPos2 == null) {
            return false; // Arena not defined yet
        }
        // Check if player is in the same dimension as the arena
        if (!player.level().dimension().equals(arenaPos1.dimension())) {
            return false;
        }
        var pos = player.blockPosition();
        int minX = Math.min(arenaPos1.pos().getX(), arenaPos2.pos().getX());
        int maxX = Math.max(arenaPos1.pos().getX(), arenaPos2.pos().getX());
        int minY = Math.min(arenaPos1.pos().getY(), arenaPos2.pos().getY());
        int maxY = Math.max(arenaPos1.pos().getY(), arenaPos2.pos().getY());
        int minZ = Math.min(arenaPos1.pos().getZ(), arenaPos2.pos().getZ());
        int maxZ = Math.max(arenaPos1.pos().getZ(), arenaPos2.pos().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getY() >= minY && pos.getY() <= maxY &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    @SubscribeEvent
    public static void onCommandExecution(CommandEvent event) {
        ParseResults<CommandSourceStack> parseResults = event.getParseResults();
        CommandSourceStack source = parseResults.getContext().getSource();

        // Only process if the source is a player.
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check the config option. If commands are not allowed for dueling players…
        if (!TaxConfig.ALLOW_PVP_ARENA_COMMANDS.get()) {
            // Block commands only for players who are actively engaged in a duel.
            if (getActiveDuel(player) != null) {
                event.setCanceled(true);
                player.sendSystemMessage(
                        Component.literal("You cannot execute commands while dueling in the PvP arena!")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    private static void handleTimerExpiry(String duelKey) {
        ActiveDuel duel = activeDuels.get(duelKey);
        if (duel == null) return;

        ServerPlayer p1 = getPlayerByUUID(duel.player1());
        ServerPlayer p2 = getPlayerByUUID(duel.player2());
        if (p1 == null || p2 == null) return;

        Map<UUID, Float> damageMap = duelDamage.getOrDefault(duelKey, new HashMap<>());
        float damage1 = damageMap.getOrDefault(p1.getUUID(), 0.0f);
        float damage2 = damageMap.getOrDefault(p2.getUUID(), 0.0f);

        ServerPlayer winner = null;
        ServerPlayer loser = null;
        float winnerDamage = 0.0f;
        float loserDamage = 0.0f;

        if (damage1 > damage2) {
            winner = p1;
            loser = p2;
            winnerDamage = damage1;
            loserDamage = damage2;
        } else if (damage2 > damage1) {
            winner = p2;
            loser = p1;
            winnerDamage = damage2;
            loserDamage = damage1;
        }

        if (winner != null) {
            // Broadcast detailed results
            MutableComponent announcement = Component.literal("")
                    .append(Component.literal("✧ Time Expired! Duel Result ✧")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append("\n")
                    .append(Component.literal("Winner: ")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(winner.getName().getString())
                            .withStyle(ChatFormatting.AQUA))
                    .append("\n")
                    .append(Component.literal("Damage Dealt: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1f", winnerDamage))
                            .withStyle(ChatFormatting.RED))
                    .append("\n")
                    .append(Component.literal("Damage Received: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1f", loserDamage))
                            .withStyle(ChatFormatting.RED));

            winner.getServer().getPlayerList().broadcastSystemMessage(announcement, false);
            handleDuelConclusion(loser, duel, winner);
        } else {
            // Handle draw
            p1.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("Duel ended in a draw! Both dealt " + damage1 + " damage")
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
            endDuel(duelKey);
        }
    }

    private static void sendTimerNotifications(String duelKey, int remainingTicks) {
        ActiveDuel duel = activeDuels.get(duelKey);
        if (duel == null) return;

        ServerPlayer p1 = getPlayerByUUID(duel.player1());
        ServerPlayer p2 = getPlayerByUUID(duel.player2());
        if (p1 == null || p2 == null) return;

        int secondsRemaining = remainingTicks / 20;
        int lastNotified = lastNotificationTime.getOrDefault(duelKey, -1);

        // Only notify when second changes and at specific intervals
        if (secondsRemaining != lastNotified &&
                (secondsRemaining % 60 == 0 || secondsRemaining <= 10)) {

            Component message;
            if (secondsRemaining > 60) {
                int minutes = secondsRemaining / 60;
                message = Component.literal("Duel Time remaining: " + minutes + " minute" + (minutes > 1 ? "s" : ""))
                        .withStyle(ChatFormatting.YELLOW);
            } else if (secondsRemaining > 10) {
                message = Component.literal("Duel Time remaining: " + secondsRemaining + " seconds")
                        .withStyle(ChatFormatting.YELLOW);
            } else {
                message = Component.literal("Duel Time remaining: " + secondsRemaining + "!")
                        .withStyle(ChatFormatting.GOLD);
            }

            p1.sendSystemMessage(message);
            p2.sendSystemMessage(message);
            lastNotificationTime.put(duelKey, secondsRemaining);
        }
    }


    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim &&
                event.getSource().getEntity() instanceof ServerPlayer attacker) {

            ActiveDuel duel = getActiveDuel(victim);
            if (duel == null) return;

            String duelKey = duel.player1().toString() + duel.player2().toString();
            if (duelDamage.containsKey(duelKey)) {
                duelDamage.get(duelKey).merge(
                        attacker.getUUID(),
                        event.getAmount(),
                        Float::sum
                );
            }
        }
    }





    private static int handleDecline(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DuelRequest request = pendingRequests.remove(player.getUUID());

            if (request == null) {
                context.getSource().sendFailure(Component.literal("No active duel requests!"));
                return 0;
            }

            ServerPlayer challenger = context.getSource().getServer().getPlayerList()
                    .getPlayer(request.challengerId());

            if (challenger != null) {
                challenger.sendSystemMessage(
                        Component.literal(player.getName().getString() + " declined your duel!")
                );
            }

            return 1;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Error handling decline", e);
            return 0;
        }
    }

    private static void startDuel(ServerPlayer p1, ServerPlayer p2, int amount) {
        p1.setGameMode(GameType.ADVENTURE);
        p2.setGameMode(GameType.ADVENTURE);
        ActiveDuel duel = new ActiveDuel(
                p1.getUUID(),
                p2.getUUID(),
                GlobalPos.of(p1.level().dimension(), p1.blockPosition()),
                GlobalPos.of(p2.level().dimension(), p2.blockPosition()),
                amount
        );

        String duelKey = p1.getUUID().toString() + p2.getUUID();
        activeDuels.put(duelKey, duel);
        duelTimers.put(duelKey, 6000); // 5 minutes ticks
        duelDamage.put(duelKey, new ConcurrentHashMap<>());

        teleportToArena(p1, arenaPos1);
        teleportToArena(p2, arenaPos2);
        applyFreezeEffects(p1);
        applyFreezeEffects(p2);
        startCountdown(p1, p2);

        // New clickable broadcast for spectators:
        MutableComponent spectateAnnouncement = Component.literal("A duel is starting between ")
                .append(Component.literal(p1.getName().getString()).withStyle(ChatFormatting.AQUA))
                .append(" and ")
                .append(Component.literal(p2.getName().getString()).withStyle(ChatFormatting.AQUA))
                .append(". Click ")
                .append(Component.literal("[SPECTATE]")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp spectate " + p1.getName().getString()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to spectate this duel"))))
                        .append(" to watch the duel!"));

        // Broadcast to all players:
        p1.getServer().getPlayerList().broadcastSystemMessage(spectateAnnouncement, false);
    }


    private static void teleportToArena(ServerPlayer player, GlobalPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null) return;

        BlockPos blockPos = pos.pos();
        player.teleportTo(
                level,
                blockPos.getX() + 0.5,
                blockPos.getY(),
                blockPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot()
        );
    }
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {


        if (event.getEntity() instanceof ServerPlayer player) {
            ActiveDuel duel = getActiveDuel(player);
            if (duel == null) {
                LOGGER.warn("Player {} died but wasn't in an active duel. Skipping duel death handling.", player.getName().getString());
                return;
            }

            String duelKey = duel.player1().toString() + duel.player2().toString();
            List<UUID> spectators = activeSpectators.remove(duelKey);
            Map<UUID, Float> damageMap = duelDamage.get(duelKey);

            if (damageMap == null) {
                damageMap = new HashMap<>();
            }

            float damage1 = damageMap.getOrDefault(duel.player1(), 0.0f);
            float damage2 = damageMap.getOrDefault(duel.player2(), 0.0f);

            saveAndClearInventory(player);



            // Format damage values
            String damageString = String.format("%s: %.1f | %s: %.1f",
                    getPlayerByUUID(duel.player1()).getName().getString(),
                    damage1,
                    getPlayerByUUID(duel.player2()).getName().getString(),
                    damage2
            );


            if(spectators != null) {
                spectators.forEach(uuid -> {
                    ServerPlayer spectator = player.getServer().getPlayerList().getPlayer(uuid);
                    if(spectator != null) {
                        stopSpectating(spectator, false);
                        spectator.sendSystemMessage(
                                Component.literal("Duel ended! Returning to your original position")
                                        .withStyle(ChatFormatting.YELLOW)
                        );
                    }
                });
            }


            ServerPlayer winner = getOpponent(player, duel);
            if (winner == null) return;

            // Create formatted announcement
            MutableComponent announcement = Component.literal("")
                    .append(Component.literal("✧ Duel Result ✧")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append("\nDamage: ")
                    .append(Component.literal(damageString)
                            .withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.literal("Winner: ")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(winner.getName().getString())
                            .withStyle(ChatFormatting.AQUA))
                    .append("\n")
                    .append(Component.literal("Loser: ")
                            .withStyle(ChatFormatting.RED))
                    .append(Component.literal(player.getName().getString())
                            .withStyle(ChatFormatting.AQUA));

            if (duel.amount() > 0) {
                announcement.append("\n")
                        .append(Component.literal("Wager: ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(duel.amount() + " coins")
                                .withStyle(ChatFormatting.GOLD));
            }

            player.getServer().getPlayerList().broadcastSystemMessage(announcement, false);

            // Spectator notification for loser
            MutableComponent spectatorMessage = Component.literal("")
                    .append(Component.literal("You are now in Spectator mode!\n")
                            .withStyle(ChatFormatting.RED))
                    .append(Component.literal("You'll be returned in "))
                    .append(Component.literal("5 seconds")
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" with your items restored."));

            player.sendSystemMessage(spectatorMessage);

            handleDuelConclusion(player, duel, winner);
        }
    }

    private static void handleDuelConclusion(ServerPlayer loser, ActiveDuel duel, ServerPlayer winner) {

        if (duel == null) {
            LOGGER.error("Duel was null during conclusion.");
            return;
        }

        if (winner == null) {
            LOGGER.warn("Opponent for {} not found. Duel ended without a winner.", loser.getName().getString());
            endDuel(duel.player1().toString() + duel.player2().toString());
            return;
        }


        // Store both players restor data
        GlobalPos loserPos = loser.getUUID().equals(duel.player1()) ?
                duel.originalPos1() : duel.originalPos2();
        GlobalPos winnerPos = winner.getUUID().equals(duel.player1()) ?
                duel.originalPos1() : duel.originalPos2();

        // Spectator transition for loser
        loser.setHealth(loser.getMaxHealth());
        loser.getFoodData().setFoodLevel(20);
        loser.removeAllEffects();
        loser.setGameMode(GameType.SPECTATOR);

        // Process payment
        if (duel.amount() > 0) {
            if (TaxConfig.isSDMShopConversionEnabled()) {
                String command = "sdmshop pay " + winner.getName().getString() + " " + duel.amount();
                loser.getServer().getCommands().performPrefixedCommand(
                        loser.createCommandSourceStack().withSuppressedOutput(),
                        command
                );
            } else {
                LOGGER.info("SDMShop Feature Disabled, Deduction only From Colony.");
                loser.sendSystemMessage(Component.literal("Duel reparations skipped (SDMShop conversion disabled).")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }


        // Schedule restoration for both players
        scheduleRestoration(loser, loserPos);
        scheduleRestoration(winner, winnerPos);

        // Cleanup
        activeDuels.remove(duel.player1().toString() + duel.player2().toString());
    }

    private static void scheduleRestoration(ServerPlayer player, GlobalPos originalPos) {
        player.getServer().execute(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    player.getServer().execute(() -> {
                        if (player.isRemoved() || player.getServer().getPlayerList().getPlayer(player.getUUID()) == null) {
                            LOGGER.warn("Player {} disconnected before restoration.", player.getName().getString());
                            return;
                        }

                        // Restore player state
                        teleportToArena(player, originalPos);
                        restoreInventory(player);
                        player.setGameMode(GameType.SURVIVAL);

                        // Force client update
                        player.connection.send(new ClientboundGameEventPacket(
                                ClientboundGameEventPacket.CHANGE_GAME_MODE,
                                (float) GameType.SURVIVAL.getId()
                        ));
                    });
                } catch (InterruptedException e) {
                    LOGGER.error("Restoration interrupted", e);
                }
            }).start();
        });
    }

    private static int setArenaPosition(CommandContext<CommandSourceStack> context, int position) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            GlobalPos pos = GlobalPos.of(player.level().dimension(), player.blockPosition());
            if(position == 1) {
                arenaPos1 = pos;
                context.getSource().sendSuccess(() ->
                        Component.literal("Set Arena Position 1"), false);
            } else {
                arenaPos2 = pos;
                context.getSource().sendSuccess(() ->
                        Component.literal("Set Arena Position 2"), false);
            }
            saveArenaPositions();
            return 1;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Error setting position", e);
            return 0;
        }
    }

    // Modify the handleChallenge method
    private static int handleChallenge(CommandContext<CommandSourceStack> context, boolean hasAmount) {

        try {


            ServerPlayer source = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "target");
            UUID challengerUUID = source.getUUID();
            int amount = hasAmount ? IntegerArgumentType.getInteger(context, "amount") : 0;




            if(source.getUUID().equals(target.getUUID())) {
                context.getSource().sendFailure(Component.literal("You can't challenge yourself!")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            // Check if the challenger is on cooldown
            Long lastChallenge = challengeCooldown.get(challengerUUID);
            if (lastChallenge != null && System.currentTimeMillis() - lastChallenge < CHALLENGE_COOLDOWN_MS) {
                context.getSource().sendFailure(Component.literal("You must wait before issuing another duel challenge."));
                return 0;
            }

            // Check if target already has a pending duel request
            if (pendingRequests.containsKey(target.getUUID())) {
                context.getSource().sendFailure(Component.literal("A duel request is already pending for this player. Please wait."));
                return 0;
            }

            pendingRequests.put(target.getUUID(), new DuelRequest(source.getUUID(), amount));
            challengeCooldown.put(challengerUUID, System.currentTimeMillis());

            // Create clickable buttons using proper style construction
            MutableComponent acceptButton = Component.literal("[ACCEPT]")
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp accept"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to accept the duel"))));

            MutableComponent declineButton = Component.literal("[DECLINE]")
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp decline"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to decline the duel"))));

            MutableComponent challengeMessage = Component.literal("")
                    .append(Component.literal(source.getName().getString())
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" has challenged you to a duel! ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("Wager: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(amount + " coins")
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("\n"))
                    .append(acceptButton)
                    .append(" ")
                    .append(declineButton);

            target.sendSystemMessage(challengeMessage);

            source.sendSystemMessage(Component.literal("Challenge sent to ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(target.getName().getString())
                            .withStyle(ChatFormatting.AQUA)));

            return 1;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Challenge error", e);
            return 0;
        }
    }

    private static int handleAccept(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DuelRequest request = pendingRequests.remove(player.getUUID());

            if (request == null || arenaPos1 == null || arenaPos2 == null) {
                context.getSource().sendFailure(Component.literal("Invalid duel request"));
                return 0;
            }

            ServerPlayer challenger = context.getSource().getServer().getPlayerList()
                    .getPlayer(request.challengerId());
            if (challenger == null) {
                context.getSource().sendFailure(Component.literal("Challenger offline"));
                return 0;
            }

            startDuel(challenger, player, request.amount());
            return 1;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Accept error", e);
            return 0;
        }
    }

    private static void applyFreezeEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 250, false, false));
    }

    private static void removeFreezeEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
    }

    private static void saveAndClearInventory(ServerPlayer player) {
        // Save main inventory
        ItemStack[] mainInventory = new ItemStack[player.getInventory().getContainerSize()];
        for(int i = 0; i < mainInventory.length; i++) {
            mainInventory[i] = player.getInventory().getItem(i).copy();
        }
        playerInventories.put(player.getUUID(), mainInventory);

        // Save armor
        ItemStack[] armorInventory = new ItemStack[4];
        for(int i = 0; i < 4; i++) {
            armorInventory[i] = player.getInventory().armor.get(i).copy();
        }
        playerArmor.put(player.getUUID(), armorInventory);

        // Clear inventory
        player.getInventory().clearContent();
    }

    private static void restoreInventory(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Restore main inventory
        ItemStack[] mainInventory = playerInventories.remove(uuid);
        if (mainInventory != null) {
            for (int i = 0; i < mainInventory.length; i++) {
                player.getInventory().setItem(i, mainInventory[i]);
            }
        }

        // Restore armor
        ItemStack[] armorInventory = playerArmor.remove(uuid);
        if (armorInventory != null) {
            for (int i = 0; i < 4; i++) {
                player.getInventory().armor.set(i, armorInventory[i]);
            }
        }

        // Update client inventory
        player.containerMenu.broadcastChanges();
    }

    private static void startCountdown(ServerPlayer p1, ServerPlayer p2) {
        new Thread(() -> {
            try {
                for (int i = 5; i > 0; i--) {
                    int finalI = i;
                    p1.getServer().execute(() -> {
                        MutableComponent countdown = Component.literal("")
                                .append(Component.literal("Duel starts in: ")
                                        .withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.valueOf(finalI))
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                        p1.sendSystemMessage(countdown);
                        p2.sendSystemMessage(countdown);
                    });
                    Thread.sleep(1000);
                }

                p1.getServer().execute(() -> {
                    removeFreezeEffects(p1);
                    removeFreezeEffects(p2);
                    MutableComponent startMessage = Component.literal("")
                            .append(Component.literal("✧ Duel Start! ✧")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                            .append("\nFight between ")
                            .append(Component.literal(p1.getName().getString())
                                    .withStyle(ChatFormatting.AQUA))
                            .append(" and ")
                            .append(Component.literal(p2.getName().getString())
                                    .withStyle(ChatFormatting.AQUA));

                    p1.getServer().getPlayerList().broadcastSystemMessage(startMessage, false);
                });
            } catch (InterruptedException e) {
                LOGGER.error("Countdown error", e);
            }
        }).start();
    }

    private static void endDuel(String duelKey) {
        activeDuels.remove(duelKey);
        duelTimers.remove(duelKey);
        duelDamage.remove(duelKey);
        activeSpectators.remove(duelKey);
    }


    @Nullable
    private static ActiveDuel getActiveDuel(ServerPlayer player) {
        return activeDuels.values().stream()
                .filter(d -> d != null && d.player1() != null && d.player2() != null) // Prevent NullPointerException
                .filter(d -> d.player1().equals(player.getUUID()) || d.player2().equals(player.getUUID()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static ServerPlayer getOpponent(ServerPlayer player, ActiveDuel duel) {
        UUID opponentId = duel.player1().equals(player.getUUID()) ? duel.player2() : duel.player1();
        return player.getServer().getPlayerList().getPlayer(opponentId);
    }

    // Spectate command implementation
    private static int handleSpectateStart(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer spectator = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "player");

            ActiveDuel duel = getActiveDuel(target);
            if(duel == null) {
                context.getSource().sendFailure(Component.literal("Player is not in a duel!"));
                return 0;
            }

            // Store original position and game mode
            GlobalPos originalPos = GlobalPos.of(spectator.level().dimension(), spectator.blockPosition());
            spectatorData.put(spectator.getUUID(), new SpectatorData(
                    originalPos,
                    spectator.gameMode.getGameModeForPlayer()
            ));

            //  position between duelests
            BlockPos pos1 = arenaPos1.pos();
            BlockPos pos2 = arenaPos2.pos();
            BlockPos middlePos = new BlockPos(
                    (pos1.getX() + pos2.getX()) / 2,
                    (pos1.getY() + pos2.getY()) / 2 + 5,  // Add height for better view
                    (pos1.getZ() + pos2.getZ()) / 2
            );

            // Teleport to arena and set spectator mode
            spectator.setGameMode(GameType.SPECTATOR);
            teleportToArena(spectator, GlobalPos.of(arenaPos1.dimension(), middlePos));

            // Add to active spectators
            String duelKey = duel.player1().toString() + duel.player2().toString();
            activeSpectators.computeIfAbsent(duelKey, k -> new ArrayList<>()).add(spectator.getUUID());

            // Send confirmation message with stop button
            MutableComponent message = Component.literal("Now spectating duel between ")
                    .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.AQUA))
                    .append("\n")
                    .append(createStopButton());

            spectator.sendSystemMessage(message);

            return 1;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Spectate error", e);
            return 0;
        }
    }

    // Stop spectating implementation
    private static int handleSpectateStop(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer spectator = context.getSource().getPlayerOrException();
            stopSpectating(spectator, true);
            return 1;
        } catch (CommandSyntaxException e) {
            LOGGER.error("Stop spectate error", e);
            return 0;
        }
    }

    private static void stopSpectating(ServerPlayer spectator, boolean sendMessage) {
        SpectatorData data = spectatorData.remove(spectator.getUUID());
        if(data != null) {
            // Restore original state
            spectator.setGameMode(data.originalGameMode());
            teleportToArena(spectator, data.originalPos());

            // Remove from all spectator lists
            activeSpectators.values().forEach(list -> list.remove(spectator.getUUID()));

            if(sendMessage) {
                spectator.sendSystemMessage(Component.literal("Stopped spectating").withStyle(ChatFormatting.GREEN));
            }
        }
    }



    private static void saveArenaPositions() {
        try (FileWriter writer = new FileWriter(ARENA_DATA_FILE)) {
            ArenaData data = new ArenaData(arenaPos1, arenaPos2);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save arena positions", e);
        }
    }

    private static ServerPlayer getPlayerByUUID(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.error("Server is not running, cannot fetch player by UUID.");
            return null;
        }
        return server.getPlayerList().getPlayer(uuid);
    }

    public static void loadArenaPositions() {
        if (!ARENA_DATA_FILE.exists()) return;

        try (FileReader reader = new FileReader(ARENA_DATA_FILE)) {
            ArenaData data = GSON.fromJson(reader, ArenaData.class);
            arenaPos1 = data.getPos1();
            arenaPos2 = data.getPos2();
        } catch (IOException e) {
            LOGGER.error("Failed to load arena positions", e);
        }
    }

    private static CompoundTag serializeGlobalPos(GlobalPos pos) {
        if(pos == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", pos.dimension().location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(pos.pos()));
        return tag;
    }

    private static GlobalPos deserializeGlobalPos(CompoundTag tag) {
        if(tag == null) return null;
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                new ResourceLocation(tag.getString("dimension"))
        );
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("pos"));
        return GlobalPos.of(dimension, pos);
    }

    // Helper method for creating clickable button
    private static MutableComponent createStopButton() {
        return Component.literal("[Stop Spectating]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp spectate stop"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to stop spectating")
                        )));
    }

    private static class ArenaData {
        String pos1Dim;
        int pos1X, pos1Y, pos1Z;
        String pos2Dim;
        int pos2X, pos2Y, pos2Z;

        ArenaData(GlobalPos pos1, GlobalPos pos2) {
            if (pos1 != null) {
                this.pos1Dim = pos1.dimension().location().toString();
                this.pos1X = pos1.pos().getX();
                this.pos1Y = pos1.pos().getY();
                this.pos1Z = pos1.pos().getZ();
            }
            if (pos2 != null) {
                this.pos2Dim = pos2.dimension().location().toString();
                this.pos2X = pos2.pos().getX();
                this.pos2Y = pos2.pos().getY();
                this.pos2Z = pos2.pos().getZ();
            }
        }

        GlobalPos getPos1() {
            if (pos1Dim == null) return null;
            return GlobalPos.of(
                    ResourceKey.create(Registries.DIMENSION, new ResourceLocation(pos1Dim)),
                    new BlockPos(pos1X, pos1Y, pos1Z)
            );
        }

        GlobalPos getPos2() {
            if (pos2Dim == null) return null;
            return GlobalPos.of(
                    ResourceKey.create(Registries.DIMENSION, new ResourceLocation(pos2Dim)),
                    new BlockPos(pos2X, pos2Y, pos2Z)
            );
        }
    }

    record DuelRequest(UUID challengerId, int amount) {}
    record ActiveDuel(UUID player1, UUID player2, GlobalPos originalPos1,
                      GlobalPos originalPos2, int amount) {}}

record SpectatorData(GlobalPos originalPos, GameType originalGameMode) {}