# Siege SMP — Testing Guide

Manual / QA test plan for the Siege SMP feature set (colony tiers, multi-besieger
raids, victory objectives, militia investment, persistent explosion damage, and the
Explosion't war-aware integration).

Codex catches static bugs; **behavioural correctness needs a live run.** This guide is
the live-run checklist. Work top to bottom — later scenarios assume the earlier
setup. Most scenarios need **two accounts** on the same server (one attacker, one
defender). A second Minecraft instance or a friend both work; a single account can
cover the build/smoke/persistence-file checks but not the combat loop.

---

## 0. Environment setup

| Step | Command / action | Expected |
|------|------------------|----------|
| Build | `./gradlew build` | `BUILD SUCCESSFUL`, jar in `build/libs/` |
| Dev server | `./gradlew runServer` | Server boots, no `ERROR`/exception on load |
| Dev client (×2) | `./gradlew runClient` (or a 2nd packaged instance) | Both connect |
| Verify mod loaded | server log on start | `War 'N Taxes` mod loads; MineColonies present |

> The two-player loop is easiest on a **dedicated dev server** (`runServer`) with two
> external clients, because besiege "attacker is solo / both-online for full war"
> rules depend on real online/offline state.

### 0.1 Config prep (`config/warntax/minecolonytax.toml`)

Stop the server once after first boot so the TOML is generated, then edit it. Keys
and their TOML sections (verified against `TaxConfig.java`):

**`[General]`**
```toml
LogLevel = 2   # 0=minimal, 1=normal summaries, 2=debug. Use 2 while testing.
```

**`[War Settings]`** — siege/tier/objective keys live here (NOT under `[Besiege System]`):
```toml
EnablePrimaryColonyTransfer        = false  # keep false first; flip to true only for tier test E2
PrimaryColonyTaxOccupationDays     = 7
BesiegeSpoilPercentOfLoserTreasury = 25
EnableExperimentalSiegeObjectives  = true   # REQUIRED for banner + townhall demolition tests (default false!)
TownHallExplosiveHitsRequired      = 5
TownHallHitCooldownMinutes         = 5      # set to 1 to test faster
MaxSiegeRadius                     = 500
AttackerGlowSeconds                = 30
BannerCaptureMinutes               = 10     # set to 1-2 to test faster
DeferRestorationToExplosiont       = false  # false = built-in ledger; true = hand off to Explosion't
```

**`[Besiege System]`**
```toml
EnableBesiegeSystem      = true
BesiegeDurationMinutes   = 20   # lower to ~3 for faster cycles
BesiegeMilitiaPercent    = 0.6
BesiegeTributePercent    = 30
BesiegeAlliesEnabled     = true
```

**`[War Settings.Colony Occupation]`**
```toml
EnableOccupationSystem = true
OccupationDurationDays = 7    # lower for faster tax-occupation expiry tests
```

**`[ColonyUpgrades]`**
```toml
UpgradeMilitiaCostBase             = 500
UpgradeMilitiaMultiplierPerLevel   = 0.10
```

> Restart the server after editing the TOML. The siege-objective scenarios (H, I)
> **will silently do nothing** if `EnableExperimentalSiegeObjectives = false` — this
> is the #1 testing gotcha.

---

## 1. Command cheat-sheet (verified)

All non-OP unless marked **[OP]** (`/op <player>` or single-player cheats on).

| Action | Command |
|--------|---------|
| Declare full war | `/wnt wagewar <colony>` (or `/wnt wagewar <colony> <extortionPercent>`) |
| Accept / decline war | `/wnt war accept <colonyId>` · `/wnt war decline <colonyId>` |
| Join / leave / pick side | `/wnt joinwar` · `/wnt leavewar` · `/choosewarside attacker|defender` |
| War report | `/wnt warinfo` · **[OP]** `/wnt debug war` |
| Start a besiege (or reclaim, as former owner) | `/wnt besiege <colony>` |
| Besiege status | `/wnt debug besiege` (OP gets admin view) |
| Sue for peace | `/wnt peace whitepeace` · `/wnt peace reparations <amount>` · `/wnt peace accept|decline` |
| Vassalize | `/wnt vasalize <percent> <colony>` · `/wnt vassalaccept <colonyId>` · `/wnt vasals` |
| Treasury | `/wnt treasury status [colonyId]` · `/wnt treasury deposit <amount> [tax|wallet|inventory]` · `/wnt treasury withdraw ...` |
| Militia / upgrades | `/wnt invest list` · `/wnt invest status` · `/wnt invest buy MILITIA` |
| **[OP]** Force-end war | `/wnt warstop <colony>` · `/wnt warstopall` |
| **[OP]** Stop raid | `/wnt raidstop` |
| Siege banner (manual) | `/give <player> minecolonytax:siege_banner` (normally auto-granted on war start) |

> There is **no** `/wnt givebanner`, `/wnt militia`, or force-besiege command. The
> banner is auto-handed to attackers on the `INWAR` transition; militia come from the
> investment upgrade and besiege conversion. Admin levers are `warstop`/`warstopall`/
> `raidstop` and `/wnt debug besiege`.

---

## 2. Core feature scenarios

Mark each ☐ → ✅/❌. Capture the server `latest.log` for any ❌.

### A. Smoke test
- ☐ Server starts with both `minecolonytax` and `minecolonies` loaded, no stacktrace.
- ☐ `/wnt help` lists commands.
- ☐ Two test colonies exist (create one per player; note their IDs and names).
- ☐ `config/warntax/` directory is created and `minecolonytax.toml` is present.

### B. Colony tiers & ownership transfer guard (`ColonyTierGuard`)
Setup: Player A owns colony **A1** (their *first* colony = Primary). Player B owns **B1**.
- ☐ **Primary is safe by default:** with `EnablePrimaryColonyTransfer = false`, run a full war where A1 (primary) loses. Expected: A1 is **tax-occupied** (taxes diverted) but ownership does **not** transfer to the winner.
- ☐ **Primary reclaim:** former owner A runs `/wnt besiege A1` → on success the tax-occupation is cleared (reclaim), no self-vassalization, A keeps ownership.
- ☐ **Secondary is claimable:** give Player A a *second* colony **A2** (non-primary). War/besiege A2 to completion. Expected: after a long-enough besiege, A2 ownership transfers permanently to the winner.
- ☐ **Primary transfer when enabled:** set `EnablePrimaryColonyTransfer = true`, restart, repeat the A1-loses war. Expected: now A1 ownership *can* transfer (config-gated path).

### C. Full war loop
Both players online (full war requires both online).
- ☐ A: `/wnt wagewar B1`. B receives a war/extortion prompt.
- ☐ B: `/wnt war accept <B1 id>`. War enters join phase; boss bar appears for involved players only.
- ☐ Allies use `/wnt joinwar` + `/choosewarside ...` during the join phase; boss bar adds them.
- ☐ `/wnt warinfo` shows correct attacker/defender teams, lives, and guard counts.
- ☐ Kill enemy guards / players; counts in `/wnt warinfo` decrement.
- ☐ War resolves on the victory condition (lives/guards exhausted or objective met). Loser pays a tax/spoil to the winner.
- ☐ **[OP]** `/wnt warstopall` cleanly ends any war (use to reset between runs).

### D. Besiege — single besieger
Besiege happens when only the attacking party is online (asymmetric).
- ☐ A: `/wnt besiege B1`. Besiege starts; `/wnt debug besiege` shows it active.
- ☐ **During besiege the defender colony's chests and villagers/citizens are NOT interactable** by the besieger (right-click does nothing / denial message). Verify on a chest and a citizen.
- ☐ Besiege runs for `BesiegeDurationMinutes`; on attacker success the loser pays `BesiegeSpoilPercentOfLoserTreasury`% of treasury (check `/wnt treasury status` before/after on both colonies).
- ☐ Spoils are **cap-aware**: if the winner's treasury is near cap, no coins vanish (deposit only up to headroom).
- ☐ Besieged colony appears in the **Vassals tab** with a `TAX-OCCUPIED` (red) or `PROVISIONAL` (orange) badge.

### E. Besiege — multiple simultaneous besiegers
- ☐ While A besieges B1, a third player C also runs `/wnt besiege B1`. Both besieges register (B1 is besieged by two parties at once).
- ☐ `/wnt debug besiege` lists both raids against B1.
- ☐ When the besiege resolves, **first-resolved-wins** — exactly one besieger gets the credit/spoils; the message reads "resolved first", not a double award.
- ☐ No crash / no double-spoil / no duplicated occupation entry.

### F. Asymmetric allies (defender notification)
- ☐ Start a besiege on B1 while an ally of B (owner/officer/friend) is online. The ally receives a **clickable notification** to defend.
- ☐ The ally can travel in and fight the besieger; the besieger remains solo (no besieger-side allies).

### G. Militia investment (`MilitiaSpawner` / `ColonyUpgradeManager`)
- ☐ `/wnt treasury deposit 5000 wallet` (fund the treasury), then `/wnt invest buy MILITIA` a few times. `/wnt invest status` shows the MILITIA level rising.
- ☐ Start a war/besiege on the invested colony. Extra militia spawn to defend, scaled by guard count × `(multiplier × level)` (≈ +5 at 10 guards / +50 at 100 at cap).
- ☐ **Militia do NOT count as victory objectives** — confirm via `/wnt warinfo` that only guards + player lives drive the win condition; killing militia does not advance victory.
- ☐ Militia despawn when the war/besiege ends (no leftover mobs).
- ☐ Militia support also applies to **besieges and raids**, not just full war.

### H. Victory objective — Plant the Banner (`PlantTheBannerObjective`)
Requires `EnableExperimentalSiegeObjectives = true`.
- ☐ On war start the attacker is auto-given a **Siege Banner** (`minecolonytax:siege_banner`). If offline at start, they get it on next login. (`/give` to force.)
- ☐ Placing the banner **outside** the town-hall borders is rejected (placement cancelled, **item not consumed**).
- ☐ Placing it **inside** the town-hall borders starts a capture **boss bar visible only to war participants**, counting down `BannerCaptureMinutes`.
- ☐ A **defender** breaking the planted banner stops the capture and consumes a replant (defender-only); a non-defender breaking it does not consume the replant budget.
- ☐ If the timer completes, the **attacker wins** the war (race-guarded — a simultaneous defender win is handled).

### I. Victory objective — Town Hall demolition (`TownHallDemolitionObjective`)
Requires `EnableExperimentalSiegeObjectives = true`. Set `TownHallHitCooldownMinutes = 1` to test fast.
- ☐ Detonate explosives (TNT / siege weapon) **on the town-hall building** (the building footprint via `IBuilding.isInBuilding`, not just the single block). A hit registers.
- ☐ Each hit requires the attacker to be within `MaxSiegeRadius` (500) blocks; a hit from farther away does not count.
- ☐ On each hit the attacker is made to **glow** for `AttackerGlowSeconds` and their **coordinates are broadcast** to the defenders.
- ☐ A second hit within `TownHallHitCooldownMinutes` does **not** count (per-attacker cooldown).
- ☐ After `TownHallExplosiveHitsRequired` valid hits, the **attacker wins**.

### J. Block-interaction filter during war (`BlockInteractionFilterHandler`)
- ☐ During an active war, a participant **cannot break blocks by hand** (break is cancelled).
- ☐ **Explosive damage still destroys blocks** (TNT works).
- ☐ Right-click interactables still work: **chests/containers, doors**, and **entity damage** are allowed (except where besiege denial in D applies).

---

## 3. Persistent damage & restoration

### K. WarBlockLedger basic restore (`DeferRestorationToExplosiont = false`)
- ☐ Start a war on a colony. Note a few specific block coordinates near the town hall.
- ☐ Blow up those blocks with TNT during the war. They are destroyed in-world.
- ☐ With `LogLevel = 2`, the log shows ledger snapshots being recorded.
- ☐ End the war (`/wnt warstop <colony>` or natural end). Log shows
  `WarBlockLedger restoring N blocks for war ... across ~M ticks`.
- ☐ Within a few seconds the destroyed blocks **reappear in their exact pre-war state**.
- ☐ Block entities round-trip: blow up a **chest with items** / a **sign with text** during the war; after restore the contents/text are intact (`saveWithFullMetadata` → `loadStatic`).

### L. ★ Ledger persistence across a server restart (the new work)
This is the gap that was just closed — wars persist, and now the damage ledger does too.
- ☐ Start a war; blow up several tracked blocks (leave them broken — do **not** end the war).
- ☐ **Gracefully stop the server** (`/stop`). In the log confirm:
  - `Saved N active wars to config/warntax/active_wars.json`
  - `WarBlockLedger flushed and saved to disk` (and `WarBlockLedger saved N blocks across M wars to config/warntax/war_block_ledger.nbt`)
- ☐ Confirm `config/warntax/war_block_ledger.nbt` exists on disk.
- ☐ **Restart the server.** In the log confirm (order matters):
  - `WarBlockLedger loaded N blocks across M wars from disk`
  - then `War persistence restoration complete`
  - then (if any orphans) `WarBlockLedger pruned K orphan ledger(s) ...`
- ☐ The war is still active (`/wnt debug war`), and the blocks are still broken (not yet restored — restore only fires at war end).
- ☐ End the war. The blocks broken **before the restart** are restored correctly. ← key assertion.

### M. Persistence edge cases
- ☐ **Mid-restore shutdown flush:** with many broken blocks (e.g. 1000+ so restore spans many ticks), end the war and `/stop` *during* the restore. Log shows `WarBlockLedger flushed N pending block restorations on shutdown`. After restart, the remaining blocks are present (saved with the world), not stranded.
- ☐ **Orphan prune:** start a war, break blocks, `/stop`. Before restart, delete/rename `active_wars.json` (simulating a war that won't resume). Restart. Log shows the ledger loaded then pruned; the orphan ledger does not later resurrect blocks.
- ☐ **Partial war-restore preserves recovery:** if `active_wars.json` partially fails to restore (renames itself to `active_wars.json.failed-<ts>`), confirm `war_block_ledger.nbt` is **not** deleted on that start (so a manual recovery still has the ledger).
- ☐ **Corrupt ledger file:** put garbage in `war_block_ledger.nbt`, restart. Server still boots; the file is preserved as `war_block_ledger.nbt.failed-<ts>` (not silently deleted), log shows the warning.
- ☐ **No double-restore:** end a war (blocks restore), then `/stop` *non-gracefully* (kill the process) before the next graceful save, restart. The already-restored blocks are **not** restored a second time (war not active → ledger pruned). If you rebuilt over them, your rebuild is preserved.

### N. Explosion't integration (only if Explosion't jar is installed)
Dep: `curse.maven:explosiont-388909:4848559`. Set `DeferRestorationToExplosiont = true`.
- ☐ With Explosion't present + `DeferRestorationToExplosiont = true`: during a war, explosion damage is captured by Explosion't but its **heal countdown is paused** (the `WorldTickHandlerMixin` cancels `handleLevelTick`) — blocks stay broken while the war/raid/besiege is active.
- ☐ When the **last** conflict in that level ends, Explosion't's heal resumes and blocks regenerate.
- ☐ With Explosion't **absent**, the mixin no-ops silently (`required: false` / `@Pseudo`) — no crash, and the built-in `WarBlockLedger` (section K) is used instead.
- ☐ `ExplosiontCompat.shouldDeferToExplosiont()` picks the right path (ledger vs mixin) based on the config + whether the mod is loaded.

---

## 4. Persistence files to inspect

All under `config/warntax/`:

| File | Written by | Notes |
|------|-----------|-------|
| `active_wars.json` | `WarSystem` | active wars; deleted/renamed `.failed-<ts>` after load |
| `war_block_ledger.nbt` | `WarBlockLedger` | explosion-damage ledger (NBT); saved on stop, loaded+pruned on start |
| `besieged_colonies.json` | `BesiegeManager` | active besieges |
| `occupations.json` | `OccupationManager` | TAX_ONLY / TRANSFER_PENDING occupations |
| `vassals.json` | `VassalManager` | vassal relationships |
| `warchests.json` | `TreasuryManager` | treasury balances (legacy filename) |
| `colony_upgrades.json` | `ColonyUpgradeManager` | MILITIA/spy/defense investment levels |
| `firstColonyData.json` | `FirstColonyTracker` | primary-colony tracking (tier detection) |

---

## 5. Observability — what to grep in `latest.log`

Set `LogLevel = 2` first. Useful markers:

```
WarBlockLedger saved        # shutdown save
WarBlockLedger loaded       # startup load
WarBlockLedger restoring    # war-end restore started
WarBlockLedger flushed      # mid-restore shutdown flush
WarBlockLedger pruned       # orphan prune at startup
WarBlockLedger ... cap of   # 50k per-war cap hit (runaway explosion guard)
Saved N active wars         # war persistence save
War restoration complete    # war persistence load
```

---

## 6. Sign-off

| Area | Result | Notes |
|------|--------|-------|
| A Smoke | ☐ | |
| B Tiers / transfer guard | ☐ | |
| C Full war loop | ☐ | |
| D Besiege (single) | ☐ | |
| E Besiege (multi) | ☐ | |
| F Defender allies | ☐ | |
| G Militia investment | ☐ | |
| H Banner objective | ☐ | |
| I Town hall demolition | ☐ | |
| J Block-interaction filter | ☐ | |
| K Ledger restore | ☐ | |
| L Ledger persists across restart | ☐ | |
| M Persistence edge cases | ☐ | |
| N Explosion't integration | ☐ | |

**Critical end-to-end loop** (run last, ties it together):
> Primary colony loses a war → becomes tax-occupied → original owner counter-besieges
> → reclaim succeeds → no self-vassalization → taxes return to the owner.

- ☐ Critical loop passes.
