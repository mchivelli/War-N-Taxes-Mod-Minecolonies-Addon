package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for managing the War Chest system.
 * 
 * Usage:
 * - /wnt warchest status - View war chest balance and stats
 * - /wnt warchest deposit <amount> - Deposit tax into war chest
 * - /wnt warchest withdraw <amount> - Withdraw from war chest to tax balance
 */
public class WarChestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("wnt")
                .then(Commands.literal("warchest")
                        .then(Commands.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource())))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeDeposit(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeWithdraw(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))));

        dispatcher.register(command);
    }

    private static int executeStatus(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        IColony colony = getPlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.literal("You must be in a colony to use this command.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        WarChestManager.sendStatus(player, colony.getID());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDeposit(CommandSourceStack source, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        IColony colony = getPlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.literal("You must be in a colony to use this command.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Check permissions - must be owner or officer
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

    private static int executeWithdraw(CommandSourceStack source, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled on this server.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        IColony colony = getPlayerColony(player);
        if (colony == null) {
            player.sendSystemMessage(Component.literal("You must be in a colony to use this command.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Check permissions - must be owner or officer
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

    private static IColony getPlayerColony(ServerPlayer player) {
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getIColonyByOwner(player.level(), player.getUUID());
    }

    private static boolean hasColonyPermission(ServerPlayer player, IColony colony) {
        // Check if player is colony manager (owner or officer)
        return colony.getPermissions().getRank(player.getUUID()).isColonyManager();
    }
}
