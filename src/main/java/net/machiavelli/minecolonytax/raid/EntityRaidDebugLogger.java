package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Comprehensive debug logging system for EntityRaid functionality.
 * Provides structured logging with different verbosity levels to help diagnose
 * issues with entity detection, filtering, and raid triggering.
 */
public class EntityRaidDebugLogger {

    private static final Logger LOGGER = LogManager.getLogger(EntityRaidDebugLogger.class);
    
    // Debug level constants
    public static final int LEVEL_BASIC = 1;
    public static final int LEVEL_DETAILED = 2;
    public static final int LEVEL_VERBOSE = 3;

    /**
     * Log entity detection results for a colony
     */
    public static void logEntityDetection(IColony colony, List<Entity> entities) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        if (colony == null || entities == null) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_BASIC) {
            LOGGER.info("[EntityRaid-DETECTION] Colony: {} | Entities Found: {} | Owner: {}", 
                    colony.getName(), 
                    entities.size(),
                    getOwnerName(colony));
        }
        
        if (debugLevel >= LEVEL_DETAILED) {
            for (Entity entity : entities) {
                LOGGER.info("[EntityRaid-DETECTION] Colony: {} | Entity: {} | Position: {} | UUID: {}", 
                        colony.getName(),
                        entity.getType().getDescriptionId(),
                        entity.blockPosition(),
                        entity.getUUID());
            }
        }
    }

    /**
     * Log each filter step during entity processing
     */
    public static void logFilterStep(Entity entity, IColony colony, String filterName, boolean passed, String reason) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        if (entity == null || colony == null) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_DETAILED) {
            String result = passed ? "PASS" : "FAIL";
            LOGGER.info("[EntityRaid-FILTER] Colony: {} | Entity: {} | Filter: {} | Result: {} | Reason: {}", 
                    colony.getName(),
                    entity.getType().getDescriptionId(),
                    filterName,
                    result,
                    reason);
        }
    }

    /**
     * Log alliance detection results
     */
    public static void logAllianceCheck(Entity entity, IColony colony, boolean isAllied, String method) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        if (entity == null || colony == null) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_DETAILED) {
            LOGGER.info("[EntityRaid-ALLIANCE] Colony: {} | Entity: {} | Allied: {} | Method: {}", 
                    colony.getName(),
                    entity.getType().getDescriptionId(),
                    isAllied,
                    method);
        }
    }

    /**
     * Log prerequisite validation checks
     */
    public static void logPrerequisiteCheck(IColony colony, String requirement, boolean met, String details) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        if (colony == null) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_BASIC) {
            String result = met ? "MET" : "NOT MET";
            LOGGER.info("[EntityRaid-PREREQUISITE] Colony: {} | Requirement: {} | Status: {} | Details: {}", 
                    colony.getName(),
                    requirement,
                    result,
                    details);
        }
    }

    /**
     * Log entity type detection results
     */
    public static void logEntityTypeDetection(Entity entity, boolean isRecruit, String detectionMethod) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_VERBOSE) {
            LOGGER.info("[EntityRaid-TYPE] Entity: {} | IsRecruit: {} | Method: {} | Class: {}", 
                    entity.getType().getDescriptionId(),
                    isRecruit,
                    detectionMethod,
                    entity.getClass().getSimpleName());
        }
    }

    /**
     * Log ownership detection results
     */
    public static void logOwnershipCheck(Entity entity, UUID entityOwner, UUID colonyOwner, boolean isOwned) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_DETAILED) {
            LOGGER.info("[EntityRaid-OWNERSHIP] Entity: {} | EntityOwner: {} | ColonyOwner: {} | IsOwned: {}", 
                    entity.getType().getDescriptionId(),
                    entityOwner != null ? entityOwner.toString().substring(0, 8) + "..." : "null",
                    colonyOwner != null ? colonyOwner.toString().substring(0, 8) + "..." : "null",
                    isOwned);
        }
    }

    /**
     * Log grace period calculations
     */
    public static void logGracePeriodCheck(Entity entity, long entityAge, boolean isRecent) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_DETAILED) {
            LOGGER.info("[EntityRaid-GRACE] Entity: {} | Age: {}ms | IsRecent: {} | Threshold: 10000ms", 
                    entity.getType().getDescriptionId(),
                    entityAge,
                    isRecent);
        }
    }

    /**
     * Log boundary detection results
     */
    public static void logBoundaryCheck(Entity entity, IColony colony, boolean insideBoundary) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_VERBOSE) {
            LOGGER.info("[EntityRaid-BOUNDARY] Colony: {} | Entity: {} | Position: {} | InsideBoundary: {}", 
                    colony.getName(),
                    entity.getType().getDescriptionId(),
                    entity.blockPosition(),
                    insideBoundary);
        }
    }

    /**
     * Log raid triggering events
     */
    public static void logRaidTrigger(IColony colony, int entityCount, int threshold) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_BASIC) {
            LOGGER.info("[EntityRaid-TRIGGER] Colony: {} | EntityCount: {} | Threshold: {} | Owner: {} | RAID TRIGGERED", 
                    colony.getName(),
                    entityCount,
                    threshold,
                    getOwnerName(colony));
        }
    }

    /**
     * Log raid state changes
     */
