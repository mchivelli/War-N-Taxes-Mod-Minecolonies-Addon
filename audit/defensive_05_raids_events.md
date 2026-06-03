# Defensive Audit 05 — Raids, Random Events, Militia, Guard Resistance

Audit scope: `raid/**`, `events/random/**`, `militia/**`, `data/RaidData.java`,
`event/RaidEndEvent.java`, `commands/Raid*Command.java`,
`network/packets/DismissEventPacket.java`. Static analysis only.

---

## Summary

Functionally the four subsystems work, persist correctly, and integrate via
the `ColonyBuildingUtil` shim. The biggest defensive issues are:

- A **non-thread-safe `HashMap` for `activeRaids`** that is read from the Forge
  event bus and written from command/scheduled threads.
- **No real bypass guard for `/raid`** — the grace-period and "already raided"
  checks happen before per-colony tax-cost deduction succeeds but rely on a
  non-atomic check + put pattern, so two near-simultaneous raids can both pass
  the `colonyAlreadyRaided` test.
- **Two divergent copies of `endRaid` logic** in `RaidManager` (instance
  `endRaid` vs static `endActiveRaid`) that drift in non-trivial ways (one has
  the victory-override clause, one does not; one logs `eventString` to history,
  one does not). Both are reachable from production code.
- **Guard resistance is permanent (2 h duration) and not idempotent**: every
  raid/war start re-applies a fresh 2 h DAMAGE_RESISTANCE; level is taken
  from config plus an *additive* upgrade bonus that can exceed 255 (Minecraft
  effect amplifier wraps), and the tracking set is keyed by Entity UUID, which
  changes when a citizen dies and respawns. Re-entry / re-spawn breaks
  removal.
- **Random events trigger one per cycle, in `RandomEventType.values()` order**
  — first event in enum order whose roll wins is chosen, so the enum
  declaration order is a hidden bias.

No CRITICAL data-loss bugs, but several HIGH-severity defensive gaps and
duplicated/dead code that should be cleaned before further changes.

---

## Critical

(none)

---

## High

### H1. `activeRaids` is a non-thread-safe `HashMap`
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:42`

```
private static final Map<UUID, ActiveRaidData> activeRaids = new HashMap<>();
```

This map is:
- written from command threads (`handleRaid` line 283, `endActiveRaid`
  line 571, instance `endRaid` line 1216),
- iterated on the Forge event bus from `WarEventHandler.onPlayerDeath`
  (line 98 there) and from the tick scheduler in `startRaidCountdown`,
- queried by `EventTriggerSystem.isColonyBeingRaided` (line 171) on every
  random-event probability roll.

The other equivalent maps in this file (`RAID_GRACE_PERIODS`,
`DEFENSE_GRACE_PERIODS`, `lastLoggedGuardCounts`) are also plain `HashMap`s.
`RandomEventManager`, `CitizenMilitiaManager`, `RecruitmentTracker` and
`GuardResistanceHandler` use `ConcurrentHashMap`. The raid maps should match.

### H2. TOCTOU on "colony already raided" / "raider already raiding" checks
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:149-155, 283`

```
boolean colonyAlreadyRaided = activeRaids.values().stream()
        .anyMatch(rd -> rd.getColony().getID() == colony.getID());
if (colonyAlreadyRaided) { … return 0; }
…
activeRaids.put(raiderUUID, raidData);
```

There is no synchronisation around the check-then-put sequence. Two players
running `/wnt raid <colony>` near-simultaneously can both pass the check
and both call `put`. The `RAID_GRACE_PERIODS` / `DEFENSE_GRACE_PERIODS`
checks (lines 240-264) are likewise non-atomic vs the writes at lines
557/562.

Combined with H1, this is exploitable to start a second raid against a
colony that already has one in progress, which then double-applies the
Hostile rank, double-snapshots the boss bar, and confuses the kill
attribution snapshot.

### H3. Duplicate raid-end logic (`endActiveRaid` static vs `endRaid` instance)
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:404-579` vs
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:1148-1316`

