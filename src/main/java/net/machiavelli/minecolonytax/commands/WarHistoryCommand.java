package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class WarHistoryCommand
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                Commands.literal("warhistory")
                        .requires(src -> src.hasPermission(0))
                        .then(Commands.argument("colony", StringArgumentType.word())
                                .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "colony"))))
                        .executes(ctx -> execute(ctx, null))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, String colonyArg) throws CommandSyntaxException
    {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("You must be a player to run this command."));
            return 0;
        }

        IColony colony = resolveColony(src, player, colonyArg);
        if (colony == null) {
            src.sendFailure(Component.literal("Colony not found or you're not a manager of any colony."));
            return 0;
        }

        // Only allow colony managers/officers
        var rank = colony.getPermissions().getRank(player.getUUID());
        if (rank == null || !rank.isColonyManager()) {
            src.sendFailure(Component.literal("You must be a colony officer to view history."));
            return 0;
        }

        HistoryManager.ColonyHistory history = HistoryManager.getColonyHistory(colony.getID());
        if (history == null || history.getEvents().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No war history for colony “" + colony.getName() + "”"), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal("§6War History for “" + colony.getName() + "”:"), false);
        for (String event : history.getEvents()) {
            src.sendSuccess(() -> Component.literal(event), false);
        }
        return 1;
    }

    /**
     * If arg is non-null, try parse as ID or name;
     * otherwise default to the first colony the player manages.
     */
    private static IColony resolveColony(
            CommandSourceStack src,
            ServerPlayer player,
            String arg
    ) {
        IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();

        if (arg != null) {
            // try by numeric ID
            try {
                int id = Integer.parseInt(arg);
                for (IColony c : mgr.getAllColonies()) {
                    if (c.getID() == id) {
                        return c;
                    }
                }
            } catch (NumberFormatException ignored) {}

            // fallback to name
            for (IColony c : mgr.getAllColonies()) {
                if (c.getName().equalsIgnoreCase(arg)) {
                    return c;
                }
            }
            return null; // explicitly requested colony not found
        }

        // no arg: pick the first colony where player is a manager
        Optional<IColony> own = mgr.getAllColonies().stream()
                .filter(c -> {
                    var rank = c.getPermissions().getRank(player.getUUID());
                    return rank != null && rank.isColonyManager();
                })
                .findFirst();
        return own.orElse(null);
    }
}
