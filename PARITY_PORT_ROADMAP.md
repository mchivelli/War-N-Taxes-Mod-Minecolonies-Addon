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
