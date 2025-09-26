# Configuration Conflict Resolution

## Overview
This document explains how the mod handles conflicts between legacy guard count requirements and new building requirements systems to prevent confusion and conflicting requirements.

## Identified Conflicts

### 1. Guard Count vs Building Requirements
**Legacy System:**
- `MinGuardsToRaid` - Requires X actual guard NPCs
- `MinGuardsToWageWar` - Requires X actual guard NPCs

**New System:**
- `RaidBuildingRequirements` - Can include "guardtower:1:3" (guard tower buildings)
- `WarBuildingRequirements` - Can include "guardtower:1:3" (guard tower buildings)

**Conflict:** Players could be required to have both guard NPCs AND guard tower buildings, which is confusing since these are different things.

## Resolution Strategy: Priority System

### Priority Rules
1. **Building Requirements ALWAYS take priority** when enabled
2. **Legacy guard count requirements are SKIPPED** when building requirements are active
3. **Clear configuration comments** explain which system is in use
4. **Help commands show only the active requirements**

### Implementation

#### Raids
```java
if (TaxConfig.isRaidBuildingRequirementsEnabled()) {
    // Use new building requirements system
    checkBuildingRequirements(colony);
} else {
    // Fall back to legacy guard count system
    checkGuardCount(colony);
}
```

#### Wars
```java
if (TaxConfig.isWarBuildingRequirementsEnabled()) {
    // Use new building requirements system
    checkBuildingRequirements(colony);
} else {
    // Fall back to legacy guard count system
    checkGuardCount(colony);
}
```

## Configuration Priority

### Active Systems Based on Settings

| Raid Building Requirements | War Building Requirements | Behavior |
|---------------------------|--------------------------|----------|
| **Enabled** | **Enabled** | Both use building requirements, legacy guard counts ignored |
| **Enabled** | Disabled | Raids use building requirements, wars use legacy guard count |
| Disabled | **Enabled** | Raids use legacy guard count, wars use building requirements |
| Disabled | Disabled | Both use legacy guard count system |

### Configuration Comments

All affected config options now include clear priority information:

```toml
# Legacy guard count (only used when building requirements disabled)
MinGuardsToRaid = 3
# NOTE: This is only used when 'EnableRaidBuildingRequirements' is disabled

# Building requirements (takes priority when enabled)
EnableRaidBuildingRequirements = true
# NOTE: When this is enabled, it replaces the 'MinGuardsToRaid' requirement entirely
```

## Player Experience

### Help Commands Show Active Requirements

**When Building Requirements Enabled:**
```
/wnt help raid
Requirements:
- Building requirements: 1x townhall level 1+, 3x guardtower level 1+
```

**When Building Requirements Disabled:**
```
/wnt help raid
Requirements:
- Your colony must have at least 3 guards
```

### Error Messages Match Active System

**Building Requirements Error:**
```
Cannot initiate raid: Missing building requirements: 2x guardtower level 1+ (current: 1)
```

**Legacy Guard Count Error:**
```
Your colony must have at least 3 guards to initiate a raid. (Found: 1)
```

## Technical Implementation

### Files Modified
1. **TaxConfig.java** - Updated config comments with priority explanations
2. **WarSystem.java** - Implemented priority system for war declarations
3. **RaidManager.java** - Implemented priority system for raid initiation
4. **WntCommands.java** - Updated help system to show active requirements
5. **BuildingRequirementsManager.java** - Added guard-related building detection

### Backward Compatibility
- ✅ **Existing servers** continue working with legacy guard count system
- ✅ **Config migration** not required - old configs remain functional
- ✅ **Gradual adoption** - can enable building requirements per action type
- ✅ **Easy rollback** - disable building requirements to return to legacy system

## Migration Guide

### For Server Administrators

#### Option 1: Keep Legacy System
```toml
EnableRaidBuildingRequirements = false
EnableWarBuildingRequirements = false
# Uses MinGuardsToRaid and MinGuardsToWageWar
```

#### Option 2: Adopt Building Requirements
```toml
EnableRaidBuildingRequirements = true
EnableWarBuildingRequirements = true
# MinGuardsToRaid and MinGuardsToWageWar are ignored
```

#### Option 3: Mixed System (Not Recommended)
```toml
EnableRaidBuildingRequirements = true  # Raids use building requirements
EnableWarBuildingRequirements = false # Wars use guard count
# Can be confusing for players
```

### Recommended Settings

For new servers or servers wanting strategic depth:
```toml
# Enable building requirements for more complex strategy
EnableRaidBuildingRequirements = true
EnableWarBuildingRequirements = true

# Customize requirements based on your server's progression
RaidBuildingRequirements = "townhall:1:1,guardtower:1:2"
WarBuildingRequirements = "townhall:2:1,guardtower:2:3,buildershut:1:1"
```

## Key Benefits

1. **No Conflicts** - Only one requirement system active at a time
2. **Clear Communication** - Players see exactly what's required
3. **Flexible Configuration** - Choose the system that fits your server
4. **Backward Compatible** - Existing setups continue working
5. **Strategic Depth** - Building requirements provide more interesting progression

## Future Considerations

- **Guard Count Integration**: Future versions could incorporate actual guard counts into building requirements if needed
- **Hybrid Systems**: Could potentially combine both systems with more complex logic if there's demand
- **UI Improvements**: Could add in-game UI to show requirements more clearly










