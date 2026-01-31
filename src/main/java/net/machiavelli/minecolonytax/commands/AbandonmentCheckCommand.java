package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Command to check colony abandonment status and timer information.
 * Shows WnT timer data, officer lists, and abandonment thresholds.
 * Usage: /wnt abandonmentcheck [colonyId]
 */
public class AbandonmentCheckCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wnt")
                .then(Commands.literal("abandonmentcheck")
                        .executes(AbandonmentCheckCommand::checkCurrentColony)
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(AbandonmentCheckCommand::checkSpecificColony))));
    }

    /**
     * Check the colony the player is currently in.
     */
    private static int checkCurrentColony(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        IColony colony = colonyManager.getColonyByPosFromWorld(player.level(), player.blockPosition());

        if (colony == null) {
            player.sendSystemMessage(Component.literal("You are not currently in a colony")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return displayColonyAbandonmentInfo(player, colony);
    }

    /**
     * Check a specific colony by ID.
     */
    private static int checkSpecificColony(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        int colonyId = IntegerArgumentType.getInteger(context, "colonyId");
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        IColony colony = colonyManager.getColonyByWorld(colonyId, player.level());

        if (colony == null) {
            player.sendSystemMessage(Component.literal("Colony with ID " + colonyId + " not found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return displayColonyAbandonmentInfo(player, colony);
    }

    /**
     * Display comprehensive abandonment information for a colony.
     */
    private static int displayColonyAbandonmentInfo(ServerPlayer player, IColony colony) {
        UUID playerId = player.getUUID();

        // Header
        player.sendSystemMessage(Component.literal("=== Colony Abandonment Info ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Colony info
        player.sendSystemMessage(Component.literal("Colony: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(colony.getName()).withStyle(ChatFormatting.WHITE)));

        player.sendSystemMessage(Component.literal("Colony ID: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(colony.getID())).withStyle(ChatFormatting.WHITE)));

        // Player's role in colony
        UUID owner = colony.getPermissions().getOwner();
        boolean isOwner = owner != null && owner.equals(playerId);
        ColonyPlayer colonyPlayer = colony.getPermissions().getPlayers().get(playerId);
        boolean isOfficer = colonyPlayer != null && colonyPlayer.getRank().isColonyManager();

        if (isOwner) {
            player.sendSystemMessage(Component.literal("Your Role: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Owner").withStyle(ChatFormatting.GREEN)));
        } else if (isOfficer) {
            player.sendSystemMessage(Component.literal("Your Role: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Officer").withStyle(ChatFormatting.GREEN)));
        } else {
            player.sendSystemMessage(Component.literal("Your Role: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Not an Officer").withStyle(ChatFormatting.RED)));
        }

        // MineColonies tracking (owner only - shown for reference)
        int mcLastContact = colony.getLastContactInHours();
        player.sendSystemMessage(Component.literal("MineColonies Timer: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(mcLastContact + " hours ago").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" (owner logins only)").withStyle(ChatFormatting.DARK_GRAY)));

        // WnT officer tracking - the authoritative timer
        long officerVisitHours = OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
        if (officerVisitHours >= 0) {
            ChatFormatting timerColor = officerVisitHours < 168 ? ChatFormatting.GREEN :
                                        officerVisitHours < 264 ? ChatFormatting.YELLOW : ChatFormatting.RED;
            player.sendSystemMessage(Component.literal("WnT Officer Timer: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(officerVisitHours + " hours ago").withStyle(timerColor))
                    .append(Component.literal(" (AUTHORITATIVE)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        } else {
            player.sendSystemMessage(Component.literal("WnT Officer Timer: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("No officer activity tracked yet").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" (using MC fallback)").withStyle(ChatFormatting.DARK_GRAY)));
        }

        // Effective timer used for abandonment
        int effectiveLastContact = (officerVisitHours >= 0) ? (int) officerVisitHours : mcLastContact;

        player.sendSystemMessage(Component.literal("Effective Timer: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(effectiveLastContact + " hours ago").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" (used for abandonment)").withStyle(ChatFormatting.GRAY)));

        // Abandonment threshold info
        int abandonHours = net.machiavelli.minecolonytax.TaxConfig.getColonyAutoAbandonDays() * 24;
        int warningHours = (net.machiavelli.minecolonytax.TaxConfig.getColonyAutoAbandonDays() -
                           net.machiavelli.minecolonytax.TaxConfig.getAbandonWarningDays()) * 24;

        player.sendSystemMessage(Component.literal("\nAbandonment Thresholds:")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        player.sendSystemMessage(Component.literal("  Warning: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(warningHours + " hours (" + (warningHours/24) + " days)")
                        .withStyle(ChatFormatting.WHITE)));

        player.sendSystemMessage(Component.literal("  Abandon: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(abandonHours + " hours (" + (abandonHours/24) + " days)")
                        .withStyle(ChatFormatting.WHITE)));

        // Status indicator
        int hoursUntilWarning = warningHours - effectiveLastContact;
        int hoursUntilAbandon = abandonHours - effectiveLastContact;

        player.sendSystemMessage(Component.literal("\nStatus: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (effectiveLastContact >= abandonHours) {
            player.sendSystemMessage(Component.literal("  ⚠ READY TO ABANDON")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else if (effectiveLastContact >= warningHours) {
            player.sendSystemMessage(Component.literal("  ⚠ WARNING ZONE")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(" - " + (hoursUntilAbandon/24) + " days until abandonment")
                            .withStyle(ChatFormatting.YELLOW)));
        } else {
            player.sendSystemMessage(Component.literal("  ✓ ACTIVE")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal(" - " + (hoursUntilWarning/24) + " days until warning")
                            .withStyle(ChatFormatting.WHITE)));
        }

        // List all officers
        player.sendSystemMessage(Component.literal("\nOfficers in Colony:").withStyle(ChatFormatting.GOLD));
        int officerCount = 0;
        for (ColonyPlayer cp : colony.getPermissions().getPlayers().values()) {
            if (cp.getRank().isColonyManager()) {
                officerCount++;
                String name = cp.getName();
                boolean isOnline = player.getServer().getPlayerList().getPlayer(cp.getID()) != null;

                player.sendSystemMessage(Component.literal("  • ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(isOnline ? " (Online)" : " (Offline)")
                                .withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
            }
        }

        if (officerCount == 0) {
            player.sendSystemMessage(Component.literal("  No officers found")
                    .withStyle(ChatFormatting.GRAY));
        }

        // Footer with help text
        player.sendSystemMessage(Component.literal("\n===============================")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Show appropriate tip based on config
        if (net.machiavelli.minecolonytax.TaxConfig.shouldResetTimerOnOfficerLogin()) {
            player.sendSystemMessage(Component.literal("Tip: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Officer login OR colony visit resets the timer")
                            .withStyle(ChatFormatting.WHITE)));
        } else {
            player.sendSystemMessage(Component.literal("Tip: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Officers must physically visit the colony to reset the timer")
                            .withStyle(ChatFormatting.WHITE)));
        }

        return 1;
    }
}
