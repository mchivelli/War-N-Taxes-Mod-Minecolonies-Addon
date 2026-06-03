# Defensive Audit 04 — Occupation, Vassalization, Abandonment & Claiming

Scope: `OccupationManager`, `VassalManager` + vassal packets, `ColonyAbandonmentManager`, `ColonyClaimingRaidManager`, `OfficerColonyVisitTracker`, `ColonyActivityTracker`, `ColonyActivityCommand`.
Method: static read-only analysis (Read/Grep/Glob). No build run.

---

## Summary

The four systems audited are largely well-structured: they use `ConcurrentHashMap` for state, persist via Gson + `AsyncSaveExecutor`, and all snapshot collections before iterating. Most building access correctly avoids the cross-version-unsafe `getBuildingManager()` / `getServerBuildingManager()` calls (only two unrelated files use them, both already going through the `ColonyBuildingUtil` shim or guarded). However, several **High-severity** correctness bugs and one **Critical** dead command exist:

- A user-facing message tells the occupier to run `/wnt collectoccupation <id>`, but that subcommand is never registered, and `OccupationManager.collectOccupationTax(...)` has zero callers — the entire manual-collection path is dead code.
- `VassalManager.requestVassalization` has a TOCTOU race with `acceptProposal` / a concurrent second `requestVassalization` (containsKey-then-put is not atomic), allowing duplicate proposals or a stale-overwrite.
- `ClaimVassalTributePacket` is fully replayable: any number of client-side packets drains the vassal's pending tribute, with no per-tick / per-claim rate limit and no idempotency token. The packet pretends to "give currency to player" but for the non-SDMShop path actually only sends a chat message — the currency item is never given.
- `VassalManager.endVassalizationWithNotification` (the EndVassalizationPacket path) is callable by **any colony manager of the vassal**, including potentially the overlord if they were ever added — but `revokeRelation` (the command path) allows either side. The two paths use slightly different authorization checks, creating an inconsistent surface.
- `OccupationManager.findColonyById` ignores `IColonyManager.getAllColonies()` and re-iterates all worlds — wasteful but correct. `VassalManager.getColonyById` and `ColonyClaimingRaidManager.getColonyById` use `getAllColonies()`. Inconsistent style across managers; should converge on `getAllColonies()`.
- `VassalManager.getPrimaryColonyOfPlayer` will NPE if any colony has a null owner (it calls `getPermissions().getOwner().equals(playerId)` without null guard) — and abandoned colonies routinely have null owners in this codebase, since `ColonyAbandonmentManager` explicitly fixes them post-hoc.

---

## Critical

### C1. `/wnt collectoccupation` command is referenced in chat but never registered
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:230` — startOccupation sends the occupier the message `"You can collect taxes with /wnt collectoccupation " + colonyId`.
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:293` — `collectOccupationTax(int, ServerPlayer)` exists.
- `Grep collectOccupationTax src/main/java/net/machiavelli/minecolonytax` returns only the definition; no command, packet, or other call site invokes it.
- `Grep "literal\(\"collectoccupation\"\)"` returns no matches in `commands/`.
- Result: Players told to run a command that does not exist. The manual-collection path is dead. (The automatic occupation tax via `processAutomaticOccupationTax` does work and runs from `TaxManager.java:702`, so occupation revenue still flows — but the manual collection promise is broken.)

### C2. `ClaimVassalTributePacket` only ever gives currency in SDMShop mode
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimVassalTributePacket.java:43-67` — when SDMShop is disabled, the handler sends a "Claimed X" message but never actually issues the currency item (`// For now, just send a message - item dropping logic would be more complex`).
- `VassalManager.claimVassalTribute` (`vassalization/VassalManager.java:712`) does drain the vassal colony's stored tax and credit the overlord's primary colony via `TaxManager.adjustTax`, so the vassal IS taxed and the overlord IS credited at the colony-tax level — but the player gets no physical currency drop. This is inconsistent with the chat message and likely a half-implemented branch. Severity is Critical because it touches a real economy flow (vassal loses tax, overlord gets colony-tax credit, but no item is dispensed for non-SDMShop configs, making the "claim" feel like a no-op to the player).

