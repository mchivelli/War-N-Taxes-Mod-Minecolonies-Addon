# War 'N Taxes — Full Mod Audit Summary
**Date:** 2026-05-25 · **Branch:** 1.20.1 · **Scope:** Static audit only, no code changes

Twelve parallel agents reviewed the mod across every major system. Eight defensive auditors covered one subsystem each; four adversary "codex" auditors red-teamed for exploits, concurrency, crashes, and edge cases. Codex CLI ran an independent third-party pass in parallel (see `CODEX_INDEPENDENT.md`).

---

## Top 12 Show-Stoppers (fix first)

1. **Negative `ClaimTaxPacket` mints unbounded tax balance** *(Codex CRIT-1)* — `ClaimTaxPacket.java:36` reads raw signed `amount` from the client; `TaxManager.claimTax:303` does `storedTax - claimedAmount` where `claimedAmount = Math.min(amount, storedTax)`. Send `ClaimTaxPacket(colonyId, -1000000)` repeatedly, then `(colonyId, -1)` → infinite currency dupe.
2. **`UpdatePlayerTaxPermissionPacket` has zero auth** — any online player can grant/revoke tax-claim permission on any colony for any UUID. (`network/packets/UpdatePlayerTaxPermissionPacket.java:38-60`)
3. **Vassal tribute double-paid** — `ClaimTaxPacket` (amount=-2 branch) and `ClaimVassalTributePacket` both transfer the tribute *and* credit the player's wallet. Every vassal cycle silently dupes. (`VassalManager.java:728-732`, `ClaimVassalTributePacket.java:52`)
4. **MineColonies internal-class boot crash** — `MineColonyTax.java:240` calls `com.minecolonies.core.MineColonies.getConfig()...` (internal, not `api`); the surrounding `catch (Exception)` does **not** trap `NoClassDefFoundError`. One MC refactor = unrecoverable startup.
5. **Direct `dev.ftb.mods.ftbteams.*` imports in `WarSystem.java:10`, `WarEconomyHandler.java:4`, `PeaceProposalManager.java:6`** *(Codex HIGH-7)* — `Class.forName` runtime guards are defeated by hard imports that fail class loading *before* the guard runs. mods.toml also doesn't declare FTB Teams or MineColonies as dependencies. Server may fail to load war/economy classes when FTB Teams absent.
6. **5 packet handlers call `Minecraft.getInstance()` at top-level on the server class** — `ColonyDataResponsePacket`, `TreasuryDataResponsePacket`, `OfficerDataResponsePacket`, `InvestmentDataResponsePacket`, `SpyDataResponsePacket`. Dedicated-server `NoClassDefFoundError`. Fix pattern: copy `OpenTaxGUIPacket`'s `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)`.
7. **GUI tax-claim packet bypasses `TaxPermissionManager`** *(Codex HIGH-2)* — `ClaimTaxPacket.java:75` only checks `Rank.isColonyManager()`; only `ClaimTaxCommand:104` calls `canPlayerClaimTax`. Owners who revoke an officer's tax-claim see the officer still claim via GUI.
8. **Officers can't deploy spies** — `DeploySpyPacket.java:43` uses `getIColonyByOwner` (owner-only) before the rank check. UI says "officers can deploy"; server silently no-ops with no error message.
9. **Upgrade purchase not atomic + cost overflow** — `ColonyUpgradeManager:64-76` does read-check-deduct-write with no per-colony lock (double-click yields free skip or double charge). And `Math.pow(scaling, level)` cast to `int` overflows negative → `TreasuryManager.purchase()` *credits* `|cost|`. Free upgrade + treasury inflation.
10. **`PvPKillEconomyHandler` is unbounded, global, farmable** — fires on every cross-player `LivingDeathEvent`, no cooldown, no per-period cap, no alt detection. `Math.max(1, ...)` guarantees at-least-1 currency.
11. **`active_wars.json` save/load loses everything on one bad entry** — `WarSystem.java:4042` deletes file after restore regardless of partial failures; one malformed war = all wars wiped. Also `saveActiveWars` iterates live CHM during `ServerStoppingEvent`. And wars whose duration expired during downtime are silently skipped without resolving outcome.
12. **`PvPManager` has zero persistence** — `originalPositions`, `playerOriginalGameModes`, `defeatedPlayers`, `lockedMaps` live only in HashMaps. Server crash mid-battle = players stranded at arena coords in SPECTATOR mode permanently with no recovery on rejoin.

