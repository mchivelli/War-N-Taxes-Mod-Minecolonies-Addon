package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Commands for managing the War Chest system.
 *
 * Usage:
 * - /wnt warchest status [colonyId] - View war chest balance and stats
 * - /wnt warchest deposit <amount> [colonyId] - Deposit tax into war chest
 * - /wnt warchest withdraw <amount> [colonyId] - Withdraw from war chest to tax balance
 */
public class WarChestCommand {

    private static final SuggestionProvider<CommandSourceStack> COLONY_SUGGESTIONS = (ctx, builder) -> {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return builder.buildFuture();
        }
        List<String> suggestions = new ArrayList<>();
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        for (Level world : player.getServer().getAllLevels()) {
            for (IColony colony : colonyManager.getColonies(world)) {
                if (colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
                    suggestions.add(String.valueOf(colony.getID()));
                }
            }
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("wnt")
                .then(Commands.literal("warchest")
                        .then(Commands.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource(), -1))
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                        .suggests(COLONY_SUGGESTIONS)
                                        .executes(ctx -> executeStatus(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "colonyId")))))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeDeposit(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount"), -1))
                                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                                .suggests(COLONY_SUGGESTIONS)
                                                .executes(ctx -> executeDeposit(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        IntegerArgumentType.getInteger(ctx, "colonyId"))))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeWithdraw(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount"), -1))
                                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                                .suggests(COLONY_SUGGESTIONS)
                                                .executes(ctx -> executeWithdraw(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        IntegerArgumentType.getInteger(ctx, "colonyId")))))));

        dispatcher.register(command);
    }

    private static int executeStatus(CommandSourceStack source, int colonyId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        IColony colony = resolveColony(player, colonyId);
        if (colony == null) return 0;

        WarChestManager.sendStatus(player, colony.getID());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDeposit(CommandSourceStack source, int amount, int colonyId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        IColony colony = resolveColony(player, colonyId);
        if (colony == null) return 0;

        if (!hasColonyPermission(player, colony)) {
            player.sendSystemMessage(Component.literal("You must be a colony owner or officer to manage the war chest.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (WarChestManager.deposit(player, colony.getID(), amount)) {
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    private static int executeWithdraw(CommandSourceStack source, int amount, int colonyId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        IColony colony = resolveColony(player, colonyId);
        if (colony == null) return 0;

        if (!hasColonyPermission(player, colony)) {
            player.sendSystemMessage(Component.literal("You must be a colony owner or officer to manage the war chest.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (WarChestManager.withdraw(player, colony.getID(), amount)) {
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    /**
     * Resolve a colony from an explicit ID or by position/ownership fallback.
     * Resolution order when colonyId == -1:
     *  1. Position-based: colony the player is standing in
     *  2. Ownership fallback: first colony they own
     *  3. Error with helpful message
     */
    private static IColony resolveColony(ServerPlayer player, int colonyId) {
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();

        if (colonyId > 0) {
            IColony colony = colonyManager.getColonyByWorld(colonyId, player.level());
            if (colony == null) {
                player.sendSystemMessage(Component.literal("Colony #" + colonyId + " not found.")
                        .withStyle(ChatFormatting.RED));
                return null;
            }
            if (!hasColonyPermission(player, colony)) {
                player.sendSystemMessage(Component.literal("You are not a manager of colony #" + colonyId + ".")
                        .withStyle(ChatFormatting.RED));
                return null;
            }
            return colony;
        }

        // 1. Position-based: colony the player is standing in
        IColony colony = colonyManager.getColonyByPosFromWorld(player.level(), player.blockPosition());
        if (colony != null && hasColonyPermission(player, colony)) {
            return colony;
        }

        // 2. Ownership fallback: first colony they own
        colony = colonyManager.getIColonyByOwner(player.level(), player.getUUID());
        if (colony != null) {
            return colony;
        }

        player.sendSystemMessage(Component.literal(
                "Could not determine your colony. Stand inside a colony or specify a colony ID: /wnt warchest status <colonyId>")
                .withStyle(ChatFormatting.RED));
        return null;
    }

    private static boolean hasColonyPermission(ServerPlayer player, IColony colony) {
        return colony.getPermissions().getRank(player.getUUID()).isColonyManager();
    }
}
