package net.machiavelli.minecolonytax.raid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Team;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Enhanced caches reflection-based method calls for Recruits mod integration.
 * This improves performance and provides centralized error handling for reflection operations.
 * Includes multiple fallback mechanisms and comprehensive error handling.
 */
public class ReflectionCache {
    
    private static final Logger LOGGER = LogManager.getLogger(ReflectionCache.class);
    
    // Cached methods for Recruits mod integration
    private static Method isAllyMethod;
    private static Method canHarmTeamMethod;
    private static Method getOwnerUUIDMethod;
    private static Method isOwnedMethod;
    private static Method getCreationTimeMethod;
    
    // Additional cached methods for enhanced alliance detection
    private static Method getTeamMethod;
    private static Method isEnemyMethod;
    private static Method canAttackMethod;
    private static Method getOwnerMethod;
    private static Method hasOwnerMethod;
    
    // Dynamic method cache for fallback scenarios
    private static final Map<String, Method> dynamicMethodCache = new ConcurrentHashMap<>();
    
    // Cache status flags
    private static boolean initialized = false;
    private static boolean recruitsModAvailable = false;
    private static boolean recruitsEventsAvailable = false;
    private static boolean abstractRecruitEntityAvailable = false;
    
    // Alternative class names to try for different Recruits mod versions
    private static final String[] RECRUIT_EVENT_CLASS_NAMES = {
        "com.talhanation.recruits.RecruitEvents",
        "com.talhanation.recruits.events.RecruitEvents",
        "com.talhanation.recruits.util.RecruitEvents"
    };
    
    private static final String[] RECRUIT_ENTITY_CLASS_NAMES = {
        "com.talhanation.recruits.entities.AbstractRecruitEntity",
        "com.talhanation.recruits.entities.RecruitEntity",
        "com.talhanation.recruits.entities.BaseRecruitEntity",
        "com.talhanation.recruits.entity.AbstractRecruitEntity"
    };
    
    /**
     * Enhanced initialization with multiple fallback mechanisms
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        EntityRaidDebugLogger.logReflectionCall("initialize", "ReflectionCache", true, "Starting enhanced reflection cache initialization");
        
        // Try to initialize RecruitEvents class with multiple fallback class names
        initializeRecruitEventsClass();
        
        // Try to initialize recruit entity classes with multiple fallback class names
        initializeRecruitEntityClass();
        
        // Set overall availability flags
        recruitsModAvailable = recruitsEventsAvailable || abstractRecruitEntityAvailable;
        
        long duration = System.currentTimeMillis() - startTime;
        if (recruitsModAvailable) {
            LOGGER.info("ReflectionCache initialized successfully in {}ms - Recruits mod integration available (Events: {}, Entities: {})", 
                    duration, recruitsEventsAvailable, abstractRecruitEntityAvailable);
        } else {
            LOGGER.info("ReflectionCache initialized in {}ms - Recruits mod not available, using fallback methods", duration);
        }
        
        initialized = true;
    }
    
    /**
     * Initialize RecruitEvents class with multiple fallback attempts
     */
    private static void initializeRecruitEventsClass() {
        Class<?> recruitEventsClass = null;
        
        // Try different class names for different Recruits mod versions
        for (String className : RECRUIT_EVENT_CLASS_NAMES) {
            try {
                recruitEventsClass = Class.forName(className);
                EntityRaidDebugLogger.logReflectionCall("Class.forName", className, true, "RecruitEvents class found");
                break;
            } catch (ClassNotFoundException e) {
                EntityRaidDebugLogger.logReflectionCall("Class.forName", className, false, "Class not found");
            }
        }
        
        if (recruitEventsClass == null) {
            EntityRaidDebugLogger.logReflectionCall("RecruitEvents", "all variants", false, "No RecruitEvents class found");
            return;
        }
        
        recruitsEventsAvailable = true;
        
        // Cache alliance checking methods with multiple method name variants
        cacheAllianceMethod(recruitEventsClass, "isAlly", new Class[]{Team.class, Team.class});
        cacheAllianceMethod(recruitEventsClass, "areAllies", new Class[]{Team.class, Team.class});
        cacheAllianceMethod(recruitEventsClass, "isTeamAlly", new Class[]{Team.class, Team.class});
        
        // Cache harm checking methods with multiple variants
        cacheHarmMethod(recruitEventsClass, "canHarmTeam", new Class[]{LivingEntity.class, LivingEntity.class});
        cacheHarmMethod(recruitEventsClass, "canAttack", new Class[]{LivingEntity.class, LivingEntity.class});
        cacheHarmMethod(recruitEventsClass, "canHarm", new Class[]{LivingEntity.class, LivingEntity.class});
        
        // Cache additional methods for enhanced detection
        cacheAdditionalMethod(recruitEventsClass, "isEnemy", new Class[]{Team.class, Team.class});
        cacheAdditionalMethod(recruitEventsClass, "getTeam", new Class[]{LivingEntity.class});
    }
    
