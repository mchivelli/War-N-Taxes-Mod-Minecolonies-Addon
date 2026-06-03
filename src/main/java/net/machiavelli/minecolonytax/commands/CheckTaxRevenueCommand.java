package net.machiavelli.minecolonytax.commands;

import net.machiavelli.minecolonytax.CrashLogger;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class CheckTaxRevenueCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("checktax")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> checkTaxForPlayer(context, StringArgumentType.getString(context, "player"))))
                .executes(CheckTaxRevenueCommand::checkTaxForSelf);

        dispatcher.register(command);
    }

    private static int checkTaxForSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
            MinecraftServer server = player.getServer();

            if (server == null) {
                source.sendFailure(Component.literal("Unable to retrieve server instance."));
                return 0;
            }

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            List<IColony> colonies = colonyManager.getAllColonies();

            boolean foundColonies = false;

            for (IColony colony : colonies) {
                Rank playerRank = colony.getPermissions().getRank(player.getUUID());

                if (playerRank.isColonyManager()) {
                    foundColonies = true;
                    int taxRevenue = TaxManager.getStoredTaxForColony(colony);
                    source.sendSuccess(() -> Component.translatable("command.checktax.self", colony.getName(), taxRevenue), false);
                }
            }

            if (!foundColonies) {
                source.sendFailure(Component.translatable("command.checktax.no_colonies"));
            }

            return 1;
        } catch (CommandSyntaxException e) {
            CrashLogger.logCrash(e, "CommandSyntaxException while executing /checktax for player.");
            source.sendFailure(Component.literal("An error occurred while processing the command. Please report this issue."));
            return 0;
        } catch (Exception e) {
            String additionalInfo = "Unexpected error while player: " + source.getTextName() + " attempted to check tax.";
            CrashLogger.logCrash(e, additionalInfo);
            source.sendFailure(Component.literal("An unexpected error occurred. Please report this issue to the server admin."));
            return 0;
        }
    }

    private static int checkTaxForPlayer(CommandContext<CommandSourceStack> context, String playerArg) {
        CommandSourceStack source = context.getSource();
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerArg);

        if (targetPlayer != null) {
            boolean foundColonies = false;
            for (IColony colony : colonyManager.getAllColonies()) {
                Rank playerRank = colony.getPermissions().getRank(targetPlayer.getUUID());
                if (playerRank.isColonyManager()) {
                    foundColonies = true;
                    int taxRevenue = TaxManager.getStoredTaxForColony(colony);
                    source.sendSuccess(() -> Component.translatable("command.checktax.other", playerArg, colony.getName(), taxRevenue), false);
                }
            }
            if (!foundColonies) {
                source.sendFailure(Component.translatable("command.checktax.no_colonies"));
            }
        } else {
            IColony colony = tryResolveColony(colonyManager, playerArg);
            if (colony == null) {
                source.sendFailure(Component.literal("Player or colony not found: " + playerArg));
                return 0;
            }
            int taxRevenue = TaxManager.getStoredTaxForColony(colony);
            final String colonyName = colony.getName();
            source.sendSuccess(() -> Component.literal("[Admin] Colony '" + colonyName + "' stored tax: " + taxRevenue), false);
        }
        return 1;
    }

    private static IColony tryResolveColony(IColonyManager mgr, String arg) {
        try {
            int id = Integer.parseInt(arg);
            for (IColony c : mgr.getAllColonies()) {
                if (c.getID() == id) return c;
            }
        } catch (NumberFormatException ignored) {}
        for (IColony c : mgr.getAllColonies()) {
            if (c.getName().equalsIgnoreCase(arg)) return c;
        }
        return null;
    }
}
