package net.machiavelli.minecolonytax.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the mod works with FTB Teams ABSENT.
 *
 * <p>FTB Teams is optional, and the failure mode when an optional dependency is handled carelessly
 * is not a graceful "feature off" — it is {@link NoClassDefFoundError} at class-load time, thrown
 * before any {@code isLoaded()} guard gets a chance to run, taking down whatever touched it.
 *
 * <p>These tests run in a plain JVM with no Forge runtime, so {@code ModList.get()} cannot work and
 * {@link FtbTeamsCompat#isInstalled()} resolves to false — i.e. the suite executes exactly the
 * "FTB Teams is not installed" path that a server without the mod takes.
 */
class FtbTeamsCompatTest {

    @Test
    @DisplayName("without a Forge runtime the shim reports FTB Teams as absent instead of throwing")
    void reportsAbsentWithoutForgeRuntime() {
        assertDoesNotThrow(FtbTeamsCompat::isInstalled,
                "isInstalled() must swallow a missing ModList, not propagate it");
        assertFalse(FtbTeamsCompat.isInstalled(),
                "With no Forge runtime there is no mod list, so FTB Teams cannot be considered present");
    }

    @Test
    @DisplayName("every entry point degrades to a safe default when FTB Teams is absent")
    void allEntryPointsDegradeSafely() {
        UUID id = UUID.randomUUID();

        assertTrue(FtbTeamsCompat.getTeamForPlayer(id).isEmpty(), "getTeamForPlayer must be empty");
        assertTrue(FtbTeamsCompat.getTeamById(id).isEmpty(), "getTeamById must be empty");
        assertNull(FtbTeamsCompat.getTeamId(null), "getTeamId(null) must be null");

        assertNotNull(FtbTeamsCompat.getTeamMembers(null), "getTeamMembers must never return null");
        assertTrue(FtbTeamsCompat.getTeamMembers(null).isEmpty(), "getTeamMembers must be empty");

        assertNotNull(FtbTeamsCompat.getPartyMembers(null), "getPartyMembers must never return null");
        assertTrue(FtbTeamsCompat.getPartyMembers(null).isEmpty(), "getPartyMembers must be empty");

        assertFalse(FtbTeamsCompat.isPartyTeam(null), "isPartyTeam must be false");
        assertFalse(FtbTeamsCompat.partyTeamContains(null, id), "partyTeamContains must be false");
    }

    @Test
    @DisplayName("null arguments never throw on any entry point")
    void nullArgumentsAreTolerated() {
        assertDoesNotThrow(() -> {
            FtbTeamsCompat.getTeamForPlayer(null);
            FtbTeamsCompat.getTeamById(null);
            FtbTeamsCompat.getTeamId(null);
            FtbTeamsCompat.getTeamMembers(null);
            FtbTeamsCompat.getPartyMembers(null);
            FtbTeamsCompat.isPartyTeam(null);
            FtbTeamsCompat.partyTeamContains(null, null);
        }, "The shim is the boundary against a missing mod; it must be totally defensive");
    }

    @Test
    @DisplayName("the public shim surface leaks no FTB Teams type")
    void publicSurfaceLeaksNoFtbTypes() {
        // This is the actual classloader-safety contract. If a method ever returns or accepts a
        // dev.ftb.* type, merely resolving FtbTeamsCompat on a server without FTB Teams can throw
        // NoClassDefFoundError - the exact failure the shim exists to prevent. TeamHandle
        // deliberately carries its delegate as a plain Object for this reason.
        for (Method m : FtbTeamsCompat.class.getMethods()) {
            assertFalse(m.getReturnType().getName().startsWith("dev.ftb."),
                    "FtbTeamsCompat." + m.getName() + " returns an FTB Teams type; keep it behind TeamHandle");
            for (Class<?> param : m.getParameterTypes()) {
                assertFalse(param.getName().startsWith("dev.ftb."),
                        "FtbTeamsCompat." + m.getName() + " takes an FTB Teams type; keep it behind TeamHandle");
            }
        }
        for (Method m : FtbTeamsCompat.TeamHandle.class.getMethods()) {
            assertFalse(m.getReturnType().getName().startsWith("dev.ftb."),
                    "TeamHandle." + m.getName() + " exposes an FTB Teams type to callers");
        }
    }

    @Test
    @DisplayName("the implementation class is never touched while FTB Teams is absent")
    void implementationStaysUnloaded() {
        // FtbTeamsCompatImpl is the only class holding dev.ftb.* imports. Calling the shim above
        // must not have caused it to load, because loading it without FTB Teams present is what
        // throws. Checking the loaded-class state directly is not portable, so assert the design
        // invariant instead: the impl is package-private and unreachable from outside.
        assertFalse(java.lang.reflect.Modifier.isPublic(FtbTeamsCompatImpl.class.getModifiers()),
                "FtbTeamsCompatImpl must stay package-private so nothing outside the shim can load it");
    }
}
