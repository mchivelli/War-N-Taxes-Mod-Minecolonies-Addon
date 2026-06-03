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

public class ColonyActivityTracker {
    
    private static final Logger LOGGER = LogManager.getLogger(ColonyActivityTracker.class);
    
    private static final Map<Integer, ActivityStatus> activityCache = new ConcurrentHashMap<>();
    private static long lastCacheUpdate = 0L;
    private static final long CACHE_VALIDITY_MS = 300000L; // 5 minutes
    
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
    
    /** Returns true if the colony should generate taxes (owner or officer visited recently). */
    public static boolean isColonyActive(IColony colony) {
        if (!TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            return true;
        }
        
        int colonyId = colony.getID();
        ActivityStatus cached = activityCache.get(colonyId);
        
        if (cached != null && !cached.isExpired()) {
            return cached.isActive;
        }

        int lastContactHours = colony.getLastContactInHours();

        // Use officer visit time if more recent than owner visit
        long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colonyId);
        if (officerVisitHours >= 0 && officerVisitHours < lastContactHours) {
            lastContactHours = (int) officerVisitHours;
        }

        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        boolean isActive = lastContactHours < threshold;
        activityCache.put(colonyId, new ActivityStatus(isActive, lastContactHours));
        
        return isActive;
    }
    
    public static ActivityStatus getColonyActivityStatus(IColony colony) {
        if (!TaxConfig.isColonyInactivityTaxPauseEnabled()) {
            return new ActivityStatus(true, colony.getLastContactInHours());
        }

        int colonyId = colony.getID();
        ActivityStatus cached = activityCache.get(colonyId);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        int lastContactHours = colony.getLastContactInHours();
        long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colonyId);
        if (officerVisitHours >= 0 && officerVisitHours < lastContactHours) {
            lastContactHours = (int) officerVisitHours;
        }

        int threshold = TaxConfig.getColonyInactivityHoursThreshold();
        boolean isActive = lastContactHours < threshold;
        ActivityStatus status = new ActivityStatus(isActive, lastContactHours);
        activityCache.put(colonyId, status);
        return status;
    }

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
    
    public static void clearCache() {
        activityCache.clear();
        lastCacheUpdate = System.currentTimeMillis();
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.debug("Colony activity cache cleared");
        }
    }
    
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
    
    public static int getHoursSinceLastContact(IColony colony) {
        return colony.getLastContactInHours();
    }
    
    public static boolean isInactivitySystemEnabled() {
        return TaxConfig.isColonyInactivityTaxPauseEnabled() && 
               TaxConfig.getColonyInactivityHoursThreshold() > 0;
    }
    
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
