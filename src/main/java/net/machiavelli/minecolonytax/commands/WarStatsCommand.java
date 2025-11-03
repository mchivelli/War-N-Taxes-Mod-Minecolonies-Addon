package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.attachment.PlayerWarDataAttachment;
import net.machiavelli.minecolonytax.data.PlayerWarData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class WarStatsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> warStatsCommand = Commands.literal("warstats")
                .executes(WarStatsCommand::showStats);

        dispatcher.register(warStatsCommand);
    }

    private static int showStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MineColonyTax.LOGGER.info("Player " + player.getName().getString() + " requested war stats");
        
        PlayerWarData warData = PlayerWarDataAttachment.get(player);
        
        // Debug: log the NBT data for analysis
        CompoundTag nbt = warData.serializeNBT();
        MineColonyTax.LOGGER.info("WarStats command found data: " + nbt.toString());
        
        // Format a nice message showing the player's stats
        Component message = Component.literal("Your War Statistics")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true))
                .append(Component.literal("\n- Players killed in wars: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(String.valueOf(warData.getPlayersKilledInWar()))
                                .withStyle(ChatFormatting.WHITE)))
                .append(Component.literal("\n- Colonies raided: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(String.valueOf(warData.getRaidedColonies()))
                                .withStyle(ChatFormatting.WHITE)))
                .append(Component.literal("\n- Total amount raided: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(String.valueOf(warData.getAmountRaided()))
                                .withStyle(ChatFormatting.WHITE)))
                .append(Component.literal("\n- Wars won: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(String.valueOf(warData.getWarsWon()))
                                .withStyle(ChatFormatting.WHITE)))
                .append(Component.literal("\n- War stalemates: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(String.valueOf(warData.getWarStalemates()))
                                .withStyle(ChatFormatting.WHITE)));
        
        player.sendSystemMessage(message);
        MineColonyTax.LOGGER.debug("Displayed war stats for player: " + player.getName().getString());
        
        return Command.SINGLE_SUCCESS;
    }
} 