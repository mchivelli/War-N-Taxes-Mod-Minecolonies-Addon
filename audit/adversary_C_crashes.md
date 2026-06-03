# Adversary Codex: Crash Hunter

Adversarial audit, read-only, no code changes. Focus: NullPointerException / NoClassDefFoundError / ClassCastException / IllegalArgumentException paths that take down a dedicated MC 1.20.1 Forge server (or block boot). Only crashes triggered from realistic user/dependency states are listed — speculative perfect-storm bugs are excluded.

## Summary

The mod is *mostly* defensive (try/catch wrappers, ColonyBuildingUtil shim, JmImpl guarding) but several high-blast-radius crash paths slip through. The most dangerous ones are:

1. A direct call into `com.minecolonies.core.MineColonies.getConfig()` on every `ServerStartingEvent` that is wrapped only in `catch (Exception)` — `NoClassDefFoundError` is an `Error`, not an `Exception`, so any MC API rename of that class bricks server boot.
2. Two hard imports of the internal class `com.minecolonies.core.entity.mobs.EntityMercenary` (`ColonyClaimingRaidManager`, `BesiegeManager`), plus an `instanceof EntityMercenary` inside the per-mob-death `RaidKillTracker` event — the class is in `com.minecolonies.core.*` (not the `api` package the rest of the mod respects) and will `NoClassDefFoundError` on every mob death if that class moves or is removed.
3. The war restore path (`loadAndResumeActiveWars` → `resumeWarFromSave`) calls `UUID.fromString` on six fields read directly from JSON with no null/format guard. A single hand-edited save file or a save written before a field existed → server-startup `IllegalArgumentException` or `NullPointerException`.
4. `OccupationManager.startOccupation` calls `originalOwnerUUID.toString()` in the constructor with no null guard — for an abandoned colony whose `getPermissions().getOwner()` is null, declaring a colony-conquest war immediately NPEs the war-end path.
5. Several network packet handlers (e.g. `DeploySpyPacket`) call `colony.getPermissions().getRank(player.getUUID()).isColonyManager()` without checking `getRank() != null` — any player who is not in the colony's permission list can crash the server tick by deploying a spy.

## Critical Crashes (server-killing or boot-blocking)

- **[CRASH-1]** Hard reference to internal MC class on every server start —
  `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java:240`
  - Trigger: `onServerStarting` always runs `com.minecolonies.core.MineColonies.getConfig().getServer().pvp_mode.get()`.
  - Exception: `NoClassDefFoundError` (or `LinkageError`).
  - Why it kills the server: wrapped only in `catch (Exception)`; `NoClassDefFoundError` extends `Error`, which is **not** caught. If `com.minecolonies.core.MineColonies` is renamed, refactored, or relocated (it is an internal class, NOT part of `com.minecolonies.api`), `onServerStarting` throws an uncaught `Error` and the server fails to start. This violates the rule documented in `CLAUDE.md` ("Never reference JM classes directly outside of `JmImpl.java`") for the analogous MineColonies-internal case.

- **[CRASH-2]** Hard import + bytecode reference to `com.minecolonies.core.entity.mobs.EntityMercenary` —
  `src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java:37,439`
  `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java:11`
  `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:14`
  - Trigger: any `LivingDeathEvent` for any entity (mob, animal, citizen, player) walks through `instanceof com.minecolonies.core.entity.mobs.EntityMercenary` — class is loaded eagerly on first event dispatch.
  - Exception: `NoClassDefFoundError` propagated out of the EventBus (Forge does NOT swallow it).
  - Why it kills the server: this is `com.minecolonies.core.*`, not `com.minecolonies.api.*`. The shim pattern used for `ColonyBuildingUtil` is not applied. If MineColonies refactors the mercenary class (already happened for the building manager), the very next time anything dies on the server the event handler throws `NoClassDefFoundError`. There is no try/catch on the `instanceof` line. Same risk on every `BesiegeManager.tick()` (scheduled every second).

