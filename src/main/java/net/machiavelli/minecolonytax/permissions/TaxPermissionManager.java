package net.machiavelli.minecolonytax.permissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.machiavelli.minecolonytax.TaxConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tax claim permissions for colonies.
 * Supports colony-wide officer permissions and per-player overrides.
 * Owners always bypass all restrictions.
 */
public class TaxPermissionManager {
    private static final Logger LOGGER = LogManager.getLogger(TaxPermissionManager.class);

    private static final Map<Integer, Boolean> OFFICER_CLAIM_PERMISSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<UUID, Boolean>> INDIVIDUAL_CLAIM_PERMISSIONS = new ConcurrentHashMap<>();

    public static boolean canPlayerClaimTax(int colonyId, UUID playerId, boolean isOwner, boolean isOfficer) {
        // Former owner is locked out while their colony is besieged; besieger collects via VassalManager.
        if (TaxConfig.isBesiegeSystemEnabled()
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId)
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.shouldBlockInteraction(playerId, colonyId)) {
            return false;
        }

        if (isOwner) {
            return true;
        }

        if (isOfficer) {
            Map<UUID, Boolean> colonyIndividualPerms = INDIVIDUAL_CLAIM_PERMISSIONS.get(colonyId);
            if (colonyIndividualPerms != null && colonyIndividualPerms.containsKey(playerId)) {
                return colonyIndividualPerms.get(playerId);
            }

            return canOfficersClaim(colonyId);
        }

        return false;
    }

    public static boolean canOfficersClaim(int colonyId) {
        return OFFICER_CLAIM_PERMISSIONS.getOrDefault(colonyId, true);
    }

    public static void setPlayerClaimPermission(int colonyId, UUID playerId, boolean allowed) {
        INDIVIDUAL_CLAIM_PERMISSIONS.computeIfAbsent(colonyId, k -> new ConcurrentHashMap<>())
                .put(playerId, allowed);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Colony {} player {} tax claim permission set to: {}", colonyId, playerId, allowed);
    }

    /** Toggles the individual claim permission and returns the new state. */
    public static boolean togglePlayerClaimPermission(int colonyId, UUID playerId, boolean isOfficer) {
        boolean currentPermission;
        Map<UUID, Boolean> colonyIndividualPerms = INDIVIDUAL_CLAIM_PERMISSIONS.get(colonyId);
        if (colonyIndividualPerms != null && colonyIndividualPerms.containsKey(playerId)) {
            currentPermission = colonyIndividualPerms.get(playerId);
        } else {
            currentPermission = isOfficer && canOfficersClaim(colonyId);
        }

        boolean newState = !currentPermission;
        setPlayerClaimPermission(colonyId, playerId, newState);
        return newState;
    }

    public static void setOfficerClaimPermission(int colonyId, boolean allowed) {
        OFFICER_CLAIM_PERMISSIONS.put(colonyId, allowed);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Colony {} officer tax claim permission set to: {}", colonyId, allowed);
    }

    public static boolean toggleOfficerClaimPermission(int colonyId) {
        boolean newState = !canOfficersClaim(colonyId);
        setOfficerClaimPermission(colonyId, newState);
        return newState;
    }

    public static void removePlayerPermission(int colonyId, UUID playerId) {
        Map<UUID, Boolean> colonyIndividualPerms = INDIVIDUAL_CLAIM_PERMISSIONS.get(colonyId);
        if (colonyIndividualPerms != null) {
            colonyIndividualPerms.remove(playerId);
            if (colonyIndividualPerms.isEmpty()) {
                INDIVIDUAL_CLAIM_PERMISSIONS.remove(colonyId);
            }
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Removed individual tax permission for player {} in colony {}", playerId, colonyId);
        }
    }

    public static void clearAllPermissions() {
        OFFICER_CLAIM_PERMISSIONS.clear();
        INDIVIDUAL_CLAIM_PERMISSIONS.clear();
        if (TaxConfig.isNormalLogging())
            LOGGER.info("All tax permissions cleared");
    }

    public static Map<Integer, Boolean> getAllPermissions() {
        return new ConcurrentHashMap<>(OFFICER_CLAIM_PERMISSIONS);
    }

    public static Map<Integer, Map<UUID, Boolean>> getAllIndividualPermissions() {
        return new ConcurrentHashMap<>(INDIVIDUAL_CLAIM_PERMISSIONS);
    }

    public static void loadPermissions(Map<Integer, Boolean> permissions) {
        OFFICER_CLAIM_PERMISSIONS.clear();
        OFFICER_CLAIM_PERMISSIONS.putAll(permissions);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Loaded {} tax permission entries", permissions.size());
    }

    public static void loadIndividualPermissions(Map<Integer, Map<UUID, Boolean>> permissions) {
        INDIVIDUAL_CLAIM_PERMISSIONS.clear();
        INDIVIDUAL_CLAIM_PERMISSIONS.putAll(permissions);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Loaded {} individual tax permission entries", permissions.size());
    }
}
