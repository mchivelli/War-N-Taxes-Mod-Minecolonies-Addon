# Optimization Audit — Server Stability at Scale

**Date:** 2026-06-03 · **Target profile:** 100+ concurrent players, hundreds of colonies, weeks of uptime.
**Method:** four parallel code audits (tick/periodic loops · high-frequency event handlers · conflict-state managers & memory · persistence & threading), top findings spot-verified against source + GitNexus call graph.

> The Minecraft server runs all game logic on **one main thread**. Anything that scales with colony-count or player-count *per tick*, does *synchronous disk I/O* on that thread, or *grows without bound*, costs TPS at scale. Findings below are ranked by real-world impact for the target profile.

---

## Verdict

The mod is **architecturally sound** but has a handful of **genuine TPS hotspots** that will hurt a large server. None are crashes; they are throughput drains. The 3 CRITICAL + a few HIGH items account for the vast majority of the risk and are mostly small, low-risk fixes (mostly: route existing saves through the existing async writer, add cheap early-outs, capture one task id). Most subsystems are already well-built (see "Already good").

---

## Implementation status (2026-06-03 pass — all codex-gated + compiled green)

**FIXED (14 of 16 CRITICAL+HIGH):**
- ✅ **C1** RandomEventManager — save once per cycle (was per-colony O(N²)).
- ✅ **C2** null-owner scan throttled 5s → 5min.
- ✅ **C3** BesiegeDamageShield — per-(raid,source) colony-mate cache with 5s TTL (catches rank changes).
- ✅ **C4** join-phase countdown task now captured + cancelled (leak closed).
- ✅ **H1** TreasuryManager save → AsyncSaveExecutor.
- ✅ **H2** war-drain uses async `save()` (not `shutdown()`).
- ✅ **H3** WarExhaustionManager save → async (warLosses deep-copied).
- ✅ **H4** TaxManager saveTaxData → async + atomic temp-move.
- ✅ **H5** RaidKillTracker early-out before colony lookup.
- ✅ **H6** BesiegeManager.hasActiveRaids() (O(1)) in the block-filter fast path.
- ✅ **H7** AbandonedColonyProtectionHandler early-out (lazy-load preserved).
- ✅ **H8** guard-tower detection: per-instance displayName + class-cached toString (also fixed a latent mis-count in `isGuardTower` for all callers).
- ✅ **H11** ColonyEventListener per-second scan skipped in production + leak avoided.
- ✅ **H12** besiege dimension cached on `BesiegeRaidData`; mixin matches in O(1).
- Bonus: `AsyncSaveExecutor.shutdownAndFlush()` made a proper barrier (await worker before inline flush) — fixes a same-file shutdown race for all async savers.

**DEFERRED (2, with rationale — correctness risk outweighs benefit):**
- ⏸ **H9** `getActiveWarForPlayer` — already early-returns O(1) for direct participants; the expensive FTB/permission work only runs for non-participants. A reverse-index restructure of war-membership logic is correctness-critical and the gain only matters with dozens of simultaneous wars (edge case). Left as-is.
- ⏸ **H10** `VassalManager.getColonyById` — the `getAllColonies().stream()` is the *correct* cross-dimension lookup; there is no safe O(1) replacement without a maintained colony-lifecycle index. The cost is interval-bounded (once per tax cycle). Left as-is to avoid a multi-dimension correctness bug.

MEDIUM/LOW items (M1–M5, L1–L3) below remain as future work.

---

## Already good (no change needed) — establishes the baseline

- **`TickScheduler`** — runs deferred work on the main thread, iterates only active tasks, self-removes one-shots, short-circuits when empty. Correct design. (One caller leaks — see C4.)
- **`AsyncSaveExecutor`** — off-thread, per-key coalescing, atomic temp+move, flush-on-shutdown. The right pattern — but only ~5 of ~20 persistence managers use it (see HIGH/persistence).
- **`BlockInteractionFilterHandler`** — model fast-path: O(1) `isEmpty()` guards gate the expensive colony-by-position lookup; deliberately ignores `LeftClickBlock`. (One allocating line defeats it — see H6.)
- **`WarBlockLedger` / `TownHallDemolitionObjective` / `PlantTheBannerObjective`** explosion+block handlers — early-out on `ACTIVE_WARS.isEmpty()` / experimental-flag / `instanceof` before any work.
- **`SpyManager`** — TTL eviction of completed missions, per-player caps, lazy cooldown eviction. Best-behaved manager.
- **`RaidManager` / `BesiegeManager` / `OccupationManager` / `VassalManager` collections** — bounded, cleaned on end/expiry; besiege uses an O(1) colony→raid index.
- **`WarStatsDB`** — fully off-thread, bounded queue, TPS-aware skip. No off-thread game-state mutation anywhere in the mod.

