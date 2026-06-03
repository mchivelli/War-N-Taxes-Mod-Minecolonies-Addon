# Codex Independent Audit

## Executive Summary

Static audit only. No Gradle build or code execution was performed. I reviewed `CLAUDE.md`, `AGENTS.md`, and the source under `src/main/java/net/machiavelli/minecolonytax/`, with focused review of taxation, war, espionage, occupation, vassalization, raids, random events, PvP arena, permissions, networking, persistence, MineColonies/JourneyMap compatibility, and server-thread scheduling.

The highest-risk issues are economy exploits in tax claiming and vassal tribute claiming, client packet authorization gaps, packet decode/handler crashes from malformed or unauthorized input, treasury/tax persistence split-brain on crash, and lifecycle/scheduling issues in PvP. Several systems also persist live gameplay state incompletely, especially tax permissions, raids, and war restoration after downtime.

## Critical Findings

- [CRIT-1] Negative tax claim packet mints colony tax balance — `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java:36` and `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:303` — Root cause: the C2S packet reads a raw signed `amount` from the client and forwards all non-`-2` values to `TaxManager.claimTax`; `claimTax` treats any value other than `-1` as a specific claim and computes `claimedAmount = Math.min(amount, storedTax)`, so a negative amount becomes a negative claim and line 304 writes `storedTax - claimedAmount`, increasing the ledger. Impact: any colony manager/officer able to send a crafted packet can inflate a colony tax balance without paying anything, then claim the inflated balance with `amount=-1`; this is an unbounded currency/tax dupe. Repro: as a colony manager, send `ClaimTaxPacket(colonyId, -1000000)` repeatedly, then send `ClaimTaxPacket(colonyId, -1)`.

- [CRIT-2] Vassal tribute is awarded twice on claim — `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:728`, `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:732`, `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java:69`, `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java:107`, and `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimVassalTributePacket.java:52` — Root cause: `VassalManager.claimVassalTribute` deducts tribute from the vassal colony and credits the overlord colony tax ledger, but both packet entry points then also pay the returned tribute amount directly to the player wallet/items. Impact: one tribute claim creates value in the overlord colony ledger and also gives the same value to the player, duplicating money; the `ClaimTaxPacket` path duplicates in both SDMShop and item-currency mode, while `ClaimVassalTributePacket` duplicates in SDMShop mode. Repro: make colony A a vassal of player B, accumulate vassal tax, then claim tribute through the GUI packet; observe the overlord colony tax balance increase and the player wallet/item payout increase by the same amount.

## High Findings

- [HIGH-1] Per-player tax permission packet has no authorization check — `src/main/java/net/machiavelli/minecolonytax/network/packets/UpdatePlayerTaxPermissionPacket.java:38` — Root cause: the handler only checks `context.getSender()` for null, then calls `TaxPermissionManager.setPlayerClaimPermission(colonyId, playerId, allowed)` at line 48 with client-supplied colony/player IDs; it never resolves the colony, validates sender ownership/management permission, or verifies that `playerId` belongs to that colony. Impact: any connected client can alter live tax-claim permissions for any colony/player pair. Repro: send `UpdatePlayerTaxPermissionPacket(victimColonyId, arbitraryUuid, true/false)` from a non-member account.

- [HIGH-2] Officer tax-claim restrictions are ignored by the main claim packet — `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java:75` and `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java:104` — Root cause: the GUI/network claim path only checks `Rank.isColonyManager()` and never calls `TaxPermissionManager.canPlayerClaimTax`, while the command path does call the policy check. Impact: owners can disable officer tax claims in the permission system, but any officer/manager can still claim taxes via the GUI packet. Repro: configure an officer to be denied tax claims, then have that officer send `ClaimTaxPacket(colonyId, -1)`.

- [HIGH-3] Officers can buy investments and spend treasury through the packet despite owner-only command policy — `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java:48`, `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java:57`, `src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java:3833`, and `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java:64` — Root cause: the packet authorizes any `isColonyManager()` rank and `ColonyUpgradeManager.purchase` has no player/owner validation, while the command path explicitly rejects non-owners. Impact: officers can spend colony treasury on upgrades that the command UI says only owners may buy. Repro: as an officer but not owner, send `BuyInvestmentPacket(colonyId, "TAX_EFFICIENCY")`; treasury is deducted if funds are available.

