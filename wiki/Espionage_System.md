# Espionage System

War ‘N Taxes includes a full spy system that allows players to gather intelligence on rival colonies over time. Espionage is managed through the colony GUI and requires the `EnableSpySystem` config option to be enabled.

---

## 1. Deploying a Spy

Open the colony management GUI and navigate to the Espionage tab. Select a target colony and click Deploy. The spy does not arrive instantly; they must travel there first.

- **Travel time** is calculated from the distance between your colony and the target. Travel speed is set by `SpyTravelBlocksPerMinute` (default: 200 blocks per minute), with a minimum of `SpyTravelMinMinutes` (default: 1) and a maximum of `SpyTravelMaxMinutes` (default: 15).
- While traveling, the mission shows a **Traveling** status with a percentage and estimated arrival time.
- The spy entity does not exist in the world until they arrive. You cannot be caught during travel.
- You can recall a spy while they are still traveling. The mission cancels immediately with no penalty.
- You are limited to `SpyMaxActivePerPlayer` (default: 3) active missions at once. You cannot send two spies to the same target colony simultaneously.

---

> **Screenshot placeholder:** *The espionage tab in the colony management GUI showing an active spy mission — ACTIVE status with the three intel tier indicators on the right, and the target colony name and field time displayed*

## 2. Intelligence Gathering

Once a spy arrives, they begin gathering intelligence passively. Intel is revealed in **three progressive tiers** over time. You do not need to issue any commands; the spy gathers automatically.

| Tier | Unlocks After | Information Revealed |
|------|--------------|----------------------|
| Early | `IntelEarlyThresholdMinutes` (default: 2 min) | Colony name, citizen count, building count, whether the colony is at war |
| Mid | `IntelMidThresholdMinutes` (default: 10 min) | Guard count, colony happiness, treasury balance |
| Late | `IntelLateThresholdMinutes` (default: 30 min) | Owner name, owner balance, full officer list with balances, complete member roster with ranks and online status |

The longer your spy stays deployed, the more detailed the picture you get. Early intel is enough for a quick assessment before a raid. Late intel gives you a full strategic picture for a war.

### Intel Book
Gathered intelligence can be received as a written in-game book (Shadow Network report format), providing a formatted summary of everything your spy has collected so far.

---

## 3. What Can Go Wrong

### Detection
If the target colony’s guards detect your spy, the spy enters a **Flee** state rather than dying instantly. The spy will attempt to escape the colony border.

- The spy pathfinds toward the nearest exit from the colony
- A speed boost is applied during flight (`FleeSpeedMultiplier`, default: 1.5x normal speed)
- Escape is confirmed only after the spy has been outside the colony border for a sustained period
- If the spy escapes successfully, all gathered intel is preserved and marked as **Escaped** in your mission list
- If the spy is killed before escaping, all gathered intel is lost

### Recalling a Spy
You can recall an active spy at any time from the GUI. Recalling initiates a return journey (same travel time as deployment). Intel gathered up to the moment of recall is fully preserved once they return.

---

## 4. Mission History

Completed missions (escaped or recalled) remain visible in the GUI for up to one hour after completion. This lets you review recently gathered intel without it disappearing immediately.

Missions where the spy was killed are removed immediately.

---

## 5. Mission Types

When deploying a spy, you choose one of four mission types. Each has a different strategic purpose:

### Scout
The default mission type. The spy passively gathers progressive intelligence as described in Section 2. No direct impact on the target colony — this is purely informational. Full intel is delivered when the spy returns.

### Sabotage
The spy plants economic disruption inside the target colony. On successful completion, the target colony’s **next tax cycle income is reduced** by a configured percentage. The spy must remain in the field for a minimum duration for the sabotage to take effect. If the spy is detected and escapes before the minimum field time, no sabotage is planted. Multiple sabotage missions can stack (up to 100% tax reduction). The effect is consumed after one tax cycle.

### Bribe
The spy bribes a number of the target colony’s guards. On the target’s **next raid**, the bribed guards receive Weakness II and Slowness II debuffs for the entire raid duration, making them significantly less effective at defending. Bribe counts accumulate from multiple missions. The effect is consumed when the next raid begins.

### Steal
On successful completion, your colony receives a **tax income bonus** (default: +20%) for a configurable number of hours. Only one active steal buff can exist per colony at a time; a new steal mission overwrites the previous one.

All non-Scout mission types carry higher detection risk. Use them only when the strategic advantage justifies the exposure.

---

## 6. JourneyMap Integration

If JourneyMap is installed, active spy missions automatically create waypoints on your map — one marker at your colony (origin) and one at the target colony. Waypoint labels and colors update based on mission status (traveling, active, fleeing). Waypoints are removed when the mission ends.

---

## Configuration Reference

| Config Key | Default | Description |
|---|---|---|
| `EnableSpySystem` | true | Enable or disable the entire espionage system |
| `SpyMaxActivePerPlayer` | 3 | Maximum concurrent spy missions per player |
| `SpyTravelBlocksPerMinute` | 200 | Travel speed (blocks per minute) |
| `SpyTravelMinMinutes` | 1 | Minimum travel time regardless of distance |
| `SpyTravelMaxMinutes` | 15 | Maximum travel time cap |
| `IntelEarlyThresholdMinutes` | 2 | Minutes until Tier 1 intel unlocks |
| `IntelMidThresholdMinutes` | 10 | Minutes until Tier 2 intel unlocks |
| `IntelLateThresholdMinutes` | 30 | Minutes until Tier 3 intel unlocks |
| `FleeSpeedMultiplier` | 1.5 | Speed multiplier applied to a fleeing spy |
| `FleeEscapeDistance` | 5 | Blocks outside colony border needed to confirm escape |
| `FleeMaxSeconds` | 30 | Maximum time a spy can flee before being considered caught |
