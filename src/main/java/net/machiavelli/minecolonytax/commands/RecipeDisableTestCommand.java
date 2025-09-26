package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.event.RecipeDisableEventHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Command to test the recipe disabling feature
 */
public class RecipeDisableTestCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeDisableTestCommand.class);
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testrecipedisable")
            .requires(source -> source.hasPermission(2)) // Requires OP level 2
            .then(Commands.literal("status")
                .executes(RecipeDisableTestCommand::showStatus))
            .then(Commands.literal("list")
                .executes(RecipeDisableTestCommand::listDisabledRecipes))
            .then(Commands.literal("check")
                .then(Commands.argument("block_id", com.mojang.brigadier.arguments.StringArgumentType.string())
                    .executes(RecipeDisableTestCommand::checkBlock)))
        );
    }
    
    private static int showStatus(CommandContext<CommandSourceStack> context) {
        boolean isEnabled = TaxConfig.isDisableHutRecipesEnabled();
        Component message = Component.literal("Recipe Disabling Status: ")
            .append(Component.literal(isEnabled ? "ENABLED" : "DISABLED")
                .withStyle(isEnabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
        
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }
    
    private static int listDisabledRecipes(CommandContext<CommandSourceStack> context) {
        Set<ResourceLocation> disabledRecipes = RecipeDisableEventHandler.getDisabledHutRecipes();
        
        Component message = Component.literal("Disabled Hut Recipes (" + disabledRecipes.size() + " total):")
            .withStyle(net.minecraft.ChatFormatting.YELLOW);
        
        context.getSource().sendSuccess(() -> message, false);
        
        for (ResourceLocation blockId : disabledRecipes) {
            Component blockMessage = Component.literal("  - " + blockId.toString())
                .withStyle(net.minecraft.ChatFormatting.GRAY);
            context.getSource().sendSuccess(() -> blockMessage, false);
        }
        
        return disabledRecipes.size();
    }
    
    private static int checkBlock(CommandContext<CommandSourceStack> context) {
        String blockIdString = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "block_id");
        ResourceLocation blockId = ResourceLocation.tryParse(blockIdString);
        
        if (blockId == null) {
            Component errorMessage = Component.literal("Invalid block ID: " + blockIdString)
                .withStyle(net.minecraft.ChatFormatting.RED);
            context.getSource().sendFailure(errorMessage);
            return 0;
        }
        
        boolean shouldDisable = RecipeDisableEventHandler.shouldDisableRecipe(blockId);
        Component message = Component.literal("Block " + blockId + " recipe should be disabled: ")
            .append(Component.literal(shouldDisable ? "YES" : "NO")
                .withStyle(shouldDisable ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN));
        
        context.getSource().sendSuccess(() -> message, false);
        return shouldDisable ? 1 : 0;
    }
}