- [HIGH-4] Treasury tax-balance transfers persist only one side, enabling crash dupes/losses — `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:157`, `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:166`, `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:231`, `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:243`, `src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java:49`, `src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java:73`, and `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:356` — Root cause: treasury deposit/withdraw saves `warchests.json` after changing treasury, but tax-balance source/destination changes use `TaxManager.adjustTax`, which only mutates the in-memory tax map and does not save tax data. Impact: a crash after treasury save but before a later tax save can duplicate deposited tax into both treasury and tax ledger, or lose withdrawn tax. Repro: deposit 100 from tax balance to treasury, kill the server process before orderly shutdown, restart, and compare persisted treasury with unchanged `colonyTaxData.json`.

- [HIGH-5] Invalid treasury action ordinal can crash packet decoding — `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryActionPacket.java:37` — Root cause: the decoder uses `ActionType.values()[buf.readInt()]` without a range check, while the source enum ordinal is range-checked at lines 41-45. Impact: a malformed C2S packet with action ordinal `-1` or a large value throws `ArrayIndexOutOfBoundsException` during decode, causing disconnect/log spam and potential server packet-handler instability. Repro: send a `TreasuryActionPacket` byte stream with invalid action ordinal.

- [HIGH-6] Several C2S handlers dereference nullable MineColonies ranks — `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java:48`, `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestTreasuryDataPacket.java:50`, `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestInvestmentDataPacket.java:43`, `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryActionPacket.java:64`, and `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java:48` — Root cause: handlers call `colony.getPermissions().getRank(player.getUUID()).isColonyManager()` without null checks. Impact: a non-member or otherwise unranked sender can trigger `NullPointerException` in server packet work for target colony IDs. Repro: from an account with no rank in a target colony, send request/action/buy packets for that colony.

- [HIGH-7] Direct FTB Teams imports conflict with optional runtime detection and missing metadata — `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:10`, `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:84`, `src/main/java/net/machiavelli/minecolonytax/event/WarEconomyHandler.java:4`, `src/main/java/net/machiavelli/minecolonytax/peace/PeaceProposalManager.java:6`, `src/main/resources/META-INF/mods.toml:44`, and `build.gradle:160` — Root cause: code tries to detect FTB Teams with `Class.forName`, but classes with direct `dev.ftb.mods.ftbteams.*` type references can fail class loading before guards if FTB Teams is absent; `mods.toml` declares only Forge and Minecraft dependencies, not FTB Teams or MineColonies. Impact: installs without FTB Teams may crash or fail to load war/economy classes, and Forge cannot enforce required MineColonies load ordering. Repro: run the mod without FTB Teams present and load a code path/class that references `WarSystem`, `WarEconomyHandler`, or `PeaceProposalManager`.

- [HIGH-8] Active war restoration can silently discard expired/skipped wars and delete the only save — `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4004`, `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4042`, `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4065`, and `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4099` — Root cause: `loadAndResumeActiveWars` counts skipped wars, then deletes `active_wars.json` regardless; `resumeWarFromSave` returns false for missing colonies and wars whose duration expired during downtime without resolving the war outcome. Impact: a server restart can erase live wars, timers, resolution rewards/penalties, and cleanup state. Repro: start a war, stop the server until its duration passes, then restart; the war is skipped and the save file is deleted.

- [HIGH-9] PvP battle scheduler uses a non-daemon executor that is never shut down — `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:59`, `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:72`, and `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:723` — Root cause: `Executors.newScheduledThreadPool(1)` creates non-daemon threads and no shutdown call exists for `BATTLE_END_SCHEDULER`; the project already has `TickScheduler` for server-thread scheduling. Impact: PvP tasks can keep the JVM alive after server stop or execute stale callbacks against a later server lifecycle. Repro: start a PvP battle or defeat a player, then stop the server while delayed restoration/end tasks are pending.

