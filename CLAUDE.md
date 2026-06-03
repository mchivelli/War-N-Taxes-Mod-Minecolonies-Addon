# CLAUDE.md - War 'N Taxes Mod (NeoForge 1.21.1 port)

## Project Overview
Minecraft **1.21.1 NeoForge** port of the War 'N Taxes MineColonies addon. Adds war,
taxation, espionage, occupation, and colony management mechanics to multiplayer servers.

- **This is the 1.21.1 / NeoForge line.** The original 1.20.1 / Forge line lives in a
  separate repo: `C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon` (branch `1.20.1`).
- **Branch**: `neoforge-1.21.1`
- **Mod version**: `4.0.0` (major bump signifies the breaking Forge -> NeoForge migration)
- **Build**: `./gradlew build`  (Gradle 8.14, NeoGradle 7.1.1, Java 21)
- **Run (dev)**: `./gradlew runClient` or `./gradlew runServer`
- **Toolchain**: Java 21 (pinned via `org.gradle.java.home` in `gradle.properties`);
  NeoForge `21.1.213`; MC `1.21.1`; Parchment `2024.11.17`.

### Dependencies (1.21.1 NeoForge, via CurseMaven)
- MineColonies `minecolonies-245506:7162708` + Structurize, Multi-Piston, BlockUI, Domum Ornamentum
- SDMShop `sdm-shop-948942:7173785` (economy API not on 1.21.1 yet -> compat layer)
- FTB Teams `ftb-teams-forge-404468:7170718`

---

## NeoForge migration rules (what differs from the 1.20.1 Forge line)

This codebase was ported from Forge. When editing, use the NeoForge idioms below, NOT the
old Forge ones. Migration is in progress; treat any remaining Forge-ism as a bug to fix.

### Package names
- `net.minecraftforge.*` -> `net.neoforged.*`
  - `net.minecraftforge.fml.*` -> `net.neoforged.fml.*`
  - `net.minecraftforge.common/event/...` -> `net.neoforged.neoforge.common/event/...`
  - `net.minecraftforge.eventbus.*` -> `net.neoforged.bus.*`
  - `net.minecraftforge.server.*` -> `net.neoforged.neoforge.server.*`
- `MinecraftForge.EVENT_BUS` -> `NeoForge.EVENT_BUS`; mod-bus events use `Bus.MOD`, game events `Bus.GAME`.

### Player war data: Data Attachments (NOT Capabilities)
- The Forge Capability system is gone. Player war data uses NeoForge **Data Attachments**.
- Entry point: `attachment/PlayerWarDataAttachment.java`. Access is just:
  ```java
  PlayerWarData data = PlayerWarDataAttachment.get(player); // auto-persisted, no markDirty()
  ```
- Never reintroduce `LazyOptional` / `Capability` / `CapabilityToken`.

### Networking: CustomPacketPayload (NOT SimpleChannel)
- Forge `SimpleChannel.registerMessage(...)` is replaced by NeoForge payloads.
- Packets live in `network/packets/` as `record X(...) implements CustomPacketPayload`
  with a `public static final Type<X> TYPE = new Type<>(ResourceLocation)` and a `StreamCodec`.
- Registration: `network/ModNetworking.java` (on `RegisterPayloadHandlersEvent`).
  `NetworkHandler.java` is the legacy registrar being phased out — prefer `ModNetworking`.
- `*.java.old` files in `network/` are migration leftovers; they are not compiled and should
  be deleted once their replacements are confirmed working.

### Config: ModConfigSpec (NOT ForgeConfigSpec)
- All config in `TaxConfig.java` uses `net.neoforged.neoforge.common.ModConfigSpec`.
- Same rules as the Forge line: add accessor methods at the bottom; group keys by feature
  sub-section; gate `LOGGER.info()` behind `TaxConfig.isDebugLogging()` / `isNormalLogging()`.

### Registries
- `ForgeRegistries.*` -> `BuiltInRegistries.*`; registration via NeoForge `DeferredRegister`.
- `new ResourceLocation(ns, path)` -> `ResourceLocation.fromNamespaceAndPath(ns, path)` /
  `ResourceLocation.parse(str)`.

### Events / ticks
- `TickEvent.ServerTickEvent` -> `ServerTickEvent.Post` / `ServerTickEvent.Pre`;
  `TickEvent.ClientTickEvent` -> `ClientTickEvent.Post`.
- Mod metadata is `src/main/resources/META-INF/neoforge.mods.toml` (NOT `mods.toml`).

---

