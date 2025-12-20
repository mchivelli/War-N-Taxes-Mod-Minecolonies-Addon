package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.RansomManager;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles /wnt ransom commands for the Ransom System.
 * 
 * Commands:
 * - /wnt ransom accept - Accept a pending ransom offer
 * - /wnt ransom deny - Deny a pending ransom offer
 * - /wnt ransom status - Check current ransom status
 */
public class RansomCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wnt")
                .then(Commands.literal("ransom")
                        .then(Commands.literal("accept")
                                .executes(RansomCommand::handleAccept))
                        .then(Commands.literal("deny")
                                .executes(RansomCommand::handleDeny))
                        .then(Commands.literal("status")
                                .executes(RansomCommand::handleStatus))));
    }

    private static int handleAccept(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isRansomSystemEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Ransom system is disabled."));
            return 0;
        }

        if (!RansomManager.hasPendingOffer(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You don't have a pending ransom offer.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean accepted = RansomManager.acceptRansom(player.getUUID());
        if (accepted) {
            // End the active raid for this player's colony
            RansomManager.RansomOffer offer = RansomManager.getPendingOffer(player.getUUID());
            if (offer != null) {
                // Try to end the raid - the RaidManager will handle cleanup
                var raidData = RaidManager.getActiveRaidForColony(offer.colonyId);
                if (raidData != null) {
                    RaidManager.endActiveRaid(raidData, "Ransom paid");
                }
            }
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("Failed to accept ransom. Insufficient funds?")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int handleDeny(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isRansomSystemEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Ransom system is disabled."));
            return 0;
        }

        if (!RansomManager.hasPendingOffer(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You don't have a pending ransom offer.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        RansomManager.denyRansom(player.getUUID());
        return 1;
    }

    private static int handleStatus(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        if (!TaxConfig.isRansomSystemEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Ransom system is disabled."));
            return 0;
        }

        // Check pending offer
        if (RansomManager.hasPendingOffer(player.getUUID())) {
            RansomManager.RansomOffer offer = RansomManager.getPendingOffer(player.getUUID());
            if (offer != null) {
                long remainingMs = offer.expiresAt - System.currentTimeMillis();
                int remainingSec = Math.max(0, (int) (remainingMs / 1000));
                player.sendSystemMessage(Component.literal("⚠ PENDING RANSOM: " + offer.amount + " gold")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("Time remaining: " + remainingSec + " seconds")
                        .withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("Use /wnt ransom accept or /wnt ransom deny")
                        .withStyle(ChatFormatting.GRAY));
                return 1;
            }
        }

        // Check immunity
        if (RansomManager.hasImmunity(player.getUUID())) {
            int hours = RansomManager.getRemainingImmunityHours(player.getUUID());
            player.sendSystemMessage(Component.literal("✓ Raid Immunity Active")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal("Remaining: " + hours + " hours")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }

        player.sendSystemMessage(Component.literal("No pending ransom offers or active immunity.")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }
}
