package net.machiavelli.minecolonytax.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract test against the REAL Corpse jar (test scope only).
 *
 * <p>The integration reads the corpse owner reflectively, so nothing about it is checked by the
 * compiler. Without this test a Corpse update that renames or reshapes the accessor would ship as
 * a feature that silently does nothing — players simply could not reach their corpses, with only a
 * warning line to show for it.
 *
 * <p>This is not hypothetical: the accessor was first written against the project's development
 * branch, which exposes {@code UUID getPlayerUuid()}. Every shipping 1.20.1 and 1.21.1 build
 * actually exposes {@code Optional&lt;UUID&gt; getCorpseUUID()}, so the original lookup matched
 * nothing. This test is what caught that.
 */
class CorpseContractTest {

    private static final String CORPSE_ENTITY = "de.maxhenkel.corpse.entities.CorpseEntity";

    @Test
    @DisplayName("the Corpse entity class exists where the integration expects it")
    void corpseEntityClassExists() {
        assertNotNull(loadCorpseEntity(), "Corpse's entity class moved");
    }

    @Test
    @DisplayName("the owner accessor the integration reflects on still exists on the real jar")
    void ownerAccessorResolvesAgainstTheRealJar() {
        Method getter = CorpseCompat.findOwnerGetter(loadCorpseEntity());
        assertNotNull(getter,
                "None of the known owner accessors exist on the shipping Corpse build. Players would "
                        + "silently be unable to retrieve their corpses inside foreign colonies. "
                        + "Add the new name to CorpseCompat.OWNER_GETTER_CANDIDATES.");

        Class<?> returnType = getter.getReturnType();
        assertTrue(UUID.class.isAssignableFrom(returnType) || Optional.class.isAssignableFrom(returnType),
                "Owner accessor " + getter.getName() + " returns " + returnType.getName()
                        + ", which the integration cannot unwrap into a UUID");
    }

    @Test
    @DisplayName("the corpse mod id matches the entity's registry namespace")
    void modIdMatchesRegistryNamespace() {
        // Detection is by registry namespace rather than by class, so MOD_ID must equal the
        // namespace Corpse registers its entity under. The lang key entity.<namespace>.<path>
        // pins that from the shipped assets: "entity.corpse.corpse".
        assertTrue("corpse".equals(CorpseCompat.MOD_ID),
                "CorpseCompat.MOD_ID must be the registry namespace 'corpse' (entity id corpse:corpse)");
    }

    private static Class<?> loadCorpseEntity() {
        try {
            // No initialisation: the entity's static setup needs a live Minecraft/Forge runtime.
            return Class.forName(CORPSE_ENTITY, false, CorpseContractTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return fail("Corpse is not on the test classpath - the contract cannot be verified", e);
        }
    }
}
