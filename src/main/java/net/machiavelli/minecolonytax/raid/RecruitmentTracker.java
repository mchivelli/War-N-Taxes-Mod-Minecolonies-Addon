package net.machiavelli.minecolonytax.raid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks recruitment times for entities to provide accurate grace period detection.
 * This system helps distinguish between newly recruited entities and existing ones.
 */
public class RecruitmentTracker {
    
    private static final Logger LOGGER = LogManager.getLogger(RecruitmentTracker.class);
    
    // Map of entity UUID to recruitment timestamp
    private static final ConcurrentMap<UUID, Long> recruitmentTimes = new ConcurrentHashMap<>();
    
    // Cleanup interval - remove entries older than 30 seconds to prevent memory leaks
    private static final long CLEANUP_THRESHOLD_MS = 30000L; // 30 seconds
    private static long lastCleanupTime = System.currentTimeMillis();
    
    /**
     * Record the recruitment time for an entity
     */
    public static void recordRecruitment(UUID entityUUID) {
        if (entityUUID != null) {
            long currentTime = System.currentTimeMillis();
            recruitmentTimes.put(entityUUID, currentTime);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Recorded recruitment time for entity {}: {}", 
                        entityUUID.toString().substring(0, 8) + "...", currentTime);
            }
        }
    }
    
    /**
     * Get the recruitment time for an entity
     * @param entityUUID The entity's UUID
     * @return The recruitment timestamp in milliseconds, or null if not tracked
     */
    public static Long getRecruitmentTime(UUID entityUUID) {
        if (entityUUID == null) {
            return null;
        }
        
        // Perform periodic cleanup
        performPeriodicCleanup();
        
        return recruitmentTimes.get(entityUUID);
    }
    
    /**
     * Check if an entity was recently recruited (within the specified time)
     * @param entityUUID The entity's UUID
     * @param gracePeriodMs The grace period in milliseconds
     * @return true if the entity was recruited within the grace period
     */
    public static boolean isRecentlyRecruited(UUID entityUUID, long gracePeriodMs) {
        Long recruitmentTime = getRecruitmentTime(entityUUID);
        if (recruitmentTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceRecruitment = currentTime - recruitmentTime;
        
        return timeSinceRecruitment < gracePeriodMs;
    }
    
    /**
     * Remove tracking for an entity (called when entity is removed or no longer relevant)
     */
    public static void removeTracking(UUID entityUUID) {
        if (entityUUID != null) {
            recruitmentTimes.remove(entityUUID);
        }
    }
    
    /**
     * Perform periodic cleanup to prevent memory leaks
     */
    private static void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        
        // Only cleanup every 30 seconds
        if (currentTime - lastCleanupTime < CLEANUP_THRESHOLD_MS) {
            return;
        }
        
        lastCleanupTime = currentTime;
        
        // Remove entries older than the cleanup threshold
        recruitmentTimes.entrySet().removeIf(entry -> {
            long age = currentTime - entry.getValue();
            return age > CLEANUP_THRESHOLD_MS;
        });
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Performed recruitment tracker cleanup. Remaining entries: {}", 
                    recruitmentTimes.size());
        }
    }
    
    /**
     * Get the current number of tracked entities (for debugging)
     */
    public static int getTrackedEntityCount() {
        performPeriodicCleanup();
        return recruitmentTimes.size();
    }
    
    /**
     * Clear all tracking data (for testing or reset purposes)
     */
    public static void clearAll() {
        recruitmentTimes.clear();
        LOGGER.info("Cleared all recruitment tracking data");
    }
}