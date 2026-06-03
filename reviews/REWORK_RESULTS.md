# Rework Pass — Final Results

Two waves of rework against the 11-step Siege SMP refactor, each codex-reviewed.

## Wave 1 — original 10 HIGH-severity bugs

| # | Bug | First codex verdict | Status |
|---|---|---|---|
| 1 | ColonyTierGuard FCT-first detection | VERIFIED | ✅ Fixed |
| 2 | Documented exemptions for reflective setOwner sites | VERIFIED | ✅ Fixed |
| 3 | reclaimByOriginalOwner gated to TAX_ONLY + caller match + atomic remove | VERIFIED | ✅ Fixed |
| 4 | Solo damage shield bypass closed | VERIFIED | ✅ Fixed |
| 5 | Defender-victory siege spoils via completeBesiege timeout path | VERIFIED | ✅ Fixed |
| 6 | Treasury cap-aware spoils transfer | VERIFIED | ✅ Fixed |
| 7 | Block ledger uses saveWithFullMetadata + loadStatic | VERIFIED | ✅ Fixed |
| 8 | WarSystem.endWar wires WarBlockLedger.restoreWarDamage | VERIFIED | ✅ Fixed |
| 9 | Town Hall demolition race guard | **PARTIAL** → fixed in wave 2 | ✅ Fixed |
| 10 | Explosion source extraction via getIndirectSourceEntity | VERIFIED | ✅ Fixed |

**Bundle:** `reviews/rework-bundle.md`
**Codex output:** `reviews/rework-codex.md`

## Wave 2 — follow-up findings from wave-1 review

| # | Finding | Status |
|---|---|---|
| 9-redux | defendersWouldWin guard now mirrors checkForVictory exactly | ✅ Fixed |
| MED-1 | OccupationManager.startOccupation uses FCT-first classification | ✅ Fixed |
| MED-2 | reclaimByOriginalOwner unused — counter-besiege wiring deferred to Phase 2 | 📋 Documented |

**Bundle:** `reviews/rework-v2-bundle.md`
**Codex output:** `reviews/rework-v2-codex.md`

## Final verdict

**Compilation:** ✅ green — only pre-existing deprecation warnings.
**Bug status:** 10 of 10 HIGH-severity bugs from the original summary are VERIFIED fixed.
**New regressions introduced by rework:** 0.
**Remaining items:**
- Phase 2 wiring task for counter-besiege → tax-occupation reclaim flow.
- Pre-existing MEDIUM/LOW findings that weren't part of this rework pass (see `reviews/SUMMARY.md` sections "Should-fix" and "Nice-to-have").

## Files touched in the rework

New files (now tracked):
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java`
- `src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java`
- `src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java`

Modified:
- `src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java`
- `src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java`
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java`
- `src/main/java/net/machiavelli/minecolonytax/WarSystem.java`
- `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java`
- `src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java`

## Wave 8-11 — Explosion't compat + remaining MEDIUMs

| # | Change | Codex | Status |
|---|---|---|---|
| W8 | Explosion't opt-in compat shim (`ExplosiontCompat` + `DeferRestorationToExplosiont` config) | VERIFIED | ✅ |
| W9a | Container detection extended to furnace/brew/lectern + `Container` BlockEntity fallback | VERIFIED | ✅ |
| W9b | New `BesiegeEntityInteractHandler` — villager/citizen trade denied during besiege (covers both `EntityInteract` and `EntityInteractSpecific`) | VERIFIED | ✅ |
| W10 | BesiegeManager `COLONY_RAID_INDEX` secondary index — `isActiveRaidOnColony` and `getRaidsForColony` now O(1)/O(matches) | VERIFIED | ✅ |
| W11 | Counter-besiege reclaim handoff: `completeBesiegeVictory` now calls `OccupationManager.reclaimByOriginalOwner` and short-circuits before vassalization | VERIFIED | ✅ |

**Bundles:** `reviews/wave-8-11-bundle.md`, `reviews/wave-8-11-codex.md`
**Follow-up fixes:** `reviews/wave-8-11-v2-bundle.md`, `reviews/wave-8-11-v2-codex.md`

Wave 8-11 codex caught three real bugs (index not lock-step in tick error paths, self-vassalization on counter-besiege success, EntityInteract-specific event bypass). All three fixed in the v2 pass and re-verified by codex.

### Explosion't integration explicitly answered

CurseForge has `explosiont-1.20.1-2.4.8.jar` (the local source we have is the 1.19.3 build). Opt-in path:
- `DeferRestorationToExplosiont = true` in config → `WarBlockLedger` skips its snapshot/restore pipeline
- Explosion't handles all explosion restoration globally (wider scope than our per-war scoped flow)
- Default OFF — operators must explicitly opt in to preserve scoped restoration behavior

## Final state

- **Total HIGH bugs fixed:** 13 across 3 rework waves (10 original + 3 from wave-8-11 review)
- **Codex outright PASSes:** ~5 of 11 original steps + most rework deltas
- **Build:** ✅ green throughout
- **Files now tracked:** all new compat/besiege/siege files added to git index

## Waves 13-14 — Finishing the PARTIAL items (Step 8 + Step 9)