Both methods:
- run nearly identical cleanup (boss bar, militia deactivate, GLOW removal,
  PermissionSnapshot restore, history events),
- both `put` into `RAID_GRACE_PERIODS` and `DEFENSE_GRACE_PERIODS`,
- both `remove(raidData.getRaider())` from `activeRaids`.

Differences:
- `endActiveRaid` (static) **applies a raid penalty via `RaidPenaltyManager`**
  on success (line 488) — `endRaid` (instance) does not.
- `endActiveRaid` writes a structured `addRaidEntry` (line 525 / 542) — the
  instance `endRaid` writes a free-text `addEvent` (line 1309) instead.
- `endRaid` has a "victory override" branch (line 1252) so wars ended with
  the success reason still pay out even if boundaries were left.
  `endActiveRaid` requires `isEligibleForRewards()` to be true.

Callers are mixed: `startRaidCountdown` uses `endRaid` (the instance form),
`stopRaidCommand` uses `endRaid`, but `handleRaiderKilled` and
`EntityRaidEventHandler` invoke `endActiveRaid` (static). A player
killed mid-raid vs a timer-expired raid therefore get **different penalty
behaviour, different history records, and possibly different reward
eligibility for the same scenario**. This is almost certainly unintentional.

### H4. Guard resistance amplifier overflow / non-idempotent stacking
`src/main/java/net/machiavelli/minecolonytax/raid/GuardResistanceHandler.java:38-43,147-167`

`int resistanceLevel = baseResistanceLevel + upgradeBonus;` is passed
directly to `new MobEffectInstance(..., level - 1, …)`. `MobEffectInstance`
takes a `byte` amplifier internally — values > 127 wrap to negative
amplifiers, which can flip the effect to harmful behaviour. There is no
clamp on the combined value. (`getDefenseLevelBonus` is uncapped.)

Additionally:
- Every raid start / war start invokes
  `applyResistanceToGuardsForRaid(colony)` / `…ForWar(colony)`. The duration
  is hard-coded to 2 h (`20 * 60 * 120` ticks, line 150). If a raid then a
  war start within 2 h, two `colonyGuardEffects` / `colonyWarGuardEffects`
  sets diverge and effects are added through `guard.addEffect` (vanilla
  semantics: a higher-amplifier effect overrides, but identical-level
  effects extend duration). On removal (line 174-180) the effect is
  unconditionally removed regardless of source, so a war ending while a
  raid is still active will strip resistance for the raid.
- Tracking is keyed by `guard.getUUID()` (line 56). If the citizen dies and
  MineColonies respawns the entity with a new UUID, removal no longer
  matches that entity — its resistance lingers until the 2 h timer expires
  or the chunk unloads.

### H5. Random events are not strictly random — enum order biases selection
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:206-230`

```
for (RandomEventType eventType : RandomEventType.values()) {
    … if (RANDOM.nextDouble() < probability) {
        triggerEvent(colony, eventType);
        return; // Only trigger one event per cycle
    }
}
```

Events are evaluated in enum declaration order, and the first to pass its
independent probability roll wins. The declaration order in
`RandomEventType.java:25-328` is roughly positive→negative→deep. So when
two events would have triggered on the same cycle, the one declared first
(MERCHANT_CARAVAN, BOUNTIFUL_HARVEST, …) is systematically favoured.

A correct implementation would either roll once globally and pick a single
weighted-random event, or shuffle the enum order before iterating.

### H6. Auto-eligible "owned colony" loop can spend treasury on a colony other than the one chosen
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:84-126, 211-237`

When building-requirements mode is on, the loop at lines 87-108 picks
*the first owned colony that satisfies requirements* — this may not be the
FCT primary if the FCT primary lacks the requirements. The treasury
deduction at line 230 then deducts from `raiderColony.getID()` (the
auto-picked one), but the player only sees `raiderColony` indirectly via
log lines — there is no confirmation prompt. A multi-colony player can have
a non-primary colony's treasury drained without realizing which one paid.

