package net.machiavelli.minecolonytax.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the two properties that make the Corpse integration safe.
 *
 * <p>These run with the Corpse mod absent (there is no Forge runtime here at all), i.e. the state
 * of every server that does not install it.
 */
class CorpseCompatTest {

    @Test
    @DisplayName("with Corpse absent the shim reports it absent instead of throwing")
    void reportsAbsentWithoutTheMod() {
        assertDoesNotThrow(CorpseCompat::isInstalled);
        assertFalse(CorpseCompat.isInstalled(),
                "With no Forge runtime there is no mod list, so Corpse cannot be considered present");
    }

    @Test
    @DisplayName("ownership fails CLOSED rather than open")
    void ownershipFailsClosed() {
        // This is the security-relevant direction. Corpse's own corpse.access.only_owner setting
        // defaults to FALSE, so if this ever returned true on an unknown entity, unblocking the
        // interaction would let a besieger open every defender's corpse. Unknown must mean "no".
        assertFalse(CorpseCompat.belongsTo(null, UUID.randomUUID()), "null entity must not be owned");
        assertFalse(CorpseCompat.belongsTo(null, null), "null player must not own anything");
        assertFalse(CorpseCompat.isCorpse(null), "null entity is not a corpse");
    }

    @Test
    @DisplayName("null arguments never throw")
    void nullArgumentsAreTolerated() {
        assertDoesNotThrow(() -> {
            CorpseCompat.isCorpse(null);
            CorpseCompat.belongsTo(null, null);
            CorpseCompat.belongsTo(null, UUID.randomUUID());
        });
    }

    @Test
    @DisplayName("the shim exposes no Corpse-mod type")
    void publicSurfaceLeaksNoCorpseTypes() {
        // Corpse is not a compile dependency at all - the entity is matched by registry id and the
        // owner read reflectively. If a Corpse type ever appeared in this surface it would become a
        // hard dependency and break every server without the mod.
        for (Method m : CorpseCompat.class.getMethods()) {
            assertFalse(m.getReturnType().getName().startsWith("de.maxhenkel."),
                    "CorpseCompat." + m.getName() + " returns a Corpse-mod type");
            for (Class<?> param : m.getParameterTypes()) {
                assertFalse(param.getName().startsWith("de.maxhenkel."),
                        "CorpseCompat." + m.getName() + " takes a Corpse-mod type");
            }
        }
    }
}