---

## Findings by Severity (counts across all 12 reports)

| Severity | Count (approx) |
|----------|---------------:|
| CRITICAL | ~20 |
| HIGH     | ~55 |
| MEDIUM   | ~70 |
| LOW      | ~30 |

---

## Findings by System

### 1. Taxation / Economy / Treasury / Upgrades (`defensive_01_taxation.md`)
- **CRIT** Upgrade purchase not atomic (`ColonyUpgradeManager.java:64-76` + `BuyInvestmentPacket.java:40`)
- **CRIT** Faction → Treasury withdrawal burns coins on `deposit()==false` (no refund) (`FactionCommand.java:261-263`)
- **CRIT** `TreasuryManager.shutdown()` used as periodic save during war drain — future contributor adding real shutdown semantics will silently corrupt every active war's treasury every 5 minutes (`WarSystem.java:340-342`)
- **HIGH** SDMShop payout boolean return discarded → coins paid into colony, never reach player (`ClaimTaxCommand.java:137-158`, `ClaimVassalTributePacket.java:51-52`)
- **HIGH** TOCTOU on player balance in `PayDebtPacket.java:84-127` and `TaxDebtCommand.java:108-118`
- **HIGH** `PayTaxDebtPacket.java:74` ignores client amount, force-pays entire debt — combined with above, one click drains a wealthy player's wallet
- **HIGH** `TaxManager.java:509-521` totals tax before per-cap check → inflates every downstream % (treasury auto-deposit, faction pool, occupation diversion, guard boost, efficiency upgrade)
- **HIGH** Upgrade cost integer overflow → negative cost → free upgrade + treasury inflation (`ColonyUpgradeManager.java:61`)
- **HIGH** `colonyTaxMap` is plain `HashMap` while sibling sets are concurrent
- **HIGH** NPE risk: `getRank(...).isColonyManager()` called without null-check in `BuyInvestmentPacket`, `TreasuryActionPacket`, `RequestInvestmentDataPacket`, `TreasuryCommand`
- **Good:** TaxManager uses `ServerTickEvent`, war drain uses TickScheduler, persistence is JSON as expected

### 2. War / Peace / Persistence (`defensive_02_war.md`)
- **CRIT** Persistence drift: `WarData.originalHostilePerms`, `originalHostilePermsForAttacker`, `activeProposal`, `acceptedAllies`, `declinedAllies`, `offlineOutpostWar` are live fields but the restoration constructor and `WarSaveEntry` silently drop them. Active peace proposals evaporate on restart.
- **CRIT** Restoration constructor is actually **28 params, not 27** as CLAUDE.md documents. Comment-tracked contract is stale. Recommend a CI round-trip test.
- **HIGH** `active_wars.json` save is non-atomic (`FileWriter`, no temp+rename). Mid-restore exception + delete = unrestored entries wiped permanently. Malformed file isn't quarantined.
- **HIGH** Peace proposal: any war participant (even a Friend who just joined) can propose SURRENDER. No rank check on proposer.
- **HIGH** Peace responder check NPEs when `Permissions.getRank(player)` is null
- **HIGH** `acceptPeace`/`declinePeace` clear `activeProposal` *after* the work block — simultaneous accepts can double-apply reparations + double-run `endWar`
- **HIGH** War time comparisons use `System.currentTimeMillis()` with no monotonic fallback — NTP skew can instantly expire every live war
- **HIGH** `endWar` is not idempotent; triple-reads `ACTIVE_WARS`
- **HIGH** `extortionImmunity` is in-memory only — players bypass cooldowns via server reboots
- **HIGH** Recovery-status checks call `saveData()` from every `getTaxMultiplier()` call → hundreds of disk writes per tick on large servers
- **MED** `MAX_COMBINED_WAR_PENALTY_PERCENT` cap silently overridden by hard `0.1` floor above 0.9

