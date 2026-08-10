# Changelog

All notable changes to the War N Tax mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [5.0.4] - 2026-08-07

NeoForge 1.21.1 only. Three systems looked fully implemented but were never actually reachable,
because the NeoForge port had lost the calls that drive them.

### Fixed - Spies stuck forever at "Traveling... 99%"

Players reported that **no spy mission ever arrived** — the espionage screen sat at 99% travelling
indefinitely. The port had lost the once-per-second espionage tick that the Forge 1.20.1 build runs,
so every mission stayed in its DEPLOYING state for good: the spy never landed at the target colony,
no spy ever spawned, and the progress display (which deliberately stops at 99% until arrival) had
nothing left to wait for. Recall journeys, the gathering of intel over time, and automatic mission
completion all hung off that same missing tick, so in practice the entire espionage system did
nothing beyond taking your deployment cost. The tick is wired again and respects the
`EnableSpySystem` switch.

Missions that have been stuck since before this update resolve themselves on the first tick after
the server restarts — expect a short burst of arrival and completion messages for them.

### Fixed - Random events never triggered

Players reported **never seeing a single random event**. The per-colony event check that runs at the
end of each tax cycle had no caller at all, so events could only ever be forced by hand with
`/wnt events force`. Every individual event toggle was already switched on — the configuration was
never at fault. The check now runs again for every colony during the tax cycle, guarded so that one
problematic colony cannot abort the cycle for everyone else.

A reminder for testing: events are deliberately paced. With a 60-minute tax interval, a two-cycle
global cooldown and 24 hours of protection for young colonies, the first event can take a couple of
hours to show up. Shorten `TaxIntervalMinutes` if you want to see them quickly.

### Fixed - Server crash when a non-member opened the war chest

Opening the war chest page as someone who is not a member of that colony threw a server-side error,
because the permission check read the player's rank without allowing for the "no rank at all" case
that non-members produce. The check is now safe, matching how the investments screen already did it.

### Fixed - Investments showing a price of zero

When the server declined an investments request, it did so without a word, and the screen filled the
gap with "Cost: 0 $" — which reads like a broken price rather than a missing answer. Both the
investments and war chest screens now state the actual reason in chat (feature switched off, colony
not present in your current dimension, or missing colony-manager rank), and the price line reads
"Cost: unavailable" instead of inventing a zero.

## [5.0.3] - 2026-07-27

