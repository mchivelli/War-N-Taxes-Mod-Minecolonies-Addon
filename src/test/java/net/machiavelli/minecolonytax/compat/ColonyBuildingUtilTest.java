package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the MineColonies cross-version building access shim.
 *
 * <p>Background: MineColonies renamed {@code IColony#getBuildingManager()} to
 * {@code getServerBuildingManager()}. Mods compiled against one API shape crash with
 * {@link NoSuchMethodError} when users run the other. {@link ColonyBuildingUtil}
 * resolves whichever method exists at runtime via reflection. These tests prove the
 * shim resolves a manager through {@code IColony}, extracts buildings, and degrades
 * to an empty collection (instead of throwing) on null/broken input.
 *
 * <p>Uses JDK dynamic proxies so no Minecraft bootstrap is required. The building
 * manager proxy implements the REAL manager interface declared by whichever
 * {@code IColony} getter exists on the compile-time MineColonies jar (discovered
 * reflectively, in the same probe order as the shim), so the test passes regardless
 * of which API shape that jar exposes — exactly the property the shim must
 * guarantee in production.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ColonyBuildingUtilTest {

    /** Same probe order as the shim: newest API first. */
    private static final String[] MANAGER_METHOD_CANDIDATES = {
        "getServerBuildingManager",
        "getBuildingManager"
    };

    /** The manager interface type declared by the first IColony getter that exists. */
    private static Class<?> managerInterface() {
        for (String name : MANAGER_METHOD_CANDIDATES) {
            try {
                Class<?> type = IColony.class.getMethod(name).getReturnType();
                assertTrue(type.isInterface(),
                    "expected building manager type to be an interface, got: " + type);
                return type;
            } catch (NoSuchMethodException ignored) {
                // not present in this MineColonies version — try next candidate
            }
        }
        throw new AssertionError(
            "IColony exposes neither getServerBuildingManager() nor getBuildingManager() — "
            + "the MineColonies API changed again; ColonyBuildingUtil needs a new candidate.");
    }

    /** A fake building manager implementing the real manager interface. */
    private static Object fakeManager(Map<Object, IBuilding> buildings) {
        Class<?> type = managerInterface();
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> {
                if (method.getName().equals("getBuildings")) {
                    return buildings;
                }
                return defaultValue(method);
            });
    }

    private static IBuilding fakeBuilding() {
        return (IBuilding) Proxy.newProxyInstance(
            IBuilding.class.getClassLoader(),
            new Class<?>[]{IBuilding.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "FakeBuilding@" + Integer.toHexString(System.identityHashCode(proxy));
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** A fake IColony whose building manager (under either API name) is {@code manager}. */
    private static IColony colonyWith(Object manager) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("getServerBuildingManager") || name.equals("getBuildingManager")) {
                // Only hand the manager out through a getter whose declared return type it
                // actually implements — mirrors production, avoids proxy ClassCastException.
                return (manager != null && method.getReturnType().isInstance(manager)) ? manager : null;
            }
            if (name.equals("getID")) {
                return 1;
            }
            return defaultValue(method);
        };
        return (IColony) Proxy.newProxyInstance(
            IColony.class.getClassLoader(), new Class<?>[]{IColony.class}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == boolean.class) return false;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        if (rt == double.class) return 0.0d;
        if (rt == float.class) return 0.0f;
        return null;
    }

    @Test
    @Order(1)
    void nullColonyReturnsEmptyWithoutThrowing() {
        Collection<IBuilding> result = ColonyBuildingUtil.getBuildings(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(2)
    void resolvesManagerViaReflectionAndReturnsBuildings() {
        Map<Object, IBuilding> map = new LinkedHashMap<>();
        IBuilding barracks = fakeBuilding();
        IBuilding townHall = fakeBuilding();
        map.put("barracks", barracks);
        map.put("townhall", townHall);

        Collection<IBuilding> result = ColonyBuildingUtil.getBuildings(colonyWith(fakeManager(map)));

        assertEquals(2, result.size(), "shim must surface all buildings from the manager");
        assertTrue(result.contains(barracks), "missing building instance from shim result");
        assertTrue(result.contains(townHall), "missing building instance from shim result");
    }

    @Test
    @Order(3)
    void nullManagerReturnsEmptyWithoutThrowing() {
        Collection<IBuilding> result = ColonyBuildingUtil.getBuildings(colonyWith(null));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(4)
    void emptyManagerReturnsEmpty() {
        Collection<IBuilding> result =
            ColonyBuildingUtil.getBuildings(colonyWith(fakeManager(new LinkedHashMap<>())));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
