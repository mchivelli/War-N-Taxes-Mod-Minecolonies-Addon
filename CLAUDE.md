# CLAUDE.md - War 'N Taxes Mod

## Project Overview
Minecraft 1.20.1 Forge mod. A MineColonies addon that adds war, taxation, espionage, occupation, and colony management mechanics to multiplayer servers.

- **Build**: `./gradlew build`
- **Run (dev)**: `./gradlew runClient` or `./gradlew runServer`
- **Branch**: `1.20.1`

---

## Critical: MineColonies Building API Compatibility

**Never call `colony.getBuildingManager()` or `colony.getServerBuildingManager()` directly.**

All building access must go through:
```java
ColonyBuildingUtil.getBuildings(colony)
// src/main/java/net/machiavelli/minecolonytax/compat/ColonyBuildingUtil.java
```

This shim uses reflection to detect which API version is present at runtime and caches the result. It handles both old (`getBuildingManager`) and new (`getServerBuildingManager`) MineColonies API versions without separate builds. Bypassing it will break compatibility or cause `NoClassDefFoundError` on older installs.

---

## Architecture: Key Systems

| System | Entry Point | Notes |
|--------|-------------|-------|
| Taxation | `TaxManager.java` | Timer-based, runs on server thread |
| War | `WarSystem.java` | Manages war lifecycle, persistence, victory/defeat |
| Raids | `RaidManager.java` | Per-colony raid tracking with grace periods |
| Occupation | `OccupationManager.java` | Post-war occupation phase, persists to JSON |
| Espionage | `SpyManager.java` | Mission lifecycle; `SpyEntity.java` is the in-world entity |
| Random Events | `RandomEventManager.java` | Triggered during tax cycles |
| Colony Abandonment | `ColonyAbandonmentManager.java` | Uses permissions/citizen APIs only |
| Task Scheduling | `TickScheduler.java` | Use this instead of `java.util.Timer`. Runs on main server thread. |
| Config | `TaxConfig.java` | All mod config values. Use accessor methods, not fields directly. |

---

## Architecture: Scheduling

**Never use `java.util.Timer` or `new Thread(...)` for deferred tasks.**

Use `TickScheduler`:
```java
TickScheduler.scheduleDelayed(runnable, delayMs);
TickScheduler.scheduleRepeating(runnable, initialDelayMs, intervalMs);
TickScheduler.cancel(taskId);
```

This keeps all state mutations on the main server thread, avoiding concurrency bugs.

---

## Architecture: Spy System State Machine

Spy missions follow this lifecycle:
```
DEPLOYING -> ACTIVE -> (FLEEING -> ESCAPED | KILLED) | RECALLED -> RETURNING -> COMPLETED
```

- **DEPLOYING**: Spy is traveling; no entity in world yet. Entity spawns when travel completes.
- **ACTIVE**: Spy entity exists at target colony; intel accumulates over time in tiers.
- **FLEEING**: Detection triggered; spy entity attempts to pathfind out of colony border.
- **ESCAPED / RECALLED**: Mission complete; intel preserved in COMPLETED_MISSIONS for 1 hour.
- **KILLED**: Intel destroyed immediately; not added to COMPLETED_MISSIONS.

---

## Architecture: War Persistence

Wars are saved on `ServerStoppingEvent` and restored on `ServerStartingEvent`. The save file is `config/warntax/active_wars.json`. It is deleted after successful load to prevent double-restoration.

When modifying `WarData`, also update the 27-parameter restoration constructor so persisted wars can be correctly rebuilt.

---

## JourneyMap Integration

All JourneyMap access is guarded by `ModList.isLoaded("journeymap")` in `SpyJourneyMapPlugin.java`. The actual implementation lives in `JmImpl.java` and is only loaded when JM is present. Never reference JM classes directly outside of `JmImpl.java`.

---

## Persistence Pattern

The mod uses Gson/JSON for all persistence (not NBT). Files live in `config/warntax/`. Existing files:
- `active_wars.json` — in-progress wars (written on shutdown, deleted after restore)
- `occupations.json` — active occupation states
- `minecolonytax.toml` — main config (managed by Forge)

---

## Config Guidelines

- All config keys are in `TaxConfig.java`. Add new keys in the correct section (there are grouped sub-sections for war, spy, occupation, etc.).
- Accessor methods are at the bottom of `TaxConfig.java`. Always add one when adding a new config field.
- Log level is controlled by `TaxConfig.isDebugLogging()` / `TaxConfig.isNormalLogging()`. Wrap `LOGGER.info()` calls with one of these; never add unconditional info-level logging.

---

## Wiki

The wiki lives in `wiki/`. Keep it up to date when adding features. Plain English, no emojis, no technical class names in player-facing pages.

Key pages:
- `War_System.md` — war, high stakes, occupation outcome, persistence
- `Occupation_System.md` — full occupation mechanic reference
- `Espionage_System.md` — spy travel, intel tiers, flee, recall
- `Configuration_Guide.md` — all config keys with defaults
- `Commands_&_Permissions.md` — all `/wnt` commands

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **War-N-Taxes-Mod---Minecolonies-Addon** (52572 symbols, 141396 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

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
3. `READ gitnexus://repo/War-N-Taxes-Mod---Minecolonies-Addon/process/{processName}` — trace the full execution flow step by step
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
| `gitnexus://repo/War-N-Taxes-Mod---Minecolonies-Addon/context` | Codebase overview, check index freshness |
| `gitnexus://repo/War-N-Taxes-Mod---Minecolonies-Addon/clusters` | All functional areas |
| `gitnexus://repo/War-N-Taxes-Mod---Minecolonies-Addon/processes` | All execution flows |
| `gitnexus://repo/War-N-Taxes-Mod---Minecolonies-Addon/process/{name}` | Step-by-step execution trace |

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