> ⚠️ **This release is still being actively hardened and may still have bugs.** If you hit a crash
> or anything misbehaving — war/siege/tax logic, HYW troops, raids — please help the mod out and
> **report it on the [GitHub issue tracker](https://github.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/issues)**
> (or drop a note in the CurseForge comments). Every report genuinely speeds up the next fix —
> thanks for testing and supporting the mod! 🙏

### Fixed - HYW troops attacking their own town

Players reported that **Hundred Years' War (HYW) troops would attack the very colony they were
meant to protect** — arrowing their commander's own colonists, which turned the colony's guards
hostile and spiralled into a player's army fighting its own townsfolk. The HYW friendly-fire
integration prevents this while preserving siege warfare:

- An HYW troop can no longer damage or target a colonist (guards included) of its own commander,
  that commander's allied colonies, or any neutral colony; protection is symmetric, so friendly
  colonists won't start the fight either. The protection lifts only for colonies you are actually
  at war with — an active War 'N Taxes war, besiege, raid, or abandoned-colony claiming raid.
- Owner attribution is offline-safe (a troop's commander is read from synced owner data, so a
  logged-out player's standing army keeps respecting its town), and now resolves through a
  version-tolerant chain so a future HYW API rename degrades gracefully instead of silently
  disabling all protection. Ownerless HYW units (bandits) stay hostile.
- Verified — by decompiling the actual HYW 0.6.4r jar — to cover the entire HYW roster: foot
  troops, workers, all cavalry, and every siege engine. See the **Hundred Years' War Integration**
  wiki page. Toggle with `EnableHywFriendlyFireProtection` (default on).

### Fixed - Withdrawing tax was broken

Players reported that **claiming/withdrawing collected taxes silently failed or lost money**. All
three claim routes (the claim GUI packet, `/claimtax`, and `/wnt claimtax`) deducted the tax from
the colony ledger *before* delivering it and never refunded on a failed deposit, so the tax could
be wiped without the player receiving the coins. Delivery now credits atomically and refunds the
colony ledger on any failure — taxes are never silently lost.

### Fixed - Claiming taxes with SDM Economy now actually pays out

Players reported that **claiming taxes did nothing when SDM Economy was installed** — the coins
never reached the wallet. The economy bridge reflected the 1.20.1 Forge class name
(`net.sixik.sdm_economy.…`), which does not exist in the 1.21.1 NeoForge economy (`net.sixik.sdmeconomy.…`,
confirmed by decompiling the deployed sdmeconomy 2.4.0 jar), so it never resolved and every claim
silently refunded the colony instead of paying the player. The bridge now targets the real 2.x API
(`EconomyAPI` → `CurrencyPlayerData.Server.addCurrencyValue`, with the result checked and the client
re-synced) and pays into the currency named by the new **`SDMCurrencyName`** config (default
`sdmcoin`, matching the currency shipped in your SDM-Economy config). If SDM rejects the currency id
the claim still safely refunds the colony (never lost)
and logs the server's valid currency ids so you can correct the config.

### Fixed - Ownership / Officer & conflict-end safety pass (ported from Forge)

This port was still missing the entire 4.x/5.0 colony-abandonment hardening, so it was vulnerable
to the original world-brick bug. The full safety architecture plus a follow-up ownership/officer
audit are now applied:

- **World-brick fix.** The mod no longer runs colony null-owner repair *immediately* at server
  start (before MineColonies finished loading colonies) — that pass injected a synthetic
  `[AUTO_OWNER]` placeholder into transiently owner-null colonies and bricked worlds. Owner repair
  now runs only on a deferred pass, only when the new master switch is enabled.
- **New master switch `EnableColonyAbandonmentSystem` (default FALSE).** Out of the box the mod
  performs no automatic writes to MineColonies owner/permission state (auto-abandon,
  debt-bankruptcy abandonment, null-owner repair, abandoned-entry cleanup all require it).
- **No more synthetic owners.** `abandonColony` and `emergencyFixAllNullOwners` no longer inject an
  `[AUTO_OWNER]` placeholder; they promote a real online colony manager via `setOwner` or leave the
  colony genuinely owner-less. `isColonyAbandoned` is now a pure read (it used to mutate colony
  state and even inject `[AUTO_EMERGENCY_OWNER]` on every status check).
- **Safer cleanup.** Abandoned-entry cleanup now matches only the exact synthetic markers the mod
  writes, instead of broad heuristics (any name containing "abandoned", `~`/`#` prefixes,
  UUID-length checks) that could delete a real player and orphan a colony.
- **Self-healing migration.** An always-on, removal-only pass repairs worlds already corrupted by
  older versions, promoting a real manager to owner before stripping any placeholder.
- **Claiming an abandoned colony** no longer leaves two owners (the former owner is demoted), aborts
  cleanly if ownership assignment fails, no longer strands combat permissions when the feature is
  toggled off mid-raid, and no longer wipes citizen AI on cleanup.
- **A live besiege** is no longer broken by an unrelated war/raid end or `/wnt permcheck`
  (`PermissionSnapshot` / `PermissionsHealthCheck` now recognize besiege as a real conflict).
- **Crash-safe officer-visit saves** (atomic write-then-move for `officerVisitData.json`).

Build green (checkBuildingApiUsage + shim tests pass).

## [5.0.0] - 2026-06-05

Major release: full port from **Minecraft 1.20.1 / Forge** to **Minecraft 1.21.1 / NeoForge 21.1**.
Verified: dedicated server boots cleanly, MineColonies + its dependency stack load with no version
conflicts, and the redesigned book GUI renders correctly on a live client.

### Platform / Port
- Migrated the entire mod from Forge to **NeoForge 21.1.213** on **Minecraft 1.21.1** (Java 21, Gradle 8.14 / NeoGradle 7.1.1).
- Networking: Forge `SimpleChannel` → NeoForge **CustomPacketPayload** system (22 payloads).
- Player war data: Forge Capabilities → NeoForge **Data Attachments**.
- Registries/events/config migrated to `BuiltInRegistries`, `ServerTickEvent`, `ModConfigSpec`; `neoforge.mods.toml` metadata.
- Dependencies declared for 1.21.1 (MineColonies, Structurize, BlockUI, Domum Ornamentum, Multi-Piston, Architectury, FTB Library, FTB Teams).

### Added
- **War persistence** ported to NeoForge — active wars save on shutdown (`config/warntax/active_wars.json`) and resume on start, including downtime-expiry resolution.
- **SDMShop / SDM-Economy integration** rewired to the real `CurrencyHelper` economy API (reflection-based, fully optional).

### Changed
- **Redesigned Tax Management GUI** to the book-style layout: two-page book background, icon tabs (Colonies / Vassals / Officers / War Chest / Espionage / Economy), per-page content.
- **Concurrency:** all war/raid/tax/battle countdown timers moved from `java.util.Timer`/raw threads to the main-thread `TickScheduler` (prevents off-thread state mutation).
- Logging: stray `System.out`/`printStackTrace` routed through the mod loggers.

### Fixed
- **Colony ownership transfer (CRITICAL):** claiming an abandoned colony and reactivation no longer leave the colony ownerless. MineColonies changed `IPermissions.setOwner(UUID)` → `setOwner(Player)`; the old reflection threw at runtime (GUI crash, broken claim/abandonment). Now uses `setOwner(online player)`, with an OWNER-rank best-effort fallback for offline/synthetic owners.
- GUI no longer shows a dark overlay or the menu blur over the book (custom `renderBackground` no-op).
- Added the missing GUI textures (`book_background.png`, tab icons), the spy entity texture, and the codex item texture.
- Dedicated-server load-time defects that prevented the mod from loading at all: missing `[[mods]]` header in `neoforge.mods.toml`, missing transitive dependencies, invalid empty `@EventBusSubscriber` classes, and client-class references that crashed registration on a dedicated server (dist isolation).

### Siege SMP parity port (2026-06-14)

The NeoForge port had branched off before the Forge "Siege SMP" body of work landed, so its
first 5.0.0 build was missing those systems. This run brings NeoForge to full feature parity
with the Forge release. Everything below ships as part of 5.0.0 (no version bump).

- **War vassalization with a one-time "huge money" grab.** When you win an offensive war and
  colony transfers are turned off, the losing colony is now forced into vassalage instead of
  changing hands. On the moment of vassalization the winner seizes a one-time cut of the loser's
  war chest **and** a one-time cut of the defending owner's personal wallet (both percentages
  configurable). This makes "don't take their town, bleed them dry" a real strategic choice.
- **Forced vassalization auto-expiry.** War-imposed vassal status now ends on its own after a
  configurable number of hours (`WarVassalizationDurationHours`) rather than lasting forever until
  manually revoked.
- **Colony tiers and permission-guard foundations.** Groundwork that protects colony ownership and
  permission state during sieges and ownership changes, with a snapshot/restore safety net so a
  colony can't be left in a broken or ownerless permission state.
- **Upgrade / Investment system with an in-game book tab.** A new "Investments" page in the colony
  book lets you spend your war chest on lasting colony upgrades. (The Investments tab now occupies
  the slot the old Economy tab used; the standalone Economy tab has been removed.)
- **Besiege system — true multiplayer sieges.** Lay siege to another player's colony with support
  for multiple attackers at once, a configurable minimum-attacker requirement before a siege can
  start, and shared spoils split across everyone on the attacking side. Sieges require the target
  owner to be online (not solo), with a configurable offline grace window. Chest/container access
  during a siege is configurable, and a colony's original owner keeps access to their own colony
  even while vassalized.
- **Siege victory objectives (experimental).** Two new ways to win a siege: **Plant the Banner**
  (place and hold a siege banner inside the enemy colony) and **Demolish the Town Hall**. A
  war-damage ledger records blocks broken during the siege so the battlefield can be restored
  afterward.
- **Mod compatibility.** Optional, reflection-guarded integrations: **Explosion't** (war damage
  regeneration is deferred so siege damage sticks until the war resolves), **Easy Factions**
  (faction membership/rank sync), and **FTB Teams**.
- **Random-event history in the colony book.** The book's Events view now shows a running history of
  random events (with a dismiss option) alongside raid history, rows for any active war / siege /
  occupation, and a structured war-history log.
- **PvP crash-recovery persistence.** PvP arena state is now written to disk so an unexpected
  server crash mid-match no longer strands players or loses their pre-match position.
- **MineColonies building-API safety guard.** Added a `checkBuildingApiUsage` Gradle check (plus
  JUnit shim tests) that fails the build if code calls the MineColonies building manager directly.
  This prevents the `NoSuchMethodError` crashes that happen when running against a different
  MineColonies version, the same cross-version protection the Forge build has.

#### Intentionally not ported
- The legacy MySQL-based war-stats push (`WarStatsDB`) was **not** carried over. The NeoForge port
  uses the built-in HTTP web API (`webapi/`) to expose war statistics instead — same goal, cleaner
  transport, no database driver dependency.

## [Unreleased]

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