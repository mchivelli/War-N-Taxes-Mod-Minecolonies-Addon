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

@Mod.EventBusSubscriber
public class AbandonedColonyProtectionHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(AbandonedColonyProtectionHandler.class);
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        Player player = event.getPlayer();
        if (player == null || !(player instanceof ServerPlayer)) return;
        ServerPlayer serverPlayer = (ServerPlayer) player;
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
            if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
                if (isPlayerInActiveClaimingRaid(serverPlayer,
                    IColonyManager.getInstance().getColonyByPosFromWorld((Level) event.getLevel(), event.getPos()))) {
                    return;
                }
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(Component.literal("You cannot interact with blocks in abandoned colonies!")
                        .withStyle(ChatFormatting.RED));
                LOGGER.debug("Blocked block interaction by {} in abandoned colony at {}", 
                    serverPlayer.getName().getString(), event.getPos());
            }
        }
    }
    
    private static boolean isBlockInAbandonedColony(BlockPos pos, Level level, ServerPlayer player) {
        try {
            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
            if (colony == null) {
                return false;
            }
            if (!ColonyAbandonmentManager.isColonyAbandoned(colony)) {
                return false;
            }
            // Admins bypass
            if (player.hasPermissions(2)) {
                return false;
            }
            // The claiming player must be able to interact during their raid
            if (isPlayerInActiveClaimingRaid(player, colony)) {
                return false;
            }
            LOGGER.debug("Blocking {} from modifying blocks in abandoned colony {} at {}",
                player.getName().getString(), colony.getName(), pos);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Error checking abandoned colony protection for position {}", pos, e);
            return false; // Don't block on error
        }
    }
    
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
