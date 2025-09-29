package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles block protection for abandoned colonies.
 * Prevents players from breaking or placing blocks in abandoned colonies.
 */
@Mod.EventBusSubscriber
public class AbandonedColonyProtectionHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(AbandonedColonyProtectionHandler.class);
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        Player player = event.getPlayer();
        if (player == null || !(player instanceof ServerPlayer)) return;
        
        ServerPlayer serverPlayer = (ServerPlayer) player;
        
        // Check if this block is in an abandoned colony
        if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
            event.setCanceled(true);
            serverPlayer.sendSystemMessage(Component.literal("You cannot break blocks in abandoned colonies!")
                    .withStyle(ChatFormatting.RED));
            LOGGER.debug("Blocked block breaking by {} in abandoned colony at {}", 
                player.getName().getString(), event.getPos());
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) event.getEntity();
            
            // Check if this block is in an abandoned colony
            if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(Component.literal("You cannot place blocks in abandoned colonies!")
                        .withStyle(ChatFormatting.RED));
                LOGGER.debug("Blocked block placing by {} in abandoned colony at {}", 
                    serverPlayer.getName().getString(), event.getPos());
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Check if this block is in an abandoned colony
            if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
                // Allow certain interactions during claiming raids
                if (isPlayerInActiveClaimingRaid(serverPlayer, 
                    IColonyManager.getInstance().getColonyByPosFromWorld((Level) event.getLevel(), event.getPos()))) {
                    return; // Allow interactions during claiming raids
                }
                
                // Block the interaction for abandoned colonies
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(Component.literal("You cannot interact with blocks in abandoned colonies!")
                        .withStyle(ChatFormatting.RED));
                LOGGER.debug("Blocked block interaction by {} in abandoned colony at {}", 
                    serverPlayer.getName().getString(), event.getPos());
            }
        }
    }
    
    /**
     * Check if a block position is within an abandoned colony.
     * @param pos The block position
     * @param level The world level
     * @param player The player attempting the action
     * @return true if the block is in an abandoned colony and should be protected
     */
    private static boolean isBlockInAbandonedColony(BlockPos pos, Level level, ServerPlayer player) {
        try {
            // Get the colony at this position
            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
            if (colony == null) {
                return false; // No colony here
            }
            
            // Check if the colony is abandoned
            if (!ColonyAbandonmentManager.isColonyAbandoned(colony)) {
                return false; // Colony is not abandoned
            }
            
            // Check if player is an admin (permission level 2 or higher) - admins can always modify
            if (player.hasPermissions(2)) {
                return false; // Allow admins to modify abandoned colonies
            }
            
            // Check if player has permission to modify blocks in this colony
            // During claiming raids, the claiming player should be able to break blocks
            if (isPlayerInActiveClaimingRaid(player, colony)) {
                return false; // Allow block breaking during claiming raids
            }
            
            // CRITICAL: Block ALL other players from modifying abandoned colonies
            // This includes former owners, officers, and anyone else
            LOGGER.debug("Blocking {} from modifying blocks in abandoned colony {} at {}", 
                player.getName().getString(), colony.getName(), pos);
            
            return true; // Block is in abandoned colony and player doesn't have permission
            
        } catch (Exception e) {
            LOGGER.error("Error checking abandoned colony protection for position {}", pos, e);
            return false; // Don't block on error
        }
    }
    
    /**
     * Check if a player is currently in an active claiming raid for the given colony.
     * @param player The player
     * @param colony The colony
     * @return true if the player is actively claiming this colony
     */
    private static boolean isPlayerInActiveClaimingRaid(ServerPlayer player, IColony colony) {
        try {
            return net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                    .isPlayerInClaimingRaid(player.getUUID(), colony.getID());
        } catch (Exception e) {
            LOGGER.debug("Error checking claiming raid status", e);
            return false;
        }
    }
}