---

## Medium

### M1. `DismissEventPacket` permission check is `ACCESS_HUTS`, not "owns colony"
`src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java:56`

`ACCESS_HUTS` is granted to Friend/Officer/Owner ranks. Any colony Friend
can permanently dismiss event log entries other colony members may still
want to see. Should be `MANAGE_HUTS` (officer/owner) — the rest of the
packet codebase uses `MANAGE_HUTS` for write actions
(see `UpdateTaxPermissionPacket.java:41`).

### M2. `RandomEventManager.notifyColonyPlayers` will NPE if SERVER is null
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:685-709`

`notifyColonyPlayers` is called from `triggerEvent` and `updateActiveEvents`
without an `SERVER != null` guard. `getColony` (line 672) has one. If the
manager somehow ticks before `initialize` (or after shutdown), the for-loop
at line 701 throws NPE — caught by the outer try, but logged only at
`debug` level (line 707), so the failure is invisible by default.

### M3. `RandomEventManager.saveData()` runs on every tax cycle, per colony
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:126, 957, 988, 998, 1020`

`onTaxCycle` calls `saveData()` at the end of each colony's cycle (line
126). With N colonies and the tax cycle iterating colony-by-colony, this
serialises the entire ACTIVE_EVENTS / EVENT_COOLDOWNS / EVENT_LOG /
COLONY_FIRST_SEEN_MS map to disk N times on every cycle. For a 20-colony
server with a 10 min cycle that's 2880 full rewrites/day. Should batch into
one write per cycle (or just on shutdown / periodic).

### M4. `data/RaidData.java` is dead code
`src/main/java/net/machiavelli/minecolonytax/data/RaidData.java:1-14`

The record `RaidData(attackerUUID, startTime, gracePeriodEnd, completed,
participants)` is imported by `WarEventHandler.java:10` but never
referenced (verified by grep — only the import line matches). The actual
raid state lives in `raid/ActiveRaidData.java`. Should be deleted, or
populated and used (since the in-memory `RAID_GRACE_PERIODS` map is lost
across server restarts — which is what `gracePeriodEnd` could fix).

### M5. Raid grace periods are not persisted across server restarts
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:45,53`

`RAID_GRACE_PERIODS` and `DEFENSE_GRACE_PERIODS` are in-memory only.
A server restart resets both — a colony that was just raided 10 s before
shutdown is immediately raidable on restart. `RAID_GRACE_PERIOD_MINUTES`
defaults to ≥ 10 min so this is exploitable. The `RaidData` record
(see M4) was probably intended for this.

### M6. `MilitiaAttackGoal` can hit allied/teammate citizens via friendly fire
`src/main/java/net/machiavelli/minecolonytax/militia/MilitiaAttackGoal.java:131-156`

`performAttack` calls `target.hurt(citizen.damageSources().mobAttack(citizen), 4.0F)`
unconditionally once distance ≤ 4 blocks. The targeting goal in
`CitizenMilitiaManager.enableMilitiaCombatAI:250` filters by
"is currently raiding *this colony*", but the target is then cached as the
citizen's `getTarget()` and `MilitiaAttackGoal.canUse` only checks alive +
within world border (line 37-49). If a colony Friend or Officer (Neutral
rank) gets between militia and raider during a melee, the militia will
keep attacking the raider — but if the raider's `getTarget()` reference
gets reassigned to a guard / other citizen via vanilla `HurtByTargetGoal`
(added at line 249), the militia happily damages the new target with no
team check.

There is also no `this.target = null` reset at the start of `start()` (it
relies on `canUse` having set it), and `tick()` early-returns if
`this.target == null` but `performAttack` only null-checks the field, not
its team alignment.

### M7. `CitizenMilitiaManager.enableMilitiaCombatAI` wipes ALL goals
`src/main/java/net/machiavelli/minecolonytax/militia/CitizenMilitiaManager.java:245-246`

```
entity.goalSelector.removeAllGoals((goal) -> true);
entity.targetSelector.removeAllGoals((goal) -> true);
```

This deletes MineColonies' own work / move / sleep goals on every
citizen converted to militia. On deactivate (line 280-285) the code only
re-runs `onJobChanged(citizen.getJob())` to restore them — but if the
citizen's job is null (deliveryman filter excluded them but unemployed
citizens can be eligible — see `getEligibleCitizens`), `onJobChanged` is
never called and the citizen is left with no goals at all until next
job assignment / chunk reload.

### M8. `EventLogEntry` deserialization assumes new fields exist
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:797-807`

