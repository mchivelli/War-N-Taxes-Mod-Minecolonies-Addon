package net.machiavelli.minecolonytax.requirements;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.machiavelli.minecolonytax.TaxConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Manages building requirements for raids, wars, and colony claiming.
 * Supports both legacy format (building:level) and new format (building:level:amount).
 */
public class BuildingRequirementsManager {
    
    private static final Logger LOGGER = LogManager.getLogger(BuildingRequirementsManager.class);
    
    /**
     * Result class for building requirement checks.
     */
    public static class RequirementResult {
        public final boolean meetsRequirements;
        public final String message;
        public final List<String> missingRequirements;
        
        public RequirementResult(boolean meetsRequirements, String message, List<String> missingRequirements) {
            this.meetsRequirements = meetsRequirements;
            this.message = message;
            this.missingRequirements = missingRequirements != null ? missingRequirements : new ArrayList<>();
        }
        
        public RequirementResult(boolean meetsRequirements, String message) {
            this(meetsRequirements, message, null);
        }
    }
    
    /**
     * Building requirement specification.
     */
    public static class BuildingRequirement {
        public final String buildingType;
        public final int requiredLevel;
        public final int requiredAmount;
        
        public BuildingRequirement(String buildingType, int requiredLevel, int requiredAmount) {
            this.buildingType = buildingType.toLowerCase();
            this.requiredLevel = requiredLevel;
            this.requiredAmount = requiredAmount;
        }
        
        @Override
        public String toString() {
            if (requiredAmount == 1) {
                return buildingType + " level " + requiredLevel + "+";
            } else {
                return requiredAmount + "x " + buildingType + " level " + requiredLevel + "+";
            }
        }
        
        /**
         * Check if this requirement involves guard-related buildings.
         */
        public boolean isGuardRelated() {
            return buildingType.contains("guard") || buildingType.contains("tower") || 
                   buildingType.contains("barracks") || buildingType.contains("archery") || 
                   buildingType.contains("combat");
        }
    }
    
    /**
     * Check raid building requirements for a colony.
     */
    public static RequirementResult checkRaidRequirements(IColony colony) {
        if (!TaxConfig.isRaidBuildingRequirementsEnabled()) {
            return new RequirementResult(true, "Building requirements disabled for raids.");
        }
        
        String requirements = TaxConfig.getRaidBuildingRequirements();
        return checkBuildingRequirements(colony, requirements, "raid");
    }
    
    /**
     * Check war building requirements for a colony.
     */
    public static RequirementResult checkWarRequirements(IColony colony) {
        if (!TaxConfig.isWarBuildingRequirementsEnabled()) {
            return new RequirementResult(true, "Building requirements disabled for wars.");
        }
        
        String requirements = TaxConfig.getWarBuildingRequirements();
        return checkBuildingRequirements(colony, requirements, "war");
    }
    
    /**
     * Check claiming building requirements for a colony (legacy support).
     */
    public static RequirementResult checkClaimingRequirements(IColony colony) {
        String requirements = TaxConfig.getClaimingBuildingRequirements();
        return checkBuildingRequirements(colony, requirements, "claiming");
    }
    