---

## CRITICAL — fix before any large deployment

### C1. `RandomEventManager` — O(N²) synchronous disk writes every tax cycle
`TaxManager.generateTaxesForAllColonies()` calls `RandomEventManager.onTaxCycle(colony)` **once per colony** (`TaxManager.java:1002`), and `onTaxCycle` calls `saveData()` (`RandomEventManager.java:126`), which **synchronously serializes the entire mod-wide event state** (`:832`). At 300 colonies that's **300 full-file blocking writes per tax cycle, each O(N) in size → O(N²) disk work on the main thread**. Single worst hotspot.
**Fix:** Remove the per-colony `saveData()`; save **once** after the tax loop, routed through `AsyncSaveExecutor.submit("random_events", snapshot)`. Turns N² sync writes into 1 coalesced async write.

### C2. `emergencyFixAllNullOwners()` — all-colony permission scan every 5 seconds, forever
Fired from the tax tick handler (`TaxManager.java:115`) every 100 ticks → `ColonyAbandonmentManager.emergencyFixAllNullOwners()` iterates **all** colonies calling `getPermissions()`/`getOwner()` each. ~3,600 permission lookups/min at 300 colonies, perpetually, with **zero work in the steady state**. This is a load-time repair masquerading as a 5-second loop.
**Fix:** Run it on `ServerStartedEvent` (+ the existing deferred startup passes) and after colony delete/abandon events only; or throttle to several minutes behind a dirty flag.

### C3. `BesiegeDamageShieldHandler.areColonyMates` — O(allColonies) scan on every player-dealt damage during a besiege
`LivingHurtEvent` is one of the highest-frequency events. The handler early-outs cheaply when no besiege is active, but once a besiege is running, every player-sourced damage tick calls `areColonyMates` (`:61`), which loops `getAllColonies()` with **two permission lookups per colony** (`:82`), plus another `getAllColonies()` stream at `:124`. With hundreds of colonies, a single sword swing during a besiege walks the whole colony list.
**Fix:** Precompute the besieger's colony-id set at besiege start (store on `BesiegeRaidData`); reorder so the O(1) `isDefenderSideTarget` check runs first and short-circuits; cache the per-pair result for the besiege duration.

### C4. Leaked repeating task per war declaration — unbounded `TickScheduler.TASKS` growth
The join-phase countdown timer (`WarSystem.java:2395`) calls `TickScheduler.scheduleRepeating(...)` but — unlike `warChestDrainTaskId` (`:325`) and `countdownTaskId` (`:2560`) — **does not capture the returned task id**, so it is never cancelled. It re-arms every second forever and its closure retains the whole `WarData` (boss bars, lives maps, colony refs). Every war with a join phase permanently leaks one task + one WarData. A real slow leak under sustained war activity.
**Fix:** Capture the id into a `WarData.joinCountdownTaskId` field and `TickScheduler.cancel()` it when the join phase ends (and in `finalizeWarStart`/`endWar`), mirroring the two timers that already do this correctly.

---

## HIGH — fix for smooth operation at scale