---

## High

### H1. `ClaimVassalTributePacket` is fully replayable / unrate-limited
- `network/packets/ClaimVassalTributePacket.java:31-67` — no cooldown, no per-mission token, no idempotency.
- `VassalManager.claimVassalTribute` (`vassalization/VassalManager.java:712-740`) takes a snapshot of the vassal's current stored tax, computes `tributeOwed = currentTaxBalance * rel.percent / 100.0`, drains it. A client spamming the packet 20 times in one tick will, after each call, see the vassal balance drop by another `percent%` of the *new* balance. With 50% tribute and an initial balance of 1000, ten replays drain to ~1 instead of the intended 500. Combined with the parallel `handleTaxIncome` path that already takes tribute at tax-generation time (`TaxManager.java:656`), this is a double-charging surface.
- Fix: add a per-vassal claim cooldown (e.g. once per tax generation cycle), or reduce `claimVassalTribute` to a no-op idempotent of `handleTaxIncome` (i.e. take tribute exactly once per cycle).

### H2. TOCTOU race in `VassalManager.requestVassalization` / `acceptProposal`
- `vassalization/VassalManager.java:59-98` — uses `containsKey` then `put`. Two concurrent requestVassalization calls (e.g. two different players spamming proposals on the same target) can both pass the guard before either writes, leaving whichever lands last as the visible proposal and silently losing the other (no notification rolled back).
- Same pattern in `forceVassalize` (line 300-320).
- Should use `putIfAbsent` and branch on the return value. The maps are `ConcurrentHashMap`, so the atomic helpers are available; only the API is wrong.

### H3. `VassalManager.getPrimaryColonyOfPlayer` NPEs on abandoned colonies
- `vassalization/VassalManager.java:489-496`:
  ```java
  for (IColony c : cm.getAllColonies()) {
      if (c.getPermissions().getOwner().equals(playerId)) return c;
  }
  ```
- `ColonyAbandonmentManager.isColonyAbandoned` (line 360-383) and `fixNullOwnerColony` (line 385-405) both explicitly handle the case where `getOwner()` returns null, proving this state is common during the abandonment grace window and before the periodic null-owner repair runs.
- An NPE here propagates up through `handleTaxIncome` (line 269), `listVassals` (line 202), and `getVassalIncomeForPlayer` (line 692). In `handleTaxIncome` the NPE is unguarded and will be thrown into the tax-generation loop in `TaxManager.java:656`, killing the rest of that colony's tax cycle.
- Fix: null-guard the owner before `equals`.

### H4. `EndVassalizationPacket` vs `VassalManager.revokeRelation` authorization mismatch
- `network/packets/EndVassalizationPacket.java:53` requires `isColonyManager()` on the vassal colony only — there is no check that the colony is in fact the *vassal* side rather than the overlord's primary. A player who is a manager of an unrelated colony cannot trigger it (the colony ID is in the URL), but the check is asymmetric vs `VassalManager.revokeRelation` (line 162-194) which accepts either an overlord-side initiator (UUID match) or a manager-of-vassal initiator. Different code paths, different rules, easy to drift further.
- Lower-impact than it sounds, but worth unifying both paths through one `endVassalization(executor, colonyId, requireRole)` helper to prevent permission divergence.

### H5. Race between two players claiming the same abandoned colony
- `ColonyClaimingRaidManager.startClaimingRaid` (`abandon/ColonyClaimingRaidManager.java:87-205`) gates with `activeClaimingRaids.containsKey(colony.getID())` then later `activeClaimingRaids.put(...)`. Between the check and the put, a second player can pass the same check and both initialise raid state, after which one overwrites the other's `ClaimingRaidData` in the map.
- The second player's `convertCitizensToMilitia` then re-applies effects/targets and re-registers them with `CitizenMilitiaManager`, while the first player's `bossEvent` becomes orphaned (still references the players but is no longer in the map, so `endClaimingRaid` will never clear it).
- Fix: `putIfAbsent` returning null gating proceed; otherwise fail.

