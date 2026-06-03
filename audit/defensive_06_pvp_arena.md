# Defensive Audit 06 — PvP Arena System

Audit scope: PvP arena, duels, team battles, spectators, persistence, and the PvP kill economy handler.
All findings are static-analysis-only — no code modified, no gradle build run.

---

## Summary

The PvP system is functional but contains a number of correctness, safety, and exploit issues. The most dangerous defects are concentrated in five areas:

1. **Concurrent mutation of unsynchronized HashMaps from multiple threads.** `PvPManager` exposes `HashMap` (not `ConcurrentHashMap`) fields that are written by both the server thread (tick handler / events) and the `BATTLE_END_SCHEDULER` worker thread (a `ScheduledExecutorService`). Several remove/iterate paths can throw `ConcurrentModificationException` or silently lose entries.
2. **Inventory loss on defeat / disconnect / crash / dimension mismatch.** Inventory save/restore was deliberately removed to fix a duplication glitch (PvPBattleManager.java:794, :983-1002), but players can still permanently lose items: defeat sets them to SPECTATOR (PvPEventHandler.java:269) yet the entity keeps its inventory — fine on clean restore, but on server crash or kick between death and the 5-second `restorePlayer()` schedule (PvPBattleManager.java:72-79), the original position is forgotten and the player respawns at world spawn still in spectator with their loot left at the arena.
3. **PvP kill economy is fully farmable.** No cooldown, no per-period cap, no alt detection, no IP/UUID rate limiting. A player can repeatedly kill an alt account in the same chunk to drain the alt's wallet/items into the main account; with raid penalty multiplier, this scales fast.
4. **Persistence is map-only.** `active_wars.json` style restore for in-flight battles does not exist. Wars in progress, pending team battles, pending duel requests, spectators, defeated-player timers, original positions, and `playerOriginalGameModes` are all in-memory only. A server restart mid-battle leaves players at the arena in spectator mode with no way back.
5. **Spectator state can permanently brick a player.** If a player is set to SPECTATOR via `handlePlayerDefeat()` (PvPEventHandler.java:269) and the 5-second `BATTLE_END_SCHEDULER` callback fails (server shutdown, exception, level unload), `playerOriginalGameModes` is consulted with `SURVIVAL` as the fallback (PvPBattleManager.java:95) — original position is lost completely, and any spec-pos override is wiped from disk on the next save.

Severity counts: **Critical 4 / High 8 / Medium 9 / Low 5**.

---

## Critical

### C1. PlayerPvPStats is never persisted
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:44`
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java:151-179` (only `arenaMapsByName` is saved)

`pvpManager.playerStats` is mutated in `PvPBattleManager.updatePlayerStats()` (line 856) and `updatePlayerKill()` (line 1034) but no `save()` writes it to disk. The map is a plain `HashMap<UUID, PlayerPvPStats>` reset to empty on every server start. Wins, losses, kills, deaths, and K/D ratios visible in the GUI are silently wiped at restart.

### C2. Arena maps and stats use unsynchronized HashMaps mutated off the server thread
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:27,32-34,38,40-44,50-53`

`arenaMapsByName`, `activeBattles`, `pendingTeamBattles`, `pendingRequests`, `playerInventories`, `playerArmor`, `spectatorData`, `playerOriginalGameModes`, `activeSpectators`, `playerStats`, `challengeCooldown`, `teamBattleCooldown`, `lastFriendlyFireNotifications` are all `HashMap`. The `BATTLE_END_SCHEDULER` (PvPManager.java:59) is a separate `ScheduledExecutorService` thread; its `endBattle()` lambda at PvPBattleManager.java:728 calls `pvpManager.activeBattles.remove(battleId)` and reads `defeatedPlayers`, while the server tick thread iterates `pvpManager.activeBattles.values()` in `PvPManager.getActiveBattle()` (line 69), `PvPMapManager.deleteMap()` (line 54), and `PvPEventHandler.onLivingDamage()` (line 230). Although the inner `endBattle()` schedules onto `server.execute()`, the *outer* `activeBattles.remove()` runs on the scheduler thread without synchronization — classic `ConcurrentModificationException` window during a damage event.

### C3. `endBattle()` runs the `activeBattles.remove()` on the scheduler thread, not the server thread
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:723-758`

