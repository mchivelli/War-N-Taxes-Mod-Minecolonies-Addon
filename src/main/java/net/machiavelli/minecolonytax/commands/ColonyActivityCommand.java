package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Command to check colony activity status and inactivity settings.
 * Provides information about when colonies were last visited by owners/officers.
 */
public class ColonyActivityCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("colonyactivity")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .then(Commands.literal("check")
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(ColonyActivityCommand::checkColonyActivity)))
                .then(Commands.literal("list")
                        .executes(ColonyActivityCommand::listInactiveColonies))
                .then(Commands.literal("status")
                        .executes(ColonyActivityCommand::showSystemStatus)));
    }

    /**
     * Check activity status of a specific colony.
     */
    private static int checkColonyActivity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int colonyId = IntegerArgumentType.getInteger(context, "colonyId");

        IColony colony = findColonyById(source, colonyId);
        if (colony == null) {
            source.sendFailure(Component.literal("Colony with ID " + colonyId + " not found."));
            return 0;
        }

        int lastContactHours = colony.getLastContactInHours();
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        boolean isInactive = TaxConfig.isColonyInactivityTaxPauseEnabled() && (lastContactHours >= threshold);
        String status = isInactive ? "INACTIVE (Tax generation paused)" : "ACTIVE";

        source.sendSuccess(() -> Component.translatable(
                "command.minecolonytax.inactivity_status",
                colony.getName(),
                lastContactHours,
                threshold,
                status
        ), true);

        // Additional details
        if (isInactive) {
            int hoursOverThreshold = lastContactHours - threshold;
            source.sendSuccess(() -> Component.literal(String.format(
                    "Colony has been inactive for %d hours beyond the threshold of %d hours.",
                    hoursOverThreshold, threshold
            )), true);
        }

        return 1;
    }

    /**
     * List all colonies that are currently inactive.
     */
    private static int listInactiveColonies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        if (!TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            source.sendSuccess(() -> Component.literal(
                    "Colony inactivity tax pause system is currently disabled."
            ), true);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                "Scanning for inactive colonies..."
        ), true);

        final int[] counters = {0, 0}; // [inactiveCount, totalCount]
        final int threshold = TaxConfig.getColonyInactivityHoursThreshold();

        for (Level world : source.getServer().getAllLevels()) {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            for (IColony colony : colonyManager.getColonies(world)) {
                counters[1]++; // totalCount
                int lastContactHours = colony.getLastContactInHours();
                
                if (lastContactHours >= threshold) {
                    counters[0]++; // inactiveCount
                    final String colonyName = colony.getName();
                    final int colonyId = colony.getID();
                    final int finalLastContactHours = lastContactHours;
                    source.sendSuccess(() -> Component.literal(String.format(
                            "• %s (ID: %d) - Last contact: %d hours ago",
                            colonyName, colonyId, finalLastContactHours
                    )), true);
                }
            }
        }

        final int finalInactiveCount = counters[0];
        final int finalTotalCount = counters[1];
        
        if (finalInactiveCount == 0) {
            source.sendSuccess(() -> Component.literal(
                    "No inactive colonies found! All " + finalTotalCount + " colonies are active."
            ), true);
        } else {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Found %d inactive colonies out of %d total colonies.",
                    finalInactiveCount, finalTotalCount
            )), true);
        }

        return 1;
    }

    /**
     * Show the current system status for colony inactivity tracking.
     */
    private static int showSystemStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        boolean enabled = TaxConfig.isColonyInactivityTaxPauseEnabled();
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();

        source.sendSuccess(() -> Component.literal("=== Colony Inactivity System Status ==="), true);
        source.sendSuccess(() -> Component.literal("System Enabled: " + (enabled ? "YES" : "NO")), true);
        
        if (enabled) {
            source.sendSuccess(() -> Component.literal("Inactivity Threshold: " + threshold + " hours"), true);
            source.sendSuccess(() -> Component.literal("Threshold in Days: " + String.format("%.1f", threshold / 24.0)), true);
            
            // Calculate some statistics
            final int[] stats = {0, 0}; // [totalColonies, inactiveColonies]
            
            for (Level world : source.getServer().getAllLevels()) {
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                for (IColony colony : colonyManager.getColonies(world)) {
                    stats[0]++; // totalColonies
                    if (colony.getLastContactInHours() >= threshold) {
                        stats[1]++; // inactiveColonies
                    }
                }
            }
            
            final int finalTotalColonies = stats[0];
            final int finalInactiveColonies = stats[1];
            final int finalActiveColonies = finalTotalColonies - finalInactiveColonies;
            
            source.sendSuccess(() -> Component.literal("Total Colonies: " + finalTotalColonies), true);
            source.sendSuccess(() -> Component.literal("Inactive Colonies: " + finalInactiveColonies), true);
            source.sendSuccess(() -> Component.literal("Active Colonies: " + finalActiveColonies), true);
            
            if (finalTotalColonies > 0) {
                final double inactivePercentage = (finalInactiveColonies * 100.0) / finalTotalColonies;
                source.sendSuccess(() -> Component.literal(String.format("Inactive Percentage: %.1f%%", inactivePercentage)), true);
            }
        } else {
            source.sendSuccess(() -> Component.literal("Tax generation continues normally for all colonies."), true);
        }

        return 1;
    }

    /**
     * Helper method to find a colony by ID across all worlds.
     */
    private static IColony findColonyById(CommandSourceStack source, int colonyId) {
        for (Level world : source.getServer().getAllLevels()) {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            for (IColony colony : colonyManager.getColonies(world)) {
                if (colony.getID() == colonyId) {
                    return colony;
                }
            }
        }
        return null;
    }
}
