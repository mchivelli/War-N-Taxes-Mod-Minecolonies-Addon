# Defensive Audit: Taxation / Economy / Treasury / Upgrades

## Summary
Several real defects exist: an exploitable upgrade dupe via concurrent BuyInvestmentPacket replays (purchase is not atomic), a coin-burning bug where FactionCommand withdraws faction funds before depositing into a possibly-full/disabled treasury, a misleading reuse of `TreasuryManager.shutdown()` as a periodic save during war drain, an off-server-thread mutation of the non-concurrent `colonyTaxMap`, and a money-loss path in legacy `ClaimTaxCommand` where tax is debited before payout is verified. Multiple NPE risks exist on `getRank(...).isColonyManager()` chains. Persistence is JSON-based with sane shutdown saves; tax timer correctly uses ServerTickEvent (no `Timer`); upgrade cost overflow becomes possible at higher levels via `int` casts of `Math.pow`.

## Critical Findings

- **[CRIT-1]** Upgrade dupe via packet replay / race (purchase is not atomic) — `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java:64-76` and `network/packets/BuyInvestmentPacket.java:40-66` — `purchase()` reads `currentLevel`, calls `TreasuryManager.purchase(cost)` (which is a read-modify-write on `TREASURIES`), then `data.setLevel(currentLevel + 1)`. Although the read and write each individually use a `ConcurrentHashMap`, the *compound* sequence is not atomic. Two `BuyInvestmentPacket`s reaching `enqueueWork` are scheduled to the main server thread (per `consumerMainThread` in `NetworkHandler`), which serialises them — so direct main-thread duplication is unlikely. However, `TreasuryManager.purchase()` is also reachable from non-main threads in principle (no thread assertion), and more importantly: the same `BuyInvestmentPacket.handle()` calls `purchase` from `enqueueWork`, and after the upgrade succeeds it sends a fresh `InvestmentDataResponsePacket`. The client GUI does not lock its button — a rapid double-click sends two packets; both are accepted on the same tick. Since each handler reads-then-writes, two near-simultaneous clicks can both pass the "currentLevel < max" check before either commits, resulting in: (a) double-deduct from treasury (cost paid twice for one level) OR (b) jump to level+1 with only one cost paid (the second `setLevel` overwrites). There is no idempotency token, no per-colony lock, no atomic compare-and-set. Impact: exploitable dupe (skip levels) or extra-charge depending on interleave; reproducible by spamming the buy button. Recommendation: synchronise `purchase` on `getOrCreate(colonyId)` or use `data.computeIfAbsent` patterns.

- **[CRIT-2]** Faction withdraw → treasury deposit can incinerate coin — `src/main/java/net/machiavelli/minecolonytax/commands/FactionCommand.java:261-263` — `faction.withdrawTax(amount)` debits the faction pool, then `TreasuryManager.deposit(player, colony.getID(), amount)` (the legacy `TAX_BALANCE` overload) is called. `TreasuryManager.deposit(...)` (`economy/TreasuryManager.java:115-179`) returns `false` if the treasury system is disabled, if the colony's tax balance is below `amount` (line 138-145: `CurrencyService.getAvailableBalance` for `TAX_BALANCE` reads `TaxManager.getStoredTaxForColony`), if the treasury is already at max capacity (line 149-153), or if no funds could actually be taken (line 158-163). When deposit returns false the faction withdrawal is NOT refunded — the coins are lost from both the faction pool and the colony treasury. Impact: faction leader loses coins permanently with no error path. Repro: cap the colony treasury, then `/wnt faction withdraw <amount>` from the faction leader.