### 3. Espionage / Spy (`defensive_03_espionage.md`)
- **CRIT** Officers can't deploy spies — `DeploySpyPacket.java:43` uses `getIColonyByOwner` (owner-only). UI lies; downstream `isColonyManager` rank check is dead code; silent return.
- **CRIT** `PENDING_COSTS` never refunded on recall / auto-cancel paths: DEPLOYING-RECALL (`SpyManager.java:322`), ACTIVE-RECALL (`:331`), target-colony-deleted (`:821`), `onSpyKilled` (`:461`). Griefing co-owned colonies.
- **CRIT** Static spy state leaks across single-player world reloads — `SpyManager.loadData()` early-returns without clearing static maps when JSON is absent
- **HIGH** Spies are **immortal to direct combat** — `SpyEntity.hurt()` returns `false` for all player/guard damage (`:211`). Only flee-timeout, lava, drowning, or `/kill` end them.
- **HIGH** `tickFleeing()` lacks the orphaned-entity self-discard check that INFILTRATING has; stale FLEEING entity runs to `FleeMaxSeconds`
- **HIGH** `AsyncSaveExecutor.submit` in `shutdown()` may not flush before JVM exit
- **HIGH** JM waypoints missing alpha channel may render invisible on some JM versions
- **MED** `SpyDataResponsePacket` 32 KB cap exceeded by 5+ tier-3 missions
- **MED** `SpyMapGenerator` uses truncating `/` instead of `Math.floorDiv` — maps shift by one cell at negative coordinates
- **MED** No at-war gating on deploys (may be intentional)

### 4. Occupation / Vassalization / Abandonment (`defensive_04_occupation.md`)
- **Good:** MineColonies API rule intact — no direct `getBuildingManager()` calls in any of these systems.
- **CRIT** Dead manual-collection path: `OccupationManager.startOccupation:230` tells player to run `/wnt collectoccupation <id>`, but command is never registered; `collectOccupationTax()` has zero callers
- **CRIT** Non-SDMShop branch of `ClaimVassalTributePacket` drains the vassal's stored tax without giving the player the currency item
- **CRIT** `VassalManager.getPrimaryColonyOfPlayer` (`:489-496`) NPEs on abandoned colonies (`getOwner().equals(...)` with no null guard); propagates into `TaxManager:656`, killing per-colony tax generation
- **HIGH** `ClaimVassalTributePacket` no rate-limit / idempotency token — spam drains repeatedly; combined with auto-`handleTaxIncome` = double-charge
- **HIGH** `requestVassalization`, `forceVassalize`, `startClaimingRaid` use non-atomic `containsKey`-then-`put` on `ConcurrentHashMap` — simultaneous claimants race
- **HIGH** `EndVassalizationPacket` and `VassalManager.revokeRelation` use slightly different auth rules for the same logical action
- **HIGH** `OccupationManager.shouldBlockInteraction` documented as blocking interaction but no permission consumer wires it up
- **HIGH** `ColonyAbandonmentManager` doesn't persist `abandonedColonies`, `formerColonyMembers`, `claimingGracePeriods`, `protectedColonies` — all wiped on restart
- **MED** None of the three JSON loaders move a corrupt file aside before overwriting with fresh empty version
- **MED** Static server refs (`OccupationManager.serverInstance`, `VassalManager.SERVER`) never cleared on shutdown

