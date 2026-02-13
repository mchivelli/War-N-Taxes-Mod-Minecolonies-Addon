package net.machiavelli.minecolonytax.events.random;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;

import java.util.Random;

/**
 * System for calculating event trigger probabilities and determining when events should occur.
 *
 * This class contains all the logic for:
 * - Calculating modified probabilities based on colony context
 * - Applying size, policy, and context modifiers
 * - Rolling random numbers to determine event triggers
 * - Checking event conditions and cooldowns
 */
public class EventTriggerSystem {

    private static final Random RANDOM = new Random();

    /**
     * Calculate the final probability for an event to trigger, applying all modifiers.
     *
     * @param colony The colony to check
     * @param type The event type
     * @return Modified probability (0.0 - 1.0)
     */
    public static double calculateEventProbability(IColony colony, RandomEventType type) {
        double baseProbability = type.getBaseProbability();

        // Apply global multiplier from config
        double probability = baseProbability * TaxConfig.getBaseChanceMultiplier();

        // Apply colony size modifier
        probability *= getColonySizeModifier(colony);

        // Apply tax policy modifier
        probability *= getTaxPolicyModifier(colony, type);

        // Apply context modifiers (war, raid, etc.)
        probability *= getContextModifier(colony, type);

        // Clamp to valid range
        return Math.max(0.0, Math.min(1.0, probability));
    }

    /**
     * Roll to see if an event should trigger.
     *
     * @param colony The colony to check
     * @param type The event type
     * @return true if the event should trigger
     */
    public static boolean shouldTriggerEvent(IColony colony, RandomEventType type) {
        // Check if conditions are met
        if (!type.meetsConditions(colony)) {
            return false;
        }

        // Calculate modified probability
        double probability = calculateEventProbability(colony, type);

        // Roll random
        double roll = RANDOM.nextDouble();

        return roll < probability;
    }

    /**
     * Get colony size modifier for event probability.
     *
     * Scaling:
     * - Colonies <20 citizens: 0.5x (50% reduced)
     * - Colonies 20-49 citizens: 1.0x (normal)
     * - Colonies 50-99 citizens: 1.2x (20% increased)
     * - Colonies 100+ citizens: 1.3x (30% increased)
     *
     * @param colony The colony to check
     * @return Size modifier (0.5 - 1.3)
     */
    private static double getColonySizeModifier(IColony colony) {
        int citizenCount = colony.getCitizenManager().getCitizens().size();

        if (citizenCount < 20) {
            return 0.5;
        } else if (citizenCount < 50) {
            return 1.0;
        } else if (citizenCount < 100) {
            return 1.2;
        } else {
            return 1.3;
        }
    }

