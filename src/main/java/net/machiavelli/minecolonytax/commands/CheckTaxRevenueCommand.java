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
import net.machiavelli.minecolonytax.MineColonyTax;  // Import the main mod class for MOD_ID
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class CheckTaxRevenueCommand {

    private static final Logger LOGGER = LogManager.getLogger(CheckTaxRevenueCommand.class);

    // Register the command
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("checktax")
                .requires(source -> source.hasPermission(0)) // Requires permission level 0 (Available to all players)
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> source.hasPermission(2)) // Requires permission level 2 (Server Ops) for checking other players
                        .executes(context -> checkTaxForPlayer(context, StringArgumentType.getString(context, "player"))))
                .executes(CheckTaxRevenueCommand::checkTaxForSelf);

        dispatcher.register(command);
    }

    private static int checkTaxForSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
            MinecraftServer server = player.getServer();  // Retrieve the server instance

            if (server == null) {
                source.sendFailure(Component.literal("Unable to retrieve server instance."));
                return 0;
            }

            LOGGER.info("Executing /checktax command for player: {}", player.getName().getString());

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            List<IColony> colonies = colonyManager.getAllColonies();  // Retrieve all colonies

            boolean foundColonies = false;

            for (IColony colony : colonies) {
                // Verify if the player is a manager of the colony
                Rank playerRank = colony.getPermissions().getRank(player.getUUID());
                LOGGER.info("Checking colony: {}, Player rank: {}", colony.getName(), playerRank);

                if (playerRank.isColonyManager()) {
                    foundColonies = true;

                    // Get the stored tax revenue for the colony
                    int taxRevenue = TaxManager.getStoredTaxForColony(colony);
                    LOGGER.info("Player {} is a manager of colony '{}'. Tax revenue: {}", player.getName().getString(), colony.getName(), taxRevenue);

                    // Send the tax information to the player via chat
                    source.sendSuccess(() -> Component.translatable("command.checktax.self", colony.getName(), taxRevenue), false);
                }
            }

            if (!foundColonies) {
                LOGGER.warn("Player {} is not a manager of any colonies.", player.getName().getString());
                source.sendFailure(Component.translatable("command.checktax.no_colonies"));
            }

            return 1;
        } catch (CommandSyntaxException e) {
            // Specific handling for this type of exception if needed
            CrashLogger.logCrash(e, "CommandSyntaxException while executing /checktax for player.");
            source.sendFailure(Component.literal("An error occurred while processing the command. Please report this issue."));
            return 0;
        } catch (Exception e) {
            // Log other unexpected exceptions
            String additionalInfo = "Unexpected error while player: " + source.getTextName() + " attempted to check tax.";
            CrashLogger.logCrash(e, additionalInfo);
            source.sendFailure(Component.literal("An unexpected error occurred. Please report this issue to the server admin."));
            return 0;
        }
    }

    private static int checkTaxForPlayer(CommandContext<CommandSourceStack> context, String playerName) {
        CommandSourceStack source = context.getSource();
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);

        if (targetPlayer != null) {
            MinecraftServer server = source.getServer();
            if (server == null) {
                source.sendFailure(Component.literal("Unable to retrieve server instance."));
                return 0;
            }

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            List<IColony> colonies = colonyManager.getAllColonies();

            boolean foundColonies = false;

            for (IColony colony : colonies) {
                Rank playerRank = colony.getPermissions().getRank(targetPlayer.getUUID());
                if (playerRank.isColonyManager()) {
                    foundColonies = true;
                    int taxRevenue = TaxManager.getStoredTaxForColony(colony);
                    source.sendSuccess(() -> Component.translatable("command.checktax.other", playerName, colony.getName(), taxRevenue), false);
                }
            }

            if (!foundColonies) {
                source.sendFailure(Component.translatable("command.checktax.no_colonies"));
            }
        } else {
            source.sendFailure(Component.translatable("command.checktax.player_not_found", playerName));;
        }
        return 1;
    }
}
