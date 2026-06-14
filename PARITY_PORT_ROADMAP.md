# NeoForge 1.21.1 ⇄ Forge 1.20.1 Parity Port Roadmap

Audit date: 2026-06-13. Source of truth: Forge `1.20.1` branch (`C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon`).

The NeoForge port (5.0.0) was branched **before** the Forge "Siege SMP" body of work and several
later systems. This document is the authoritative gap list and the dependency-ordered plan to bring
NeoForge to feature parity.

## Fundamental API differences (apply to EVERY ported file)

| Concern | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Config spec | `ForgeConfigSpec` | `ModConfigSpec` |
| Event bus | `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| Networking | `SimpleChannel` packets (`*Packet`) | `CustomPacketPayload` (`*Payload`) + `ModNetworking` |
| Per-player data | Capabilities (`PlayerWarDataCapability`) | Attachments (`PlayerWarDataAttachment`) |
| Colony treasury | `TreasuryManager` | `WarChestManager` (+ `TaxManager`) |
| Player wallet | `CurrencyService` (Source enum) | `SDMShopCompat` / `SDMShopIntegration` (reflection) |
| Deferred tick work | `WorldTickHandlerMixin` | `TickScheduler` (already present) |
| Block/Entity registration | Forge `DeferredRegister` | NeoForge `DeferredRegister` (different holders) |

**Rule:** ported systems are rewired onto Neo's existing economy (`WarChestManager`/`TaxManager`/`SDMShopCompat`),
NOT a forked `TreasuryManager`/`CurrencyService`.

## True feature gaps (excluding files that are just API-renamed reimplementations)

The "26 missing network files" are almost all the `*Packet` → `*Payload` rename and are NOT real gaps.
Real missing FEATURES, dependency-ordered:

### Stage 1 — War vassalization parity (DONE in this commit)
- Config: `WarVassalizationTreasuryGrabPercent`, `WarVassalizationPlayerBalanceGrabPercent`
- `VassalManager.forceVassalize(...)` (forced, non-proposal)
- `WarSystem` war-win branch: vassalize when transfers off + one-time "huge money" grab
  (war chest % + player wallet %)
- Note: Neo `VassalRelation` has no expiry field yet → forced vassalization is permanent-until-revoked.
  Adding `WarVassalizationDurationHours` expiry is a Stage 1b follow-up (extend VassalRelation + expiry check in tick/handleTaxIncome).

### Stage 2 — Foundations for siege (blocking besiege)
- `permissions/PermissionSnapshot.java`, `permissions/ColonyTierGuard.java`, `permissions/PermissionsHealthCheck.java`
- `util/AsyncSaveExecutor.java`
- `militia/MilitiaSpawner.java` → map onto existing `militia/CitizenMilitiaManager` + `MilitiaAttackGoal`

### Stage 3 — Upgrade / investment system
- `upgrade/UpgradeType.java`, `upgrade/ColonyUpgradeData.java`, `upgrade/ColonyUpgradeManager.java`
- Payloads: `BuyInvestment`, `RequestInvestmentData`, `InvestmentDataResponse` (as `*Payload` + register in `ModNetworking`)
- `gui/book/InvestmentsPage.java`
- Economy: fund via `WarChestManager`

### Stage 4 — Besiege system (the big one, ~1,400 lines)
- `besiege/BesiegeManager.java` (+ recent feature work: chest access, multi-attacker, online, shared spoils, min-attackers)
- `besiege/BesiegeDamageShieldHandler.java`, `besiege/BesiegeEntityInteractHandler.java`
- Tick: drive via `TickScheduler` (not a mixin)
- Economy: spoils via `WarChestManager`
- `event/BlockInteractionFilterHandler`: add besiege container-deny + vassal-owner-lockout (config-gated, matching Stage-1 keys `BesiegeAllowChestAccess`, `VassalLockOutFormerOwner`, etc.)

### Stage 5 — Siege victory objectives
- `siege/ModSiegeBlocks.java`, `siege/SiegeBannerBlock.java` (custom block — NeoForge registry + blockstate/model assets)
- `siege/PlantTheBannerObjective.java`, `siege/TownHallDemolitionObjective.java`, `siege/WarBlockLedger.java`
- `network/EntityGlowPacket` → Neo already has `EntityGlowPayload`
- Explosion war-awareness: Forge uses a mixin; Neo needs an equivalent (event-based if possible)

### Stage 6 — Remaining/optional
- `compat/`: `ExplosiontCompat`, `EasyFactionsBridge`/`EasyFactionsPermissionSync`, `FtbTeamsCompat(+Impl)`
- `db/WarStatsDB.java` (Neo has `webapi/` instead — reconcile)
- `commands/DebugTaxCommand`, `commands/TreasuryCommand` (→ WarChest equivalent), `events/random/EventLogEntry`, `espionage/SpyClientHandler`, `pvp/PvPStatsPersistence`

## Config key parity additions (added as each stage lands)
Besiege keys (`BesiegeAllowChestAccess`, `VassalLockOutFormerOwner`, `BesiegeMinAttackers`,
`BesiegeShareSpoils`, `BesiegeRequireOnline`, `BesiegeOfflineGraceMinutes`) land with Stage 4, not before
(they would be dead config otherwise).

---

## STATUS (updated 2026-06-14)

| Stage | Status | Commit |
|-------|--------|--------|
| 1 — War vassalization + money grab | ✅ DONE | `90f7df7` |
| 2 — Foundations (tier guard, perm snapshot, async save) | ✅ DONE | `848daed` |
| 3 — Upgrade/investment backend | ✅ DONE (GUI/payloads = 3b, pending) | `6bb0452` |
| 4 — Besiege system (+ multiplayer/online/spoils features) | ✅ DONE & WIRED | `04379d6`, `c52550c` |
| 5 — Siege victory objectives (banner/demolition/ledger) | ✅ DONE & WIRED | `04379d6`, `c52550c` |
| 6 — Compat (Explosiont, EasyFactions, FtbTeams) | ✅ FILES DONE (5 config keys pending) | `04379d6` |

**`gradlew build` is GREEN → `WarNTaxes-NeoForge-5.0.jar`. MineColonies API guard passes.**

### Stage 3b — Investment GUI + network (DONE) — commit `67e430e`
- `InvestmentsPage` book tab (gated on `isUpgradesEnabled`), 3 payloads
  (`RequestInvestmentDataPayload`/`InvestmentDataResponsePayload`/`BuyInvestmentPayload`),
  registered in `ModNetworking`, client handler + screen wiring, `investments_icon` asset.
- **Verified in-client:** payloads auto-register, config parses, no crash.

### Stage 6 config (DONE) — commit `67e430e`
- Real `TaxConfig` keys for `EnableEasyFactionsIntegration` / `EasyFactionsSyncIntervalTicks` /
  `EasyFactionsMemberRank` / `PromoteEasyFactionsOfficers` / `DeferRestorationToExplosiont`;
  compat stubs replaced with the real accessors.

### Misc files — ASSESSED: 5 of 6 already covered by Neo equivalents (NOT gaps)
- `commands/TreasuryCommand` → Neo `commands/WarChestCommand` ✅
- `db/WarStatsDB` → Neo `webapi/` (WebAPIServer + WarStatsAPIData + PlayerDataCache) ✅
- `espionage/SpyClientHandler` → Neo espionage pkg (SpyEntityRenderer/SpyIntelBookGenerator/SpyMapGenerator) ✅
- `pvp/PvPStatsPersistence` → Neo `pvp/persistence/` + `pvp/model/PlayerPvPStats` ✅
- `commands/DebugTaxCommand` → Neo `/wnt debugtax` (+ wardebug/debugguards/debugbossbar) ✅
- `events/random/EventLogEntry` → **the one genuine remaining gap.** NOT a misc file — it backs a
  player-facing random-event-HISTORY display on the colony book page (RandomEventManager EVENT_LOG +
  ColonyDataResponse field + ColoniesPage rendering + DismissEvent payload). Porting it is an invasive
  change to Neo's *working* colony payload/page. Deferred as an optional secondary-display feature.

### Optional follow-ups (non-blocking)
- **Stage 1b**: `WarVassalizationDurationHours` expiry (extend `VassalRelation` + expiry check; currently permanent-until-revoked).
- **Random-event-history display** (`EventLogEntry`, see above).
- Distinct `investments_icon.png` art (currently a copy of `economy_icon.png`).
- wiki/CHANGELOG for the NeoForge Siege SMP feature set.

## 1:1 closeout (commit `158c7f1`)
The last two genuine feature gaps (everything else was NeoForge-renamed equivalents):
- **Random-event-history display** — `EventLogEntry` + `DismissEventPayload` + `RandomEventManager.EVENT_LOG`
  (persisted) + `ColonyDataResponsePayload` 3rd field + `RequestColonyDataPayload.buildEventLog` +
  real `ColoniesPage.renderEventsView` (rows/tooltip/dismiss/scroll). Server-verified.
  *(Follow-up: raid/active-state + structured-war rows in the Events view — Neo `HistoryManager` lacks structured wars.)*
- **PvP crash-recovery persistence** — `PvPStatsPersistence` (atomic JSON; login-restore self-registers) +
  `PvPManager.PVP_STATS_FILE`/`playerOriginalPositions` + save-sites in `PvPBattleManager` + start/stop in `MineColonyTax`. Server-verified.

**Architecture note:** `WarStatsDB` (Forge MySQL push for a companion website) is intentionally NOT
ported — Neo replaced it with the `webapi/` HTTP API (`WebAPIServer`). Same goal, different transport.
Port `WarStatsDB` only if you specifically need the MySQL push for an existing website backend.

## FINAL: Siege SMP parity port is functionally COMPLETE
`gradlew build` green → `WarNTaxes-NeoForge-5.0.jar`; MineColonies API guard passes; **server AND
client boot clean, player joins world, all new config sections parse, investment networking registers.**
6 commits: `90f7df7`, `848daed`, `6bb0452`, `04379d6`, `c52550c`, `67e430e`.
