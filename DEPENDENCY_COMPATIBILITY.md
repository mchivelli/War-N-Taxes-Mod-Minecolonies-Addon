# Dependency Compatibility — Latest 1.20.1 Forge Stack

**Checked:** 2026-06-03 against MineColonies **1.20.1-1.1.1237-snapshot** (CurseForge file `8186502`, released 2026-06-02) and the latest 1.20.1-Forge build of every other dependency.

## Verdict

✅ **War 'N Taxes is compatible with the latest MineColonies + the full latest dependency stack** — *provided the whole MineColonies stack is upgraded together.*

The one hard break found: our pinned **Structurize was `1.0.800`**, but MineColonies 1.1.1237 **mandates `structurize >= 1.0.806`**. Bumping MineColonies alone would crash at load (`Missing or unsupported mandatory dependencies`). Fixed by upgrading the whole coherent stack (below).

### How this was verified
1. Our code **compiles** against MineColonies 1.1.1237 (`./gradlew compileJava` — BUILD SUCCESSFUL, only pre-existing deprecation warnings). Covers every compile-time MineColonies API we use (`ModEntities.MERCENARY`, `IBuilding.isInBuilding`, `ITownHall`, `Rank.isHostile`, eventbus events, `IColony*`).
2. The full **runtime classpath resolves** cleanly with all updated deps (`./gradlew dependencies --configuration runtimeClasspath` — BUILD SUCCESSFUL, no unresolved).
3. **FML-style range check**: every mandatory `versionRange` in each mod's own `mods.toml` is satisfied by the chosen set (verified by extracting `META-INF/mods.toml` from each jar). Chains checked: MineColonies → Structurize/BlockUI/Domum/Multi-Piston; FTB Teams → FTB Library/Architectury; SDM Shop → SDM Engine Core/Economy/UI-Lib/FTB Library/Architectury.
4. The two reflection-based paths (`ColonyBuildingUtil`, mercenary spawn in `MilitiaSpawner`) are version-agnostic by design (try-both-method-names / `NoClassDefFoundError` guard).

> Final empirical step (human): a `./gradlew runServer` / `runClient` smoke test to confirm in-game load. All deterministic checks above pass.

## The verified coherent set (in `build.gradle`)

| Mod | Old file | Old ver | New file | New ver | Action |
|-----|----------|---------|----------|---------|--------|
| **MineColonies** (245506) | 7629580 | 1.1.x | **8186502** | 1.1.1237-snapshot | ⬆ bump (trigger) |
| **Structurize** (298744) | 7532330 | 1.0.800 | **8138301** | 1.0.816 | ⬆ **bump — MANDATORY** (MC needs ≥1.0.806; 1.0.800 too old) |
| **BlockUI** (522992) | 7541343 | 1.0.193 | **7606230** | 1.0.194-snapshot | ⬆ bump (old already ≥1.0.190) |
| **Domum Ornamentum** (527361) | 7585567 | 1.0.296 | **8179147** | 1.0.301-snapshot | ⬆ bump (old already ≥1.0.288) |
| **Multi-Piston** (303278) | 5204918 | 1.2.43-RELEASE | **7097889** | 0.0.47-snapshot | ⬆ bump (Structurize hard dep; re-versioned 1.2.x→0.0.x) |
| **JEI** (238222) | 5739402 | 15.x | **7920915** | 15.20.0.130 | ⬆ bump |
| **FTB Teams** (404468) | 5267190 | 2001.x | **7499810** | 2001.3.2 | ⬆ bump (needs ftblib ≥2001.2.0 ✓) |
| **Recruits** (523860) | 6500292 | 1.x | **7906232** | 1.15.0 | ⬆ bump |
| **SDM Shop** (948942) | 7395729 | 7.1.13 | **7935087** | 7.2.2 | ⬆ bump (needs ui-lib 1.8.3, econ 2.2.0, ftblib ≥2001.2.9, arch ≥9.2.14 — all ✓) |
| **SDM Engine Core** (964997) | 5761090 | 2.1.1 | **7090174** | 2001.4.0 | ⬆ bump (needs arch ≥9.2.14 ✓) |
| Architectury (419699) | 5137938 | 9.2.14 | 5137938 | 9.2.14 | ✔ keep — **last 1.20.1 build** |
| FTB Library (404465) | 7296748 | 2001.2.12 | 7296748 | 2001.2.12 | ✔ keep — **last 1.20.1 build** |
| JourneyMap (32274) | 5789363 | 5.10.3 | 5789363 | 5.10.3 | ✔ keep — **last 1.20.1 build** |
| Explosion't (388909) | 4848559 | 2.4.8 | 4848559 | 2.4.8 | ✔ keep — **last 1.20.1 build** (Nov 2023; none newer) |
| SDM Economy (1102542) | 6689080 | 2.2.0 | 6689080 | 2.2.0 | ✔ keep — **last 1.20.1 build** |
| SDM UI Lib (1095061) | 6086204 | 1.8.3 | 6086204 | 1.8.3 | ✔ keep — **last 1.20.1 build** |

## Caveats / ceilings for the 1.20.1 line

- **No headroom on Architectury / SDM UI Lib / SDM Economy.** SDM Shop 7.2.2 requires *exactly* `architectury ≥9.2.14`, `sdm_ui_lib ≥1.8.3`, `sdmeconomy ≥2.2.0` — and those are the **final** 1.20.1 builds. A future SDM Shop update could demand newer Architectury/UI-Lib that will never exist for 1.20.1, which would strand it. **SDM Shop 7.2.2 is effectively the ceiling for 1.20.1.**
- **Explosion't** has no 1.20.1 build newer than 2.4.8 (file 4848559). Confirmed.
- **MineColonies main branch** (`version/main`) is still 1.20.1 Forge — they have not moved main to 1.21/NeoForge — so 1.20.1 MineColonies continues to receive snapshots.
- Several deps (Architectury, JourneyMap, FTB Library, SDM Economy/UI-Lib) have far newer releases overall, but those target 1.20.6 / 1.21.x only. For **1.20.1 Forge** the versions above are the latest.

## If you stay on the old MineColonies

The previously committed set (MineColonies 7629580 + Structurize 1.0.800 + …) is itself internally coherent and works. The upgrade above is only required to run the *latest* MineColonies.
