# Audit 08 — Compatibility, Threading, Performance, Configuration

Scope: `compat/`, `util/TickScheduler`, `util/ColonyActivityTracker`, `util/TranslationUtil`, `CrashLogger`, `raid/ReflectionCache`, `integration/SDMShopIntegration`, `TaxConfig`, `datagen/`, `recipe/`, `event/Datapack/Recipe*`, `event/PatchouliBookHandler`.

Method: static analysis only (Read/Grep/Glob). No gradle build, no edits.

---

## Summary

The compat & threading layer is mostly disciplined: `ColonyBuildingUtil` is the single point of MineColonies building API access (no direct call sites outside it), `TickScheduler` is correctly registered + drained, `JmImpl` is the only file importing JourneyMap and is guarded by `ModList.isLoaded("journeymap")` in `SpyJourneyMapPlugin`. The big problems are elsewhere:

1. **Client-class leakage in network packets** — five `*ResponsePacket` classes hard-import `net.minecraft.client.Minecraft` at top-level, including the encode/decode paths that run on the dedicated server. This is the exact pattern called out as CRITICAL in CLAUDE.md.
2. **Non-TickScheduler scheduling** — `PvPManager.BATTLE_END_SCHEDULER` (ScheduledExecutorService) is never shut down, and `WarStatsDB.WRITER` + `AsyncSaveExecutor.EXEC` raw threads exist (the latter two are at least intentional async I/O with controlled shutdown).
3. **TaxConfig accessor discipline is broken** — CLAUDE.md mandates accessor methods; the codebase has 108 direct `TaxConfig.SOMETHING.get()` call sites across 21 files (most in `WarSystem.java`).
4. **Recipe disabling stack is duplicated and racey** — `DatapackInjector`, `RecipeDisableRuntime`, `RecipeDisableClient`, `RecipeDisableEventHandler`, `RecipeCraftBlocker`, `DisabledRecipeProvider`/`DisabledRecipe` are five+ independent paths that all try to suppress the same hut recipes; reflection on `RecipeManager` fields can break across Forge versions.
5. **SDMShopIntegration has no `ModList.isLoaded` short-circuit** — runs full reflection probe on every server even when SDMShop is absent (cheap but spams initialization logs).
6. **`ReflectionCache.initialize()` swallows non-presence and lacks a way to retry** — once a method is resolved as missing, it stays missing for the JVM lifetime; reasonable but no diagnostic surface.
7. **`CrashLogger` writes to CWD with `FileWriter`** — opens a synchronous file handle on whatever thread calls it; no path scoping.

---

## CRITICAL

### C1. Network packet handlers import `net.minecraft.client.Minecraft` at top level (5 classes)

CLAUDE.md (`feedback_dist_isolation.md`): *Never reference client-only classes from entities or common classes; use DistExecutor double-lambda.*

These packet classes are constructed AND have their `toBytes()` invoked on the dedicated server side (server-to-client packets). The top-level import causes the JVM verifier to require the `Minecraft` class be resolvable when the packet class itself loads — on a dedicated server JAR the class resolves because Forge ships unified jars, but any future mapping change or refactor that touches these handlers risks a hard `NoClassDefFoundError`. The correct pattern (already used in `OpenTaxGUIPacket`) is to route client-only work through `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ...)` with the actual `Minecraft.getInstance()` call inside a separate `*ClientHandler` class.

