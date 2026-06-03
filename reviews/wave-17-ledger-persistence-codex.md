# Wave 17 — WarBlockLedger restart persistence (codex-gated)

Closes the deferred "Phase 2" gap: wars persist across a server restart
(`active_wars.json`) but the explosion-damage ledger did not, so blocks broken
before a mid-war restart were never restored at war end.

## Change
- `WarBlockLedger.saveToDisk()` / `loadFromDisk()` / `pruneOrphans(Set)` /
  `flushPendingRestores()` — NBT persistence to `config/warntax/war_block_ledger.nbt`.
- `restoreWarDamage` restructured to track in-flight batched restores in an
  `IN_FLIGHT` `RestoreJob` map so they can be flushed synchronously on stop.
- `MineColonyTax`: `loadFromDisk()` before `WarSystem.loadAndResumeActiveWars()`,
  `pruneOrphans()` after; `flushPendingRestores()` + `saveToDisk()` on stop,
  before `TickScheduler.shutdown()`.
- NBT (not Gson) because `BlockState` + block-entity snapshots are NBT-native.

## Codex round 1 — 4 findings, all fixed
- HIGH load ordering: downtime-expired war could `endWar()` during resume before
  ledger load → moved `loadFromDisk()` before war resume.
- HIGH mid-restore shutdown loses remaining blocks → `IN_FLIGHT` + `flushPendingRestores()`.
- MEDIUM delete-on-parse-failure too harsh → rename to `.failed-<ts>` like `active_wars.json`.
- MEDIUM stale in-memory `LEDGERS` on same-JVM relog → clear `LEDGERS`+`IN_FLIGHT` at top of `loadFromDisk()`.
- Codex CONFIRMED correct: NBT round-trip (`writeBlockState`/`readBlockState`,
  `saveWithFullMetadata`/`loadStatic`), ConcurrentHashMap safety.

## Codex round 2 — 1 MEDIUM, fixed
- MEDIUM: `loadFromDisk()` deleted the file before war restore was known-good →
  a partial war-restore + `pruneOrphans` could lose recoverable ledgers, file gone.
  Fix: do NOT delete on load; `saveToDisk()` owns the file lifecycle (rewrites,
  or removes when empty, on next shutdown after prune). Reload is idempotent
  (clears first) so keeping the file cannot double-restore.
- Codex CONFIRMED: setBlock during ServerStoppingEvent OK; IN_FLIGHT vs
  TickScheduler OK (same thread); prune does not kill in-flight restores.

## Codex round 3 — double-restore concern, proven UNREACHABLE
Codex worried a retained ledger file + stale `active_wars.json` could double-restore
after a crash. Proven not reachable and documented in `restoreWarDamage` javadoc:
- `restoreWarDamage` does `LEDGERS.remove(warId)` first → same-session idempotent.
- Only re-population is `loadFromDisk()`; `pruneOrphans` retains only active wars.
- `active_wars.json` is always deleted/renamed on load and co-written with the
  ledger only at graceful shutdown — so "war active in active_wars.json AND already
  restored" is unreachable; a reloaded ended-war ledger is pruned, not re-applied.
- Codex final verdict: "Confirmed safe... I cannot produce a concrete reachable
  sequence."

## Build
`./gradlew build -x test` → BUILD SUCCESSFUL (only pre-existing
FMLJavaModLoadingContext deprecation warnings in MineColonyTax.java).
