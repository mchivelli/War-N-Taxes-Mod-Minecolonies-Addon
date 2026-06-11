# Porting Notes — for the 1.21 / NeoForge Port Agent

**Temporary handoff doc.** Bugs and MineColonies-API compatibility findings discovered while validating the 1.20.1 mod against **MineColonies 1.1.1237** (CurseForge file 8186502) on 2026-06-05. The 1.21/NeoForge port (`C:\Dev\MinecolonyTaxAddon-Dev-1.21 Neoforge`) targets a newer MineColonies still, so re-verify each of these against the port's MineColonies build.

Search the 1.20.1 source for the marker `[1.21-PORT]` — every spot below is tagged there in code.

---

## 1. CRITICAL — `IPermissions.setOwner` signature changed (UUID → Player) + cached `ownerUUID`

**What broke (1.20.1, found via dedicated-server boot test):** MineColonies changed
`IPermissions.setOwner(UUID)` → **`boolean setOwner(net.minecraft.world.entity.player.Player)`**.
Three sites reflected `setOwner(UUID)` and, on failure, invoked any 1-arg `setOwner` with a UUID →
`java.lang.IllegalArgumentException: argument type mismatch` at runtime, leaving colonies **ownerless**
(GUI crashes, abandonment/claiming ownership transfer silently failing).

**Crucial MineColonies internals (verified in `core/colony/permissions/Permissions.java`):**
- `getOwner()` returns a **cached `ownerUUID` field**. It only recomputes from the OWNER-ranked
  player (`getOwnerEntry()`) when `ownerUUID == null`.
- **`setOwner(Player)` is the ONLY public method that updates `ownerUUID`** (also `setOwnerAbandoned()`,
  which assigns a *random* uuid — used for the "[abandoned]" owner the mod deliberately avoids).
- **`setPlayerRank(uuid, OWNER, level)` does NOT update `ownerUUID`** — it only changes the rank map.
  So if `ownerUUID` is already non-null, setting OWNER rank will NOT change `getOwner()`.
- **`removePlayer(uuid)` refuses to remove an OWNER-ranked entry** (so you can't drop the current owner that way).

**Net API limitation:** in the new MineColonies API you **cannot set an OFFLINE or SYNTHETIC player
as the colony owner** — `setOwner` needs an online `Player`. The old `setOwner(UUID)` allowed it.

**How it was fixed in 1.20.1 (mirror this in the port):**
| Site | Fix |
|------|-----|
| `ColonyClaimingRaidManager` (~L807) | `permissions.setOwner(claimingPlayer)` — claimer is an online `ServerPlayer`; complete fix. |
| `ColonyAbandonmentManager.cleanupSystemOwnerAndSetRealOwner` | resolve online `ServerPlayer`; `setOwner(online)` if online, else best-effort `setPlayerRank(uuid, OWNER, level)`. |
| `WntCommands` (system-owner emergency fix) | best-effort `setPlayerRank(systemOwner, OWNER, level)` — synthetic uuid can never be online, so `setOwner(Player)` is impossible. |

**For the port:** confirm the port's MineColonies has `setOwner(Player)` (it will). The offline/synthetic
limitation persists — if the port needs true offline-owner reassignment, it must find a MineColonies-supported
path (there isn't a clean public one as of 1.1.1237) or accept best-effort. `WarSystem.transferOwnership`
(1.20.1 ~L1274) already calls `setOwner(Player)` directly — that path is fine and is the reference pattern.

---

## 2. Other MineColonies version-sensitive points to re-verify on 1.21/NeoForge

- **Building manager** — `ColonyBuildingUtil.getBuildings(colony)` (compat shim) reflects
  `getServerBuildingManager()` then `getBuildingManager()`. Re-confirm the method names on the port's MC.
- **Mercenary entity** — `MilitiaSpawner` + `RaidKillTracker` reference `com.minecolonies.api.entity.ModEntities.MERCENARY`
  and the internal `com.minecolonies.core.entity.mobs.EntityMercenary`. Internal package — guarded with
  `NoClassDefFoundError`/`LinkageError` catches. Re-verify the class still exists / package path on the port.
- **Guard-tower detection** — `WarSystem.isGuardTower(IBuilding)` matches by displayName "Guard Tower" (per-instance)
  + className/`toString()` containing "guardtower" (class-cached). If MC renames the guard-tower building class,
  update the matcher. (This was also a perf hotspot — see §3.)
- **`IPermissions.setPlayerRank(UUID, Rank, Level)`** — stable in 1.1.1237; confirm signature on the port.
- **Eventbus events** — `ColonyCreatedModEvent` / `ColonyDeletedModEvent` (FirstColonyTracker bootstrap),
  subscribed via `IMinecoloniesAPI.getInstance().getEventBus()`. Re-verify event class names/packages.

---

## 3. Optimization work carried out on 1.20.1 (port should carry forward)

A full server-stability optimization pass landed on 1.20.1 (`OPTIMIZATION_AUDIT.md`). The port should
reproduce the equivalents (note: the port uses **WarChestManager**, not TreasuryManager — apply the async-save
fix to WarChestManager there):

- **Async saves**: route per-change saves through an off-thread coalescing writer (1.20.1 `AsyncSaveExecutor`).
  Done for Treasury/WarExhaustion/Tax on 1.20.1; **MEDIUM** items still open: `RaidPenaltyManager`,
  `TaxPolicyManager`, `TradeRouteManager`, `ColonyUpgradeManager`, `FirstColonyTracker`, `HistoryManager`.
- **RandomEventManager** save-once-per-cycle (not per colony — was O(N²)).
- **Per-second / per-event early-outs**: ColonyEventListener building scan (logging-gated), RaidKillTracker,
  AbandonedColonyProtectionHandler, BlockInteractionFilter (`BesiegeManager.hasActiveRaids()`).
- **Deferred on 1.20.1 (still open, re-evaluate on port):**
  - **H9** `getActiveWarForPlayer` — O(wars × FTB/permission); reverse index `playerId→colonyId` if many simultaneous wars.
  - **H10** `VassalManager.getColonyById` / `getPrimaryColonyOfPlayer` — O(allColonies) stream inside the tax cycle; needs a colony-id index (no safe O(1) cross-dimension lookup without one).
  - MEDIUM/LOW: `ColonyDataCollector` GUI-open scan (cache w/ TTL), entity-raid AABB query, `MilitiaSpawner` spawn-count clamp, `GuardResistanceHandler` → ConcurrentHashMap, atomic temp+move on remaining sync writers.

---

## 4. Dependency-stack lesson (applies to the port's MineColonies too)

MineColonies declares **mandatory `versionRange`s on its hard deps** (Structurize/BlockUI/Domum Ornamentum)
in its `mods.toml`. Bumping MineColonies REQUIRES bumping those to match or the game refuses to load.
On 1.20.1 this caught us: pinned Structurize 1.0.800 < MC 1.1.1237's required `>=1.0.806`. For the port,
always read the new MineColonies jar's `META-INF/mods.toml` ranges before locking the dependency set.
(1.20.1 verified set is in `DEPENDENCY_COMPATIBILITY.md`.)

---

## 5. Status of the 1.20.1 work (context)

All of the above 1.20.1 fixes are **codex-reviewed and compile-green**, but **not yet runtime-tested in-game**
(no two-player `runServer` pass) and **uncommitted** at the time of writing. See `TESTING_GUIDE.md`,
`OPTIMIZATION_AUDIT.md`, `DEPENDENCY_COMPATIBILITY.md`.
