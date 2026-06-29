package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.colony.IColony;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * API contract test for the MineColonies cross-version building access shim.
 *
 * <p>Background: MineColonies renamed {@code IColony#getBuildingManager()} to
 * {@code getServerBuildingManager()}. Mods compiled against one API shape crash with
 * {@link NoSuchMethodError} when users run the other. {@link ColonyBuildingUtil}
 * resolves whichever method exists at runtime via reflection — but that only works
 * while the reflection targets it probes for actually exist. This test verifies those
 * targets against whatever MineColonies jar is on the build classpath, so a future
 * dependency bump that renames the API again fails the build here instead of
 * crashing players' servers.
 *
 * <p>See {@link ColonyBuildingUtilTest} for the full behavioral test (fake colony
 * proxies end-to-end).
 */
public class ColonyBuildingUtilContractTest {

    /** Probe order the shim must use: newest API name first, legacy name as fallback. */
    private static final String[] EXPECTED_CANDIDATES = {
        "getServerBuildingManager",
        "getBuildingManager"
    };

    @Test
    void nullColonyReturnsEmptyWithoutThrowing() {
        Collection<?> result = ColonyBuildingUtil.getBuildings(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shimProbesNewApiNameFirst() throws Exception {
        Field candidates = ColonyBuildingUtil.class.getDeclaredField("MANAGER_METHOD_CANDIDATES");
        candidates.setAccessible(true);
        assertArrayEquals(EXPECTED_CANDIDATES, (String[]) candidates.get(null),
            "shim probe order changed — it must try getServerBuildingManager (new API) "
            + "before getBuildingManager (legacy API)");
    }

    @Test
    void classpathMineColoniesExposesAShimCandidate() {
        Method managerGetter = null;
        for (String name : EXPECTED_CANDIDATES) {
            try {
                managerGetter = IColony.class.getMethod(name);
                break;
            } catch (NoSuchMethodException ignored) {
                // not present in this MineColonies version — try next candidate
            }
        }
        if (managerGetter == null) {
            fail("IColony exposes neither getServerBuildingManager() nor getBuildingManager(). "
                + "The MineColonies API changed again — add the new method name to "
                + "ColonyBuildingUtil.MANAGER_METHOD_CANDIDATES (and to this test).");
        }

        // The shim then calls getBuildings() on the manager and casts the result to Map.
        Class<?> managerType = managerGetter.getReturnType();
        try {
            Method getBuildings = managerType.getMethod("getBuildings");
            assertTrue(Map.class.isAssignableFrom(getBuildings.getReturnType()),
                "manager getBuildings() no longer returns a Map — ColonyBuildingUtil's "
                + "cast would fail. Manager type: " + managerType.getName()
                + ", return type: " + getBuildings.getReturnType().getName());
        } catch (NoSuchMethodException e) {
            fail("Building manager type " + managerType.getName() + " has no getBuildings() "
                + "method — ColonyBuildingUtil cannot extract buildings from it.");
        }
    }
}
