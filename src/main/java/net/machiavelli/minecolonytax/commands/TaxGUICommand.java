package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.OpenTaxGUIPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to open the Tax Management GUI.
 * 
 * Usage: /wnt taxgui, /taxgui, or /mct
 */
public class TaxGUICommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register as /wnt taxgui (primary command)
        dispatcher.register(
                Commands.literal("wnt")
                        .then(Commands.literal("taxgui")
                                .executes(ctx -> executeOpenGUI(ctx.getSource()))));

        // Register as /taxgui for convenience
        dispatcher.register(
                Commands.literal("taxgui")
                        .requires(source -> source.hasPermission(0))
                        .executes(ctx -> executeOpenGUI(ctx.getSource())));

        // Also register as /mct for convenience
        dispatcher.register(
                Commands.literal("mct")
                        .requires(source -> source.hasPermission(0))
                        .executes(ctx -> executeOpenGUI(ctx.getSource())));
    }

    private static int executeOpenGUI(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        // Send packet to client to open the GUI
        NetworkHandler.sendToPlayer(player, new OpenTaxGUIPacket());
        return Command.SINGLE_SUCCESS;
    }
}