    /**
     * Initialize recruit entity classes with multiple fallback attempts
     */
    private static void initializeRecruitEntityClass() {
        Class<?> recruitEntityClass = null;
        
        // Try different class names for different Recruits mod versions
        for (String className : RECRUIT_ENTITY_CLASS_NAMES) {
            try {
                recruitEntityClass = Class.forName(className);
                EntityRaidDebugLogger.logReflectionCall("Class.forName", className, true, "Recruit entity class found");
                break;
            } catch (ClassNotFoundException e) {
                EntityRaidDebugLogger.logReflectionCall("Class.forName", className, false, "Class not found");
            }
        }
        
        if (recruitEntityClass == null) {
            EntityRaidDebugLogger.logReflectionCall("RecruitEntity", "all variants", false, "No recruit entity class found");
            return;
        }
        
        abstractRecruitEntityAvailable = true;
        
        // Cache entity methods with multiple method name variants
        cacheEntityMethod(recruitEntityClass, "getOwnerUUID", new Class[]{});
        cacheEntityMethod(recruitEntityClass, "getOwner", new Class[]{});
        cacheEntityMethod(recruitEntityClass, "getOwnerID", new Class[]{});
        
        cacheEntityMethod(recruitEntityClass, "isOwned", new Class[]{});
        cacheEntityMethod(recruitEntityClass, "hasOwner", new Class[]{});
        
        cacheEntityMethod(recruitEntityClass, "getCreationTime", new Class[]{});
        cacheEntityMethod(recruitEntityClass, "getSpawnTime", new Class[]{});
        cacheEntityMethod(recruitEntityClass, "getBirthTime", new Class[]{});
        
        cacheEntityMethod(recruitEntityClass, "getTeam", new Class[]{});
    }
    