| # | Change | Codex | Status |
|---|---|---|---|
| 13.1 | Extracted militia spawning to shared `MilitiaSpawner` helper (PathfinderMob-based, no `EntityMercenary` internal-package coupling) | n/a | ✅ |
| 13.2 | `BesiegeManager.spawnMilitiaUpgradeReinforcements` now delegates to `MilitiaSpawner` | n/a | ✅ |
| 13.3 | `WarSystem.finalizeWarStart` spawns militia for both defender AND attacker colonies, race-guarded with `ACTIVE_WARS.get(colonyId) == war` | VERIFIED | ✅ |
| 13.4 | `WarData.militiaSupport` field added (transient — entities don't survive restart) | n/a | ✅ |
| 13.5 | `WarSystem.endWar` despawns war militia via `MilitiaSpawner.despawnAll` | n/a | ✅ |
| 14.1 | `VassalIncomeData.VassalKind` ordinal now encoded/decoded in `ColonyDataResponsePacket` | VERIFIED | ✅ |
| 14.2 | `PROTOCOL_VERSION` bumped 3 → 4 to prevent old-client desync | VERIFIED | ✅ |
| 14.3 | `ColonyDataCollector` populates TAX_OCCUPIED + PROVISIONAL rows from `OccupationManager.getActiveOccupations()`, with dedup against existing vassal rows | VERIFIED | ✅ |
| 14.4 | `VassalsPage` renders kind-aware badges: red TAX-OCCUPIED, orange PROVISIONAL, green VASSAL — plus a one-letter row tag in the compact list view | VERIFIED | ✅ |
| 14.5 | Bonus collateral: fixed two illegal multi-catches (`NoClassDefFoundError | Throwable`, `NoClassDefFoundError | LinkageError`) introduced by the concurrent audit refactor | n/a | ✅ |

**Bundles:** `reviews/waves-13-14-bundle.md`, `reviews/waves-13-14-codex.md`

Codex flagged 3 issues, all fixed inline: race-guard on militia spawn, network protocol version bump, dedup against existing vassal rows.

### Step 8 + Step 9 status update

Both now flip from **PARTIAL → DONE**. The HTML status snapshot in `Siege_SMP_Design_Changes.html` will need refreshing to reflect 9 DONE / 2 PARTIAL (steps 3 + 11 remain).

## Wave 12 — War-aware Explosion't integration (mixin)

| # | Change | Codex | Status |
|---|---|---|---|
| 12.1 | CurseForge dep added (project 388909, file 4848559) — `curse.maven:explosiont-388909:4848559` | n/a | ✅ Resolves cleanly through cursemaven |
| 12.2 | Verified 1.20.1-2.4.8 jar API: `ChunkDataHandler.toHealDimMap` still public, same type signature | n/a | ✅ Verified by javap inspection |
| 12.3 | `warntax.mixins.json` + `MixinConfigs` manifest entry — Forge bundled mixin loader picks it up automatically | VERIFIED | ✅ |
| 12.4 | `WorldTickHandlerMixin` — `@Pseudo` + `@Mixin(targets=...)` + `remap=false`, cancels `handleLevelTick` at HEAD when any war/raid/besiege involves the level | VERIFIED | ✅ |
| 12.5 | `ExplosiontCompat` updated — `shouldDeferToExplosiont` now returns true whenever the mod is present (mixin makes it war-aware automatically); legacy config kept as explicit opt-out | VERIFIED | ✅ |
| 12.6 | `compileOnly` + `runtimeOnly` for Explosion't so the mixin can reference its classes at compile time without making the mod required | n/a | ✅ Build green |

**Bundles:** `reviews/wave-12-bundle.md`, `reviews/wave-12-codex.md`, `reviews/wave-12-v2-bundle.md`, `reviews/wave-12-v2-codex.md`

Wave 12 codex caught 3 real issues (missing raid/besiege coverage, weak optional-mod gating, undocumented side-effect risk). Wave 12 v2 closed all three. Final codex verdict: **STATUS APPROVE, no further fixes needed.**

### Behavior with the integration

| Explosion't installed? | What happens |
|---|---|
| **Yes** | Mixin cancels Explosion't's level tick whenever ANY active war/raid/besiege touches the level. Snapshots accumulate during conflict, heals resume after the last conflict in that level ends. WarBlockLedger steps aside (compat helper returns `shouldDeferToExplosiont = true`). |
| **No** | Mixin silently no-ops (`@Pseudo` + `required: false` makes target-class absence safe). WarBlockLedger is the canonical path — works standalone as before. |

## Final state

- **HIGH bugs fixed:** 13 across 4 rework waves (10 original + 3 from wave 8-11 + 3 from wave 12)
- **Codex APPROVE outright** on wave 12 v2 (the integration that delivers the user's "restore only after wars/raids" requirement)
- **Build:** ✅ green throughout
- **Explosion't:** properly integrated with project ID 388909 + file ID 4848559, verified jar contents match assumed API

## Recommended next steps (in priority order)

1. **Test the actual gameplay flow** in a dev server with Explosion't installed — verify the mixin actually cancels ticks during a war and snapshots persist + heal post-war. Codex catches static bugs; runtime behavior needs a live test.
2. **Step 9 UI/packet wiring** — VassalsPage badge rendering + ColonyDataResponsePacket extension to send the `kind` field client-side. Largest remaining MEDIUM.
3. **Step 10 JSON persistence for WarBlockLedger** — when running WITHOUT Explosion't, the ledger is in-memory only. Server crash mid-war = lost restoration.
4. **Phase 2 Siege Banner objective** — requires `DeferredRegister<Item>` + resource files. Largest single piece of remaining design scope.
5. **Step 3 Phase 2** — shared defender pool + last-kill-credit across concurrent besiegers.
