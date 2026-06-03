# Adversary Codex: Concurrency Adversary

## Summary

Overall, the project has internalized the "main-server-thread" doctrine reasonably well:

- All 25 network packet handlers (under `src/main/java/net/machiavelli/minecolonytax/network/packets/`) correctly wrap server-state mutation in `ctx.get().enqueueWork(...)` and call `setPacketHandled(true)`. None were found mutating maps from the netty thread.
- The mod has a real central scheduler — `TickScheduler` — that uses Forge's `ServerTickEvent` and a `ConcurrentHashMap` for tasks. Documentation in `CLAUDE.md` and `MEMORY.md` is honored across the war/raid/spy systems.
- Major shared maps that span multiple threads (e.g. `WarSystem.ACTIVE_WARS`, `TreasuryManager.TREASURIES`, `SpyManager.ACTIVE_MISSIONS`, `OccupationManager.ACTIVE_OCCUPATIONS`, `BesiegeManager.ACTIVE_RAIDS`) are correctly `ConcurrentHashMap`.
- Persistence has been moved to `AsyncSaveExecutor` (single daemon thread) with a documented snapshot-on-calling-thread pattern; `WarStatsDB` similarly uses a single bounded-queue writer.

However, there are **two background `java.util.concurrent` executors that violate the project's stated "no `Executors.`/`new Thread`" rule** (per CLAUDE.md and the audit brief), several non-thread-safe `HashMap`/`HashSet` collections in handlers that *probably* run on the server thread but have no enforcement, and one client-side static cache (`TaxManagementScreen.latestSpyMissions`) that exhibits a torn-read window. Real bug risk is concentrated in PvP — `PvPManager` holds critical battle state in plain `HashMap`s alongside its own external `ScheduledExecutorService`.

---

## Critical Concurrency Bugs (server crash, data corruption, definite race)

### [CONC-1] `PvPManager.BATTLE_END_SCHEDULER` is a non-game-thread `ScheduledExecutorService`
**File:** `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:59`
```java
public static final ScheduledExecutorService BATTLE_END_SCHEDULER =
        Executors.newScheduledThreadPool(1);
```
- **Threads involved:** dedicated worker thread vs. server thread.
- **Violates CLAUDE.md rule:** "Never use `java.util.Timer` or `new Thread(...)` for deferred tasks. Use TickScheduler."
- **Failure mode:**
  1. `PvPBattleManager.java:72` and `PvPBattleManager.java:723` schedule tasks here. Both correctly bounce to `server.execute(...)` *inside* the lambda, so the visible mutation runs on the server thread. That makes today's usage non-fatal.
  2. However, the executor is **never shut down** anywhere in the codebase. After `ServerStoppingEvent`, any in-flight 5-second restoration timer will still fire, call `ServerLifecycleHooks.getCurrentServer()` (null), and silently no-op — but threads will linger until JVM exit. On a `/reload`-style restart inside a single client process (integrated server), this leaks one daemon thread per cycle.
  3. The bigger risk: the scheduler is "public static final" and tempting for future calls to skip the `server.execute()` hop. Every new call site is a foot-gun.
- **Repro:** in dev, watch `Thread.activeCount()` cross two integrated-server restarts; `pool-N-thread-1` accumulates.
- **Fix:** delete the executor; route both schedule sites through `TickScheduler.scheduleDelayed(...)` (already used everywhere else).

