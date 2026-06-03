# Adversary Codex: Edge Case + State Adversary

## Summary

War 'N Taxes manages long-lived, cross-restart state across at least eight independent JSON files plus per-entity NBT. The state machines for spies, wars, occupations, PvP battles, and vassal proposals are robust under happy-path execution, but they assume the server reaches its `ServerStoppingEvent` cleanly, that no two operations race on the same entity, that money/intel transfers are atomic, and that no JSON file is ever partially-written. None of these assumptions are enforced.

The single largest class of bugs found is **non-atomic state mutation**: nearly every "give X to player" / "claim Y" / "purchase Z" code path mutates the authoritative server-side map first, then attempts the secondary side-effect. If the secondary effect fails (player offline, integration mod missing, JVM dies, exception), the resource is permanently lost or duplicated. A second large class is **state that lives only in-memory** (TickScheduler tasks, PvP battles, vassal proposals, raid grace periods, sabotage effects in pending costs) — a crash or even a clean restart silently destroys it. A third class is **trust placed in `getOrDefault → put` without compare-and-set**, allowing two server-thread paths plus async-save snapshots to interleave.

I did not modify code. I read implementations, not tests.

---

## Critical State Bugs (data loss, soft-lock, permanent breakage)

### [EDGE-1] PvP battle players permanently stranded in arena on server crash
**File:** `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:32-44`, `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:84-105, 727-758`

**Scenario:** A 6-player team battle starts on a PvP map in dimension X. Each player's pre-battle `GlobalPos` is stored in `ActiveBattle.originalPositions` (HashMap, in-memory only) and their `GameType` in `PvPManager.playerOriginalGameModes` (HashMap, in-memory only). Two players have been defeated and are in `defeatedPlayers` (also in-memory). The server crashes (OOM, kernel oom-killer, host reboot, `ServerCrashedEvent`).

**Outcome:** `PvPManager` state is completely lost — there is no persistence layer for `activeBattles`, `playerOriginalGameModes`, `defeatedPlayers`, `playerInventories`, `originalPositions`, or `lockedMaps`. On restart:
- All players who were in the battle remain at arena coordinates.
- Defeated players who had been set to SPECTATOR by `PvPEventHandler.onLivingDamage:269` stay in SPECTATOR mode forever; on next login they cannot interact with the world.
- The map remains "locked" in nobody's view but as soon as someone tries to use it `lockedMaps` is empty so a new battle can be started on top of the stranded players.
- `restorePlayer()` is never called, so health/food/fire/gamemode are never reset.

**Detection hint:** After ServerCrashedEvent, scan PlayerList for anyone whose persisted position is inside a `PvPMap` bounding box. Grep `PvPManager.java` for `ConcurrentHashMap`/`HashMap` declarations with no corresponding `saveData()`.

---

### [EDGE-2] Tax claim debits colony, then silently fails to credit player wallet
**File:** `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java:137-158`, `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:296-313`

**Scenario:** Player runs `/claimtax MyColony 5000`. `TaxManager.claimTax` deducts 5000 from `colonyTaxMap` (line 300/304), calls `saveTaxData(true)`, returns 5000. The command then enters the SDMShop branch:

```java
long currentBalance = SDMShopIntegration.getMoney(player);     // line 154
SDMShopIntegration.setMoney(player, currentBalance + totalClaimed);  // line 155
```

If `SDMShopIntegration.setMoney()` throws (network shop addon, missing capability, version skew), the player is brought to their previous balance + 5000 in a CompletableFuture that may swallow the exception. There is no try/catch around lines 153-158 and no rollback of `colonyTaxMap`. There is also no idempotency token — a player rapidly double-clicking `/claimtax` (or having the command rebound to a key) hits `claimTax` twice; if the first call succeeded and the SDMShop API is slow, the second call also finds non-zero tax and deducts again.

**Outcome:** Tax disappears from colony but never reaches player wallet. Permanent loss. Across many cycles in production this can drain a colony's revenue silently.

