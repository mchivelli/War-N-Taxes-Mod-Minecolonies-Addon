package net.machiavelli.minecolonytax.permissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tax claim permissions for officers in colonies.
 * Colony owners can restrict officer access to tax claiming via GUI.
 * Supports both colony-wide and individual player permissions.
 */
public class TaxPermissionManager {
    private static final Logger LOGGER = LogManager.getLogger(TaxPermissionManager.class);
    
    /** Colony ID -> whether officers can claim taxes (default: true) */
    private static final Map<Integer, Boolean> OFFICER_CLAIM_PERMISSIONS = new ConcurrentHashMap<>();
    
    /** Colony ID -> Player ID -> whether this specific player can claim taxes */
    private static final Map<Integer, Map<UUID, Boolean>> INDIVIDUAL_CLAIM_PERMISSIONS = new ConcurrentHashMap<>();
    
    /**
     * Check if a specific player can claim taxes from a colony.
     * Considers both individual and colony-wide permissions with proper hierarchy.
     * Owners always override officer restrictions.
     */
    public static boolean canPlayerClaimTax(int colonyId, UUID playerId, boolean isOwner, boolean isOfficer) {
        // Owners always can claim taxes regardless of restrictions
        if (isOwner) {
            return true;
        }
        
        // For officers, check individual permission first, then fall back to colony-wide
        if (isOfficer) {
            // Check if there's an individual permission set for this player
            Map<UUID, Boolean> colonyIndividualPerms = INDIVIDUAL_CLAIM_PERMISSIONS.get(colonyId);
            if (colonyIndividualPerms != null && colonyIndividualPerms.containsKey(playerId)) {
                return colonyIndividualPerms.get(playerId);
            }
            
            // Fall back to colony-wide officer permission
            return canOfficersClaim(colonyId);
        }
        
        // Non-officers (friends, neutrals, etc.) cannot claim taxes
        return false;
    }
    
    /**
     * Check if officers can claim taxes from a specific colony (legacy method)
     */
    public static boolean canOfficersClaim(int colonyId) {
        // Default to true (officers can claim) if not explicitly set
        return OFFICER_CLAIM_PERMISSIONS.getOrDefault(colonyId, true);
    }
    
    /**
     * Set whether a specific player can claim taxes from a colony
     * Only colony owners should be able to call this
     */
    public static void setPlayerClaimPermission(int colonyId, UUID playerId, boolean allowed) {
        INDIVIDUAL_CLAIM_PERMISSIONS.computeIfAbsent(colonyId, k -> new ConcurrentHashMap<>())
                                   .put(playerId, allowed);
        LOGGER.info("Colony {} player {} tax claim permission set to: {}", colonyId, playerId, allowed);
    }
    
    /**
     * Toggle individual player claim permission for a colony
     * Returns new permission state
     */
    public static boolean togglePlayerClaimPermission(int colonyId, UUID playerId, boolean isOfficer) {
        // Get current permission state
        boolean currentPermission;
        Map<UUID, Boolean> colonyIndividualPerms = INDIVIDUAL_CLAIM_PERMISSIONS.get(colonyId);
        if (colonyIndividualPerms != null && colonyIndividualPerms.containsKey(playerId)) {
            currentPermission = colonyIndividualPerms.get(playerId);
        } else {
            // Fall back to colony-wide permission if no individual setting
            currentPermission = isOfficer ? canOfficersClaim(colonyId) : false;
        }
        
        boolean newState = !currentPermission;
        setPlayerClaimPermission(colonyId, playerId, newState);
        return newState;
    }
    
    /**
     * Set whether officers can claim taxes from a specific colony (legacy method)
     * Only colony owners should be able to call this
     */
    public static void setOfficerClaimPermission(int colonyId, boolean allowed) {
        OFFICER_CLAIM_PERMISSIONS.put(colonyId, allowed);
        LOGGER.info("Colony {} officer tax claim permission set to: {}", colonyId, allowed);
    }
    
    /**
     * Toggle officer claim permission for a colony (legacy method)
     * Returns new permission state
     */
    public static boolean toggleOfficerClaimPermission(int colonyId) {
        boolean newState = !canOfficersClaim(colonyId);
        setOfficerClaimPermission(colonyId, newState);
        return newState;
    }
    
    /**
     * Remove individual permission for a player (reverts to colony-wide setting)
     */
    public static void removePlayerPermission(int colonyId, UUID playerId) {
        Map<UUID, Boolean> colonyIndividualPerms = INDIVIDUAL_CLAIM_PERMISSIONS.get(colonyId);
        if (colonyIndividualPerms != null) {
            colonyIndividualPerms.remove(playerId);
            if (colonyIndividualPerms.isEmpty()) {
                INDIVIDUAL_CLAIM_PERMISSIONS.remove(colonyId);
            }
            LOGGER.info("Removed individual tax permission for player {} in colony {}", playerId, colonyId);
        }
    }
    
    /**
     * Clear all permissions (for server shutdown/reload)
     */
    public static void clearAllPermissions() {
        OFFICER_CLAIM_PERMISSIONS.clear();
        INDIVIDUAL_CLAIM_PERMISSIONS.clear();
        LOGGER.info("All tax permissions cleared");
    }
    
    /**
     * Get all current permissions (for data persistence if needed)
     */
    public static Map<Integer, Boolean> getAllPermissions() {
        return new ConcurrentHashMap<>(OFFICER_CLAIM_PERMISSIONS);
    }
    
    /**
     * Get all individual permissions (for data persistence if needed)
     */
    public static Map<Integer, Map<UUID, Boolean>> getAllIndividualPermissions() {
        return new ConcurrentHashMap<>(INDIVIDUAL_CLAIM_PERMISSIONS);
    }
    
    /**
     * Load permissions from data (for server startup if persistence is added)
     */
    public static void loadPermissions(Map<Integer, Boolean> permissions) {
        OFFICER_CLAIM_PERMISSIONS.clear();
        OFFICER_CLAIM_PERMISSIONS.putAll(permissions);
        LOGGER.info("Loaded {} tax permission entries", permissions.size());
    }
    
    /**
     * Load individual permissions from data (for server startup if persistence is added)
     */
    public static void loadIndividualPermissions(Map<Integer, Map<UUID, Boolean>> permissions) {
        INDIVIDUAL_CLAIM_PERMISSIONS.clear();
        INDIVIDUAL_CLAIM_PERMISSIONS.putAll(permissions);
        LOGGER.info("Loaded {} individual tax permission entries", permissions.size());
    }
}
