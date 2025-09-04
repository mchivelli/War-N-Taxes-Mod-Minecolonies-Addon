package net.machiavelli.minecolonytax.permissions;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages general colony permissions that allow non-allies to interact with items and blocks
 * within colony boundaries based on configuration settings.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GeneralColonyPermissionsManager {

    private static final Logger LOGGER = LogManager.getLogger(GeneralColonyPermissionsManager.class);
    
    // Track original permissions for each colony to restore them later if needed
    private static final Map<Integer, Map<Action, Boolean>> originalNeutralPermissions = new HashMap<>();
    private static final Map<Integer, Map<Action, Boolean>> originalHostilePermissions = new HashMap<>();
    
    // Track which colonies have general permissions applied
    private static final Map<Integer, Boolean> coloniesWithGeneralPermissions = new HashMap<>();
 
    // Ticker to periodically scan for newly created colonies without referencing MineColonies event classes
    private static int scanTicker = 0;

    /**
     * Apply general colony permissions to all colonies when the server starts
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (TaxConfig.isGeneralItemInteractionsEnabled()) {
            LOGGER.info("Applying general colony permissions to all colonies...");
            applyGeneralPermissionsToAllColonies();
        } else {
            LOGGER.info("General colony permissions are disabled in configuration");
        }
    }

    /**
     * Periodically scan for newly created colonies and apply general permissions.
     * This avoids directly referencing MineColonies-specific events that may differ between versions.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
 
        // Run every 200 ticks (~10 seconds)
        if (++scanTicker < 200) {
            return;
        }
        scanTicker = 0;
 
        if (!TaxConfig.isGeneralItemInteractionsEnabled()) {
            return;
        }
 
        try {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                if (!coloniesWithGeneralPermissions.getOrDefault(colony.getID(), false)) {
                    applyGeneralPermissions(colony);
                    LOGGER.debug("Auto-applied general permissions during periodic scan to colony '{}' (ID: {})", colony.getName(), colony.getID());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during periodic general permissions scan", e);
        }
    }

    /**
     * Apply general permissions to all existing colonies
     */
    public static void applyGeneralPermissionsToAllColonies() {
        if (!TaxConfig.isGeneralItemInteractionsEnabled()) {
            return;
        }

        try {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                applyGeneralPermissions(colony);
            }
            LOGGER.info("General colony permissions applied to {} colonies", IColonyManager.getInstance().getAllColonies().size());
        } catch (Exception e) {
            LOGGER.error("Error applying general permissions to all colonies", e);
        }
    }

    /**
     * Apply general permissions to a specific colony
     */
    public static void applyGeneralPermissions(IColony colony) {
        if (!TaxConfig.isGeneralItemInteractionsEnabled() || colony == null) {
            return;
        }

        try {
            int colonyId = colony.getID();
            if (coloniesWithGeneralPermissions.getOrDefault(colonyId, false)) {
                return; // Already applied
            }

            IPermissions permissions = colony.getPermissions();
            Set<Action> allowedActions = TaxConfig.getGeneralColonyActionSet();
            
            if (allowedActions.isEmpty()) {
                LOGGER.warn("No valid general colony actions configured");
                return;
            }

            // Store original permissions for neutral and hostile ranks before modifying
            storeOriginalPermissions(colony, allowedActions);

            // Apply permissions to neutral rank (strangers/non-allies)
            Rank neutralRank = permissions.getRankNeutral();
            for (Action action : allowedActions) {
                permissions.setPermission(neutralRank, action, true);
            }

            // Apply permissions to hostile rank (enemies) - they should also be able to interact
            Rank hostileRank = permissions.getRankHostile();
            for (Action action : allowedActions) {
                permissions.setPermission(hostileRank, action, true);
            }

            coloniesWithGeneralPermissions.put(colonyId, true);
            
            LOGGER.debug("Applied general permissions to colony '{}' (ID: {}): {}", 
                    colony.getName(), colonyId, allowedActions);
                    
        } catch (Exception e) {
            LOGGER.error("Failed to apply general permissions to colony '{}' (ID: {})", 
                    colony.getName(), colony.getID(), e);
        }
    }

    /**
     * Remove general permissions from a specific colony and restore original permissions
     */
    public static void removeGeneralPermissions(IColony colony) {
        if (colony == null) {
            return;
        }

        try {
            int colonyId = colony.getID();
            if (!coloniesWithGeneralPermissions.getOrDefault(colonyId, false)) {
                return; // Not applied
            }

            IPermissions permissions = colony.getPermissions();
            
            // Restore original neutral permissions
            Map<Action, Boolean> originalNeutral = originalNeutralPermissions.get(colonyId);
            if (originalNeutral != null) {
                Rank neutralRank = permissions.getRankNeutral();
                for (Map.Entry<Action, Boolean> entry : originalNeutral.entrySet()) {
                    permissions.setPermission(neutralRank, entry.getKey(), entry.getValue());
                }
            }

            // Restore original hostile permissions
            Map<Action, Boolean> originalHostile = originalHostilePermissions.get(colonyId);
            if (originalHostile != null) {
                Rank hostileRank = permissions.getRankHostile();
                for (Map.Entry<Action, Boolean> entry : originalHostile.entrySet()) {
                    permissions.setPermission(hostileRank, entry.getKey(), entry.getValue());
                }
            }

            // Clean up tracking
            coloniesWithGeneralPermissions.remove(colonyId);
            originalNeutralPermissions.remove(colonyId);
            originalHostilePermissions.remove(colonyId);
            
            LOGGER.debug("Removed general permissions from colony '{}' (ID: {})", 
                    colony.getName(), colonyId);
                    
        } catch (Exception e) {
            LOGGER.error("Failed to remove general permissions from colony '{}' (ID: {})", 
                    colony.getName(), colony.getID(), e);
        }
    }

    /**
     * Remove general permissions from all colonies
     */
    public static void removeGeneralPermissionsFromAllColonies() {
        try {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                removeGeneralPermissions(colony);
            }
            LOGGER.info("General colony permissions removed from all colonies");
        } catch (Exception e) {
            LOGGER.error("Error removing general permissions from all colonies", e);
        }
    }

    /**
     * Reapply general permissions to all colonies (useful for config reloads)
     */
    public static void reapplyGeneralPermissions() {
        LOGGER.info("Reapplying general colony permissions...");
        
        // First remove existing permissions
        removeGeneralPermissionsFromAllColonies();
        
        // Then apply new permissions if enabled
        if (TaxConfig.isGeneralItemInteractionsEnabled()) {
            applyGeneralPermissionsToAllColonies();
        }
    }

    /**
     * Store original permissions before applying general permissions
     */
    private static void storeOriginalPermissions(IColony colony, Set<Action> actions) {
        int colonyId = colony.getID();
        IPermissions permissions = colony.getPermissions();
        
        // Store original neutral permissions
        Map<Action, Boolean> neutralPermissions = new HashMap<>();
        Rank neutralRank = permissions.getRankNeutral();
        for (Action action : actions) {
            neutralPermissions.put(action, permissions.hasPermission(neutralRank, action));
        }
        originalNeutralPermissions.put(colonyId, neutralPermissions);

        // Store original hostile permissions
        Map<Action, Boolean> hostilePermissions = new HashMap<>();
        Rank hostileRank = permissions.getRankHostile();
        for (Action action : actions) {
            hostilePermissions.put(action, permissions.hasPermission(hostileRank, action));
        }
        originalHostilePermissions.put(colonyId, hostilePermissions);
    }

    /**
     * Apply general permissions to a newly created colony
     */
    public static void onColonyCreated(IColony colony) {
        if (TaxConfig.isGeneralItemInteractionsEnabled()) {
            LOGGER.debug("Applying general permissions to newly created colony: {}", colony.getName());
            applyGeneralPermissions(colony);
        }
    }

    /**
     * Clean up permissions tracking when a colony is deleted
     */
    public static void onColonyDeleted(int colonyId) {
        coloniesWithGeneralPermissions.remove(colonyId);
        originalNeutralPermissions.remove(colonyId);
        originalHostilePermissions.remove(colonyId);
        LOGGER.debug("Cleaned up general permissions tracking for deleted colony ID: {}", colonyId);
    }

    /**
     * Check if a colony has general permissions applied
     */
    public static boolean hasGeneralPermissions(IColony colony) {
        return colony != null && coloniesWithGeneralPermissions.getOrDefault(colony.getID(), false);
    }

    /**
     * Get the current status of general permissions for all colonies
     */
    public static Map<Integer, Boolean> getGeneralPermissionsStatus() {
        return new HashMap<>(coloniesWithGeneralPermissions);
    }
}