### H6. `OccupationManager.shouldBlockInteraction` semantics inverted from the docstring intent
- `occupation/OccupationManager.java:569-576`:
  ```java
  /** Returns true if the occupier should be blocked from interacting with the occupied colony's buildings and items. */
  public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
      OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
      if (data == null) return false;
      // Block the occupier from interacting with the occupied colony's items
      return data.occupierUUID.equals(playerUUID.toString());
  }
  ```
- The method is queried by `BesiegeManager.shouldBlockInteraction` (per Grep output) and presumably permissions code. It returns **true only for the occupier**, meaning the original owner can interact freely while the occupier is blocked. That matches the docstring, but the spec in the class header (lines 31-39) says "The occupier may collect taxes but cannot interact with occupied colony buildings or items" — confirmed correct. However, in the unrelated `BesiegeManager.shouldBlockInteraction` call site there appears to be no `OccupationManager` integration check — if `colony` is both besieged and occupied, only the besiege check fires. Verify the wiring in `permissions/TaxPermissionManager.java` actually consults `OccupationManager.shouldBlockInteraction` too. (Grep shows it does not — only `BesiegeManager.shouldBlockInteraction` is consulted in `TaxPermissionManager.java:26`.)
- Effect: post-war occupation by the attacker does NOT actually block their interaction with the occupied colony. The docstring promise is unenforced.

---

## Medium

### M1. `OccupationManager.findColonyById` is O(worlds × colonies) for no reason
- `occupation/OccupationManager.java:627-637` — iterates `server.getAllLevels()` × `IColonyManager.getInstance().getColonies(level)`. Every other manager (`VassalManager.getColonyById`, `ColonyClaimingRaidManager.getColonyById`) uses `IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()`. The latter is the canonical API; the former duplicates work and is broken in dimensions added after the iteration.

### M2. `ColonyAbandonmentManager` startup data has no integrity check
- `abandon/ColonyAbandonmentManager.java` — `formerColonyMembers`, `abandonedColonies`, `warnedColonies` are **not persisted at all**. After a server restart, any colony that was abandoned by `abandonColony()` retains its neutral-rank permissions (because those went through `IPermissions.setPermission`, which MineColonies saves), but the WnT-side flags (`isColonyAbandoned`, `wasFormerOwnerOrOfficer`, `claimingGracePeriods`) are wiped. Result:
  - Former owners cannot use the "bypass claiming requirements" reclaim path post-restart.
  - `isColonyAbandoned(colony)` returns false until the next periodic check re-derives the state, allowing a player to claim an abandoned colony as a normal colony (which fails at the `startClaimingRaid` precondition — but the user-facing list will be empty, masking the abandoned state until the abandonment scheduler ticks again).
- Fix: persist `abandonedColonies`, `formerColonyMembers`, and `claimingGracePeriods` to JSON (similar to `occupations.json` / `vassals.json`).

### M3. Occupations file load has no schema/integrity guard
- `OccupationManager.loadData` (line 605-625) — if `occupations.json` is corrupted (truncated, bad JSON), `GSON.fromJson` will throw → caught, logged, and the live map stays empty. No backup is made of the corrupt file; the next `saveData()` writes a fresh, empty version, silently losing all occupations. Same issue in `VassalManager.loadData` (line 505-521) and `OfficerColonyVisitTracker.loadData` (line 174-196).
- Suggest renaming a corrupt file to `*.broken-<timestamp>` instead of overwriting.

### M4. `ColonyAbandonmentManager.fixNullOwnerColony` automatically promotes the first found player to Owner
- `abandon/ColonyAbandonmentManager.java:385-405` — assigns "any player in the permissions map" as the new owner if owner is null, then marks the colony abandoned. This is invoked from `isColonyAbandoned` (line 368), which is called from `ColonyClaimingRaidManager.startClaimingRaid` (line 94) on the colony the *claiming player* is targeting. If two abandoned-but-null-owner colonies are claimed simultaneously, this can promote an arbitrary low-rank player to Owner with no notification. The owner change is permanent and persisted by MineColonies.
- Lower severity because this is a recovery path for a degenerate state, but worth wrapping in `LOGGER.warn` + admin-pinging.