- `src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java:8` — `import net.minecraft.client.Minecraft;` → `handle()` calls `Minecraft.getInstance()` directly at line 220.
- `src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryDataResponsePacket.java:5` — same pattern.
- `src/main/java/net/machiavelli/minecolonytax/network/packets/OfficerDataResponsePacket.java:4` — same pattern.
- `src/main/java/net/machiavelli/minecolonytax/network/packets/InvestmentDataResponsePacket.java:4-5` — also imports `net.minecraft.client.gui.screens.Screen`.
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java:11` — same pattern; line 85 references `Minecraft.getInstance()`.

(The good reference: `OpenTaxGUIPacket.java:31` uses the correct `DistExecutor.unsafeRunWhenOn` form. `GlowClientHandler.java` is correctly gated with `@EventBusSubscriber(value = Dist.CLIENT)`, but `EntityGlowPacket.handle()` at line 35 captures `GlowClientHandler::handleGlowPacket` in a lambda that's compiled into the common-side class; if JVM eagerly verifies the lambda metafactory's target, the same pattern risks loading `GlowClientHandler` → `Minecraft` on the server. The runtime `isClient()` guard at line 34 does not protect against classloading-time verification, only against execution.)

### C2. `RecipeDisableRuntime` / `RecipeDisableClient` reflectively mutate `RecipeManager` private fields

- `src/main/java/net/machiavelli/minecolonytax/event/RecipeDisableRuntime.java:148-177` (server)
- `src/main/java/net/machiavelli/minecolonytax/event/RecipeDisableClient.java:131-159` (client)

Both try field names `{"recipes", "byType", "f_44006_"}` and `{"byName", "f_44007_"}`. The reflection finds the field on `manager.getClass()` (i.e., the runtime subclass) — if Forge or a Mixin replaces `RecipeManager` with a custom subclass that re-declares `recipes` differently, this throws `IllegalStateException` and is logged. No fallback; recipes silently remain enabled. Mid-game `/reload` does NOT re-trigger removal because both handlers listen only to `ServerStartedEvent` / `RecipesUpdatedEvent` (the client one survives `/reload`; the server-side one does NOT). Datapack reload on the server leaves hut recipes re-enabled until restart.

---

## HIGH

### H1. `PvPManager.BATTLE_END_SCHEDULER` — ScheduledExecutorService never shut down

- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:59` — `public static final ScheduledExecutorService BATTLE_END_SCHEDULER = Executors.newScheduledThreadPool(1);`
- Used at `pvp/PvPBattleManager.java:72` and `:723`.
- No `BATTLE_END_SCHEDULER.shutdown()` call exists anywhere in the source tree (grep `BATTLE_END_SCHEDULER\.shutdown` → no matches).
- Daemon-thread? No — `Executors.newScheduledThreadPool(1)` creates non-daemon threads by default. Single-player worlds reloading will retain the thread; server stop may need to wait for tasks (or hang). Tasks delegate work via `server.execute(...)` which is correct, but the timer thread itself is foreign.
- CLAUDE.md mandates `TickScheduler` for deferred tasks; `BATTLE_END_SCHEDULER` violates that rule wholesale. Replace `BATTLE_END_SCHEDULER.schedule(r, n, TimeUnit.SECONDS)` with `TickScheduler.scheduleDelayed(r, n * 1000L)`.

### H2. `WarStatsDB.WRITER` — raw `Thread`/`ThreadPoolExecutor`

- `src/main/java/net/machiavelli/minecolonytax/db/WarStatsDB.java:44-49` constructs `LinkedBlockingQueue<Runnable>(1000)` + `ThreadPoolExecutor` with `new Thread(r, "WarStatsDB-Writer")`.
- Justified for blocking JDBC I/O (correct decision — JDBC must not run on the tick thread). Shutdown is at line 85-91 with 10s grace. The daemon flag is set. This is acceptable, but it's a deliberate exception to the "use TickScheduler" rule and should be called out in CLAUDE.md so future contributors don't replicate the pattern for non-I/O work.

### H3. `AsyncSaveExecutor.EXEC` — raw `Thread`

- `src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java:33-37` — `Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "WNT-AsyncSave"); t.setDaemon(true); return t; })`.
- Same shape as H2 — intentional async file I/O, daemon, has `shutdownAndFlush()` at line 88 called from `MineColonyTax:406`. Acceptable, but again is a documented carve-out from the "TickScheduler only" rule.

### H4. `TaxConfig` direct field access pattern violates CLAUDE.md

CLAUDE.md: *Accessor methods are at the bottom of `TaxConfig.java`. Always add one when adding a new config field.*

There are **108 occurrences** of `TaxConfig.SOMETHING.get()` across 21 files, including 37 in `WarSystem.java` alone. Top offenders:

- `WarSystem.java` — 37 sites, e.g. lines 132, 355, 381, 409, 522, 530, 646, 653, 662, 764, 1444, 1468, 1821, 1895-1896, 2159, 2235, 2258, 2489, 2633, 2656-2658, 2788, 2984, 3021, 3034-3036, 3088, 3102, 3403, 3629, 3825, 3831, 3845, 4101, 4176, 4211
- `raid/RaidManager.java` — 18
- `raid/GuardResistanceHandler.java` — 6
- `event/WarEventHandler.java` — 5, `militia/CitizenMilitiaManager.java` — 5, `pvp/PvPBattleManager.java` — 5, `commands/WntCommands.java` — 5
- `event/PvPKillEconomyHandler.java` — 4, `commands/WarCommands.java` — 3, `pvp/PvPEventHandler.java` — 3, `event/EntityRaidBossbarAttachHandler.java` — 3
- `besiege/BesiegeManager.java` — 2, `data/WarData.java` — 2, `peace/PeaceProposalManager.java` — 2, `raid/EntityRaidManager.java` — 2
- `MineColonyTax.java` — 1, `TaxManager.java` — 1, `event/PatchouliBookHandler.java` — 1 (`TaxConfig.GIVE_PATCHOULI_BOOK_ON_JOIN.get()`), `event/ColonyEventListener.java` — 1, `event/EntityRaidEventHandler.java` — 1, `economy/WarExhaustionManager.java` — 1

