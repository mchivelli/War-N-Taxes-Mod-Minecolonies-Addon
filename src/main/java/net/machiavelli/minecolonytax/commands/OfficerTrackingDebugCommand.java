package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.machiavelli.minecolonytax.FirstColonyTracker;
import net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class OfficerTrackingDebugCommand {

    public static int checkCurrentColony(CommandContext<CommandSourceStack> context) {
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
        
        return displayColonyTrackingInfo(source, player, colony);
    }

    public static int checkSpecificColony(CommandContext<CommandSourceStack> context) {
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

        return displayColonyTrackingInfo(source, player, colony);
    }
    
    private static int displayColonyTrackingInfo(CommandSourceStack source, ServerPlayer player, IColony colony) {
        UUID playerId = player.getUUID();

        UUID owner = colony.getPermissions().getOwner();
        boolean isOwner = owner != null && owner.equals(playerId);
        ColonyPlayer colonyPlayer = colony.getPermissions().getPlayers().get(playerId);
        boolean isOfficer = colonyPlayer != null && colonyPlayer.getRank().isColonyManager();
        boolean isAdmin = source.hasPermission(2);

        if (!isAdmin && !isOwner && !isOfficer) {
            player.sendSystemMessage(Component.literal("You must be an owner, officer, or admin to view this colony's tracking data.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("=== Officer Tracking Info ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        player.sendSystemMessage(Component.literal("Colony: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(colony.getName()).withStyle(ChatFormatting.WHITE)));

        player.sendSystemMessage(Component.literal("Colony ID: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(colony.getID())).withStyle(ChatFormatting.WHITE)));

        String roleLabel = isOwner ? "Owner" : (isOfficer ? "Officer" : "Admin");
        ChatFormatting roleColor = isAdmin && !isOwner && !isOfficer ? ChatFormatting.RED : ChatFormatting.GREEN;
        player.sendSystemMessage(Component.literal("Your Role: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(roleLabel).withStyle(roleColor)));

        // --- Colony Owner (resolved name, not raw permissions entry) ---
        String ownerDisplay;
        if (owner == null) {
            ownerDisplay = "None (no owner set)";
        } else {
            ServerPlayer ownerPlayer = source.getServer() != null
                    ? source.getServer().getPlayerList().getPlayer(owner) : null;
            if (ownerPlayer != null) {
                ownerDisplay = ownerPlayer.getName().getString();
            } else {
                ColonyPlayer ownerEntry = colony.getPermissions().getPlayers().get(owner);
                String entryName = ownerEntry != null ? ownerEntry.getName() : null;
                if (entryName != null && !entryName.contains("[abandoned]") && !entryName.isBlank()) {
                    ownerDisplay = entryName + " (offline)";
                } else {
                    ownerDisplay = owner.toString() + " (offline, name unavailable)";
                }
            }
        }
        player.sendSystemMessage(Component.literal("Colony Owner: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(ownerDisplay).withStyle(ChatFormatting.WHITE)));

        // --- FirstColonyTracker primary-colony status ---
        UUID fctOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
        if (fctOwner != null) {
            ServerPlayer fctPlayer = source.getServer() != null
                    ? source.getServer().getPlayerList().getPlayer(fctOwner) : null;
            String fctName = fctPlayer != null ? fctPlayer.getName().getString() : fctOwner.toString();
            player.sendSystemMessage(Component.literal("Primary Colony Of: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(fctName).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" (FCT tracked — war required to contest)")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            player.sendSystemMessage(Component.literal("Primary Colony Of: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Not tracked as anyone's primary colony")
                            .withStyle(ChatFormatting.GRAY)));
        }

        int mcLastContact = colony.getLastContactInHours();
        player.sendSystemMessage(Component.literal("MineColonies Last Contact: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(mcLastContact + " hours ago").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (owner visits only)").withStyle(ChatFormatting.GRAY)));
        
        long officerVisitHours = OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
        if (officerVisitHours >= 0) {
            player.sendSystemMessage(Component.literal("Officer Last Visit: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(officerVisitHours + " hours ago").withStyle(ChatFormatting.GREEN)));
        } else {
            player.sendSystemMessage(Component.literal("Officer Last Visit: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("No visits tracked yet").withStyle(ChatFormatting.GRAY)));
        }
        
        int effectiveLastContact = mcLastContact;
        if (officerVisitHours >= 0 && officerVisitHours < mcLastContact) {
            effectiveLastContact = (int) officerVisitHours;
        }
        
        player.sendSystemMessage(Component.literal("Effective Last Contact: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(effectiveLastContact + " hours ago").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (used for abandonment)").withStyle(ChatFormatting.GRAY)));
        
        player.sendSystemMessage(Component.literal("Officers:").withStyle(ChatFormatting.GOLD));
        int officerCount = 0;
        for (ColonyPlayer cp : colony.getPermissions().getPlayers().values()) {
            // Skip owner — shown separately above; skip corrupt [abandoned] entries
            if (cp.getID().equals(owner)) continue;
            String cpName = cp.getName();
            if (cpName == null || cpName.isBlank() || cpName.contains("[abandoned]")
                    || cpName.toLowerCase().contains("abandoned")) continue;

            if (cp.getRank().isColonyManager()) {
                officerCount++;
                boolean isOnline = player.getServer().getPlayerList().getPlayer(cp.getID()) != null;
                player.sendSystemMessage(Component.literal("  • ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(cpName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(isOnline ? " (Online)" : " (Offline)")
                                .withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
            }
        }

        if (officerCount == 0) {
            player.sendSystemMessage(Component.literal("  No officers found")
                    .withStyle(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.literal("===========================")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        return 1;
    }
}
