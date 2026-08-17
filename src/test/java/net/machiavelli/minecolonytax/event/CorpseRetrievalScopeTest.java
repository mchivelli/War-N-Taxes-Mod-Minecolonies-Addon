package net.machiavelli.minecolonytax.event;

import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how far the corpse exemption reaches.
 *
 * <p>The handler weakens a restriction, which is the kind of change that quietly grows. Two
 * properties keep it safe and neither is enforced by the compiler:
 *
 * <ul>
 *   <li>It only ever touches the two entity-INTERACTION events. Attacking is a separate event
 *       ({@code AttackEntityEvent}, which MineColonies gates on {@code ATTACK_ENTITY}); subscribing
 *       to it here would let players damage corpses — including other people's — in colonies where
 *       that is denied. Corpse's own damage rules must stay the only authority on that.</li>
 *   <li>It never cancels anything. A handler that both un-cancels and cancels could tighten
 *       restrictions as a side effect of a convenience feature.</li>
 * </ul>
 */
class CorpseRetrievalScopeTest {

    @Test
    @DisplayName("the handler subscribes to entity interaction only, never to attacks")
    void subscribesOnlyToInteractionEvents() {
        List<String> subscribed = new ArrayList<>();
        for (Method m : CorpseRetrievalHandler.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(SubscribeEvent.class)) {
                assertEquals(1, m.getParameterCount(),
                        "An event handler takes exactly one event parameter: " + m.getName());
                subscribed.add(m.getParameterTypes()[0].getName());
            }
        }

        assertEquals(2, subscribed.size(),
                "Expected exactly the two interaction handlers, found: " + subscribed);

        for (String eventType : subscribed) {
            assertTrue(eventType.contains("PlayerInteractEvent$EntityInteract"),
                    "Corpse retrieval must only relax entity INTERACTION, but it subscribes to " + eventType);
            assertFalse(eventType.contains("AttackEntityEvent"),
                    "Subscribing to AttackEntityEvent would let players attack corpses in colonies "
                            + "where MineColonies denies it - including corpses that are not theirs");
            assertFalse(eventType.contains("LivingHurtEvent") || eventType.contains("LivingAttackEvent"),
                    "Damage events are out of scope for corpse retrieval: " + eventType);
        }
    }

    @Test
    @DisplayName("the handler only ever un-cancels, never cancels")
    void neverCancelsAnything() throws Exception {
        // Read the source rather than the bytecode: the point is a reviewable invariant about what
        // this file is allowed to do, and setCanceled(true) anywhere in it would break it.
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/net/machiavelli/minecolonytax/event/CorpseRetrievalHandler.java");
        assertTrue(java.nio.file.Files.exists(source), "handler source not found at " + source.toAbsolutePath());
        String code = java.nio.file.Files.readString(source);

        assertFalse(code.contains("setCanceled(true)"),
                "CorpseRetrievalHandler must never cancel an event - it exists purely to undo a cancel");
        assertTrue(code.contains("setCanceled(false)"),
                "CorpseRetrievalHandler is supposed to un-cancel; it no longer does");
        assertTrue(code.contains("belongsTo"),
                "The exemption must remain gated on corpse ownership, never granted to any corpse");
    }
}
