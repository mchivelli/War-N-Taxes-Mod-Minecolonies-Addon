# Changelog

All notable changes to WarNTaxes (the War 'N Taxes MineColonies addon) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

## [5.0.0] - 2026-06-05

This is the **WarNTaxes 5.0** release: the mod is rebranded to **WarNTaxes** and gains a
full **Siege SMP** ruleset, persistent siege damage, a dependency-stack update to the latest
MineColonies, and a server-stability/performance pass — on top of the Occupation, War
Persistence, and Espionage systems consolidated from earlier 5.0 development below.

### Added - Siege SMP: Colony Tiers & Conquest

- Colonies now have tiers. Your **first** colony is a **Primary** (capital); the rest are **Secondary** (outposts). A Primary can be besieged and have its taxes occupied, but its ownership cannot be permanently claimed unless the server enables `EnablePrimaryColonyTransfer` (default off) — the owner reclaims a tax-occupied capital by winning a counter-besiege. Secondary colonies can be permanently claimed after a long-enough besiege.
- **Multi-besieger sieges**: a colony can be besieged by several attackers at once; the first to break through wins the spoils ("resolved first", no double awards). Besiegers fight **solo** (their colony-mates cannot damage the defenders), but defenders may rally **allies**, who receive a clickable call-to-arms notification.
- The besiege winner takes a configurable share of the loser's treasury (`BesiegeSpoilPercentOfLoserTreasury`, cap-aware so no coins are lost).
- During a besiege the colony's **chests and villagers cannot be used** by attackers.
- Besieged and occupied colonies now appear in the **Vassals tab** with colour-coded status badges (vassal / tax-occupied / provisional).
- During an active war, blocks can no longer be broken by hand — only **explosive damage** destroys them; chests, doors and combat still work.

### Added - Siege Victory Objectives (experimental)

Gated behind `EnableExperimentalSiegeObjectives` (default off):

- **Plant the Banner** — attackers are given a Siege Banner; planting it inside the town-hall borders starts a war-scoped capture timer shown as a boss bar to participants only. Hold it for `BannerCaptureMinutes` to win; defenders can break the banner to stop the capture.
- **Demolish the Town Hall** — destroy the town-hall *building* with explosives (ideally siege weaponry). Each valid hit (within `MaxSiegeRadius`) makes the attacker **glow** and broadcasts their coordinates to the defenders; after `TownHallExplosiveHitsRequired` hits the colony falls. A per-attacker cooldown (`TownHallHitCooldownMinutes`) sits between counted hits.

### Added - Militia Investment & Persistent Siege Damage

- New **Militia** investment spawns extra defenders scaled to a colony's guard count (applies to wars, besieges, and raids). Militia extend a fight but **never count as victory objectives** — only guards and player lives do — and despawn when the conflict ends.
- Explosion damage during a war is now recorded and **fully restored when the war ends**, blocks and block-entity contents (chests, signs) intact, so siege warfare no longer permanently scars the map. The damage ledger **persists across server restarts** (saved on stop, reloaded and pruned on start).
- **Explosion't integration**: when the Explosion't mod is installed, a war-aware mixin pauses its regeneration during any active war/raid/besiege and resumes only after the conflict ends (`DeferRestorationToExplosiont`).

### Added - Siege & Vassalization Controls (refinements)

- **Configurable besiege chest access** — attackers no longer hard-blocked from containers during a siege. New `BesiegeAllowChestAccess` (default `false`, combat-only) lets servers permit looting mid-siege.
- **Owner keeps access while vassalized** — when a colony is vassalized via besiege occupation, its original owner now **retains** colony access (chests, buildings) by default; only tax tribute is siphoned. The old full lockout is now opt-in via `VassalLockOutFormerOwner` (default `false`).
- **Online requirement for sieges** — `BesiegeRequireOnline` (default `true`): a besieger who logs off longer than `BesiegeOfflineGraceMinutes` (default 5) has their participation cancelled. With multiple attackers, one logging off does not collapse the siege for the others.
- **Multiplayer sieges share the spoils** — when several players besiege the same colony (each runs `/wnt besiege` on it), victory now splits the treasury spoils across **all** participating besiegers' primary colonies (`BesiegeShareSpoils`, default `true`) instead of only the first to resolve. The lead besieger still receives the ongoing tax-tribute stream.
- **"Not solo" minimum-attacker gate** — `BesiegeMinAttackers` (default 1): if fewer than this many distinct besiegers take part, the defenders can be wiped but the colony is **not** captured. Set to 2+ to force players to band together.
- **Vassalize-only "huge money" war grab** — when a war victory vassalizes a colony instead of transferring it (deed-transfer disabled), the victor now takes a one-time cut of the loser's colony treasury (`WarVassalizationTreasuryGrabPercent`, default 50%) and the losing player's wallet (`WarVassalizationPlayerBalanceGrabPercent`, default 25%). Either can be set to 0 to disable.

### Fixed - Release-readiness audit pass

A full release-readiness audit found and fixed a batch of money-loss and feature-loss bugs:

- **Paying off tax debt no longer destroys your coins.** On servers using item currency, paying a debt you couldn't fully afford used to consume the coins from your inventory *and* leave the debt unpaid. Payment now checks you have enough before taking anything.
- **Spy rewards earned while you were offline now arrive when you log back in.** If a spy mission finished while the owner was away, the intel report book and map were silently lost (and the colony was still charged). Pending rewards are now delivered on login and saved across restarts.
- **Paid colony upgrades survive a crash.** A crash while the game was saving could wipe the upgrades file, resetting every colony's investment levels to zero after you had already paid. Upgrade data is now written safely (write-then-swap) so a crash can't corrupt it.
- **War militia now actually fight the enemy.** Militia summoned during a war equipped weapons and announced they had joined the defense but would only swing back after being hit. They now seek out and attack enemy war participants on their own.
- **Six admin commands no longer disappear after `/reload`.** The treasury, raid-repair, faction, tax-policy, random-events, and recipe-test commands were only registered at server boot and vanished after a `/reload`; they are now re-registered every reload.
- **A corrupt tax-data file no longer prevents the world from loading.** A truncated or malformed `colonyTaxData.json` used to crash world load; it is now handled gracefully by starting fresh.
- **Raid penalties now work and persist.** The raid-penalty system was never started up, so `/wnt repair` always failed and penalties were forgotten on every restart. It is now initialized properly and its data is saved and restored.
- **Duel wagers are now real.** `/pvp duel <wager>` advertised a coin stake but never collected or paid it. Wagers are now escrowed when a duel is accepted, paid to the winner, and refunded on a draw, disconnect, timeout, or server shutdown.
- **Team friendly-fire protection now triggers.** The setting meant to stop teammates from damaging each other in team battles never actually fired; same-team members are now correctly detected and protected (one-on-one and free-for-all fights are unaffected).
- **Safer saves and corrupt-file handling across more systems** — the tax-policy, raid-penalty, random-events, besiege, faction, and spy data files now use the same crash-safe write-then-swap approach and survive a corrupt file on load without aborting world load.
- **Occupation and vassal taxes can no longer push a colony's tax ledger negative.** When a colony is both occupied and paying vassal tribute, the combined cut is now floored so it never takes more than the colony has, and the amount credited to the collector always equals the amount debited (no coins created or destroyed).
- Removed several orphaned, never-wired features (trade routes and a pair of unused network packets) to reduce confusion and dead weight.

### Fixed - War-system soundness pass

A focused audit of the war, besiege, occupation, siege, and militia systems:

- **War/besiege militia stay on the field.** Summoned militia were being discarded a few seconds after spawning (they were created without being tied to a colony), so the paid Militia investment did nothing. They are now correctly assigned to their colony and persist for the fight, and despawn properly when a besiege ends.
- **The Siege Banner can be broken again.** The during-war block-break ban was overriding the banner's own handler, making a planted Siege Banner unbreakable and Plant-the-Banner impossible for defenders to stop. The banner is now exempt from the war break-ban.
- **First-colony (capital) registry is crash-safe.** The file tracking which colony is each player's capital is now written with the crash-safe write-then-swap method and tolerates a corrupt file on load, so a crash mid-save can't blank it and make every capital look claimable.
- **Victory can no longer pay out twice.** A re-entry guard prevents war victory spoils and the vassalization money grab from firing more than once in the same tick.
- **Configurable war stalemate.** A no-loss stalemate now honors the `WarStalematePercentage` setting instead of a hardcoded value.
- **New `BannerMaxReplants` config key** controls how many times a Siege Banner may be replanted (previously hardcoded).
- **Vassal tribute is only taken when there's an overlord to receive it** — tribute is no longer deducted from a vassal and then dropped when the receiving colony is gone.

### Added - New Configuration (War Settings)

- `EnablePrimaryColonyTransfer`, `PrimaryColonyTaxOccupationDays`, `BesiegeSpoilPercentOfLoserTreasury`, `EnableExperimentalSiegeObjectives`, `TownHallExplosiveHitsRequired`, `TownHallHitCooldownMinutes`, `MaxSiegeRadius`, `AttackerGlowSeconds`, `BannerCaptureMinutes`, `DeferRestorationToExplosiont`.

### Changed - Dependencies (latest MineColonies stack)

- Updated to **MineColonies 1.20.1-1.1.1237** and the matching coherent dependency stack: Structurize 1.0.816, BlockUI 1.0.194, Domum Ornamentum 1.0.301, Multi-Piston 0.0.47, JEI 15.20.0.130, FTB Teams 2001.3.2, Recruits 1.15.0, SDM Shop 7.2.2, SDM Engine Core 2001.4.0. Verified the mod compiles against the new API and the whole stack loads coherently (MineColonies mandates minimum Structurize/BlockUI/Domum versions). See `DEPENDENCY_COMPATIBILITY.md`.

### Performance - Server Stability at Scale

A server-stability pass for high player counts and hundreds of colonies (see `OPTIMIZATION_AUDIT.md`):

- Removed an O(N²) per-tax-cycle disk write in the random-events system (now one coalesced save per cycle).
- Moved treasury, war-exhaustion, and tax-data saves onto the off-thread async writer — no synchronous disk I/O on the server tick or on player actions.
- Throttled a perpetual all-colony permission scan from every 5 seconds to every 5 minutes.
- Added cheap early-outs to the highest-frequency event handlers (citizen-death, block break/place/interact, abandoned-colony protection) and an allocation-free besiege-active check on the block-interaction filter.
- Fixed a repeating-task leak on every war declaration and hardened the join-phase start so an ended war can no longer be resurrected.

### Fixed - MineColonies 1.1.1237 Compatibility

- MineColonies changed `IPermissions.setOwner` from taking a player UUID to taking an online player entity, and `getOwner()` now returns a cached owner id updated only by `setOwner`. Colony-ownership assignment in the abandonment, claiming-raid, and admin-fix paths was rewritten accordingly (the old reflection failed with "argument type mismatch", leaving colonies ownerless). See `PORTING_NOTES.md`.

### Fixed - World corruption when claiming/using a town hall (CRITICAL)

- Root cause: the automatic colony owner-repair ran **immediately at `ServerStartingEvent`**, before MineColonies finished loading colonies. A colony that momentarily reported a null owner mid-load had a synthetic `[AUTO_OWNER]` placeholder UUID written into its permissions and was flagged abandoned, corrupting its saved data and bricking the world on the next load. This ran unconditionally in every version (matching reports that downgrading did not help).
- Added master switch `EnableColonyAbandonmentSystem` (default **false**). Out of the box the mod now performs **no automatic writes** to MineColonies owner/permission state. The inactivity auto-abandon, debt-bankruptcy abandonment, null-owner repair, and abandoned-entry cleanup all require this switch to be enabled.
- Removed the immediate `ServerStartingEvent` owner-fix pass; any owner-repair now runs only on a deferred pass after colonies have finished loading, and only when the system is enabled.
- `emergencyFixAllNullOwners()` / `fixNullOwnerColony()` no longer inject synthetic placeholder owners or flag colonies abandoned — they only promote a real online colony-manager to owner via `setOwner`, otherwise they leave the colony untouched.
- `isColonyAbandoned()` is now a pure read with no side effects (it previously mutated colony state during a status check).
- `cleanupAbandonedEntries()` now matches only the exact synthetic markers the mod itself wrote; the old broad heuristics (any name containing "abandoned", `~`/`#` prefixes, a bogus UUID-length check) could delete a real player and leave a colony ownerless.
- Added an always-on, **removal-only** migration that heals worlds already corrupted by older versions: it strips legacy synthetic placeholder entries, promoting a real manager to owner first so a colony is never left ownerless.
- Added null-world guards to all permission rank writes so a null Level is never passed into MineColonies.

### Fixed - Claimed tax coins never appearing in shop balance

- All three tax-claim paths (claim GUI packet, `/claimtax`, `/wnt claimtax`) deducted the tax from the colony ledger **before** delivery and never refunded on failure — so if the shop economy was unavailable or the deposit failed, the tax was lost. Delivery now refunds the colony ledger (`TaxManager.adjustTax`) on any failure; taxes are never silently lost.
- When `EnableSDMShopConversion=true` but the economy is unavailable, the claim is refunded with a clear message instead of silently dropping surprise currency items.
- Added `SDMCurrencyName` config (default `sdm_coin`) so servers whose SDM-Economy currency id differs from the default can point taxes at the correct currency (previously hardcoded, causing deposits into an invisible currency).
- Added a one-time WARN when conversion is enabled but unavailable, and the `/wnt debug sdm status` command to diagnose integration state, currency id, and wallet balance (covers the single-player `CurrencyPlayerData.SERVER`-not-ready timing case).

### Changed - Debug commands consolidated under `/wnt debug`

- Moved `emergencyfix`, `fixnullowners`, `cleanupabandonedentries`, `forcecleanupcolony`, `debugbossbar` (now `bossbar`), and `claimraidstatus` under the `/wnt debug` prefix, alongside the new `/wnt debug sdm status`.

### Fixed - Colony Ownership Tracking

- `FirstColonyTracker` now correctly identifies and displays the real owner of a colony's primary slot in the Officer Tracking debug command; previously showed `[abandoned]` due to corrupted permission entries
- Added `getFirstColonyOwner()` reverse lookup so any system (besiege, debug display) can reliably resolve which player holds a colony as their primary without touching potentially-corrupted MineColonies permission data
- Added `bootstrapFromExistingColonies()` so the tracker automatically seeds data for colonies that existed before the mod was installed; uses colony ID as a creation-order proxy (lower ID = older = primary)
- Placeholder owner entries created by MineColonies' abandonment system (`[abandoned]`, `[AUTO_OWNER]`) are now detected and skipped during bootstrap and display
- Fixed duplicate MineColonies event bus subscriptions in single-player: the `ColonyCreatedModEvent` / `ColonyDeletedModEvent` handlers are now guarded by a static flag because `DefaultEventBus` is a JVM-lifetime singleton that accumulates handlers across world loads
- `OfficerColonyVisitTracker` now loads persisted visit timestamps at server start; previously the file was written but never read back, causing WnT abandonment timers to reset to MineColonies' own timer value on every restart
- Officer Tracking debug command no longer lists the colony owner as an officer; also filters out `[abandoned]` placeholder entries from the officers list
- Besiege system now uses `FirstColonyTracker` as its primary guard when checking whether a target colony is someone's primary colony, falling back to permissions-based lookup only when FCT data is absent

### Fixed - War Chest Integration

- War chest drain is now active during wars: both attacker and defender chests drain every 60 seconds while a war is ongoing
- Defender home-field advantage (reduced drain rate) is now applied when a war starts and cleared when it ends
- War chest balance is now shown in the tax report alongside the auto-deposit line
- War chest commands (`/wnt warchest status/deposit/withdraw`) now accept an optional `[colonyId]` argument; when omitted, the colony is resolved by position first, then ownership fallback
- Colony suggestion provider added to war chest commands for tab-completion of managed colony IDs
- Removed unnecessary disk writes from `drainWarChest()` and `deductFromWarChest()` — persistence is now batched every 5 minutes during the drain loop instead of on every tick
- War chest drain tasks are properly cancelled when wars end and re-scheduled when wars are restored after server restart

### Changed - War Chest Balance

- Default `WarChestDrainPercent` reduced from 0.02 (2%) to 0.004 (0.4%) — a full 25,000 chest now lasts approximately 4 hours instead of 50 minutes
- Default `WarChestDrainPerMinute` (flat fallback) reduced from 50 to 10, also yielding approximately 4 hours of war sustainment

### Fixed - War Declaration

- Attacker colony selection now prefers the player's primary (first) colony when declaring war, preventing secondary colonies from being wagered unintentionally
- A colony already engaged in an active war can no longer start a second war simultaneously, preventing concurrent war conflicts
- Besieged and occupied colonies are now correctly blocked from tax claiming in the GUI

### Added - Status Notifications & GUI

- Besieged and occupied colony status is now displayed in the colony list GUI ("Besieged" in red, "Occupied" in orange)
- Players now receive login notifications for active wars, besieges, and occupations on their colonies, including when events occurred while they were offline

### Added - Colony Occupation System

- Winning a war no longer immediately transfers a colony. Instead the losing colony enters an **Occupation phase**
- The occupier collects a share of the losing colony's taxes during the occupation window (configurable, default 50%)
- The original owner retains full control of their colony and has a set number of days to fight back before ownership transfers (default 7 days)
- Occupiers cannot interact with the occupied colony's buildings or items during this period
- Occupation state persists across server restarts
- New config options: `EnableOccupationSystem`, `OccupationDurationDays`, `OccupationTaxPercentage`

### Added - War Persistence

- Active wars now survive server restarts and crashes
- All war state is saved to disk on server shutdown and automatically restored on startup
- Wars that expired during downtime are discarded cleanly; wars still in the join phase are promoted to active
- Server broadcasts a notification when a war is restored after restart

### Added - Espionage System (Full Expansion)

- Spies now have a **travel phase** before arriving at the target colony; travel time is based on distance and is configurable
- Intel is gathered progressively over time across three tiers: early (basic colony info), mid (guards and treasury), late (full roster and leadership)
- Detected spies now attempt to **flee** the colony rather than dying instantly; escape is confirmed only when the spy clears the colony border
- Recalling a spy mid-travel cancels the mission immediately; recalling an active spy triggers a return journey
- Completed missions (escaped or recalled) retain their intel for review for up to one hour
- Killed spies lose all gathered intel
- Spy intel can be received as a written in-game book (Shadow Network report format)
- JourneyMap waypoints are created for active spy missions when JourneyMap is installed (source and destination markers)

### Added - Happiness Integration

- War N Taxes now applies its own happiness modifiers directly into the MineColonies happiness system
- Active random events and tax policy contribute to colony happiness visible in the standard MineColonies UI

### Added - Crafting Recipe

- The War N Taxes Codex (Patchouli guide book) can now be crafted in-game: 8 gold nuggets surrounding a book

### Changed - Tax System Overhaul

- Guard Tower boost minimum threshold reduced from 5 towers to 1 — the bonus now starts from your very first tower rather than requiring 5 before any benefit applies
- Guard Tower income now scales linearly at +8% per tower, replacing the old flat +50% jump; cap is 80%
- Bugfix: Corrected the Guard Tower multiplier calculation that was producing incorrect values for high tower counts
- Tax generation now has a hard floor of 30% — no combination of war penalties, strikes, or exhaustion can reduce a colony below this minimum
- War tax freeze is now cycle-based (default 3 cycles) rather than hours-based, ensuring consistent penalties regardless of server configuration
- Tax revenue cap raised to 10,000 coins (was 5,000)
- Debt limit enabled at 2,000 coins (was disabled by default)

### Changed - Economy & Config Rebalance

- War victory and defeat penalty percentages rebalanced to 20% each (was 25% / 15%)
- War Chest auto-deposit halved to 5%; drain rate reduced; capacity reduced — wars now require active manual funding rather than passive accumulation
- Defender war chest drain rate reduced to encourage fair fights
- Raid tax percentages reduced significantly to better reflect raid scale
- Trade routes nerfed: income cut from 5 to 1 coin per chunk, max distance cut from 1,000 to 100 chunks, max routes reduced from 3 to 2, maintenance cost halved
- Infrastructure investments: base cost raised to 1,500 coins, max stacks halved to 5, and diminishing returns tightened (each purchase is less effective than the last)
- Faction creation now costs 5,000 coins (was 1,000)
- Default extortion demand raised from 15% to 20% of the defender's balance
- Guard bribe cost increased to 2,500 coins; detection risk also increased, making bribing guards a riskier action
- Ransom immunity after paying extortion reduced from 24 hours to 4 hours
- War penalties now have a combined cap to prevent stacking beyond a configurable maximum
- Peace proposals expire after a configurable timeout

### Changed - Random Event Balance

- **Labor Strike**: Tax penalty reduced from -40% to -30%
- **Plague Outbreak**: Tax penalty reduced from -35% to -25%
- **Guard Desertion**: Tax penalty reduced from -30% to -20%
- Negative events are less punishing overall to keep long-term play rewarding rather than discouraging

### Changed - Infrastructure

- All building lookups now route through a compatibility shim that automatically detects the installed MineColonies API version; the mod now works with both older and newer MineColonies builds without separate releases
- Internal timers replaced with a server-thread-safe task scheduler, eliminating potential cross-thread state issues
- MineColonies, Structurize, BlockUI, and DomumOrnamentum dependencies upgraded to current versions
- JourneyMap added as an optional dependency
- Easy Factions removed (was causing crashes in some environments)

---

## [4.2.0] - 2026-03-07

### Added - High Stakes War System

- **NEW FEATURE**: **Primary vs Secondary Colony Rules** (`EnableOutpostVulnerability`)
  - First colony a player creates is their **Primary Colony (Capital)** - can only be attacked when owner is online
  - All additional colonies are **Secondary Colonies (Outposts)** - can be attacked even when owner is offline
  - Offline outpost attacks: Guards defend automatically, war proceeds without defender player participation
  - Tracked via `FirstColonyTracker` - persists to `config/warntax/firstColonyData.json`

- **NEW FEATURE**: **Colony Wager System** (`EnableColonyWager`)
  - Attacker's colony becomes a **WAGER** in every war declaration
  - If attacker wins: Target colony enters OCCUPIED state (existing behavior)
  - If defender wins: **Attacker's colony enters OCCUPIED state!** (Counter-Conquest)
  - Creates high-risk, high-reward warfare - attackers cannot safely grief weaker players
  - Notification messages for both sides on wager outcome

- **NEW FEATURE**: **Reclamation War Exception**
  - Players can declare war **from inside their occupied colony** against the occupier
  - Building requirements and war chest costs **waived** for reclamation wars
  - Allows players whose only colony is occupied to still fight back
  - Logged as "Reclamation war exception" in `WARSYSTEM_LOGGER`

### Changed

- **`WarSystem.java`**: Modified `processWageWarRequest` to check `FirstColonyTracker` for primary/secondary colony status
- **`WarSystem.java`**: Added `processOfflineOutpostAttack` and `initiateOfflineOutpostWar` methods for offline outpost attacks
- **`WarSystem.java`**: Modified `findValidAttackerColony` to allow occupied colonies for reclamation wars
- **`WarSystem.java`**: Modified `handleVictoryRewards` to implement Colony Wager System
- **`WarData.java`**: Added `offlineOutpostWar` field and getter/setter
- **`TaxConfig.java`**: Added `EnableOutpostVulnerability` (default: true) and `EnableColonyWager` (default: true) config options

### Documentation

- **`wiki/War_System.md`**: Added new "High Stakes War System" section (Section 2) documenting:
  - Primary vs Secondary Colonies (Outpost Vulnerability)
  - Colony Wager System mechanics
  - Reclamation Wars special privileges

## [4.1.0] - 2026-03-06

### Changed - Logging System Overhaul

- **OVERHAUL**: Centralized logging system across all files to drastically reduce console spam
- **`TaxConfig.java`**: Added new `LOG_LEVEL` configuration (`0` = Minimal/Errors only, `1` = Normal Info, `2` = Debug Logging)
- **`WarSystem.java` & `WarEventHandler.java`**: Over 50 `System.out.println` calls wrapped with `isDebugLogging()` or `isNormalLogging()`
- **All Managers**: Over 150 `LOGGER.info()` calls systematically wrapped with log level guards, allowing server admins to silence verbose mod logs completely
  - Affected files: `ColonyAbandonmentManager`, `ColonyClaimingRaidManager`, `RaidManager`, `MineColonyTax`, `PlayerWarDataCapability`, `WarChestManager`, `TaxPermissionManager`, `CitizenMilitiaManager`, `SpyManager`, `WarExhaustionManager`
- Error (`LOGGER.error`) and Warning (`LOGGER.warn`) messages remain always visible for critical system diagnostics

### Added - Random Events System

- **NEW FEATURE**: **Random Events System** - Dynamic, context-aware events that trigger based on colony conditions
  - **16 Unique Events**: 6 positive, 7 negative, 2 neutral/choice, 1 special war event
  - **Event Categories**:
    - **Positive**: Merchant Caravan, Bountiful Harvest, Cultural Festival, Successful Recruitment, Royal Feast, War Profiteering
    - **Negative**: Food Shortage, Disease Outbreak, Bandit Harassment, Corrupt Official, Guard Desertion, Labor Strike, Plague Outbreak, Crop Blight
    - **Neutral/Choice**: Wandering Trader Offer, Neighboring Alliance
  - **Tax & Happiness Impacts**: Events modify tax multipliers (-40% to +35%) and happiness (-0.8 to +0.6)
  - **Duration System**: Events last 1-3 tax cycles based on severity
  - **Cooldown System**: Prevents event spam (4-10 cycles between same event)
  - **Condition-Based Triggering**: Events only trigger when specific colony conditions met (happiness, buildings, citizens, tax policy)

### Added - Deep Citizen Integration

- **NEW FEATURE**: **CitizenManipulator System** - Reflection-based deep integration with MineColonies citizen states
  - Uses Java reflection to access MineColonies internal APIs safely
  - Graceful fallback if MineColonies internals change
  - Thread-safe operations with error handling

- **4 Deep Integration Events** that directly manipulate citizen states:

  1. **LABOR_STRIKE** (Happiness <3.5, HIGH/WAR_ECONOMY policy, 30+ citizens)
     - Sets 30-50% of workers to STUCK job status (prevents working)
     - -40% tax, -0.7 happiness, 2 cycles
     - **Restoration**: All affected citizens returned to WORKING status on expiration

  2. **PLAGUE_OUTBREAK** (50+ citizens, no Hospital L3+)
     - Infects 20-40% of citizens with random diseases
     - Visible disease particles in-game
     - -35% tax, -0.8 happiness, 3 cycles
     - **Restoration**: ALL sick citizens cured on expiration

  3. **ROYAL_FEAST** (Happiness >7.0, not at war)
     - Sets ALL citizens' saturation to max (20.0) - instant feast
     - +10% tax, +0.6 happiness, 1 cycle
     - No restoration needed (saturation naturally decreases)

  4. **CROP_BLIGHT** (5+ farms)
     - Sets ALL citizens' saturation to near-starvation (3.0)
     - -25% tax, -0.5 happiness, 2 cycles
     - No restoration needed (saturation can naturally recover)

### Added - Event Management Features

- **Active Event Tracking**: Stores active events with remaining cycles and affected citizens
- **Data Persistence**: Events and cooldowns persist across server restarts
  - `config/warntax/events.json` - Active events and affected citizen IDs
  - `config/warntax/eventCooldowns.json` - Cooldown expiration timestamps
- **Integration with Tax System**: Event tax multipliers applied during tax calculation
- **Integration with Happiness System**: Event happiness modifiers stack additively

### Added - Event Commands

- `/wnt events <colonyId>` - View all active events for a colony (name, description, remaining cycles, impacts, affected citizens)
- `/wnt triggerevent <colonyId> <eventType>` - (Admin) Manually trigger specific event for testing (bypasses conditions/probability/cooldowns)

### Technical Implementation

- **Core Classes**:
  - `RandomEventType.java` - Enum of 16 events with properties and condition logic
  - `RandomEventManager.java` - Event lifecycle manager (triggering, persistence, restoration)
  - `ActiveEvent.java` - Data model for tracking active events
  - `CitizenManipulator.java` - Reflection utility for citizen state manipulation
- **Probability System**: Weighted probability with base chance per event, modified by conditions
- **Trigger Timing**: Checked during tax cycles, only if owner/officer online
- **Cooldown Prevention**: Events cannot re-trigger immediately (stored per-colony)

### Documentation

- **NEW**: `docs/RANDOM_EVENTS_SYSTEM.md` - Comprehensive documentation:
  - System architecture and event lifecycle
  - Complete event table with conditions and impacts
  - Deep integration technical details
  - CitizenManipulator implementation using reflection
  - Data persistence format
  - Testing guide for deep integration events
  - Developer notes for adding new events

### Known Limitations

- Deep integration depends on MineColonies internal APIs (may need updates if MineColonies changes)
- No per-event configuration via config file (requires code changes to disable individual events)
- Neutral/choice events (WANDERING_TRADER_OFFER, NEIGHBORING_ALLIANCE) are informational only (no player choice system yet)
- Several events check for "at war" status, but WarManager integration is pending

### Future Enhancements

- JSON-based event configuration (enable/disable, adjust probabilities, modify effects)
- Player choice system for neutral events
- WarManager integration for war-dependent events
- Colony news feed GUI
- Event chains (one event triggers another)

### Fixed
- **War Chest UI**: Fixed hardcoded "gold" currency labels - now displays configured currency (SDMShop $ or item name)
- **War Chest UI**: Removed misleading "Min for War" calculation that incorrectly used player's own colony balance instead of target's
- **War Chest UI**: War Chest tab now properly hides when `ENABLE_WAR_CHEST` config is disabled
- **Tax Report**: Fixed confusing tax report that made players think happiness bonus was added on top of generated revenue (it was already included)
- **Tax Report**: Added missing War Chest Auto-Deposit and Faction Pool deductions to player reports (previously only logged to server)
- **Tax Report**: Fixed misleading "Final Balance" that showed total stored tax instead of net change from the tax cycle

### 💰 Tax Report Overhaul

- **MAJOR IMPROVEMENT**: Complete redesign of tax report with "Colonial Ledger" theme
- **NEW LAYOUT**: Professional ledger-style format with box-drawing characters and organized sections
  ```
  ╔═══════════════════════════════════╗
  ║    COLONY LEDGER - YourColony     ║
  ╚═══════════════════════════════════╝

  » Income Recorded «
    58 buildings produced........329 coins
    Worker morale boost..........+52 coins
    ─────────────────────────────────
    Total collected..............381 coins

  » Expenses Deducted «
    Building upkeep..............59 coins
    War chest reserves...........38 coins

  ══════════════════════════════════
  Net profit this cycle: +284 coins
  Vault holds unclaimed: 384 coins
  Settlement thriving
  ══════════════════════════════════
  ```
- **IMPROVED CLARITY**: Shows clear breakdown of base revenue → happiness bonus → total revenue → expenses
- **ALL DEDUCTIONS VISIBLE**: Now shows War Chest Auto-Deposit (10% default) and Faction Pool contributions
- **NET VS TOTAL**: Separates "Net profit this cycle" from "Vault holds unclaimed" to avoid confusion
- **COLONIAL VOCABULARY**: Medieval/colonial terminology fits Minecolonies theme
  - "Worker morale boost" instead of "Happiness Modifier"
  - "Settlement thriving" instead of "Colony finances stable"
  - "Vault holds unclaimed" instead of "Total Stored (Unclaimed)"
- **MINIMAL COLORS**: Restrained color palette (gray/white base, green for profits, red for debts)
- **ENHANCED SERVER LOGS**: Debug logs now show complete calculation breakdown including starting balance, net change, and war chest deductions

### 🏚️ Colony Abandonment System Overhaul

- **MAJOR IMPROVEMENT**: **War-N-Taxes Timer Now Controls Abandonment** - Colony abandonment now uses WnT's physical visit tracking system instead of MineColonies' owner-only login timer
- **WHY THIS MATTERS**: Previously, colonies would NEVER be abandoned if any officer logged into the server within the threshold period, even if they never visited the colony. MineColonies' internal timer only tracked owner logins and didn't account for officers.
- **NEW DEFAULT BEHAVIOR**: Abandonment timer resets ONLY when officers/owners **physically enter their colony chunks** (chunk-based detection)
  - This forces officers to actually visit their colonies to prevent abandonment
  - More efficient: Only checks when players change chunks (not every tick or login)
  - More realistic: Officers must actively manage their colonies, not just log into the server
- **OPTIONAL LOGIN TRACKING**: New config option `ResetTimerOnOfficerLogin` (default: **false**)
  - When enabled, timer also resets when officers log in (for ALL colonies they manage)
  - **NOT RECOMMENDED**: This defeats the purpose of requiring actual colony visits
  - Only enable this if you want the old MineColonies behavior (easier but less meaningful)
- **Technical Implementation**:
  - `OfficerColonyVisitTracker.java`: Chunk-based physical visit detection (primary), optional login tracking (disabled by default)
  - `ColonyAbandonmentManager.java`: Modified `checkColonyAbandonmentStatus()` to use WnT timer as the authoritative source (with MineColonies timer as fallback for colonies without WnT data yet)
  - Efficient: Physical visit detection uses chunk-change tracking (not every tick), optional login tracking only runs on rare login events
- **Data Migration**: Automatic one-time migration on server start initializes WnT tracking for existing colonies using their current MineColonies timer values (prevents premature abandonment)
- **Command Renamed**: `/wnt officertracking [colonyId]` → `/wnt abandonmentcheck [colonyId]`
  - Enhanced display now shows both timer sources, effective timer used for abandonment, and clear status indicators (ACTIVE/WARNING/READY TO ABANDON)
  - Added countdown displays (e.g., "7 days until warning" or "2 days until abandonment")
  - Shows all officers and their online/offline status
  - Tip message adapts based on config: Shows "must physically visit" by default, or "login OR visit" if login tracking enabled

### ⚙️ New Configuration Options

- **`ResetTimerOnOfficerLogin`** (default: `false`) - Controls whether officer logins reset abandonment timers
  - **When FALSE (recommended)**: Timer only resets when officers physically enter colony chunks
  - **When TRUE (not recommended)**: Timer resets for ALL colonies an officer manages just by logging into the server
  - Location: `Colony Auto-Abandon` section in config
  - **WARNING**: Enabling this defeats the purpose of requiring actual colony visits and may prevent colonies from ever being abandoned

### 🏘️ Multi-Colony Support (Requires Minecolonies Multiple Colonies Enabled)

- **NEW FEATURE**: **First Colony Tracking System** - Prevents colony abandonment when creating multiple colonies
- **THE PROBLEM**: When a player created a second colony in Minecolonies, they would become owner of the new colony, causing the first colony to be marked as abandoned (since they were no longer actively visiting it)
- **THE SOLUTION**:
  - The mod now tracks which colony is each player's "first" (primary) colony
  - When creating a **second+ colony**: Player remains **owner of their first colony** and becomes **officer in the new colony**
  - This prevents the first colony from being abandoned since the player is still the owner
- **DYNAMIC PROMOTION**: If the first colony is deleted, the next-oldest colony is automatically promoted to "first" and the player becomes its owner
- **CLEAN NAMING**: Fixed issue where colonies showed "Abandoned" prefix during ownership transitions
  - Ownership changes now trigger immediate cleanup of any transient `[abandoned]` permission entries
  - Prevents misleading "Abandoned" prefix from appearing on active colonies
- **PERSISTENT TRACKING**: First colony data is saved to `config/warntax/firstColonyData.json` and persists across server restarts
- **AUTOMATIC**: Works automatically when Minecolonies' multiple colonies feature is enabled - no additional configuration needed
- **Technical Implementation**:
  - `FirstColonyTracker.java`: Tracks player UUID → colony IDs in creation order, handles deletion and promotion
  - `ColonyOwnershipHandler.java`: Subscribes to Minecolonies events (`ColonyCreatedModEvent`, `ColonyDeletedModEvent`) to manage ownership
  - Integrates with existing `ColonyAbandonmentManager.cleanupAbandonedEntries()` to remove problematic permission entries

### 📝 Technical Changes

**Modified Files (Tax Report):**
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java`
  - Added tracking for `startingBalance` before each tax cycle to calculate net change
  - Added `warChestDeposit` and `factionPoolContribution` tracking variables
  - Completely restructured player tax report with box-drawing header and section separators
  - Split income and expenses into separate "» Income Recorded «" and "» Expenses Deducted «" sections
  - Moved happiness from confusing "+bonus" format to clear base → morale boost → total flow
  - Added dotted alignment for ledger-style formatting (e.g., "Building upkeep..............59 coins")
  - Updated colors to minimal palette (GRAY/WHITE base, GREEN profits, RED debts)
  - Enhanced server logs to show full calculation: starting + base + happiness - maintenance - war chest = final
  - Fixed guard tower boost display (removed duplicate translation key)
- `src/main/resources/assets/minecolonytax/lang/en_us.json`
  - Renamed `tax_report_header` to "COLONY LEDGER - %s"
  - Added `tax_report_subseparator` for dotted separator lines
  - Added `tax_report_income_header` and `tax_report_expenses_header` section titles
  - Updated all line items to ledger format with dotted alignment
  - Changed vocabulary to colonial theme (e.g., "Worker morale boost", "Settlement thriving")
  - Added `tax_report_net_change` and `tax_report_total_stored` to separate net vs total
  - Updated `debt_warning` to remove colony name parameter (now shown in context)
  - Removed duplicate `tax_report_guard_boost` entry at line 157

**Modified Files (Abandonment):**
- `src/main/java/net/machiavelli/minecolonytax/event/OfficerColonyVisitTracker.java`
  - Updated class documentation to clarify physical visits are default, login tracking is optional
  - Added `onPlayerLogin()` event handler (only runs if `ResetTimerOnOfficerLogin` config enabled)
  - Added `migrateExistingColonies()` method for automatic data migration on server start
- `src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java`
  - Modified `checkColonyAbandonmentStatus()` to use WnT timer exclusively (with fallback)
  - Updated documentation to reflect physical visits as default, login tracking as optional
  - Updated logging in `abandonColony()` and `warnColonyOwnersAndOfficers()` to show both timer values
- `src/main/java/net/machiavelli/minecolonytax/commands/AbandonmentCheckCommand.java` (NEW)
  - Complete rewrite of debug command with enhanced status display
  - Shows WnT timer (authoritative), MineColonies timer (reference), and effective timer
  - Color-coded status indicators and countdown timers
  - Dynamic tip message that changes based on `ResetTimerOnOfficerLogin` config setting
- `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java`
  - Added `RESET_TIMER_ON_OFFICER_LOGIN` config option (default: false)
  - Added `shouldResetTimerOnOfficerLogin()` getter method
- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java`
  - Updated command registration to use `AbandonmentCheckCommand` instead of `OfficerTrackingDebugCommand`

**New Files (Multi-Colony Support):**
- `src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java` (NEW)
  - Tracks each player's colonies in creation order (oldest to newest)
  - Persists data to `config/warntax/firstColonyData.json`
  - Provides methods: `addColony()`, `removeColony()`, `getFirstColony()`, `isFirstColony()`, `getPlayerColonies()`
  - Automatically promotes next-oldest colony to "first" when first colony is deleted
- `src/main/java/net/machiavelli/minecolonytax/event/ColonyOwnershipHandler.java` (NEW)
  - Event handler that subscribes to Minecolonies event bus for colony creation/deletion
  - `onColonyCreated()`: Tracks new colonies and manages ownership for secondary colonies
  - `handleSecondaryColonyCreation()`: Sets player as officer in new colony, restores ownership to first colony
  - `onColonyDeleted()`: Handles promotion when first colony is deleted
  - `promoteToFirstColony()`: Sets player as owner of promoted colony
  - Calls `ColonyAbandonmentManager.cleanupAbandonedEntries()` immediately after ownership changes to prevent "Abandoned" prefix

**Modified Files (Multi-Colony Support):**
- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java`
  - Added initialization: `FirstColonyTracker.loadData()` on server start (line 102-103)
  - Added registration: `ColonyOwnershipHandler.register()` to Minecolonies event bus (line 106-107)

**Deprecated Files:**
- `src/main/java/net/machiavelli/minecolonytax/commands/OfficerTrackingDebugCommand.java` - Replaced by `AbandonmentCheckCommand.java`

---

## [4.0.2] - 2026-01-27

### 🐛 Bug Fixes

- **FIXED**: **GUARDS_ATTACK NoSuchFieldError Server Crash** - Fixed critical `java.lang.NoSuchFieldError` causing server crashes when colonies were abandoned or claiming raids were managed
- **Root Cause**: The `GUARDS_ATTACK` permission was removed from the Minecolonies API (`Action` enum), but the addon was still referencing it in three locations
- **Solution**: Removed all `Action.GUARDS_ATTACK` references from the codebase:
  - `ColonyAbandonmentManager.java:258` - Removed from neutral rank permission setting during abandonment
  - `ColonyClaimingRaidManager.java:1313` - Removed from neutral rank permission grant during claiming raids
  - `ColonyClaimingRaidManager.java:1324` - Removed from neutral rank permission revoke after claiming raids
  - `TaxConfig.java` - Removed `"GUARDS_ATTACK"` from default WarActions, RaidActions, and ClaimingActions config lists
- **Technical Details**: Guard attack behavior is now controlled by `Rank.isHostile()` in the Minecolonies API rather than a granular permission flag

---

## [4.0.1] - 2026-01-12

### 🐛 Bug Fixes

- **FIXED**: **Lectern Book Overwrite Bug** - Fixed critical bug where placing any book in a lectern would cause it to display as the War 'N Taxes Codex
- **Root Cause**: The `book.json` configuration used `"custom_book_item": "minecraft:written_book"`, causing Patchouli's lectern handler to match ALL written books as the mod's guidebook
- **Solution**: Removed `custom_book_item` and `dont_generate_book` settings so Patchouli generates a unique book item that won't conflict with other books

---

## [4.0.0] - 2026-01-07

### 📖 Patchouli Guidebook Integration

- **NEW FEATURE**: **War 'N Taxes Codex** - Complete in-game guidebook powered by Patchouli
- **Automatic Book Distribution**: Players receive the codex on first join (configurable)
- **9 Content Categories**:
  - Getting Started: Welcome guide and first steps
  - Tax System: How taxes work, happiness modifier, guard tower boost, claiming tax, tax rates
  - Raid System: Overview, starting raids, mechanics, rewards, militia
  - War System: Overview, declaring war, war phases, vassalization
  - Diplomacy: Peace proposals, extortion
  - PvP Arena: Duels, team battles
  - Colony Management: Abandonment, claiming colonies
  - Commands: Player and admin command references
  - Configuration: Admin-only section (config-gated)
- **Interactive Commands**: Clickable command links throughout the book that execute commands
- **Custom Styling**: Color-coded text macros for tax, raid, war, success, warning, and more
- **Militia Entity Display**: Entity page showing citizen militia with wooden sword equipment
- **Multi-Language Support**: Translations for German, Russian, French, and Spanish
- **Advancements**: Progress tracking for claiming tax, starting raids, and declaring war

#### Configuration Options:
- `GivePatchouliBookOnJoin` (default: true) - Give book to new players
- `ShowAdminPatchouliCategory` (default: false) - Show admin config section in book

### 💰 SDMShop Integration Fix

- **FIXED**: **SDMShop Currency Conversion** - Fixed critical bug where SDMShop currency items were not being properly detected and converted when players claimed tax
- **Root Cause**: The currency item lookup was failing when SDMShop mod was present, resulting in players receiving nothing when claiming tax
- **Solution**: Added proper null checks and fallback handling for SDMShop integration

### 🏚️ Colony Abandonment Configuration

- **NEW CONFIG**: `EnableListAbandonedForAll` - Controls who can use the `/wnt listabandoned` command
  - Default: `false` (OP-only, requires permission level 2)
  - When `true`: All players can view the list of abandoned colonies
- **Location**: Colony Auto-Abandon section of config

### 🔧 Configuration Improvements

- **FIXED**: **Absurd Range Values** - Replaced all `Double.MAX_VALUE` (1.7976931348623157E308) in config ranges with sensible maximum of `10000.0`
- **FIXED**: **Military Building Map Bug** - `barrackstower`, `archery`, and `combatacademy` were incorrectly using `BUILDING_TAXES` map; now correctly use `BUILDING_MAINTENANCE` map
- **IMPROVED**: Added `[WIP]` markers to Tax Expansion config sections (Economy, Factions, Espionage, War Mechanics, Money Sinks) to indicate feature branches not yet merged

### 🐛 Critical Tax System Fixes

- **FIXED**: **Military Buildings Using Wrong Config Map** - `barrackstower`, `archery`, and `combatacademy` were incorrectly using `BUILDING_TAXES.put()` instead of `BUILDING_MAINTENANCE.put()` in the military maintenance section
- **FIXED**: **Duplicate Tax Entries** - Removed duplicate `barracks`, `guardtower`, and `barrackstower` entries from BUILDING_TAXES (military buildings should only have maintenance costs, not generate tax income)
- **FIXED**: **Debug Command Using Wrong Identifier** - The `/wnt debugtax` command was using `getBuildingDisplayName()` (e.g., "Restaurant", "Guard Tower") instead of the Registry ID (`getBuildingType().getRegistryName().getPath()` - e.g., "cook", "guardtower"), causing ALL buildings to show 0 tax in debug output
- **IMPROVED**: Debug output now shows both display name and registry ID for clarity: `Restaurant [cook] (L3): +15 tax, -0 maint = +15 net`

#### Technical Details:
- Military buildings (`barracks`, `barrackstower`, `guardtower`, `archery`, `combatacademy`) now correctly only have maintenance entries
- Tax-generating buildings use `BUILDING_TAXES` map, military buildings use `BUILDING_MAINTENANCE` map
- All 54 MineColonies Registry IDs verified against `ModBuildings.java`
- `CLASS_NAME_TO_SHORT_NAME` mappings verified: `baker`→`bakery`, `residence`→`home`, all others→same name

### 🐛 Bug Fixes

- **FIXED**: **War Declaration with Multiple Colonies** - Fixed an issue where players with multiple colonies could not declare war if their "first" colony didn't meet requirements. The system now correctly checks all owned colonies for valid war capabilities.
- **FIXED**: Added missing prerequisite note in First Steps explaining MineColonies colony requirement
- **FIXED**: Advancement localization keys added for proper display in Advancements screen

## [3.2.11] - 2025-12-07

### ⚔️ War Vassalization System

- **NEW FEATURE**: **War Vassalization** - When colony transfer is disabled, winning a war now vassalizes the losing colony instead
- **Configurable Duration**: War vassalization lasts for a configurable time period (default: 168 hours / 1 week)
- **Tribute Payments**: Vassalized colonies pay a percentage of their tax income to the victor (default: 25%)
- **Automatic Expiration**: War vassalizations automatically expire after the configured duration with notifications to both parties
- **Three New Config Options**:
  - `EnableWarVassalization` (default: true) - Toggle war vassalization when colony transfer is off
  - `WarVassalizationDurationHours` (default: 168) - How long vassalization lasts (0 = permanent)
  - `WarVassalizationTributePercentage` (default: 25) - Tribute rate percentage

#### Technical Implementation:
- Added `forceVassalize()` method to `VassalManager.java` for war-triggered vassalizations
- Extended `VassalRelation` class with `expirationTime` and `isWarVassalization` fields
- Modified `handleTaxIncome()` to check for and auto-remove expired vassalizations
- Integrated with `WarSystem.checkForVictory()` to trigger vassalization on attacker victory

### 🐛 Critical Colony Abandonment Bug Fix

- **FIXED**: **Officer Visit Data Not Being Used** - Colony abandonment checks now properly consider officer visit data
- **Root Cause**: `ColonyAbandonmentManager.checkColonyAbandonmentStatus()` only used MineColonies' internal `lastContactInHours` and completely ignored visit data from `OfficerColonyVisitTracker`
- **Impact**: Even when officers physically entered their colony, the abandonment timer was NOT being reset, causing colonies to become abandoned despite active officer presence
- **Solution**: Abandonment status checks now compare both MineColonies' contact hours AND officer visit hours, using whichever is more recent
- **Result**: Officer physical presence now properly resets the abandonment timer as intended

#### Technical Details:
- **File**: `ColonyAbandonmentManager.java` lines 92-129
- **Fix**: Added call to `OfficerColonyVisitTracker.getHoursSinceOfficerVisit()` in `checkColonyAbandonmentStatus()`
- **Logic**: Uses the minimum of MineColonies' hours and officer physical entry hours for the most recent activity

### 🏰 Colony Abandonment Timer - Physical Entry Detection

- **CHANGED**: Timer reset now requires **physical colony entry** - simply logging in no longer resets ALL your colonies' timers
- **EFFICIENT**: Uses chunk-based detection - colony lookup only happens when player moves to a new chunk (every ~16 blocks)
- **OPTIMIZED I/O**: File saves are batched every 5 minutes instead of on every entry, preventing disk thrashing
- **DIRTY FLAG**: Skips saves entirely if no changes occurred, further reducing I/O overhead
- **PERSISTENT**: Visit timestamps saved to `config/warntax/officerVisitData.json`, survives server restarts
- **CLEANUP**: Player tracking data cleared on logout to minimize memory usage

#### How It Works:
1. Player moves to a new chunk → Check if they entered a colony
2. If entering a colony they own/officer → Reset that colony's timer only
3. Changes batched in memory, saved every 5 minutes or on shutdown

#### Why This Change:
- More fair: Requires actual presence in the colony, not just being online
- More efficient: No iteration over all colonies on every login
- No TPS impact: Only checks colony on chunk changes, not every tick

### 🐛 Critical Raid System Bug Fixes

#### Bug #1: Raid Ending Immediately
- **FIXED**: **Raid Ending Immediately Bug** - Raids no longer end instantly without starting the timer
- **Root Cause**: Duration check was executing BEFORE incrementing elapsed time, causing raids to end on first timer tick
- **Impact**: Raids would show "Raid FAILED! No rewards earned" message instantly without boss bar appearing
- **Solution**: Reordered timer logic to increment time and update boss bar FIRST, then check duration
- **Technical Change**: Changed `>=` to `>` in duration check to allow full raid duration (5 minutes default)

**Technical Details:**
- **File**: `RaidManager.java` lines 752-762
- **Before**: Check duration → Increment time → Update boss bar
- **After**: Increment time → Update boss bar → Check duration
- **Logic Fix**: Changed `elapsedSeconds >= maxDuration` to `elapsedSeconds > maxDuration`
- **Result**: Raids now properly run for their full configured duration with boss bar visible

#### Bug #2: No Rewards Despite Killing All Guards
- **FIXED**: **Reward Eligibility Bug** - Raiders now receive rewards when successfully killing all guards
- **Root Cause**: Guard reconciliation system updated `CitizenMilitiaManager` counter but not `ActiveRaidData.guardsKilled`
- **Impact**: Even after killing all guards and winning the raid, raiders received "failed to kill any guards" message
- **Why It Happened**: `isEligibleForRewards()` checks `ActiveRaidData.guardsKilled > 0`, which was never incremented
- **Solution**: Call `raidData.incrementGuardsKilled()` in reconciliation loop alongside `CitizenMilitiaManager` update

**Technical Details:**
- **File**: `RaidManager.java` line 848
- **Fix**: Added `raidData.incrementGuardsKilled()` in guard reconciliation loop
- **Debug Logging**: Enhanced logging to show both counter values for troubleshooting
- **Result**: Victory detection and reward eligibility now work correctly together

## [3.2.6] - 2025-10-24

### 📊 Enhanced Raid History Tracking System

- **NEW FEATURE**: **Structured Raid History** - Complete overhaul of raid tracking with detailed, queryable data
- **Comprehensive Tracking**: Records raider UUID, name, amount stolen, timestamp, and success/failure status for every raid attempt
- **Query Methods**: New API methods for filtering raids by player, calculating totals, and analyzing raid patterns:
  - `getRaidsByPlayer(UUID)` - Get all raids by specific player
  - `getTotalAmountStolen()` - Calculate total amount stolen across all successful raids
  - `getSuccessfulRaidCount()` / `getFailedRaidCount()` - Raid statistics
- **Rich Data Format**: Each raid entry includes:
  - Timestamp with formatted date/time (`yyyy-MM-dd HH:mm:ss`)
  - Raider UUID (persists across name changes)
  - Raider name (human-readable)
  - Amount stolen (exact currency amount)
  - Success status (successful/failed)
  - Failure reason (e.g., "left colony boundaries", "failed to kill guards")
- **Backward Compatible**: Legacy string-based raid events (`getRaidEvents()`) still supported
- **Automatic JSON Storage**: Data persisted in `config/warntax/colony_history.json`
- **Colored Chat Messages**: Formatted raid history with color-coded success/failure indicators
- **History Limit**: Automatically maintains last 100 raids per colony for performance

#### Technical Implementation:
- Created `RaidEntry` inner class in `HistoryManager.java` with full structured data
- Updated `RaidManager.java` to use `addRaidEntry()` instead of legacy string format
- Added query methods: `getStructuredRaids()`, `getRaidsByPlayer()`, `getTotalAmountStolen()`
- Enhanced `/raidhistory` command to display new structured data
- Automatic migration from legacy format (both formats maintained for compatibility)

### 🔧 Mod-Level Block Filtering Enhancement

- **NEW FEATURE**: **Whole-Mod Blocking** - Block or allow entire mods at once using `#` prefix in block interaction filters
- **Simple Syntax**: Use `#modid` to target all blocks from a specific mod (e.g., `#refinedstorage`, `#mekanism`, `#ae2`)
- **Works in Both Lists**: Supports both blacklist (block all) and whitelist (allow all) configurations
- **Blacklist Examples**:
  - `#refinedstorage` - Blocks ALL Refined Storage blocks (controllers, grids, cables, etc.)
  - `#mekanism` - Blocks ALL Mekanism blocks (machines, pipes, cables, etc.)
  - `#ae2` - Blocks ALL Applied Energistics 2 blocks
- **Whitelist Examples**:
  - `#ironchest` - Allows ALL Iron Chests blocks for looting
  - `#sophisticatedstorage` - Allows ALL Sophisticated Storage blocks
- **Smart Matching**: Automatically matches any block starting with `modid:` (e.g., `#refinedstorage` matches `refinedstorage:controller`, `refinedstorage:grid`, etc.)
- **Priority Preserved**: Blacklist still takes highest priority, then whitelist, then existing protection systems
- **Mixed Configuration Support**: Can combine specific blocks and whole mods in same list

#### Configuration Examples:
```toml
BlockInteractionBlacklist = [
    "minecraft:bedrock",               # Specific block
    "#refinedstorage",                 # Entire mod
    "#mekanism",                       # Entire mod
    "minecolonies:blockhuttownhall"   # Specific block
]

BlockInteractionWhitelist = [
    "minecraft:chest",      # Specific block
    "#ironchest",          # Entire mod - all chest types
    "#metalbarrels"        # Entire mod - all barrel types
]
```

#### Technical Implementation:
- Enhanced `BlockInteractionFilterHandler.java` with mod-level matching logic
- Added iteration over blacklist/whitelist checking for `#` prefix entries
- Matches block IDs starting with `modid:` when mod-level entry found
- Updated `TaxConfig.java` comments with `#` prefix syntax documentation
- Created comprehensive documentation: `NEW_FEATURES_RAID_HISTORY_AND_MOD_BLOCKING.md`

### 🐛 Critical WebAPI Bug Fixes

- **FIXED**: **500 Internal Server Error** - Resolved critical bug causing API endpoints to crash
- **Root Cause**: `getServerStatsJSON()` had severely broken logic with 3 redundant loops and no error handling
- **Data Loading Fix**: Replaced `getOrCreate()` with `capability.resolve()` to prevent reading empty unattached instances
- **Performance Improvement**: Eliminated 2 useless loops (60-65% faster response times)
- **Error Recovery**: Added comprehensive try-catch blocks and null checks to prevent crashes
- **Enhanced Logging**: Full stack traces and detailed error messages for debugging

#### Issues Fixed:
1. **Server Stats Endpoint Crash**: Three loops iterating over players, only one actually worked, others created garbage-collected arrays
2. **Leaderboard Endpoint Crash**: Using `getOrCreate()` returned fake empty instances instead of real data
3. **False Empty Stats**: `getOrCreate()` created new PlayerWarData instances that weren't attached to players
4. **No Error Handling**: Single null pointer exception crashed entire API
5. **Missing Debug Info**: No visibility into what went wrong when errors occurred

#### Technical Changes:
- **`WarStatsAPIData.java`**:
  - Removed 2 redundant loops in `getServerStatsJSON()` (performance boost)
  - Changed from `getOrCreate()` to `capability.resolve().orElse(null)` in all methods
  - Added null checks and warning logs when capabilities not loaded
  - Added debug logging showing actual stats read from each player
- **`WebAPIServer.java`**:
  - Enhanced error logging with full stack traces
  - Added request path to error messages for easier debugging
- **Created comprehensive debug documentation**:
  - `WEBAPI_DEBUG_GUIDE.md` - Complete testing and troubleshooting guide
  - `WEBAPI_500_ERROR_FIX.md` - Detailed explanation of bugs and fixes
  - `WEBAPI_DATA_LOADING_VERIFICATION.md` - How to verify data loading works correctly

#### Verification Steps:
- Check server logs for "Web API Server Started Successfully!"
- Test health endpoint: `GET /api/health` should return `200 OK`
- Test server stats: `GET /api/warstats/server` should return real data, not 500
- Test leaderboard: `GET /api/warstats/leaderboard` should return player stats
- Look for debug logs showing "Read stats for PlayerName: wars=X, raids=Y..."

### 💰 Tax GUI & Calculation Improvements

- **FIXED**: **Tax GUI Refresh Button** - Approximate income now accurately reflects actual tax generation
- **IMPROVED**: **Revenue Calculation** - Uses actual config values instead of hardcoded estimates
- **NEW FEATURE**: **Debug Tax Command** - Comprehensive tax breakdown command for troubleshooting

#### Tax GUI Refresh Fix:
- **Accurate Estimates**: Approximate revenue calculation now uses real config tax values
- **Happiness Integration**: Accounts for happiness modifier multiplier (0.5x to 1.5x default)
- **Guard Tower Boost**: Properly calculates guard tower boost (25% default with 5+ towers)
- **Max Cap Respect**: Respects maximum tax revenue cap from config
- **Real-Time Updates**: Refresh button properly updates all colony data including approximate income

#### Technical Changes:
- **`ColonyDataCollector.java`**:
  - Rewrote `calculateApproximateRevenue()` to use actual config values
  - Added happiness multiplier calculation matching `TaxManager` logic
  - Added guard tower boost calculation using `TaxConfig.getRequiredGuardTowersForBoost()`
  - Added max revenue cap enforcement
  - Replaced hardcoded 3.5 per building with config-based estimates

#### Debug Tax Command (`/wnt debugtax <colony>`):
- **Admin Command**: Requires OP level 2, provides detailed tax breakdown
- **Current Balance**: Shows stored tax for colony
- **Happiness Analysis**:
  - Enabled/disabled status
  - Average colony happiness (0-10 scale)
  - Tax multiplier applied (e.g., 1.22x for 122%)
  - Visual color coding (green bonus, red penalty, yellow neutral)
- **Guard Tower Boost**:
  - Tower count vs requirement
  - Boost percentage and activation status
  - Color-coded active/inactive display
- **Building Breakdown**:
  - First 15 buildings with individual tax/maintenance
  - Per-building level and net income
  - "... and X more buildings" summary
- **Summary Statistics**:
  - Total buildings in colony
  - Base tax (before happiness modifier)
  - Generated tax (with happiness modifier)
  - Guard tower boost amount (if active)
  - Total maintenance costs
  - **Net Income Per Interval** (matches actual generation)
  - Max tax revenue cap

#### Command Output Example:
```
═══════════════════════════════════════
📊 TAX DEBUG BREAKDOWN: MyColony
═══════════════════════════════════════
Current Balance: 1500

🎭 Happiness Modifier:
  Enabled: YES
  Avg Happiness: 7.20/10.0
  Multiplier: 1.22x (122%)

🏰 Guard Tower Boost:
  Guard Towers: 6 / 5 required
  Boost: 25% (ACTIVE)

🏘️ Building Breakdown:
  Town Hall (L5): +15 tax, -5 maint = +10 net
  Guard Tower (L3): +8 tax, -3 maint = +5 net
  ... and 42 more buildings

📋 Summary:
  Total Buildings: 45
  Base Tax (before happiness): 180
  Generated Tax (with happiness): 220
  Guard Tower Boost: +55
  Total Maintenance: -90
  Net Income Per Interval: +185
  Max Tax Cap: 5000
═══════════════════════════════════════
```

#### Use Cases:
- **Players**: Click refresh in Tax GUI to see accurate income estimates
- **Admins**: Use `/wnt debugtax` to troubleshoot tax calculation issues
- **Server Operators**: Verify config values are working as intended
- **Debugging**: Identify why colonies aren't generating expected taxes

#### Benefits:
- ✅ Tax GUI shows realistic income projections
- ✅ Players can plan economy based on accurate estimates
- ✅ Admins can verify config changes immediately
- ✅ Troubleshoot happiness modifier effects
- ✅ Verify guard tower boost activation
- ✅ Confirm maintenance costs are correct
- ✅ Match expected vs actual tax generation

#### Documentation:
- Created `TAX_GUI_AND_DEBUG_FIXES.md` with complete implementation details
- Includes verification steps, troubleshooting guide, and config reference
- Documents calculation flow matching `TaxManager.generateTaxesForAllColonies()`

## [3.2.5] - 2025-10-15

### 🔒 Raid Announcement Privacy Fix

- **FIXED**: **Hostile and Neutral Player Announcements** - Raid notifications now only sent to colony allies
- **Security Improvement**: Hostile players no longer receive raid alerts when their target colony is being raided
- **Privacy Enhancement**: Neutral (non-allied) players are excluded from all raid-related announcements
- **Targeted Notifications**: Only colony Owner, Officers, and Friends receive raid announcements including:
  - Raid start alerts ("The Colony is currently being raided!")
  - Raid boss bar progress tracking
  - Guard/militia defender kill notifications
  - Hostile player entry warnings
  - Raid completion/failure messages
  - Tax transfer notifications
  - Raid end title commands

#### Technical Implementation:
- Updated `sendColonyMessage()` in `RaidManager.java` to filter by rank (Owner/Officer/Friend only)
- Updated `sendColonyMessageExcluding()` in `RaidManager.java` with same filtering logic
- Updated `sendColonyMessage()` in `WarSystem.java` to exclude Hostile and Neutral ranks
- Updated `sendColonyMessage()` in `WarEventHandler.java` with rank filtering
- Updated raid kill notifications in `RaidKillTracker.java` to filter recipients
- Updated boss bar participant list in `ActiveRaidData.java` to exclude non-allies
- Applied consistent filtering across all raid-related announcement systems (6 locations in RaidManager alone)
- Added explicit comments documenting exclusion of Hostile and Neutral players

### 🛡️ Block Interaction Filter System

- **NEW**: **Configurable Block Blacklist/Whitelist** - Server-controlled block interaction filtering during raids and wars
- **Anti-Griefing Protection**: Blacklist prevents interaction with critical blocks (overrides ALL other protection systems)
- **Gameplay Flexibility**: Whitelist explicitly allows interaction with specific blocks (e.g., chests for looting)
- **HIGHEST Priority Enforcement**: Runs before all other protection handlers to guarantee rule enforcement
- **Modded Block Support**: Works with any mod using standard block registry IDs (`modid:blockname`)
- **Configurable Scope**: Enable/disable filtering separately for wars and raids
- **Default Security**: Protects bedrock, command blocks, structure blocks, and MineColonies town halls by default
- **Default Whitelist**: Allows chest, barrel, furnace, and hopper interactions by default
- **Smart Conflict Detection**: Only activates during active raids/wars, zero overhead otherwise
- **Comprehensive Coverage**: Filters block breaking, placement, and usage (right-click) interactions

#### Configuration Options:
- `EnableBlockInteractionFilter` - Master toggle for the system (default: true)
- `BlockInteractionBlacklist` - List of blocks that CANNOT be interacted with (highest priority)
- `BlockInteractionWhitelist` - List of blocks that CAN be interacted with (overrides normal restrictions)
- `BlockFilterWars` - Apply filtering during wars (default: true)
- `BlockFilterRaids` - Apply filtering during raids (default: true)

#### Security Architecture:
- **Priority Order**: Blacklist > Whitelist > Existing Protection Systems
- **Immutable Config**: Configuration values cannot be modified at runtime
- **EventPriority.HIGHEST**: Guarantees filter runs before all other protection handlers
- **Audit Logging**: All denied interactions logged with player, block, position, and reason
- **Thread-Safe**: Uses immutable Set copies for blacklist/whitelist access
- **No Bypass Exploits**: All interaction types (break/place/use) comprehensively covered

#### Technical Implementation:
- Created `BlockInteractionFilterHandler.java` event handler with HIGHEST priority
- Added 5 new configuration options to `TaxConfig.java` with secure getter methods
- Integrated with `RaidManager` active raid detection system
- Integrated with `WarSystem` active war detection system
- Created comprehensive documentation: `docs/BLOCK_INTERACTION_FILTER_SYSTEM.md`
- Created technical summary: `BLOCK_FILTER_IMPLEMENTATION_SUMMARY.md`

#### Default Blacklist (Protected):
- `minecraft:bedrock` - World boundaries
- `minecraft:command_block` - Admin tools
- `minecraft:chain_command_block` - Admin tools
- `minecraft:repeating_command_block` - Admin tools
- `minecraft:structure_block` - World edit tools
- `minecraft:jigsaw` - Generation tools
- `minecolonies:blockhuttownhall` - Colony center

#### Default Whitelist (Accessible):
- `minecraft:chest` - Looting during raids
- `minecraft:barrel` - Storage access
- `minecraft:furnace` - Resource blocks
- `minecraft:blast_furnace` - Resource blocks
- `minecraft:smoker` - Resource blocks
- `minecraft:dropper` - Automation blocks
- `minecraft:dispenser` - Automation blocks
- `minecraft:hopper` - Automation blocks

### 🌐 Web API for WarStats

- **NEW**: **REST API Server** - Secure HTTP server for exposing war statistics to external websites and applications
- **5 REST Endpoints**: Health check, all player stats, leaderboards, individual player lookup, server statistics
- **Offline Player Support**: Optional caching system to include statistics from offline players
- **API Key Authentication**: Configurable authentication with X-API-Key header
- **Rate Limiting**: Per-IP rate limiting (default: 60 requests/minute) to prevent abuse
- **CORS Support**: Cross-Origin Resource Sharing enabled for web browser access
- **Read-Only Access**: GET-only endpoints prevent data modification
- **Zero Client Impact**: Server-side only execution, no client-side requirements
- **Intelligent Caching**: Background NBT parsing with configurable refresh intervals
- **JSON Responses**: Clean, structured JSON data for easy integration

#### API Endpoints:
- `GET /api/health` - Server health check and feature availability
- `GET /api/warstats/all` - Retrieve all player war statistics
- `GET /api/warstats/leaderboard?sort=warsWon&limit=50` - Sorted leaderboards
- `GET /api/warstats/player/{uuid}` - Individual player statistics by UUID
- `GET /api/warstats/server` - Server-wide statistics and aggregates

#### Configuration Options:
- `EnableWebAPI` - Master toggle for the API server (default: false)
- `WebAPIPort` - HTTP port for the API server (default: 8090)
- `WebAPIKey` - API key for authentication (default: "change-me-in-production")
- `WebAPIRateLimitRequestsPerMinute` - Rate limit per IP (default: 60)
- `WebAPIRequireAuthentication` - Require API key authentication (default: true)
- `WebAPIEnableOfflinePlayers` - Include offline player statistics (default: false)
- `WebAPICacheRefreshMinutes` - Offline cache refresh interval (default: 10)

#### Security Features:
- **Authentication Required**: API key protection enabled by default
- **Rate Limiting**: Per-IP request throttling prevents abuse
- **Read-Only**: No write operations, GET requests only
- **Input Validation**: All parameters validated and sanitized
- **Daemon Threads**: Background processing won't block server shutdown
- **Error Handling**: Graceful error responses with proper HTTP status codes

#### Offline Player Caching:
- **NBT Parsing**: Scans `world/playerdata/*.dat` files for war statistics
- **Background Processing**: Non-blocking cache refresh every 10 minutes (configurable)
- **Memory Efficient**: ~500 bytes per player (~5MB for 10,000 players)
- **Thread-Safe**: ConcurrentHashMap for concurrent access
- **Opt-In**: Disabled by default for zero performance impact
- **Query Parameter**: `?includeOffline=true` to include offline players in results

#### Technical Implementation:
- Created `WebAPIServer.java` - HTTP server with security features and endpoint routing
- Created `WarStatsAPIData.java` - Data collection, JSON serialization, and online/offline merging
- Created `PlayerDataCache.java` - Offline player NBT parsing and intelligent caching
- Added 7 configuration options to `TaxConfig.java`
- Integrated server lifecycle in `MineColonyTax.java` (start on ServerStartingEvent, stop on ServerStoppingEvent)
- Created comprehensive documentation: `WEB_API_DOCUMENTATION.md`
- Created implementation summary: `WEB_API_IMPLEMENTATION_SUMMARY.md`

#### Performance Characteristics:
- **Response Time**: <1ms for online players, <10ms with offline data included
- **Disk I/O**: 1-5 seconds per cache refresh (background, non-blocking)
- **Memory**: Minimal overhead, cached data only when offline support enabled
- **Async Processing**: Daemon thread pool for concurrent request handling
- **No Dependencies**: Uses Java built-in HttpServer (com.sun.net.httpserver)

#### Example Usage:
```bash
# Get all player statistics
curl -H "X-API-Key: your-api-key" http://localhost:8090/api/warstats/all

# Get leaderboard sorted by wars won
curl -H "X-API-Key: your-api-key" \
  "http://localhost:8090/api/warstats/leaderboard?sort=warsWon&limit=10"

# Get specific player stats (includes offline players if cached)
curl -H "X-API-Key: your-api-key" \
  http://localhost:8090/api/warstats/player/550e8400-e29b-41d4-a716-446655440000

# Include offline players in results
curl -H "X-API-Key: your-api-key" \
  "http://localhost:8090/api/warstats/all?includeOffline=true"
```

## [3.2.4] - 2025-10-09

### 🐛 Critical PvP Arena Duplication Glitch Fix

- **FIXED**: **Item Duplication Exploit** - Eliminated critical duplication glitch in PvP Arena system
- **Root Cause**: Inventory was saved at match start and restored at match end. Players could move items into containers (backpacks, shulker boxes, etc.) during matches, and restored inventory would duplicate those moved items
- **Solution**: Removed inventory save/restore system entirely - players now naturally keep their actual inventory throughout matches
- **Keep Inventory Behavior**: Players maintain their inventory during matches without any save/restore snapshots
- **Death Handling**: Defeated players are converted to spectator mode (preserving inventory) rather than clearing inventory
- **No Item Loss**: Players start with their inventory and leave with their inventory - no clearing, no snapshots, no duplication

#### Technical Implementation:
- Removed `saveInventory()` call from battle start sequence (`startBattle()`)
- Removed `restoreInventory()` call from player restoration sequence (`restorePlayer()`)
- Deprecated `saveInventory()` and `restoreInventory()` methods (kept as NO-OPs for compatibility)
- Deprecated `playerInventories` and `playerArmor` maps in `PvPManager`
- Added cleanup logic to remove any legacy inventory data
- Players naturally keep their actual inventory state throughout the entire match lifecycle

## [3.2.3] - 2025-10-06

### ⚔️ PvP Arena Death Handling Fix

- **FIXED**: **Instant Teleport Bug** - Players are no longer immediately teleported back when killed in PvP Arena battles
- **5-Second Spectator Mode**: Defeated players now properly stay in spectator mode for 5 seconds before restoration
- **Inventory Preservation**: Player inventories are saved at battle start and properly restored after the spectator delay
- **Proper State Management**: Added defeated player tracking system to prevent duplicate handling and ensure smooth transitions
- **Battle End Integration**: Defeated players are handled independently from battle end, preventing conflicts during restoration
- **Disconnect Safety**: Defeated player tracking is properly cleaned up when players disconnect

#### Technical Implementation:
- Added `defeatedPlayers` tracking map to `PvPManager` for state management
- Modified `handlePlayerDefeat()` to schedule 5-second delayed restoration using battle end scheduler
- Created `restoreDefeatedPlayer()` method for clean player state restoration
- Updated `endBattle()` to skip players already being restored individually
- Enhanced `handlePlayerDisconnect()` to clean up defeated player tracking

### 📢 War Notification System Overhaul

- **IMPROVED**: **Hybrid Notification System** - War messages now use intelligent targeting based on message type
- **Server-Wide War Announcements**: Major war events are now broadcasted to the entire server for awareness and engagement:
  - War declarations (regular and extortion wars)
  - War acceptance/decline responses
  - War initiated messages (auto-accepted wars)
  - War begin announcements
  - Victory/defeat/stalemate results
  - War cancellation messages
- **Targeted Participation Messages**: War participation details sent only to relevant colony officers/owners:
  - Join phase announcements and countdowns
  - Boss bar updates and progress tracking
  - Ongoing war status updates
- **Reduced Spam**: Non-participants no longer receive join phase or internal war status messages
- **Enhanced Awareness**: Server-wide knowledge of wars creates better community engagement and diplomacy opportunities
- **FTB Teams Integration**: Team members from both sides receive appropriate notifications based on their involvement

#### Technical Implementation:
- Created `broadcastToServer()` helper method for server-wide announcements
- Maintained `sendNotificationToWarParticipants()` for targeted officer/friend notifications
- Updated `broadcastComponent()` to use server-wide broadcasts for war results
- Converted 10+ message locations to use appropriate notification method based on message type
- Preserved existing boss bar and join phase targeting logic

---

## [3.2.0] - 2025-09-19

### 🏛️ Colony Auto-Abandonment & Claiming System

- **NEW FEATURE**: **Automatic Colony Abandonment** - Colonies automatically become abandoned after a configurable period of owner/officer inactivity (default: 2 weeks).
- **Colony Claiming Raids**: Abandoned colonies can be claimed by eligible players using `/wnt claimcolony <colony>`, triggering a 5-minute raid where:
  - All citizens become hostile militia with resistance effects
  - Mercenaries spawn if fewer than 5 citizens/guards exist
  - Victory conditions: Kill ALL defenders to win - timer expiration results in defender victory
  - Successful claimers automatically become Officers of the colony
- **Offline Notifications**: Players receive notifications when rejoining if their colony was abandoned or claimed while offline.
- **Admin Commands**: `/wnt forceabandon <colony>` for manual colony abandonment.
- **Smart Entry Messages**: Players entering abandoned colonies see claimability status and eligibility requirements.

#### Configuration Options:
- `AutoAbandonmentEnabled` (default: true)
- `ColonyInactivityDays` (default: 14)
- `ClaimingRaidDurationMinutes` (default: 5)
- `ClaimingRequirements` (configurable building/level requirements)

### 🏗️ Advanced Building Requirements System

- **NEW FEATURE**: **Configurable Building Requirements** for raids, wars, and colony claiming.
- **Smart Format**: `building:level:amount` syntax (e.g., `townhall:2:1,guardtower:1:3`).
- **Priority System**: Building requirements take precedence over legacy guard count settings when enabled.
- **Conflict Resolution**: Automatic handling of conflicting configuration values.

#### Default Requirements:
- **Raids**: Townhall (level 1) + 3 Guard Towers (level 1)
- **Wars**: Townhall (level 2) + 3 Guard Towers (level 1) + Builder's Hut (level 1) + 1 Residential Hut (level 1)
- **Colony Claiming**: Configurable (default: owning a colony with 3 guards)

#### Configuration Options:
- `EnableRaidBuildingRequirements` / `EnableWarBuildingRequirements`
- `RaidBuildingRequirements` / `WarBuildingRequirements`
- Legacy settings (`MinGuardsToRaid`, `MinGuardsToWageWar`) used as fallback when building requirements disabled

### ⚔️ Enhanced War Completion & Economy System

- **MAJOR OVERHAUL**: **Single Winner Reward System** - Only ONE player (colony owner/officer) receives ALL war rewards.
- **Priority-Based Selection**: Rewards distributed in order: Colony Owner > Officers > Any Participant > Fallback to Owner.
- **Comprehensive Participant Handling**: ALL losing participants have their balance deducted when wars are lost.
- **Multi-Economy Support**: Full compatibility with SDMShop, inventory-based currency, and colony tax systems.
- **Participant-Only Messaging**: War economy transactions now only visible to war participants (no server-wide spam).
- **Colony Transfer Integration**: Automatic colony ownership transfer when enabled and attackers win.

#### Economy Features:
- **SDMShop Integration**: Direct balance transfers between participants
- **Inventory Currency**: Physical item transfers with detailed tracking
- **Colony Tax System**: Tax pool transfers between colonies
- **Transaction Transparency**: Detailed breakdowns showing who lost/gained what amounts

### 🛡️ Enhanced War Participation System

- **IMPROVED**: **Officer & Friendly War Invitations** - All colony Officers and Friendlies now receive comprehensive war join prompts.
- **Detailed Notifications**: Rich, formatted messages explaining war status, player roles, and join options.
- **Multi-Level Support**: Colony-based invitations + FTB Teams integration for broader participation.
- **Clear Role Communication**: Players informed of their rank and eligibility status.

#### Notification Types:
- **⚔️ WAR DECLARED**: For attacking colony members
- **🛡️ COLONY UNDER ATTACK**: For defending colony members  
- **⚔️ TEAM WAR DECLARED**: For FTB Teams attackers
- **🛡️ TEAM COLONY UNDER ATTACK**: For FTB Teams defenders

### 🎯 Raid Progress Tracking Fixes

- **FIXED**: **Boss Bar Progress Display** - Raid progress now correctly shows "X/Y Guards" killed instead of "0/Y".
- **Self-Healing System**: Automatic detection and correction of defender count initialization issues.
- **Enhanced Debugging**: Comprehensive logging for progress tracking troubleshooting.
- **Universal Compatibility**: Works with militia enabled/disabled configurations.

### 💰 Kill Counter & Tax Reward Improvements

- **FIXED**: **Claiming Raid Kill Tracking** - Guards and militia kills during claiming raids now properly trigger tax rewards.
- **Immediate Tax Awards**: Per-kill tax rewards during claiming raids with proper balance integration.
- **Enhanced Death Penalties**: Improved raider death penalty system with raid-specific messaging.
- **Economy Integration**: Seamless SDMShop and colony tax system integration for all reward types.

### 📋 New Commands & Features

#### New Commands:
- `/wnt claimcolony <colony>` - Claim an abandoned colony
- `/wnt listabandoned` - List all abandoned colonies  
- `/wnt forceabandon <colony>` - Admin-only colony abandonment

#### Enhanced Commands:
- `/wnt help raid` / `/wnt help wagewar` - Now show building requirements or legacy guard requirements based on configuration
- All commands now provide clearer feedback and requirement validation

### 🔧 Technical Improvements

- **New Classes**:
  - `ColonyAbandonmentManager` - Handles automatic abandonment and notifications
  - `ColonyClaimingRaidManager` - Manages claiming raid mechanics
  - `BuildingRequirementsManager` - Centralized building requirement validation
  - `CitizenMilitiaManager` - Enhanced militia and kill tracking
- **Enhanced Classes**:
  - `WarSystem` - Complete war completion overhaul
  - `RaidManager` - Building requirements integration
  - `PvPKillEconomyHandler` - Enhanced death penalty system
  - `WarEconomyHandler` - Public API methods for economy integration

- **MineColonies Hut Recipe Toggle**: Added config-driven recipe disabling for MineColonies building huts. New key `DisableHutRecipes` under `["War Settings"."Recipe Disabling"]` injects/removes a world datapack (`mct_disable_huts`) on world start to disable/restore all hut recipes. Works in singleplayer and servers; no manual datapack management required.

---

## [3.1.0] - 2025-09-11

### 🗡️ Raider Guard Kill Tax Stealing

- **NEW FEATURE**: Raiders can now steal a percentage of a colony's tax revenue by killing its guards and militia during a raid.
- **Kill-Based Rewards**: Each guard or militia kill contributes to the total percentage of tax revenue that can be stolen, up to a configurable maximum.
- **Strict Boundary Enforcement**: Raiders must remain within the colony's boundaries for the entire duration of the raid. Leaving the boundaries, even once, results in **instant disqualification** and the raid immediately ends with no reward.
- **Comprehensive Instructions**: Raiders now receive a detailed, formatted message upon starting a raid, explaining all rules, objectives, and potential rewards.
- **Enhanced Boss Bar**: The raid boss bar has been updated to show real-time status, including kill progress, remaining time, and a "DISQUALIFIED" state if the raider leaves the boundaries.
- **Death Penalty**: If a raider is killed, any potential earnings are transferred to the defending colony as a defense bonus.

### 🤝 War Extortion System Enhancements

- NEW: Interactive extortion prompt with clickable chat buttons for defenders: Accept War / Decline / Pay Extortion.
- Response Timer: Configurable 5-minute default; if no response, war starts automatically.
- Payment Flow: SDMShop wallet is used first; automatic fallback to defender colony funds with partial payments supported.
- Extortion Immunity: Paying extortion grants a configurable cooldown preventing repeated extortion attempts.
- Commands:
  - `/wnt wagewar <colony> <percent>` — declare war with optional extortion percentage (1–100).
  - `/wnt payextortion <colonyId> <percent>` — defenders pay to avoid war.
- Validation & Permissions: Owner/Officer-only actions, percentage consistency checks, attacker-online verification, and clear error feedback.
- Stability & UX:
  - Daemonized timer threads for extortion deadlines (prevents server shutdown hangs).
  - Pending request cleanup on payment/response to prevent duplicate war starts.
  - War response handling updated to support extortion-request records safely.

#### Configuration
- `EnableExtortionSystem` (boolean)
- `DefaultExtortionPercentage` (double 0.0–1.0; e.g., 0.15 => 15%)
- `ExtortionResponseTimeMinutes` (int, default 5)
- `ExtortionImmunityHours` (int, default 24)

Technical references:
- Commands and payment flow: `src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java`
- War flow, timers, immunity, pending requests: `src/main/java/net/machiavelli/minecolonytax/WarSystem.java`
- Config keys: `src/main/java/net/machiavelli/minecolonytax/TaxConfig.java`

---

## [3.0.0] - 2025-09-07

### 🎨 Enhanced Tax Report Design

- **Redesigned Tax Reports**: Complete visual overhaul with color-coded sections and improved formatting
- **Color-Coded Information**: Green for revenue, red for maintenance, blue for bonuses, yellow for warnings
- **Removed Emojis**: Clean, professional appearance without emoji clutter
- **Better Structure**: Organized layout with clear separators and logical information flow
- **Multilingual Support**: Updated translations for English, German, Spanish, French, Russian, and Chinese
- **Status Indicators**: Clear visual feedback for debt, capacity warnings, and healthy finances

### 😊 Happiness-Based Tax Modifiers

- **NEW FEATURE**: Colony tax generation now affected by average citizen happiness (0.0-10.0 scale)
- **Dynamic Tax Impact**: Happy colonies (7-10 happiness) generate up to 50% bonus tax, unhappy colonies (0-4 happiness) suffer up to 50% tax penalty
- **Clear Reporting**: Tax reports show exact coin amounts gained/lost due to happiness, not confusing percentages
- **Smart Calculation**: Uses average happiness of adult citizens only, with graceful fallbacks for missing data
- **Configurable System**: New config options for enabling/disabling and adjusting min/max multipliers (0.1-2.0 range)
- **Professional Display**: Happiness impact shown as "+50 coins" or "-30 coins" with color-coded formatting

### 🏛️ Colony Inactivity Tax Pause System

- **NEW FEATURE**: Tax generation automatically pauses for inactive colonies when owners/officers haven't visited
- **Smart Integration**: Uses MineColonies' built-in activity tracking (`getLastContactInHours()`)
- **Configurable Threshold**: Default 168 hours (1 week), range 1 hour to 1 year
- **Master Toggle**: Can be completely enabled/disabled via `EnableColonyInactivityTaxPause` config
- **Performance Optimized**: Efficient early return for inactive colonies with minimal overhead

### 🛡️ Raid Defense Reward Integration

- **NEW FEATURE**: Unified raid defense reward system directly integrated into main tax balance for seamless experience
- **Configurable Reward Percentage**: Default 10% of raider's balance transferred as defense reward when raider is killed during raids
- **Unified Balance Display**: Raid defense rewards now visible in standard `/wnt checktax` command and GUI alongside regular tax revenue
- **Simplified Claiming**: Single `/wnt claimtax` command now claims both tax revenue and raid defense rewards from unified balance
- **Enhanced Notifications**: Updated raid completion notifications to direct players to check tax balance for defense rewards
- **Streamlined Architecture**: Removed separate raid reward storage system for cleaner, more maintainable codebase
- **Backward Compatible**: Existing tax systems continue to work unchanged while gaining raid reward integration

### ⚔️ Militia Combat System Overhaul

- **CRITICAL FIX**: Completely rewrote militia combat AI to resolve server crashes and ensure militia actually attack raiding players
- **Custom Attack Goal**: Created `MilitiaAttackGoal` that bypasses MineColonies citizens' missing `ATTACK_DAMAGE` attribute requirement
- **Crash Prevention**: Eliminated attribute-related crashes that previously prevented militia from functioning during raids
- **Active Combat**: Militia now actively pursue and attack raiding players instead of just targeting them without engaging
- **AI Goal Management**: Militia system clears conflicting AI goals and adds high-priority combat behaviors during raids
- **Equipment Integration**: Militia automatically receive wooden swords and proper combat equipment during raid activation
- **Performance Optimization**: Reduced glow effect logging spam by only applying effects when not already present
- **Stable Operation**: Militia system now operates without server crashes or attribute-related errors
- **Clean Restoration**: Militia AI and equipment are properly restored to original state when raids end

---

## [3.4.5] - 2025-09-06

### 🛡️ Guard Resistance During Raids and Wars

- **NEW FEATURE**: Colony guards now receive configurable resistance effects during raids and wars to help defend their colonies
- **Configurable Effect Level**: New `GuardResistanceLevel` config (default: 2) sets the resistance effect intensity (1-255)
- **Master Toggle**: New `EnableGuardResistanceDuringRaids` config (default: true) to enable/disable the entire system
- **Automatic Application**: Resistance effects are automatically applied to all guards when a raid or war starts
- **Smart Detection**: Identifies guards in various military buildings (guard towers, barracks, combat academy, archery)
- **Automatic Cleanup**: Resistance effects are automatically removed when raids or wars end (successful completion, raider death, war victory, or interruption)
- **Duration Management**: Effects last for the full duration of any raid or war (up to 2 hours maximum for raids)
- **Visual Feedback**: Guards display the resistance effect icon, making it clear they're protected
- **Performance Optimized**: Minimal overhead with efficient guard tracking and cleanup systems
- **Safe Operation**: Emergency cleanup prevents orphaned effects if server issues occur

---

## [2025-08-19]

### 🛡️ War Tax Protection

- Prevents claiming taxes from colonies during both the join phase and active war phase
- Consistent checks across backend and command pathways with clear player feedback
- Ensures no tax can be claimed from colonies involved in wars

### ⚔️ PvP Kill Economy (configurable)

- NEW FEATURE: Reward killers with a configurable percentage of the victim's balance
- Disabled by default; configure in `config/warntax/minecolonytax.toml`
- Config keys: `ENABLE_PVP_KILL_ECONOMY` (default: false) and `PVP_KILL_REWARD_PERCENTAGE` (default: 0.10)
- Integrates with `SDMShopIntegration` when SDMShop is present; otherwise falls back to item-based transfer
- Ignores self-kills and non-player kills; includes player notifications and detailed logging

---

## [2025-08-18] - Critical Fix Update

### 🚨 **CRITICAL FIX: Tax Intervals Not Working with Server Restarts**

- **Fixed tax generation being based on server uptime instead of real-world time**
- Tax intervals now use persistent timestamps that survive server restarts
- Added `config/warntax/lastTaxGeneration.json` to track timing across restarts
- Improved performance by reducing timing checks from 20/second to 1/second
- Added validation for corrupted timestamps and system clock changes

**Impact**: Tax intervals now work correctly regardless of server restart schedules. A 6-hour tax interval will generate taxes every 6 real-world hours, even if the server restarts every 12 hours.

---

## [Previous Release] - 2025-08-12

### 🆕 Enhanced Entity Raid System

- **NEW FEATURE**: Added comprehensive Entity-Triggered Raid system for automatic raid initiation based on hostile entity presence
- **Entity Detection**: Configurable whitelist of entities that can trigger raids (default: zombies, skeletons, creepers, witches, pillagers)
- **Threshold-Based Triggering**: Raids trigger when a configurable number of whitelisted entities are detected inside colony boundaries (default: 5 entities)
- **Colony Boundary Enforcement**: Entities must remain within colony boundaries during raids, with configurable grace period for re-entry

#### 🎮 **Dynamic Raid Experience**
- **Dynamic Bossbar**: Real-time bossbar displaying remaining attacking entities and countdown timer
- **Smart Grace Period**: 5-second grace timer activates only when ALL entities leave boundary, pauses/resumes if entities return
- **Fixed Duration Raids**: Raids last exactly 5 minutes regardless of entity movement (configurable)
- **Entity Count Display**: Bossbar shows "X entities attacking" and updates dynamically as entities are killed/leave
- **Accurate Timer**: Fixed timer display bug - now shows proper countdown (e.g., "4m 23s left")

#### 💰 **Economic Impact System**
- **Configurable Tax Deductions**: Automatic tax revenue penalties every minute during active raids
- **Percentage-Based Penalties**: Uses configured `RaidPenaltyPercentage` (e.g., 25% per minute)
- **Real-Time Notifications**: Players receive tax deduction alerts during raids
- **Revenue Protection**: Deductions are capped to prevent complete colony bankruptcy

#### 🛡️ **Advanced Alliance & Diplomacy**
- **MineColonies Integration**: Respects colony officer/friend ranks - allies won't trigger raids
- **Recruits Diplomacy Support**: Revolutionary integration with Recruits mod diplomatic system
- **Team-Based Filtering**: Recruits with ALLY diplomatic status are excluded from triggering raids
- **Multi-Layer Alliance Detection**: Checks Recruits diplomacy → team membership → ownership hierarchy
- **Cross-Mod Compatibility**: Works seamlessly whether Recruits mod is present or not

#### ⚙️ **System Improvements**
- **Cooldown System**: Configurable cooldown periods between entity raids for the same colony (default: 30 minutes)
- **Chat Deduplication**: Fixed duplicate notification spam to colony members
- **Performance Optimized**: Configurable check intervals to balance detection accuracy with server performance
- **Admin Commands**: Complete `/wnt entityraid` command suite for testing, monitoring, and management
- **Integration Safety**: Entity raids won't trigger if colonies are already under player raids or in wars
- **Smart Defaults**: Entity raids are **disabled by default** to allow server administrators to enable and configure as needed

### 🔓 General Colony Permissions System

- **NEW FEATURE**: Added General Colony Permissions system for universal item interactions within colonies  
- **Universal Access**: Allows **all players** (including non-allies, strangers, and enemies) to drop and pickup items in colony boundaries
- **Configurable Actions**: Default permissions include `TOSS_ITEM` and `PICKUP_ITEM` (block interactions can be added via configuration)
- **MineColonies Integration**: Leverages native MineColonies permission system by modifying neutral and hostile ranks
- **Automatic Application**: Permissions automatically applied to all colonies on server startup
- **Permission Preservation**: Original permissions are safely stored and can be restored when system is disabled
- **Admin Control Suite**: Complete `/wnt permissions` command system for granular management:
  - `/wnt permissions status` - View current permissions status across all colonies
  - `/wnt permissions config` - Display current configuration settings
  - `/wnt permissions apply/remove` - Apply or remove permissions from all colonies
  - `/wnt permissions reload` - Refresh permissions based on current configuration
  - `/wnt permissions apply/remove <colonyId>` - Target specific colonies
- **Enabled by Default**: System is active by default to provide immediate improved player experience
- **Colony-Specific Management**: Individual colonies can have permissions applied or removed independently
- **Safe Restoration**: Complete rollback capability to restore original MineColonies permissions
- **Performance Efficient**: Minimal overhead with smart caching and batch operations

### 🔧 War System Fixes

- **Fixed War Interaction Permissions**: Resolved issue where allies and officers on the attacker's side could not break blocks or interact with containers during wars
  - Added `assignWarParticipantRanks` helper method to properly assign hostile ranks to war participants
  - Updated war initiation logic to assign hostile ranks to all participants (attackers, defenders, and FTB team members)
  - Fixed join war logic to assign hostile ranks when players join during the join/active phase
  - Ensures all war participants get proper permissions to interact with opposing colonies during conflicts

### 🔧 PvP Configuration Overhaul

- **Centralized PvP Settings**: All PvP-related settings have been moved into the main `minecolonytax.toml` config file under the `["PvP Arena Settings"]` section. This removes the separate `minecolonytax-pvp.toml` file and consolidates all server configurations into a single, easy-to-manage location.
- **Configurable Timers & Cooldowns**: Added new configuration options for all PvP countdowns and cooldowns:
    - `allowCommandsInBattle`: Toggle whether players can use commands during a battle.
    - `challengeCooldownSeconds`: Set the cooldown for duel challenges.
    - `teamBattleCooldownSeconds`: Set the cooldown for starting team battles.
    - `battleDurationSeconds`: Define the default length of a battle before it's declared a draw.
    - `teamBattleStartCountdownSeconds`: Control the countdown before a team battle begins.
    - `battleEndCountdownSeconds`: Adjust the delay before players are teleported back after a battle.
- **Improved Countdown Notifications**: The team battle start countdown is now less spammy. It notifies players at 10-second intervals until the last 5 seconds, at which point it notifies every second to build anticipation.
- **NEW FEATURE - Team PvP System**: Added comprehensive team-based PvP functionality with the new `/teampvp` command:
    - `/teampvp create <map>`: Create a new team battle on a specified map
    - `/teampvp join <battleId> <team>`: Join a team battle (team 1 or 2)
    - `/teampvp switch <battleId> <team>`: Switch teams within a battle
    - `/teampvp start <battleId>`: Start a team battle early (organizer only)
    - Team battles support multiple players per team with automatic balancing
    - Interactive team rosters with real-time updates
    - Configurable team sizes based on map capacity
    - Automatic countdown system with configurable duration

### 🛡️ Raid Guard Protection System

- **NEW FEATURE**: Added RaidGuardProtection system to protect smaller colonies from being overwhelmed by raids
- **Configurable protection requirements**: Target colonies must meet minimum defense requirements to be eligible for raids
- **Guard protection**: New `MinGuardsToBeRaided` config (default: 2) requires target colonies to have sufficient guards
- **Guard tower protection**: New `MinGuardTowersToBeRaided` config (default: 1) requires target colonies to have sufficient guard towers
- **Master toggle**: New `EnableRaidGuardProtection` config (default: true) to enable/disable the entire protection system
- **Enhanced raid command help**: `/wnt help raid` now shows current protection requirements dynamically
- **Clear feedback**: Raiders receive informative error messages when raids are blocked due to protection requirements
- **Localized messages**: Added translatable error messages for better international support
- **Performance optimized**: Efficient guard tower counting using building display name filtering
- **Admin flexibility**: Set either requirement to 0 to disable specific protection checks

### 🔧 Guard Tower Detection Bug Fix

- **CRITICAL FIX**: Fixed guard tower counting bug in RaidGuardProtection system
- **Root cause resolved**: Guard towers now properly detected using robust building type identification instead of unreliable display names
- **Multiple detection methods**: Implemented fallback detection using class names and building type patterns
- **Backwards compatibility**: Maintains support for existing display name detection while adding new robust methods
- **Future proof**: Will correctly identify new guard tower types and variations automatically
- **Enhanced debugging**: Added `/wnt debugguards [colony]` admin command for troubleshooting guard/tower counting issues
- **Comprehensive diagnostics**: Debug command shows guard counts, protection status, building analysis, and detection mismatches
- **Improved reliability**: Guard tower protection now functions correctly across all Minecolonies versions and configurations

### Death Processing Fixes

- **Fixed corpse spawning during wars**: Death events now properly process natural death mechanics, allowing Corpse mod and other death-related mods to function correctly
- **Fixed death messages and scoreboards**: Deaths in war now trigger proper death messages and update death scoreboards as expected
- **Improved inventory preservation**: Last life inventory preservation now works through respawn events for more reliable inventory restoration
- **Enhanced death debugging**: Added extensive debug logging for war death processing to help diagnose future issues

### Console Logging Control

- **Added configurable tax generation logging**: New `ShowTaxGenerationLogs` config option to reduce console spam during initialization
- **Preserved error logging**: Critical error messages still display regardless of logging setting
- **Improved server administration**: Cleaner server logs while maintaining debugging capabilities when needed

### Vassalization Feature Enhancements

- **Improved tribute display**: Vassal tribute payments now correctly displayed in tax reports
- **Enhanced `/wnt vasals` command**: Shows tribute percentage, last payment amount, and vassal status
- **Dynamic currency display**: Shows "$" if SDMShop is enabled and proper item name (e.g., "emerald") when using custom currency
- **Vassal status information**: Command now displays if the player is a vassal, including overlord name and tribute rate
- **Tribute payment tracking**: Added system to track and display the last tribute amount paid by vassal colonies

### War System Improvements

- **Added team selection feature**: Players who are members of both warring teams can now choose which side to join instead of being blocked from participating
- **New commands**: `/choosewarside attacker` and `/choosewarside defender` for selecting a team when dual membership is detected
- **Improved war participation**: Players receive clickable prompts in chat to select their preferred side

### Fixed
- **Server Startup Performance**: Drastically reduced log spam during server startup by condensing building detection messages into single summary per colony
- **Guard Tower Boost Bug**: Fixed guard tower tax boost being applied every tick instead of once per cooldown period (configurable, default 5 minutes)
- **Improved Logging**: Replaced individual building messages with comprehensive colony summaries showing processed buildings, tax generated, guard count, and max tax status
- **Duplicate Raid Tax Announcements**: Fixed issue where raid tax announcements were being displayed twice per interval. Messages are now sent only once to all relevant players without duplication, even if players are members of both colonies involved in the raid.


### 🏰 Guard Tower Tax Boost Implementation

- **NEW FEATURE**: Implemented the long-awaited guard tower tax boost system
- **Automatic Application**: Tax boost is now applied every tax interval when colonies have sufficient guard towers
- **Configurable Requirements**: `RequiredGuardTowersForBoost` setting determines how many guard towers are needed (default: 5)
- **Percentage-Based Boost**: `GuardTowerTaxBoostPercentage` setting controls the boost amount (default: 50% increase)
- **Tax Report Integration**: Guard tower boost information is now displayed in tax reports when applicable
- **Removed Unnecessary Cooldown**: Eliminated the `GuardTowerBoostCooldownMinutes` setting as the boost now applies every tax interval as intended
- **Robust Detection**: Uses multiple methods to detect guard towers (display name, class name, and toString analysis)
- **Performance Optimized**: Guard tower counting is integrated into the main tax generation cycle for efficiency

## [Previous Release] - 2025-01-30

### 🚀 Major Features Added

#### Unified Command System
- **BREAKING CHANGE**: All commands now require `/wnt` prefix (War 'N Taxes)
- Implemented comprehensive unified command handler in `WntCommands.java`
- Removed individual command auto-registration to prevent conflicts
- All functionality preserved under new command structure

#### Intelligent Command Suggestions
- **Colony name suggestions** with proper quote formatting for names containing spaces
- **Player name suggestions** for admin commands  
- **Colony ID suggestions** for war responses with context-awareness
- **Permission-based suggestions** that adapt to user access levels

#### Comprehensive Help System
- **Main help** via `/wnt` or `/wnt help` showing command overview
- **Command-specific help** via `/wnt help <command>` with detailed explanations
- **Permission-aware help** that hides admin commands from regular users
- **Context-sensitive help** with requirements and examples

### 🎮 Command Changes

#### New Unified Commands
All commands now use `/wnt` prefix:

**War Commands:**
- `/wnt wagewar "<colony>"` - Declare war (previously `/wagewar`)
- `/wnt raid "<colony>"` - Start raid (previously `/raid`)
- `/wnt joinwar` - Join war (previously `/joinwar`)
- `/wnt leavewar` - Leave war (previously `/leavewar`)
- `/wnt war accept/decline <colonyId>` - Respond to war (previously `/war`)
- `/wnt warinfo` - War status (previously `/warinfo`)

**Peace Commands:**
- `/wnt peace whitepeace` - Propose white peace (previously `/suepeace whitepeace`)
- `/wnt peace reparations <amount>` - Propose reparations (previously `/suepeace reparations`)
- `/wnt peace accept/decline` - Respond to peace (previously `/peace`)

**Tax Commands:**
- `/wnt claimtax [colony] [amount]` - Claim tax (previously `/claimtax`)
- `/wnt checktax [player]` - Check tax (previously `/checktax`)
- `/wnt taxdebt pay <amount> "<colony>"` - Pay debt (previously `/taxdebt pay`)

**Statistics Commands:**
- `/wnt warhistory [colony]` - View war history (previously separate command)
- `/wnt warstats` - Personal statistics (previously separate command)

**Admin Commands:**
- `/wnt wardebug` - Debug wars (previously `/wardebug`)
- `/wnt warstop "<colony>"` - Stop specific war (previously `/warstop`)
- `/wnt warstopall` - Stop all wars (previously `/warstopall`)
- `/wnt raidstop` - Stop raids (previously `/raidstop`)
- `/wnt taxgen disable/enable <colonyId>` - Control tax generation (previously `/taxgen`)

### 🔧 Technical Improvements

#### Command Architecture
- **Centralized command handling** in `WntCommands.java`
- **Removed redundant registrations** from individual command classes
- **Preserved all functionality** while eliminating command conflicts
- **Improved error handling** and user feedback

#### Parameter Processing
- **Smart colony name extraction** from quoted format
- **Automatic quote handling** for colonies with spaces in names
- **Robust parameter parsing** with fallback support
- **Context-aware validation** based on user permissions

#### Code Organization
- **Eliminated duplicate code** between old command classes and new unified system
- **Wrapper methods** for functionality that couldn't be directly delegated
- **Consistent error messaging** across all commands
- **Streamlined registration process** in `MineColonyTax.java`

### 🎨 User Experience Enhancements

#### Visual Improvements
- **Shortened tax report display** by reducing "=" characters by 8 for better chat fit
- **Consistent command formatting** across all help displays
- **Color-coded help sections** for better readability
- **Structured command lists** with clear categories

#### Accessibility
- **Tab completion** for all command parameters
- **Smart suggestions** that reduce typing errors
- **Context help** available at every step
- **Permission-appropriate** command visibility

### 🐛 Bug Fixes

#### Command Conflicts Resolved
- **Fixed duplicate command registrations** that caused conflicts
- **Eliminated `/checktax` bypass** that allowed old command usage
- **Resolved parameter suggestion issues** with bracket vs quote formatting
- **Fixed colony name parsing** for names containing spaces

#### Registration Issues
- **Removed `@Mod.EventBusSubscriber`** annotations from old command classes
- **Cleaned up `@SubscribeEvent`** methods to prevent auto-registration
- **Updated main registration** to only use unified command handler
- **Preserved PvP commands** as separate system (intentionally kept separate)

### 📝 Documentation Updates

#### README.md Complete Rewrite
- **Comprehensive command documentation** with examples and requirements
- **Feature-focused organization** highlighting key capabilities
- **Updated installation instructions** reflecting new command system
- **Detailed configuration guide** with all available options
- **Developer information** for contributors and mod developers

#### Help System Enhancement
- **In-game documentation** accessible via `/wnt help`
- **Command-specific guidance** with practical examples
- **Requirement explanations** for each command
- **Permission level clarification** for admin functions

### 🔄 Migration Notes

#### For Server Administrators
- **All commands now require `/wnt` prefix** - update any scripts or documentation
- **Old commands no longer work** - users will need to adapt to new system
- **All functionality preserved** - no features lost in transition
- **Enhanced admin tools** with improved debugging and control

#### For Players
- **Simple migration**: Add `/wnt` before existing commands
- **Improved help**: Use `/wnt help` to discover all available commands
- **Better suggestions**: Tab completion now shows proper formatting
- **Colony names**: Use quotes for names with spaces (e.g., `"My Colony"`)

### 🏗️ Internal Changes

#### Code Structure
- **Unified command registration** in single file
- **Eliminated redundant command handlers** 
- **Preserved all manager instances** (RaidManager, PeaceProposalManager, etc.)
- **Maintained compatibility** with existing data structures

#### Build System
- **Successful compilation** verified with all changes
- **No breaking changes** to mod loading or dependencies
- **Maintained Forge compatibility** 
- **Clean build output** with minimal warnings

---

## [Previous Versions]

### Features Preserved from Earlier Versions
- Complete war system with declarations, join phases, and combat
- Raid mechanics with territory requirements and penalties  
- Tax collection and debt management systems
- Peace proposal and diplomatic resolution systems
- Player statistics and war history tracking
- Admin tools for server management
- Comprehensive configuration options
- Crash logging and error handling
- SDMShop integration for currency handling
- PvP arena system (maintained as separate command structure)

---

## Migration Guide

### From Previous Versions

1. **Update all command usage** to include `/wnt` prefix
2. **Use quoted colony names** for colonies with spaces (e.g., `"Colony Name"`)
3. **Leverage new help system** with `/wnt help` and `/wnt help <command>`
4. **Take advantage of tab completion** for easier command usage
5. **Update any scripts or documentation** to reflect new command structure

### Command Mapping

| Old Command | New Command |
|-------------|-------------|
| `/claimtax` | `/wnt claimtax` |
| `/checktax` | `/wnt checktax` |
| `/wagewar` | `/wnt wagewar` |
| `/raid` | `/wnt raid` |
| `/joinwar` | `/wnt joinwar` |
| `/warinfo` | `/wnt warinfo` |
| `/peace` | `/wnt peace` |
| All others | Add `/wnt` prefix |

---

*This changelog documents the major refactoring to implement a unified command system while preserving all existing functionality and improving user experience.* 