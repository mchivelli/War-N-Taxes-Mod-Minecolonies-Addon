# Technology Stack

## Build System
- **Gradle** with Forge MDK setup
- **MinecraftForge Gradle Plugin** (6.0-6.2 range)
- **Parchment mappings** for parameter names and javadocs

## Core Technologies
- **Java 17** (required for Minecraft 1.20.1+)
- **Minecraft Forge 47.3.10** for 1.20.1
- **Parchment mappings** (2023.09.03-1.20.1)

## Key Dependencies
- **MineColonies API** - Core colony management integration
- **SDMShop** - Optional currency/economy integration
- **FTB Teams** - Team-based mechanics
- **Recruits mod** - Additional combat features
- **JEI** - Just Enough Items integration (runtime)

## Configuration
- **Forge Config Spec** for TOML-based configuration
- **NightConfig** for configuration file handling
- Custom config migration system for version upgrades

## Data Persistence
- **JSON files** for data storage (tax data, history, PvP arenas, vassals)
- **Minecraft Capabilities** for player data attachment
- **File-based storage** in `config/warntax/` directory

## Common Commands

### Development
```bash
# Build the mod
./gradlew build

# Run client for testing
./gradlew runClient

# Run server for testing  
./gradlew runServer

# Run second client instance
./gradlew runClient2

# Generate data (recipes, loot tables, etc.)
./gradlew runData

# Clean build artifacts
./gradlew clean
```

### Testing
```bash
# Run game tests
./gradlew runGameTestServer

# Compile and check syntax
./gradlew compileJava
```

## Code Style Guidelines
- Use **ForgeConfigSpec** for all configuration values
- Implement proper **event handling** with @SubscribeEvent
- Follow **Minecraft/Forge naming conventions**
- Use **capability system** for persistent player data
- Implement **command suggestions** for better UX
- Always include **permission checks** for admin commands