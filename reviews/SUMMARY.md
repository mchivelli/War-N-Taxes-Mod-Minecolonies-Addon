# Siege SMP Refactor — Codex Review Summary

All 11 steps were implemented and reviewed by `codex exec` (gpt-5.5) after each step. Each step's bundle and codex output is saved as `reviews/step-NN-bundle.md` and `reviews/step-NN-codex.md` for reference.

## Verdict per step

| Step | Codex Status | Compiles? | Critical bugs | Notes |
|---|---|---|---|---|
| 1 — ColonyTierGuard | REWORK | ✓ | 2 high | Primary detection too narrow; void return masks denials |
| 2 — Tax-occupation modes | REWORK | ✓ | 1 high | reclaimByOriginalOwner is overscoped |
| 3 — Multi-besieger refactor | REWORK | ✓ | 2 high (fixed inline) | Stale colonyId keys in remove + registerAlly |
| 4 — Defender ally notify | PASS (minor caveats) | ✓ | — | Clean small change |
| 5 — Solo damage shield | REWORK | ✓ | 1 high | Bypass: any besieger can freely help others |
| 6 — Container deny during besiege | NEEDS CHANGES | ✓ | — | Defender-side overreach + missing block types |
| 7 — Siege spoils | CHANGES REQUESTED | ✓ | 2 high | Defender-win path never runs; coins disappear when treasury caps |
| 8 — Militia upgrade | PASS (logging fix) | ✓ | — | SLF4J format string is wrong |
| 9 — Vassals data class | PASS | ✓ | — | UI/packet wiring deferred |
| 10 — Block ledger | NEEDS FIXES | ✓ | 1 high | Wrong BlockEntity NBT API for 1.20.1; not wired into endWar |
| 11 — Town Hall demolition | CHANGES NEEDED | ✓ | 2 high | Victory trigger races; TNT source extraction misses player owner |

**Compilation:** every step compiles cleanly. Only pre-existing deprecation warnings (5, all in `MineColonyTax.java`).

---

## Rework backlog — grouped by severity

### Must-fix-before-merge (HIGH)

1. **`transferOwnership` still uses owner UUID for primary detection** (Step 1).
   Permissions owner can be null/stale/placeholder. Wrap with `FirstColonyTracker.getFirstColonyOwner(colonyId)` reverse lookup as the first check, then fall back to permissions owner. *File: `permissions/ColonyTierGuard.java:37`*

2. **Reflective `setOwner` bypasses bypass the guard entirely** (Step 1).
   `ColonyClaimingRaidManager.java:787`, `ColonyAbandonmentManager.java:788`, `WntCommands.java:3420` all flip ownership without going through `transferOwnership`. Either route them through `WarSystem.transferOwnership` (preferred) or explicitly document them as system-owner exemptions.

3. **`reclaimByOriginalOwner` removes ANY occupation including TRANSFER_PENDING** (Step 2).
   Should be gated to `OccupationMode.TAX_ONLY` only and validate the caller is actually the original owner. *File: `occupation/OccupationManager.java`, the new method*

4. **Solo damage shield has a major bypass** (Step 5).
   The `sourceOwnRaid != null` early return lets any active besieger freely deal damage in *any* concurrent besiege scenario — defeats the whole purpose. Remove the global return; keep only the per-raid same-UUID skip. *File: `besiege/BesiegeDamageShieldHandler.java:53`*

5. **Defender-victory siege spoils never fire** (Step 7).
   The timeout path in `BesiegeManager.tick()` cleans up directly instead of routing through `completeBesiege(raid, false, colony)`. Route timeouts via `completeBesiege` so `applySiegeSpoils(..., false)` runs.

6. **Coins disappear when winner treasury is at cap** (Step 7).
   `deductFromTreasury` removes the full amount; `addToTreasury` caps at capacity. Compute actual creditable amount first, deduct only that.

7. **Block ledger uses wrong BlockEntity NBT API for 1.20.1** (Step 10).
   `serializeNBT()/deserializeNBT()` is not the world-save-compatible path. Use `BlockEntity.saveWithFullMetadata()` for capture and `BlockEntity.loadStatic(pos, state, tag)` (or `be.load(tag)` after `setBlock`) for restore.

8. **Block ledger never restores anything** (Step 10).
   `restoreWarDamage(warId, level)` is not called from `WarSystem.endWar`. Ledger just accumulates indefinitely. Add the call.

9. **Town Hall victory triggers race with defender-win** (Step 11).
   Zeroing defender lives can broadcast "experimental victory" but resolve as defender victory if attackers also at 0. Add an explicit `WarSystem.endWarWithReason()` or guard against simultaneous defender win before mutating lives.

10. **TNT/projectile source extraction misses player owner** (Step 11).
    `getDirectSourceEntity()` returns the `PrimedTnt` entity, not the player who lit it. Check `getIndirectSourceEntity()` for `ServerPlayer` first, then fall back to direct.

### Should-fix (MEDIUM)

