# Design Document

## Overview

The EntityRaid system is a sophisticated threat detection and response mechanism that monitors for hostile entities near colonies and triggers defensive measures. The current implementation has a solid architectural foundation but suffers from two critical issues: raids not triggering when they should, and glow effects being visible to all players instead of just the colony owner.

This design addresses these issues through enhanced debugging capabilities, improved entity filtering logic, and a proper per-player glow effect system.

## Architecture

### Current System Components

1. **EntityRaidManager**: Central coordinator for entity detection and raid management
2. **ActiveEntityRaid**: Data structure tracking ongoing raids
3. **RaidState Enum**: Manages raid phases (DETECTED, RAIDING, LEAVING)
4. **Entity Filtering Pipeline**: Multi-stage filtering for recruit entities
5. **Glow Effect System**: Visual feedback for threatening entities
6. **Bossbar Management**: Real-time raid status display

### Key Integration Points

- **MineColonies API**: Colony boundaries, ownership, permissions
- **Recruits Mod**: Entity ownership, diplomacy system via reflection
- **Minecraft Teams**: Alliance detection fallback
- **TaxManager**: Economic impact during raids
- **Configuration System**: Customizable thresholds and behaviors

## Components and Interfaces

### Enhanced Debug Logging System

**Purpose**: Provide comprehensive visibility into the entity detection and filtering process

**Key Methods**:
- `logEntityDetection(IColony colony, List<Entity> entities)`: Log detected entities per colony
- `logFilterStep(Entity entity, String filterName, boolean passed, String reason)`: Track each filter stage
- `logAllianceCheck(Entity entity, IColony colony, boolean isAllied)`: Debug diplomacy system
- `logPrerequisiteCheck(IColony colony, String requirement, boolean met)`: Track requirement validation

**Implementation Strategy**:
```java
// Add debug flag to control verbosity
private static final boolean DEBUG_ENTITY_RAIDS = TaxConfig.isEntityRaidDebugEnabled();

// Structured logging with context
private static void logEntityRaidStep(String phase, IColony colony, Entity entity, String details) {
    if (DEBUG_ENTITY_RAIDS) {
        LOGGER.info("[EntityRaid-{}] Colony: {} | Entity: {} | Details: {}", 
                phase, colony.getName(), entity.getType().getDescriptionId(), details);
    }
}
```

### Improved Entity Filtering Pipeline

**Current Issues Identified**:
1. `isRecentlyRecruited()` uses `entity.tickCount` which may not accurately reflect recruitment time
2. Alliance detection relies on reflection that may fail silently
3. Entity type detection may not cover all recruit variants
4. Grace period logic might be too restrictive

**Enhanced Filtering Logic**:

```java
private static boolean shouldRecruitTriggerRaid(Entity entity, IColony colony, UUID colonyOwnerUUID, Level level) {
    String entityId = entity.getType().getDescriptionId();
    
    // Step 1: Ownership check with enhanced logging
    if (isOwnedRecruit(entity)) {
        UUID recruitOwner = getRecruitOwnerUUID(entity);
        boolean isOwnerMatch = recruitOwner != null && recruitOwner.equals(colonyOwnerUUID);
        logFilterStep(entity, "OWNERSHIP", !isOwnerMatch, 
                isOwnerMatch ? "Owned by colony owner" : "Owned by different player");
        if (isOwnerMatch) return false;
    }
    
    // Step 2: Enhanced grace period check
    boolean isRecent = isRecentlyRecruited(entity);
    logFilterStep(entity, "GRACE_PERIOD", !isRecent, 
            isRecent ? "Within 10s grace period" : "Outside grace period");
    if (isRecent) return false;
    
    // Step 3: Alliance check with fallback mechanisms
    boolean isAllied = isRecruitAlliedToColony(entity, colony, level);
    logFilterStep(entity, "ALLIANCE", !isAllied, 
            isAllied ? "Allied through diplomacy" : "Not allied");
    if (isAllied) return false;
    
    // Step 4: Boundary check
    boolean insideBoundary = colony.isCoordInColony(level, entity.blockPosition());
    logFilterStep(entity, "BOUNDARY", !insideBoundary, 
            insideBoundary ? "Inside colony boundaries" : "Outside colony boundaries");
    if (insideBoundary) return false;
    
    // Entity passed all filters - can trigger raid
    logFilterStep(entity, "FINAL", true, "Can trigger EntityRaid");
    return true;
}
```

### Per-Player Glow Effect System

**Current Problem**: The existing implementation applies `MobEffects.GLOWING` universally and attempts to control visibility through packets, but this approach is fundamentally flawed.

**New Design**: Custom client-side rendering approach

**Components**:

1. **Custom Network Packet**: `EntityGlowPacket`
   - Sent only to colony owner
   - Contains entity UUIDs and glow state
   - Handled client-side to apply visual effects

2. **Client-Side Renderer**: `EntityRaidRenderer`
   - Intercepts entity rendering for specified entities
   - Applies glow effect overlay only for the receiving player
   - Manages glow effect lifecycle