- **[CRASH-3]** War restore: malformed/old `active_wars.json` →
  `IllegalArgumentException` from `UUID.fromString` —
  `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4122-4126,4086,4090`
  - Trigger: `loadAndResumeActiveWars` called on every `ServerStartingEvent` reads `e.warID`, `e.attacker`, `e.defender`, `e.attackerTeamID`, `e.defenderTeamID`, plus every key in `attackerLives`/`defenderLives`, all via `UUID.fromString(...)` with no `try { } catch (IllegalArgumentException)` and no null check.
  - Exception: `NullPointerException` if any UUID field is missing from the JSON (older save), `IllegalArgumentException` if any field is corrupt or hand-edited.
  - Scenario: any of `attackerTeamID`/`defenderTeamID` was never set (single-player war, no FTBTeams) → the save writes `null.toString()` line 3957 — wait, this is actually CRASH-3b below.
  - Outer `try { } catch (Exception ex)` in `loadAndResumeActiveWars` (line 4044) DOES catch this and logs, but the per-war `resumeWarFromSave` is called from inside the same try, so one bad war prevents all subsequent wars from being restored even when the outer catch logs. More dangerous: if Gson returns `null` (empty file) at line 4012, the code handles it; but `IllegalArgumentException` from a corrupt UUID just gets swallowed without restoring any war, which is data loss not a crash. Still HIGH but not boot-blocking.

- **[CRASH-3b]** War save: `getAttackerTeamID().toString()` on a null team —
  `src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3957-3958`
  - Trigger: A war initiated without FTBTeams (FTB_TEAMS_INSTALLED=false) — `attackerTeamID` defaults to `attacker.getUUID()` (line 117) so this is OK in the normal path. BUT `defenderTeamID` defaults to `colony.getPermissions().getOwner()` (line 119), which is **null** for an abandoned colony. A war declared against an abandoned/system-owned colony stores `defenderTeamID = null`. On server stop, line 3958 does `war.getDefenderTeamID().toString()` → `NullPointerException`.
  - Exception: `NullPointerException`.
  - Why it kills the server: `saveActiveWars` is wrapped in try/catch (line 3999) but inside it iterates ACTIVE_WARS; the throw aborts the iteration so all *subsequent* wars in the map are dropped from disk and never restored. The catch logs, so the server doesn't hard-crash on stop — but on next start the unsaved wars are gone, and any restored partial state can corrupt subsequent loads.

- **[CRASH-4]** Occupation start with null colony owner →
  `NullPointerException` in OccupationData constructor —
  `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:88,163,197`
  - Trigger: `WarSystem.checkForVictory` → attackers win → `OccupationManager.startOccupation(colony, attacker, attackerColony)`. Inside, line 163 calls `colony.getPermissions().getOwner()` — this returns null for an abandoned colony (ColonyAbandonmentManager explicitly leaves owner=null in some legacy paths and FCT explicitly checks `if (ownerUUID != null)`). Line 197-201 passes this null straight to the `OccupationData` constructor, which on line 88 calls `originalOwnerUUID.toString()` → NPE.
  - Exception: `NullPointerException`.
  - Why it kills the server: thrown from the war-victory tick path. Wrapped only in the outer `try` in `checkForVictory`? No — `checkForVictory` has NO try/catch. The NPE propagates up the TickScheduler → caught by TickScheduler at line 103-105 (logs, does not re-throw), so the *server does not crash* but the war never ends cleanly: ACTIVE_WARS still contains the war, treasury drain keeps running, players stay in spectator mode forever. Recovery requires server restart.

- **[CRASH-5]** Spy deployment NPE from non-member player —
  `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java:48`
  - Trigger: client sends DeploySpyPacket. Server handler resolves colony via `IMinecoloniesAPI.getInstance().getColonyManager().getIColonyByOwner(player.level(), player)` — this returns the player's owned colony, so most paths are safe. **However**, if the player is owner of one colony but uses the GUI of another (e.g. faction member, allied colony in a paired environment) the lookup of `getRank(player.getUUID())` on a colony where the player has no permission record returns null. Line 48 chains `.isColonyManager()` on the null Rank → `NullPointerException`.
  - Exception: `NullPointerException` on the server-side network thread (via `enqueueWork`).
  - Why it kills the server: Forge swallows the throw in `enqueueWork` but logs it as `WARN` and the packet handler aborts — not boot-blocking, but easily crash-loopable by spamming the packet, and the partial state (PENDING_COSTS may have been bumped) leaks.

## High (rare conditions but reproducible)

