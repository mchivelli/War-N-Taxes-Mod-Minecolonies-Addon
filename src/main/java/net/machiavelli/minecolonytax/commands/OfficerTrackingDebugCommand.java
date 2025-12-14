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
 * Debug command to check officer visit tracking status.
 * Usage: /wnt officertracking [colonyId]
 */
public class OfficerTrackingDebugCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wnt")
                .then(Commands.literal("officertracking")
                        .executes(OfficerTrackingDebugCommand::checkCurrentColony)
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(OfficerTrackingDebugCommand::checkSpecificColony))));
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
        
        return displayColonyTrackingInfo(player, colony);
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
        
        return displayColonyTrackingInfo(player, colony);
    }
    
    /**
     * Display tracking information for a colony.
     */
    private static int displayColonyTrackingInfo(ServerPlayer player, IColony colony) {
        UUID playerId = player.getUUID();
        
        // Header
        player.sendSystemMessage(Component.literal("=== Officer Tracking Info ===")
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
        
        // MineColonies tracking (owner only)
        int mcLastContact = colony.getLastContactInHours();
        player.sendSystemMessage(Component.literal("MineColonies Last Contact: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(mcLastContact + " hours ago").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (owner visits only)").withStyle(ChatFormatting.GRAY)));
        
        // Our officer tracking
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
        
        // Effective last contact (what abandonment system uses)
        int effectiveLastContact = mcLastContact;
        if (officerVisitHours >= 0 && officerVisitHours < mcLastContact) {
            effectiveLastContact = (int) officerVisitHours;
        }
        
        player.sendSystemMessage(Component.literal("Effective Last Contact: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(effectiveLastContact + " hours ago").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (used for abandonment)").withStyle(ChatFormatting.GRAY)));
        
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
        
        // Footer
        player.sendSystemMessage(Component.literal("===========================")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        
        return 1;
    }
}