## Medium Findings

- [MED-1] Tax permission settings are not persisted — `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java:19`, `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java:103`, and `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java:111` — Root cause: permissions live only in static maps; getters/loaders exist, but source search found no save/load integration except packet setters. Impact: officer and per-player tax permission configuration is lost on restart. Repro: change officer/player tax claim permissions, restart the server, and observe defaults return.

- [MED-2] Active raids and raid grace periods are not persisted — `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:42`, `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:45`, and `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:53` — Root cause: active raids and attacker/defender grace windows are static maps with no save/load path in `RaidManager`. Impact: restarting mid-raid clears raid state, cooldowns, and defender protection. Repro: start a raid, stop/restart before completion, then attempt another raid immediately.

- [MED-3] Raid treasury cost is deducted before later rejection checks — `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:211`, `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:230`, `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:240`, `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:253`, and `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java:269` — Root cause: treasury payment happens before attacker cooldown, defender grace, and self-raid checks. Impact: rejected raid attempts can still burn treasury funds. Repro: enable raid treasury costs, place the attacker on cooldown or the defender under grace, then attempt a raid and observe treasury deduction before command failure.

- [MED-4] PvP disconnect mid-battle can leave the disconnected player unrestored — `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:231`, `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:840`, `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:747`, and `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:749` — Root cause: disconnect cancels the battle via `endBattle`, but `endBattle` only restores players returned by `server.getPlayerList().getPlayer`; the disconnected participant is skipped and their original gamemode/position cleanup is not applied. Impact: reconnecting players can remain at the arena or in stale PvP state, with stale `playerOriginalGameModes` entries. Repro: join a PvP battle, disconnect before restoration, then reconnect.

- [MED-5] Spy completion rewards are lost if the attacker is offline — `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:551`, `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:574`, `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:774`, and `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:906` — Root cause: mission completion moves the mission to completed and immediately calls `giveIntelBook`/`giveSpyMap`; both helpers return when `ServerPlayer` is null and do not queue rewards. Impact: players who log out before spy return/completion permanently lose intel book/map rewards. Repro: deploy a spy, log out before ETA/completion, wait for completion, then log back in.

- [MED-6] Spy chunk force-loading is not exception-safe and may unforce another owner's chunk — `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:1041`, `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:1052`, and `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:1063` — Root cause: `setChunkForced(true)` is not paired with `false` in a `finally` block, and the code unconditionally clears force-loading without tracking whether WnT owned the force. Impact: an exception during spy spawn can leave chunks forced forever; conversely, WnT can unforce chunks kept loaded by another system/admin. Repro: corrupt a saved mission attacker UUID or trigger an entity-spawn exception after line 1041; or pre-force the target chunk by another system, then let spy spawn clear it.

- [MED-7] Random event persistence performs synchronous full-file writes during tax cycles — `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:109`, `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:126`, and `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:832` — Root cause: every colony tax cycle calls `saveData`, which builds the full JSON tree and writes it via `Files.newBufferedWriter` on the server thread at line 911. Impact: many colonies/events can cause tick stalls during tax generation. Repro: enable random events on a server with many colonies and trigger a global tax cycle.

- [MED-8] Entity raid scans are O(colonies * loaded entities) on the server thread — `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java:44`, `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java:103`, `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java:134`, and `src/main/java/net/machiavelli/minecolonytax/raid/EntityRaidManager.java:872` — Root cause: every 200 ticks, the listener iterates all colonies and for each colony iterates `level.getEntities().getAll`; active raid counting also scans all loaded entities. Impact: large servers with many colonies/entities can get regular main-thread spikes. Repro: load many colonies and recruited entities, enable entity raids, and profile the 10-second scan interval.

- [MED-9] Multiple critical JSON saves are non-atomic direct overwrites — `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:1128`, `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:513`, `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3994`, and `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:911` — Root cause: important state is written directly to the target file, not to a temp file followed by atomic rename. Impact: process crash/power loss during write can truncate or corrupt tax, treasury, war, or event state. Repro: kill the JVM during one of these save operations and inspect the target JSON file.