The defensive `compactStat` read uses `has("compactStat")` (good), but
`displayName`, `description`, `colorCode`, `isActive`, `remainingCycles`
are read with `.get(…).getAs…()` without `has(…)` checks. A truncated /
partially-written `random_events.json` (likely under M3's pressure if
the server is killed mid-save) will throw `NullPointerException` at load
and the catch at line 823 swallows it — wiping the entire event log on
recovery.

### M9. ReflectionCache initialisation timing and `forceReinitialize` race
`src/main/java/net/machiavelli/minecolonytax/raid/ReflectionCache.java:60-89, 779-799`

- Each public method calls `initialize()` (lines 260, 316, 382, 508, 582,
  617, 627, 678, 686, 701). The first-call guard `if (initialized) return`
  before the synchronized check is racy on first use (a second thread can
  observe `initialized = false`, enter `synchronized`, and re-run); the
  method *is* declared `synchronized` though, so it only re-checks under
  the lock. OK.
- `forceReinitialize` (line 779) sets `initialized = false` and clears the
  primary Method fields, but other threads holding cached `Method`
  references continue to use them mid-call (no guard). Real-world risk is
  low (only called from tests per Javadoc), but should be documented.
- The dynamic-cache key collisions: `cacheEntityMethod` puts `entity_<name>`
  for everything not matched by the if-cascade. The match arms use
  `methodName.contains("Owner") && methodName.contains("UUID")` etc., so
  if MineColonies/Recruits ever renames `getOwnerUUID` to `getPlayerUUID`,
  the primary slot stays null and only the fallback path catches it. The
  fallback path correctly handles this — graceful degradation works.

---

## Low

### L1. `RandomEventType.countBuildingsOfType` is O(N×K) per check
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventType.java:421-433`

Each event probability roll iterates every building in the colony for each
event type. With 16 enum values × N buildings per cycle × M colonies, this
is wasted CPU when most events fail the simpler numeric checks. Could
short-circuit on the cheap predicates first, or cache building counts per
cycle.

### L2. `ActiveRaidData` snapshot heuristics are weak
`src/main/java/net/machiavelli/minecolonytax/raid/ActiveRaidData.java:192-213`

`snapshotOriginalGuardIds` matches buildings by lowercase substring of
`getBuildingDisplayName()`. Localised display names break it (display name
in Russian / German won't contain "guard" / "barracks"). Should match by
registry name (as `RandomEventType` does at line 424).

### L3. `RaidManager.handleRaid` writes raider-colony name "(id=N)" only inside isDebugLogging
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:128-131`

Operational logs (`isNormalLogging`) only show the raid command via
"Raid blocked" / treasury messages — when a multi-owned-colony player
raids successfully, normal logs don't tell admins *which* of the
player's colonies funded / sourced the raid. Combine with H6.

### L4. `RaidHistoryCommand` returns the last 50 in raid-insertion order
`src/main/java/net/machiavelli/minecolonytax/commands/RaidHistoryCommand.java:65-77`

Slices `raidEvents.size() - eventCount … raidEvents.size() - 1` and
iterates in reverse. If `raidEvents.size() == 0` the outer check at line
57 catches it, but if `raidEvents` is non-null with size = 0 the
`Math.min(0, 50)` makes the loop range empty — no bug, but `eventCount`
is unused when `raidEvents.size() < 50` (you just iterate them all in
reverse). Minor cosmetic.

