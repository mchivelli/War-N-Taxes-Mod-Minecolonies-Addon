package net.machiavelli.minecolonytax.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-shot startup check that the MineColonies API this mod actually calls is present.
 *
 * <p><b>Why this exists.</b> The mod declares an open-ended MineColonies dependency range, so
 * Forge/NeoForge happily loads it against a MineColonies whose API has drifted. Because the
 * mismatches are missing methods and missing enum constants, nothing fails at load time — the
 * server starts clean and then throws {@code NoSuchMethodError} / {@code NoSuchFieldError} hours
 * later, in the middle of gameplay. That is exactly what shipped in 5.0.4 on the 1.21.1 branch.
 *
 * <p>Worse than a crash is the silent case: {@link ColonyBuildingUtil#getBuildings} deliberately
 * returns an empty collection on any failure, so a drifted building-manager API means every colony
 * reports zero buildings. Tax generation quietly produces nothing for everybody, and the deletion
 * grace period collapses to "instant" because the colony looks empty. Nobody gets an error.
 *
 * <p>This check converts all of that into one loud, consolidated line at server start. It is
 * deliberately reflection-only and never throws: a compatibility checker that crashes the server
 * it is meant to protect would be worse than no checker at all.
 *
 * <p>The probe is a pure function ({@link #probeApiSurface()}) so it can be unit-tested against
 * whichever MineColonies jar is on the classpath, rather than only being exercised in production.
 */
public final class CompatibilityCheck {

    private static final Logger LOGGER = LogManager.getLogger(CompatibilityCheck.class);

    private CompatibilityCheck() {}

    /** Outcome of a probe. Never null fields; {@link #isCompatible()} means nothing critical is missing. */
    public static final class Result {
        /** Missing API the mod cannot work without. */
        public final List<String> criticalFailures;
        /** Present-but-notable findings (e.g. an older API shape that a shim covers). */
        public final List<String> warnings;

        Result(List<String> criticalFailures, List<String> warnings) {
            this.criticalFailures = Collections.unmodifiableList(criticalFailures);
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public boolean isCompatible() {
            return criticalFailures.isEmpty();
        }
    }

    // The API surface the mod actually calls. Probed BY NAME only: MineColonies changes parameter
    // types between versions more often than it removes a method outright, and a signature-exact
    // probe would report false failures for methods that are perfectly usable.
    private static final String[] COLONY_METHODS = {
            "getPermissions", "getID", "getName", "getCenter", "getDimension", "getWorld",
            "getCitizenManager", "getLastContactInHours"
    };
    private static final String[] PERMISSION_METHODS = {
            "getOwner", "getPlayers", "getPlayersByRank", "getRank",
            "getRankOwner", "getRankOfficer", "getRankNeutral", "getRankHostile",
            "setPermission", "setPlayerRank"
    };
    private static final String[] RANK_METHODS = {
            "getId", "getName", "isColonyManager", "isHostile"
    };
    private static final String[] COLONY_MANAGER_METHODS = {
            "getAllColonies", "getColonies", "getColonyByWorld", "getColonyByDimension"
    };
    /** The building-manager accessor has been renamed before; any ONE of these is enough. */
    private static final String[] BUILDING_MANAGER_CANDIDATES = {
            "getServerBuildingManager", "getBuildingManager"
    };

    /**
     * Probe the MineColonies API surface. Pure and side-effect free apart from classloading —
     * safe to call from a unit test.
     */
    public static Result probeApiSurface() {
        List<String> critical = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        checkClass(critical, "com.minecolonies.api.colony.IColony", COLONY_METHODS);
        checkClass(critical, "com.minecolonies.api.colony.permissions.IPermissions", PERMISSION_METHODS);
        checkClass(critical, "com.minecolonies.api.colony.permissions.Rank", RANK_METHODS);
        checkClass(critical, "com.minecolonies.api.colony.IColonyManager", COLONY_MANAGER_METHODS);

        // Building manager: any one candidate is fine, but NONE is fatal — without it every colony
        // reads as having zero buildings and the whole tax economy silently produces nothing.
        try {
            Class<?> colony = load("com.minecolonies.api.colony.IColony");
            Set<String> names = methodNames(colony);
            String found = null;
            for (String candidate : BUILDING_MANAGER_CANDIDATES) {
                if (names.contains(candidate)) { found = candidate; break; }
            }
            if (found == null) {
                critical.add("IColony has none of " + Arrays.toString(BUILDING_MANAGER_CANDIDATES)
                        + " - tax generation would silently produce nothing for every colony, "
                        + "and colony deletion would treat every colony as empty");
            } else if (!BUILDING_MANAGER_CANDIDATES[0].equals(found)) {
                warnings.add("Using the older building-manager API (IColony#" + found
                        + "); the ColonyBuildingUtil shim covers this");
            }
        } catch (Throwable t) {
            critical.add("Could not inspect IColony: " + t);
        }

        return new Result(critical, warnings);
    }

    /**
     * Run the check and log a consolidated report. Call once, at server start.
     *
     * @return true when nothing critical is missing
     */
    public static boolean runAtStartup() {
        Result result;
        try {
            result = probeApiSurface();
        } catch (Throwable t) {
            // Never let the guard take down the server it is guarding.
            LOGGER.error("[WnT] MineColonies compatibility check could not run: {}", t.toString());
            return true;
        }

        for (String warning : result.warnings) {
            LOGGER.warn("[WnT] MineColonies compatibility: {}", warning);
        }

        if (result.isCompatible()) {
            LOGGER.info("[WnT] MineColonies API compatibility check passed.");
            return true;
        }

        LOGGER.error("[WnT] ================= MineColonies INCOMPATIBILITY =================");
        LOGGER.error("[WnT] War 'N Taxes expects MineColonies API that this install does not provide.");
        LOGGER.error("[WnT] The server will keep running, but the affected features will misbehave -");
        LOGGER.error("[WnT] usually by silently doing nothing rather than by throwing. Update or");
        LOGGER.error("[WnT] downgrade MineColonies to a version this build was tested against.");
        for (String failure : result.criticalFailures) {
            LOGGER.error("[WnT]   - {}", failure);
        }
        LOGGER.error("[WnT] ===============================================================");
        return false;
    }

    /**
     * Load a class <em>without running its static initializer</em>.
     *
     * <p>This matters: {@code IColony}'s {@code <clinit>} resolves a Forge capability token, which
     * only works once Forge's bytecode transformer has run. Initializing it from a probe throws
     * {@code NoClassDefFoundError} and the guard would report a perfectly good MineColonies as
     * incompatible. More generally, a compatibility probe has no business executing third-party
     * static initializers and their side effects — {@link Class#getMethods()} does not need them.
     */
    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className, false, CompatibilityCheck.class.getClassLoader());
    }

    private static void checkClass(List<String> critical, String className, String[] required) {
        Class<?> type;
        try {
            type = load(className);
        } catch (Throwable t) {
            critical.add("Missing class " + className + " (" + t.getClass().getSimpleName() + ")");
            return;
        }
        Set<String> names = methodNames(type);
        for (String method : required) {
            if (!names.contains(method)) {
                critical.add(className + "#" + method + "() is missing");
            }
        }
    }

    private static Set<String> methodNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Method m : type.getMethods()) {
            names.add(m.getName());
        }
        return names;
    }
}