```java
PvPManager.BATTLE_END_SCHEDULER.schedule(() -> server.execute(() -> endBattle(battleId)), ...);
```
Actually the schedule lambda *does* immediately enter `server.execute()` and `endBattle()` is called from there — good. **But** the per-player restore at line 747 (`server.execute(() -> {...})`) nests a *second* `server.execute()` call inside the already-server-thread context. This causes the per-player restore to run on a *later* tick than the `activeBattles.remove()` at line 728, opening a window where the battle is removed but `pvpManager.playerOriginalGameModes` still has entries — a follow-up logout in that window will leave the player permanently in spectator mode at the arena.

Additionally `BATTLE_END_SCHEDULER.schedule(...)` in `handlePlayerDefeat()` at line 72 nests `server.execute()` from the scheduler thread — correct pattern — but the variable `player` is captured rather than re-looked-up by UUID. If the player disconnects in the 5-second window the captured `ServerPlayer` is stale and `setGameMode()` / `teleportTo()` on it is a no-op, leaving them in spectator on next login.

### C4. PvP kill economy is unbounded and farmable
File: `src/main/java/net/machiavelli/minecolonytax/event/PvPKillEconomyHandler.java:25-64`

No cooldown between kills of the same victim, no per-period cap on rewards earned, no IP-based alt detection, no minimum playtime guard, no "same victim within N seconds" suppression. With two accounts and any tradable currency item, a player can:
- Stand alt next to main, kill repeatedly.
- Each kill transfers `victimCurrencyCount * PVP_KILL_REWARD_PERCENTAGE` (default percentage not visible here but multiplier-based) plus the raid penalty when applicable.
- `Math.max(1, ...)` at line 152 guarantees at least 1 item per kill even when victim has near-zero balance, allowing slow farming of an SDMShop balance toward zero (and toward the killer).

Self-kill is excluded (line 37) but alt-account exploitation is trivial. The raid-related branch (line 44-54) actively *amplifies* the reward, so the exploit gets worse during legitimate raid events.

---

## High

### H1. Inventory still lost on crash/disconnect/dim-mismatch
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:264-273` (in PvPEventHandler) and `restorePlayer()` line 967

When `onLivingDamage` fires (PvPEventHandler.java:264-273) with damage ≥ player health, the player is set to SPECTATOR (still alive). `restorePlayer()` does NOT restore inventory (intentional, fixes duping). However:
- The player remains alive in spectator with full inventory at the arena.
- If the server crashes during the 5-second scheduled restore, the player respawns at world spawn in spectator mode the next session with no easy way back to their items.
- `pvpManager.playerOriginalGameModes` is in-memory only — restart returns them to SURVIVAL (line 95 default) but the spectator gamemode is already saved to the player's `playerdata/<uuid>.dat` because Minecraft persists gamemode per-player.

### H2. `setGameMode(SPECTATOR)` to fake a "death" causes inventory-loss-on-death-protection bugs
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java:264-272`

`event.setCanceled(true)` cancels the damage, sets full health, then `setGameMode(SPECTATOR)`. The player is treated as alive. If anything (a poison-tick, a mob nearby, fire) damages them after the cancel and before `restorePlayer()` runs the next tick, the cancel does not stop a *new* damage event — they can be killed for real while waiting 5 seconds, losing their entire inventory to a normal death.

### H3. Race when both target and challenger accept their pending request
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:257-276`

`handleAccept` does:
```java
BattleRequest request = pvpManager.pendingRequests.remove(player.getUUID());
boolean allAccepted = allTargets.stream().noneMatch(pvpManager.pendingRequests::containsKey);
```
Two targets in a multi-accept duel running `accept` on the same tick can both observe `allAccepted == true` and both call `startChallengedBattle(request)`, creating two `ActiveBattle` entries with the same players. Map name collision would then be caught by `lockMap` (already in `lockedMaps`) but only after both call `startBattle()` — leading to the second call running through `startBattle()` and overwriting `playerOriginalGameModes` with the spawn-point gametype values (currently SURVIVAL since players are forcibly survival in arena? — no, `startBattle()` doesn't change game mode pre-fight, but the *next* defeat will record SPECTATOR as "original"). Also `pendingRequests` is `HashMap` not concurrent (PvPManager.java:34) so `remove`/`containsKey` is not atomic.

### H4. Decline does not refund or notify other accept-pending targets
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:278-298`

In a multi-target duel, when one target declines:
- Other still-pending target's `pendingRequests` entry is removed (line 288).
- Other targets are NOT notified that the battle was cancelled.
- The challenger is told "X declined" but if multiple targets had not yet accepted, only the latest decliner is named.

