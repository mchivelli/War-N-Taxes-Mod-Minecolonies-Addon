package net.machiavelli.minecolonytax.economy.policy;

import net.machiavelli.minecolonytax.TaxConfig;

/**
 * Represents the tax policy a colony can adopt.
 * Each policy affects both tax revenue generation and citizen happiness.
 */
public enum TaxPolicy {
    /**
     * Default policy - no modifiers applied.
     */
    NORMAL("Normal", "Balanced approach - no modifiers"),

    /**
     * Low tax policy - less revenue but happier citizens.
     */
    LOW("Low Tax", "Reduced revenue but improved citizen happiness"),

    /**
     * High tax policy - more revenue but unhappier citizens.
     */
    HIGH("High Tax", "Increased revenue but reduced citizen happiness"),

    /**
     * War economy policy - maximum revenue boost during wartime with happiness penalty.
     */
    WAR_ECONOMY("War Economy", "Maximum revenue boost at significant happiness cost");

    private final String displayName;
    private final String description;

    TaxPolicy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the revenue modifier for this policy.
     * @return multiplicative modifier (e.g., 0.75 = 25% less, 1.25 = 25% more)
     */
    public double getRevenueModifier() {
        return switch (this) {
            case NORMAL -> 1.0;
            case LOW -> 1.0 + TaxConfig.getTaxPolicyLowRevenueModifier(); // negative modifier
            case HIGH -> 1.0 + TaxConfig.getTaxPolicyHighRevenueModifier(); // positive modifier
            case WAR_ECONOMY -> 1.0 + TaxConfig.getTaxPolicyWarRevenueModifier(); // high positive modifier
        };
    }

    /**
     * Get the happiness modifier for this policy.
     * This affects the happiness multiplier calculation.
     * @return additive modifier to happiness growth rate
     */
    public double getHappinessModifier() {
        return switch (this) {
            case NORMAL -> 0.0;
            case LOW -> TaxConfig.getTaxPolicyLowHappinessModifier(); // positive modifier
            case HIGH -> TaxConfig.getTaxPolicyHighHappinessModifier(); // negative modifier
            case WAR_ECONOMY -> TaxConfig.getTaxPolicyWarHappinessModifier(); // negative modifier
        };
    }

    /**
     * Get the color code for display purposes.
     */
    public String getColorCode() {
        return switch (this) {
            case NORMAL -> "§f"; // white
            case LOW -> "§a"; // green
            case HIGH -> "§c"; // red
            case WAR_ECONOMY -> "§6"; // gold
        };
    }

    /**
     * Parse a policy from string (case-insensitive).
     * @param name the policy name
     * @return the TaxPolicy or null if not found
     */
    public static TaxPolicy fromString(String name) {
        if (name == null) return null;
        String normalized = name.toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return TaxPolicy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try display names
            for (TaxPolicy policy : values()) {
                if (policy.displayName.equalsIgnoreCase(name) ||
                    policy.name().equalsIgnoreCase(name)) {
                    return policy;
                }
            }
            return null;
        }
    }
}