**Detection hint:** Wrap line 155 in a try/catch; on failure restore via `TaxManager.adjustTax(colony, totalClaimed)`. Add `synchronized` to `claimTax` or use AtomicInteger.

---

### [EDGE-3] Spy mission DEPLOYING → ACTIVE never fires if travel completion straddles restart and colony has been deleted
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:729-799, 1026-1064`

**Scenario:** Player deploys a 12-minute SCOUT mission. After 4 minutes the server stops. Save persists the mission as `DEPLOYING` with `startTime` and `travelDurationMs`. Server is down 20 minutes. Meanwhile the target colony's owner uses the MineColonies admin command to delete the colony (other admin tool, on the file system). Server restarts. `SpyManager.tick()` evaluates `now - mission.getStartTime() >= mission.getTravelDurationMs()` → true, so it calls `spawnSpyEntityForMission(mission)` (line 742) which then calls `getColonyByWorld(...)` (line 1029-1030). Returns null. Logged at line 1032 as `LOGGER.warn`. The mission status is then set to `ACTIVE` at line 743 *regardless of whether the entity was created*. The mission now lives forever as ACTIVE with no entity. The only cleanup at line 814-828 fires for ACTIVE missions with null target colony, so eventually it does get cancelled — **but** lines 740-762 don't `continue` before the spawn failure; the spawn failure does not change status back. Specifically: status is set to ACTIVE, the "anyProcessed = true" path runs, the auto-cleanup at 814 fires next tick and the player gets "mission was cancelled — target colony no longer exists". OK, recoverable.

**The real bug:** the player whose colony was deleted is the *target*. The attacker has paid the pending cost (deducted in next tax cycle). The cost is never refunded — see `PENDING_COSTS` map; once consumed by `TaxManager.consumePendingCost`, gone. The mission was cancelled before any spy entity even arrived. Stolen funds.

**Outcome:** Attacker loses cost (50 to 500 currency) with no spy ever deployed; chat message says "cancelled" with no refund offered.

**Detection hint:** In the cancellation block at 818-826, also call `PENDING_COSTS.remove(mission.getAttackerColonyId())` and credit back via `TaxManager.adjustTax`.

---

### [EDGE-4] Treasury withdraw subtracts before checking destination availability
**File:** `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:203-251`

**Scenario:** Player calls `/treasury withdraw 500 wallet`. Line 228 does `TREASURIES.put(colonyId, currentBalance - amount);` BEFORE calling `CurrencyService.giveToPlayer` at line 231. If `giveToPlayer` returns 0, lines 233-241 restore the treasury — but if it *throws* (NPE in SDMShop adapter, ClassCastException on an upgraded item, server thread interruption), the exception bubbles up past the put-on-line-228, leaving treasury permanently debited and player uncredited. No surrounding try/catch.

**Outcome:** Treasury balance drops by `amount`, player receives nothing, no error logged because the exception propagates to command framework.

**Detection hint:** Wrap 230-241 in try/catch; restore in catch block too.

---

### [EDGE-5] War active_wars.json — delete-after-load is unconditional and runs even on partial failure
**File:** `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4004-4047`

**Scenario:** Server starts, `loadAndResumeActiveWars()` reads `active_wars.json` with 5 wars. War #3 throws because its `attackerColonyId` is a valid colony but its participant UUID parse fails (corrupted save, hand-edited entry). The catch at 4035-4038 logs and increments `skipped`. Loop continues. After the loop, line 4042 calls `Files.deleteIfExists(path)` unconditionally — **the source of truth for war #3 is now gone**.

Worse: if a `WarData` constructor throws partway through restoration of war #2, the partial state is left in `ACTIVE_WARS` (the `put` at line 4138 may have succeeded before a later line threw); now there's a half-built war with no boss bar, no drain task scheduled, and no save file to recover from.

Even worse: if the entire `loadAndResumeActiveWars` throws *before* the final `deleteIfExists` (e.g., a `LinkageError` from a class loading mismatch), the JSON is preserved, BUT `WarSystem.saveActiveWars()` on the next `ServerStoppingEvent` will overwrite it with whatever (possibly partial) state is in memory. No backup file is taken before overwriting.

**Outcome:** Crash recovery is one-shot. If anything goes wrong on the first restart after a crash, the war record is destroyed for good.

**Detection hint:** Before `deleteIfExists`, rename the file to `active_wars.json.consumed-<timestamp>`. Save `saveActiveWars` should write `.tmp` then `Files.move` with `ATOMIC_MOVE`.

---

### [EDGE-6] All JSON saves are non-atomic — JVM kill mid-FileWriter produces corrupt file
**File:** every `saveData()` in `SpyManager.java:121-142`, `OccupationManager.java:584-603`, `VassalManager.java:523-537`, `TreasuryManager.java:509-518`, `ColonyUpgradeManager.java:144-152`, `WarSystem.java:3994-3996`

**Scenario:** Server is under load. Player A executes `/spy deploy`. `SpyManager.saveData()` queues an async write; the worker opens `espionage.json` for writing (truncates it) and starts streaming JSON. The host process is killed (OOM kill, `kill -9`, power loss). When the server next starts, `espionage.json` exists but is truncated to `{"activeMissions":{` or similar invalid JSON.

`SpyManager.loadData` line 84 `GSON.fromJson(reader, SpySaveData.class)` throws `JsonSyntaxException`. The catch at line 116 logs and returns. **All spy missions, all completed missions, all pending costs, all stolen-secrets buffs, all bribe data are silently dropped.** Pending costs that were already debited via tax cycle are not refunded. Stolen-secrets buffs that players paid for are gone.

Same vulnerability exists for occupations (irretrievable land claims), treasury balances (irretrievable currency), vassal relationships, upgrades, wars.

**Outcome:** A single OOM-kill during a save permanently corrupts up to 6 distinct persistence files.

**Detection hint:** Replace direct `FileWriter` with write-to-`.tmp` then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. Keep a `.bak` of the last successful save.

---

### [EDGE-7] PvPManager.BATTLE_END_SCHEDULER tasks survive ServerStoppingEvent but reference dead state
**File:** `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:59`, `PvPBattleManager.java:72-79, 711-725`

**Scenario:** Battle ends in winner. `scheduleBattleEnd` posts to `BATTLE_END_SCHEDULER` a 5-second delayed task. Within those 5 seconds the server begins shutdown. `BATTLE_END_SCHEDULER` is a `ScheduledExecutorService` created at field init — it is **never shut down**. The task fires AFTER `ServerStoppingEvent`, calls `ServerLifecycleHooks.getCurrentServer()` which returns the still-stopping server or null. If non-null, it queues `server.execute(...)` against an executor that may have already drained. The teleport executes against a player that has already been removed from PlayerList.

In single-player or integrated server (which uses MinecraftServer lifecycle), the scheduled thread may execute *after the next world load*, attempting to restore positions on the new world.

**Outcome:** Race-conditioned teleports between worlds, NPEs in shutdown logs, possible duplicate "restorePlayer" calls. The `defeatedPlayers` race condition becomes deterministic on shutdown.

**Detection hint:** `BATTLE_END_SCHEDULER.shutdownNow()` in `MineColonyTax.onServerStopping`.

---

### [EDGE-8] All TickScheduler tasks are lost on server restart
**File:** `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:26-121`

**Scenario:** Hundreds of TickScheduler tasks exist at any given moment: war countdown warnings, treasury drain loops (one per active war), occupation expiry checker, BesiegeManager tick, DB snapshot loop, abandonment check loops, war timer warnings, PvP countdowns, etc. `TASKS` is a ConcurrentHashMap with no persistence.

On `ServerStoppingEvent` line 74-81 clears the map. On restart `WarSystem.loadAndResumeActiveWars` re-schedules war drain + timer warnings (good — see line 4180), but:
- `BesiegeManager.tick()` repeating schedule is re-armed via `MineColonyTax.onServerStarting:261-267` (good).
- Occupation expiry check is re-armed (good — 273-279).
- **However:** A war that ended exactly at `warStartTime + WAR_DURATION_MINUTES` during downtime is detected at 4102-4105 as "expired during server downtime, skipping restoration" — but its **occupation, reparations, ranks, and economic effects are NEVER STARTED**. The defender gets no end-of-war reparations, the attacker gets no victory occupation, hostile ranks remain on both colonies until `PermissionsHealthCheck` runs 5 seconds later (it may not be aware of the won war).
- A war in JOINING phase at 4106-4112 is force-transitioned to INWAR with `e.warStartTime = now`, EVEN IF the join phase ended hours ago during downtime. Defender suddenly faces a "fresh" war they thought ended.

**Outcome:** Players who won a war during downtime get no rewards. Players who started a join phase right before downtime are surprised by a war that re-starts on restart.

**Detection hint:** Add an "end of war during downtime" handler in resume path that calls the normal `endWar(colony)` logic when `now > warStartTime + warDuration`.

---

### [EDGE-9] Concurrency: claimTax non-atomic — double-claim possible from two threads
**File:** `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:244-313`

**Scenario:** Player has command rebound to mouse button; misclicks rapidly. Two server command threads (or one ServerTickEvent thread + one command thread) both enter `claimTax` for the same colony.

- Thread A reads `storedTax = 5000` at line 247.
- Thread B reads `storedTax = 5000` at line 247.
- Thread A passes guards, sets `colonyTaxMap.put(colonyId, 0)` at line 300.
- Thread B passes guards (since it already cached storedTax=5000), sets `colonyTaxMap.put(colonyId, 0)` at line 300.
- Both return 5000. Player gets 10000 from a 5000 colony balance.

`colonyTaxMap` is a `ConcurrentHashMap` (presumably) but the `getOrDefault → put` sequence is not atomic. The CONTAINS check at 271 (RaidManager) is also racy w.r.t. raid start.

**Outcome:** Tax duplication. Especially dangerous combined with EDGE-2.

**Detection hint:** Convert to `colonyTaxMap.computeIfPresent(colonyId, (k, v) -> ...)` returning the claimed amount via AtomicInteger holder, or wrap entire method in `synchronized` on a per-colony lock.

---

### [EDGE-10] Vassal proposals not persisted — accept-after-restart impossible
**File:** `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:41-42, 523-537, 505-521`

**Scenario:** Player A sends vassalization proposal to Player B's colony. B sees `[Accept] [Decline]` buttons. B logs off; server restarts.

`loadData` reads only `VassalRelation` entries from the file. `PENDING_PROPOSALS` is in-memory only. On restart it is empty. When B logs back in and clicks `[Accept]`, the command `/wnt vassalaccept <colonyId>` hits `acceptProposal(colonyId)` which returns "No pending proposal for this colony."

**Outcome:** Proposal silently lost. Player B has stale chat buttons. Player A's UI still shows "Awaiting response..." indefinitely (since `OFFLINE_MESSAGES` is also in-memory). They have no way to resync.

**Detection hint:** Persist `PENDING_PROPOSALS` to the same file; on load, fire expiration timer.

---

## High (broken feature under realistic conditions)

### [EDGE-11] Spy DEPLOYING + recall during travel — no cost refund
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:309-348`

**Scenario:** Player deploys spy with 10-minute travel time. After 30 seconds they realise wrong target, click recall. Mission goes DEPLOYING→RECALLED immediately. `PENDING_COSTS` for that colony is NOT cleared — next tax cycle deducts the full cost even though the spy never even arrived.

**Outcome:** Recall before arrival costs the full mission cost. Players will perceive this as bug, not a "you spent on travel" feature, since no message explains it.

**Detection hint:** In recall DEPLOYING branch, subtract `mission.getCost()` from `PENDING_COSTS` or refund via `TaxManager.adjustTax`.

---

### [EDGE-12] Spy bribed-guards and stolen-secrets buffs survive colony deletion / become orphan keys
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:48-50`

**Scenario:** Colony A bribes guards of Colony B (`BRIBED_GUARDS.put(B, 5)`). Colony B is then deleted by its owner. `BRIBED_GUARDS` entry for B is never cleaned up. Years later, if B's colony ID is reused (MineColonies recycles IDs after enough deletions), the new colony at that ID inherits 5 bribed guards on its first raid. Same for `STOLEN_SECRETS_BUFF` and `SABOTAGE_EFFECTS`.

**Outcome:** Phantom debuffs applied to unrelated colonies. Detection is nearly impossible since the bribe message was years ago.

**Detection hint:** On `ColonyDeletedModEvent`, scrub all maps keyed by `colonyId`. Currently only `FirstColonyTracker` listens.

---

### [EDGE-13] Occupation expiry timer uses System.currentTimeMillis — clock jumps backwards = forever-occupation
**File:** `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:103-109, 416-433`

**Scenario:** Server admin's NTP daemon corrects the clock backwards by 1 hour (DST transition fall-back, manual fix after BIOS reset, container date sync). All `expirationTime` fields stored as absolute epoch ms now appear 1 hour in the future. Same effect: war durations, spy travel ETAs, cooldowns, stolen-secrets buff, raid grace periods all extend.

If clock jumps backwards by 7 days (unusual but possible after a hardware failure), an occupation about to expire suddenly has 7 days left.

**Conversely**, forward clock jumps (NTP corrects a slow clock) make all timers expire immediately, including cooldowns. Player who deployed a spy 5 seconds ago suddenly receives "spy arrived" because `now >= startTime + travelDurationMs`.

**Outcome:** Time-based contracts can be cancelled or extended arbitrarily by clock manipulation. Server admin doesn't even need to be malicious — just unlucky.

**Detection hint:** Use `System.nanoTime()` for relative durations; persist `remainingMs` instead of `expirationTime`.

---

### [EDGE-14] Sabotage applied via SABOTAGE_EFFECTS map — applied to wrong colony if target's ID is reused
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:957-961` + EDGE-12

Same orphan-key risk as bribed-guards; SABOTAGE_EFFECTS stays until consumed by a tax cycle. If target colony is deleted before its next tax cycle, the effect orphans.

---

### [EDGE-15] Spy DEPLOYING with same-colony source and target — division by zero risk
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:151-155, 270-273`

Self-targeting is blocked at line 151. But: what if `attackerColonyId == -1` (no attacker colony) and target == 0? Distance becomes 0, travelMinutes = `0 / (blocksPerMinute * speedMultiplier)` = 0. Clamped to min by `Math.max(minMs, ...)` so probably OK. But `speedMultiplier` can theoretically be 0 if upgrade multiplier is configured to -1 — Math.max ensures non-negative travel time but doesn't catch divide-by-zero (Java: `0 / 0.0` = NaN, then `(long) (NaN * 60000)` is 0, clamped → minMs). So no crash, but a subtle: travel time floor is hit even for very long distances if speedMultiplier=0 due to misconfig.

---

### [EDGE-16] War initialization when defender colony has 0 citizens
**File:** `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:186-228`

If the defender colony has no players in perms (only the owner UUID, who is offline), `defenderPerms.getPlayers()` returns just the owner. Players in `getDefenderLives()` map will be just the owner. If owner is offline, the war boss bar is never displayed to anyone on defender side. The war runs to timeout with no defender able to participate. Attacker wins by default if `initialDefenderTotalLives` <= 0 makes `lives.values().stream().mapToInt(...).sum()` = `playerLives`. Auto-win, no fight.

**Outcome:** Easy exploit — wait for target owner to log off, declare war, win uncontested.

**Detection hint:** Require `ALLOW_OFFLINE_RAIDS`-style config gate for wars; or refuse war declaration if no defender is online.

---

### [EDGE-17] PvP arena player who disconnects has `originalPositions` map entry that never clears
**File:** `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:840-854`, `PvPManager.java:53` (defeatedPlayers)

`cancelBattleDueToDisconnect` calls `endBattle(battleId)` which removes from `activeBattles` but the per-player maps (`spectatorData`, `playerOriginalGameModes`) are only cleaned for non-defeated players via `playerOriginalGameModes.remove(playerId)` at line 754. For the disconnected player specifically:
- `defeatedPlayers.remove(playerId)` was called at line 238 (handlePlayerDisconnect) BEFORE `cancelBattleDueToDisconnect`, so they're not in defeatedPlayers anymore.
- They're in `originalPositions` of the ActiveBattle which is removed from `activeBattles`.
- Their `playerOriginalGameModes` entry IS removed via line 754 since they're NOT in `defeatedPlayers` now.
- BUT their actual entity is still at arena coordinates with arena gamemode, and they're now offline.

**Outcome:** When they rejoin, they're still in arena with combat gamemode. The battle is gone, the arena is unlocked, but they have no way back home. They didn't disconnect by choice maybe (kicked, ISP cut) — now stranded.

**Detection hint:** On disconnect mid-battle, teleport them back to original location BEFORE removing from PlayerList; persist their original position so we can restore on next login.

---

### [EDGE-18] War extortion immunity in `extortionImmunity` map not persisted
**File:** `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:61`

`private static final Map<Integer, Long> extortionImmunity = new ConcurrentHashMap<>();` — no save/load methods. Paying extortion grants immunity, server restarts, immunity gone, attacker can extort again immediately.

---

### [EDGE-19] ColonyUpgradeManager.purchase races with TreasuryManager.purchase
**File:** `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java:64-76`

```java
int currentLevel = data.getLevel(type);                    // read
if (currentLevel >= TaxConfig.getUpgradeMaxLevel()) return false; // check
int cost = getUpgradeCost(colonyId, type);                 // compute
if (!TreasuryManager.purchase(colonyId, cost, server)) return false; // deduct
data.setLevel(type, currentLevel + 1);                     // write
```

Two simultaneous officer purchases of the same upgrade:
- Both read level=5 (assume cap=10), both compute cost=1000.
- Both enter `TreasuryManager.purchase`.
- `TreasuryManager.purchase` line 476-483 has the same `getTreasuryBalance → put` race.
- Both `purchase` calls succeed if treasury had >=2000.
- Both call `data.setLevel(type, 6)` — wait, both write level 6, not 6 and 7. So colony pays for two upgrades but receives one level.

**Outcome:** Officer-double-purchase pays 2× cost for 1× upgrade. Money sink for the colony.

**Detection hint:** Wrap purchase in `synchronized` on `data`, or use `ColonyUpgradeData.atomicIncrement(type)`.

---

### [EDGE-20] War saving on shutdown: TreasuryManager.shutdown called during war drain loop
**File:** `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:340-342`

The drain loop calls `TreasuryManager.shutdown()` every 5 minutes as a "periodic save". `shutdown()` calls `saveData()` — fine in normal operation. But `MineColonyTax.onServerStopping` also calls `TreasuryManager.shutdown()` at line 319 explicitly. If a drain tick fires during shutdown (between TickScheduler.shutdown not yet called and TreasuryManager.shutdown being called), the save runs twice in rapid succession. The second `FileWriter` may truncate the file the first is reading (or vice versa) — race on stream.

Worse, calling `shutdown()` as a periodic save is a code smell that implies the method has side-effects beyond saving. If `shutdown()` is ever changed to also clear in-memory state, the war drain loop will start clearing the live treasury maps mid-war.

**Outcome:** Latent bug; correct today but fragile.

**Detection hint:** Make `saveData()` package-private and call it directly, never `shutdown()`.

---

## Medium (rare conditions, narrow window)

### [EDGE-21] BesiegeManager and OccupationManager both call AsyncSaveExecutor with no FILE LOCK
**File:** `util/AsyncSaveExecutor.java:50-69`

Coalesces by key. If two different managers happen to use the same key by accident, only the last submit wins. No documentation enforces unique keys; current keys are `"espionage"`, `"occupations"`, `"vassals"`. Future contributor could re-use `"upgrades"` or similar and silently drop writes.

### [EDGE-22] Player UUID used as map key — login by different player with same display name pre-online-mode
**File:** Multiple managers

If `online-mode=false`, two different players using the same username get different UUIDs (offline UUIDs are derived from username, so actually same — but if one player switches usernames, their UUID changes). All player-keyed maps (`pendingNotifications`, `OFFLINE_MESSAGES`, `playerStats`, `playerOriginalGameModes`, `attackerLives`, etc.) become stale.

### [EDGE-23] Tax rate 100% or higher creates negative tax floor
**File:** `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:509`

`int generatedTax = (int) (taxWithHappiness * combinedMultiplier);` — if `combinedMultiplier` is negative (manually set in config or via stacked random events × policy × happiness < 0), tax is negative. Then `incrementTaxRevenue` line 336 does `Math.min(currentTax + negativeTax, maxTax)` which decreases the balance, not increases. Maintenance then deducts MORE. Building generation now drains the colony.

### [EDGE-24] Spy travel distance from colony center may target wrong dimension
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:250-278`

`SERVER.overworld()` is hardcoded. If MineColonies/CompactClaims allows nether colonies (it does, with config), spy travel calculates distance in overworld coordinates between two nether colonies. Could be way off. Spy entity spawns in overworld at target colony's recorded center (which is the *nether* center read as overworld coords) — likely under bedrock or in a void.

### [EDGE-25] PvP arena map can be in a different dimension; player teleport on arena cleanup may fail
**File:** `PvPBattleManager.java:922-944`

`teleportTo` calls `server.getLevel(pos.dimension())` — if that dimension has been unloaded (cleanup mod, dimension removed mid-restart), returns null. Method returns silently. Player remains at arena, no error to the player or admin.

### [EDGE-26] War join phase end during downtime resets warStartTime — countdown re-armed
**File:** `WarSystem.java:4106-4112`

`status = WarData.WarStatus.INWAR; e.warStartTime = now;` — But `joinPhaseEndTime` is not reset, so `scheduleTimerWarnings(warData, remaining)` may compute a `remaining` based on `warStartTime + warDurationMs - now` = full duration. Player who never had a chance to react gets a fresh full-duration war.

### [EDGE-27] Spy IsActiveMission check at SpyEntity:122-126 only runs every 6000 ticks (5 min)
**File:** `espionage/SpyEntity.java:120-127`

A spy entity whose mission was completed/killed server-side stays in the world for up to 5 minutes before noticing it's orphaned. During that window, guard kills can fire `onSpyKilled` which then does nothing (mission already removed) — but client-side, players see the spy entity. If a player attacks it, `hurt` calls `enterFleeState`, which calls `SpyManager.onSpyDetected(missionId)` for an inactive mission — no-op but increments fleeAttemptTicks. Eventually `discard()` at line 164 fires; this calls `SpyManager.onSpyKilled` for an inactive mission — no-op. So the entity just disappears, but for up to 5 min there was a confusing ghost spy.

### [EDGE-28] FirstColonyTracker bootstrap on TickScheduler 6000-tick delay can race with active war restore
**File:** `MineColonyTax.java:131-139` (bootstrap) vs `204` (war restore)

War restore runs at server-start synchronously; FCT bootstrap runs in 5 minutes (6000 ticks). If a war ends during those 5 minutes and triggers OccupationManager flow that consults `FirstColonyTracker.isFirstColony`, the answer may be wrong (not yet bootstrapped). Could route a Primary colony into TRANSFER_PENDING by mistake.

### [EDGE-29] Spy `dismissMission` allows player to wipe a non-existent mission
**File:** `espionage/SpyManager.java:1003-1011`

No-op if not in COMPLETED_MISSIONS, fine. But: `dismissMission` is called by client → server packet. A malicious client could spam dismissals with fabricated `missionId`s, each forcing a `saveData()` if the (unrelated) match happens to succeed. Even without match, no rate limit on the packet handler. Low impact but: confirm there's a rate limiter on the network packet.

### [EDGE-30] Boss bar created in `loadAndResumeActiveWars` never has players added if they're not online at restart
**File:** `WarSystem.java:4144-4153`

Loop only adds online players to boss bar. If a player joins AFTER war is restored, there's no `PlayerLoggedIn` event handler that adds them to the boss bar. So they see no war indicator despite being a participant. (Maybe one exists elsewhere — `WarEventHandler` — but I didn't see it in this file. If absent, real bug.)

---

## Suggested invariants to enforce

1. **Atomic file writes for all JSON state.** Use `Files.write(tmp, ..., REPLACE_EXISTING)` then `Files.move(tmp, real, ATOMIC_MOVE, REPLACE_EXISTING)`. Keep a `.bak` of the previous version.
2. **Atomic state mutations.** `claimTax`, `TreasuryManager.purchase/withdraw/deposit`, `ColonyUpgradeManager.purchase` must use `computeIfPresent` / per-colony `synchronized` blocks / `AtomicInteger`. The "read then put" pattern is the root of EDGE-2, EDGE-4, EDGE-9, EDGE-19.
3. **Try/finally around external integrations.** Any call into SDMShopIntegration, CurrencyService, MineColonies API that follows a state mutation must restore the state on exception. The current "check then throw" pattern leaks resources.
4. **ColonyDeletedModEvent fanout.** Every manager keyed by colonyId must subscribe to colony deletion and clean its maps. Currently only FirstColonyTracker does. Build a `ColonyCleanupBus` so missing one is a compile error.
5. **No `System.currentTimeMillis()` for relative durations.** Use a server-tick counter or `System.nanoTime()`. Persist `remainingMs` rather than absolute expirations.
6. **PvP state must be persisted.** Stranded players in arenas after a crash will rage-quit. `originalPositions`, `playerOriginalGameModes`, `defeatedPlayers`, `lockedMaps` need JSON backing.
7. **TickScheduler tasks for end-of-war / end-of-occupation effects must be idempotent and replayable on restart.** Right now they vanish silently.
8. **Vassal proposals (and any "pending request waiting on second party") must persist** with a TTL, OR be auto-cancelled and re-issued on restart.
9. **Pending resource costs (`PENDING_COSTS`, etc.) must be refundable.** Any mission cancellation path before the resource is "used" must walk back the pending charge.
10. **`BATTLE_END_SCHEDULER` and any other static `ScheduledExecutorService` must be shutdown** in `MineColonyTax.onServerStopping`, before `AsyncSaveExecutor.shutdownAndFlush`.
11. **Unique key namespace for `AsyncSaveExecutor.submit`.** Make keys an enum or a `final class Key` to prevent accidental collision (EDGE-21).
12. **Idempotency tokens on all "claim" / "purchase" commands.** A debounce window or single-flight lock to defend against double-click and command-key-rebinding (EDGE-2, EDGE-9).

---

## Files Audited

- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java` (1122 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java` (429 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyMission.java`
- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java` (focused: persistence 3900-4200, drain 324-352)
- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java` (focused: 85-407)
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java` (638 lines)
- `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java` (534 lines)
- `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java` (153 lines)
- `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java` (focused: 1-200, 505-600)
- `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java` (focused: 1-200, grace periods)
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java` (focused: 1-200)
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java` (88 lines)
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java` (1043 lines)
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java` (374 lines)
- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` (121 lines)
- `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java` (113 lines)
- `src/main/java/net/machiavelli/minecolonytax/util/ItemUtils.java` (287 lines)
- `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java` (176 lines)
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java` (focused: claimTax 244-318, generateTaxesForAllColonies 412-580)
