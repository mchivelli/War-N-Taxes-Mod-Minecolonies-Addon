# War 'N Taxes — Release-Readiness Audit (2026-06-14)

**Method:** 14 parallel system-lane audit agents → adversarial re-verification of every CRITICAL/HIGH finding by a second independent agent. 25 agents total, ~2.8M tokens. Build verified green (`gradlew build` exit 0: `checkBuildingApiUsage` guard + JUnit shim tests pass).

## Verdict

**Releasable with fixes.** No CRITICAL defects. The core war lifecycle, persistence (34-param restoration constructor round-trips), victory re-entry guard, money math (no negative balances / no money creation), and dist isolation are all sound and show prior hardening. The issues below are real but bounded — none brick a default-config server, but several cause player-facing money/feature loss that would generate bug reports on release.

| Severity | Count | Distinct |
|----------|-------|----------|
| CRITICAL | 0 | 0 |
| HIGH | 10 | 9 (H7 = H10) |
| MEDIUM | 19 | 19 |
| LOW | 44 | ~42 |

## Cross-cutting patterns (the real story)

1. **Non-atomic JSON saves** — The atomic temp+move pattern was applied to Treasury/War/Occupation/Vassal/FCT but **missed** on: `ColonyUpgradeManager` (H3 — money loss), `RaidPenaltyManager` (M9), `BesiegeManager` (M16), `RandomEventManager` (M11), `SpyManager` (L37), `TaxPolicyManager` (M4). A crash mid-write truncates these files.
2. **Corrupt-JSON load not caught** — `IOException`-only catches let `JsonSyntaxException` (a RuntimeException) escape. On startup handlers this is **fatal to world load**: `TaxManager.loadTaxData` (H6 — exists on every install!) and `FactionManager` (M17). Already-fixed siblings: FCT/Spy/Occupation.
3. **Never-wired subsystems** — `RaidPenaltyManager.initialize()` is never called (H7/H10 — `/wnt repair` permanently broken + penalties lost on restart); `deliverPendingRewards()` has zero callers (H2 — offline spies lose intel); `TradeRouteManager` fully orphaned (M19); `PayDebtPacket` / `ClaimVassalTributePacket` never registered (L34/L35/L43/L44).
4. **Command registration split** — 6 commands on `ServerStartingEvent` vanish after `/reload` (H5); `TaxGUICommand` dist-gated off dedicated servers (M14); 3 dead command classes (L29).

---

## HIGH (confirmed, verified)

### H1 — `TaxDebtCommand` destroys player currency items on insufficient funds `[EXPLOIT]`
`commands/TaxDebtCommand.java:125-144`. In item-currency mode (or any server without SDMShop), `deductCurrencyFromInventory` zeroes matched stacks *as it scans*, then returns false if short — items already destroyed, debt not paid, no rollback. **Fix:** pre-count sufficiency before mutating (the codebase already has the correct pattern in `ItemUtils.takeCurrencyFromInventory` / `CurrencyService.takeFromPlayer`).

### H2 — Offline spy rewards never delivered `[DISCONNECT]`
`espionage/SpyManager.java:614`. `deliverPendingRewards()` has zero callers; missions completing while the owner is offline queue into `PENDING_REWARDS` which is never drained (and never persisted). Intel book + map silently lost; colony still charged. **Fix:** call from `PlayerLoggedInEvent` + the GUI-open path; persist `PENDING_REWARDS`.

### H3 — `colony_upgrades.json` non-atomic save wipes paid upgrades `[PERSISTENCE]`
`upgrade/ColonyUpgradeManager.java:162-170`. Plain `FileWriter` truncates before write; crash → empty file → load swallows the error → every colony's investment levels reset to 0, treasury already debited, no refund. **Fix:** temp+ATOMIC_MOVE via `AsyncSaveExecutor` (mirror `TreasuryManager.writeData`).

### H4 — War militia never autonomously attack the enemy `[DISCONNECT]`
`militia/CitizenMilitiaManager.java:252`. The targeting predicate keys off `RaidManager.isPlayerCurrentlyRaiding`, but the war flow never populates `activeRaids`. War militia equip weapons + show "joined the militia to defend" but only retaliate *after* being hit. **Fix:** make the predicate war-aware (`WarSystem.isEnemyWarParticipant(uuid, colony)`).

### H5 — Six admin commands disappear after `/reload` `[DISCONNECT]`
`MineColonyTax.java:93-99`. `TreasuryCommand`, `RaidRepairCommand`, `FactionCommand`, `TaxPolicyCommand`, `RandomEventsCommand`, `RecipeDisableTestCommand` are registered on `ServerStartingEvent` (fires once at boot), not `RegisterCommandsEvent` (fires on every `/reload`). After a routine `/reload` they vanish until full restart. **Fix:** move the 6 `register()` calls into `PvPEventHandler.onRegisterCommands`.