## Critical: MineColonies Building API Compatibility (UNCHANGED from 1.20.1)

**Never call `colony.getBuildingManager()` or `colony.getServerBuildingManager()` directly.**
Use the reflection shim:
```java
ColonyBuildingUtil.getBuildings(colony)
// src/main/java/net/machiavelli/minecolonytax/compat/ColonyBuildingUtil.java
```
It detects the available MineColonies API at runtime and caches it. Bypassing it breaks
cross-version compatibility.

---

## Architecture: Scheduling (UNCHANGED from 1.20.1)

**Never use `java.util.Timer` or `new Thread(...)` for deferred tasks.** Use `TickScheduler`
(`util/TickScheduler.java`) so all state mutations stay on the main server thread:
```java
TickScheduler.scheduleDelayed(runnable, delayMs);
TickScheduler.scheduleRepeating(runnable, initialDelayMs, intervalMs);
TickScheduler.cancel(taskId);
```

---

## Architecture: Key Systems (same systems as the 1.20.1 line)

| System | Entry Point | Notes |
|--------|-------------|-------|
| Taxation | `TaxManager.java` | Timer-based, runs on server thread |
| War | `WarSystem.java` | War lifecycle, persistence, victory/defeat |
| Raids | `RaidManager.java` | Per-colony raid tracking with grace periods |
| Occupation | `OccupationManager.java` | Post-war occupation phase, persists to JSON |
| Espionage | `SpyManager.java` | Mission lifecycle; `SpyEntity.java` is the in-world entity |
| Random Events | `RandomEventManager.java` | Triggered during tax cycles |
| Colony Abandonment | `ColonyAbandonmentManager.java` | Permissions/citizen APIs only |
| Config | `TaxConfig.java` | All config; use accessor methods, ModConfigSpec |

---

## Persistence Pattern (UNCHANGED)
Gson/JSON for all persistence (not NBT). Files live in `config/warntax/`. Player war data is
the exception — it uses Data Attachments (auto-serialized), see above.

---

## JourneyMap Integration (UNCHANGED)
All JourneyMap access is guarded by `ModList.isLoaded("journeymap")` in `SpyJourneyMapPlugin.java`;
real impl in `JmImpl.java`, loaded only when JM is present. Never reference JM outside `JmImpl.java`.

---

## Migration / build status
- Build target: get `./gradlew build` green, then commit to `neoforge-1.21.1`.
- Known migration leftovers to clean up before committing: `network/*.java.old` files, and the
  pile of one-off `fix-*.ps1` / `create-*.ps1` scripts + stray `*_STATUS.md` / `*_SUMMARY.md`
  docs in the repo root (Windsurf-era migration scaffolding, not source).
- The older root status docs (`PROGRESS_SUMMARY.md`, `SESSION_SUMMARY.md`, `MIGRATION_STATUS.md`)
  are from Nov 2025 and UNDERSTATE progress — networking was actually migrated in March 2026.

---

## GitNexus
This port is a separate codebase from the 1.20.1 repo's GitNexus index. To get code intelligence
here, run `npx gitnexus analyze` in this folder (the 1.20.1 repo's index does NOT cover this code —
running GitNexus tools without indexing here will return results for the wrong codebase).

## Wiki
The wiki lives in `wiki/` (shared content with the 1.20.1 line). Plain English, no emojis, no
technical class names in player-facing pages.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **MinecolonyTaxAddon-Dev-1.21 Neoforge** (50925 symbols, 139191 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/MinecolonyTaxAddon-Dev-1.21 Neoforge/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/MinecolonyTaxAddon-Dev-1.21 Neoforge/context` | Codebase overview, check index freshness |
| `gitnexus://repo/MinecolonyTaxAddon-Dev-1.21 Neoforge/clusters` | All functional areas |
| `gitnexus://repo/MinecolonyTaxAddon-Dev-1.21 Neoforge/processes` | All execution flows |
| `gitnexus://repo/MinecolonyTaxAddon-Dev-1.21 Neoforge/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## Keeping the Index Fresh

After committing code changes, the GitNexus index becomes stale. Re-run analyze to update it:

```bash
npx gitnexus analyze
```

If the index previously included embeddings, preserve them by adding `--embeddings`:

```bash
npx gitnexus analyze --embeddings
```

To check whether embeddings exist, inspect `.gitnexus/meta.json` — the `stats.embeddings` field shows the count (0 means no embeddings). **Running analyze without `--embeddings` will delete any previously generated embeddings.**

> Claude Code users: A PostToolUse hook handles this automatically after `git commit` and `git merge`.

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
