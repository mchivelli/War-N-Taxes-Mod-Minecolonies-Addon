# War Persistence System

**Added in:** v4.1.0

---

## Overview

Prior to this feature, **all active wars were lost on server stop or crash**. The `ACTIVE_WARS` map lived entirely in memory, meaning any server restart during an ongoing war would silently erase the conflict — no winner, no loser, no consequences. Players could also exploit this by forcing a restart to escape a losing war.

The War Persistence system serializes all active war state to disk on server shutdown and restores it on the next startup, ensuring wars survive restarts and crashes gracefully.

---

## What Changed (Files Modified)

### 1. `WarData.java` — Restoration Constructor

**What:** Added a second constructor specifically for rebuilding a `WarData` object from saved data.

**Why:** The original constructor calls `initializeGuards()` which scans the colony for guards and generates a fresh `warID`. When restoring a saved war, we need to inject the *exact* values from the save file — the original war ID, the original guard counts, the original lives — without recalculating anything.

**Details:**
- The new constructor accepts **all** war state fields (27 parameters) and assigns them directly
- It does **not** call `initializeGuards()` — guard IDs come from the save file
- Null-safe: all collection parameters are guarded with null checks before `putAll`/`addAll`
- A `setStalemateTriggered(boolean)` setter was also added since the field had no public mutator

---

### 2. `WarSystem.java` — Persistence Engine

This is the core of the feature. Four methods and two inner data classes were added at the end of the `WarSystem` class.

#### Inner Classes: `WarSaveEntry` and `WarSaveData`

Plain data holders for Gson serialization. `WarSaveEntry` mirrors every field of `WarData` but uses only JSON-safe types:
- **UUIDs** are stored as `String` (not `java.util.UUID`)
- **Lives maps** are `Map<String, Integer>` (UUID string keys)
- **Sets of UUIDs** (allies, spectators, etc.) are stored as `List<String>`
- **Colony references** are stored as `int` colony IDs (not `IColony` objects)
- **Boss bars** are not stored at all (recreated on load)
- **War status** is stored as its enum `name()` string

`WarSaveData` is simply a wrapper with a `List<WarSaveEntry>` field called `wars`.

#### `saveActiveWars()`

**When called:** During `ServerStoppingEvent`, before `TickScheduler.shutdown()`.

**What it does:**
1. Creates the directory `config/warntax/` if it doesn't exist
2. Iterates every entry in the `ACTIVE_WARS` concurrent map
3. For each `WarData`, creates a `WarSaveEntry` snapshot:
   - Converts all UUIDs to strings
   - Copies all primitive counters (lives, guards, timers)
   - Converts lives maps from `Map<UUID, Integer>` to `Map<String, Integer>`
   - Converts UUID sets to string lists
4. Writes the entire `WarSaveData` to `config/warntax/active_wars.json` using Gson with pretty-printing
5. Logs the count of wars saved

**If no wars are active**, an empty list is written (which loads cleanly as a no-op).

#### `loadAndResumeActiveWars()`

**When called:** During `ServerStartingEvent`, after colony manager and permissions are initialized.

**What it does:**
1. Checks if `config/warntax/active_wars.json` exists — if not, returns silently
2. Deserializes the file into a `WarSaveData` object
3. Gets the current `MinecraftServer` from `ServerLifecycleHooks`
4. For each `WarSaveEntry`, calls `resumeWarFromSave()` in a try/catch
5. Tracks restored vs. skipped counts and logs the summary
6. **Deletes the save file** after processing to prevent double-restoration on the next restart

#### `resumeWarFromSave(WarSaveEntry, MinecraftServer)`

This is the most complex method. It rebuilds a single war from its save entry.

**Step 1 — Colony Lookup:**
- Iterates all loaded `Level`s on the server
- For each level, searches `IColonyManager.getInstance().getColonies(level)` for colonies matching the saved defender/attacker colony IDs
- If the defender colony no longer exists, the war is **skipped** (logged as warning)
- If the attacker colony was saved but no longer exists, the war is also **skipped**

**Step 2 — Status Validation:**
- Parses the saved status string back to `WarData.WarStatus` enum
- Invalid status strings cause the war to be skipped