3. **Server-Side Manager**: `GlowEffectManager`
   - Tracks which entities should glow for which players
   - Sends packets when entities enter/leave glow state
   - Handles cleanup when raids end or players disconnect

**Implementation Strategy**:
```java
// Server-side: Send custom packet instead of applying MobEffect
private static void applyGlowEffectToEntities(IColony colony, Set<UUID> entityUUIDs) {
    UUID ownerUUID = colony.getPermissions().getOwner();
    ServerPlayer owner = getOnlineOwner(colony);
    if (owner == null) return;
    
    // Send custom packet to owner only
    EntityGlowPacket packet = new EntityGlowPacket(entityUUIDs, true);
    PacketHandler.sendToPlayer(packet, owner);
    
    // Track glow state for cleanup
    GlowEffectManager.addGlowingEntities(colony.getID(), entityUUIDs, ownerUUID);
}

// Client-side: Handle packet and apply visual effects
@OnlyIn(Dist.CLIENT)
public static void handleEntityGlowPacket(EntityGlowPacket packet) {
    for (UUID entityUUID : packet.getEntityUUIDs()) {
        if (packet.shouldGlow()) {
            EntityRaidRenderer.addGlowingEntity(entityUUID);
        } else {
            EntityRaidRenderer.removeGlowingEntity(entityUUID);
        }
    }
}
```

### Enhanced Recruit Detection System

**Current Issues**:
- Entity type checking may miss some recruit variants
- Reflection-based method calls lack proper error handling
- Grace period calculation is unreliable

**Improved Implementation**:

```java
private static boolean isRecruitEntity(Entity entity) {
    String entityId = entity.getType().getDescriptionId();
    
    // Primary check: entity ID contains "recruits"
    if (entityId != null && entityId.contains("recruits")) {
        logEntityRaidStep("DETECTION", null, entity, "Identified as recruit by entity ID");
        return true;
    }
    
    // Secondary check: class name analysis
    String className = entity.getClass().getSimpleName().toLowerCase();
    if (className.contains("recruit")) {
        logEntityRaidStep("DETECTION", null, entity, "Identified as recruit by class name");
        return true;
    }
    
    // Tertiary check: interface/superclass analysis
    Class<?> entityClass = entity.getClass();
    while (entityClass != null) {
        if (entityClass.getSimpleName().toLowerCase().contains("recruit")) {
            logEntityRaidStep("DETECTION", null, entity, "Identified as recruit by inheritance");
            return true;
        }
        entityClass = entityClass.getSuperclass();
    }
    
    return false;
}

private static boolean isRecentlyRecruited(Entity entity) {
    try {
        // Method 1: Check entity age (more reliable than tickCount)
        long worldTime = entity.level().getGameTime();
        long entitySpawnTime = entity.tickCount; // This is actually ticks since spawn
        long entityAgeMs = entitySpawnTime * 50L; // Convert to milliseconds
        
        if (entityAgeMs < 10000) { // 10 seconds
            logEntityRaidStep("GRACE_PERIOD", null, entity, 
                    String.format("Recently spawned: %dms ago", entityAgeMs));
            return true;
        }
        
        // Method 2: Try to access recruit-specific recruitment timestamp
        if (isRecruitEntity(entity)) {
            UUID recruitOwner = getRecruitOwnerUUID(entity);
            if (recruitOwner != null) {
                // If we can get owner, check if recruitment was recent
                // This would require tracking recruitment times separately
                Long recruitmentTime = RecruitmentTracker.getRecruitmentTime(entity.getUUID());
                if (recruitmentTime != null) {
                    long timeSinceRecruitment = System.currentTimeMillis() - recruitmentTime;
                    if (timeSinceRecruitment < 10000) {
                        logEntityRaidStep("GRACE_PERIOD", null, entity, 
                                String.format("Recently recruited: %dms ago", timeSinceRecruitment));
                        return true;
                    }
                }
            }
        }
        
        return false;
    } catch (Exception e) {
        LOGGER.warn("Error checking recruitment time for entity {}: {}", 
                entity.getType().getDescriptionId(), e.getMessage());
        return false; // If we can't determine, assume not recent
    }
}
```

## Data Models

### Enhanced ActiveEntityRaid

```java
public static class ActiveEntityRaid {
    private final int colonyId;
    private final long startTime;
    private final Set<UUID> triggeringEntities;
    private final Map<UUID, EntityRaidData> entityData; // NEW: Track per-entity data
    private final Timer boundaryTimer;
    private RaidState currentState; // NEW: Track current state
    private boolean isActive;
    private boolean hasLeftBoundary;
    private long boundaryLeaveTime;
    private int lastKnownEntityCount; // NEW: For bossbar updates
    
    // NEW: Per-entity tracking
    public static class EntityRaidData {
        private final UUID entityUUID;
        private final String entityType;
        private final long detectionTime;
        private boolean isAlive;
        private boolean isInBoundary;
        private BlockPos lastKnownPosition;
        
        // Constructor and getters...
    }
}
```

### GlowEffectManager State