(Full list from `grep "TaxConfig\.[A-Z_]+\.get()"`.) This breaks the abstraction CLAUDE.md mandates and means any rename / type change on a config key requires touching every call site instead of one accessor.

### H5. Datapack write to live world directory at `ServerAboutToStart`

`event/DatapackInjector.java:33-77` — on every server start with `DisableHutRecipes=true`, the code writes 49 empty JSON files to `<world>/datapacks/mct_disable_huts/data/minecolonies/recipes/`, then on `ServerStarted` (line 80) it does it again and runs `datapack enable` + `reload`. Issues:
- The recipe-id list is hardcoded twice (lines 62-64 and 113-115); the two lists are identical and must be kept in sync manually.
- If a future MineColonies version renames a hut recipe, this list silently misses it (e.g., `blockhutuniversity.json` was added, future huts won't be).
- The write happens unconditionally even if the pack contents already match — Forge will see file mtimes change every restart, forcing recipe-network resync to every client.
- No backup is taken if the user already had a `mct_disable_huts` folder with custom contents (unlikely but destructive).
- `deleteRecursive` (line 135) walks paths in `getNameCount()` order rather than reverse-depth — if two paths have the same depth count, deletion order is undefined; in practice this works only because the directory tree is shallow.

### H6. `RecipeCraftBlocker.onItemCrafted` sets count to 0 AFTER crafting

`event/RecipeCraftBlocker.java:81-104` — `PlayerEvent.ItemCraftedEvent` fires after the item is already crafted; setting `output.setCount(0)` empties the result stack, but the ingredients have already been consumed. The end result is the player paying ingredients and getting nothing. The user-facing message says "Crafting … is disabled" without compensating. Acceptable as a hard block, but the better choice is to remove the recipe from `RecipeManager` (which the other handlers attempt) or block at JEI level (which `RecipeDisableClient` does). Three different layers all try to do this — they overlap and at least one (`PatchouliBookHandler`, no — `RecipeCraftBlocker`) consumes ingredients.

---

## MEDIUM

### M1. `ColonyBuildingUtil` init race window on initialization

`compat/ColonyBuildingUtil.java:46-69`:

```java
public static Collection<IBuilding> getBuildings(IColony colony) {
    if (colony == null) return Collections.emptyList();
    ensureInit(colony);
    if (managerGetter == null) return Collections.emptyList();
    ...
}
```

`ensureInit` synchronizes correctly, but the cached `buildingsGetter` field is read non-atomically at line 58 then assigned without synchronization at line 61 (worker may publish a stale getter). Because both reads/writes are reference-type and final, this is benign on x86 but technically a data race. Acceptable.

### M2. `ColonyActivityTracker` cache key collision risk

`util/ColonyActivityTracker.java:18` — `Map<Integer, ActivityStatus>` keyed by `colony.getID()`. Colony IDs are unique per **world dimension**; if multi-world setups have colonies with the same numeric ID in different dimensions, the cache will merge them. MineColonies in 1.20.1 typically uses a global ID space, so this is probably fine, but the design could break in modded multi-dimension setups.

### M3. `ColonyActivityTracker.cleanupExpiredCache` uses stale `lastCacheUpdate` field

`util/ColonyActivityTracker.java:140-167`. `clearCache()` writes `lastCacheUpdate = System.currentTimeMillis()`, then `cleanupExpiredCache()` only runs the iterator if `currentTime - lastCacheUpdate > CACHE_VALIDITY_MS` (line 150). So if `clearCache()` was called recently, the cleanup is skipped — but `clearCache()` just emptied the map, so the cleanup is a no-op anyway. The semantics are not wrong, just confusing.

### M4. `ReflectionCache` permanently disables itself on first failed init

`raid/ReflectionCache.java:60-89`. If Recruits isn't loaded at startup, `initialized=true` and the cache is permanently in "unavailable" mode. If Recruits is added by hot-reload (unsupported by Forge anyway), the cache will never re-probe. `forceReinitialize()` exists (line 779) but is only called from test code. Acceptable for normal mod lifecycle.

### M5. `ReflectionCache` swallows reflection exceptions silently

`raid/ReflectionCache.java:272-303` (and similar in `canHarmTeam`, `getOwnerUUID`, `isOwned`). Every reflective call is wrapped in `try/catch(Exception)` that logs to `EntityRaidDebugLogger` and returns `null`. Many call sites then convert `null` to "no opinion" — which means a transient reflection failure (e.g. class verifier hiccup) silently disables Recruits integration for that one call. Persistent failures spam the debug log but never escalate to a user-visible warning.

### M6. `SDMShopIntegration` has no `ModList.isLoaded` short-circuit

`integration/SDMShopIntegration.java:62-93`. The static initializer calls `initialize()` which probes three different APIs via `Class.forName`. There is no `ModList.get().isLoaded("sdmshop")` (or similar) guard — every server start runs all three probes whether SDMShop is installed or not, logging `Attempting to initialize...` at INFO level (line 71) and then `not available - no compatible API found` at WARN level (line 92). On servers without SDMShop, this produces a misleading warning every start. Also the static `initialize()` runs at class-load time, which is before config is loaded — `LOGGER.info` calls here are unconditional (do not respect `TaxConfig.isNormalLogging()`).

### M7. `CrashLogger` writes synchronously to CWD with `FileWriter`

`CrashLogger.java:15` — `private static final String CRASH_LOG_FILE = "crash_report.log";` (no path scoping; lands in `cwd`, which for a server is the server root). Every `logCrash` call opens a `FileWriter` on whatever thread invoked it (line 18, line 51). If invoked from the tick thread during a war event, it can stall the server for as long as the disk takes. No use of `AsyncSaveExecutor`. Should either be removed (Log4j already captures exceptions to `latest.log`) or routed through async I/O.

### M8. `DisabledRecipeProvider` static initialer touches MineColonies class

`datagen/DisabledRecipeProvider.java:33-80` — calls `ForgeRegistries.BLOCKS.getKey(...)` on MineColonies `ModBlocks` fields at class-load time. Data generation runs during build-time `runData`, so this is fine, but the same pattern is repeated in `RecipeCraftBlocker.java:26-79`, `RecipeDisableRuntime.java:39-85`, `RecipeDisableEventHandler.java:34-85`, `RecipeDisableClient.java:35-79`. Four parallel hand-maintained block lists; adding a new MineColonies hut requires editing all five.

### M9. `DatapackInjector` writes 49 files unconditionally even when contents unchanged

See H5. Every `datapack enable` + `reload` triggers a full RecipeManager rebuild and pushes `RecipesUpdatedEvent` to all clients — this is non-trivial overhead on every server start (and runs even if the previous shutdown left the pack in place).

### M10. `EasyFactionsBridge.resolve()` is `synchronized` but `state` is read unsynchronized

`compat/EasyFactionsBridge.java:47-82`. The double-checked-locking pattern works because `state` is `volatile` (line 33), but the `Method` fields are also `volatile` per the comment at line 35 — good. The pattern is correct.

### M11. Datapack and runtime recipe disable are duplicated

The recipe disable path runs:
1. `DatapackInjector` (writes empty JSONs, `ServerAboutToStart` + `ServerStarted`) — overrides via the resource pack system.
2. `RecipeDisableRuntime` (`ServerStarted`) — reflectively removes from `RecipeManager.recipes` and `RecipeManager.byName`.
3. `RecipeDisableClient` (`RecipesUpdatedEvent`) — same reflection on the client.
4. `RecipeDisableEventHandler` (`AddReloadListenerEvent`, HIGHEST priority) — just logs.
5. `RecipeCraftBlocker` (`PlayerEvent.ItemCraftedEvent`) — clears the result stack post-craft.
6. `DisabledRecipeProvider` (datagen) — runs at build time only.

(1) and (2) overlap — both try to suppress the same recipes at server start. (3) catches client-side display. (5) is the last-resort post-craft cleanup that also eats ingredients (see H6). Three independent paths trying to do the same thing means three places that can diverge.

### M12. `PatchouliBookHandler.giveBookToPlayer` uses unchecked reflection cast

`event/PatchouliBookHandler.java:79-99` — `(ItemStack) getBookStackMethod.invoke(...)`. If a future Patchouli version changes the return type, this throws `ClassCastException` rather than being caught by the `Exception` handler at line 102. Acceptable but should be `Object → instanceof ItemStack` for defensive parsing.

### M13. `TaxConfig` is 3687 lines — very large

`TaxConfig.java` has 253 accessor methods. The file mixes config declaration, defaults registration, accessor methods, and computed helpers (e.g. `getHappinessTaxMultiplier(avgHappiness)` at line 2940). Splitting per-system (WarConfig, RaidConfig, SpyConfig, etc.) would help readability and reduce merge conflicts. Not a defect, but a maintenance smell.

### M14. `SpyJourneyMapPlugin.jmPresent` is cached as `Boolean` (boxed) without volatile

`compat/SpyJourneyMapPlugin.java:24` — `private static Boolean jmPresent = null;` then assigned at line 28 from inside a non-synchronized `if (jmPresent == null)`. If `syncWaypoints` is called concurrently (it shouldn't be on the client tick thread, but it's accessible), the check-then-assign is racey. Benign because `ModList.isLoaded` is deterministic, but should be `volatile`.

---

## LOW

### L1. `TranslationUtil.createComplexMessage` calls `.getString()` on translatable

`util/TranslationUtil.java:96` — `String baseText = Component.translatable(baseKey).getString();`. This eagerly resolves on the server, which uses the *server's* locale rather than each client's. Use `Component.translatable(baseKey, components)` directly so each client renders in its own locale. Affects raid/war chat messages produced this way.

### L2. `ColonyHelper.getPrimaryColony` iterates all colonies per call

`compat/ColonyHelper.java:12-15` — `for (IColony c : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies())` — O(N) every call. If invoked per-tick or per-player-login on a large server (1000+ colonies), adds up. No caching layer. Use `FirstColonyTracker` (already exists, line 104 of `MineColonyTax.java`) where applicable.

### L3. `CrashLogger.logCrash` is duplicated for `Exception` and `Throwable`

`CrashLogger.java:17` and `:50` — two near-identical methods, the second taking `Throwable`. The `Exception` overload can be deleted since `Exception extends Throwable`.

### L4. Unconditional INFO logs

These bypass `TaxConfig.isNormalLogging()` (per CLAUDE.md they should be gated):

- `compat/SpyJourneyMapPlugin.java:30` (wrapped — OK)
- `compat/EasyFactionsBridge.java:74` (wrapped — OK)
- `compat/ColonyBuildingUtil.java:84` — `LOGGER.info("[WnT] MineColonies building API detected: IColony#{}()", name);` — NOT wrapped. Fires once at first call, low impact.
- `util/TickScheduler.java:79` (wrapped — OK)
- `integration/SDMShopIntegration.java:71, 76, 82, 88, 115, 168, 245` — 7 unconditional INFO logs in the static initializer (config not even loaded yet, so wrapping wouldn't work anyway).
- `event/DatapackInjector.java:43, 73, 90, 127` — 4 unconditional INFO logs.
- `event/PatchouliBookHandler.java:61, 117` (wrapped — OK).
- `event/RecipeDisableRuntime.java:123, 131` (wrapped — OK).
- `event/RecipeDisableClient.java:110` (wrapped — OK).
- `event/RecipeDisableEventHandler.java:107, 110, 112` — 3 unconditional within `if (TaxConfig.isDisableHutRecipesEnabled())` but NOT wrapped in `isNormalLogging`; produces a long per-recipe list at every reload when the feature is on.

### L5. `TickScheduler` interval task uses int subtraction without epoch handling

`util/TickScheduler.java:98` — `task.ticksRemaining--;` — `long` arithmetic, safe. No issue.

### L6. `TickScheduler.cancel` on a non-existent id is silently OK

`util/TickScheduler.java:63-68` — `TASKS.remove(taskId)` returns null if not present; method short-circuits. Correct.

### L7. `TickScheduler` cancel-during-iteration is safe

`util/TickScheduler.java:88-115` — uses an `Iterator` and calls `it.remove()` for terminal/cancelled tasks; new tasks added during `task.action.run()` go into the `ConcurrentHashMap` and will be picked up next tick (not this tick), which is the desired semantic.

### L8. `DisabledRecipe.getResultItem` returns `resultItem.copy()` per call

`recipe/DisabledRecipe.java:39-41`. Allocates on each call — fine for a recipe-book display lookup but slightly wasteful if mods call it on every tick. Recipes that never match shouldn't be in any hot loop anyway.

### L9. `ColonyActivityTracker.getInactiveColonies` allocates per call

`util/ColonyActivityTracker.java:119-138` — fresh `ArrayList` each call, plus inner iterations of `IColonyManager.getColonies(world)`. If called from a hot path it allocates; only called from commands today.

### L10. `EasyFactionsPermissionSync` tick-based throttle uses `int scanTicker`

`compat/EasyFactionsPermissionSync.java:62, 73` — `scanTicker` is incremented on every server tick on the main thread (no sync needed). If `getEasyFactionsSyncIntervalTicks()` is set very large by the user, the int can overflow after ~13 years of continuous ticks; benign.

### L11. `DatapackInjector.deleteRecursive` swallows IOException

`event/DatapackInjector.java:140` — `try { Files.deleteIfExists(p); } catch (IOException ignored) {}` — silent failure to delete file means recipes might remain disabled even after config toggle. Should at least log to DEBUG.

### L12. `RecipeCraftBlocker` does NOT compensate ingredients

See H6 — also LOW because the user-facing message tells the player crafting is disabled, but they still lose materials.

### L13. JmImpl waypoint label uses unicode arrow that may not render in all maps

`compat/JmImpl.java:115` — `"Spy base (-> " + ...` — uses ASCII arrow; OK. Other lines use stylized labels.

### L14. `JmImpl.removeWaypoints` doesn't clear `LAST_STATUS` if removal partial

`compat/JmImpl.java:96-99` — only `LAST_STATUS.remove(id)` happens in the `staleKeys` loop (line 70), not in `removeWaypoints` itself. If the status change path at line 44 removes waypoints without subsequently updating `LAST_STATUS`, the cached status becomes stale on next iteration. Actually line 45 does set the new status — OK.

---

## Files Audited (in scope)

- `compat/ColonyBuildingUtil.java` — clean, only call site of MC building API
- `compat/ColonyHelper.java` — O(N) per call (L2)
- `compat/JmImpl.java` — JM-only, gated correctly
- `compat/SpyJourneyMapPlugin.java` — `ModList.isLoaded` guard correct, M14
- `compat/EasyFactionsBridge.java` — clean reflection bridge
- `compat/EasyFactionsPermissionSync.java` — clean, uses TickScheduler-style tick counter
- `compat/ExplosiontCompat.java` — clean
- `util/TickScheduler.java` — correct
- `util/AsyncSaveExecutor.java` — intentional async I/O (H3, acceptable)
- `util/ColonyActivityTracker.java` — minor (M2, M3, L9)
- `util/TranslationUtil.java` — L1 (server-side translation resolution)
- `CrashLogger.java` — M7, L3
- `raid/ReflectionCache.java` — M4, M5
- `integration/SDMShopIntegration.java` — M6, L4
- `TaxConfig.java` — H4 (direct field access), M13 (size)
- `datagen/DisabledRecipeProvider.java` — M8 (duplicated hut block list)
- `datagen/ModDataGenerators.java` — clean
- `datagen/MCTLanguageProvider.java` — not present
- `recipe/DisabledRecipe.java` — clean, L8
- `recipe/DisabledRecipeSerializer.java` — clean
- `recipe/ModRecipeSerializers.java` — clean
- `event/DatapackInjector.java` — H5, M9, L11
- `event/RecipeCraftBlocker.java` — H6, M8, L12
- `event/RecipeDisableRuntime.java` — C2, M8, M11
- `event/RecipeDisableClient.java` — C2, M8, M11
- `event/RecipeDisableEventHandler.java` — M8, M11, L4
- `event/PatchouliBookHandler.java` — M12

## Out-of-scope files referenced

- `network/packets/ColonyDataResponsePacket.java` — C1
- `network/packets/TreasuryDataResponsePacket.java` — C1
- `network/packets/InvestmentDataResponsePacket.java` — C1
- `network/packets/SpyDataResponsePacket.java` — C1
- `network/packets/OfficerDataResponsePacket.java` — C1
- `network/EntityGlowPacket.java` + `network/GlowClientHandler.java` — C1 (lambda capture risk)
- `pvp/PvPManager.java` — H1
- `pvp/PvPBattleManager.java` — H1
- `db/WarStatsDB.java` — H2 (acceptable carve-out)
- `MineColonyTax.java` — registers TickScheduler correctly at line 61
