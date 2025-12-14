# War Penalties System - Complete Feature Reference

> **Last Updated**: 2025-12-14  
> **Status**: Implemented  
> **Branch**: `feature/war-penalties`

## Overview

The War Penalties system adds economic consequences to warfare, preventing endless back-to-back wars and creating strategic recovery periods. It consists of two main components:

1. **War Exhaustion**: Colonies at war generate reduced taxes, with gradual recovery after war ends
2. **War Reparations**: Colonies that lose multiple wars in quick succession face additional tax penalties

---

## Configuration (TaxConfig.java)

All config values already exist in `TaxConfig.java`:

### War Exhaustion
| Config Key | Type | Default | Description |
|------------|------|---------|-------------|
| `EnableWarExhaustion` | Boolean | `true` | Master toggle for exhaustion system |
| `WarTaxReductionPercent` | Double | `0.30` | Tax reduction during active war (30%) |
| `PostWarRecoveryHours` | Int | `48` | Hours for full tax recovery after war |

### War Reparations
| Config Key | Type | Default | Description |
|------------|------|---------|-------------|
| `EnableWarReparations` | Boolean | `true` | Master toggle for reparations |
| `ReparationsTaxPenaltyPercent` | Double | `0.20` | Additional tax penalty (20%) |
| `ReparationsDurationHours` | Int | `72` | Duration of reparations debuff |
| `ReparationsTriggerLossesCount` | Int | `3` | Losses in 7 days to trigger |

---

## Core Logic: `WarExhaustionManager.java`

**Path**: `src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java`

### Data Tracked
```java
Map<Integer, Long> COLONIES_AT_WAR;    // colonyId -> war start time
Map<Integer, Long> RECOVERY_STATUS;    // colonyId -> war end time
Map<Integer, List<Long>> WAR_LOSSES;   // colonyId -> loss timestamps (7-day window)
Map<Integer, Long> REPARATIONS;        // colonyId -> reparations expiry time
```

### Key Methods

| Method | Purpose |
|--------|---------|
| `applyWarStatus(colonyId)` | Mark colony as "at war" (called on war start) |
| `removeWarStatus(colonyId)` | End war status, start recovery period |
| `recordWarLoss(colonyId)` | Record a loss, check if reparations should trigger |
| `getTaxMultiplier(colonyId)` | Get combined multiplier for tax generation |
| `isAtWar(colonyId)` | Check if colony is currently at war |
| `isInRecovery(colonyId)` | Check if colony is in post-war recovery |
| `hasReparations(colonyId)` | Check if colony is under reparations debuff |

### Tax Multiplier Logic

```
getTaxMultiplier(colonyId):
   1. If at war → return 0.70 (30% reduction)
   2. If in recovery → linear interpolation from 0.70 to 1.0 over 48h
   3. If under reparations → additional 20% reduction (multiplicative)
   4. Minimum: 0.10 (10% tax - never fully 0)
```

**Recovery Curve Example (48h):**
- Hour 0: 70% tax
- Hour 12: 77.5% tax
- Hour 24: 85% tax
- Hour 36: 92.5% tax
- Hour 48: 100% tax (full recovery)

### Persistence
**File**: `config/warntax/war_exhaustion.json`

---

## Integration Points

### 1. WarSystem.java

**War Start** (`initiateWar()`):
```java
// Apply War Exhaustion - both colonies generate less tax during war
WarExhaustionManager.applyWarStatus(colony.getID());
if (attackerColony != null) {
    WarExhaustionManager.applyWarStatus(attackerColony.getID());
}
```

**War End** (`endWar()`):
```java
// Remove War Exhaustion status and start recovery period
WarExhaustionManager.removeWarStatus(colony.getID());
if (warData != null && warData.getAttackerColony() != null) {
    WarExhaustionManager.removeWarStatus(warData.getAttackerColony().getID());
}
```

**Victory Check** (`checkForVictory()`):
```java
// Record war loss for loser (triggers reparations if 3+ losses in 7 days)
if (defendersWin) {
    WarExhaustionManager.recordWarLoss(attackerColony.getID()); // Attacker lost
} else if (attackersWin) {
    WarExhaustionManager.recordWarLoss(defenderColony.getID()); // Defender lost
}
```

### 2. TaxManager.java

**Tax Generation** (`generateTaxesForAllColonies()`):
```java
// Apply War Exhaustion Multiplier
double warExhaustionMultiplier = WarExhaustionManager.getTaxMultiplier(colonyId);
int generatedTax = (int) (taxWithHappiness * raidPenaltyMultiplier * warExhaustionMultiplier);
```

### 3. MineColonyTax.java

**Server Start**:
```java
WarExhaustionManager.initialize(event.getServer());
```

**Server Stop**:
```java
WarExhaustionManager.shutdown();
```

---

## Multiplayer Implications

### Gameplay Impact
1. **War Deterrent**: Colonies lose 30% tax during war - encourages shorter conflicts
2. **Recovery Period**: 48h recovery prevents back-to-back wars on same colony
3. **Repeated Loss Penalty**: Losing 3+ wars in 7 days → 72h reparations (20% penalty)
4. **Strategic Depth**: Players must weigh war benefits vs. economic costs

### Balance Notes
- Reparations only affect the **loser** of each war
- Recovery period applies to **both** attacker and defender colonies
- Penalties stack multiplicatively with raid penalties
- Minimum 10% tax ensures colonies are never completely crippled

---

## Manual Testing Guide

### Test 1: War Exhaustion During War
1. Start a war between Colony A and Colony B
2. Check tax generation for both colonies
3. **Expected**: Both colonies generate 30% less tax

### Test 2: Post-War Recovery
1. End the war (one side wins or timer expires)
2. Immediately check tax generation for both colonies
3. **Expected**: Tax still reduced (near 70%)
4. Wait or simulate time passing
5. **Expected**: Tax gradually increases to 100% over 48h

### Test 3: War Reparations Trigger
1. Have Colony A lose 3 wars within 7 days
2. Check Colony A's tax generation
3. **Expected**: Additional 20% penalty on top of any recovery penalty

### Test 4: Persistence
1. Restart server while a colony is in recovery/reparations
2. Check status after restart
3. **Expected**: State is preserved, recovery continues from where it was

### Test 5: Config Toggles
1. Set `EnableWarExhaustion=false` in config
2. Start a war
3. **Expected**: No tax reduction during war or recovery
4. (Reparations should still work if `EnableWarReparations=true`)

---

## File Changes Summary

| File | Change |
|------|--------|
| **[NEW]** `economy/WarExhaustionManager.java` | Core exhaustion/reparations logic |
| **[MODIFY]** `WarSystem.java` | war start/end hooks, loss recording |
| **[MODIFY]** `TaxManager.java` | Apply exhaustion multiplier |
| **[MODIFY]** `MineColonyTax.java` | Initialize/shutdown manager |

---

## For AI Agents: Key Editing Points

1. **Adjust recovery curve**: Modify `calculateRecoveryMultiplier()` method
2. **Change reparations trigger**: Modify `REPARATIONS_TRIGGER_LOSSES_COUNT` config and `recordWarLoss()` logic
3. **Add admin commands**: Add methods like `clearExhaustion(colonyId)` and wire to command handler
4. **Stack different penalties**: Modify `getTaxMultiplier()` to add new penalty sources
5. **GUI integration**: Use `getRemainingRecoveryHours()` and `getRemainingReparationsHours()` for display
