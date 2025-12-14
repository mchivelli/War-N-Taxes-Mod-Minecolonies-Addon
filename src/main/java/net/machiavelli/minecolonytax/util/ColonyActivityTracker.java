package net.machiavelli.minecolonytax.util;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for tracking colony activity and providing centralized methods
 * for activity-related operations across the tax system.
 */
public class ColonyActivityTracker {
    
    private static final Logger LOGGER = LogManager.getLogger(ColonyActivityTracker.class);
    
    // Cache for recently computed activity status to improve performance
    private static final Map<Integer, ActivityStatus> activityCache = new ConcurrentHashMap<>();
    private static long lastCacheUpdate = 0L;
    private static final long CACHE_VALIDITY_MS = 300000L; // 5 minutes
    
    /**
     * Data class to hold colony activity information.
     */
    public static class ActivityStatus {
        public final boolean isActive;
        public final int lastContactHours;
        public final long timestamp;
        
        public ActivityStatus(boolean isActive, int lastContactHours) {
            this.isActive = isActive;
            this.lastContactHours = lastContactHours;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_VALIDITY_MS;
        }
    }
    
    /**
     * Check if a specific colony is currently active based on inactivity settings.
     * ENHANCED: Now considers officer visits in addition to owner visits.
     * 
     * @param colony The colony to check
     * @return true if the colony is active (should generate taxes), false if inactive
     */
    public static boolean isColonyActive(IColony colony) {
        if (!TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            return true; // System disabled, all colonies are considered active
        }
        
        int colonyId = colony.getID();
        ActivityStatus cached = activityCache.get(colonyId);
        
        // Use cached result if still valid
        if (cached != null && !cached.isExpired()) {
            return cached.isActive;
        }
        
        // Calculate fresh activity status
        int lastContactHours = colony.getLastContactInHours();
        
        // CRITICAL FIX: Check if officers have visited recently
        long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colonyId);
        if (officerVisitHours >= 0 && officerVisitHours < lastContactHours) {
            // Officers visited more recently - use that time
            lastContactHours = (int) officerVisitHours;
        }
        
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        boolean isActive = lastContactHours < threshold;
        
        // Cache the result
        activityCache.put(colonyId, new ActivityStatus(isActive, lastContactHours));
        
        return isActive;
    }
    
    /**
     * Get detailed activity status for a colony.
     * ENHANCED: Now considers officer visits in addition to owner visits.
     * 
     * @param colony The colony to check
     * @return ActivityStatus containing detailed information
     */
    public static ActivityStatus getColonyActivityStatus(IColony colony) {
        if (!TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            return new ActivityStatus(true, colony.getLastContactInHours());
        }
        
        int colonyId = colony.getID();
        ActivityStatus cached = activityCache.get(colonyId);
        
        // Use cached result if still valid
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        
        // Calculate fresh activity status
        int lastContactHours = colony.getLastContactInHours();
        
        // CRITICAL FIX: Check if officers have visited recently
        long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colonyId);
        if (officerVisitHours >= 0 && officerVisitHours < lastContactHours) {
            // Officers visited more recently - use that time
            lastContactHours = (int) officerVisitHours;
        }
        
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        boolean isActive = lastContactHours < threshold;
        
        ActivityStatus status = new ActivityStatus(isActive, lastContactHours);
        activityCache.put(colonyId, status);
        
        return status;
    }
    
    /**
     * Get activity statistics for all colonies across all worlds.
     * 
     * @param server The server instance to scan
     * @return Map containing activity statistics
     */
    public static Map<String, Integer> getGlobalActivityStatistics(net.minecraft.server.MinecraftServer server) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("active", 0);
        stats.put("inactive", 0);
        
        if (server == null) {
            return stats;
        }
        
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        boolean systemEnabled = TaxConfig.isColonyInactivityTaxPauseEnabled();
        
        for (Level world : server.getAllLevels()) {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            for (IColony colony : colonyManager.getColonies(world)) {
                stats.put("total", stats.get("total") + 1);
                
                if (!systemEnabled || colony.getLastContactInHours() < threshold) {
                    stats.put("active", stats.get("active") + 1);
                } else {
                    stats.put("inactive", stats.get("inactive") + 1);
                }
            }
        }
        
        return stats;
    }
    
    /**
     * Get a list of all inactive colonies across all worlds.
     * 
     * @param server The server instance to scan
     * @return List of inactive colonies
     */
    public static List<IColony> getInactiveColonies(net.minecraft.server.MinecraftServer server) {
        List<IColony> inactiveColonies = new ArrayList<>();
        
        if (server == null || !TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            return inactiveColonies;
        }
        
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        
        for (Level world : server.getAllLevels()) {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            for (IColony colony : colonyManager.getColonies(world)) {
                if (colony.getLastContactInHours() >= threshold) {
                    inactiveColonies.add(colony);
                }
            }
        }
        
        return inactiveColonies;
    }
    
    /**
     * Clear the activity cache. Useful for forcing fresh calculations.
     */
    public static void clearCache() {
        activityCache.clear();
        lastCacheUpdate = System.currentTimeMillis();
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.debug("Colony activity cache cleared");
        }
    }
    
    /**
     * Clean up expired entries from the activity cache.
     */
    public static void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate > CACHE_VALIDITY_MS) {
            Iterator<Map.Entry<Integer, ActivityStatus>> iterator = activityCache.entrySet().iterator();
            int removedCount = 0;
            
            while (iterator.hasNext()) {
                Map.Entry<Integer, ActivityStatus> entry = iterator.next();
                if (entry.getValue().isExpired()) {
                    iterator.remove();
                    removedCount++;
                }
            }
            
            lastCacheUpdate = currentTime;
            if (removedCount > 0 && TaxConfig.showTaxGenerationLogs()) {
                LOGGER.debug("Cleaned up {} expired entries from colony activity cache", removedCount);
            }
        }
    }
    
    /**
     * Get the number of hours since a colony was last contacted by owners/officers.
     * This is a convenience method that wraps the MineColonies API call.
     * 
     * @param colony The colony to check
     * @return Hours since last contact
     */
    public static int getHoursSinceLastContact(IColony colony) {
        return colony.getLastContactInHours();
    }
    
    /**
     * Check if the inactivity system is enabled and configured properly.
     * 
     * @return true if the system is properly configured and enabled
     */
    public static boolean isInactivitySystemEnabled() {
        return TaxConfig.isColonyInactivityTaxPauseEnabled() && 
               TaxConfig.getColonyInactivityHoursThreshold() > 0;
    }
    
    /**
     * Get a human-readable string describing the colony's activity status.
     * 
     * @param colony The colony to describe
     * @return Activity status description
     */
    public static String getActivityStatusDescription(IColony colony) {
        if (!TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            return "Active (inactivity system disabled)";
        }
        
        int lastContactHours = colony.getLastContactInHours();
        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        
        if (lastContactHours < threshold) {
            return String.format("Active (last contact: %d hours ago)", lastContactHours);
        } else {
            int hoursOverThreshold = lastContactHours - threshold;
            return String.format("Inactive (last contact: %d hours ago, %d hours over threshold)", 
                                lastContactHours, hoursOverThreshold);
        }
    }
}
