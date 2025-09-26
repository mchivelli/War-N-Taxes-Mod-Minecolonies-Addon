# Building Requirements Feature

## Overview
Extended the building requirements system to apply to raids and wars, allowing server administrators to configure specific building and level requirements for initiating combat actions.

## Configuration Options

### Raid Building Requirements
```properties
# Enable/disable building requirements for raids
EnableRaidBuildingRequirements=true

# Building requirements for raids (default: townhall level 1 + 3 guard towers level 1)
RaidBuildingRequirements="townhall:1:1,guardtower:1:3"
```

### War Building Requirements
```properties
# Enable/disable building requirements for wars
EnableWarBuildingRequirements=true

# Building requirements for wars (default: townhall level 2 + 3 guard towers + 1 builders hut + 1 residential building)
WarBuildingRequirements="townhall:2:1,guardtower:1:3,buildershut:1:1,house:1:1"
```

## Format
Building requirements use the format: `building:level:amount`

- **building**: Building type name (e.g., townhall, guardtower, buildershut, house)
- **level**: Minimum building level required
- **amount**: Number of buildings of this type and level required

Multiple requirements are separated by commas.

## Supported Building Types
- `townhall` - Town Hall
- `guardtower` - Guard Towers
- `buildershut`/`buildershop` - Builder's Hut
- `house`/`residential` - Residential Buildings
- `barracks` - Barracks
- `archery` - Archery Range
- `combatacademy` - Combat Academy
- `mine` - Mine
- `farm` - Farm
- `warehouse` - Warehouse
- `deliveryman` - Delivery Point

## Implementation Details

### BuildingRequirementsManager
- Central manager for all building requirement checks
- Supports both legacy format (building:level) and new format (building:level:amount)
- Handles building type aliases and variations
- Provides detailed error messages

### Integration Points
1. **Raid System**: Checks requirements in `RaidManager.handleRaid()`
2. **War System**: Checks requirements in `WarSystem.processWageWarRequestWithExtortion()`
3. **Colony Claiming**: Uses same system for abandoned colony claiming
4. **Help System**: Displays current requirements in command help

### Error Messages
- Clear, specific error messages indicating missing requirements
- Shows current vs. required amounts
- Integrates with existing command feedback systems

## Examples

### Basic Setup
```properties
# Simple raid requirements: 1 townhall + 2 guard towers
RaidBuildingRequirements="townhall:1:1,guardtower:1:2"

# Advanced war requirements: Multiple building types
WarBuildingRequirements="townhall:2:1,guardtower:2:3,barracks:1:1,house:1:5"
```

### Disable Requirements
```properties
# Disable by setting to empty string
RaidBuildingRequirements=""
WarBuildingRequirements=""

# Or disable entirely
EnableRaidBuildingRequirements=false
EnableWarBuildingRequirements=false
```

## Player Experience

### Command Help
Players can see current building requirements using:
- `/wnt help raid` - Shows raid requirements
- `/wnt help wagewar` - Shows war requirements

### Error Feedback
When requirements aren't met, players receive clear messages:
```
Cannot initiate raid: Missing building requirements: 2x guardtower level 1+ (current: 1), 1x buildershut level 1+ (current: 0)
```

### Help Integration
Requirements are automatically displayed in command help text when enabled.