    /**
     * Cache alliance-related methods with error handling
     */
    private static void cacheAllianceMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            if (isAllyMethod == null) { // Use the first successful method as primary
                isAllyMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary alliance method cached");
            } else {
                dynamicMethodCache.put("alliance_" + methodName, method);
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Fallback alliance method cached");
            }
        } catch (NoSuchMethodException e) {
            EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), false, "Method not found");
        }
    }
    
    /**
     * Cache harm-related methods with error handling
     */
    private static void cacheHarmMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            if (canHarmTeamMethod == null) { // Use the first successful method as primary
                canHarmTeamMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary harm method cached");
            } else {
                dynamicMethodCache.put("harm_" + methodName, method);
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Fallback harm method cached");
            }
        } catch (NoSuchMethodException e) {
            EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), false, "Method not found");
        }
    }
    
    /**
     * Cache additional methods for enhanced detection
     */
    private static void cacheAdditionalMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            dynamicMethodCache.put("additional_" + methodName, method);
            EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Additional method cached");
        } catch (NoSuchMethodException e) {
            EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), false, "Method not found");
        }
    }
    
    /**
     * Cache entity-related methods with error handling
     */
    private static void cacheEntityMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            
            // Assign to appropriate primary method if not already set
            if (methodName.contains("Owner") && methodName.contains("UUID") && getOwnerUUIDMethod == null) {
                getOwnerUUIDMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary owner UUID method cached");
            } else if (methodName.contains("Owner") && !methodName.contains("UUID") && getOwnerMethod == null) {
                getOwnerMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary owner method cached");
            } else if ((methodName.equals("isOwned") || methodName.equals("hasOwner")) && isOwnedMethod == null) {
                isOwnedMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary ownership check method cached");
            } else if (methodName.contains("Time") && getCreationTimeMethod == null) {
                getCreationTimeMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary time method cached");
            } else if (methodName.equals("getTeam") && getTeamMethod == null) {
                getTeamMethod = method;
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Primary team method cached");
            } else {
                // Store as fallback method
                dynamicMethodCache.put("entity_" + methodName, method);
                EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), true, "Fallback entity method cached");
            }
        } catch (NoSuchMethodException e) {
            EntityRaidDebugLogger.logReflectionCall(methodName, clazz.getSimpleName(), false, "Method not found");
        }
    }
    
    /**
     * Enhanced alliance checking with multiple fallback mechanisms
     */
    public static Boolean isAlly(Team team1, Team team2) {
        initialize();
        
        if (!recruitsEventsAvailable) {
            EntityRaidDebugLogger.logReflectionCall("isAlly", "ReflectionCache", false, "RecruitEvents not available");
            return null; // Indicates method not available
        }
        
        // Method 1: Try primary cached isAlly method
        if (isAllyMethod != null) {
            try {
                Object result = isAllyMethod.invoke(null, team1, team2);
                if (result instanceof Boolean) {
                    EntityRaidDebugLogger.logReflectionCall("isAlly", "primary", true, "Result: " + result);
                    return (Boolean) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.isAlly", "Error invoking primary isAlly method", e);
            }
        }
        
        // Method 2: Try fallback alliance methods
        for (Map.Entry<String, Method> entry : dynamicMethodCache.entrySet()) {
            if (entry.getKey().startsWith("alliance_")) {
                try {
                    Object result = entry.getValue().invoke(null, team1, team2);
                    if (result instanceof Boolean) {
                        EntityRaidDebugLogger.logReflectionCall("isAlly", entry.getKey(), true, "Fallback result: " + result);
                        return (Boolean) result;
                    }
                } catch (Exception e) {
                    EntityRaidDebugLogger.logError("ReflectionCache.isAlly", "Error invoking fallback method " + entry.getKey(), e);
                }
            }
        }
        
        // Method 3: Try inverted harm check as alliance indicator
        Boolean harmResult = tryInvertedHarmCheck(team1, team2);
        if (harmResult != null) {
            EntityRaidDebugLogger.logReflectionCall("isAlly", "inverted_harm", true, "Inverted harm result: " + harmResult);
            return harmResult;
        }
        
        EntityRaidDebugLogger.logReflectionCall("isAlly", "all_methods", false, "All alliance detection methods failed");
        return null;
    }
    
    /**
     * Try to determine alliance by checking if teams can harm each other (inverted logic)
     */
    private static Boolean tryInvertedHarmCheck(Team team1, Team team2) {
        // This would require creating dummy entities with the teams, which is complex
        // For now, we'll return null to indicate this method isn't available
        // This could be enhanced in the future if needed
        return null;
    }
    
    /**
     * Enhanced harm checking with multiple fallback mechanisms
     */
    public static Boolean canHarmTeam(LivingEntity entity1, LivingEntity entity2) {
        initialize();
        
        if (!recruitsEventsAvailable) {
            EntityRaidDebugLogger.logReflectionCall("canHarmTeam", "ReflectionCache", false, "RecruitEvents not available");
            return null; // Indicates method not available
        }
        
        // Method 1: Try primary cached canHarmTeam method
        if (canHarmTeamMethod != null) {
            try {
                Object result = canHarmTeamMethod.invoke(null, entity1, entity2);
                if (result instanceof Boolean) {
                    EntityRaidDebugLogger.logReflectionCall("canHarmTeam", "primary", true, "Result: " + result);
                    return (Boolean) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.canHarmTeam", "Error invoking primary canHarmTeam method", e);
            }
        }
        
        // Method 2: Try fallback harm methods
        for (Map.Entry<String, Method> entry : dynamicMethodCache.entrySet()) {
            if (entry.getKey().startsWith("harm_")) {
                try {
                    Object result = entry.getValue().invoke(null, entity1, entity2);
                    if (result instanceof Boolean) {
                        EntityRaidDebugLogger.logReflectionCall("canHarmTeam", entry.getKey(), true, "Fallback result: " + result);
                        return (Boolean) result;
                    }
                } catch (Exception e) {
                    EntityRaidDebugLogger.logError("ReflectionCache.canHarmTeam", "Error invoking fallback method " + entry.getKey(), e);
                }
            }
        }
        
        // Method 3: Try isEnemy method (inverted logic)
        Method isEnemyMethod = dynamicMethodCache.get("additional_isEnemy");
        if (isEnemyMethod != null) {
            try {
                Team team1 = entity1.getTeam();
                Team team2 = entity2.getTeam();
                if (team1 != null && team2 != null) {
                    Object result = isEnemyMethod.invoke(null, team1, team2);
                    if (result instanceof Boolean) {
                        Boolean isEnemy = (Boolean) result;
                        EntityRaidDebugLogger.logReflectionCall("canHarmTeam", "isEnemy_inverted", true, "Enemy result: " + isEnemy + ", can harm: " + isEnemy);
                        return isEnemy; // If they're enemies, they can harm each other
                    }
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.canHarmTeam", "Error invoking isEnemy method", e);
            }
        }
        
        EntityRaidDebugLogger.logReflectionCall("canHarmTeam", "all_methods", false, "All harm detection methods failed");
        return null;
    }
    
    /**
     * Enhanced owner UUID retrieval with multiple fallback mechanisms
     */
    public static UUID getOwnerUUID(Object entity) {
        initialize();
        
        if (!abstractRecruitEntityAvailable) {
            EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", "ReflectionCache", false, "Recruit entity classes not available");
            return null;
        }
        
        // Method 1: Try primary cached getOwnerUUID method
        if (getOwnerUUIDMethod != null) {
            try {
                Object result = getOwnerUUIDMethod.invoke(entity);
                if (result instanceof UUID) {
                    EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", "primary", true, "UUID retrieved successfully");
                    return (UUID) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.getOwnerUUID", "Error invoking primary getOwnerUUID method", e);
            }
        }
        
        // Method 2: Try fallback entity methods that return UUID
        for (Map.Entry<String, Method> entry : dynamicMethodCache.entrySet()) {
            if (entry.getKey().startsWith("entity_") && entry.getKey().contains("Owner") && entry.getKey().contains("UUID")) {
                try {
                    Object result = entry.getValue().invoke(entity);
                    if (result instanceof UUID) {
                        EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", entry.getKey(), true, "Fallback UUID retrieved");
                        return (UUID) result;
                    }
                } catch (Exception e) {
                    EntityRaidDebugLogger.logError("ReflectionCache.getOwnerUUID", "Error invoking fallback method " + entry.getKey(), e);
                }
            }
        }
        
        // Method 3: Try getOwner method and extract UUID if it returns a Player
        if (getOwnerMethod != null) {
            try {
                Object result = getOwnerMethod.invoke(entity);
                if (result != null) {
                    // Try to get UUID from player object
                    UUID uuid = extractUUIDFromOwner(result);
                    if (uuid != null) {
                        EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", "getOwner_extracted", true, "UUID extracted from owner object");
                        return uuid;
                    }
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.getOwnerUUID", "Error invoking getOwner method", e);
            }
        }
        
        // Method 4: Try dynamic method lookup as last resort
        try {
            Class<?> entityClass = entity.getClass();
            
            // Try common method names
            String[] methodNames = {"getOwnerUUID", "getOwner", "getOwnerID", "getPlayerUUID"};
            for (String methodName : methodNames) {
                try {
                    Method method = entityClass.getMethod(methodName);
                    Object result = method.invoke(entity);
                    
                    if (result instanceof UUID) {
                        EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", "dynamic_" + methodName, true, "Dynamic UUID retrieved");
                        return (UUID) result;
                    } else if (result != null) {
                        UUID uuid = extractUUIDFromOwner(result);
                        if (uuid != null) {
                            EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", "dynamic_" + methodName + "_extracted", true, "Dynamic UUID extracted");
                            return uuid;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next method name
                }
            }
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("ReflectionCache.getOwnerUUID", "Error with dynamic method lookup", e);
        }
        
        EntityRaidDebugLogger.logReflectionCall("getOwnerUUID", "all_methods", false, "All owner UUID retrieval methods failed");
        return null;
    }
    
    /**
     * Extract UUID from various owner object types
     */
    private static UUID extractUUIDFromOwner(Object owner) {
        if (owner == null) return null;
        
        try {
            // If it's already a UUID
            if (owner instanceof UUID) {
                return (UUID) owner;
            }
            
            // If it's a Player object
            if (owner.getClass().getSimpleName().contains("Player")) {
                Method getUUIDMethod = owner.getClass().getMethod("getUUID");
                Object result = getUUIDMethod.invoke(owner);
                if (result instanceof UUID) {
                    return (UUID) result;
                }
            }
            
            // If it's a string representation of UUID
            if (owner instanceof String) {
                try {
                    return UUID.fromString((String) owner);
                } catch (IllegalArgumentException ignored) {
                    // Not a valid UUID string
                }
            }
            
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("ReflectionCache.extractUUIDFromOwner", "Error extracting UUID from owner object", e);
        }
        
        return null;
    }
    
    /**
     * Enhanced ownership checking with multiple fallback mechanisms
     */
    public static Boolean isOwned(Object entity) {
        initialize();
        
        if (!abstractRecruitEntityAvailable) {
            EntityRaidDebugLogger.logReflectionCall("isOwned", "ReflectionCache", false, "Recruit entity classes not available");
            return null;
        }
        
        // Method 1: Try primary cached isOwned method
        if (isOwnedMethod != null) {
            try {
                Object result = isOwnedMethod.invoke(entity);
                if (result instanceof Boolean) {
                    EntityRaidDebugLogger.logReflectionCall("isOwned", "primary", true, "Result: " + result);
                    return (Boolean) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.isOwned", "Error invoking primary isOwned method", e);
            }
        }
        
        // Method 2: Try fallback ownership methods
        for (Map.Entry<String, Method> entry : dynamicMethodCache.entrySet()) {
            if (entry.getKey().startsWith("entity_") && (entry.getKey().contains("Owned") || entry.getKey().contains("Owner"))) {
                try {
                    Object result = entry.getValue().invoke(entity);
                    if (result instanceof Boolean) {
                        EntityRaidDebugLogger.logReflectionCall("isOwned", entry.getKey(), true, "Fallback result: " + result);
                        return (Boolean) result;
                    }
                } catch (Exception e) {
                    EntityRaidDebugLogger.logError("ReflectionCache.isOwned", "Error invoking fallback method " + entry.getKey(), e);
                }
            }
        }
        
        // Method 3: Infer ownership from getOwnerUUID result
        UUID ownerUUID = getOwnerUUID(entity);
        if (ownerUUID != null) {
            EntityRaidDebugLogger.logReflectionCall("isOwned", "inferred_from_uuid", true, "Inferred ownership from UUID presence");
            return true;
        }
        
        // Method 4: Try dynamic method lookup
        try {
            Class<?> entityClass = entity.getClass();
            String[] methodNames = {"isOwned", "hasOwner", "isTamed", "isPlayerOwned"};
            
            for (String methodName : methodNames) {
                try {
                    Method method = entityClass.getMethod(methodName);
                    Object result = method.invoke(entity);
                    if (result instanceof Boolean) {
                        EntityRaidDebugLogger.logReflectionCall("isOwned", "dynamic_" + methodName, true, "Dynamic result: " + result);
                        return (Boolean) result;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next method name
                }
            }
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("ReflectionCache.isOwned", "Error with dynamic method lookup", e);
        }
        
        EntityRaidDebugLogger.logReflectionCall("isOwned", "all_methods", false, "All ownership detection methods failed");
        return null;
    }
    
    /**
     * Get the creation time of a recruit entity
     */
    public static Long getCreationTime(Object entity) {
        initialize();
        
        if (!recruitsModAvailable) {
            return null;
        }
        
        // Try cached method first
        if (getCreationTimeMethod != null) {
            try {
                Object result = getCreationTimeMethod.invoke(entity);
                if (result instanceof Long) {
                    return (Long) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.getCreationTime", "Error invoking cached getCreationTime method", e);
            }
        }
        
        // Fallback: try to find method dynamically
        try {
            Class<?> entityClass = entity.getClass();
            Method method = entityClass.getMethod("getCreationTime");
            Object result = method.invoke(entity);
            if (result instanceof Long) {
                return (Long) result;
            }
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("ReflectionCache.getCreationTime", "Error with dynamic method lookup", e);
        }
        
        return null;
    }
    
    /**
     * Check if Recruits mod is available
     */
    public static boolean isRecruitsModAvailable() {
        initialize();
        return recruitsModAvailable;
    }
    
    /**
     * Get the team of an entity using cached reflection
     */
    public static Team getEntityTeam(Object entity) {
        initialize();
        
        if (!abstractRecruitEntityAvailable) {
            return null;
        }
        
        // Method 1: Try cached getTeam method
        if (getTeamMethod != null) {
            try {
                Object result = getTeamMethod.invoke(entity);
                if (result instanceof Team) {
                    EntityRaidDebugLogger.logReflectionCall("getEntityTeam", "primary", true, "Team retrieved successfully");
                    return (Team) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.getEntityTeam", "Error invoking primary getTeam method", e);
            }
        }
        
        // Method 2: Try fallback team methods
        Method teamMethod = dynamicMethodCache.get("additional_getTeam");
        if (teamMethod != null) {
            try {
                Object result = teamMethod.invoke(entity);
                if (result instanceof Team) {
                    EntityRaidDebugLogger.logReflectionCall("getEntityTeam", "fallback", true, "Team retrieved via fallback");
                    return (Team) result;
                }
            } catch (Exception e) {
                EntityRaidDebugLogger.logError("ReflectionCache.getEntityTeam", "Error invoking fallback getTeam method", e);
            }
        }
        
        // Method 3: If entity is a LivingEntity, use standard Minecraft method
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            Team team = ((net.minecraft.world.entity.LivingEntity) entity).getTeam();
            if (team != null) {
                EntityRaidDebugLogger.logReflectionCall("getEntityTeam", "minecraft_standard", true, "Team retrieved via standard method");
                return team;
            }
        }
        
        EntityRaidDebugLogger.logReflectionCall("getEntityTeam", "all_methods", false, "All team retrieval methods failed");
        return null;
    }
    
    /**
     * Check if the Recruits mod events system is available
     */
    public static boolean isRecruitsEventsAvailable() {
        initialize();
        return recruitsEventsAvailable;
    }
    
    /**
     * Check if recruit entity classes are available
     */
    public static boolean isRecruitEntityClassAvailable() {
        initialize();
        return abstractRecruitEntityAvailable;
    }
    
    /**
     * Get owner UUID specifically for recruit entities
     * This is a specialized version of getOwnerUUID for recruit entities
     */
    public static UUID getRecruitOwnerUUID(Object entity) {
        if (entity == null) {
            EntityRaidDebugLogger.logReflectionCall("getRecruitOwnerUUID", "null_entity", false, "Entity is null");
            return null;
        }
        
        initialize();
        
        // Verify this is a recruit entity first
        if (!abstractRecruitEntityAvailable || !isRecruitEntity(entity)) {
            EntityRaidDebugLogger.logReflectionCall("getRecruitOwnerUUID", "not_recruit", false, "Entity is not a recruit entity");
            return null;
        }
        
        // Use the standard getOwnerUUID method for recruit entities
        UUID ownerUUID = getOwnerUUID(entity);
        if (ownerUUID != null) {
            EntityRaidDebugLogger.logReflectionCall("getRecruitOwnerUUID", "success", true, "Recruit owner UUID retrieved: " + ownerUUID.toString());
        } else {
            EntityRaidDebugLogger.logReflectionCall("getRecruitOwnerUUID", "failed", false, "Failed to retrieve recruit owner UUID");
        }
        
        return ownerUUID;
    }
    
    /**
     * Helper method to verify if an entity is a recruit entity
     */
    private static boolean isRecruitEntity(Object entity) {
        if (entity == null) return false;
        
        String entityClass = entity.getClass().getName();
        return entityClass.contains("recruit") || 
               entityClass.contains("Recruit") || 
               entityClass.contains("soldier") || 
               entityClass.contains("guard");
    }
    
    /**
     * Get cache statistics for debugging
     */
    public static String getCacheStatistics() {
        initialize();
        
        StringBuilder stats = new StringBuilder();
        stats.append("ReflectionCache Statistics:\n");
        stats.append("- Recruits Mod Available: ").append(recruitsModAvailable).append("\n");
        stats.append("- Events Available: ").append(recruitsEventsAvailable).append("\n");
        stats.append("- Entity Classes Available: ").append(abstractRecruitEntityAvailable).append("\n");
        stats.append("- Primary Methods Cached: ");
        
        int primaryCount = 0;
        if (isAllyMethod != null) primaryCount++;
        if (canHarmTeamMethod != null) primaryCount++;
        if (getOwnerUUIDMethod != null) primaryCount++;
        if (isOwnedMethod != null) primaryCount++;
        if (getCreationTimeMethod != null) primaryCount++;
        if (getTeamMethod != null) primaryCount++;
        
        stats.append(primaryCount).append("\n");
        stats.append("- Fallback Methods Cached: ").append(dynamicMethodCache.size()).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Clear the dynamic method cache (for memory management)
     */
    public static synchronized void clearDynamicCache() {
        dynamicMethodCache.clear();
        EntityRaidDebugLogger.logReflectionCall("clearDynamicCache", "ReflectionCache", true, "Dynamic cache cleared");
    }
    
    /**
     * Force re-initialization (for testing purposes)
     */
    public static synchronized void forceReinitialize() {
        initialized = false;
        recruitsModAvailable = false;
        recruitsEventsAvailable = false;
        abstractRecruitEntityAvailable = false;
        
        // Clear all cached methods
        isAllyMethod = null;
        canHarmTeamMethod = null;
        getOwnerUUIDMethod = null;
        isOwnedMethod = null;
        getCreationTimeMethod = null;
        getTeamMethod = null;
        isEnemyMethod = null;
        canAttackMethod = null;
        getOwnerMethod = null;
        hasOwnerMethod = null;
        
        // Clear dynamic cache
        dynamicMethodCache.clear();
        
        initialize();
    }
}