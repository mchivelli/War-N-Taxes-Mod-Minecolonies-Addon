package net.machiavelli.minecolonytax.upgrade;

public enum UpgradeType {
    MILITIA,         // More citizens mobilize as guards during raids
    SPY_CAPACITY,    // More concurrent active spy missions
    SPY_SPEED,       // Reduced spy travel time
    SPY_EVASION,     // Reduced enemy detection chance against your spies
    RAID_FORCE,      // Raid party effectiveness multiplier
    DEFENSE,         // Guard resistance level bonus during raids
    TREASURY_CAP,    // Flat increase to max treasury capacity
    TAX_EFFICIENCY,  // Bonus % to tax generation per cycle
    FORTIFICATION,   // Reduces incoming siege/raid damage percentage
    COUNTER_INTEL    // Passive % chance to auto-detect incoming spy on arrival
}
