# Colony Investments System

The Colony Investments system lets you spend Treasury funds on permanent, per-colony upgrades. Each upgrade type improves a specific aspect of your colony and can be leveled up multiple times, with each level costing more than the last.

Investments are a strategic money sink — converting the Treasury reserves you accumulate between wars into lasting military, economic, or espionage advantages.

---

## How It Works

### Enabling the System

**Config:** `EnableColonyUpgrades` (default: true)

When enabled, all ten upgrade types become available to any colony that holds sufficient Treasury funds.

### Purchasing an Upgrade

**Command:** `/wnt invest buy <type>`

Each purchase deducts the cost directly from the colony's Treasury. If the Treasury does not have enough funds, the purchase fails.

The cost of each level scales exponentially:

```
Level Cost = BaseCost × (CostScalingFactor ^ currentLevel)
```

**Config:** `UpgradeCostScalingFactor` (default: 2.0) — each level costs twice the previous by default.

### Viewing Costs and Levels

| Command | Description |
|---|---|
| `/wnt invest list` | List all upgrade types with their current cost for your colony |
| `/wnt invest status` | Show your colony's current level for each upgrade type |
| `/wnt invest buy <type>` | Purchase the next level of an upgrade (funded from treasury) |

### Maximum Level

**Config:** `UpgradeMaxLevel` (default: 5) — no upgrade can exceed this level.

### Persistence

All upgrade levels are saved to `config/warntax/colony_upgrades.json` and survive server restarts.

---

## Upgrade Types

### MILITIA — Citizen Militia Size

Increases the proportion of citizens that convert into militia during raids.

- **Effect per level:** +10% militia size multiplier (`UpgradeMilitiaMultiplierPerLevel`, default: 0.10)
- **Base cost:** 500 (`UpgradeMilitiaCostBase`)
- **Example at level 3:** Militia size is 1.3x the base conversion percentage

### SPY_CAPACITY — Concurrent Spy Limit

Increases the number of spy missions you can have active at the same time.

- **Effect per level:** +1 additional active spy slot (`UpgradeSpyCapacityPerLevel`, default: 1)
- **Base cost:** 300 (`UpgradeSpyCapacityCostBase`)
- **Example at level 2:** Can run 2 extra concurrent spy missions beyond the base limit

### SPY_SPEED — Spy Travel Speed

Reduces how long it takes your spies to travel to target colonies.

- **Effect per level:** +20% travel speed multiplier (`UpgradeSpySpeedMultiplierPerLevel`, default: 0.20)
- **Base cost:** 200 (`UpgradeSpySpeedCostBase`)
- **Example at level 3:** Spies travel 60% faster, dramatically reducing the DEPLOYING phase

### SPY_EVASION — Detection Reduction

Reduces the chance that your spies are detected while on active missions.

- **Effect per level:** -5% detection chance (`UpgradeDetectionReductionPerLevel`, default: 0.05)
- **Base cost:** 400 (`UpgradeSpyEvasionCostBase`)
- **Example at level 4:** Each spy has 20% lower detection chance per check

### RAID_FORCE — Raiding Effectiveness

Increases attacker effectiveness during raids, allowing more damage and pressure on the defending colony.

- **Effect per level:** +10% raid force multiplier (`UpgradeRaidForceMultiplierPerLevel`, default: 0.10)
- **Base cost:** 600 (`UpgradeRaidForceCostBase`)
- **Example at level 2:** Raid force is 1.2x baseline

### DEFENSE — Guard Resistance Level

Increases the resistance buff applied to guards during raids and wars.

- **Effect per level:** +1 additional resistance level (`UpgradeDefenseBonusPerLevel`, default: 1)
- **Base cost:** 350 (`UpgradeDefenseCostBase`)
- **Example at level 3:** Guards receive 3 extra resistance levels on top of the base resistance

### TREASURY_CAP — Treasury Capacity

Permanently increases the maximum Treasury balance your colony can hold, beyond the global `TreasuryMaxCapacity` cap.

- **Effect per level:** +2,000 flat Treasury capacity (`UpgradeTreasuryCapFlatPerLevel`, default: 2000)
- **Base cost:** 3,000 (`UpgradeTreasuryCapCostBase`)
- **Example at level 5:** Treasury cap increased by 10,000

### TAX_EFFICIENCY — Tax Generation Bonus

Permanently increases how much tax your colony generates each cycle.

- **Effect per level:** +5% tax generation (`UpgradeTaxEfficiencyPercentPerLevel`, default: 0.05)
- **Base cost:** 4,000 (`UpgradeTaxEfficiencyCostBase`)
- **Example at level 5:** Colony generates 25% more tax each cycle before other modifiers

### FORTIFICATION — Siege and Raid Damage Reduction

Reduces incoming damage to the colony during sieges and raids. Capped at a maximum of 75% total reduction regardless of level.

