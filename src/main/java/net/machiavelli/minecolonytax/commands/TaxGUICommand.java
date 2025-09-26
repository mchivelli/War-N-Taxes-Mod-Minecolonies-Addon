package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to open the Tax Management GUI
 */
public class TaxGUICommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("taxgui")
                .requires(source -> source.hasPermission(0)) // Any player can use this
                .executes(TaxGUICommand::execute)
        );
        
        // Also register as "mct" for convenience
        dispatcher.register(
            Commands.literal("mct")
                .requires(source -> source.hasPermission(0))
                .executes(TaxGUICommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // Check if this is being run on the server side
        if (source.getEntity() instanceof ServerPlayer player) {
            // Send message to player to use the client-side command or keybind
            player.sendSystemMessage(Component.literal("Use the T key or /taxgui on the client to open the Tax Management GUI"));
            
            // Note: The actual GUI opening happens on the client side through a keybind or client command
            // This server command just provides feedback to the player
        }
        
        return 1;
    }
}
