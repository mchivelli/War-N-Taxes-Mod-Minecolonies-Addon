# Changelog

All notable changes to the MineColonyTax mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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