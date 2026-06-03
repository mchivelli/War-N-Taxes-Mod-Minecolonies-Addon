package net.machiavelli.minecolonytax.events.random;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.ChatFormatting;

/**
 * Enumeration of all random event types.
 *
 * Each event has:
 * - Tax multiplier (1.0 = no change, 0.8 = -20%, 1.2 = +20%)
 * - Happiness modifier (additive, e.g., -0.3, +0.5)
 * - Duration in tax cycles
 * - Condition checking logic
 * - Base probability per tax cycle
 *
 * Phase 1: Implementing 6 basic events
 */
public enum RandomEventType {

    // ==================== POSITIVE EVENTS ====================

    MERCHANT_CARAVAN(
            "Merchant Caravan",
            "A wealthy merchant caravan has arrived, boosting trade",
            1.15, // +15% tax
            0.3, // +0.3 happiness
            2, // 2 cycles
            0.08, // 8% base probability
            4, // 4 cycle cooldown
            ChatFormatting.GREEN) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require 3+ markets/taverns, not at war
            int marketCount = countBuildingsOfType(colony, "restaurant", "tavern");
            return marketCount >= 3 && !isColonyAtWar(colony);
        }
    },

    BOUNTIFUL_HARVEST(
            "Bountiful Harvest",
            "Exceptional crop yields have blessed the colony",
            1.20, // +20% tax
            0.4, // +0.4 happiness
            1, // 1 cycle
            0.06, // 6% base probability
            5, // 5 cycle cooldown
            ChatFormatting.GREEN) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require 20+ citizens
            return colony.getCitizenManager().getCitizens().size() >= 20;
        }
    },

    CULTURAL_FESTIVAL(
            "Cultural Festival",
            "A grand festival raises spirits despite costs",
            0.95, // -5% tax (event costs)
            0.5, // +0.5 happiness
            1, // 1 cycle
            0.07, // 7% base probability
            6, // 6 cycle cooldown
            ChatFormatting.AQUA) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require happiness >6.0
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            return happiness > 6.0;
        }
    },

    // ==================== NEGATIVE EVENTS ====================

    FOOD_SHORTAGE(
            "Food Shortage",
            "Dwindling supplies reduce productivity and morale",
            0.85, // -15% tax
            -0.3, // -0.3 happiness
            2, // 2 cycles
            0.06, // 6% base probability
            4, // 4 cycle cooldown
            ChatFormatting.YELLOW) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // More likely if happiness <5.0
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            return happiness < 5.0;
        }
    },

    DISEASE_OUTBREAK(
            "Disease Outbreak",
            "Sickness spreads through the colony",
            0.80, // -20% tax
            -0.4, // -0.4 happiness
            3, // 3 cycles
            0.05, // 5% base probability
            8, // 8 cycle cooldown
            ChatFormatting.RED) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require 40+ citizens, happiness <5.0, no Hospital L3+
            int citizenCount = colony.getCitizenManager().getCitizens().size();
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            int hospitalLevel = getMaxBuildingLevel(colony, "hospital");

            return citizenCount >= 40 && happiness < 5.0 && hospitalLevel < 3;
        }
    },

    BANDIT_HARASSMENT(
            "Bandit Harassment",
            "Bandits harass trade routes and workers",
            0.90, // -10% tax
            -0.2, // -0.2 happiness
            2, // 2 cycles
            0.07, // 7% base probability
            4, // 4 cycle cooldown
            ChatFormatting.YELLOW) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // More likely if <3 guard towers
            int guardTowers = countBuildingsOfType(colony, "guardtower");
            return guardTowers < 3;
        }
    },

    // ==================== POSITIVE EVENTS (continued) ====================

    SUCCESSFUL_RECRUITMENT(
            "Successful Recruitment",
            "New skilled workers join the colony",
            1.10, // +10% tax
            0.2, // +0.2 happiness
            3, // 3 cycles
            0.06, // 6% base probability
            5, // 5 cycle cooldown
            ChatFormatting.GREEN) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require happiness >5.5, citizen count >15
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            int citizenCount = colony.getCitizenManager().getCitizens().size();
            return happiness > 5.5 && citizenCount > 15;
        }
    },

    // ==================== NEGATIVE EVENTS (continued) ====================

    CORRUPT_OFFICIAL(
            "Corrupt Official",
            "Embezzlement discovered in colony administration",
            0.75, // -25% tax (instant loss)
            -0.1, // -0.1 happiness
            1, // 1 cycle (instant effect)
            0.04, // 4% base probability
            10, // 10 cycle cooldown
            ChatFormatting.RED) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Only under HIGH or WAR_ECONOMY tax policy
            net.machiavelli.minecolonytax.economy.policy.TaxPolicy policy = net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager
                    .getPolicy(colony.getID());
            return policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.HIGH ||
                    policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.WAR_ECONOMY;
        }
    },

    // ==================== NEUTRAL/CHOICE EVENTS ====================

    WANDERING_TRADER_OFFER(
            "Wandering Trader Offer",
            "A wandering trader buys colony goods at premium prices",
            1.20, // +20% tax (premium sale)
            0.1,  // +0.1 happiness
            1, // 1 cycle
            0.05, // 5% base probability
            6, // 6 cycle cooldown
            ChatFormatting.LIGHT_PURPLE) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require stored tax >500, not at war
            int storedTax = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(colony);
            return storedTax > 500 && !isColonyAtWar(colony);
        }
    },

    NEIGHBORING_ALLIANCE(
            "Neighboring Alliance",
            "A nearby settlement forms a trade pact, boosting morale and commerce",
            1.05, // +5% tax (trade cooperation)
            0.3,  // +0.3 happiness (morale boost)
            2, // 2 cycles
            0.04, // 4% base probability
            8, // 8 cycle cooldown
            ChatFormatting.AQUA) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Require happiness >6.0, not at war, >30 citizens
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            int citizenCount = colony.getCitizenManager().getCitizens().size();
            return happiness > 6.0 && citizenCount > 30 && !isColonyAtWar(colony);
        }
    },

    // ==================== SPECIAL CONTEXT EVENTS ====================

    WAR_PROFITEERING(
            "War Profiteering",
            "War drives demand for colony goods",
            1.35, // +35% tax
            -0.5, // -0.5 happiness (moral cost)
            3, // 3 cycles
            0.15, // 15% base probability (when at war)
            6, // 6 cycle cooldown
            ChatFormatting.GOLD) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Requires WAR_ECONOMY policy AND an active war
            net.machiavelli.minecolonytax.economy.policy.TaxPolicy policy = net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager
                    .getPolicy(colony.getID());
            return policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.WAR_ECONOMY
                    && isColonyAtWar(colony);
        }
    },

    GUARD_DESERTION(
            "Guard Desertion",
            "Guards abandon posts due to poor conditions",
            0.80, // -20% tax
            -0.4, // -0.4 happiness
            2, // 2 cycles
            0.08, // 8% base probability (when conditions met)
            8, // 8 cycle cooldown
            ChatFormatting.DARK_RED) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Happiness <3.0 with HIGH or WAR_ECONOMY policy
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            net.machiavelli.minecolonytax.economy.policy.TaxPolicy policy = net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager
                    .getPolicy(colony.getID());

            return happiness < 3.0 &&
                    (policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.HIGH ||
                            policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.WAR_ECONOMY);
        }
    },

    // ==================== DEEP INTEGRATION EVENTS ====================

    LABOR_STRIKE(
            "Labor Strike",
            "Workers refuse to work due to poor conditions",
            0.70, // -30% tax (workers stopped)
            -0.5, // -0.5 happiness
            2, // 2 cycles
            0.12, // 12% base probability (when conditions met)
            6, // 6 cycle cooldown
            ChatFormatting.DARK_RED) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Happiness <3.5, HIGH or WAR_ECONOMY policy, >30 citizens
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            net.machiavelli.minecolonytax.economy.policy.TaxPolicy policy = net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager
                    .getPolicy(colony.getID());
            int citizenCount = colony.getCitizenManager().getCitizens().size();

            return happiness < 3.5 && citizenCount > 30 &&
                    (policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.HIGH ||
                            policy == net.machiavelli.minecolonytax.economy.policy.TaxPolicy.WAR_ECONOMY);
        }
    },

    PLAGUE_OUTBREAK(
            "Plague Outbreak",
            "Deadly plague spreads through the colony",
            0.75, // -25% tax
            -0.6, // -0.6 happiness
            3, // 3 cycles
            0.05, // 5% base probability
            10, // 10 cycle cooldown
            ChatFormatting.DARK_RED) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Colony >50 citizens, no Hospital L3+
            int citizenCount = colony.getCitizenManager().getCitizens().size();
            int hospitalLevel = getMaxBuildingLevel(colony, "hospital");

            return citizenCount > 50 && hospitalLevel < 3;
        }
    },

    ROYAL_FEAST(
            "Royal Feast",
            "Crown provides feast for citizens",
            1.10, // +10% tax (productivity boost)
            0.6, // +0.6 happiness
            1, // 1 cycle
            0.06, // 6% base probability
            8, // 8 cycle cooldown
            ChatFormatting.GOLD) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Happiness >7.0, not at war
            double happiness = TaxManager.calculateColonyAverageHappiness(colony);
            return happiness > 7.0 && !isColonyAtWar(colony);
        }
    },

    CROP_BLIGHT(
            "Crop Blight",
            "Crop failure causes widespread hunger",
            0.75, // -25% tax
            -0.5, // -0.5 happiness
            2, // 2 cycles
            0.04, // 4% base probability
            8, // 8 cycle cooldown
            ChatFormatting.RED) {
        @Override
        public boolean meetsConditions(IColony colony) {
            // Colony has 5+ farms
            int farmCount = countBuildingsOfType(colony, "farm");
            return farmCount >= 5;
        }
    };

    // ==================== Enum Fields ====================

    private final String displayName;
    private final String description;
    private final double taxMultiplier;
    private final double happinessModifier;
    private final int durationCycles;
    private final double baseProbability;
    private final int cooldownCycles;
    private final ChatFormatting color;

    // ==================== Constructor ====================

    RandomEventType(String displayName, String description, double taxMultiplier,
            double happinessModifier, int durationCycles, double baseProbability,
            int cooldownCycles, ChatFormatting color) {
        this.displayName = displayName;
        this.description = description;
        this.taxMultiplier = taxMultiplier;
        this.happinessModifier = happinessModifier;
        this.durationCycles = durationCycles;
        this.baseProbability = baseProbability;
        this.cooldownCycles = cooldownCycles;
        this.color = color;
    }

    // ==================== Abstract Method ====================

    /**
     * Check if this event can trigger for the given colony.
     * Override for each event type.
     *
     * @param colony The colony to check
     * @return true if conditions are met for this event to trigger
     */
    public abstract boolean meetsConditions(IColony colony);

    // ==================== Getters ====================

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getTaxMultiplier() {
        return taxMultiplier;
    }

    public double getHappinessModifier() {
        return happinessModifier;
    }

    public int getDurationCycles() {
        return durationCycles;
    }

    public double getBaseProbability() {
        return baseProbability;
    }

    public int getCooldownCycles() {
        return cooldownCycles;
    }

    public ChatFormatting getColor() {
        return color;
    }

    // ==================== Helper Methods ====================

    /**
     * Check if a colony is currently a participant in any active war.
     * Delegates to EventTriggerSystem to keep war-check logic in one place.
     *
     * @param colony The colony to check
     * @return true if the colony is at war
     */
    protected static boolean isColonyAtWar(IColony colony) {
        return EventTriggerSystem.isColonyAtWar(colony.getID());
    }

    /**
     * Count buildings of specific types in the colony.
     *
     * @param colony The colony to search
     * @param types  Building type names to search for (partial match)
     * @return Number of matching buildings
     */
    protected static int countBuildingsOfType(IColony colony, String... types) {
        int count = 0;
        for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
            String buildingType = building.getBuildingType().getRegistryName().getPath();
            for (String type : types) {
                if (buildingType.contains(type)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * Get the maximum building level for a specific type.
     *
     * @param colony The colony to search
     * @param type   Building type name (partial match)
     * @return Maximum building level, or 0 if none found
     */
    protected static int getMaxBuildingLevel(IColony colony, String type) {
        int maxLevel = 0;
        for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
            String buildingType = building.getBuildingType().getRegistryName().getPath();
            if (buildingType.contains(type)) {
                maxLevel = Math.max(maxLevel, building.getBuildingLevel());
            }
        }
        return maxLevel;
    }

    /**
     * Check if colony has at least one building of a type.
     *
     * @param colony The colony to search
     * @param type   Building type name (partial match)
     * @return true if at least one matching building exists
     */
    protected static boolean hasBuildingOfType(IColony colony, String type) {
        return countBuildingsOfType(colony, type) > 0;
    }
}