- **[CRASH-6]** `notifyColonyOfficers` adds a null UUID to the recipient set —
  `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:1102`
  - Trigger: `perms.getOwner()` returns null for an abandoned target colony. Line 1102 adds null to `recipients` HashSet. Line 1108 then calls `SERVER.getPlayerList().getPlayer(null)`.
  - Exception: `NullPointerException` inside `PlayerList.getPlayer(UUID)` (vanilla MC).
  - Scenario: any spy mission against a colony whose owner was abandoned at the moment of detection/escape — fires from `onSpyDetected`, `onSpyEscaped`, `onMissionSuccess`. Server-side, propagates through TickScheduler — caught (logged) but mission state is left dangling.

- **[CRASH-7]** `WarData` constructor NPE on null attackerColony —
  `src/main/java/net/machiavelli/minecolonytax/data/WarData.java:79`
  - Trigger: `new WarData(..., null /*attackerColony*/)` — line 79 calls `attackerColony.getCitizenManager().getCitizens()` unconditionally. The matching block at line 89 IS null-guarded, but line 79 is not.
  - Exception: `NullPointerException`.
  - Scenario: `initiateWar` is the only caller in production and always passes the attacker's colony; but `attackerColony` is "the attacker's own MineColonies colony", which is null for players who don't own one. WntCommands has several flows that allow attackers without their own colony (extortion, FTBTeams allies). Triggers reliably on a player without a colony declaring war.

- **[CRASH-8]** `OccupationData.getOccupierUUID` / `getOriginalOwnerUUID` —
  `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:122,126`
  - Trigger: Gson reads `occupations.json` and `occupierUUID`/`originalOwnerUUID` fields are missing (legacy save written before a field was renamed). `getOccupierUUID()` then calls `UUID.fromString(null)` → `NullPointerException` (not IAE). Called from `endOccupation` line 400, `reclaimByOriginalOwner` line 550, `checkExpiredOccupations` line 446.
  - Exception: `NullPointerException`.
  - Why high: the occupation tick (line 275 in MineColonyTax) wraps in try/catch but only swallows `Throwable` on `checkExpiredOccupations` as a whole — first failing occupation aborts processing of all later ones in the same tick, leaving stale occupations in memory forever.

- **[CRASH-9]** Direct ref to `IRegisteredStructureManager` via reflection raises on probing —
  `src/main/java/net/machiavelli/minecolonytax/compat/ColonyBuildingUtil.java:67`
  - Trigger: shim falls through entirely (both `getServerBuildingManager` and `getBuildingManager` missing) on a future MineColonies version. `managerGetter` stays null → all callers get empty list. Not a crash itself, but every system that needs buildings (tax generation, war reparations, etc.) silently degrades to zero — TaxManager generates no taxes, war reparations compute 0, etc. Worth flagging because it's the silent failure mode.

- **[CRASH-10]** `tryShopUtilsApi` static initializer loads Player.class —
  `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java:62-64`
  - Trigger: class is referenced anywhere → static initializer runs `initialize()` which calls `Class.forName("net.sixik.sdmshop.utils.ShopUtils")`. The class load happens during mod init. If a SECOND SDM mod is present that throws during its static init while WnT is touching its class, the failure cascades into WnT's static initializer → `ExceptionInInitializerError` on the next reference to SDMShopIntegration.
  - Exception: `ExceptionInInitializerError` (subclass of `LinkageError` / `Error`), not catchable by `catch (Exception)`.
  - Scenario: only triggers if SDM has a corrupted install or partial install; combined with the broad `Class<?>[] paramTypes` reflection loop, every miss is just a NoSuchMethodException (caught), so the actual blast radius is small.

- **[CRASH-11]** `tryCurrencyDataApi` reflective `value` field read returns null —
  `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java:319-326`
  - Trigger: `getBalance` returns ErrorCodeStruct with `value=null` (player not registered in currency system). `valueField.get(result)` returns null. Line 325 does `if (value instanceof Double)` — instanceof handles null safely, OK. No crash. But line 322 `result.getClass().getField("value")` will throw if `result` itself is null — caught by outer `try` in `getMoney`. Confirmed safe.

- **[CRASH-12]** PatchouliBookHandler reflection — handled gracefully.
  `src/main/java/net/machiavelli/minecolonytax/event/PatchouliBookHandler.java:99-105` — has `catch (ClassNotFoundException)` and broad `catch (Exception)`. Pattern is correct.

## Medium (edge cases, narrow trigger)

