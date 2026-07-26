package net.machiavelli.minecolonytax.ransom;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pure-math contract for {@link RansomCalculator}. These rules encode the v2 fixes:
 * below-minimum balances must produce NO offer (0), and the demand can never exceed
 * the actual balance, so an accepted offer is always payable at creation time.
 */
class RansomCalculatorTest {

    private static final double PERCENT = 0.15;
    private static final int MIN = 100;
    private static final int MAX = 10_000;

    @Test
    void balanceBelowMinimumProducesNoOffer() {
        assertEquals(0, RansomCalculator.computeRansomAmount(99, PERCENT, MIN, MAX));
        assertEquals(0, RansomCalculator.computeRansomAmount(1, PERCENT, MIN, MAX));
    }

    @Test
    void zeroAndNegativeBalancesProduceNoOffer() {
        assertEquals(0, RansomCalculator.computeRansomAmount(0, PERCENT, MIN, MAX));
        assertEquals(0, RansomCalculator.computeRansomAmount(-500, PERCENT, MIN, MAX));
    }

    @Test
    void percentageIsAppliedBetweenFloorAndCap() {
        // 10_000 * 0.15 = 1_500 — inside [100, 10_000]
        assertEquals(1_500, RansomCalculator.computeRansomAmount(10_000, PERCENT, MIN, MAX));
    }

    @Test
    void minimumFloorsSmallPercentages() {
        // 500 * 0.15 = 75 → floored to 100, balance 500 can cover it
        assertEquals(100, RansomCalculator.computeRansomAmount(500, PERCENT, MIN, MAX));
    }

    @Test
    void flooredAmountIsStillCappedByBalance() {
        // balance 100 (== min): 100 * 0.15 = 15 → floored to 100 → capped at balance 100
        assertEquals(100, RansomCalculator.computeRansomAmount(100, PERCENT, MIN, MAX));
    }

    @Test
    void maximumCapsRichColonies() {
        // 1_000_000 * 0.15 = 150_000 → capped to 10_000
        assertEquals(MAX, RansomCalculator.computeRansomAmount(1_000_000, PERCENT, MIN, MAX));
    }

    @Test
    void fullPercentTakesWholeBalanceUpToCap() {
        assertEquals(5_000, RansomCalculator.computeRansomAmount(5_000, 1.0, MIN, MAX));
        assertEquals(MAX, RansomCalculator.computeRansomAmount(50_000, 1.0, MIN, MAX));
    }

    @Test
    void zeroPercentStillFloorsToMinimum() {
        // documented floor behaviour: percent 0 with sufficient balance demands the minimum
        assertEquals(MIN, RansomCalculator.computeRansomAmount(1_000, 0.0, MIN, MAX));
    }

    @Test
    void roundingIsHalfUp() {
        // 1_003 * 0.15 = 150.45 → 150; 1_010 * 0.15 = 151.5 → 152
        assertEquals(150, RansomCalculator.computeRansomAmount(1_003, PERCENT, MIN, MAX));
        assertEquals(152, RansomCalculator.computeRansomAmount(1_010, PERCENT, MIN, MAX));
    }

    @Test
    void cooldownKeyIsDirectional() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(a + ":" + b, RansomCalculator.cooldownKey(a, b));
        assertNotEquals(RansomCalculator.cooldownKey(a, b), RansomCalculator.cooldownKey(b, a));
    }
}