### L5. `RaidRepairCommand` registered as a child of `wnt`, but `WntCommands` also registers `wnt`
`src/main/java/net/machiavelli/minecolonytax/commands/RaidRepairCommand.java:52-63`

Both `WntCommands.register` (uses `Commands.literal("wnt")`) and
`RaidRepairCommand.register` register a `wnt` root. Brigadier merges
child nodes, but if both are added to the dispatcher this duplicates the
root literal node — confirm only one is invoked from `RegisterCommandsEvent`
to avoid silent shadowing. (Did not verify wiring.)

### L6. `RecruitmentTracker` 30 s cleanup threshold is too short to use as a grace window
`src/main/java/net/machiavelli/minecolonytax/raid/RecruitmentTracker.java:23, 89-101`

Both the "is recent" query window and the cleanup TTL are
`CLEANUP_THRESHOLD_MS = 30 s`. Querying `isRecentlyRecruited` with a grace
period > 30 s never returns true because cleanup has already evicted
the entry. The two constants should be decoupled.

### L7. `RandomEventManager.spawnBanditsNearColony` ignores configured difficulty / dim
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:386-419`

`colony.getWorld()` is cast directly to `ServerLevel` without checking
that it isn't the nether/end dimension. If a colony is ever in a
non-overworld dim, `getHeightmapPos(MOTION_BLOCKING_NO_LEAVES, …)` still
works but the y-coordinate of "ground" in the nether is ambiguous —
pillagers may spawn at lava height.

### L8. `EventTriggerSystem.isColonyAtWar` does linear scan over `WarSystem.ACTIVE_WARS.values()`
`src/main/java/net/machiavelli/minecolonytax/events/random/EventTriggerSystem.java:185-205`

Called for every event probability calculation. With many colonies and
active wars this becomes per-cycle quadratic. Should cache war
participants as a set keyed by colony ID.

### L9. `applyBribeEffectToGuards` uses raw `colony.getCitizenManager()` rather than `ColonyBuildingUtil`
`src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:1352-1389`

Not a compatibility issue (citizen manager is stable across versions),
but the CitizenData iteration here doesn't filter by building loadedness —
on a server where a guard tower's chunk is unloaded, the entity will be
absent (skipped) and the bribe debuff applies only to currently-loaded
guards. Combined with the random pick, an attacker who bribed N guards
may see fewer than N applied on raid start.

### L10. `RandomEventManager.forceTriggerEvent` skips cooldowns and conditions
`src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:997-1000`

Used by admin commands, but does **not** check `meetsConditions`,
`isOnCooldown`, simultaneous-event limit, or new-colony protection.
Force-triggering `WAR_PROFITEERING` on a non-war colony will succeed and
the +35 % tax modifier will apply. Intentional for admins, but the
absence of any opt-in flag means a typo can break game balance.

---

## Files Audited

- `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java` (1952 lines)
- `src/main/java/net/machiavelli/minecolonytax/raid/ActiveRaidData.java`
- `src/main/java/net/machiavelli/minecolonytax/raid/GuardResistanceHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/raid/RecruitmentTracker.java`
- `src/main/java/net/machiavelli/minecolonytax/raid/ReflectionCache.java`
- `src/main/java/net/machiavelli/minecolonytax/data/RaidData.java`
- `src/main/java/net/machiavelli/minecolonytax/event/RaidEndEvent.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/RaidHistoryCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/RaidRepairCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventType.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/ActiveEvent.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/EventTriggerSystem.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/EventLogEntry.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/deep/CitizenManipulator.java`
- `src/main/java/net/machiavelli/minecolonytax/militia/CitizenMilitiaManager.java`
- `src/main/java/net/machiavelli/minecolonytax/militia/MilitiaAttackGoal.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java`

Cross-referenced (not full-read): `WarSystem.java`, `TaxManager.java`,
`TaxConfig.java`, `WarEventHandler.java`, `WarCommands.java`,
`WntCommands.java`, `ColonyBuildingUtil.java`.