- **[CRASH-13]** `SpyEntity.computeFleeTarget` ClassCast on first tick after dimension change —
  `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java:266,298,311,331`
  - Multiple `(ServerLevel) this.level()` casts. The hurt → enterFleeState path is server-side-only (early-return at line 209 on client), so the cast is correct in practice. Risk: if MineColonies' spawn process ever transiently puts the entity in a non-ServerLevel context during entity loading, the cast crashes. Low-probability but unguarded.

- **[CRASH-14]** `loadTaxData` only catches `IOException`, not `JsonSyntaxException` —
  `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:1149`
  - A corrupted `colonyTaxData.json` (mid-write crash) triggers `JsonSyntaxException` from Gson, which is a `RuntimeException` not caught by `catch (IOException)`. Propagates up to `TaxManager.initialize` → uncaught → server start fails. The sibling `loadLastTaxGenerationTime` (line 1212) handles this correctly with broad `catch (Exception)`. Asymmetry → boot-blocker if data file is corrupt.

- **[CRASH-15]** `WarEventHandler.sendColonyMessage` cast —
  `src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:471`
  - `(ServerPlayer) colony.getWorld().getPlayerByUUID(uuid)` — `getPlayerByUUID` returns `Player`, not `ServerPlayer`. On a server-side Level the actual type IS ServerPlayer, so the cast succeeds. Safe in practice but fragile if MC ever returns a placeholder Player wrapper.

- **[CRASH-16]** `EasyFactionsBridge` — handled correctly with `catch (Throwable)`.
  `src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsBridge.java:77,109`
  - This is the model the rest of the mod should follow for optional integrations.

- **[CRASH-17]** `getMoneyViaCurrencyData` NoSuchFieldException not caught locally —
  `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java:322`
  - `result.getClass().getField("value")` — if ErrorCodeStruct is refactored to make `value` private (most likely future change), throws `NoSuchFieldException`. The outer `try { } catch (Exception)` in `getMoney` (line 301) catches it but downgrades the failure to "balance = 0" silently — every player appears bankrupt → war reparation transfers compute 0 → silent gameplay break.

- **[CRASH-18]** `SpyManager.gatherTier3Intel` — `cp.getID()` then `SERVER.getProfileCache().get(cp.getID()).map(p -> p).orElse(null)` —
  `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:660-663,688-691`
  - `cp.getID()` could be null in theory; `ProfileCache.get(null)` throws NPE in vanilla MC. ColonyPlayer.getID() is documented as non-null but isn't validated. Lower risk.

- **[CRASH-19]** `tryShopUtilsApi` does `legacySetMoney.invoke(null, ...)` after returning, no return-value check on legacy-add fallback path —
  `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java:448`
  - Method returns `true` regardless of actual result. Bug, not a crash — masks legacy failure.

- **[CRASH-20]** TickScheduler swallows `Exception` but not `Error` —
  `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:103`
  - Any `NoClassDefFoundError` (e.g. CRASH-2 propagating through a scheduled callback) crashes the entire scheduler tick — every task after the failing one in the same tick is silently skipped. Not a server crash but state-corrupting.

- **[CRASH-21]** `JmImpl.removeWaypoint` swallows `NoClassDefFoundError` only inside `syncWaypoints` —
  `src/main/java/net/machiavelli/minecolonytax/compat/SpyJourneyMapPlugin.java:34-39` and `JmImpl.java:106`
  - `removeWaypoint` catches generic `Exception` — if JM Waypoint API throws an `Error`, propagates to the GUI thread. Low risk; JM is a stable mod.

## Files Audited

- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java` (partial — building name maps)
- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java` (partial — first 1086 lines + persistence section ~3904-4243)
- `src/main/java/net/machiavelli/minecolonytax/data/WarData.java` (partial — constructors)
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java`
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java`
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java`
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyMission.java` (partial)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyClientHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/espionage/ModEntities.java`
- `src/main/java/net/machiavelli/minecolonytax/event/PatchouliBookHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java`
- `src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java` (partial)
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java` (partial)
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java` (partial)
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java` (partial)
- `src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/ColonyBuildingUtil.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/SpyJourneyMapPlugin.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/ColonyHelper.java`
- `src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsBridge.java`
- `src/main/java/net/machiavelli/minecolonytax/capability/PlayerWarDataCapability.java`
- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java`
- `src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java` (partial — packet registration only)
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/PayTaxDebtPacket.java`
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java`
- `src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java` (partial)
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java` (partial)