### H6 — Corrupt `colonyTaxData.json` aborts world load `[PERSISTENCE]`
`TaxManager.java:1207`. `loadTaxData` only catches `IOException`; a corrupt/truncated tax file throws `JsonSyntaxException` (RuntimeException) out of `onServerStarting` → **world fails to load**. File exists on every install. **Fix:** broaden to `catch (Exception)` → start fresh (the team already did this for FCT).

### H7 / H10 — `RaidPenaltyManager` never initialized `[DISCONNECT]`
`economy/RaidPenaltyManager.java:47`. `initialize()` is never called → `SERVER` stays null (so `/wnt repair` always reports "Colony not found" — command is registered but can never succeed) → `loadData()` never runs (persisted raid penalties dropped every restart). **Fix:** wire `initialize(server)`/`shutdown()` in `MineColonyTax` like the other ~15 managers.

### H8 — Duel wager collected & advertised but never settled `[DISCONNECT]`
`pvp/PvPBattleManager.java:352,695,1069`. `/pvp duel <wager>` tells both players "for X coins"; `getAmount()` is never read, `processBattleRewards` is an empty stub. No escrow, no deduction, no payout. **Fix (product decision):** remove the wager from the command (safe), or implement escrow+payout via the currency layer.

### H9 — Team-battle friendly-fire protection never triggers `[BUG]`
`pvp/PvPEventHandler.java:280-303`. Looks up the team battle in `pendingTeamBattles` using the `ActiveBattle` id (`ab_…`/`challenge_…`) — a disjoint key namespace, and the entry is removed before the battle starts. Lookup is always null → `PVP_DISABLE_FRIENDLY_FIRE` is a silent no-op. **Fix:** detect same team from the live `ActiveBattle.getTeamIndex`, guarding `size() > 1` so duels/FFA aren't affected.

---

## MEDIUM (19)

| # | Lane | Issue | File |
|---|------|-------|------|
| M1 | war | `handleTimeExpiry()` lacks the re-entry guard `checkForVictory()` has (safe only by caller gating) | `WarSystem.java:1707` |
| M2 | war | `endWar()` history records `amountTransferred=0` for most wins (string-match disconnect) | `WarSystem.java:1573` |
| M3 | tax | `CheckTaxRevenueCommand` NPE — `playerRank` not null-checked | `CheckTaxRevenueCommand.java:55` |
| M4 | tax | `TaxPolicyManager` load doesn't catch corrupt JSON; save non-atomic | `economy/policy/TaxPolicyManager.java:350` |
| M5 | siege | Banner capture state not persisted; restart strands inert banner | `siege/PlantTheBannerObjective.java:60` |
| M6 | vassal | `VassalManager.loadData` never clears maps → relations leak across single-player worlds | `vassalization/VassalManager.java:518` |
| M7 | vassal | Occupation tax + vassal tribute stack with no floor → tax ledger negative | `TaxManager.java:676` |
| M8 | espionage | FLEEING mission whose chunk unloads leaks forever, consuming a spy slot | `espionage/SpyManager.java:863` |
| M9 | economy | `raid_penalties.json` non-atomic save | `economy/RaidPenaltyManager.java:260` |
| M10 | raids | Guard resistance buff never removed if config toggled off mid-raid | `raid/GuardResistanceHandler.java:391` |
| M11 | events | `random_events.json` non-atomic save | `events/random/RandomEventManager.java:921` |
| M12 | events | `LABOR_STRIKE` sets transient `JobStatus.STUCK` the AI overwrites — cosmetic | `events/random/deep/CitizenManipulator.java:58` |
| M13 | config | DEAD key `ResearchSpyDefenseBonus` accessor never called | `TaxConfig.java:1837` |
| M14 | commands | `TaxGUICommand` not registered on dedicated servers (dist-gated) | `pvp/PvPEventHandler.java:99` |
| M15 | economy | (= H3) `ColonyUpgradeManager.saveData` non-atomic, on server thread | `upgrade/ColonyUpgradeManager.java:162` |
| M16 | besiege | `BesiegeManager.saveData` non-atomic — drops persisted besiege occupations | `besiege/BesiegeManager.java:1434` |
| M17 | faction | Corrupt `factions.json` aborts world load (IOException-only catch) | `faction/FactionManager.java:41` |
| M18 | pvp | Spectators stranded in SPECTATOR at arena when battle ends | `pvp/PvPBattleManager.java:731` |
| M19 | wiring | `TradeRouteManager` fully orphaned (never init → NPE; command never registered) | `trade/TradeRouteManager.java:111` |

## LOW (44) — selected

Balance/config: war-spoils stacking (50% treasury + 25% wallet + tribute) may over-punish (L26); `OccupationTaxPercentage` 0.50 vs 100% primary path (L27); `TreasuryDrainPerMinute` comment/value mismatch (L28); `SpyTravelMinMinutes` min 0 = instant arrival (L25); DEAD `WarTaxFreezeHours` (L24); free factions ignore configured cost (L33). Persistence: `espionage.json` non-atomic (L37). Dead code: `PayDebtPacket`/`ClaimVassalTributePacket` never registered (L34/L35), 3 dead command classes (L29), `EntityRaidManager` `@Deprecated` but still ticked (L22). Safety: reparations div-by-zero NaN when losing balance is 0 (L1); militia `HurtByTargetGoal` no friendly-fire exclusion (L17); `/wnt abandonmentcheck` leaks officer rosters to all players (L32). Plus log-gating violations and minor edge cases. Junk root files (L38) — **already removed**.

