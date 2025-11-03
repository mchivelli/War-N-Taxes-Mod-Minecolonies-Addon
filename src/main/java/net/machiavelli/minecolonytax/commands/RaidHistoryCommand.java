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

/**
 * Command to view raid history for a colony.
 * Shows all raids (successful and failed) with raider names and amounts stolen.
 */
public class RaidHistoryCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("raidhistory")
                        .requires(src -> src.hasPermission(0))
                        .then(Commands.argument("colony", StringArgumentType.word())
                                .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "colony"))))
                        .executes(ctx -> execute(ctx, null))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, String colonyArg) throws CommandSyntaxException {
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

        // Permission check: Colony managers/officers OR admins (permission level 2+)
        var rank = colony.getPermissions().getRank(player.getUUID());
        boolean isAdmin = src.hasPermission(2);
        
        if (!isAdmin && (rank == null || !rank.isColonyManager())) {
            src.sendFailure(Component.literal("You must be a colony officer or admin to view raid history."));
            return 0;
        }

        HistoryManager.ColonyHistory history = HistoryManager.getColonyHistory(colony.getID());
        if (history == null || history.getRaidEvents().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No raid history for colony \"" + colony.getName() + "\""), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal("§6=== Raid History for \"" + colony.getName() + "\" ==="), false);
        src.sendSuccess(() -> Component.literal(""), false);
        
        List<String> raidEvents = history.getRaidEvents();
        int eventCount = Math.min(raidEvents.size(), 50); // Show last 50 raids
        
        // Show most recent raids first
        for (int i = raidEvents.size() - 1; i >= raidEvents.size() - eventCount; i--) {
            String event = raidEvents.get(i);
            src.sendSuccess(() -> Component.literal(event), false);
        }
        
        if (raidEvents.size() > 50) {
            src.sendSuccess(() -> Component.literal(""), false);
            src.sendSuccess(() -> Component.literal("§7(Showing last 50 of " + raidEvents.size() + " raids)"), false);
        }
        
        return 1;
    }

    /**
     * If arg is non-null, try parse as ID or name;
     * otherwise default to the first colony the player manages.
     * Admins can view any colony without being a member.
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