- **Effect per level:** 5% damage reduction (`UpgradeFortificationDamageReductionPerLevel`, default: 0.05)
- **Base cost:** 3,500 (`UpgradeFortificationCostBase`)
- **Example at level 5:** 25% damage reduction (hard cap: 75%)

### COUNTER_INTEL — Auto-Detect Incoming Spies

Grants a per-level chance to automatically detect and neutralize an enemy spy the moment they arrive at your colony (during the DEPLOYING-to-ACTIVE transition). Capped at a maximum of 90% total detection chance.

- **Effect per level:** +10% auto-detection chance (`UpgradeCounterIntelDetectChancePerLevel`, default: 0.10)
- **Base cost:** 5,000 (`UpgradeCounterIntelCostBase`)
- **Example at level 5:** 50% chance to catch any arriving spy before they gather any intel

---

## Configuration Reference

All upgrade configuration keys live under the `[ColonyUpgrades]` section in `config/warntax/minecolonytax.toml`.

| Key | Default | Description |
|---|---|---|
| `EnableColonyUpgrades` | true | Enable or disable the entire investment system |
| `UpgradeMaxLevel` | 5 | Maximum level for each upgrade type |
| `UpgradeCostScalingFactor` | 2.0 | Cost multiplier per level (2.0 = each level costs double the previous) |
| `UpgradeMilitiaCostBase` | 500 | Base cost for MILITIA level 1 |
| `UpgradeSpyCapacityCostBase` | 300 | Base cost for SPY_CAPACITY level 1 |
| `UpgradeSpySpeedCostBase` | 200 | Base cost for SPY_SPEED level 1 |
| `UpgradeSpyEvasionCostBase` | 400 | Base cost for SPY_EVASION level 1 |
| `UpgradeRaidForceCostBase` | 600 | Base cost for RAID_FORCE level 1 |
| `UpgradeDefenseCostBase` | 350 | Base cost for DEFENSE level 1 |
| `UpgradeTreasuryCapCostBase` | 3000 | Base cost for TREASURY_CAP level 1 |
| `UpgradeTaxEfficiencyCostBase` | 4000 | Base cost for TAX_EFFICIENCY level 1 |
| `UpgradeFortificationCostBase` | 3500 | Base cost for FORTIFICATION level 1 |
| `UpgradeCounterIntelCostBase` | 5000 | Base cost for COUNTER_INTEL level 1 |
| `UpgradeMilitiaMultiplierPerLevel` | 0.10 | Militia multiplier bonus per MILITIA level |
| `UpgradeSpyCapacityPerLevel` | 1 | Extra spy slots per SPY_CAPACITY level |
| `UpgradeSpySpeedMultiplierPerLevel` | 0.20 | Speed multiplier per SPY_SPEED level |
| `UpgradeDetectionReductionPerLevel` | 0.05 | Detection chance reduction per SPY_EVASION level |
| `UpgradeDefenseBonusPerLevel` | 1 | Extra resistance levels per DEFENSE level |
| `UpgradeTreasuryCapFlatPerLevel` | 2000 | Flat treasury capacity increase per TREASURY_CAP level |
| `UpgradeTaxEfficiencyPercentPerLevel` | 0.05 | Tax generation bonus fraction per TAX_EFFICIENCY level |
| `UpgradeFortificationDamageReductionPerLevel` | 0.05 | Damage reduction fraction per FORTIFICATION level (max 75%) |
| `UpgradeCounterIntelDetectChancePerLevel` | 0.10 | Auto-detect chance per COUNTER_INTEL level (max 90%) |
| `UpgradeRaidForceMultiplierPerLevel` | 0.10 | Raid force multiplier per RAID_FORCE level |

---

## Funding Investments

Investments are funded exclusively from your colony's **Treasury**, not from your general tax balance. To build up Treasury funds:

- **Auto-deposit:** Each tax cycle, a small percentage of tax revenue deposits automatically (`TreasuryAutoDepositPercent`, default: 5%)
- **Manual deposit:** `/wnt treasury deposit <amount> [colonyId]`

See [War System — Treasury Economy](War_System#3-treasury-economy) for full Treasury mechanics.

---

## Strategic Notes

- **Cost scaling is steep.** At the default factor of 2.0, a MILITIA upgrade costs 500 at level 1, 1,000 at level 2, 2,000 at level 3, 4,000 at level 4, and 8,000 at level 5 — a total of 15,500 to max out one type.
- **Invest in what you use.** SPY_CAPACITY and SPY_SPEED are only valuable if you actively run espionage missions. DEFENSE and FORTIFICATION pay off most during server conflicts.
- **TAX_EFFICIENCY compounds.** At level 5 (+25% tax) every future tax cycle earns 25% more, which accelerates recovery of the investment cost over time.
- **COUNTER_INTEL is defensive espionage.** Colonies targeted by active spymasters should prioritize this — at level 5 there is a 50% chance any arriving spy is immediately neutralized.
