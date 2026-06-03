package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.economy.RaidPenaltyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class RaidRepairCommand {

    private static final Logger LOGGER = LogManager.getLogger(RaidRepairCommand.class);

    // Suggestion provider for colony names (with quotes if needed)
    private static final SuggestionProvider<CommandSourceStack> COLONY_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        List<String> colonyNames = colonyManager.getAllColonies().stream()
                .filter(colony -> {
                    Rank rank = colony.getPermissions().getRank(player.getUUID());
                    return rank != null && rank.isColonyManager();
                })
                .map(IColony::getName)
                .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                .collect(Collectors.toList());

        return SharedSuggestionProvider.suggest(colonyNames, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wnt")
                        .then(Commands.literal("repair")
                                .requires(source -> source.hasPermission(0)) // Available to everyone (permission
                                                                             // checked in execute)
                                .then(Commands.argument("colony", StringArgumentType.string())
                                        .suggests(COLONY_SUGGESTIONS)
                                        .executes(context -> {
                                            String colonyName = StringArgumentType.getString(context, "colony")
                                                    .replace("\"", "");
                                            return execute(context, colonyName);
                                        }))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String colonyName)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        List<IColony> colonies = colonyManager.getAllColonies();

        IColony targetColony = null;
        for (IColony colony : colonies) {
            if (colony.getName().equalsIgnoreCase(colonyName)) {
                targetColony = colony;
                break;
            }
        }

        if (targetColony == null) {
            source.sendFailure(Component.literal("Colony '" + colonyName + "' not found.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Check Permissions
        Rank playerRank = targetColony.getPermissions().getRank(player.getUUID());
        if (playerRank == null || !playerRank.isColonyManager()) {
            source.sendFailure(
                    Component.literal("You must be an officer or owner of this colony to repair raid damage.")
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        int colonyId = targetColony.getID();

        // Delegate repair logic to manager
        if (RaidPenaltyManager.repair(player, colonyId)) {
            return 1;
        } else {
            return 0;
        }
    }
}