### [CONC-2] `PvPManager` battle state is plain `HashMap` while mutators run from packet handlers + tick handler + scheduler callback
**File:** `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:32–44, 50–51, 53`
```java
public final Map<String, ActiveBattle>  activeBattles      = new HashMap<>();
public final Map<String, TeamBattle>    pendingTeamBattles = new HashMap<>();
public final Map<UUID, BattleRequest>   pendingRequests    = new HashMap<>();
public final Map<UUID, GameType>        playerOriginalGameModes = new HashMap<>();
public final Map<String, List<UUID>>    activeSpectators        = new HashMap<>();
public final Map<UUID, PlayerPvPStats>  playerStats             = new HashMap<>();
public final Map<UUID, Long>            challengeCooldown       = new HashMap<>();
public final Map<UUID, Long>            teamBattleCooldown      = new HashMap<>();
public final Map<UUID, Long>            lastFriendlyFireNotifications = new HashMap<>();
```
- **Threads involved:** server tick (`PvPEventHandler.onServerTick` lines 94, 97–137, 139–161), packet handlers (Forge's `enqueueWork` is server thread → OK), and `BATTLE_END_SCHEDULER` callbacks. The latter currently hop via `server.execute(...)` but the design does not require it.
- **Failure mode:** `onServerTick` (line 94) calls `pendingRequests.entrySet().removeIf(...)` while the `forEach` on `pendingTeamBattles.values()` (line 97) may mutate the map via `battlesToStart.forEach(battle -> battleManager.startTeamBattle(battle))` (line 134), and `startTeamBattle` does `pvpManager.pendingTeamBattles.remove(...)` (lines 118, 125, 137, 144, 158). That mutation happens during the outer iteration. With a plain `HashMap`, this is a textbook **`ConcurrentModificationException`** — even on a single thread, because the structural modification happens between iterator creation and the next `next()`.
  - Mitigated for `pendingTeamBattles` because `battlesToStart` is collected first and started after the iteration finishes — but only by luck of code layout. The first refactor that inlines this pattern will crash.
  - **`activeBattles`** is read by `getActiveBattle(player)` (line 69) which does a `values().stream()...` while `PvPBattleManager` puts/removes from packet handlers AND from `endBattle()` (line 728) AND from scheduler callbacks. The scheduler `server.execute(...)` bounce makes this same-thread *today*, but `getActiveBattle` is called from `onCommandExecution` (PvPEventHandler line 175) which is server-thread. Whole class is one missed `enqueueWork` away from a CME.
- **Fix:** replace every `new HashMap<>()` in PvPManager with `new ConcurrentHashMap<>()`, replace `playerInventories`/`playerArmor` (deprecated, keep for now) and `spectatorData` similarly. Even better: enforce server-thread invariant via an assertion.

### [CONC-3] `RaidManager` global state maps are plain `HashMap`
**File:** `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:42, 45, 53, 55`
```java
private static final Map<UUID, ActiveRaidData> activeRaids          = new HashMap<>();
private static final Map<UUID, Long>           RAID_GRACE_PERIODS   = new HashMap<>();
private static final Map<Integer, Long>        DEFENSE_GRACE_PERIODS= new HashMap<>();
private static final Map<Integer, Integer>     lastLoggedGuardCounts= new HashMap<>();
```
- **Threads involved:** server thread (TickScheduler raid lifecycle), `PlayerLoggedInEvent` (`RaidLoginNotifier.onPlayerLogin` line 52 reads `RaidManager.getActiveRaids().values()` — login is server-thread under Forge but the read returns the live map). Also accessed via commands.
- **Failure mode:** if a raid ends (`activeRaids.remove`, line 571 and 1216) and `lastLoggedGuardCounts.put` (line 1124) happen in interleaving order with `activeRaids.values().stream()` in another spot (line 1472, 1927), one read can observe a torn map. Less likely with all callers on the server thread, but `getActiveRaids()` (line 581) returns the *live* HashMap reference and is consumed by `RaidLoginNotifier` and possibly capability/event handlers — any future async consumer is unsafe.
- **Fix:** swap to `ConcurrentHashMap`. The collection is small (<100 entries), cost is zero.

### [CONC-4] `FactionManager.FACTIONS` is a `HashMap` snapshotted on the server thread then handed to async writer — but the *next* `loadData()` reassigns the static reference
**File:** `src/main/java/net/machiavelli/minecolonytax/faction/FactionManager.java:35, 51`
```java
private static Map<UUID, FactionData> FACTIONS = new HashMap<>();
...
private static void loadData() {
    ...
    if (loaded != null) { FACTIONS = loaded; }  // line 51 — non-volatile static reassignment
}
```
- **Threads involved:** server thread (game tick, login). `loadData()` is called from `init()` only, on startup — single-threaded. But `FACTIONS` reference itself is not `volatile` and is dereferenced from many spots (lines 85, 97, 106, 115, ...). If a future call site re-runs `loadData()` from a non-startup path, readers on other threads may see a stale reference.
- **Failure mode (today):** none. Save uses snapshot (line 60). All callers appear to be server-thread. Risk is for the next refactor.
- **Fix:** declare `private static volatile Map<UUID, FactionData> FACTIONS = new ConcurrentHashMap<>();` and remove the reassignment in `loadData()` (use `FACTIONS.clear(); FACTIONS.putAll(loaded);` instead).

---

## High (race conditions under load)

### [CONC-5] `AsyncSaveExecutor` uses raw `new Thread`
**File:** `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java:33–37`
```java
private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "WNT-AsyncSave");
    t.setDaemon(true);
    return t;
});
```
- This **is** the project's sanctioned async I/O pipe, but the brief lists `Executors.` and `new Thread` as forbidden patterns. The shutdownAndFlush pattern (line 88) is correct and `ServerStoppingEvent` calls it (`MineColonyTax.java:406`). Per-key coalescing prevents pile-ups.
- **Real concern:** `submit()` stores the latest Runnable per key but the worker invokes whatever `Runnable` callers passed. Several callers (e.g. `SpyManager.saveData()` line 134, `WarSystem` writers, `OccupationManager`) snapshot maps via `new HashMap<>(...)` first — but `FactionManager.saveData()` snapshots only the top-level `FACTIONS` map; it does *not* deep-copy `FactionData` mutable fields. If a `FactionData` setter runs on the server thread while Gson is mid-serialization on the writer thread, the JSON can be torn or throw `ConcurrentModificationException` (e.g., on a nested member list). Same risk in `SpyManager.saveData()` — the `SpyMission` objects in the snapshot map are by-reference and `mission.setStatus(...)` happens on the server thread concurrently with serialization on the writer thread.
- **Mitigation:** either keep this and accept that all serialized objects must be effectively immutable while a save is pending, or move all save() calls to `TickScheduler` and serialize inline (simpler).
- **Suggest:** at minimum, add a doc-comment in `AsyncSaveExecutor` warning that all values transitively reachable from the snapshot must be effectively immutable, and audit every caller.

### [CONC-6] `WarStatsDB` uses `new ThreadPoolExecutor` + `new Thread` (DB writer)
**File:** `src/main/java/net/machiavelli/minecolonytax/db/WarStatsDB.java:45–49`
```java
private static final ExecutorService WRITER = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, WRITE_QUEUE,
        r -> { Thread t = new Thread(r, "WarStatsDB-Writer"); t.setDaemon(true); return t; },
        ...);
```
- Same comment as [CONC-5]: this is intentional because JDBC blocks I/O, but it violates the brief's "all forbidden patterns are HIGH". `snapshotAllColonies` (line 441) and `recordWarEnd` (line 360) capture data on the game thread via primitives/UUIDs/`Set.copyOf` before submitting — good pattern.
- **Concern:** `recordWarEnd` captures `Set.copyOf(warData.getAttackerLives().keySet())` (line 357). `warData.getAttackerLives()` returns the live `ConcurrentHashMap<UUID, Integer> attackerLives` (`WarData.java:23`). `Set.copyOf` iterates a CHM — safe because of CHM's weakly-consistent iterator. **Not a bug**, but worth flagging that the *value* `WarData` itself is still being mutated (e.g., `remainingAttackerGuards` field) while reads above happen. Since only primitives + UUIDs + map keys are captured *before* submit, the worker thread never re-reads `warData` — actually safe.

### [CONC-7] `SpyDataResponsePacket` server-side constructor reads many `SpyMission` getters; mission state is mutated on the same server thread elsewhere — but the constructor is called from `enqueueWork`/server thread sites only
**File:** `src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java:31–65`
- All four construction sites (`DeploySpyPacket:60`, `DismissSpyMissionPacket:44`, `RecallSpyPacket:44`, `RequestSpyDataPacket:34`, and `SpyManager.pushSpyDataToPlayer:605`) run inside `enqueueWork` lambdas. No race; called out for completeness.
- **However:** the `client-side` handler (line 76) calls `TaxManagementScreen.updateLatestMissions(missions)` (line 82) which mutates `latestSpyMissions = new ArrayList<>(missions)` — see [CONC-9].

### [CONC-8] `RaidLoginNotifier.notifiedRaidsByPlayer` etc. are plain `HashMap`s
**File:** `src/main/java/net/machiavelli/minecolonytax/event/RaidLoginNotifier.java:33–36`
```java
private static final Map<UUID, Set<UUID>>    notifiedRaidsByPlayer       = new HashMap<>();
private static final Map<UUID, Set<Integer>> notifiedWarsByPlayer        = new HashMap<>();
private static final Map<UUID, Set<Integer>> notifiedBesiegesByPlayer    = new HashMap<>();
private static final Map<UUID, Set<Integer>> notifiedOccupationsByPlayer = new HashMap<>();
```
- **Today:** only mutated from `onPlayerLogin` (server thread) and `recordCompletedRaid` (also server thread). Safe.
- **Risk:** `recordCompletedRaid` (line 39) calls `completedRaids.add(raid)` and is wrapped in `Collections.synchronizedList` (line 30), but `notifiedRaidsByPlayer` is not. If the project adds an async login-prep path or moves notifications off-thread, this silently breaks.
- **Fix:** swap to `ConcurrentHashMap` and `ConcurrentHashMap.newKeySet()`.

---

## Medium (theoretical races, narrow window)

### [CONC-9] `TaxManagementScreen.latestSpyMissions` — server packet handler updates a client static while the render thread iterates it
**File:** `src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:66, 87–93, 139`
```java
private static volatile List<SpyMissionData> latestSpyMissions = new ArrayList<>();
...
public static List<SpyMissionData> getLatestSpyMissions() { return latestSpyMissions; }
public static void updateLatestMissions(List<SpyMissionData> missions) {
    latestSpyMissions = new ArrayList<>(missions);  // reference swap
}
```
- **Threads involved:** packet receive (network-thread → `enqueueWork` → client main thread for client packets, so this *should* be the same thread as the render loop in vanilla Forge). However, `enqueueWork` on a CLIENT-side context schedules to the client main thread; render also runs on the client main thread; so today this is single-threaded.
- **Hazard:** the `volatile` ensures reference-publication is visible. But `getLatestSpyMissions()` returns the **internal list reference** (not a copy). If a caller holds the returned list across a packet receive boundary and then iterates it, the iteration sees the new list (because reference is swapped, not mutated), so no CME there. **However**, `SpyJourneyMapPlugin.syncWaypoints(missions)` (called from packet `handle` line 83) and the GUI render path both potentially iterate the same `missions` parameter — safe today.
- **Risk:** if any code path mutates the returned list (e.g., `getLatestSpyMissions().add(...)`), the rotating reference makes the mutation invisible after the next packet — a silent data-loss bug. Defensive copy on read would be safer.
- **Fix:** `getLatestSpyMissions()` should return `List.copyOf(latestSpyMissions)` or `Collections.unmodifiableList(latestSpyMissions)`.

### [CONC-10] `GlowClientHandler.glowingEntities` — `ConcurrentHashMap` but iteration calls `Entity.setGlowingTag` on entries it then removes
**File:** `src/main/java/net/machiavelli/minecolonytax/network/GlowClientHandler.java:19, 46–61`
- Uses CHM correctly. Iteration removes via iterator. **OK** but: `setGlowingTag` is called inside the iteration; if a packet on the network thread races with this iteration via `enqueueWork`-on-client, both run on the client main thread — same-thread.
- **No bug.** Defensive design is correct.

### [CONC-11] `BesiegeDamageShieldHandler.LAST_BLOCK_MESSAGE` and `BesiegeEntityInteractHandler.LAST_DENY_MESSAGE` are plain `HashMap`
**Files:**
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:38`
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java:36`
- Both are written from Forge event handlers. `LivingHurtEvent` and `PlayerInteractEvent` both fire on the server thread for server-side checks (`event.getLevel().isClientSide()` filter is correct).
- **Risk:** zero today; flagged for the convention.

### [CONC-12] `GuardResistanceHandler.colonyGuardEffects` and `colonyWarGuardEffects` — plain `HashMap`
**File:** `src/main/java/net/machiavelli/minecolonytax/raid/GuardResistanceHandler.java:24–25`
- Written from raid-start/raid-end on the server thread. Read from same. No mutator is documented for async paths.
- **Risk:** zero today.

### [CONC-13] `ColonyEventListener.colonyBuildingLevels` — plain nested `HashMap`
**File:** `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java:33`
- Only touched in `onServerTick`. Single-threaded.

### [CONC-14] `TaxManager.colonyTaxMap` — plain `HashMap`
**File:** `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:50`
- Single-thread access via the tax tick. Save uses snapshot pattern in `saveData()`.
- **Risk:** if any read API ever runs from a network packet without enqueueWork, this breaks. Network packets all use enqueueWork today.

### [CONC-15] `WarSystem.saveActiveWars()` iterates the live `ACTIVE_WARS` CHM during shutdown while drain tasks may still be hitting it
**File:** `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3943–4002`, called from `MineColonyTax.java:369` in `ServerStoppingEvent`
- `ServerStoppingEvent` runs on the server thread before `ServerStoppedEvent`. By that point Forge has paused most tick activity but the `TickScheduler` is still alive (shutdown happens at `MineColonyTax.java:397`, after `WarSystem.saveActiveWars()`). If a scheduled war-drain task fires on the last few ticks before TickScheduler.shutdown(), it can mutate `ACTIVE_WARS` during the save iteration.
- CHM's weakly-consistent iterator won't CME, but a war added/removed mid-save can be partially serialized: included with stale `remainingAttackerGuards` or missed entirely.
- **Fix:** snapshot `ACTIVE_WARS` to a local `HashMap` at the top of `saveActiveWars()` (matches the pattern used in `SpyManager.saveData()`).

### [CONC-16] Persistence ordering: TickScheduler shutdown happens AFTER all manager shutdowns that may have queued AsyncSaveExecutor jobs
**File:** `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java:303–411`
- Order:
  1. WarStatsDB shutdown (drains DB writer)
  2. Various manager shutdowns (each calls `saveData()` which submits to AsyncSaveExecutor)
  3. WarSystem.saveActiveWars (synchronous file write)
  4. TickScheduler.shutdown (cancels all pending TickScheduler tasks — INCLUDING the periodic snapshot scheduled at line 286)
  5. AsyncSaveExecutor.shutdownAndFlush (final drain to disk)
- This order is correct: step 5 properly drains every queued write. Step 4 happens after step 3, so any TickScheduler task firing between step 1 and step 4 may queue an async save — drained at step 5. **OK.**
- **Risk:** if any future shutdown step *re-queues* after `shutdownAndFlush()` runs, that save runs inline on the shutdown thread synchronously, which is at least never lost (line 52-58 of AsyncSaveExecutor handles this) but could block JVM exit.

---

## Patterns to enforce (suggested project rules)

1. **Ban `Executors.*`, `new Thread`, `new Timer`, `Thread.sleep`, `CompletableFuture.runAsync/supplyAsync` in `src/main/java/net/machiavelli/minecolonytax/`**, with two grandfathered exceptions:
   - `util/AsyncSaveExecutor.java` (single daemon thread for disk I/O)
   - `db/WarStatsDB.java` (single daemon thread for JDBC I/O)
   - Add a CheckStyle / SpotBugs / ArchUnit rule (or a CI grep) to fail the build if any new `Thread`/`Executors`/`Timer` reference appears outside the allowlist. **`PvPManager.BATTLE_END_SCHEDULER` must be deleted and migrated to TickScheduler before this rule lands.**

2. **Ban plain `HashMap`/`HashSet`/`ArrayList` for `private static`/`public static` collections.** Project convention should be `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()` / `CopyOnWriteArrayList` (or `Collections.synchronizedList`). Local-method-scope `HashMap` is fine; class-scope statics are foot-guns. Specifically rewrite:
   - `PvPManager` lines 32–53
   - `RaidManager` lines 42, 45, 53, 55
   - `RaidLoginNotifier` lines 33–36
   - `FactionManager.FACTIONS` line 35
   - `GuardResistanceHandler` lines 24, 25
   - `WarEventHandler.savedInventories, disconnectedWarParticipants` lines 48, 50
   - `BesiegeDamageShieldHandler.LAST_BLOCK_MESSAGE`, `BesiegeEntityInteractHandler.LAST_DENY_MESSAGE`
   - `ColonyEventListener.colonyBuildingLevels`
   - `TaxManager.colonyTaxMap`

3. **All callers of `AsyncSaveExecutor.submit` must deep-snapshot mutable nested objects.** Add this rule to the AsyncSaveExecutor Javadoc and the project CLAUDE.md. The current pattern for `SpyManager.saveData()` (line 124–131) only `putAll`s the top-level map, sharing `SpyMission` references with the worker — this is wrong if `SpyMission` is mutated on the server thread during the write. Either:
   - Make `SpyMission`, `WarData`, `FactionData` etc. fully immutable (DTO clone before save), or
   - Move all saves to `TickScheduler` and write inline (slower, but no race).

4. **`WarSystem.saveActiveWars()` should snapshot `ACTIVE_WARS` before iterating** to avoid an iterator-time race with late-firing TickScheduler war tasks during `ServerStoppingEvent`.

5. **`TaxManagementScreen.getLatestSpyMissions()` should return an immutable copy** (`List.copyOf(latestSpyMissions)`), not the internal reference.

6. **Add an assertion helper** like `ServerThreadAssert.check()` that throws when called off the main server thread, and sprinkle it at the top of every public mutator in `WarSystem`, `SpyManager`, `TreasuryManager`, `OccupationManager`, `PvPManager`. Catches the next "I forgot enqueueWork" bug in dev.

---

## Files Audited

**Schedulers & threading utilities**
- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` — OK; central scheduler, ConcurrentHashMap, atomic counters, properly hooks ServerTickEvent.END
- `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java` — uses `Executors.newSingleThreadExecutor` and raw `new Thread`; intentional but flagged [CONC-5]
- `src/main/java/net/machiavelli/minecolonytax/db/WarStatsDB.java` — uses `new ThreadPoolExecutor` and raw `new Thread`; intentional but flagged [CONC-6]
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java` — **VIOLATION**: ScheduledExecutorService + many plain HashMaps [CONC-1, CONC-2]

**Network packet handlers (all 25 verified to wrap in `enqueueWork`)**
- `src/main/java/net/machiavelli/minecolonytax/network/EntityGlowPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/GlowClientHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimVassalTributePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissSpyMissionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/EndVassalizationPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/InvestmentDataResponsePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/OfficerDataResponsePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/OpenTaxGUIPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayTaxDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RecallSpyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestInvestmentDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestOfficerDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestSpyDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestTreasuryDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SetTaxPolicyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryActionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryDataResponsePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/UpdatePlayerTaxPermissionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/UpdateTaxPermissionPacket.java`

**Manager classes (collection-thread audit)**
- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java` — ACTIVE_WARS, pendingWarRequests, extortionImmunity all ConcurrentHashMap; saveActiveWars iterates live map [CONC-15]
- `src/main/java/net/machiavelli/minecolonytax/data/WarData.java` — internal lives/spectators/guard sets are CHM-backed; OK
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java` — All maps CHM; saveData snapshots top-level only (nested SpyMission shared) [CONC-5 caveat]
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java` — calls SpyManager from server-thread entity tick; OK
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java` — ACTIVE_OCCUPATIONS is CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java` — TREASURIES is CHM, DEFENDER_COLONIES is CHM keyset; OK
- `src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java` — all maps CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java` — **VIOLATION**: plain HashMaps [CONC-3]
- `src/main/java/net/machiavelli/minecolonytax/raid/GuardResistanceHandler.java` — plain HashMap [CONC-12]
- `src/main/java/net/machiavelli/minecolonytax/raid/EntityRaidManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java` — multiple plain HashMaps [CONC-2]
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java` — mutates PvPManager fields from packet handlers (server thread) and BATTLE_END_SCHEDULER callbacks (server.execute hop)
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java` — ServerTickEvent iterates plain HashMaps from PvPManager [CONC-2]
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java` — plain HashMap [CONC-11]
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java` — plain HashMap [CONC-11]
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/faction/FactionManager.java` — plain HashMap + reassignment [CONC-4]
- `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/trade/TradeRouteManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java` — all CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java` — CHM with nested CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/data/HistoryManager.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/economy/RaidPenaltyManager.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/economy/policy/TaxPolicyManager.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java` — plain HashMap (tick-only) [CONC-13]
- `src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/event/OfficerColonyVisitTracker.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/event/RaidLoginNotifier.java` — plain HashMaps for notified* [CONC-8]
- `src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java` — plain HashMap for savedInventories/disconnectedWarParticipants (server thread only today)
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java` — colonyTaxMap plain HashMap (tick-only access today) [CONC-14]
- `src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java` — CHM (client-thread only); OK
- `src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsPermissionSync.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/raid/ReflectionCache.java` — CHM; OK
- `src/main/java/net/machiavelli/minecolonytax/util/ColonyActivityTracker.java` — CHM; OK

**Persistence / lifecycle**
- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java` — ServerStarting/Stopping handlers; shutdown order verified [CONC-16]
- `src/main/java/net/machiavelli/minecolonytax/capability/PlayerWarDataCapability.java` — PlayerLoggedIn/Out + PlayerEvent.SaveToFile; all on server thread

**Client / GUI**
- `src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java` — `latestSpyMissions` volatile static returned by reference [CONC-9]
- `src/main/java/net/machiavelli/minecolonytax/network/GlowClientHandler.java` — CHM, client main thread only [CONC-10]
