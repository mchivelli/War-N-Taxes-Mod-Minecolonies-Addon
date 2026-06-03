# Defensive Audit 02 — War, Peace, and War Persistence

Audit scope: `WarSystem.java`, `WarData.java`, `PlayerWarData.java`, `PlayerWarDataManager.java`, `peace/*`, `event/WarEconomyHandler.java`, `event/WarVictoryEvent.java`, `commands/WarStatsCommand.java`, `capability/PlayerWarDataCapability.java`, `MineColonyTax.java` lifecycle hooks, and `economy/WarExhaustionManager.java` (war tax modifier).

## Summary

The war subsystem is large (`WarSystem.java` is 4244 lines) and the persistence layer is bolted on to a model class that has accumulated runtime, transient, and metadata fields without a clear separation. The restoration constructor for `WarData` now takes **28 parameters** (CLAUDE.md says 27 — the contract is already drifting). Several `WarData` fields are silently dropped on save/restore, peace‑proposal authorization has logic bugs, the persistence file is best‑effort overwrite‑then‑delete with no atomic guarantees, and the war‑exhaustion tax modifier can stack with reparations beyond the documented cap on edge cases. None are immediately game‑breaking, but compounded after a crash they will produce wrong state silently.

---

## Critical

### C1. `WarData.originalHostilePerms` / `originalHostilePermsForAttacker` are never persisted
`WarData.java:41-42` declares two public mutable maps that store the colony's pre‑war Hostile rank permission snapshot. They are referenced from the WarData object but the persistence layer (`WarSystem.java:3943-4002` `saveActiveWars` / `WarSaveEntry` at 3909-3937) never serializes them, and the restoration constructor (`WarData.java:98-137`) never accepts them. After a server crash mid‑war:
- The war resumes (`loadAndResumeActiveWars`) and `setWarInteractionPermissions(colony, true)` is re‑applied at `WarSystem.java:4155-4158`, which calls `PermissionSnapshot.snapshotBefore(colony)` — overwriting the *war‑modified* state as the new "pre‑war baseline."
- When the war then ends, `PermissionSnapshot.restoreIfNoConflict` at `WarSystem.java:1295-1298` restores to the wrong snapshot — leaving the war’s hostile permissions enabled permanently.
- Workaround currently relies entirely on `PermissionSnapshot` persisting separately to disk (`PermissionSnapshot.loadFromFile()` at `MineColonyTax.java:145`). This works *only* if the snapshot file is consistent with active_wars.json — there is no transactional link between them.

### C2. `WarData.activeProposal` (PeaceProposal) is never persisted
`WarData.java:46` holds the live `PeaceProposal`. If a proposal is in flight when the server stops:
- `saveActiveWars` does not write it (no field in `WarSaveEntry`).
- On restore the proposal is silently dropped.
- A player who proposed peace and was waiting for an accept will see the proposal vanish with no notification — but the war restoration message at `WarSystem.java:4221-4227` makes them think the war just resumed normally.
- Easy attack vector: declare war, propose surrender, force a server restart (or wait for one) — the surrender proposal evaporates while the war continues.

### C3. Restoration constructor parameter count drift
CLAUDE.md (project contract): "When modifying WarData, also update the 27‑parameter restoration constructor". The constructor at `WarData.java:98-108` now takes **28 parameters** (warID, attacker, defender, attackerTeamID, defenderTeamID, warStartTime, joinPhaseEndTime, bossEvent, colony, attackerColony, status, accepted, initialAttackerGuards, remainingAttackerGuards, initialDefenderGuards, remainingDefenderGuards, initialAttackerTotalLives, initialDefenderTotalLives, attackerLivesData, defenderLivesData, defenderGuardIDsData, attackerGuardIDsData, attackerAlliesData, defenderAlliesData, spectatorsData, lastLifeData, penaltyReport, stalemateTriggered). The CLAUDE.md guidance is already out of date — and meanwhile the following live `WarData` fields are still **not** in the constructor:
- `originalHostilePerms`, `originalHostilePermsForAttacker` (C1)
- `activeProposal` (C2)
- `acceptedAllies`, `declinedAllies` (`WarData.java:38-39`) — silently dropped; in‑flight ally invites are lost
- `offlineOutpostWar` (`WarData.java:48`) — `setOfflineOutpostWar` is called by features but the boolean is not persisted; offline outpost wars get re‑classified as normal wars after restart
- `alliesBossEvent` — re‑created on restore only for `JOINING` status (`WarSystem.java:4187-4192`); a war saved during JOINING with no alliesBossEvent is fine, but an active INWAR war that mid‑game had one is silently lost (low impact, cosmetic)
- `countdownTaskId`, `warChestDrainTaskId` — transient by design (re‑created in restore) — OK
- `totalGuards`, `remainingGuards` (`WarData.java:44-45`) — declared but never used by the restoration path; live use should be audited (see L4)

