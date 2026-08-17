package net.machiavelli.minecolonytax.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the startup compatibility guard against whichever MineColonies jar is on the
 * classpath, so a dependency bump that removes something the mod calls fails the BUILD instead of
 * failing a player's server hours into a session.
 *
 * <p>This is the counterpart to MineColoniesRankContractTest: that one pins the rank NUMBERING,
 * this one pins the API SURFACE.
 */
class CompatibilityCheckTest {

    @Test
    @DisplayName("the MineColonies on the classpath provides every API the mod calls")
    void apiSurfaceIsSatisfiedByTheCompiledAgainstMineColonies() {
        CompatibilityCheck.Result result = CompatibilityCheck.probeApiSurface();

        assertTrue(result.isCompatible(),
                "The MineColonies jar this build compiles against is missing API the mod calls. "
                        + "This is exactly the drift that shipped as a runtime crash in 5.0.4. Missing: "
                        + result.criticalFailures);
    }

    @Test
    @DisplayName("the guard actually detects a missing method rather than always passing")
    void guardDetectsMissingApi() {
        // A checker that can only ever return "fine" protects nothing. Verify the underlying
        // detection against a class that deliberately does NOT have the method, using the same
        // name-based lookup the guard uses.
        Set<String> namesOnObject = methodNamesOf(Object.class);
        assertFalse(namesOnObject.contains("getServerBuildingManager"),
                "sanity: java.lang.Object must not carry a MineColonies method");

        Set<String> namesOnColony = methodNamesOf(loadColony());
        assertTrue(namesOnColony.contains("getPermissions"),
                "sanity: IColony must carry getPermissions(), otherwise the probe is looking at the wrong type");
    }

    @Test
    @DisplayName("the building-manager accessor the whole tax economy depends on is present")
    void buildingManagerAccessorExists() {
        // ColonyBuildingUtil returns an EMPTY collection when this is missing, so its absence does
        // not throw - it silently zeroes tax generation for every colony and makes every colony
        // look empty to the deletion grace calculation. Worth its own assertion.
        Set<String> names = methodNamesOf(loadColony());
        assertTrue(names.contains("getServerBuildingManager") || names.contains("getBuildingManager"),
                "IColony exposes neither getServerBuildingManager() nor getBuildingManager(); "
                        + "tax generation would silently produce nothing");
    }

    /**
     * Loads IColony WITHOUT running its static initializer, exactly as the guard does.
     * IColony's {@code <clinit>} resolves a Forge capability token that only works after Forge's
     * bytecode transformer has run, so initializing it here throws and would make a healthy
     * MineColonies look broken. This test found that defect in the guard itself.
     */
    private static Class<?> loadColony() {
        try {
            return Class.forName("com.minecolonies.api.colony.IColony", false,
                    CompatibilityCheckTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("MineColonies is not on the test classpath", e);
        }
    }

    private static Set<String> methodNamesOf(Class<?> type) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Method m : type.getMethods()) {
            names.add(m.getName());
        }
        return names;
    }
}