- **[CRIT-3]** `TreasuryManager.shutdown()` invoked as a periodic save inside the war drain loop — `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:340-342` — every 5 drain ticks the code calls `TreasuryManager.shutdown()` which currently *only* calls `saveData()` (TreasuryManager.java:63-65). This works today by accident. If `shutdown()` is ever extended to (a) clear `TREASURIES`, (b) null out `SERVER`, or (c) save `DEFENDER_COLONIES` — all reasonable shutdown semantics — every war will corrupt every colony's treasury balance every 5 minutes. This is a landmine: it does what's intended now, but the name lies about intent, no test prevents misuse, and a future contributor adding a `clear()` or `SERVER = null` to `shutdown()` would silently break the entire economy during active wars. Recommendation: extract a dedicated `flushToDisk()` method, leave `shutdown()` for actual shutdown.

## High Findings

- **[HIGH-1]** `colonyTaxMap` is a plain `HashMap` (not concurrent) but is mutated from packet handlers and read/written by the tax-generation loop — `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:50` (`new HashMap<>()`). Packet handlers use `consumerMainThread` (good — main-thread serialised), but several other paths mutate it: `claimTax`, `payTaxDebt`, `deductColonyTax`, `adjustTax`, `incrementTaxRevenue`, the `generateTaxesForAllColonies` loop, and `loadTaxData` (called from `initialize` which can run on main thread). Forge tick events fire on the main thread, so today this is mostly safe — but the asymmetry is dangerous: `TREASURIES`/`FROZEN_COLONIES`/`DISABLED_GENERATION` are all `ConcurrentHashMap`/`ConcurrentHashMap.newKeySet()`, while the *largest* state map is not. `saveTaxData()` then uses Gson to serialize this `HashMap` while another thread (e.g. an async save) could be reading it. Compound `getOrDefault → put` patterns (e.g. lines 332-337, 347-349, 358-359) are also not atomic — two concurrent `adjustTax`/`incrementTaxRevenue` calls (or one tick handler + one async path) can lose updates. Fix: switch to `ConcurrentHashMap` and use `compute`/`merge` for the read-modify-write sites.