The audit recommends adding either a `@PersistenceContract` checklist or a unit test that asserts every non‑transient WarData field round‑trips through `WarSaveEntry`.

---

## High

### H1. `active_wars.json` write/delete is non‑atomic; partial state on crash
`WarSystem.java:3994-3997` writes directly via `FileWriter` (no temp‑file + rename), and `WarSystem.java:4042` calls `Files.deleteIfExists(path)` **after** the in‑memory restore loop has begun but BEFORE the wars are fully wired up (boss bars, scheduled tasks, applyResistance, etc. all happen after the delete in `resumeWarFromSave`). Failure modes:
- Power loss mid‑save: the file is left half‑written; on next start `WAR_GSON.fromJson` throws (caught at `4044-4046` and logged) — the catch swallows the error and the file is **not** deleted, so it will be retried next start with the same broken contents indefinitely.
- Save succeeds but JVM dies before `WarSystem.saveActiveWars` is even reached (Hard crash): nothing saved, wars silently disappear. No recovery hook.
- Load partially succeeds (one war restored, second war throws): the loop continues, then `Files.deleteIfExists(path)` at line 4042 deletes the file — wiping the unrestored entries. Subsequent restart can never recover them.
- Recommendation: write to `active_wars.json.tmp` then `Files.move(tmp, path, ATOMIC_MOVE, REPLACE_EXISTING)`. Only delete the source file after **all** entries have been processed successfully, or move it aside as `active_wars.json.loaded-<timestamp>` to allow forensic recovery.

### H2. Race: simultaneous peace accept by both sides
`PeaceProposalManager.acceptPeace` (`PeaceProposalManager.java:122-150`) and `declinePeace` (152-186) read `war.getActiveProposal()` and then perform multi‑step state mutation (`finalizePeaceProposal` → `WarSystem.endWar`) with no synchronization. Although TickScheduler is single‑threaded, command execution is driven by the Forge command dispatcher on the server thread *and* the network packet handler — `accept` is reachable directly from a `RUN_COMMAND` click event in chat. Two players clicking accept (or accept vs decline) in the same tick:
- Both will see `proposal != null` and `isExpired() == false`.
- Both will pass `isAuthorizedToRespondToPeace`.
- Both will call `finalizePeaceProposal(war, true, player)` and both will call `WarSystem.endWar(war.getColony())` — leading to double rank demotion, double history records, and (in REPARATIONS) double money transfer if `payReparationsProportionally` doesn’t early‑exit.
- Fix: clear `war.setActiveProposal(null)` *before* the work block, not at end.

### H3. Peace proposer authorization is too loose — proposer can be a Hostile rank
`PeaceProposalManager.handleSuePeaceProposal` (line 43) only checks that the player is in `getAttackerLives()` or `getDefenderLives()` (line 108‑115). It does **not** check the player’s rank or authority. Anyone added to lives — including a `Friend`‑rank player who joined via prompt and has no decision authority — can propose `SURRENDER` on behalf of their whole side. The responder check at line 188‑219 does require officer+, but the proposer check is asymmetric: a low‑rank player can propose surrender, a defender officer can accept it, and the entire colony is lost.

### H4. Peace responder authority check has a `NullPointerException` foot‑gun
`PeaceProposalManager.java:209-210` and 215-216 call `colony.getPermissions().getRank(responder).isHostile()` and `.getId()` without null checks. `Permissions.getRank(Player)` can return `null` for a player who is not registered in the colony (e.g. a war participant from an allied FTB team but not a member of the colony). In that case `acceptPeace` throws NPE and the command silently fails with "Internal command error" — proposal stays open, opponent never gets a response.

