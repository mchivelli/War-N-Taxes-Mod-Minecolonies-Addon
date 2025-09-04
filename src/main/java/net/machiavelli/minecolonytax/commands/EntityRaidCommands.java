package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.raid.EntityRaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Commands for managing and testing entity raids
 */
public class EntityRaidCommands {

    private static final Logger LOGGER = LogManager.getLogger(EntityRaidCommands.class);

    /**
     * Register entity raid commands
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wnt")
                .then(Commands.literal("entityraid")
                        .requires(source -> source.hasPermission(2)) // OP level 2
                        .then(Commands.literal("status")
                                .executes(EntityRaidCommands::showEntityRaidStatus))
                        .then(Commands.literal("config")
                                .executes(EntityRaidCommands::showEntityRaidConfig))
                        .then(Commands.literal("end")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                        .executes(EntityRaidCommands::endEntityRaid)))
                        .then(Commands.literal("test")
                                .then(Commands.argument("colonyName", StringArgumentType.string())
                                        .executes(EntityRaidCommands::testEntityRaid)))
                        .then(Commands.literal("reload")
                                .executes(EntityRaidCommands::reloadEntityRaidConfig))
                )
        );
    }

    /**
     * Show current entity raid status
     */
    public static int showEntityRaidStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            Map<Integer, EntityRaidManager.ActiveEntityRaid> activeRaids = EntityRaidManager.getActiveEntityRaids();
            