//    public static void logRaidStateChange(IColony colony, EntityRaidManager.RaidState oldState, EntityRaidManager.RaidState newState, String reason) {
//        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
//        
//        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
//        if (debugLevel >= LEVEL_BASIC) {
//            LOGGER.info("[EntityRaid-STATE] Colony: {} | StateChange: {} -> {} | Reason: {}", 
//                    colony.getName(),
//                    oldState != null ? oldState.name() : "NONE",
//                    newState.name(),
//                    reason);
//        }
//    }

    /**
     * Log performance metrics
     */
    public static void logPerformanceMetrics(String operation, long durationMs, int entitiesProcessed) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_VERBOSE) {
            LOGGER.info("[EntityRaid-PERFORMANCE] Operation: {} | Duration: {}ms | EntitiesProcessed: {} | AvgPerEntity: {}ms", 
                    operation,
                    durationMs,
                    entitiesProcessed,
                    entitiesProcessed > 0 ? (durationMs / entitiesProcessed) : 0);
        }
    }

    /**
     * Log reflection-based method calls and their results
     */
    public static void logReflectionCall(String methodName, String className, boolean success, String result) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_VERBOSE) {
            LOGGER.info("[EntityRaid-REFLECTION] Method: {} | Class: {} | Success: {} | Result: {}", 
                    methodName,
                    className,
                    success,
                    result);
        }
    }

    /**
     * Log error conditions and exceptions
     */
    public static void logError(String operation, String error, Exception exception) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        // Always log errors regardless of debug level
        if (exception != null) {
            LOGGER.error("[EntityRaid-ERROR] Operation: {} | Error: {} | Exception: {}", 
                    operation, error, exception.getMessage());
        } else {
            LOGGER.error("[EntityRaid-ERROR] Operation: {} | Error: {}", operation, error);
        }
    }

    /**
     * Log cooldown and timing information
     */
    public static void logCooldownCheck(IColony colony, long cooldownEnd, boolean onCooldown) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_DETAILED) {
            long currentTime = System.currentTimeMillis();
            long remainingMs = Math.max(0, cooldownEnd - currentTime);
            LOGGER.info("[EntityRaid-COOLDOWN] Colony: {} | OnCooldown: {} | RemainingMs: {} | CooldownEnd: {}", 
                    colony.getName(),
                    onCooldown,
                    remainingMs,
                    cooldownEnd);
        }
    }

    /**
     * Log entity filtering pipeline summary
     */
    public static void logFilteringSummary(IColony colony, int totalEntities, int passedFiltering, int threshold) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_BASIC) {
            boolean wouldTrigger = passedFiltering >= threshold;
            LOGGER.info("[EntityRaid-SUMMARY] Colony: {} | TotalEntities: {} | PassedFiltering: {} | Threshold: {} | WouldTrigger: {}", 
                    colony.getName(),
                    totalEntities,
                    passedFiltering,
                    threshold,
                    wouldTrigger);
        }
    }

    /**
     * Helper method to get owner name for logging
     */
    private static String getOwnerName(IColony colony) {
        UUID ownerUUID = colony.getPermissions().getOwner();
        if (ownerUUID == null) {
            return "NO_OWNER";
        }
        
        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
            ServerPlayer owner = colony.getWorld().getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                return owner.getName().getString();
            }
        }
        
        return ownerUUID.toString().substring(0, 8) + "...";
    }

    /**
     * Log debug session start
     */
    public static void logDebugSessionStart() {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        LOGGER.info("[EntityRaid-DEBUG] ========== EntityRaid Debug Session Started ==========");
        LOGGER.info("[EntityRaid-DEBUG] Debug Level: {} | Enabled Features: Detection, Filtering, Alliance, Performance", 
                TaxConfig.getEntityRaidDebugLevel());
    }

    /**
     * Log debug session end
     */
    public static void logDebugSessionEnd() {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        LOGGER.info("[EntityRaid-DEBUG] ========== EntityRaid Debug Session Ended ==========");
    }
    
    /**
     * Log detailed filter pipeline statistics
     */
    public static void logFilterPipelineStats(IColony colony, int totalEntities, int passedWhitelist, 
                                            int passedRecruitFilter, int finalAccepted) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_DETAILED) {
            LOGGER.info("[EntityRaid-PIPELINE] Colony: {} | Total: {} | Whitelist: {} | RecruitFilter: {} | Final: {} | Owner: {}", 
                    colony.getName(),
                    totalEntities,
                    passedWhitelist,
                    passedRecruitFilter,
                    finalAccepted,
                    getOwnerName(colony));
        }
    }
    
    /**
     * Log filter step with timing information
     */
    public static void logFilterStepTimed(Entity entity, IColony colony, String filterName, 
                                        boolean passed, String reason, long durationMs) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_VERBOSE) {
            String result = passed ? "PASS" : "FAIL";
            LOGGER.info("[EntityRaid-FILTER-TIMED] Colony: {} | Entity: {} | Filter: {} | Result: {} | Duration: {}ms | Reason: {}", 
                    colony != null ? colony.getName() : "N/A",
                    entity.getType().getDescriptionId(),
                    filterName,
                    result,
                    durationMs,
                    reason);
        }
    }
    
    /**
     * Log comprehensive entity analysis
     */
    public static void logEntityAnalysis(Entity entity, IColony colony, String analysis) {
        if (!TaxConfig.isEntityRaidDebugEnabled()) return;
        
        int debugLevel = TaxConfig.getEntityRaidDebugLevel();
        if (debugLevel >= LEVEL_VERBOSE) {
            LOGGER.info("[EntityRaid-ANALYSIS] Colony: {} | Entity: {} | UUID: {} | Analysis: {}", 
                    colony != null ? colony.getName() : "N/A",
                    entity.getType().getDescriptionId(),
                    entity.getUUID().toString().substring(0, 8) + "...",
                    analysis);
        }
    }
}