### H5. War duration uses `System.currentTimeMillis()` — wall clock skew kills wars
`updateBossBar` (`WarSystem.java:529`), `startWarCountdown` (`WarSystem.java:2509`), and `handleTimeExpiry` test all rely on `System.currentTimeMillis() - war.warStartTime`. If the server clock is set backwards (NTP adjustment, manual change, container time shift), all live wars instantly appear "expired" or "not yet started" depending on direction. Long restoration of `INWAR` wars also computes `now - warStartTime` against persisted `warStartTime`; if the persisted value is in the future (e.g. someone time‑traveled the server), `remaining` is huge and the war runs indefinitely. No monotonic time fallback; no sanity check against `warDurationMs * 2`.

### H6. `WarData.warStartTime` is overloaded — also stores join‑phase start
In the constructor at `WarData.java:69`: `this.warStartTime = joinPhaseStart`. The same field is then reassigned to the real war start at `finalizeWarStart` (`WarSystem.java:610`) and at `startJoinPhase`’s scheduled callback (`WarSystem.java:2371`). Until `INWAR`, every check that uses `warStartTime` as "time since war began" (e.g. `isWarTimeExpired()` at `WarData.java:178`) is comparing against join‑phase start. `isWarTimeExpired()` is currently only called transitively from a tick handler that bails on `JOINING`, but adding any other caller would silently break. The field needs splitting (`joinStartTime` + `warStartTime`) or `isWarTimeExpired` needs to assert `status == INWAR`.

---

## Medium

### M1. `extortionImmunity` (`WarSystem.java:61`) is in‑memory only
The map is checked at `:3095` and `:3807-3828` when refusing repeat war declarations. It is wiped on every server restart, letting players bypass extortion cooldowns by waiting for a server reboot. No persistence layer — write a tiny `extortion_immunity.json` alongside `active_wars.json`.

### M2. `WarSystem.endWar` triple‑reads `ACTIVE_WARS.get/remove`
At `:1243`, `:1276`, `:1292`, `:1296`, etc. the method calls `ACTIVE_WARS.get(colony.getID())` at line 1243, then mutates state, then does `ACTIVE_WARS.remove(colony.getID())` at line 1292. Other callers of `endWar` on the same colony in the same tick (it happens — see H2; also `checkForVictory` → `endWar` and `handleTimeExpiry` → `endWar` can both fire from the same tick’s death event) will read a stale, partially‑torn‑down WarData and double the cleanup work. Pattern should be: `WarData warData = ACTIVE_WARS.remove(colony.getID()); if (warData == null) return;` as the very first operation, making the function idempotent.

### M3. War economy tax modifier — combined cap can be exceeded indirectly
`WarExhaustionManager.getTaxMultiplier` (`:286-318`) computes:
```
multiplier = atWar ? (1 - warTaxReductionPct) : (in recovery ? curve : 1.0)
if (hasReparations) multiplier *= (1 - reparationsPct)
multiplier = max(1 - MAX_COMBINED_WAR_PENALTY_PERCENT, multiplier)
return max(0.1, multiplier)
```
But the *floor* is `Math.max(minMultiplier, multiplier)` *then* `Math.max(0.1, ...)`. If `MAX_COMBINED_WAR_PENALTY_PERCENT > 0.9`, the cap is silently lifted by the hard `0.1` minimum (because `1 - 0.9 = 0.1`); if the admin sets it to `0.95`, the cap is `0.05` but the hard floor wins at `0.1`, so the cap is silently weaker than configured. Should be one `Math.max` with both clamps documented.

Also: the recovery curve at `:324-342` is computed on every `getTaxMultiplier()` call, but `isInRecovery` at line 135‑148 calls `saveData()` (via the cleanup at 143). Each tax cycle calls `getTaxMultiplier()` for every colony — this can produce hundreds of `saveData()` JSON writes per tick on a large server with many colonies just transitioned out of war. The "if recovery expired, remove + save" pattern needs to debounce.

### M4. `PlayerWarData` save format is brittle to version changes
`PlayerWarData.deserializeNBT` at `:55-64` uses `nbt.getInt("warsWon")` etc. These return `0` for missing keys (Forge default), so deletions silently zero stats. There is no schema version tag, no migration path, and the NBT lives inside `player.getPersistentData().ForgeData.minecolonytax_war_data` (`PlayerWarDataCapability.java:69-87`). Renaming any of the 7 fields will lose all historical stats with no recovery. Add a `dataVersion` int and a migration switch.