```java
public class GlowEffectManager {
    // Map of colony ID to glowing entities for that colony
    private static final Map<Integer, Set<UUID>> colonyGlowingEntities = new ConcurrentHashMap<>();
    
    // Map of player UUID to entities they should see glowing
    private static final Map<UUID, Set<UUID>> playerGlowingEntities = new ConcurrentHashMap<>();
    
    // Cleanup tracking
    private static final Map<UUID, Long> entityGlowStartTime = new ConcurrentHashMap<>();
}
```

### Debug Configuration

```java
// Add to TaxConfig
public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_RAID_DEBUG = BUILDER
    .comment("Enable detailed debug logging for EntityRaid system")
    .define("enableEntityRaidDebug", false);

public static final ForgeConfigSpec.IntValue ENTITY_RAID_DEBUG_LEVEL = BUILDER
    .comment("Debug level: 1=Basic, 2=Detailed, 3=Verbose")
    .defineInRange("entityRaidDebugLevel", 1, 1, 3);
```

## Error Handling

### Reflection Safety

```java
private static class ReflectionCache {
    private static Method isAllyMethod;
    private static Method canHarmTeamMethod;
    private static Method getOwnerUUIDMethod;
    private static Method isOwnedMethod;
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) return;
        
        try {
            Class<?> recruitEventsClass = Class.forName("com.talhanation.recruits.RecruitEvents");
            isAllyMethod = recruitEventsClass.getMethod("isAlly", Team.class, Team.class);
            canHarmTeamMethod = recruitEventsClass.getMethod("canHarmTeam", LivingEntity.class, LivingEntity.class);
            
            // Cache recruit entity methods
            // Note: This assumes a common base class - may need adjustment
            Class<?> recruitEntityClass = Class.forName("com.talhanation.recruits.entities.AbstractRecruitEntity");
            getOwnerUUIDMethod = recruitEntityClass.getMethod("getOwnerUUID");
            isOwnedMethod = recruitEntityClass.getMethod("isOwned");
            
            initialized = true;
            LOGGER.info("EntityRaid reflection cache initialized successfully");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize EntityRaid reflection cache: {}", e.getMessage());
            initialized = true; // Prevent repeated attempts
        }
    }
}
```

### Graceful Degradation

```java
private static boolean isRecruitAlliedToColony(Entity entity, IColony colony, Level level) {
    try {
        ReflectionCache.initialize();
        
        // Primary method: Use Recruits mod alliance system
        if (ReflectionCache.isAllyMethod != null) {
            // ... existing logic
        }
        
        // Fallback 1: Team-based alliance
        Team entityTeam = entity.getTeam();
        Team ownerTeam = getColonyOwnerTeam(colony);
        if (entityTeam != null && ownerTeam != null && entityTeam.equals(ownerTeam)) {
            logEntityRaidStep("ALLIANCE", colony, entity, "Allied through team system");
            return true;
        }
        
        // Fallback 2: Distance-based heuristic for owned entities
        UUID entityOwner = getRecruitOwnerUUID(entity);
        if (entityOwner != null) {
            // Check if owner is a colony member
            if (colony.getPermissions().hasPermission(entityOwner, Action.ACCESS_HUTS)) {
                logEntityRaidStep("ALLIANCE", colony, entity, "Owner is colony member");
                return true;
            }
        }
        
        return false;
    } catch (Exception e) {
        LOGGER.warn("Error in alliance detection for entity {} at colony {}: {}", 
                entity.getType().getDescriptionId(), colony.getName(), e.getMessage());
        return false; // Default to not allied if we can't determine
    }
}
```

## Testing Strategy

### Unit Testing Approach

1. **Mock Entity Creation**: Create test entities that simulate recruit behavior
2. **Colony Simulation**: Mock colony boundaries and ownership
3. **Reflection Testing**: Test all reflection-based calls with mock objects
4. **State Transition Testing**: Verify raid state changes work correctly

### Integration Testing

1. **End-to-End Raid Flow**: Spawn test recruits and verify raid triggering
2. **Alliance System Testing**: Test with actual Recruits mod entities
3. **Performance Testing**: Verify system handles multiple colonies efficiently
4. **Edge Case Testing**: Test boundary conditions and error scenarios

### Debug Testing Tools

```java
// Add admin command for testing
@Command("entityraid")
public class EntityRaidCommand {
    @SubCommand("debug")
    public void debugColony(@Arg("colonyId") int colonyId) {
        // Force debug logging for specific colony
        // Show current entity detection state
        // Display filter results for nearby entities
    }
    
    @SubCommand("simulate")
    public void simulateRaid(@Arg("colonyId") int colonyId, @Arg("entityCount") int count) {
        // Spawn test entities to trigger raid
        // Useful for testing without actual recruit spawning
    }
    
    @SubCommand("glow")
    public void testGlow(@Arg("entityId") String entityId) {
        // Test glow effect on specific entity
        // Verify per-player visibility
    }
}
```

This design provides a comprehensive solution to the EntityRaid issues while maintaining backward compatibility and adding robust debugging capabilities. The modular approach allows for incremental implementation and testing of each component.