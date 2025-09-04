package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.permissions.GeneralColonyPermissionsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Commands for managing general colony permissions
 */
public class GeneralPermissionsCommands {

    private static final Logger LOGGER = LogManager.getLogger(GeneralPermissionsCommands.class);

    /**
     * Register general permissions commands
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wnt")
                .then(Commands.literal("permissions")
                        .requires(source -> source.hasPermission(2)) // OP level 2
                        .then(Commands.literal("status")
                                .executes(GeneralPermissionsCommands::showPermissionsStatus))
                        .then(Commands.literal("config")
                                .executes(GeneralPermissionsCommands::showPermissionsConfig))
                        .then(Commands.literal("apply")
                                .executes(GeneralPermissionsCommands::applyGeneralPermissions))
                        .then(Commands.literal("remove")
                                .executes(GeneralPermissionsCommands::removeGeneralPermissions))
                        .then(Commands.literal("reload")
                                .executes(GeneralPermissionsCommands::reloadGeneralPermissions))
                        .then(Commands.literal("apply")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                        .executes(GeneralPermissionsCommands::applyToSpecificColony)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                        .executes(GeneralPermissionsCommands::removeFromSpecificColony)))
                )
        );
    }

    /**
     * Show current general permissions status
     */
    public static int showPermissionsStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            Map<Integer, Boolean> status = GeneralColonyPermissionsManager.getGeneralPermissionsStatus();
            
            source.sendSuccess(() -> Component.literal("General Colony Permissions Status:")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            
            source.sendSuccess(() -> Component.literal("System Enabled: " + TaxConfig.isGeneralItemInteractionsEnabled())
                    .withStyle(TaxConfig.isGeneralItemInteractionsEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            
            if (status.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No colonies have general permissions applied.")
                        .withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendSuccess(() -> Component.literal("Colonies with General Permissions (" + status.size() + "):")
                        .withStyle(ChatFormatting.AQUA), false);
                
                for (Map.Entry<Integer, Boolean> entry : status.entrySet()) {
                    int colonyId = entry.getKey();
                    IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
                    String colonyName = colony != null ? colony.getName() : "Unknown";
                    
                    source.sendSuccess(() -> Component.literal("  • Colony: " + colonyName + " (ID: " + colonyId + ")")
                            .withStyle(ChatFormatting.GREEN), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error showing permissions status", e);
            source.sendFailure(Component.literal("Error retrieving permissions status: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Show current general permissions configuration
     */
    public static int showPermissionsConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            source.sendSuccess(() -> Component.literal("General Colony Permissions Configuration:")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            
            source.sendSuccess(() -> Component.literal("  Enabled: " + TaxConfig.isGeneralItemInteractionsEnabled())
                    .withStyle(TaxConfig.isGeneralItemInteractionsEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            
            List<? extends String> actions = TaxConfig.getGeneralColonyActions();
            source.sendSuccess(() -> Component.literal("  Allowed Actions (" + actions.size() + "):")
                    .withStyle(ChatFormatting.AQUA), false);
            
            for (String action : actions) {
                source.sendSuccess(() -> Component.literal("    • " + action)
                        .withStyle(ChatFormatting.GRAY), false);
            }
            
            source.sendSuccess(() -> Component.literal("  Description: These actions are allowed for ALL players")
                    .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("  (including strangers and enemies) within colony boundaries.")
                    .withStyle(ChatFormatting.YELLOW), false);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error showing permissions config", e);
            source.sendFailure(Component.literal("Error retrieving permissions config: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Apply general permissions to all colonies
     */
    public static int applyGeneralPermissions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            if (!TaxConfig.isGeneralItemInteractionsEnabled()) {
                source.sendFailure(Component.literal("General colony permissions are disabled in configuration"));
                return 0;
            }
            
            GeneralColonyPermissionsManager.applyGeneralPermissionsToAllColonies();
            
            source.sendSuccess(() -> Component.literal("General permissions applied to all colonies successfully!")
                    .withStyle(ChatFormatting.GREEN), true);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error applying general permissions", e);
            source.sendFailure(Component.literal("Error applying general permissions: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Remove general permissions from all colonies
     */
    public static int removeGeneralPermissions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            GeneralColonyPermissionsManager.removeGeneralPermissionsFromAllColonies();
            
            source.sendSuccess(() -> Component.literal("General permissions removed from all colonies successfully!")
                    .withStyle(ChatFormatting.GREEN), true);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error removing general permissions", e);
            source.sendFailure(Component.literal("Error removing general permissions: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Reload general permissions (remove and reapply based on current config)
     */
    public static int reloadGeneralPermissions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            GeneralColonyPermissionsManager.reapplyGeneralPermissions();
            
            source.sendSuccess(() -> Component.literal("General permissions reloaded successfully!")
                    .withStyle(ChatFormatting.GREEN), true);
            
            source.sendSuccess(() -> Component.literal("Current status: " + 
                    (TaxConfig.isGeneralItemInteractionsEnabled() ? "ENABLED" : "DISABLED"))
                    .withStyle(TaxConfig.isGeneralItemInteractionsEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error reloading general permissions", e);
            source.sendFailure(Component.literal("Error reloading general permissions: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Apply general permissions to a specific colony
     */
    public static int applyToSpecificColony(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int colonyId = IntegerArgumentType.getInteger(context, "colonyId");
        
        try {
            IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
            if (colony == null) {
                source.sendFailure(Component.literal("Colony not found with ID: " + colonyId));
                return 0;
            }
            
            if (!TaxConfig.isGeneralItemInteractionsEnabled()) {
                source.sendFailure(Component.literal("General colony permissions are disabled in configuration"));
                return 0;
            }
            
            GeneralColonyPermissionsManager.applyGeneralPermissions(colony);
            
            source.sendSuccess(() -> Component.literal("General permissions applied to colony '" + colony.getName() + "' successfully!")
                    .withStyle(ChatFormatting.GREEN), true);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error applying general permissions to specific colony", e);
            source.sendFailure(Component.literal("Error applying general permissions: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Remove general permissions from a specific colony
     */
    public static int removeFromSpecificColony(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int colonyId = IntegerArgumentType.getInteger(context, "colonyId");
        
        try {
            IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
            if (colony == null) {
                source.sendFailure(Component.literal("Colony not found with ID: " + colonyId));
                return 0;
            }
            
            GeneralColonyPermissionsManager.removeGeneralPermissions(colony);
            
            source.sendSuccess(() -> Component.literal("General permissions removed from colony '" + colony.getName() + "' successfully!")
                    .withStyle(ChatFormatting.GREEN), true);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error removing general permissions from specific colony", e);
            source.sendFailure(Component.literal("Error removing general permissions: " + e.getMessage()));
            return 0;
        }
    }
}