### M5. PlayerWarData stored both in capability AND in persistent NBT — two sources of truth
`PlayerWarDataCapability` writes the same `PlayerWarData` blob to:
- Capability instance (in‑memory, per‑player) — read by `WarStatsCommand`, `PlayerWarDataManager`
- `player.getPersistentData().ForgeData.<modid>_war_data` — written on every increment via `markDirty` (`PlayerWarDataManager.java:80-87`), and re‑read on multiple events (`AttachCapabilities`, `LoadFromFile`, `PlayerLoggedIn` — `:48-141`)

Three different load paths (`onPlayerLoad`, `loadDataFromPersistent`, `onPlayerLoggedIn`) all deserialize from the same NBT into the same capability. Last‑writer‑wins between AttachCapabilities and PlayerLoggedIn is undefined and depends on Forge event ordering. Should consolidate to capability‑only and only sync NBT once on player save.

### M6. PlayerWarData captures *all* players on each kill, but the capability is per‑Entity
`PlayerWarDataCapability.attachCapability` at `:49` attaches to *any* `Player`, including the client‑side dummy player in single‑player. The provider is registered for every Player entity in every dimension, but `LazyOptional.invalidate()` is registered as a listener (`:53`). This is correct in single‑player, but in dev‑integrated server the client and server attach separate capabilities to the same UUID; calls from the server thread side will hit the server capability correctly, but a misplaced client‑side call would silently no‑op. Add an `instanceof ServerPlayer` guard inside `attachCapability` like other capabilities in the same package do.

### M7. `checkForVictory` does not lock `WarData` lives maps
`WarSystem.java:668-696` reads attackerLives and defenderLives, then calls `endWar`, but lives maps are `ConcurrentHashMap`s being mutated by handlers like `handleGuardKilled` (`:1781-1794`) on the same tick. The reads at 669-670 use `.stream().allMatch(...)` which is *weakly consistent* — two near‑simultaneous deaths can make `allDefendersDead == true` for both calls of `checkForVictory`, leading to two simultaneous `applyWarEconomyTransfers` (`:735`) doubling the economy hit. Pure CPU‑level race even within the single tick thread is unlikely, but the same call from two separate tick handlers (death + countdown) is. Mark `checkForVictory` idempotent the same way as `endWar` (M2).

### M8. `WarEconomyHandler.payReparationsProportionally` division‑by‑zero
`WarEconomyHandler.java:206`: `(long) (balance * ((double) demandedAmount / getTeamTotalBalance(losingTeamID)))`. If between line 195 (where `losingTeam` is fetched) and line 206 every team member spends all their currency to zero, `getTeamTotalBalance` returns 0 and division yields `NaN` cast to `(long)` = `0` — reparations succeed for 0 coins, war ends, defender gets nothing. Caller at `PeaceProposalManager.java:270` checks `teamTotal < demandedAmount` once at acceptance time but does not re‑validate after the per‑member loop. Add `if (totalBalance == 0) return false;`.

### M9. `loadAndResumeActiveWars` ignores per‑war broadcast spam
`WarSystem.java:4221-4227` broadcasts "War Restored" *per war* to the entire server. On a multi‑war reboot, every player sees N notifications even if they’re not involved. Should batch into a single summary at the end of the load loop.

### M10. Peace `REPARATIONS` flow assumes `attackerColony` is non‑null
`PeaceProposalManager.java:263-264`: `winningPlayerId = war.getAttackerColony().getPermissions().getOwner();` when the defender proposes reparations. If the war was started without an attackerColony (legacy code path; CLAUDE.md notes wars can persist without `attackerColony`), this NPEs and the proposer’s currency is *already deducted* by the time we get here — money disappears. Need a null check before deducting.

---

## Low

### L1. Persistence file uses `Files.createDirectories(path.getParent())` but does not check `parent != null`
`WarSystem.java:3946`. If `WAR_STORAGE_FILE` were ever changed to a bare filename (no parent directory), this NPEs. Mostly hypothetical — but defensive code should pass `path.getParent()` through `Optional.ofNullable`.