**Step 3 — Data Reconstruction:**
- Converts all string UUIDs back to `java.util.UUID` objects
- Rebuilds `Map<UUID, Integer>` for attacker/defender lives
- Rebuilds `Set<UUID>` for allies, spectators, last-life preservation
- Rebuilds `Set<Integer>` for guard IDs

**Step 4 — Expired War Handling:**
- **INWAR status:** Calculates `warStartTime + warDurationMs`. If the current time exceeds this, the war expired during downtime and is **skipped** (not restored).
- **JOINING status:** If the join phase end time has passed, the war is **promoted to INWAR** immediately with `warStartTime` set to now. The join phase is not re-run.

**Step 5 — Boss Bar Recreation:**
- Creates a fresh `ServerBossEvent` with the colony name
- Sets progress to 1.0 and visibility to true
- Adds all online attacker and defender players to the boss bar

**Step 6 — WarData Construction:**
- Calls the new restoration constructor with all 27 parameters
- Puts the `WarData` into `ACTIVE_WARS` keyed by defender colony ID

**Step 7 — State Restoration (INWAR wars):**
- Reapplies war glow effects to all participants
- Reapplies guard glow to defender and attacker colonies
- Reapplies guard resistance buffs via `GuardResistanceHandler`
- Calls `startWarCountdown()` to resume the war timer
- Calculates remaining time and calls `scheduleTimerWarnings()` with the remaining duration
- Updates the boss bar display

**Step 7b — State Restoration (JOINING wars):**
- Creates a secondary allies boss bar
- Calculates remaining join time
- Schedules a `TickScheduler.scheduleDelayed()` callback for when the join phase expires, which transitions the war to INWAR and calls `finalizeWarStart()`

**Step 8 — Broadcast:**
- Sends a gold/yellow server-wide message: *"War Restored: The war for [colony] has been resumed after server restart."*

#### `parseUUIDList(List<String>)`

A safe utility that converts a list of UUID strings to a `Set<UUID>`, silently skipping any malformed entries.

---

### 3. `MineColonyTax.java` — Lifecycle Hooks

Two additions to the main mod class:

**`onServerStarting`** — Added after guard resistance cleanup:
```java
WarSystem.loadAndResumeActiveWars();
```
Wrapped in try/catch so a corrupt save file cannot crash server startup.

**`onServerStopping`** — Added **before** `TickScheduler.shutdown()`:
```java
WarSystem.saveActiveWars();
```
Placed before TickScheduler shutdown because the war state must be captured while timers are still valid. Also wrapped in try/catch.

---

## Persistence File Format

**Location:** `config/warntax/active_wars.json`

```json
{
  "wars": [
    {
      "warID": "550e8400-e29b-41d4-a716-446655440000",
      "attacker": "d4f7a8b2-...",
      "defender": "a1b2c3d4-...",
      "attackerTeamID": "...",
      "defenderTeamID": "...",
      "defenderColonyId": 3,
      "attackerColonyId": 7,
      "warStartTime": 1709654400000,
      "joinPhaseEndTime": 1709654700000,
      "status": "INWAR",
      "accepted": true,
      "stalemateTriggered": false,
      "attackerLives": {
        "d4f7a8b2-...": 3,
        "e5f6a7b8-...": 2
      },
      "defenderLives": {
        "a1b2c3d4-...": 3
      },
      "guardIDs": [12, 34, 56],
      "attackerAllies": [],
      "defenderAllies": ["f6g7h8i9-..."],
      "spectators": [],
      "lastLifeInventoryPreservation": [],
      "initialAttackerGuards": 8,
      "remainingAttackerGuards": 5,
      "initialDefenderGuards": 6,
      "remainingDefenderGuards": 4,
      "initialAttackerTotalLives": 9,
      "initialDefenderTotalLives": 6,
      "penaltyReport": ""
    }
  ]
}
```

---

## Edge Cases Handled

| Scenario | Behavior |
|---|---|
| Server crashes mid-war | Save file from last clean stop is used; crash-time progress since last stop is lost |
| War timer expired during downtime | War is **not restored** — silently skipped with a log message |
| Join phase expired during downtime | War is **promoted to INWAR** immediately on restore |
| Colony deleted while server was off | War is **skipped** — cannot restore without the colony |
| Corrupt/malformed JSON | Entire load is wrapped in try/catch; server starts normally |
| No save file exists | `loadAndResumeActiveWars()` returns immediately — clean first boot |
| Save file is empty or has no wars | File is deleted, no wars restored |
| Player is offline at restore time | Boss bar skips them; they'll see it when they log in (boss bar syncs on join) |
| Multiple wars active | All are independently saved and restored |