### M5. `OccupationManager.startOccupation` does not check whether the colony is currently abandoned
- `occupation/OccupationManager.java:149-208` — no consultation of `ColonyAbandonmentManager.isColonyAbandoned(colony)`. If a war ended with the attacker triggering occupation against an already-abandoned target, the original owner UUID stored in `OccupationData.originalOwnerUUID` may be the system-owner placeholder UUID (`createSystemOwner`), which has no real player. The reclamation-by-original-owner path then becomes unreachable.

### M6. `OccupationManager.checkExpiredOccupations` does not preserve order under concurrent `endOccupation`
- `occupation/OccupationManager.java:416-514` — builds `toTransfer` from a snapshot, then for each entry calls `ACTIVE_OCCUPATIONS.get(colonyId)` and operates on the live data. Between the snapshot and the get, `endOccupation` or `reclaimByOriginalOwner` could remove the entry — handled (the `null` branch continues). But for entries that DO still exist, the broadcast and transfer happen even if the war state has changed in the meantime. There is no re-check of `data.reclamationAttempted` or war state.
- Minor — only matters in narrow timing windows.

### M7. `OfficerColonyVisitTracker` keeps `playerLastChunk` and `playerLastColony` for offline players
- `event/OfficerColonyVisitTracker.java:39-40, 348-354` — these maps ARE cleared on `PlayerLoggedOutEvent`, but only if the player logs out cleanly. Server crash → maps grow until next restart.
- Lower-severity memory leak; affects long-running servers with high join churn.

### M8. `VassalManager.handleTaxIncome` removes an expired vassal from inside its own caller's iteration?
- `vassalization/VassalManager.java:244-285` is called from `TaxManager.java:656` per colony per cycle. It mutates `ACTIVE_VASSALS` via `remove` on expiry. Looking at the call site, `TaxManager` does NOT iterate `ACTIVE_VASSALS`; it iterates colonies and asks per-colony, so this is safe. Just noting for any future code that wants to iterate the active vassals map and call this method.

### M9. `requestVassalization` does not reject offers from a player who already has the max number of vassals
- `vassalization/VassalManager.java:626-634` exposes `countVassalsForPlayer` but no caller in `requestVassalization` enforces a cap. There is no config for "max vassals per overlord" visible in the audit (search `TaxConfig.java` did not surface one in scope). An overlord can spam-request and accumulate unlimited vassals.

---

## Low

### L1. `OccupationManager.collectOccupationTax` writes `saveData()` per call
- `occupation/OccupationManager.java:339` — every manual collection triggers an async write. Combined with the dead-command issue (C1), this is moot, but if revived it should debounce.

### L2. `ColonyClaimingRaidManager.cleanupOldGracePeriods` runs on every `updateClaimingRaids` tick
- `abandon/ColonyClaimingRaidManager.java:429-431` — cheap (iterates a usually-small map), but could be throttled to once per minute.

### L3. `ColonyActivityCommand` registers itself at root `/colonyactivity`, not under `/wnt`
- `commands/ColonyActivityCommand.java:18-27` — inconsistent with every other mod command. Op-level 2 gated, so not a security issue, but discoverability is poor.

### L4. `ColonyActivityCommand.listInactiveColonies` ignores `OfficerColonyVisitTracker`
- `commands/ColonyActivityCommand.java:85` and `:131,136` use `colony.getLastContactInHours()` directly, bypassing the WnT-authoritative `OfficerColonyVisitTracker` that `ColonyAbandonmentManager.checkColonyAbandonmentStatus` (line 96-122) explicitly says is the canonical timer. Admin command will report a different state than the abandonment system would act on, causing confusing diagnostics.

