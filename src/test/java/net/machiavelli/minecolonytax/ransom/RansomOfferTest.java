package net.machiavelli.minecolonytax.ransom;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * State-machine contract for {@link RansomOffer}: exactly one terminal transition
 * ever succeeds (CAS), protecting against double-clicked chat buttons and an
 * expiry tick racing an accept.
 */
class RansomOfferTest {

    private static RansomOffer newOffer() {
        return new RansomOffer(UUID.randomUUID(), UUID.randomUUID(), 42, 1_500,
                1_000L, 61_000L, ConflictType.RAID);
    }

    @Test
    void startsPending() {
        RansomOffer offer = newOffer();
        assertTrue(offer.isPending());
        assertEquals(RansomOffer.State.PENDING, offer.getState());
    }

    @Test
    void eachTerminalStateIsReachableOnce() {
        for (RansomOffer.State terminal : new RansomOffer.State[]{
                RansomOffer.State.ACCEPTED, RansomOffer.State.DENIED,
                RansomOffer.State.CANCELLED, RansomOffer.State.EXPIRED}) {
            RansomOffer offer = newOffer();
            assertTrue(offer.resolve(terminal), "first resolve to " + terminal + " must win");
            assertEquals(terminal, offer.getState());
            assertFalse(offer.isPending());
        }
    }

    @Test
    void secondResolveLosesAndKeepsFirstState() {
        RansomOffer offer = newOffer();
        assertTrue(offer.resolve(RansomOffer.State.ACCEPTED));
        assertFalse(offer.resolve(RansomOffer.State.DENIED));
        assertFalse(offer.resolve(RansomOffer.State.ACCEPTED)); // even the same target loses
        assertEquals(RansomOffer.State.ACCEPTED, offer.getState());
    }

    @Test
    void expiryRacingAcceptIsSettledByCas() {
        RansomOffer offer = newOffer();
        assertTrue(offer.resolve(RansomOffer.State.EXPIRED));   // tick fires first...
        assertFalse(offer.resolve(RansomOffer.State.ACCEPTED)); // ...late click has no effect
        assertEquals(RansomOffer.State.EXPIRED, offer.getState());
    }

    @Test
    void resolvingToPendingIsRejected() {
        RansomOffer offer = newOffer();
        assertThrows(IllegalArgumentException.class, () -> offer.resolve(RansomOffer.State.PENDING));
        assertTrue(offer.isPending()); // guard did not consume the transition
    }

    @Test
    void expiryIsAWallClockBoundary() {
        RansomOffer offer = newOffer(); // expiresAtMs = 61_000
        assertFalse(offer.isExpired(60_999L));
        assertTrue(offer.isExpired(61_000L));
        assertTrue(offer.isExpired(100_000L));
    }
}