---

## Architecture Decision: Why Not NBT?

The mod already uses Gson/JSON for `WarExhaustionManager`, `FactionManager`, `TaxPolicyManager`, and other persistence. JSON was chosen for consistency with the existing codebase pattern and for human-readability (admins can inspect/edit the save file if needed).

---

## What Is NOT Persisted

These runtime-only objects are **recreated** on load, not serialized:

- **`ServerBossEvent`** — Minecraft boss bar instance (recreated with colony name)
- **`IColony` references** — Looked up by colony ID from the colony manager
- **`alliesBossEvent`** — Secondary boss bar for join phase (recreated if in JOINING status)
- **Active `TickScheduler` timers** — Rescheduled based on remaining time calculations
- **Glow effects** — Reapplied to online participants
- **Guard resistance buffs** — Reapplied via `GuardResistanceHandler`

---

## Explosion Damage Restoration (WarBlockLedger)

**Added after the v2 update.**

War state is not the only thing that must survive a restart. During a war, explosions damage blocks; those blocks are restored when the war ends. If wars persist across a restart but the *damage ledger* does not, blocks broken before the restart would never be restored when the war later ends. `WarBlockLedger` closes that gap.

### What it does

- On `ExplosionEvent.Detonate` (HIGHEST priority) during an active war, it snapshots every affected block — its `BlockState`, position, and full block-entity NBT (chest contents, sign text, etc.) — into a per-war ledger keyed by the war's UUID. First snapshot per position wins, so repeat blasts on the same spot still restore the original pre-war state.
- Scope is limited to a padding radius around the defender colony, and a hard 50,000-entry-per-war cap guards against a runaway explosion loop.
- On `endWar()`, the ledgered blocks are restored to the world at ~50 blocks per tick to avoid chunk flicker. Block entities round-trip via `saveWithFullMetadata()` (capture) and `BlockEntity.loadStatic()` (restore).

### Persistence

The ledger is saved to **`config/warntax/war_block_ledger.nbt`** and follows the same lifecycle as `active_wars.json`:

| When | Action |
|---|---|
| Server stop (`ServerStoppingEvent`) | `flushPendingRestores()` synchronously finishes any in-progress restoration, then `saveToDisk()` writes the remaining active-war ledgers. Both run **before** `TickScheduler.shutdown()`. |
| Server start (`ServerStartingEvent`) | `loadFromDisk()` runs **before** war resume (so a war that ends on resume can still restore), then `pruneOrphans()` drops any ledger whose war did not come back. |

NBT (not Gson/JSON) is used here specifically because `BlockState` and block-entity snapshots are NBT-native and round-trip cleanly — unlike the war-state files, which stay JSON for human-readability.

### Edge cases handled

| Scenario | Behavior |
|---|---|
| Restart mid-war, war ends later | Blocks broken before the restart are restored correctly from the reloaded ledger. |
| Server stops *during* a restoration | Remaining blocks are flushed synchronously and saved with the world — none are stranded. |
| War failed to resume (orphan ledger) | Pruned at startup; cannot resurrect blocks at an unrelated war's end. |
| Partial war-restore (`active_wars.json.failed-<ts>`) | The ledger file is **not** deleted, so a manual recovery still has the damage data. |
| Corrupt `war_block_ledger.nbt` | Preserved as `war_block_ledger.nbt.failed-<ts>`; server still boots. |
| Double-restore after a crash | Not possible — `restoreWarDamage` removes the ledger first, prune drops non-active wars, and `active_wars.json` is deleted on load + co-written at shutdown, so a "war active and already restored" state cannot occur. |

### Explosion't integration

If the **Explosion't** mod is installed and `DeferRestorationToExplosiont = true`, the built-in ledger steps aside and a war-aware mixin (`WorldTickHandlerMixin`) pauses Explosion't's heal countdown while any war, raid, or besiege is active in that level — so destruction only regenerates **after** the conflict. When Explosion't is absent the mixin no-ops silently and the WarBlockLedger is the standalone fallback. `ExplosiontCompat.shouldDeferToExplosiont()` selects the active path.