| # | Area | File:line | Problem | Fix |
|---|------|-----------|---------|-----|
| H1 | Persistence | `TreasuryManager.java:534` | Sync full-map write on **every** deposit/withdraw/purchase | Route through `AsyncSaveExecutor.submit("treasury", snapshot)` |
| H2 | Persistence | `WarSystem.java:335` | War drain calls `TreasuryManager.shutdown()` (== saveData) every 5 min **per war** as a periodic save | After H1, drop the manual call; rely on coalesced async write |
| H3 | Persistence | `WarExhaustionManager.java:432` | Sync write of 5 colony-keyed maps on ~every war state transition | Async submit with caller-thread snapshot |
| H4 | Tick / I/O | `TaxManager.java:1141,314,1220,1293` | `saveTaxData()`/timestamp written **synchronously** per cycle **and per player claim/debt-payment**; no atomic temp+move | Async submit + atomic write |
| H5 | Event | `RaidKillTracker.java:34` | `LivingDeathEvent` for citizens has **no conflict early-out** — colony lookup + (raid-related) double full-citizen `.stream()` on **every citizen death** across all colonies | Add top guard: return if raids+claiming+wars all empty; gate eager debug-arg expressions behind `isDebugLogging()` |
| H6 | Event | `BesiegeManager.java:1063` via `BlockInteractionFilterHandler.java:104` | The "free" block-interaction guard calls `getActiveRaids()`, which **allocates+copies a HashMap on every block break/place/use** | Add `BesiegeManager.hasActiveRaids()` (O(1) `!ACTIVE_RAIDS.isEmpty()`) and call that |
| H7 | Event | `AbandonedColonyProtectionHandler.java:26,42,58` | Unconditional `getColonyByPosFromWorld` on every block break/place/use (twice on right-click), no early-out, even when zero colonies abandoned | Add "any abandoned colonies?" `isEmpty()` guard; resolve colony once on right-click |
| H8 | Tick | `TaxManager.java:499,622` | Iterates colony buildings **twice** per cycle + per-building `building.toString().toLowerCase()` (expensive alloc); reimplements guard-tower detection instead of cached `WarSystem.isGuardTower` | Single building pass; reuse `isGuardTower` cache |
| H9 | Managers | `WarSystem.java:2134` | `getActiveWarForPlayer` = O(wars × FTB/permission lookups), called on hot paths | Reverse index `playerId → colonyId` built at war start, cleared at end |
| H10 | Managers | `VassalManager.java:494` | `getColonyById`/`getPrimaryColonyOfPlayer` = O(allColonies) stream, called **inside the tax cycle** per vassal | Use direct colony-by-id API + `FirstColonyTracker` |
| H11 | Tick / Memory | `ColonyEventListener.java:33,57` | All-colony × all-building scan **every second** purely for cosmetic logging; `colonyBuildingLevels` map **leaks** (keyed by `IBuilding`, never cleaned) | Early-return when logging off; drive from MC building events; clean/rekey the map |
| H12 | Mixin | `WorldTickHandlerMixin.java:87` | Besiege branch does `getAllColonies().stream()` **per besiege per level-tick** (war/raid branches already avoid this) | Cache colony `Level`/dimension on `BesiegeRaidData` |

---

## MEDIUM / LOW (summary)

- **M1** `ColonyDataCollector` — O(colonies × buildings + citizens) main-thread scan on **every** tax-GUI open; cache per-player with a short TTL. (`server/ColonyDataCollector.java:29`)
- **M2** Entity-raid scan iterates **all entities in the level** per colony every 10s (when enabled) → use a bounded AABB query. (`event/ColonyEventListener.java:134`)
- **M3** `RaidPenaltyManager` / `TaxPolicyManager` / `TradeRouteManager` / `ColonyUpgradeManager` — sync per-change O(colonies) writes → async (mechanical). 
- **M4** `OccupationManager.findColonyById` / `FirstColonyTracker.getFirstColonyOwner` — O(n) scans on warm paths → direct id lookup / reverse index.
- **M5** `FirstColonyTracker.saveData()` sync + non-atomic on every colony add/remove → async + temp-move.
- **L1** `GuardResistanceHandler` uses plain `HashMap` (latent off-thread hazard) → `ConcurrentHashMap`.
- **L2** `MilitiaSpawner` bonus spawn count is unclamped → clamp/stagger a large war-start spawn batch.
- **L3** Several managers write non-atomically (truncate risk on crash) → standardize temp+atomic-move (folds into the async conversion).

---

## Recommended fix order (effort → impact)

1. **C1** RandomEventManager save-once-async — *biggest single win, ~10 lines.*
2. **C2** throttle/relocate the 5s null-owner scan — *removes a perpetual all-colony loop.*
3. **C4** capture+cancel the join-phase task — *stops a real memory/task leak, ~5 lines.*
4. **H4 + H1 + H3 + H2** route the four hottest saves through `AsyncSaveExecutor` — *removes sync disk I/O from tick + player-action paths; reuses existing infra.*
5. **C3 + H5 + H6 + H7** event-handler early-outs/indexes — *removes per-hit / per-block-break amplification.*
6. **H8–H12, M/L** — incremental.

Items 1–4 are low-risk, high-impact, and mostly reuse existing patterns (`AsyncSaveExecutor`, the `isGuardTower` cache, the task-id-capture idiom). They should be codex-gated and re-tested (compile + a boot/TPS check) like the rest of the project.