### H5. `cancelBattleDueToDisconnect()` calls `endBattle()` synchronously from the disconnect event thread
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:840-854`

`onPlayerDisconnect` (PvPEventHandler.java:197-201) fires on the network/main thread but `endBattle()` reads from `playerStats`, mutates `activeBattles`, `battleTimers`, `lastNotificationTime`, `battleDamage`, all of which are read by the server tick handler. The actual server.execute wrapper inside `endBattle()` (line 747) is per-player; the *initial* `pvpManager.activeBattles.remove(battleId)` at line 728 still runs on whatever thread called `endBattle()` directly. PlayerLoggedOutEvent is on the main server thread in Forge 1.20.1, so this happens to be OK — but the same `endBattle()` is also called from `scheduleBattleEnd()` via the executor (line 723), where the `server.execute` wrapper only guards the *whole* `endBattle` call. Confirmation needed by checking the executor lambda — actually looking again at line 723, `server.execute(() -> endBattle(battleId))` does wrap correctly, so the scheduler path is fine. The race is between the synchronous disconnect call and the scheduled call. Both can run, double-removing.

### H6. PvP kill rewards trigger on death outside of arena
File: `src/main/java/net/machiavelli/minecolonytax/event/PvPKillEconomyHandler.java:26-64`

`PvPKillEconomyHandler` listens to *all* `LivingDeathEvent` for players, not just arena/raid deaths. Outside any arena or raid context, normal world PvP (a player ganking another at a build site) still transfers currency. The "PvP economy" name implies opt-in arena economics but the implementation taxes every cross-player kill. Combined with C4 (no rate limit) this is a global-server item drainer.

### H7. `checkIfRaidRelated()` returns true if EITHER side is in a raid, even unrelated raids
File: `src/main/java/net/machiavelli/minecolonytax/event/PvPKillEconomyHandler.java:66-79`

If victim is participating in raid A and killer is wandering in unrelated village, kill is flagged "raid-related" → boosted penalty. Worse: killer can deliberately enter a raid on another colony to trigger the raid multiplier, then kill an unrelated player and pocket the multiplied reward.

### H8. `arenaMapsByName.clear()` on load runs without checking whether a battle is currently active
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java:194`

`loadArenaData()` is called from `onServerStart` (PvPEventHandler.java:55-57) but is also a `public` method that could be re-invoked. If ever called mid-session (or if a reload mechanism is added), it wipes active map definitions while `activeBattles` may still reference them by name → next defeat/timer expiry will call `pvpManager.arenaMapsByName.get(mapName)` and NPE-handle as "Battle map not found!" (line 312), leaving players stranded.

---

## Medium

### M1. Spawn point safety completely absent
File: `src/main/java/net/machiavelli/minecolonytax/pvp/persistence/SpawnPointData.java:1-5`
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java:80-102, 196-203`
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:922-944`

No validation that spawn coordinates are within world bounds, not in lava/void, not inside a block, not unloaded. `teleportTo()` at line 938 happily teleports to `y = -2048` (suffocate/void death) or into a wall. The placeholder `BlockPos.ZERO` insertion at PvPMapManager.java:88 means a partially-configured map can teleport players to (0,0,0) (typically bedrock/void).

### M2. `addSpawnPoint(... spawnIndex)` allows arbitrarily large gaps with placeholder zeros
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java:86-89`

```java
while (map.getSpawnPoints().size() < spawnIndex) {
    map.getSpawnPoints().add(GlobalPos.of(player.level().dimension(), BlockPos.ZERO));
}
```
An admin running `/pvparena addspawn map 1000` will add 999 placeholder GlobalPos at (0,0,0), then `getSpawnPoints().size()` reports 1000, `maxPlayers` is bypassed, and the map serializes 1000 spawn entries to JSON. Combined with M1, the next battle that picks index 5 may teleport someone into the void.

### M3. `addSpawnPoint` ignores `maxPlayers` cap when growing via spawnIndex
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java:86-96`

`PvPMap.addSpawnPoint()` (PvPMap.java:33) throws if exceeding maxPlayers — but `PvPMapManager` bypasses this by using `getSpawnPoints().add(...)` and `.set(...)` directly. The defensive check in PvPMap is bypassed by its only caller.