- [MED-10] AsyncSaveExecutor cannot recover after server stop in the same JVM — `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java:33`, `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java:42`, `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java:88`, and `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java:405` — Root cause: `shutdownAndFlush` sets static `running=false` and shuts down the static executor, with no initialize/reset path on the next server start. Impact: integrated-server or test-server restarts in one JVM make all later async saves run inline on the caller thread, increasing tick-time I/O and changing performance behavior. Repro: stop a singleplayer/integrated server, start another world in the same client JVM, then call a manager that uses `AsyncSaveExecutor.submit`.

- [MED-11] Vassal save can fail if its storage directory does not already exist — `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:523` and `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:531` — Root cause: `saveData` writes `SERVER.getServerDirectory()/config/warntax/vassals.json` through `FileWriter` but never creates the parent directory. Impact: first-run vassal data may fail to save if no other manager has already created `config/warntax`. Repro: start from a clean server directory and trigger only vassal save before tax/other managers create the directory.

- [MED-12] Tax ledger arithmetic is unbounded signed int addition — `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:356` and `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:1179` — Root cause: `adjustTax` and `payTaxDebt` write `current + delta` without clamp or overflow checks. Impact: large adjustments or repeated exploit packets can wrap balances negative/positive, causing false debt or excess tax. Repro: repeatedly add large tax deltas through exposed paths until the 32-bit integer wraps.

## Low Findings / Code Smells

- [LOW-1] Vassal offline notifications are volatile and lost on restart — `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:43`, `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:480`, `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:684`, and `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:542` — Root cause: `OFFLINE_MESSAGES` is an in-memory static map only. Impact: vassal expiry/revocation/tribute notices queued for offline players disappear on restart. Repro: queue a vassal notification while the player is offline, restart, then log in.

- [LOW-2] Vassal primary-colony lookup can NPE on null MineColonies owners — `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:489` and `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java:492` — Root cause: the code calls `c.getPermissions().getOwner().equals(playerId)` without checking owner for null, even though other code paths include null-owner repair logic. Impact: vassal tribute/status operations can crash if a colony has a missing owner. Repro: create/load a colony with null owner, then call a vassal path that resolves an overlord colony.

- [LOW-3] Colony data response leaks all colony IDs and names to any GUI requester — `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java:216` — Root cause: the response appends `new ColonySummary(c.getID(), c.getName())` for every colony without access/range filtering. Impact: any player able to open the GUI learns every colony ID/name, which may be sensitive for PvP/spy servers. Repro: connect as a new player, request colony data, inspect `allColonySummaries`.

- [LOW-4] Command suggestions also dereference nullable ranks — `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java:52`, `src/main/java/net/machiavelli/minecolonytax/commands/TreasuryCommand.java:54`, `src/main/java/net/machiavelli/minecolonytax/commands/TaxDebtCommand.java:50`, and `src/main/java/net/machiavelli/minecolonytax/commands/FactionCommand.java:83` — Root cause: several command helper/suggestion paths call `getRank(...).isColonyManager()` without null checks. Impact: command completion or helper calls can throw for players not ranked in scanned colonies. Repro: use tab completion or a helper path as a player who has no rank entry in at least one colony.

- [LOW-5] Direct MineColonies core classes are used outside compatibility shims — `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java:11`, `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:14`, `src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java:37`, and `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java:240` — Root cause: code imports/uses `com.minecolonies.core.*` classes directly instead of API interfaces/reflection/compat wrappers; `ColonyBuildingUtil` correctly centralizes building API drift, but these core usages are scattered. Impact: MineColonies internals changing class names/packages can break loading or runtime behavior. Repro: run against a MineColonies build that moves/removes `EntityMercenary` or core config classes.

- [LOW-6] JourneyMap compatibility is guarded, but dependency metadata does not express optional integration — `src/main/java/net/machiavelli/minecolonytax/compat/SpyJourneyMapPlugin.java:14`, `src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java:3`, `build.gradle:165`, and `src/main/resources/META-INF/mods.toml:44` — Root cause: direct JourneyMap API imports are isolated in `JmImpl` and guarded by `SpyJourneyMapPlugin`, but `mods.toml` has no optional JourneyMap dependency/ordering metadata. Impact: the code guard is good, but loader metadata cannot communicate optional compatibility expectations to Forge/users. Repro: inspect `mods.toml`; only Forge/Minecraft dependencies are declared.

