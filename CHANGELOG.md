# Changelog

All notable changes to the MineColonyTax mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - 2025-01-30

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