### L5. `ColonyActivityTracker.lastCacheUpdate` is not synchronized
- `util/ColonyActivityTracker.java:19,142,150,162` — non-volatile `long` updated from multiple threads. Worst case is the cleanup runs once more or once less than intended; acceptable but inconsistent with the rest of the code which uses `volatile` (cf. `OfficerColonyVisitTracker.isDirty` line 41).

### L6. `OccupationManager.OccupationData.occupierUUID` stored as `String`
- `occupation/OccupationManager.java:65-66` — both UUIDs are stored as strings and re-parsed on every access via `UUID.fromString(...)`. Functionally correct, but `equals` comparisons elsewhere mix `UUID#toString` and `String#equals`, which is fragile if anyone ever feeds in a `UUID` formatted differently (capitalisation, dashed). Use `UUID` directly with a custom Gson `TypeAdapter`.

### L7. `OccupationManager.checkExpiredOccupations` schedule of 5 minutes is hard-coded
- `MineColonyTax.java:279` — `300_000, 300_000` magic numbers. Suggest exposing in config.

### L8. `VassalManager.SERVER` is a `static MinecraftServer` that is never cleared on shutdown
- `vassalization/VassalManager.java:45` — stale ref into a stopped server held across restarts (within the same JVM, e.g. single-player → load → quit → load again). Subscribed event handler at line 539 will dispatch with the stale `SERVER`. Setting `SERVER = null` in `shutdown()` would be safer; same issue in `OccupationManager.serverInstance` (line 48).

### L9. `OccupationManager.expirationTime` arithmetic ignores DST/leap seconds
- `occupation/OccupationManager.java:195` — `now + (durationDays * 24L * 60L * 60L * 1000L)`. Uses wall clock; if the system clock jumps backward (DST or NTP correction), `isExpired()` can flip back to false. Acceptable for game-time durations; flagged because the audit asked.

### L10. `ColonyClaimingRaidManager` does not persist `protectedColonies`
- `abandon/ColonyClaimingRaidManager.java:50` — admin-set protections are wiped on restart. No save/load.

---

## Files Audited

| Path | Purpose |
|------|---------|
| `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java` | Occupation lifecycle, persistence, tax flow |
| `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java` | Vassal proposals, relations, tribute, persistence |
| `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimVassalTributePacket.java` | Client → server tribute claim |
| `src/main/java/net/machiavelli/minecolonytax/network/packets/EndVassalizationPacket.java` | Client → server vassal break |
| `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java` | Inactivity-driven abandonment + cleanup |
| `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java` | Claiming raids on abandoned colonies |
| `src/main/java/net/machiavelli/minecolonytax/event/OfficerColonyVisitTracker.java` | Authoritative officer-visit timer + persistence |
| `src/main/java/net/machiavelli/minecolonytax/util/ColonyActivityTracker.java` | Tax-pause activity cache |
| `src/main/java/net/machiavelli/minecolonytax/commands/ColonyActivityCommand.java` | `/colonyactivity` admin command |
| `src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java` (claim/vassal blocks only) | Command wiring |
| `src/main/java/net/machiavelli/minecolonytax/TaxManager.java` (line 654-712) | Vassal tribute + occupation tax integration |
| `src/main/java/net/machiavelli/minecolonytax/WarSystem.java` (line 1180-1240) | `transferOwnership` (post-occupation deed transfer) |
| `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java` (line 165-380) | Init/shutdown wiring of managers |

### Confirmed API Safety
- `Grep "getBuildingManager|getServerBuildingManager" src/main/java/net/machiavelli/minecolonytax` returned **only** `compat/ColonyBuildingUtil.java` (the sanctioned reflection shim) and `siege/TownHallDemolitionObjective.java` (out of audit scope). **Neither `ColonyAbandonmentManager` nor `ColonyClaimingRaidManager` nor `OccupationManager` nor `VassalManager` directly call the cross-version-unsafe building APIs.** That part of the architecture rule is intact.