### 5. Raids / Random Events / Militia / Guards (`defensive_05_raids_events.md`)
- **HIGH** `activeRaids` is plain `HashMap` despite cross-thread access
- **HIGH** Start-raid "already raided / not in grace" checks TOCTOU-racy with `put`
- **HIGH** Two divergent raid-end methods (`endActiveRaid` static vs `endRaid` instance) apply different penalties, write different history records, different reward-eligibility rules
- **HIGH** Guard resistance: config + uncapped upgrade bonus can overflow Minecraft's byte amplifier (>127 wraps)
- **HIGH** Raid- and war-effect maps overlap → war ending mid-raid strips resistance; tracking keyed by entity UUID (citizen respawn breaks removal)
- **HIGH** Random events biased by enum declaration order — `RandomEventManager` iterates `RandomEventType.values()` and stops on first independent winner. Earlier events (MERCHANT_CARAVAN, BOUNTIFUL_HARVEST) systematically favored. Needs weighted single-pick or shuffle.
- **MED** `DismissEventPacket` uses `ACCESS_HUTS` (any colony Friend can dismiss other members' log entries) where `MANAGE_HUTS` is correct
- **MED** `saveData()` full-rewrites the events JSON once per colony per tax cycle
- **MED** Raid grace periods not persisted across restarts
- **MED** `enableMilitiaCombatAI` wipes all goals and only restores via `onJobChanged` — unemployed-but-eligible citizens may be left with no AI
- **MED** `MilitiaAttackGoal` no team check at attack time, only at target acquisition
- **Good:** `ReflectionCache` is well-handled. `RaidHistoryCommand`/`RaidRepairCommand` properly gated. Building composition checks correctly use `ColonyBuildingUtil` shim.

### 6. PvP Arena (`defensive_06_pvp_arena.md`) — 4 Critical, 8 High, 9 Medium
- **CRIT** `PlayerPvPStats` are **never persisted** — wins/losses/K-D reset every restart. Only arena map definitions saved.
- **CRIT** PvP kill economy fully farmable: every cross-player `LivingDeathEvent`, no cooldown/cap/alt detection. Raid penalty multiplier amplifies. `checkIfRaidRelated()` treats unrelated raids as raid-related.
- **CRIT** Thread safety broken: `PvPManager` exposes plain `HashMap` for `activeBattles`, `pendingTeamBattles`, `pendingRequests`, `playerOriginalGameModes`, `spectatorData`, `playerStats` — mutated from server tick + `BATTLE_END_SCHEDULER` worker thread. `endBattle()` does the outer `activeBattles.remove()` outside any `server.execute()` wrapper.
- **CRIT** Players permanently stranded in SPECTATOR — defeat sets SPECTATOR, scheduled 5s restore captures stale `ServerPlayer`. Crash/disconnect/level-unload in the window = no recovery on rejoin.
- **HIGH** `SpawnPointData` no bounds/lava/void/loaded-chunk validation; placeholder (0,0,0) spawns into the void if large `spawnIndex` supplied
- **HIGH** `isPlayerBusy()` doesn't consult `pendingTeamBattles` — player can join two team battles or accept a duel while in pending team battle

### 7. Permissions / Commands / Networking — SECURITY (`defensive_07_security.md`)
- **CRIT** `UpdatePlayerTaxPermissionPacket` has **zero** permission check — total bypass of WnT permission layer
- **CRIT** `EntityGlowPacket` registered with no `NetworkDirection` + unbounded VarInt decoder — netty-thread spam DoS; one future-maintainer edit and clients control server entity-glow
- **CRIT** `BuyInvestmentPacket` accepts officers; slash command (`WntCommands.java:3833`) requires owner — GUI bypasses owner-only economy actions
- **CRIT** Mass NPE/DoS via unchecked `getRank().isColonyManager()` across `DeploySpyPacket`, `TreasuryActionPacket`, `BuyInvestmentPacket`, `RequestTreasuryDataPacket`, `RequestInvestmentDataPacket` — send packet for colony you don't belong to → main-thread crash
- **CRIT** `WarCommands.register` puts `/raid`, `/wagewar`, `/joinwar`, `/peace`, `/warinfo`, `/suepeace`, `/choosewarside` at the ROOT command namespace with no `.requires(...)`. Collides with other mods, bypasses `/wnt` namespace discipline. Also `TaxGUICommand` registers bare `/mct` and `/taxgui`.
- **HIGH** `TaxManager.payTaxDebt` doesn't validate debt; CLI paths skip pre-check (`TaxManager.java:1179`). Combined with `/wnt claimtax` is a no-op laundering channel; future conversion rate diff = money printer.
- **HIGH** `TaxPermissionManager` officer-toggle state in-memory only — every restart re-enables officer claim permissions the owner had revoked
- **Dead-code risk:** `ClaimVassalTributePacket` and `PayDebtPacket` exist but are NOT registered in `NetworkHandler`. Former has zero permission check. Recommend deletion before either is re-wired.

### 8. Compat / Threading / Performance / Config (`defensive_08_compat_threading.md`)
- **Good:** MineColonies API discipline is clean — zero direct `getBuildingManager()` outside `ColonyBuildingUtil.java`. Only comment hit in `siege/TownHallDemolitionObjective.java:95`.
- **Good:** JM isolation clean — only `JmImpl.java:3-4` imports `journeymap.*`. `SpyJourneyMapPlugin.java:28` correctly gates with `ModList.isLoaded` and catches `NoClassDefFoundError`.
- **CRIT** Client-class leakage in 5 packets (see #4 in top-10 above)
- **HIGH** `PvPManager.BATTLE_END_SCHEDULER` is a `ScheduledExecutorService` with no `.shutdown()`. (Also `WarStatsDB.WRITER` and `AsyncSaveExecutor.EXEC` exist — intentional blocking-I/O carve-outs with proper shutdown.)
- **HIGH** `TaxConfig` accessor discipline broken — 108 direct `TaxConfig.X.get()` call sites across 21 files (37 in `WarSystem.java` alone). CLAUDE.md mandates accessor methods.
- **HIGH** Recipe disable stack duplicated 5 ways: `DatapackInjector` + `RecipeDisableRuntime` + `RecipeDisableClient` + `RecipeDisableEventHandler` + `RecipeCraftBlocker`. Reflection on private `RecipeManager` fields. `RecipeCraftBlocker.onItemCrafted` clears result *after* ingredients consumed (player loses materials). Five hand-maintained block lists must stay in sync.
- **MED** `SDMShopIntegration` no `ModList.isLoaded` short-circuit — spams INFO/WARN every server start when absent
- **MED** `CrashLogger` opens synchronous `FileWriter` on calling thread (no async routing)
- **MED** `TaxConfig` is 3687 lines, 253 accessors

---

## Adversary Codex Findings

### A. Exploit Hunter (`adversary_A_exploits.md`)
Already merged into Top 10 + System sections above. Notable additions:
- **MED** Integer underflow: `claimTax(MIN_VALUE)` paths
- **MED** Free-upgrade-when-cost=0
- **MED** Spy intel leaking live SDMShop wallets
- **MED** Spy-cooldown bypass via self-lava-suicide
- **MED** Disk I/O amplification via spam-claiming amount=0
- **Hardening:** standardize permission checks, add a packet rate limiter, single-sink currency payments, long-based arithmetic everywhere, GUI-session nonces for Pay-Debt, audit-log every mutating packet

### B. Concurrency (`adversary_B_concurrency.md`) — 16 findings (CONC-1..16)
- **Good:** All 25 network packet handlers correctly wrap server-state mutation in `ctx.enqueueWork(...)` — exhaustive grep confirmed. No netty-thread mutations.
- **CRIT** Only one forbidden executor: `PvPManager.BATTLE_END_SCHEDULER` (`PvPManager.java:59`). Two others (`AsyncSaveExecutor`, `WarStatsDB.WRITER`) are intentional I/O carve-outs that should be explicitly grandfathered.
- **HIGH** `PvPManager` 9+ critical battle-state collections plain `HashMap` (see PvP section)
- **HIGH** `RaidManager.java:42,45,53,55` plain `HashMap`; `getActiveRaids()` returns live reference
- **HIGH** `FactionManager.FACTIONS` static reassigned non-volatilely in `loadData()`
- **HIGH** `WarSystem.saveActiveWars` iterates live `ACTIVE_WARS` during `ServerStoppingEvent` before `TickScheduler.shutdown()` — late-firing war-drain task can see partially-serialized state. Other managers snapshot first; this one doesn't.
- **HIGH** `AsyncSaveExecutor` callers like `SpyManager.saveData()` snapshot the top-level map but share `SpyMission` object references with the worker — Gson can serialize while server thread mutates fields. Latent torn-write race. Deep-copy or move to TickScheduler.

### C. Crash Hunter (`adversary_C_crashes.md`) — 14 candidates
- **CRIT** Already covered: internal MC class on startup, EntityMercenary hard import in 3 files (RaidKillTracker, ColonyClaimingRaidManager, BesiegeManager) — every `LivingDeathEvent` walks `instanceof EntityMercenary` with no NCDFE protection. Server unrecoverable if class moves.
- **CRIT** War save NPE on abandoned-colony defender team ID (`WarSystem.java:3958`) — drops all later wars on save
- **CRIT** Occupation start NPE on null owner (`OccupationManager.startOccupation` + `OccupationData:88`) — war victory against abandoned colony NPEs, war stuck in ACTIVE_WARS forever draining treasuries
- **CRIT** Spy notification NPE: `SpyManager.notifyColonyOfficers:1102` adds `perms.getOwner()` (potentially null) into recipient set, then `PlayerList.getPlayer(null)` — vanilla NPE on every spy detection/escape against abandoned colony
- **CRIT** `TaxManager.loadTaxData:1149` only catches `IOException`, not `JsonSyntaxException` — truncated JSON from prior crash mid-write = unhandled RuntimeException out of init = boot-blocked. Sibling `loadLastTaxGenerationTime` has broad catch — asymmetry is the bug.

### D. Edge Cases / State (`adversary_D_edge_cases.md`) — 30 findings (10 Crit, 10 High, 10 Med) + 12 invariants
- **CRIT** All JSON saves non-atomic — no `.tmp + Files.move`, no `.bak`. `kill -9` mid-write = truncated JSON = silently dropped on load (pending costs already debited, occupations gone, treasuries zeroed)
- **CRIT** Won-during-downtime wars give no rewards — wars elapsed during shutdown are marked "expired" and skipped; no end-of-war handler runs; victors get no occupation/reparations, hostile ranks linger (`WarSystem.java:4100-4105`)
- **CRIT** `ColonyDeleted` fanout missing — only `FirstColonyTracker` listens. `SABOTAGE_EFFECTS`, `BRIBED_GUARDS`, `STOLEN_SECRETS_BUFF`, `TREASURIES`, `ACTIVE_VASSALS`, `ACTIVE_OCCUPATIONS`, `extortionImmunity` keep stale colony-ID entries. When MineColonies recycles an ID, the new colony inherits old debuffs.

---

## Cross-Cutting Themes (patterns that appear repeatedly)

1. **Currency payouts don't check success.** Multiple places call `SDMShopIntegration.setMoney(...)` / `removeMoney(...)` / `TreasuryManager.deposit(...)` and ignore the boolean return → coins destroyed on a single failure with no refund. **Recommend:** a single `CurrencyService.transferOrThrow(from, to, amount)` that throws on failure and is the *only* sink.
2. **`isColonyManager()` deref without null-checking the rank.** Repeated in 5+ packets and commands. One pattern, many crashes. **Recommend:** a `Permissions.isManager(player, colony)` static helper that null-safes.
3. **Non-atomic JSON persistence.** Every `saveData()` opens `FileWriter` directly. **Recommend:** a `JsonStore.writeAtomic(path, obj)` helper using temp + `Files.move(ATOMIC_MOVE)`, plus `.bak` rotation.
4. **Missing `ColonyDeletedModEvent` listeners.** Many static maps keyed by colony ID, but only one listener cleans them up. **Recommend:** every static colony-keyed map registers a cleanup hook on a central `ColonyLifecycleHub`.
5. **Plain `HashMap` for cross-thread state.** Pattern repeats in `PvPManager`, `RaidManager`, `TaxManager.colonyTaxMap`. **Recommend:** project-wide audit of all `static` Map fields.
6. **`Math.currentTimeMillis` for cooldown/timer arithmetic** without monotonic fallback. NTP skew breaks every war timer.
7. **Direct `TaxConfig.X.get()` calls** instead of accessor methods (108 sites). CLAUDE.md mandate not enforced.
8. **Permission checks live in client GUI / slash command but not the underlying packet.** GUI bypass = packet bypass. **Recommend:** every packet handler must call the same `requireManager(player, colony)` helper before mutating.
9. **No `WarData` round-trip test.** Restoration constructor drifts silently. **Recommend:** CI test that serializes and deserializes a `WarData` and asserts equality.
10. **Several dead-code packets (`ClaimVassalTributePacket`, `PayDebtPacket`) and commands (`collectoccupation`).** Either delete or finish wiring. Latent live grenades if a future PR re-registers them without the missing checks.

---

## What's Solid (no findings worth flagging)

- `ColonyBuildingUtil` reflection shim is well-designed and the rule is being followed everywhere.
- `JmImpl.java` / `SpyJourneyMapPlugin.java` JourneyMap isolation pattern is exemplary.
- `ReflectionCache` (MineColonies reflection) handles version drift gracefully.
- `TickScheduler` itself is well-designed (task add/cancel during iteration safe, shutdown drains correctly).
- All 25 network packet handlers correctly `enqueueWork` to the server thread.
- `RandomEventType` building-composition checks correctly use `ColonyBuildingUtil`.
- `RaidHistoryCommand` / `RaidRepairCommand` properly gated.

---

---

## Codex Independent Audit — Unique Additions

Codex CLI's third-party pass corroborated ~80% of the Claude swarm's findings (same vassal dupe, same `UpdatePlayerTaxPermissionPacket` zero-auth, same officer-vs-owner mismatch on `BuyInvestmentPacket`, same `PvPManager` non-daemon executor, same `active_wars.json` data loss, same 5+ null-rank deref NPEs). **Unique additions beyond the swarm:**

- **[CRIT — new]** `ClaimTaxPacket` negative-amount unbounded dupe (now Top-12 #1). The most dangerous single exploit in the whole audit.
- **[HIGH — new]** `TreasuryActionPacket.java:37` decoder does `ActionType.values()[buf.readInt()]` without bounds check → malformed packet throws `ArrayIndexOutOfBoundsException` during decode → disconnect/log spam + handler instability.
- **[HIGH — new]** Direct FTB Teams imports in `WarSystem`, `WarEconomyHandler`, `PeaceProposalManager` (now Top-12 #5).
- **[HIGH — new]** GUI tax claim packet ignores `TaxPermissionManager` (now Top-12 #7) — command path checks it; packet path doesn't.
- **[HIGH — new]** Treasury↔tax-balance transfers persist only one ledger — crash window can dupe or destroy money. `TreasuryManager.deposit`/`withdraw` save `warchests.json` but `TaxManager.adjustTax` only mutates in-memory.
- **[MED — new]** Raid treasury cost deducted *before* cooldown/grace/self-raid rejection checks (`RaidManager.java:211`) — rejected raid attempts still burn treasury.
- **[MED — new]** Spy completion rewards (intel book / map) silently lost if attacker logged out — `giveIntelBook`/`giveSpyMap` return on null `ServerPlayer` without queuing.
- **[MED — new]** Spy chunk force-loading not exception-safe — `setChunkForced(true)` not paired with `false` in a `finally` block; can leave chunks forced forever OR unforce chunks another system was keeping loaded.
- **[MED — new]** `ColonyEventListener` raid scan is O(colonies × loaded entities) every 200 ticks on the server thread — tick spikes on large servers.
- **[MED — new]** `AsyncSaveExecutor.shutdownAndFlush` has no reinit path — integrated-server restart in the same JVM (singleplayer → new world) makes every subsequent async save run inline on the caller thread.
- **[MED — new]** `VassalManager.saveData` doesn't create parent dir — first-run vassal save can fail before any other manager creates `config/warntax`.
- **[MED — new]** `TaxManager.adjustTax` / `payTaxDebt` are unbounded signed-int adds with no overflow clamp → repeated large deltas wrap balance.
- **[LOW — new]** `RequestColonyDataPacket.java:216` returns full colony name/ID list to any GUI requester — info leak on PvP/spy servers.
- **[LOW — new]** Several saves use process-relative `config/warntax/` rather than `server.getServerDirectory()` → multi-world or integrated-server worlds collide. Only `VassalManager` anchors correctly.

Full Codex report at `audit/CODEX_INDEPENDENT.md`. Codex's cross-cutting recommendations align with the Claude swarm's: validate every C2S packet's input range + auth, centralize role checks into null-safe helpers, transactional persistence across tax/treasury/vassal ledgers, atomic file writes, persist or reconcile live operations on restart, replace ad hoc executors with `TickScheduler`, declare optional integrations in `mods.toml`.

---

## Files

| Report | Agent |
|--------|-------|
| `defensive_01_taxation.md` | Taxation / Economy / Treasury / Upgrades |
| `defensive_02_war.md` | War / Peace / Persistence |
| `defensive_03_espionage.md` | Spy system (full) |
| `defensive_04_occupation.md` | Occupation / Vassalization / Abandonment |
| `defensive_05_raids_events.md` | Raids / Random Events / Militia / Guards |
| `defensive_06_pvp_arena.md` | PvP Arena |
| `defensive_07_security.md` | Permissions / Commands / Network (security) |
| `defensive_08_compat_threading.md` | Compat / Threading / Performance / Config |
| `adversary_A_exploits.md` | Red team — Exploit Hunter |
| `adversary_B_concurrency.md` | Red team — Concurrency |
| `adversary_C_crashes.md` | Red team — Crash Hunter |
| `adversary_D_edge_cases.md` | Red team — Edge Cases / State |
| `CODEX_INDEPENDENT.md` | Codex CLI independent third-party review |
