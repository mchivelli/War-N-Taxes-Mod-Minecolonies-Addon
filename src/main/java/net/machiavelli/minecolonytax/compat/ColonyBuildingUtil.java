package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Cross-version compatibility shim for accessing MineColonies building data.
 *
 * <p>MineColonies renamed {@code getBuildingManager()} to {@code getServerBuildingManager()}
 * in newer API versions. This utility detects which method is available at runtime via
 * reflection and caches the result, so the mod works with both old and new MineColonies
 * installations without requiring separate builds or direct type references to the
 * version-specific manager interfaces.
 *
 * <p>Usage: replace all {@code colony.get*BuildingManager().getBuildings().values()}
 * call sites with {@code ColonyBuildingUtil.getBuildings(colony)}.
 */
public final class ColonyBuildingUtil {

    private static final Logger LOGGER = LogManager.getLogger();

    // Newest API first; ColonyBuildingUtil will cache whichever resolves successfully
    private static final String[] MANAGER_METHOD_CANDIDATES = {
        "getServerBuildingManager",
        "getBuildingManager"
    };

    private static volatile Method managerGetter  = null;
    private static volatile Method buildingsGetter = null;
    private static volatile boolean initialized    = false;

    private ColonyBuildingUtil() {}

    /**
     * Returns all buildings in the colony. Never throws; returns an empty collection
     * if the colony is null, the API is unavailable, or any error occurs.
     */
    @SuppressWarnings("unchecked")
    public static Collection<IBuilding> getBuildings(IColony colony) {
        if (colony == null) return Collections.emptyList();

        ensureInit(colony);

        if (managerGetter == null) return Collections.emptyList();

        try {
            Object manager = managerGetter.invoke(colony);
            if (manager == null) return Collections.emptyList();

            // buildingsGetter may have been null if manager was null during first init probe
            Method bg = buildingsGetter;
            if (bg == null) {
                bg = manager.getClass().getMethod("getBuildings");
                buildingsGetter = bg;
            }

            Map<?, IBuilding> map = (Map<?, IBuilding>) bg.invoke(manager);
            return map != null ? map.values() : Collections.emptyList();
        } catch (Exception e) {
            LOGGER.warn("[WnT] Could not read buildings for colony {}: {}", colony.getID(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private static void ensureInit(IColony colony) {
        if (initialized) return;
        synchronized (ColonyBuildingUtil.class) {
            if (initialized) return;
            for (String name : MANAGER_METHOD_CANDIDATES) {
                try {
                    Method getter = IColony.class.getMethod(name);
                    Object mgr = getter.invoke(colony);
                    managerGetter = getter;
                    if (mgr != null) {
                        buildingsGetter = mgr.getClass().getMethod("getBuildings");
                    }
                    LOGGER.info("[WnT] MineColonies building API detected: IColony#{}()", name);
                    break;
                } catch (NoSuchMethodException ignored) {
                } catch (Exception e) {
                    LOGGER.debug("[WnT] Probe of {}() failed: {}", name, e.getMessage());
                }
            }
            if (managerGetter == null) {
                LOGGER.error("[WnT] No compatible MineColonies building manager method found! " +
                             "Tried: getServerBuildingManager, getBuildingManager. " +
                             "Building-dependent features will be disabled.");
            }
            initialized = true;
        }
    }
}
