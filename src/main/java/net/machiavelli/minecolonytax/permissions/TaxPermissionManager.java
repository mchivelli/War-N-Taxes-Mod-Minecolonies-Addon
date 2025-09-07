package net.machiavelli.minecolonytax.permissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tax claim permissions for officers in colonies.
 * Colony owners can restrict officer access to tax claiming via GUI.
 */
public class TaxPermissionManager {
    private static final Logger LOGGER = LogManager.getLogger(TaxPermissionManager.class);
    
    /** Colony ID -> whether officers can claim taxes (default: true) */
    private static final Map<Integer, Boolean> OFFICER_CLAIM_PERMISSIONS = new ConcurrentHashMap<>();
    
    /**
     * Check if officers can claim taxes from a specific colony
     */
    public static boolean canOfficersClaim(int colonyId) {
        // Default to true (officers can claim) if not explicitly set
        return OFFICER_CLAIM_PERMISSIONS.getOrDefault(colonyId, true);
    }
    
    /**
     * Set whether officers can claim taxes from a specific colony
     * Only colony owners should be able to call this
     */
    public static void setOfficerClaimPermission(int colonyId, boolean allowed) {
        OFFICER_CLAIM_PERMISSIONS.put(colonyId, allowed);
        LOGGER.info("Colony {} officer tax claim permission set to: {}", colonyId, allowed);
    }
    
    /**
     * Toggle officer claim permission for a colony
     * Returns new permission state
     */
    public static boolean toggleOfficerClaimPermission(int colonyId) {
        boolean newState = !canOfficersClaim(colonyId);
        setOfficerClaimPermission(colonyId, newState);
        return newState;
    }
    
    /**
     * Clear all permissions (for server shutdown/reload)
     */
    public static void clearAllPermissions() {
        OFFICER_CLAIM_PERMISSIONS.clear();
        LOGGER.info("All tax permissions cleared");
    }
    
    /**
     * Get all current permissions (for data persistence if needed)
     */
    public static Map<Integer, Boolean> getAllPermissions() {
        return new ConcurrentHashMap<>(OFFICER_CLAIM_PERMISSIONS);
    }
    
    /**
     * Load permissions from data (for server startup if persistence is added)
     */
    public static void loadPermissions(Map<Integer, Boolean> permissions) {
        OFFICER_CLAIM_PERMISSIONS.clear();
        OFFICER_CLAIM_PERMISSIONS.putAll(permissions);
        LOGGER.info("Loaded {} tax permission entries", permissions.size());
    }
}