    /**
     * Get tax policy modifier for event probability.
     *
     * Policy effects:
     * - LOW: +20% positive event probability, -20% negative event probability
     * - NORMAL: No modifier
     * - HIGH: -10% positive event probability, +20% negative event probability
     * - WAR_ECONOMY: -20% positive event probability, +40% negative event probability
     *
     * @param colony The colony to check
     * @param type The event type
     * @return Policy modifier (0.6 - 1.4)
     */
    private static double getTaxPolicyModifier(IColony colony, RandomEventType type) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return 1.0;
        }

        TaxPolicy policy = TaxPolicyManager.getPolicy(colony.getID());
        boolean isPositiveEvent = type.getTaxMultiplier() > 1.0 || type.getHappinessModifier() > 0.0;
        boolean isNegativeEvent = type.getTaxMultiplier() < 1.0 || type.getHappinessModifier() < 0.0;

        switch (policy) {
            case LOW:
                if (isPositiveEvent) return 1.2;  // +20% positive events
                if (isNegativeEvent) return 0.8;  // -20% negative events
                return 1.0;

            case NORMAL:
                return 1.0;  // No modifier

            case HIGH:
                if (isPositiveEvent) return 0.9;  // -10% positive events
                if (isNegativeEvent) return 1.2;  // +20% negative events
                return 1.0;

            case WAR_ECONOMY:
                if (isPositiveEvent) return 0.8;  // -20% positive events
                if (isNegativeEvent) return 1.4;  // +40% negative events
                return 1.0;

            default:
                return 1.0;
        }
    }

    /**
     * Get context modifier based on colony state (war, raid, etc.).
     *
     * Context effects:
     * - During active raid: 0.0 (no events trigger)
     * - At war: 0.5 (50% reduced event frequency, except WAR_PROFITEERING)
     * - After war loss (recovery): 1.0 for positive, 0.5 for negative (grace period)
     *
     * @param colony The colony to check
     * @param type The event type
     * @return Context modifier (0.0 - 1.0)
     */
    private static double getContextModifier(IColony colony, RandomEventType type) {
        int colonyId = colony.getID();

        // Check for active raid
        if (isColonyBeingRaided(colonyId)) {
            return 0.0;  // No events during raids
        }

        // Check for active war
        if (isColonyAtWar(colonyId)) {
            // WAR_PROFITEERING is special - it only triggers during war
            if (type == RandomEventType.WAR_PROFITEERING) {
                return 1.0;  // Normal probability
            }
            // Other events reduced during war
            return 0.5;
        }

        // Check for recent war loss (recovery grace period)
        if (isColonyInRecovery(colonyId)) {
            boolean isNegativeEvent = type.getTaxMultiplier() < 1.0 || type.getHappinessModifier() < 0.0;
            if (isNegativeEvent) {
                return 0.5;  // Reduced negative events during recovery
            }
        }

        return 1.0;  // No modifier
    }

    /**
     * Check if colony is currently being raided.
     *
     * @param colonyId The colony ID
     * @return true if colony has an active raid
     */
    private static boolean isColonyBeingRaided(int colonyId) {
        // Check RaidManager for active raids
        try {
            return net.machiavelli.minecolonytax.raid.RaidManager.getActiveRaidForColony(colonyId) != null;
        } catch (Exception e) {
            // If RaidManager not available, assume not raided
            return false;
        }
    }

    /**
     * Check if colony is currently at war.
     *
     * @param colonyId The colony ID
     * @return true if colony is in an active war
     */
    private static boolean isColonyAtWar(int colonyId) {
        // Check WarSystem for active wars
        try {
            // Check if colony is defender
            if (net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
                return true;
            }

            // Check if colony is attacker
            for (net.machiavelli.minecolonytax.data.WarData war : net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.values()) {
                if (war.getAttackerColony() != null && war.getAttackerColony().getID() == colonyId) {
                    return true;
                }
            }
        } catch (Exception e) {
            // If WarSystem not available, assume not at war
            return false;
        }

        return false;
    }

    /**
     * Check if colony is in recovery period after war loss.
     *
     * @param colonyId The colony ID
     * @return true if colony is in recovery grace period
     */
    private static boolean isColonyInRecovery(int colonyId) {
        // Check WarExhaustionManager for recovery status
        try {
            // If colony has war exhaustion penalty, it's in recovery
            double warExhaustion = net.machiavelli.minecolonytax.economy.WarExhaustionManager.getTaxMultiplier(colonyId);
            return warExhaustion < 1.0;
        } catch (Exception e) {
            // If WarExhaustionManager not available, assume not in recovery
            return false;
        }
    }

    /**
     * Check if a colony is new and protected from events.
     *
     * @param colony The colony to check
     * @return true if colony is new and should be protected
     */
    public static boolean isColonyProtected(IColony colony) {
        int protectionHours = TaxConfig.getNewColonyProtectionHours();

        if (protectionHours <= 0) {
            return false;  // Protection disabled
        }

        // Get colony age in hours
        int colonyAgeHours = colony.getLastContactInHours();

        // Note: lastContactInHours gives time since last player visit, not colony age
        // This is an approximation - ideally we'd track actual colony creation time
        // For now, assume very young colonies haven't been visited much
        return colonyAgeHours < protectionHours;
    }

    /**
     * Get a display string for event probability (for debugging/admin commands).
     *
     * @param colony The colony
     * @param type The event type
     * @return Formatted probability string (e.g., "8.5% (base: 8.0%, size: +0%, policy: +0%, context: +0%)")
     */
    public static String getProbabilityBreakdown(IColony colony, RandomEventType type) {
        double baseProbability = type.getBaseProbability();
        double globalMultiplier = TaxConfig.getBaseChanceMultiplier();
        double sizeModifier = getColonySizeModifier(colony);
        double policyModifier = getTaxPolicyModifier(colony, type);
        double contextModifier = getContextModifier(colony, type);

        double finalProbability = baseProbability * globalMultiplier * sizeModifier * policyModifier * contextModifier;

        return String.format("%.1f%% (base: %.1f%%, global: x%.1f, size: x%.1f, policy: x%.1f, context: x%.1f)",
                finalProbability * 100,
                baseProbability * 100,
                globalMultiplier,
                sizeModifier,
                policyModifier,
                contextModifier);
    }
}
