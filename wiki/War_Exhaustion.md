# War Exhaustion, Reparations, and Weariness

Wars leave a mark on your colony's economy long after the fighting stops. This page explains the three post-war economic systems: war exhaustion (reduced tax generation during and after war), war reparations (a harsher penalty for repeat losers), and war weariness protection (a comeback mechanic for severely weakened colonies).

---

## War Exhaustion

**Config:** `EnableWarExhaustion` (default: true)

War exhaustion represents the economic strain of sustained conflict. It has two phases.

### During Active War

While your colony is at war, tax generation is reduced by `WarTaxReductionPercent` (default: 30%). If your colony normally earns 1,000 coins per tax cycle, it earns 700 instead. This applies for the entire duration of the war.

Additionally, immediately after a war ends in a loss or stalemate, the colony's tax generation is skipped entirely for `WarTaxFreezeCycles` (default: 3) tax cycles. At the default interval of 60 minutes per cycle, that is 3 skipped cycles — approximately 3 hours with no income.

### Post-War Recovery

After the freeze cycles complete, recovery begins. The colony does not snap back to full income immediately. Instead, tax generation climbs linearly from the war-level reduced rate back to 100% over `PostWarRecoveryHours` (default: 48 hours).

For example, with a 30% war reduction and a 48-hour recovery period:
- At the start of recovery: 70% of normal income
- At the 24-hour mark: approximately 85% of normal income
- At 48 hours: 100%, fully recovered

Recovery state persists through server restarts. The mod saves the war-end timestamp and continues the linear climb on next startup.

---

## War Reparations

**Config:** `EnableWarReparations` (default: true)

Reparations are a separate, stacking penalty triggered only by repeated losses. A single war loss does not trigger reparations.

### Trigger Condition

If a colony loses `ReparationsTriggerLossesCount` (default: 3) wars within a rolling 7-day window, the reparations debuff activates immediately. The loss counter resets once reparations are applied, so reparations cannot stack on top of themselves from the same streak.

### The Debuff

While under reparations, the colony's tax generation is reduced by an additional `ReparationsTaxPenaltyPercent` (default: 20%) on top of any exhaustion penalty already present. Reparations last for `ReparationsDurationHours` (default: 72 hours).

### Vassalization Exception

**Config:** `VassalizationReplacesReparations` (default: true)

If a colony is vassalized as the outcome of a war loss, the ongoing vassal tribute replaces the reparations debuff. The two penalties do not stack — a vassalized colony pays tribute instead of suffering the reparations tax cut.

---

## War Weariness Protection

**Config:** `EnableWarWeariness` (default: true)

War weariness is a protection mechanic, not a penalty. It exists to prevent players from repeatedly targeting an already-weakened colony.

### How It Works

Every time a colony loses a war, the mod checks the colony's current tax balance. If that balance is at or below `WarWearinessThreshold` (default: 500 coins), the colony is granted temporary immunity from war declarations for `WarWearinessImmunityHours` (default: 24 hours).

During immunity:
- No other player can declare war on the protected colony
- The protected colony also cannot declare war on others

This window gives a nearly-bankrupt colony time to rebuild its tax balance before being targeted again.

> Note: War immunity is not automatic after every loss. It only triggers when the colony's tax balance is at or below the threshold at the moment of defeat. A wealthy colony that loses a war receives no immunity.

---

## How the Penalties Stack

All three systems can be active on the same colony simultaneously. The mod applies them in this order:

1. **Active war:** tax multiplier reduced by `WarTaxReductionPercent`
2. **Recovery:** multiplier climbs linearly from the reduced rate back to 1.0
3. **Reparations:** an additional multiplicative reduction of `ReparationsTaxPenaltyPercent`

A combined cap of `MaxCombinedWarPenaltyPercent` (default: 50%) prevents the stacked penalties from exceeding half of normal income. On top of that, a hard floor of 10% tax generation is enforced — no colony can be reduced to zero income by war penalties alone.

The global `MinTaxGenerationPercent` (default: 30%) acts as a wider safety net across all penalty sources (raids, events, and war), ensuring colonies always earn at least 30% of their base income regardless of how many systems are penalizing them at once.

### Example

A colony under reparations that also loses another war immediately:
- Base income: 1,000 coins
- Active war reduction (30%): 700 coins
- Reparations reduction (20% of remaining): 560 coins
- Combined penalty cap check: 50% cap means floor is 500 coins — 560 passes
- Hard floor check: 10% floor is 100 coins — 560 passes
- Result: 560 coins per cycle until the war ends, then linear recovery while reparations continue

---

## Config Reference

All keys are in `minecolonytax.toml` under the `War Exhaustion`, `War Reparations`, and `War Weariness` sub-sections.

| Config Key | Default | Description |
|---|---|---|
| `EnableWarExhaustion` | true | Master toggle for war exhaustion and recovery |
| `WarTaxReductionPercent` | 0.30 | Tax reduction during active war (0.0–1.0) |
| `WarTaxFreezeCycles` | 3 | Tax cycles skipped immediately after a loss or stalemate |
| `PostWarRecoveryHours` | 48 | Hours to fully recover from war-level tax reduction |
| `MaxCombinedWarPenaltyPercent` | 0.50 | Cap on combined exhaustion + reparations penalty |
| `EnableWarReparations` | true | Master toggle for the reparations debuff |
| `ReparationsTriggerLossesCount` | 3 | Losses within 7 days needed to trigger reparations |
| `ReparationsTaxPenaltyPercent` | 0.20 | Additional tax penalty while under reparations (0.0–1.0) |
| `ReparationsDurationHours` | 72 | How long reparations last once triggered |
| `VassalizationReplacesReparations` | true | Vassal tribute replaces reparations instead of stacking |
| `EnableWarWeariness` | true | Master toggle for the weariness immunity mechanic |
| `WarWearinessThreshold` | 500 | Tax balance at or below which immunity is granted after a loss |
| `WarWearinessImmunityHours` | 24 | Hours of war immunity granted to eligible colonies |

---

## Strategies

**Minimize exhaustion duration:** End wars quickly. The exhaustion recovery timer starts the moment the war ends. A war that drags on for 2 hours delays your recovery by 2 hours compared to one that ends in 20 minutes.

**Avoid triggering reparations:** Three losses in seven days is the threshold. If you lost two wars recently, avoid aggressive play until the 7-day window resets. The loss history is per-colony, so losses are tracked individually.

**Use the weariness window:** If you are granted immunity after a loss, use those 24 hours to rebuild your tax balance above the weariness threshold. Once your balance recovers, you will no longer qualify for automatic immunity — but reparations and exhaustion will also have partially wound down.

**Accept vassalization over reparations:** If you are facing multiple recent losses and a vassalization outcome is on the table, accepting it may be economically preferable. Vassalization tribute replaces the reparations tax cut rather than adding to it, and vassalization ends after a fixed duration.

**Negotiate peace:** If you are losing a war and already carry one or two recent losses, settling via a peace treaty before the third loss prevents reparations from triggering entirely. See [War System](War_System) for peace proposal commands.

---

*See also: [War System](War_System) | [War Persistence](War_Persistence) | [Configuration Guide](Configuration_Guide)*