### L2. `WarSaveEntry` field `defenderTeamID.toString()` NPE when teamID is null
`WarSystem.java:3957-3958`. If FTB is not installed but defender is a solo player, `defenderTeamID` is set to the colony owner UUID in `initiateWar` (`:118-119`) — never null in practice. But the contract is implicit; a future code path that leaves it null will NPE the save. Add explicit null guards or use `Objects.toString(uuid, "null")`.

### L3. `parseUUIDList` silently swallows malformed UUIDs
`WarSystem.java:4232-4243` catches `IllegalArgumentException` and skips. After a corrupted save, the war restores with missing members — no log, no alert. Should `LOGGER.warn` on each skip so the operator at least sees something is wrong.

### L4. `WarData.totalGuards` and `remainingGuards` are dead fields
`WarData.java:44-45` declare them; only `remainingAttackerGuards`/`remainingDefenderGuards` are actually used. Dead public mutable state is a footgun (external code could set them). Either remove or document as deprecated.

### L5. Reading config inside hot path
`WarData.isWarTimeExpired()` (`:178`) calls `TaxConfig.WAR_DURATION_MINUTES.get()` every check; same in `updateBossBar`, `startWarCountdown`, `scheduleTimerWarnings`. The Forge config implementation does a hashmap lookup per `.get()` — fine, but worth caching `warDurationMs` in `WarData` at construction. (Already done for `warDurationSeconds` in `startWarCountdown:2489`.)

### L6. `WarStatsCommand` triggers full PlayerList save
`WarStatsCommand.java:61` calls `player.getServer().getPlayerList().saveAll()` after every `/warstats` invocation. This writes every online player’s data to disk just to flush one stat panel — a non‑trivial cost on a busy server. The capability is already saved on logout / scheduled save; the explicit save is redundant.

### L7. `WarVictoryEvent` is a plain Event with no `@Cancelable` and no `Result` — fine but undocumented
`event/WarVictoryEvent.java:6`. If external mods are intended to react (and there is no documentation either way), document the contract.

### L8. `PlayerWarDataCapability.onPlayerChangeDimension` is a no‑op with a comment
`:118-122`. The comment says data lives in player persistent data so no migration needed. Correct — but the empty event handler is still subscribed (registers on bus, runs every dimension change). Remove the subscription entirely.

### L9. `MineColonyTax.onServerStarting` order: `WarSystem.loadAndResumeActiveWars()` runs at line 204
This happens **before** `OccupationManager.initialize` at line 251. If a war ended in `ENABLE_COLONY_TRANSFER` mode and started an occupation just before crash, the occupation is loaded *after* the war is restored. `OccupationManager.startOccupation` is called from `endWar`/`checkForVictory` only at runtime, not on load — but `loadAndResumeActiveWars` itself never re‑applies occupation, so an occupation that was triggered exactly as the server crashed (after `endWar` removed the war but before OccupationManager.saveData fired) is lost. Low because the window is small, but the lifecycle order is unsafe.

### L10. `saveActiveWars` writes file even when `ACTIVE_WARS` is empty
`WarSystem.java:3998` always logs "Saved N active wars". When N=0 we still create the directory, write an empty `{"wars":[]}` to disk, and log it. Harmless but adds noise; check `ACTIVE_WARS.isEmpty()` and skip both the write and the log.

---

## Files Audited

- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java` (4244 lines, full read)
- `src/main/java/net/machiavelli/minecolonytax/data/WarData.java`
- `src/main/java/net/machiavelli/minecolonytax/data/PlayerWarData.java`
- `src/main/java/net/machiavelli/minecolonytax/data/PlayerWarDataManager.java`
- `src/main/java/net/machiavelli/minecolonytax/peace/PeaceProposal.java`
- `src/main/java/net/machiavelli/minecolonytax/peace/PeaceProposalManager.java`
- `src/main/java/net/machiavelli/minecolonytax/event/WarEconomyHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/event/WarVictoryEvent.java`
- `src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java` (skim for event-bus subscription points only)
- `src/main/java/net/machiavelli/minecolonytax/commands/WarStatsCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/capability/PlayerWarDataCapability.java`
- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java` (lifecycle hooks `onServerStarting`/`onServerStopping`)
- `src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java`
- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` (for concurrency model)
- Persistence file referenced: `config/warntax/active_wars.json` (paths verified at `WarSystem.java:3907`)
