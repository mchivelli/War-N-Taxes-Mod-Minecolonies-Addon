package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Monitors colony permissions to detect when officers are added to abandoned colonies.
 * When officers are added to an abandoned colony, it should no longer be considered abandoned.
 */
@Mod.EventBusSubscriber
public class ColonyPermissionMonitor {
    
    private static final Logger LOGGER = LogManager.getLogger(ColonyPermissionMonitor.class);
    
    // Track the last known officer count for each colony
    private static final Map<Integer, Set<UUID>> lastKnownOfficers = new ConcurrentHashMap<>();
    
    // Only check every 5 seconds to avoid performance issues
    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 100; // 5 seconds at 20 TPS
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        
        // Check all colonies for officer changes
        checkColonyOfficerChanges(event.getServer());
    }
    
    /**
     * Check all colonies for changes in officer status.
     */
    private static void checkColonyOfficerChanges(MinecraftServer server) {
        try {
            com.minecolonies.api.colony.IColonyManager colonyManager = 
                com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager();
            
            for (net.minecraft.world.level.Level world : server.getAllLevels()) {
                for (IColony colony : colonyManager.getColonies(world)) {
                    checkColonyForOfficerChanges(colony);
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("Error checking colony officer changes", e);
        }
    }
    
    /**
     * Check a specific colony for officer changes.
     */
    private static void checkColonyForOfficerChanges(IColony colony) {
        try {
            int colonyId = colony.getID();
            
            // Get current officers
            Set<UUID> currentOfficers = colony.getPermissions().getPlayers().values().stream()
                    .filter(player -> player.getRank().isColonyManager())
                    .map(ColonyPlayer::getID)
                    .collect(Collectors.toSet());
            
            // Get last known officers
            Set<UUID> previousOfficers = lastKnownOfficers.get(colonyId);
            
            // Update the tracking
            lastKnownOfficers.put(colonyId, currentOfficers);
            
            // If we don't have previous data, skip this check
            if (previousOfficers == null) {
                return;
            }
            
            // Check if new officers were added
            boolean newOfficersAdded = currentOfficers.size() > previousOfficers.size() || 
                                     !previousOfficers.containsAll(currentOfficers);
            
            if (newOfficersAdded) {
                LOGGER.info("Detected officer changes in colony {}: {} -> {} officers", 
                    colony.getName(), previousOfficers.size(), currentOfficers.size());
                
                // Check if this colony was abandoned and now has officers
                if (ColonyAbandonmentManager.isColonyAbandoned(colony) && !currentOfficers.isEmpty()) {
                    LOGGER.info("Colony {} was abandoned but now has officers - marking as no longer abandoned", 
                        colony.getName());
                    
                    // Use the new method to handle this
                    ColonyAbandonmentManager.checkForNewOfficers(colony);
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("Error checking colony {} for officer changes", colony.getID(), e);
        }
    }
    
    /**
     * Force refresh the officer tracking for a specific colony.
     * Useful when we know officers have been added via commands.
     */
    public static void refreshColonyOfficerTracking(IColony colony) {
        try {
            Set<UUID> currentOfficers = colony.getPermissions().getPlayers().values().stream()
                    .filter(player -> player.getRank().isColonyManager())
                    .map(ColonyPlayer::getID)
                    .collect(Collectors.toSet());
            
            lastKnownOfficers.put(colony.getID(), currentOfficers);
            
            LOGGER.debug("Refreshed officer tracking for colony {}: {} officers", 
                colony.getName(), currentOfficers.size());
            
        } catch (Exception e) {
            LOGGER.error("Error refreshing officer tracking for colony {}", colony.getID(), e);
        }
    }
    
    /**
     * Clear tracking data for a colony (useful when colony is deleted).
     */
    public static void clearColonyTracking(int colonyId) {
        lastKnownOfficers.remove(colonyId);
    }
}