- **Step 2 — `startOccupation` can NPE on null `originalOwner`.** MineColonies can return null during edge states. Log + return before constructing `OccupationData`.
- **Step 2 — Mode selection should call `ColonyTierGuard.canTransferOwnership` instead of querying `FirstColonyTracker` directly** so the two systems can never disagree.
- **Step 3 — `getActiveRaids()` backward-compat view hides extra besiegers.** External callers in `WntCommands`, `WarEventHandler`, `RaidLoginNotifier`, `RequestColonyDataPacket` should switch to `getAllActiveRaidsByBesieger()` / `getRaidsForColony()`.
- **Step 5 — Custom hostile ranks slip past Neutral/Hostile checks.** Use `rank.isHostile()` instead of equals comparisons.
- **Step 5 — `isDefenderSideTarget` misses `raid.spawnedMercenaries`.** Mercs are defender-side and should be protected by the shield.
- **Step 5 — Non-player damage sources (pets, traps, redstone) bypass the shield.** Only `ServerPlayer` source is handled.
- **Step 6 — `isBesiegeActiveForPlayer` denies containers for any player in the besieged colony, not just the besieger.** Either rename for honesty or add an actual attacker-only check.
- **Step 6 — Container block list is incomplete.** Missing furnace/smoker/blast furnace/brewing stand/lectern. Consider a `BlockEntity` `Container` instanceof check at the world level instead.
- **Step 7 — Reclaim path's attacker/defender selection is suspect.** A successful reclaim treats the occupied colony as loser and the former owner's primary as winner, which may be the same colony.
- **Step 10 — Memory unbounded.** Repeated TNT in same chunk appends duplicates. Switch from `List` to `Map<BlockPos, BlockInfo>` with first-snapshot-wins.
- **Step 11 — UUID that's both attacker and defender allows self-sabotage.** Hard-reject defender membership at the source check.

### Nice-to-have (LOW)

- **Step 1 — `transferOwnership(null, …)` NPEs before reaching the guard.** Add early null check.
- **Step 1 — Vassalization fallback doesn't fire at full-war victory when occupation mode is on.** Only fires at occupation expiry. May leave primaries occupied indefinitely without vassal tribute.
- **Step 2 — Use atomic `ACTIVE_OCCUPATIONS.remove(colonyId, data)`** instead of plain remove.
- **Step 2 — `reclamationAttempted` semantics for TAX_ONLY undefined** — document or clear explicitly.
- **Step 3 — `getActiveRaids()` view's "first raid per colony" is non-deterministic** due to `ConcurrentHashMap` weak consistency.
- **Step 5 — Cooldown denial messages can spam.** Already throttled, but consider per-event-not-per-attempt throttling.
- **Step 6 — Modded containers that don't subclass vanilla blocks are missed.** Block-entity instanceof check would catch these.
- **Step 6 — Hot-path linear scan over active besieges on every right-click.** Add a `colonyId → raid` index in BesiegeManager.
- **Step 8 — SLF4J `{:.2f}` is wrong.** Use `String.format(Locale.ROOT, "%.2f", multiplier)` and pass as a `{}` arg.
- **Step 8 — `militiaUpgradeCount` is assigned but unused.** Either add it to `totalDefenders` (probably wrong — it's not a victory objective) or remove.
- **Step 11 — Wall-clock cooldown can jump on system time change.** Use `System.nanoTime()` if precision matters.
- **Step 11 — `WAR_HITS` only clears on victory or shutdown.** Add cleanup in normal `endWar` path so peace-ended or stalemate wars don't leak hit state.

---

## Intentionally deferred (Phase 2 — not bugs, just scope)

- **Step 3:** Shared defender pool across concurrent besiegers; last-kill-credit semantics for the victory-race winner.
- **Step 6:** Villager trade `PlayerInteractEvent.EntityInteract` handler for besiege lockdown.
- **Step 8:** Wire militia spawn into `WarSystem.startWar` (currently only besiege calls it).
- **Step 9:** `VassalsPage` UI badge rendering + `ColonyDataResponsePacket` extension to ship the kind field client-side.
- **Step 10:** JSON persistence of the ledger across server restarts.
- **Step 11:** Plant-the-Banner objective (requires `DeferredRegister<Item>` + resource files).

---

## Recommended order to apply reworks

1. Address bug #2 (reflective setOwner bypasses) first — it makes the guard meaningful.
2. Then #1 (FCT-first detection) — strengthens the guard.
3. Then #7 + #8 (block ledger API + wiring) — without these, every wartime explosion permanently disfigures the map.
4. Then #5, #6 (siege spoils correctness) — wrong money is loud once it ships.
5. Then #3, #4, #9, #10 (gameplay correctness for new mechanics).
6. Cluster MEDIUMs by file and clean up in one pass per file.
7. LOWs can wait for the next maintenance pass.

Total HIGH-severity fixes: ~10 distinct changes across ~7 files. Realistic budget: 4-6 hours for a focused pass.
