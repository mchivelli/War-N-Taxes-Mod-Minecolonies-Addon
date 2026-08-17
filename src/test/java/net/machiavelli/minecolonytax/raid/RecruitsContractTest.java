package net.machiavelli.minecolonytax.raid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the Recruits integration, which is driven entirely by reflection on
 * hard-coded class and method names in {@link ReflectionCache}.
 *
 * <p>Nothing about those names is checked by the compiler, so a Recruits update that moves a class
 * or renames an accessor would turn the integration off silently — every recruit would simply stop
 * being recognised, with no error anywhere. That is the same failure the Corpse integration was
 * caught with, where the accessor name taken from the project's development branch matched nothing
 * on any shipping release.
 *
 * <p>The candidate lists here are duplicated from ReflectionCache on purpose: this test asserts
 * that at least one candidate still resolves against the real jar. If Recruits changes, the build
 * fails with the reason rather than a player noticing months later.
 */
class RecruitsContractTest {

    // Mirrors ReflectionCache.RECRUIT_ENTITY_CLASS_NAMES
    private static final String[] ENTITY_CLASS_CANDIDATES = {
            "com.talhanation.recruits.entities.AbstractRecruitEntity",
            "com.talhanation.recruits.entities.RecruitEntity",
            "com.talhanation.recruits.entities.BaseRecruitEntity",
            "com.talhanation.recruits.entity.AbstractRecruitEntity"
    };

    // Mirrors ReflectionCache.RECRUIT_EVENT_CLASS_NAMES
    private static final String[] EVENT_CLASS_CANDIDATES = {
            "com.talhanation.recruits.RecruitEvents",
            "com.talhanation.recruits.events.RecruitEvents",
            "com.talhanation.recruits.util.RecruitEvents"
    };

    /** Mirrors the owner-accessor candidates ReflectionCache tries on a recruit entity. */
    private static final String[] OWNER_METHOD_CANDIDATES = {
            "getOwnerUUID", "getOwner", "getOwnerID", "getPlayerUUID"
    };

    @Test
    @DisplayName("at least one recruit entity class candidate exists on the real jar")
    void recruitEntityClassResolves() {
        assertNotNull(firstResolvable(ENTITY_CLASS_CANDIDATES),
                "None of the Recruits entity class names resolve. The Recruits integration would "
                        + "silently stop recognising recruits. Update ReflectionCache."
                        + "RECRUIT_ENTITY_CLASS_NAMES (and this test) with the new name.");
    }

    @Test
    @DisplayName("at least one recruit events class candidate exists on the real jar")
    void recruitEventsClassResolves() {
        assertNotNull(firstResolvable(EVENT_CLASS_CANDIDATES),
                "None of the Recruits events class names resolve. Update ReflectionCache."
                        + "RECRUIT_EVENT_CLASS_NAMES (and this test) with the new name.");
    }

    @Test
    @DisplayName("a usable owner accessor exists on the recruit entity")
    void ownerAccessorResolves() {
        Class<?> entity = firstResolvable(ENTITY_CLASS_CANDIDATES);
        assertNotNull(entity, "recruit entity class must resolve first");

        List<String> found = new ArrayList<>();
        for (String name : OWNER_METHOD_CANDIDATES) {
            try {
                Method m = entity.getMethod(name);
                if (m.getParameterCount() == 0) {
                    found.add(name + " -> " + m.getReturnType().getSimpleName());
                }
            } catch (NoSuchMethodException ignored) {
                // next candidate
            }
        }

        assertTrue(!found.isEmpty(),
                "No owner accessor found on " + entity.getName()
                        + "; ownership checks against recruits would always fail. Tried: "
                        + String.join(", ", OWNER_METHOD_CANDIDATES));

        // The UUID-returning variant is the one ownership comparisons rely on; a Player-returning
        // getOwner() alone still works but goes through an extra dereference, so record both.
        assertTrue(found.stream().anyMatch(s -> s.endsWith("UUID") || s.endsWith("Player")),
                "Owner accessor returns neither a UUID nor a Player: " + found);
    }

    private static Class<?> firstResolvable(String[] candidates) {
        for (String name : candidates) {
            try {
                // No initialisation: these entity classes need a live Minecraft runtime.
                return Class.forName(name, false, RecruitsContractTest.class.getClassLoader());
            } catch (Throwable ignored) {
                // try the next candidate
            }
        }
        return null;
    }
}