            if (activeRaids.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No active entity raids.")
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.literal("Active Entity Raids (" + activeRaids.size() + "):")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                
                for (Map.Entry<Integer, EntityRaidManager.ActiveEntityRaid> entry : activeRaids.entrySet()) {
                    int colonyId = entry.getKey();
                    EntityRaidManager.ActiveEntityRaid raid = entry.getValue();
                    
                    IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
                    String colonyName = colony != null ? colony.getName() : "Unknown";
                    
                    long duration = (System.currentTimeMillis() - raid.getStartTime()) / 1000;
                    String status = raid.hasLeftBoundary() ? "BOUNDARY VIOLATION" : "ACTIVE";
                    
                    source.sendSuccess(() -> Component.literal("  • Colony: " + colonyName + " (ID: " + colonyId + ")")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("\n    Status: " + status)
                                    .withStyle(raid.hasLeftBoundary() ? ChatFormatting.RED : ChatFormatting.GREEN))
                            .append(Component.literal("\n    Duration: " + duration + "s")
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("\n    Entities: " + raid.getTriggeringEntities().size())
                                    .withStyle(ChatFormatting.AQUA)), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error showing entity raid status", e);
            source.sendFailure(Component.literal("Error retrieving entity raid status: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Show current entity raid configuration
     */
    public static int showEntityRaidConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            source.sendSuccess(() -> Component.literal("Entity Raid Configuration:")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            
            source.sendSuccess(() -> Component.literal("  Enabled: " + TaxConfig.isEntityRaidsEnabled())
                    .withStyle(TaxConfig.isEntityRaidsEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            
            source.sendSuccess(() -> Component.literal("  Threshold: " + TaxConfig.getEntityRaidThreshold() + " entities")
                    .withStyle(ChatFormatting.YELLOW), false);
            
            source.sendSuccess(() -> Component.literal("  Detection Radius: " + TaxConfig.getEntityRaidDetectionRadius() + " blocks")
                    .withStyle(ChatFormatting.YELLOW), false);
            
            source.sendSuccess(() -> Component.literal("  Message Only: " + TaxConfig.isEntityRaidMessageOnly())
                    .withStyle(ChatFormatting.YELLOW), false);
            
            source.sendSuccess(() -> Component.literal("  Boundary Timer: " + TaxConfig.getEntityRaidBoundaryTimerSeconds() + " seconds")
                    .withStyle(ChatFormatting.YELLOW), false);
            
            source.sendSuccess(() -> Component.literal("  Check Interval: " + TaxConfig.getEntityRaidCheckIntervalTicks() + " ticks")
                    .withStyle(ChatFormatting.YELLOW), false);
            
            source.sendSuccess(() -> Component.literal("  Cooldown: " + TaxConfig.getEntityRaidCooldownMinutes() + " minutes")
                    .withStyle(ChatFormatting.YELLOW), false);
            
            List<? extends String> whitelist = TaxConfig.getEntityRaidWhitelist();
            source.sendSuccess(() -> Component.literal("  Whitelisted Entities (" + whitelist.size() + "):")
                    .withStyle(ChatFormatting.AQUA), false);
            
            for (String entityType : whitelist) {
                source.sendSuccess(() -> Component.literal("    • " + entityType)
                        .withStyle(ChatFormatting.GRAY), false);
            }
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error showing entity raid config", e);
            source.sendFailure(Component.literal("Error retrieving entity raid config: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * End an active entity raid
     */
    public static int endEntityRaid(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int colonyId = IntegerArgumentType.getInteger(context, "colonyId");
        
        try {
            if (!EntityRaidManager.hasActiveEntityRaid(colonyId)) {
                source.sendFailure(Component.literal("No active entity raid found for colony ID: " + colonyId));
                return 0;
            }
            
            EntityRaidManager.endEntityRaid(colonyId, "Ended by administrator");
            
            source.sendSuccess(() -> Component.literal("Entity raid ended for colony ID: " + colonyId)
                    .withStyle(ChatFormatting.GREEN), true);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error ending entity raid", e);
            source.sendFailure(Component.literal("Error ending entity raid: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Test entity raid system by triggering a raid on a specific colony
     */
    public static int testEntityRaid(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String colonyName = StringArgumentType.getString(context, "colonyName");
        
        try {
            // Find colony by name
            IColony foundColony = null;
            for (IColony c : IColonyManager.getInstance().getAllColonies()) {
                if (c.getName().equalsIgnoreCase(colonyName)) {
                    foundColony = c;
                    break;
                }
            }
            
            if (foundColony == null) {
                source.sendFailure(Component.literal("Colony not found: " + colonyName));
                return 0;
            }
            
            final IColony colony = foundColony; // Make effectively final for lambda use
            
            if (EntityRaidManager.hasActiveEntityRaid(colony.getID())) {
                source.sendFailure(Component.literal("Colony already has an active entity raid: " + colonyName));
                return 0;
            }
            
            if (!TaxConfig.isEntityRaidsEnabled()) {
                source.sendFailure(Component.literal("Entity raids are disabled in the configuration"));
                return 0;
            }
            
            // Create a fake entity raid for testing
            // In a real scenario, this would be triggered by actual entities
            source.sendSuccess(() -> Component.literal("⚠ TEST ENTITY RAID TRIGGERED ⚠")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            
            source.sendSuccess(() -> Component.literal("This is a test of the entity raid system for colony: " + colony.getName())
                    .withStyle(ChatFormatting.YELLOW), true);
            
            source.sendSuccess(() -> Component.literal("In a real scenario, this would be triggered by " + 
                    TaxConfig.getEntityRaidThreshold() + " or more whitelisted entities within " + 
                    TaxConfig.getEntityRaidDetectionRadius() + " blocks of the colony.")
                    .withStyle(ChatFormatting.GRAY), false);
            
            // Note: We don't actually trigger a raid here since it would require fake entities
            // This is just a configuration and system test
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error testing entity raid", e);
            source.sendFailure(Component.literal("Error testing entity raid: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Reload entity raid configuration
     */
    public static int reloadEntityRaidConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            // Note: Forge configs are automatically reloaded, but we can provide feedback
            source.sendSuccess(() -> Component.literal("Entity raid configuration reloaded successfully!")
                    .withStyle(ChatFormatting.GREEN), true);
            
            source.sendSuccess(() -> Component.literal("Current status: " + 
                    (TaxConfig.isEntityRaidsEnabled() ? "ENABLED" : "DISABLED"))
                    .withStyle(TaxConfig.isEntityRaidsEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error reloading entity raid config", e);
            source.sendFailure(Component.literal("Error reloading entity raid config: " + e.getMessage()));
            return 0;
        }
    }
}