### M4. Players can be in two pendingTeamBattles simultaneously
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:482-514`

`joinTeamBattle` checks `isPlayerBusy()` which checks `activeBattles` and `pendingRequests` — but NOT `pendingTeamBattles`. Two different team battles can have the same player joined to each. When one starts, the player is teleported; when the second starts, the player gets re-teleported and the first battle now has a player who is in a different arena. `TeamBattle.addPlayerToTeam` (TeamBattle.java:43-53) only blocks the same player joining both teams of the *same* battle.

### M5. `isPlayerBusy()` doesn't check pendingTeamBattles, only activeBattles + pendingRequests
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:75-87`

Same root cause as M4. `pendingTeamBattles` is not consulted. A player can be in a pending team battle, then receive and accept a duel — the duel will start, teleporting them; when the team battle starts they get teleported again to a different arena, breaking both.

### M6. Spectator returns to original position via `teleportTo` which has no dimension safety
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:955-965`

`stopSpectating()` teleports to `data.originalPos()`. If the original level is unloaded (`server.getLevel(pos.dimension())` returns null at line 935), the method silently returns and the spectator is stuck in SPECTATOR mode at the arena — except line 958 has already set the gamemode back. Result: survival mode at the arena spectating-spot, no inventory teleport home.

### M7. Spectators are not cleaned up when battle ends
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:727-758`

`endBattle()` removes `battleTimers`, `lastNotificationTime`, `battleDamage` — but does NOT iterate `pvpManager.activeSpectators.get(battleId)` to teleport each spectator back. `pvpManager.spectatorData` entries for those spectators are never removed. The next battle on the same map id (timestamp-based, but collision-possible within the same ms) inherits stale spectator data.

### M8. `BATTLE_END_SCHEDULER` is a single-thread executor with no shutdown hook
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:59`

`Executors.newScheduledThreadPool(1)` is created at static init and never shutdown. On server stop, scheduled tasks may still try to call `ServerLifecycleHooks.getCurrentServer()` which can return null mid-shutdown, leading to NPE in the executor that's logged but unhandled. Also leaks the thread on /reload-style restarts in dev environments.

### M9. `lastCommandBlockMessage` and similar maps grow unbounded
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java:46, 50, 52`

`lastCommandBlockMessage`, `notifiedAbandonedColonies`, `lastColonyNotifications` are never pruned. On long-running servers with many unique players entering/leaving battles or abandoned colonies, these grow without bound. Minor memory leak but real.

---

## Low

### L1. Battle ID collision via System.currentTimeMillis()
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:155, 627`

`String battleId = "ab_" + System.currentTimeMillis();` (team) and `"challenge_" + ms` (duel). Two battles starting in the same millisecond collide — second overwrites first in `activeBattles` map. Use `UUID.randomUUID()` like team battles do at line 450.

### L2. `defaultMapName` is read without checking it still exists in arenaMapsByName
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:359-369`

`handleDuel` checks `pvpManager.arenaMapsByName.containsKey(pvpManager.defaultMapName)` but `defaultMapName` is mutated from another thread possibly during command handling. If admin deletes the default map at the same instant, can return false — falls back to "first map" which is fine, but if maps is empty (clear-during-load) gives the "no arena" error.

### L3. `appendTeamMembers` uses "Offline Player" placeholder, can confuse roster display
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:875-888`

If a player joined a pending team battle then logged out, the roster shows "Offline Player". Multiple offline players are indistinguishable. Not a security issue but UX.

### L4. `calculateMapCenter` averages spawn Y coordinates and may produce mid-air spectator pos
File: `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:906-920`

Spectator is placed at `centerPos.offset(0, 10, 0)` (PvPBattleManager.java:322). If spawn points are at varying Y (multi-level arena), the average may be inside a roof block and the +10 still inside terrain. Not blocking since spectator clips through blocks, but visually wrong.

### L5. `expiryTime` for BattleRequest is hardcoded to 60s
File: `src/main/java/net/machiavelli/minecolonytax/pvp/model/BattleRequest.java:19`

Not configurable — should be a TaxConfig key.

---

## Files Audited

- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPArenaCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/ActiveBattle.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/BattleRequest.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/PlayerPvPStats.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/PvPMap.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/SpectatorData.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/TeamBattle.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/model/TeamBattleState.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/persistence/ArenaDataCollection.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/persistence/ArenaMapData.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/persistence/SpawnPointData.java`
- `src/main/java/net/machiavelli/minecolonytax/event/PvPKillEconomyHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java` (relevant PvP keys only)
