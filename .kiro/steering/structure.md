# Project Structure

## Root Directory
- `build.gradle` - Main build configuration with dependencies and Forge setup
- `gradle.properties` - Mod metadata and version information
- `settings.gradle` - Gradle project settings
- `.kiro/` - Kiro AI assistant configuration and specs

## Source Organization

### Main Source (`src/main/java/net/machiavelli/minecolonytax/`)

#### Core Classes
- `MineColonyTax.java` - Main mod class with @Mod annotation and initialization
- `TaxConfig.java` - Forge configuration specification and settings
- `TaxManager.java` - Core tax collection and management logic
- `WarSystem.java` - War declaration and management system
- `CrashLogger.java` - Error logging and crash reporting

#### Package Structure

**`commands/`** - All command implementations
- `WntCommands.java` - Unified `/wnt` command dispatcher
- `WarCommands.java` - War-related commands
- `ClaimTaxCommand.java` - Tax collection commands
- `EntityRaidCommands.java` - Entity raid management
- `GeneralPermissionsCommands.java` - Colony permission commands

**`capability/`** - Minecraft capability system
- `PlayerWarDataCapability.java` - Player data attachment for war stats

**`data/`** - Data models and persistence
- `WarData.java` - War state data structure
- `RaidData.java` - Raid information storage
- `PlayerWarData.java` - Individual player statistics
- `HistoryManager.java` - War/raid history tracking
- `MCTLanguageProvider.java` - Localization data generation

**`event/`** - Forge event handlers
- `WarEventHandler.java` - War-related game events
- `ColonyEventListener.java` - Colony system integration
- `RaidLoginNotifier.java` - Player login notifications

**`raid/`** - Raid system implementation
- `RaidManager.java` - Core raid mechanics
- `EntityRaidManager.java` - Entity-triggered raids
- `ActiveRaidData.java` - Active raid state tracking

**`peace/`** - Diplomatic system
- `PeaceProposalManager.java` - Peace negotiation handling
- `PeaceProposal.java` - Peace proposal data structure

**`permissions/`** - Colony permission management
- `GeneralColonyPermissionsManager.java` - Universal colony permissions

**`pvp/`** - PvP arena system
- `PvPManager.java` - PvP battle coordination
- `PvPArenaCommand.java` - Arena management commands
- `model/` - PvP data models
- `persistence/` - PvP data storage

**`util/`** - Utility classes
- `ItemUtils.java` - Item handling utilities
- `TranslationUtil.java` - Localization helpers

**`vassalization/`** - Colony vassalization system
- `VassalManager.java` - Vassal relationship management

### Resources (`src/main/resources/`)
- `META-INF/mods.toml` - Mod metadata for Forge
- `assets/minecolonytax/` - Client-side assets (textures, models, etc.)
- `data/minecolonytax/` - Data generation (recipes, loot tables, etc.)
- `pack.mcmeta` - Resource pack metadata

### Generated Resources (`src/generated/resources/`)
- Auto-generated data from `runData` gradle task

## Runtime Directories
- `run/` - Primary development client/server environment
- `run2/` - Secondary client for multiplayer testing
- `run-data/` - Data generation output directory

## Configuration Storage
- `config/warntax/` - All mod configuration and data files
  - `minecolonytax.toml` - Main configuration file
  - `colonyTaxData.json` - Tax collection data
  - `colony_history.json` - War and raid history
  - `pvp_arena_data.json` - PvP arena configurations
  - `vassals.json` - Vassalization relationships

## Architecture Patterns

### Command System
- Unified `/wnt` prefix for all commands
- Hierarchical command structure with subcommands
- Permission-based access control
- Context-aware suggestions and tab completion

### Event-Driven Architecture
- Forge event bus for game integration
- Custom events for mod-specific actions
- Listener pattern for system coordination

### Configuration Management
- ForgeConfigSpec for type-safe configuration
- Automatic migration from old config locations
- Runtime config reloading support

### Data Persistence
- JSON-based file storage for complex data
- Minecraft capabilities for player-attached data
- Automatic backup and recovery mechanisms