    /**
     * Check building requirements against a colony.
     */
    private static RequirementResult checkBuildingRequirements(IColony colony, String requirements, String actionType) {
        LOGGER.info("Checking building requirements for {}: '{}' (Colony ID: {})", actionType, requirements, colony.getID());
        
        if (requirements == null || requirements.trim().isEmpty()) {
            LOGGER.info("No building requirements configured for {}", actionType);
            return new RequirementResult(true, "No building requirements configured for " + actionType + ".");
        }
        
        try {
            List<BuildingRequirement> parsedRequirements = parseRequirements(requirements);
            LOGGER.info("Parsed {} requirements: {}", parsedRequirements.size(), parsedRequirements);
            
            List<String> missingRequirements = new ArrayList<>();
            
            for (BuildingRequirement requirement : parsedRequirements) {
                LOGGER.info("Checking requirement: {} level {}+ (need {})", 
                           requirement.buildingType, requirement.requiredLevel, requirement.requiredAmount);
                           
                int availableCount = countBuildingsOfTypeAndLevel(colony, requirement.buildingType, requirement.requiredLevel);
                
                LOGGER.info("Available count for {} level {}+: {}", 
                           requirement.buildingType, requirement.requiredLevel, availableCount);
                
                if (availableCount < requirement.requiredAmount) {
                    String missing = requirement.toString() + " (current: " + availableCount + ")";
                    missingRequirements.add(missing);
                    LOGGER.warn("Missing requirement: {}", missing);
                } else {
                    LOGGER.info("Requirement met: {} (has {} out of {} required)", 
                               requirement, availableCount, requirement.requiredAmount);
                }
            }
            
            if (missingRequirements.isEmpty()) {
                LOGGER.info("All building requirements met for {}", actionType);
                return new RequirementResult(true, "All building requirements met for " + actionType + ".");
            } else {
                String message = "Missing building requirements for " + actionType + ": " + String.join(", ", missingRequirements);
                LOGGER.warn("Building requirements check failed: {}", message);
                return new RequirementResult(false, message, missingRequirements);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error checking building requirements for {}: {}", actionType, requirements, e);
            LOGGER.error("Exception details: ", e);
            // CRITICAL: This was allowing actions even when there was an error!
            // We should fail-safe by denying the action when there's an error
            return new RequirementResult(false, "Error checking building requirements - denying " + actionType + " for safety.");
        }
    }
    
    /**
     * Parse building requirements string into list of requirements.
     * Supports both formats:
     * - Legacy: "building:level" (assumes amount = 1)
     * - New: "building:level:amount"
     */
    private static List<BuildingRequirement> parseRequirements(String requirements) {
        List<BuildingRequirement> result = new ArrayList<>();
        
        if (requirements == null || requirements.trim().isEmpty()) {
            return result;
        }
        
        String[] pairs = requirements.split(",");
        for (String pair : pairs) {
            String[] parts = pair.trim().split(":");
            if (parts.length < 2 || parts.length > 3) {
                LOGGER.warn("Invalid building requirement format: {}", pair);
                continue;
            }
            
            try {
                String buildingType = parts[0].trim();
                int level = Integer.parseInt(parts[1].trim());
                int amount = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 1;
                
                result.add(new BuildingRequirement(buildingType, level, amount));
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid number in building requirement: {}", pair);
            }
        }
        
        return result;
    }
    
    /**
     * Count buildings of a specific type that meet the minimum level requirement.
     */
    private static int countBuildingsOfTypeAndLevel(IColony colony, String buildingType, int minLevel) {
        int count = 0;
        
        try {
            LOGGER.debug("Checking building requirements for colony {}: {} level {}+", 
                        colony.getID(), buildingType, minLevel);
                        
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                String actualBuildingType = building.getBuildingType().getRegistryName().getPath().toLowerCase();
                int buildingLevel = building.getBuildingLevel();
                
                LOGGER.debug("Found building: {} level {}", actualBuildingType, buildingLevel);
                
                if (matchesBuildingType(actualBuildingType, buildingType)) {
                    LOGGER.debug("Building type {} matches requirement {}", actualBuildingType, buildingType);
                    
                    if (buildingLevel >= minLevel) {
                        count++;
                        LOGGER.debug("Building meets level requirement. Count: {}", count);
                    } else {
                        LOGGER.debug("Building level {} does not meet minimum requirement {}", buildingLevel, minLevel);
                    }
                } else {
                    LOGGER.debug("Building type {} does not match requirement {}", actualBuildingType, buildingType);
                }
            }
            
            LOGGER.debug("Final count for {} level {}+: {}", buildingType, minLevel, count);
        } catch (Exception e) {
            LOGGER.error("Error counting buildings of type {} for colony {}", buildingType, colony.getID(), e);
        }
        
        return count;
    }
    
    /**
     * Check if a building type matches the requirement.
     * Handles common aliases and variations.
     */
    private static boolean matchesBuildingType(String actualType, String requiredType) {
        // Direct match
        if (actualType.equals(requiredType)) {
            return true;
        }
        
        // Handle common aliases
        return switch (requiredType) {
            case "townhall" -> actualType.contains("townhall");
            case "guardtower" -> actualType.contains("guardtower") || actualType.contains("guard");
            case "buildershut", "buildershop" -> actualType.contains("builder");
            case "house", "residential" -> actualType.contains("house") || actualType.contains("residence") || actualType.contains("citizen");
            case "barracks" -> actualType.contains("barracks");
            case "archery" -> actualType.contains("archery");
            case "combatacademy" -> actualType.contains("combat");
            case "mine" -> actualType.contains("mine");
            case "farm" -> actualType.contains("farm");
            case "warehouse" -> actualType.contains("warehouse");
            case "deliveryman" -> actualType.contains("deliveryman");
            default -> actualType.contains(requiredType);
        };
    }
    
    /**
     * Get a formatted list of requirements for display purposes.
     */
    public static List<String> getFormattedRequirements(String requirements) {
        List<String> formatted = new ArrayList<>();
        
        try {
            List<BuildingRequirement> parsed = parseRequirements(requirements);
            for (BuildingRequirement req : parsed) {
                formatted.add(req.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Error formatting requirements: {}", requirements, e);
            formatted.add("Error parsing requirements");
        }
        
        return formatted;
    }
}
