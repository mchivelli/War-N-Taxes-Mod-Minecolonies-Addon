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
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.integration.CurrencyService;
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
 * Commands for managing the Treasury system.
 *
 * /wnt treasury status [colonyId]
 *   Shows balance, drain rate, sustainability, and available deposit sources.
 *
 * /wnt treasury deposit <amount> [tax|wallet|inventory] [colonyId]
 *   Deposits funds into the treasury from the specified source:
 *     tax       — colony's accumulated tax balance (default)
 *     wallet    — player's SDMShop / SDMEconomy balance
 *     inventory — physical currency items from the player's inventory
 *
 * /wnt treasury withdraw <amount> [tax|wallet|inventory] [colonyId]
 *   Withdraws funds from the treasury to the specified destination:
 *     tax       — colony's accumulated tax balance (default)
 *     wallet    — player's SDMShop / SDMEconomy balance
 *     inventory — gives physical currency items to the player's inventory
 */
public class TreasuryCommand {

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
                .then(Commands.literal("treasury")
                        // status [colonyId]
                        .then(Commands.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource(), -1))
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                        .suggests(COLONY_SUGGESTIONS)
                                        .executes(ctx -> executeStatus(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "colonyId")))))
                        // deposit <amount> [tax|wallet|inventory] [colonyId]
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        // default (tax balance)
                                        .executes(ctx -> executeDeposit(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount"),
                                                CurrencyService.Source.TAX_BALANCE, -1))
                                        // explicit source literals
                                        .then(buildDepositSourceBranch("tax",       CurrencyService.Source.TAX_BALANCE))
                                        .then(buildDepositSourceBranch("wallet",    CurrencyService.Source.WALLET))
                                        .then(buildDepositSourceBranch("inventory", CurrencyService.Source.INVENTORY))
                                        // colonyId without source (tax default)
                                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                                .suggests(COLONY_SUGGESTIONS)
                                                .executes(ctx -> executeDeposit(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        CurrencyService.Source.TAX_BALANCE,
                                                        IntegerArgumentType.getInteger(ctx, "colonyId"))))))
                        // withdraw <amount> [tax|wallet|inventory] [colonyId]
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        // default (tax balance)
                                        .executes(ctx -> executeWithdraw(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount"),
                                                CurrencyService.Source.TAX_BALANCE, -1))
                                        // explicit destination literals
                                        .then(buildWithdrawDestBranch("tax",       CurrencyService.Source.TAX_BALANCE))
                                        .then(buildWithdrawDestBranch("wallet",    CurrencyService.Source.WALLET))
                                        .then(buildWithdrawDestBranch("inventory", CurrencyService.Source.INVENTORY))
                                        // colonyId without destination (tax default)
                                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                                .suggests(COLONY_SUGGESTIONS)
                                                .executes(ctx -> executeWithdraw(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                                        CurrencyService.Source.TAX_BALANCE,
                                                        IntegerArgumentType.getInteger(ctx, "colonyId")))))));

        dispatcher.register(command);
    }

    // Builds: deposit <amount> <sourceLiteral> [colonyId]
    private static LiteralArgumentBuilder<CommandSourceStack> buildDepositSourceBranch(
            String literal, CurrencyService.Source source) {
        return Commands.literal(literal)
                .executes(ctx -> executeDeposit(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "amount"), source, -1))
                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                        .suggests(COLONY_SUGGESTIONS)
                        .executes(ctx -> executeDeposit(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "amount"), source,
                                IntegerArgumentType.getInteger(ctx, "colonyId"))));
    }

    // Builds: withdraw <amount> <destLiteral> [colonyId]
    private static LiteralArgumentBuilder<CommandSourceStack> buildWithdrawDestBranch(
            String literal, CurrencyService.Source destination) {
        return Commands.literal(literal)
                .executes(ctx -> executeWithdraw(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "amount"), destination, -1))
                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                        .suggests(COLONY_SUGGESTIONS)
                        .executes(ctx -> executeWithdraw(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "amount"), destination,
                                IntegerArgumentType.getInteger(ctx, "colonyId"))));
    }

    private static int executeStatus(CommandSourceStack source, int colonyId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        if (!TaxConfig.isTreasuryEnabled()) {
            player.sendSystemMessage(Component.literal("Treasury system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        IColony colony = resolveColony(player, colonyId);
        if (colony == null) return 0;
        TreasuryManager.sendStatus(player, colony.getID());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDeposit(CommandSourceStack source, int amount,
                                      CurrencyService.Source src, int colonyId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        if (!TaxConfig.isTreasuryEnabled()) {
            player.sendSystemMessage(Component.literal("Treasury system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        IColony colony = resolveColony(player, colonyId);
        if (colony == null) return 0;
        if (!hasColonyPermission(player, colony)) {
            player.sendSystemMessage(Component.literal("You must be a colony owner or officer to manage the treasury.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return TreasuryManager.deposit(player, colony.getID(), amount, src) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeWithdraw(CommandSourceStack source, int amount,
                                       CurrencyService.Source dest, int colonyId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        if (!TaxConfig.isTreasuryEnabled()) {
            player.sendSystemMessage(Component.literal("Treasury system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        IColony colony = resolveColony(player, colonyId);
        if (colony == null) return 0;
        if (!hasColonyPermission(player, colony)) {
            player.sendSystemMessage(Component.literal("You must be a colony owner or officer to manage the treasury.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return TreasuryManager.withdraw(player, colony.getID(), amount, dest) ? Command.SINGLE_SUCCESS : 0;
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
                "Could not determine your colony. Stand inside a colony or specify a colony ID: /wnt treasury status <colonyId>")
                .withStyle(ChatFormatting.RED));
        return null;
    }

    private static boolean hasColonyPermission(ServerPlayer player, IColony colony) {
        return colony.getPermissions().getRank(player.getUUID()).isColonyManager();
    }
}
