package net.machiavelli.minecolonytax.ransom;

import java.util.UUID;

/**
 * Pure ransom math — no Minecraft or config dependencies, fully unit-testable.
 *
 * <p>Design rules (fixes for the v1 implementation):
 * <ul>
 *   <li>If the colony balance is below the configured minimum, NO offer is created
 *       (v1 floored poor colonies up to an unpayable minimum, then consumed the offer
 *       on a failing accept).</li>
 *   <li>The result is always capped at the actual balance so an accepted offer can
 *       never fail for insufficient funds at creation time.</li>
 * </ul>
 */
public final class RansomCalculator {

    private RansomCalculator() {
    }

    /**
     * Computes the ransom amount for a colony.
     *
     * @param taxBalance the victim colony's current stored tax balance
     * @param percent    fraction of the balance to demand (0.0–1.0)
     * @param min        minimum ransom amount (floor of the percentage calculation)
     * @param max        maximum ransom amount (cap of the percentage calculation)
     * @return the amount to demand, or {@code 0} if no offer should be created
     *         (balance below {@code min} or not positive)
     */
    public static int computeRansomAmount(int taxBalance, double percent, int min, int max) {
        if (taxBalance <= 0 || taxBalance < min) {
            return 0;
        }
        int calculated = (int) Math.round(taxBalance * percent);
        int clamped = Math.max(min, Math.min(max, calculated));
        return Math.min(clamped, taxBalance);
    }

    /**
     * Directional cooldown key: the same attacker cannot re-demand from the same
     * victim during the cooldown, but other pairings are unaffected.
     */
    public static String cooldownKey(UUID attacker, UUID victim) {
        return attacker.toString() + ":" + victim.toString();
    }
}