---

*Full structured findings (every finding + verifier reasoning) preserved in the workflow run output.*

---

## Fixes Applied (2026-06-14, same session) — BUILD GREEN

Scope chosen: **all 9 HIGH + key MEDIUM**, **implement** the duel wager, **remove** dead code. Applied in 8 parallel disjoint file-groups, then an **adversarial review of the new economy code** caught 2 CRITICAL + 2 HIGH regressions in the *fixes themselves*, which were then fixed in a second round. Final `gradlew build` passes (API guard + tests). 21 files changed (+646/−118), 4 dead files deleted (−791).

### HIGH fixed
- **H1** `TaxDebtCommand` now routes the item-currency branch through `CurrencyService.takeFromPlayer` (non-destructive sufficiency pre-check) — no more item loss on underpay.
- **H2** `SpyManager.deliverPendingRewards` wired to `WarEventHandler.onPlayerLogin` + `RequestSpyDataPacket` (GUI open); `PENDING_REWARDS` now persisted in save data.
- **H3** `ColonyUpgradeManager` save → atomic temp+ATOMIC_MOVE via `AsyncSaveExecutor`; load keeps prior state on corrupt parse.
- **H4** `WarSystem.isEnemyWarParticipant(uuid, colony)` added (INWAR-gated, dual-side-safe); militia target predicate now war-aware.
- **H5** Six commands moved from `ServerStartingEvent` → `PvPEventHandler.onRegisterCommands` (survive `/reload`).
- **H6** `TaxManager.loadTaxData` catch broadened to `Exception` (corrupt tax file → start fresh, no world-load crash).
- **H7/H10** `RaidPenaltyManager.initialize/shutdown` wired into `MineColonyTax` lifecycle (`/wnt repair` works, penalties persist).
- **H8** Duel wager **fully implemented**: escrow at accept, pot payout to winner, refunds on every non-win exit (draw/disconnect/timeout/abort/**shutdown**), source stored at escrow time, delivery-confirmed messaging with alternate-source fallback, offline-winner pot redistributed/refunded (never burned).
- **H9** Team friendly-fire now detected from the live `ActiveBattle` team index (`size()>1` guard preserves 1v1/FFA).

### MEDIUM fixed
M1 (time-expiry re-entry guard), M3 (CheckTaxRevenue NPE), M4 (TaxPolicy atomic+catch), M6 (Vassal cross-world leak), M7 (occupation/tribute negative floor — see C#2), M9 (raid_penalties atomic+catch), M10 (guard-resistance removal ungated), M11 (random_events atomic), M14 (TaxGUICommand registered on servers), M16 (Besiege atomic), M17 (factions corrupt-load), M18 (spectators released on battle end).

### Regressions caught by the review gate and re-fixed
- **C#1** Server-stop during a wagered duel destroyed escrowed coins → `onServerStopping` now refunds in-flight wagers.
- **C#2** M7 occupation clamp created money (occupier credited unclamped, colony debited clamped) → clamp moved into `processAutomaticOccupationTax` so **credit == debit**; same conservation clamp added to the faction shared-pool path (`FactionManager.processFactionTax`).
- **H#3** Wager refund/payout ignored `giveToPlayer()` return → now delivery-confirmed with alternate-source fallback.
- **H#4** `SpyManager.saveData` was the last non-atomic save → converted to atomic temp+move.

### Dead code removed
`TradeRouteManager`, `TradeRouteCommand`, `PayDebtPacket`, `ClaimVassalTributePacket` (−791 lines). Plus 13 stray 0-byte shell-artifact files cleaned from the repo root.

### Known residual items (non-blocking, documented)
- **Faction-pool / occupation at revenue/pool cap (deflationary):** `incrementTaxRevenue` caps the recipient at `MaxTaxRevenue`; an occupier *at the cap* can be credited slightly less than the colony is debited (money destroyed, not duplicated — not exploitable). Full conservation needs `incrementTaxRevenue` to return the actually-credited amount (a broad cross-caller change).
- **Wager escrow not disk-persisted:** in-flight wagers refund online players on shutdown, but a player already *offline* at shutdown can't be paid synchronously (would need on-disk escrow). Rare and only after fixing the prior CRITICAL.
- **Deferred from this pass** (lower priority): M2, M5, M8, M12, M13 and the LOW tier (dead packets already removed; balance tweaks like war-spoils stacking L26, `SpyTravelMinMinutes` min 0 L25, free factions L33; log-gating cleanups; `/wnt abandonmentcheck` info leak L32).