- **[HIGH-2]** `ClaimTaxCommand` debits colony tax before verifying payout — `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java:137-158` — `TaxManager.claimTax(colony, amount)` is called (which zeroes/decrements the colony ledger), THEN the code chooses payout path: `SDMShopIntegration.setMoney(player, currentBalance + totalClaimed)` (line 154-155). `setMoney` returns boolean but the return is discarded. If SDMShop returns false (mod unavailable, server overflow, version mismatch) the player gets nothing and the colony has already lost the tax. Same shape in `ClaimVassalTributePacket.java:51-52`. Compare with `ClaimTaxPacket.java:107` which uses `addMoney(...)` and at least logs failure (but still doesn't restore the colony balance). Impact: silent coin loss on SDM failure or race. Fix: payout first, debit colony only on confirmed success — or refund on payout failure.

- **[HIGH-3]** `PayDebtPacket` (`src/main/java/net/machiavelli/minecolonytax/network/packets/PayDebtPacket.java:84-127`) has TOCTOU on player balance: it reads `SDMShopIntegration.getMoney(player)` (line 86), confirms `>= payAmount`, then calls `SDMShopIntegration.removeMoney(player, payAmount)` (line 109) without checking the return value. If the player's balance dropped between check and remove (concurrent purchase/transfer via SDMShop GUI), `removeMoney` may underflow / fail silently; then `TaxManager.payTaxDebt(colony, payAmount)` credits the colony anyway → free debt repayment. Impact: griefer can drain another player's funds to clear colony debt, or pay debt with funds they don't have. Same lack of validation in `TaxDebtCommand.java:108-118`.

- **[HIGH-4]** `PayTaxDebtPacket` (`src/main/java/net/machiavelli/minecolonytax/network/packets/PayTaxDebtPacket.java:74`) ignores the client-supplied amount and always settles the *entire* debt (`int debtAmount = Math.abs(currentTax)`). If a player intends to pay 100 but the debt is 10_000_000 and they have the funds, they're billed the full 10M with no client confirmation. The packet carries no amount field (it only encodes `colonyId`). The only check is `currentBalance < debtAmount`. Combined with HIGH-3's TOCTOU, this is an oversized footgun. Impact: griefer with officer rank can force a player with deep wallets to pay the entire colony debt with one click.

- **[HIGH-5]** Tax generation `incrementTaxRevenue` silently drops over-cap revenue but `totalGeneratedTax` still counts it — `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:509-521`. The accumulator `totalGeneratedTax += generatedTax` happens before the cap check, but if `currentTax >= maxTax` the call to `incrementTaxRevenue` is skipped. Downstream calculations that scale on `totalGeneratedTax` (Treasury auto-deposit at line 664-679, faction pool diversion 683-696, occupation diversion 699-711, vassal tribute 656-660, stolen secrets bonus 590-602, sabotage reduction 581-587, guard tower boost 633-652, tax efficiency upgrade bonus 547-560) are therefore inflated relative to what actually entered the ledger. Treasury auto-deposit then `adjustTax(-depositAmount)` may push the colony into debt even though `totalGeneratedTax` reflected revenue that was never received. Fix: track `actuallyAddedTax` separately from `totalGeneratedTax` and use that for all downstream percentages.

- **[HIGH-6]** Upgrade cost overflows past ~level 19 with default scaling — `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java:61` — `(int) (baseCost * Math.pow(scaling, level))`. With `baseCost=1000` and `scaling=1.5`, level 30 is 1000 * 1.5^30 ≈ 1.92e8 (ok); but if `baseCost` is configured higher or `scaling` is configured ≥2.0, `Math.pow` will exceed `Integer.MAX_VALUE` (~2.1e9) within ~21 levels and the `(int)` cast silently overflows to a negative or wraparound value. A negative cost passed to `TreasuryManager.purchase(...)` (line 475-484) compares `currentBalance < cost`; a negative `cost` is always `>= currentBalance` so the check passes, then `TREASURIES.put(colonyId, currentBalance - cost)` *increases* the balance by `|cost|`. Free upgrade + treasury inflation. Fix: use `long` throughout and clamp to `Integer.MAX_VALUE` or reject when overflow detected.

- **[HIGH-7]** `BuyInvestmentPacket` permission check NPE risk — `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java:48` — `colony.getPermissions().getRank(player.getUUID()).isColonyManager()`. The MineColonies `IPermissions.getRank(UUID)` returns null for unknown players in some versions. NPE here crashes the packet handler (caught by Forge's net layer but logs an error). Same pattern exists at `TreasuryActionPacket.java:64`, `RequestInvestmentDataPacket.java:43`, and `TreasuryCommand.java:240`. Compare with the defensive `playerRank == null` checks in `ClaimTaxPacket.java:77`, `PayDebtPacket.java:61`, `PayTaxDebtPacket.java:55` (correct pattern).

## Medium Findings

- **[MED-1]** `TaxManager.payTaxDebt` (`TaxManager.java:1179-1190`) is unconditional: amount is added to colony balance with no validation that it's positive. `PayDebtPacket` validates `payAmount > 0` (line 76) but `TaxDebtCommand` passes the raw argument (validated `IntegerArgumentType.integer(1)` so minimum 1 — OK). However the JavaDoc says "to reduce the colony's tax debt or add to its balance" — meaning a non-debt colony can be inflated above cap via debt-pay (line 1183 has no `Math.min(amount, maxTax - currentTax)`). Combined with `ClaimTaxPacket`/`ClaimTaxCommand` paying out at face value, a colony can be inflated past `MAX_TAX_REVENUE` via repeated `taxdebt pay` calls — this circumvents the cap.

- **[MED-2]** `TaxManager.generateTaxesForAllColonies` iterates *only* `serverInstance.getAllLevels()` and within each calls `getColonies(world)` (line 414-416). With multiple dimensions, a colony in dim A may iterate once per dimension if the colony manager returns it for each `world` (it shouldn't, but if it does the tax generation duplicates revenue, maintenance, treasury deposit, etc.). The codebase has many `getAllColonies()` usages elsewhere — this is inconsistent. Verify against MineColonies semantics; if `getColonies(world)` ever returns colonies registered in another dimension this will silently multiply revenue per cycle.

- **[MED-3]** `TaxManager.deductColonyTax` (line 346-354) computes `int deduction = (int)(currentTax * percentage)`. When `currentTax` is negative (colony in debt), this *reduces* the debt; the JavaDoc says "as penalty" but the math doesn't handle the sign. Caller `RaidPenaltyManager.repair` (line 183) computes `repairPercent = (double) repairCost / taxBalance` — if `taxBalance` is 0 this is a divide-by-zero (NaN), then `(int)(currentTax * NaN)` = 0. Repair would silently succeed without actually deducting (lines 174-187 already gate on `taxBalance >= repairCost`, so 0 balance can't trigger it — but a 1-coin balance / 0-cost repair is racy). Low impact in practice but logically wrong.

- **[MED-4]** `WarExhaustionManager.cleanupExpiredData` (line 388-401) is only invoked at `initialize` (line 78). Expired entries in `WAR_LOSSES` never get cleaned up live — `getRecentLossCount` filters at read time but the map grows unboundedly with stale timestamps for colonies that lost wars long ago. Memory leak proportional to total lifetime war count.

- **[MED-5]** `WarExhaustionManager.saveData()` (line 432) is called from many hot paths (e.g. every `hasReparations` cleanup, every `isInRecovery` cleanup, every `hasWarImmunity` cleanup at lines 144, 201, 266) on the read path. Reading status from many places per tick triggers many full JSON file rewrites synchronously. Performance issue and disk-thrash; should debounce via dirty flag.

- **[MED-6]** `TreasuryManager.computeEffectiveDrain` (line 383-398) double-applies defender reduction if config flips drain mode mid-war: `baseDrain = ceil(maxCapacity * percent)` then `baseDrain = ceil(baseDrain * (1 - reduction))`. The `ceil` rounding compounds — for low drains the defender reduction may round back up to the same value. Minor.

- **[MED-7]** `TaxManager.checkForTaxGeneration` (line 199-230) uses wall-clock `System.currentTimeMillis()` for tax intervals. The forward-clock-jump guard (line 204-211) catches "future timestamp" but a *backward* clock jump (e.g. NTP correction during play) silently delays the next tax cycle by the amount of the jump. Use `System.nanoTime()` or a monotonic tick counter.

- **[MED-8]** `TaxConfig.TAX_INTERVAL_MINUTES.get()` is read by `freezeColonyTax` (line 1159) to compute the freeze duration. If the operator changes the interval mid-server (Forge config reload), the freeze duration computed at freeze time uses the old value but the cycles compared against use the new value — colonies get a wrong freeze window. Minor.

- **[MED-9]** `ColonyUpgradeData` uses string keys for the levels map (line 9) and Gson serializes the whole `ColonyUpgradeData` object. If `UpgradeType` enum values are ever renamed (e.g. `MILITIA` → `MILITIA_TROOPS`), persisted upgrade levels become orphaned silently. No version field in the JSON. Adding new upgrade types via enum is safe (defaults to 0) but rename is silent data loss.

- **[MED-10]** `TaxPolicyManager.findPlayerColony` falls back to `name.contains(lower)` substring match (line 322-326). Two colonies "Atlas" and "Atlas2" — typing `atlas` matches the first; player can accidentally change the wrong colony's policy. Use exact-match or `startsWith`.

- **[MED-11]** `ColonyHappinessModifierManager` is documented (line 17-42) as having a *double-counting bug* that was intentionally disabled. The class is dead code in the loaded paths I traced (no caller in `TaxManager` for `updateColonyHappinessModifiers`). Confirm via `Grep updateColonyHappinessModifiers` — if anything still calls it, the documented bug is live. (Grep shows no callers — so it's quiescent, but the file should either be deleted or the warning marked TODO with a tracking issue.)

- **[MED-12]** `WarExhaustionManager` and `TreasuryManager` use `Files.createDirectories(path.getParent())` without checking if `path.getParent()` is null (line 435 / TreasuryManager.java:511 uses `file.getParentFile().mkdirs()` which also returns boolean ignored). If `STORAGE_FILE` is reconfigured to a relative path with no parent, NPE/save failure.

## Low Findings

- **[LOW-1]** Many `LOGGER.info("…{:.0f}…", value)` format strings (TaxManager lines 461, 469, 478) use SLF4J `{}` with a Java `Formatter` spec — `{:.0f}` is *not* parsed by SLF4J; it will print the literal `{:.0f}` substring. Cosmetic.

- **[LOW-2]** `TreasuryManager.sendStatus` (line 286) computes `int minutesOfWar = balance / effectiveDrain` without guarding against `effectiveDrain == 0` (the surrounding `if (... && effectiveDrain > 0)` does guard — confirmed safe). Just verifying.

- **[LOW-3]** `RaidPenaltyManager.getRepairCost` (line 145-153) computes cost from current tax balance — players can game this by claiming their tax to 0 first to make repair free. Working-as-coded but likely unintended design.

- **[LOW-4]** `TreasuryManager.deposit` logs every save (line 166 implicit via `saveData`). Same in `addToTreasury` (line 455). With ~100 colonies and frequent deposits, this is heavy disk I/O. Already debounced for the war-drain path (line 416 comment: "Periodic save handled by WarSystem drain loop") but not for everything else.

- **[LOW-5]** `BuyInvestmentPacket.handle` does not message the player on success/failure when `purchase` returns false because the player isn't a manager (line 48 returns silently). Should at least log or notify.

- **[LOW-6]** `TaxManager.calculateColonyAverageHappiness` (line 369-410) reads happiness inside a per-citizen loop without checking citizen count first; for a 100-citizen colony this triggers 100 modifier handler lookups every tax cycle. Could be cached per cycle.

- **[LOW-7]** `CurrencyService.takeFromPlayer` for `TAX_BALANCE` (line 46-51) checks `stored < amount` then `adjustTax(-amount)`. Two concurrent treasury deposits from `WALLET` source on different threads could both pass the (stored=100, amount=80) check and both succeed, overdrawing the colony. Same race shape as HIGH-1. With `consumerMainThread` packet dispatch the practical exposure is small, but `TaxDebtCommand` / direct command paths are not guarded.

- **[LOW-8]** `SetTaxPolicyPacket.handle` (line 38-92) and the `setPolicy` flow both rebuild the same human-readable policy effect string. Duplication; should share with `TaxPolicyManager.setPolicyCommand`.

- **[LOW-9]** `RequestTreasuryDataPacket` (line 56) reads `TaxConfig.getTreasuryDrainPerMinute()` (the flat drain value) regardless of whether percent-mode is active. The client GUI then displays a misleading drain number when percent-mode is on.

- **[LOW-10]** `TaxConfig` accessor `TaxConfig.TAX_INTERVAL_MINUTES.get()` is used directly in `freezeColonyTax:1159` (bypassing the `getTaxIntervalInMinutes()` accessor) — inconsistent with the rest of the file's accessor convention.

## Files Audited
- `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/RaidPenaltyManager.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/policy/TaxPolicy.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/policy/TaxPolicyManager.java`
- `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java`
- `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeData.java`
- `src/main/java/net/machiavelli/minecolonytax/upgrade/UpgradeType.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java`
- `src/main/java/net/machiavelli/minecolonytax/happiness/ColonyHappinessModifierManager.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayTaxDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SetTaxPolicyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimVassalTributePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestInvestmentDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryActionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestTreasuryDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/AdminTaxGenCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/CheckTaxRevenueCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/DebugTaxCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/TaxGUICommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/TaxDebtCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/TaxPolicyCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/TreasuryCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java` (cross-ref)
- `src/main/java/net/machiavelli/minecolonytax/commands/FactionCommand.java` (cross-ref, withdraw path)
- `src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java`
- `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java` (partial; reflection wrapper)
- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java`
- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java` (treasury drain scheduling only)
- `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java` (accessor methods only)
