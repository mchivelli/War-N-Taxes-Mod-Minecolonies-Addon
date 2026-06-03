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
 * Applies configurable interaction permissions to the neutral rank in every colony.
 * Runs a periodic scan to catch newly created colonies. Stores originals so permissions
 * can be cleanly restored when the feature is disabled.
 *
 * IMPORTANT: This manager deliberately does NOT touch the Hostile rank. Hostile rank
 * permissions are exclusively managed by WarSystem.setWarInteractionPermissions() and
 * RaidManager.setRaidInteractionPermissions(). Granting any permissions to the Hostile
 * rank here would allow war/raid opponents (who are assigned Hostile rank) to retain
 * those permissions even after wars/raids end, because this manager re-applies at every
 * server start while MineColonies persists player rank assignments across restarts.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GeneralColonyPermissionsManager {

    private static final Logger LOGGER = LogManager.getLogger(GeneralColonyPermissionsManager.class);
    
    private static final Map<Integer, Map<Action, Boolean>> originalNeutralPermissions = new HashMap<>();
    private static final Map<Integer, Boolean> coloniesWithGeneralPermissions = new HashMap<>();

    private static int scanTicker = 0;


    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (TaxConfig.isGeneralItemInteractionsEnabled()) {
            applyGeneralPermissionsToAllColonies();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
 
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

    public static void applyGeneralPermissionsToAllColonies() {
        if (!TaxConfig.isGeneralItemInteractionsEnabled()) {
            return;
        }

        try {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                applyGeneralPermissions(colony);
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("General colony permissions applied to {} colonies", IColonyManager.getInstance().getAllColonies().size());
        } catch (Exception e) {
            LOGGER.error("Error applying general permissions to all colonies", e);
        }
    }

    public static void applyGeneralPermissions(IColony colony) {
        if (!TaxConfig.isGeneralItemInteractionsEnabled() || colony == null) {
            return;
        }

        try {
            int colonyId = colony.getID();
            if (coloniesWithGeneralPermissions.getOrDefault(colonyId, false)) {
                return;
            }

            // Do not grant general interaction permissions to abandoned colonies.
            // Their permissions are locked down by ColonyAbandonmentManager and must not be overridden.
            if (net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.isColonyAbandoned(colony)) {
                return;
            }

            IPermissions permissions = colony.getPermissions();
            Set<Action> allowedActions = TaxConfig.getGeneralColonyActionSet();
            
            if (allowedActions.isEmpty()) {
                LOGGER.warn("No valid general colony actions configured");
                return;
            }

            storeOriginalPermissions(colony, allowedActions);

            Rank neutralRank = permissions.getRankNeutral();
            for (Action action : allowedActions) {
                permissions.setPermission(neutralRank, action, true);
            }

            // NOTE: Hostile rank is intentionally excluded. See class-level javadoc.

            coloniesWithGeneralPermissions.put(colonyId, true);
            
            LOGGER.debug("Applied general permissions to colony '{}' (ID: {}): {}", 
                    colony.getName(), colonyId, allowedActions);
                    
        } catch (Exception e) {
            LOGGER.error("Failed to apply general permissions to colony '{}' (ID: {})", 
                    colony.getName(), colony.getID(), e);
        }
    }

    public static void removeGeneralPermissions(IColony colony) {
        if (colony == null) {
            return;
        }

        try {
            int colonyId = colony.getID();
            if (!coloniesWithGeneralPermissions.getOrDefault(colonyId, false)) {
                return;
            }

            IPermissions permissions = colony.getPermissions();
            
            Map<Action, Boolean> originalNeutral = originalNeutralPermissions.get(colonyId);
            if (originalNeutral != null) {
                Rank neutralRank = permissions.getRankNeutral();
                for (Map.Entry<Action, Boolean> entry : originalNeutral.entrySet()) {
                    permissions.setPermission(neutralRank, entry.getKey(), entry.getValue());
                }
            }

            // NOTE: Hostile rank is intentionally excluded — not touched on apply, not restored here.

            coloniesWithGeneralPermissions.remove(colonyId);
            originalNeutralPermissions.remove(colonyId);

            LOGGER.debug("Removed general permissions from colony '{}' (ID: {})",
                    colony.getName(), colonyId);
                    
        } catch (Exception e) {
            LOGGER.error("Failed to remove general permissions from colony '{}' (ID: {})", 
                    colony.getName(), colony.getID(), e);
        }
    }

    public static void removeGeneralPermissionsFromAllColonies() {
        try {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                removeGeneralPermissions(colony);
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("General colony permissions removed from all colonies");
        } catch (Exception e) {
            LOGGER.error("Error removing general permissions from all colonies", e);
        }
    }

    public static void reapplyGeneralPermissions() {
        removeGeneralPermissionsFromAllColonies();
        if (TaxConfig.isGeneralItemInteractionsEnabled()) {
            applyGeneralPermissionsToAllColonies();
        }
    }

    private static void storeOriginalPermissions(IColony colony, Set<Action> actions) {
        int colonyId = colony.getID();
        IPermissions permissions = colony.getPermissions();
        
        Map<Action, Boolean> neutralPermissions = new HashMap<>();
        Rank neutralRank = permissions.getRankNeutral();
        for (Action action : actions) {
            neutralPermissions.put(action, permissions.hasPermission(neutralRank, action));
        }
        originalNeutralPermissions.put(colonyId, neutralPermissions);
        // Hostile rank is not stored — this manager does not modify Hostile rank permissions.
    }

    public static void onColonyCreated(IColony colony) {
        if (TaxConfig.isGeneralItemInteractionsEnabled()) {
            LOGGER.debug("Applying general permissions to newly created colony: {}", colony.getName());
            applyGeneralPermissions(colony);
        }
    }

    public static void onColonyDeleted(int colonyId) {
        coloniesWithGeneralPermissions.remove(colonyId);
        originalNeutralPermissions.remove(colonyId);
        LOGGER.debug("Cleaned up general permissions tracking for deleted colony ID: {}", colonyId);
    }

    public static boolean hasGeneralPermissions(IColony colony) {
        return colony != null && coloniesWithGeneralPermissions.getOrDefault(colony.getID(), false);
    }

    public static Map<Integer, Boolean> getGeneralPermissionsStatus() {
        return new HashMap<>(coloniesWithGeneralPermissions);
    }
}