- [LOW-7] Dismissing random-event log entries requires only hut access — `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java:56` and `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java:58` — Root cause: the packet allows anyone with `Action.ACCESS_HUTS` to remove an event log entry. Impact: low-ranked colony members who can access huts may hide event history from the GUI. Repro: grant a non-manager hut access, then send `DismissEventPacket(colonyId, eventId)`.

- [LOW-8] Several world gameplay saves are stored under global `config/warntax` paths — `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:53`, `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java:42`, `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java:48`, and `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3907` — Root cause: multiple managers use process-relative config files rather than consistently anchoring world state under `server.getServerDirectory()`, while `VassalManager` does anchor through `SERVER.getServerDirectory()`. Impact: singleplayer/integrated or multi-world setups can leak/collide gameplay state across worlds using the same config directory. Repro: run two worlds with overlapping colony IDs under the same instance and inspect shared `config/warntax` JSON.

## Cross-cutting Recommendations

- Treat every C2S packet as hostile input: validate enum ordinals, string enums, signed amounts, colony membership, rank nullability, and owner/officer authority in the packet handler or a shared server-side authorization helper.

- Centralize colony role checks into helpers that are null-safe and policy-aware, e.g. owner-only, manager/officer, tax-claim permission, treasury-management permission, and spy-deploy permission. Use those helpers consistently across commands and packets.

- Make economy operations transactional from the persistence perspective. Tax ledger and treasury/vassal ledger changes should save atomically together or through a single durable state manager, especially for deposit/withdraw/tribute flows.

- Use temp-file plus atomic rename for critical JSON saves. Apply this to tax, treasury, war, random events, occupation, upgrades, policies, and vassal data.

- Persist or explicitly reconcile live operations on restart: active raids, raid grace, active wars, PvP battle state, pending spy rewards, and tax permissions. For expired wars/raids during downtime, resolve outcomes instead of deleting state.

- Replace remaining ad hoc executors/schedulers with `TickScheduler` or lifecycle-managed daemon services. If a static executor is necessary, initialize it per server start and shut it down per server stop.

- Keep optional integrations behind classloader-safe boundaries. Move FTB Teams and MineColonies core internals behind compat classes, and declare mandatory/optional dependencies in `mods.toml`.

- Add targeted negative tests/static checks for crafted packets: negative tax claims, invalid ordinals, unauthorized tax-permission updates, officer investment purchases, null rank packets, and restart-mid-transaction persistence.

## Files Reviewed

- `CLAUDE.md`
- `AGENTS.md`
- `build.gradle`
- `src/main/resources/META-INF/mods.toml`
- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java`
- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/TreasuryCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/TaxDebtCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/FactionCommand.java`
- `src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/ColonyBuildingUtil.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/SpyJourneyMapPlugin.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/RaidPenaltyManager.java`
- `src/main/java/net/machiavelli/minecolonytax/economy/policy/TaxPolicyManager.java`
- `src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java`
- `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java`
- `src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimVassalTributePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayTaxDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/UpdateTaxPermissionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/UpdatePlayerTaxPermissionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestTreasuryDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryActionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SetTaxPolicyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestInvestmentDataPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RecallSpyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissSpyMissionPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/EndVassalizationPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java`
- `src/main/java/net/machiavelli/minecolonytax/permissions/PermissionSnapshot.java`
- `src/main/java/net/machiavelli/minecolonytax/permissions/PermissionsHealthCheck.java`
- `src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java`
- `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java`
- `src/main/java/net/machiavelli/minecolonytax/raid/EntityRaidManager.java`
- `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java`
- `src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java`
- `src/main/java/net/machiavelli/minecolonytax/event/WarEconomyHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java`
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java`
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java`
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java`
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java`
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java`
- `src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java`
- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java`
- `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java`
