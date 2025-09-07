package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.raid.EntityRaidDebugLogger;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.EntityGlowPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages entity-based raids and glow effects for colony defense
 */
public class EntityRaidManager {
    private static final Logger LOGGER = LogManager.getLogger(EntityRaidManager.class);
    
    // Active entity raids by colony ID
    private static final Map<Integer, ActiveEntityRaid> activeEntityRaids = new ConcurrentHashMap<>();
    
    // Recently recruited entities to prevent immediate raid triggers
    private static final Map<UUID, Long> recentlyRecruitedEntities = new ConcurrentHashMap<>();
    
    // Track last known inside/outside state per colony for boundary crossing detection
    private static final Map<Integer, Map<UUID, Boolean>> entityInsideByColony = new ConcurrentHashMap<>();
    
    // Centralized per-colony cooldown tracking (last raid trigger timestamp)
    private static final Map<Integer, Long> lastRaidTimeByColony = new ConcurrentHashMap<>();
    
    // Cache for entity type whitelist checking to avoid repeated string matching
    private static final Map<net.minecraft.world.entity.EntityType<?>, Boolean> whitelistCache = new ConcurrentHashMap<>();
    private static long lastWhitelistCacheUpdate = 0L;
    private static final long WHITELIST_CACHE_DURATION = 30000L; // 30 seconds
    
    // Configuration constants
    private static final int RAID_DURATION_SECONDS = 300; // 5 minutes
    private static final int RECRUITMENT_COOLDOWN_MS = 30000; // 30 seconds
    
    /**
     * Check if an entity should trigger a raid based on recruitment and alliance status
     */
    public static boolean shouldTriggerEntityRaid(Entity entity, IColony colony) {
        if (entity == null || colony == null) {
            if (TaxConfig.isEntityRaidDebugEnabled()) {
                LOGGER.warn("[EntityRaid] ⚠️ NULL CHECK FAILED: entity={}, colony={}", 
                    (entity != null ? entity.getType().getDescriptionId() : "null"), 
                    (colony != null ? colony.getName() : "null"));
            }
            return false;
        }
        
        EntityRaidDebugLogger.logFilterStep(entity, colony, "RAID_TRIGGER_CHECK", true, 
            "Checking raid trigger for entity: " + entity.getType().getDescriptionId());
        
        // Check if this is a recruit entity
        if (!isRecruitEntity(entity)) {
            EntityRaidDebugLogger.logFilterStep(entity, colony, "RECRUIT_CHECK", false, 
                "Entity is not a recruit, no raid triggered");
            return false;
        }
        
        // Check if recently recruited (cooldown)
        if (isRecentlyRecruited(entity)) {
            EntityRaidDebugLogger.logGracePeriodCheck(entity, 
                System.currentTimeMillis() - recentlyRecruitedEntities.getOrDefault(entity.getUUID(), 0L), true);
            return false;
        }
        
        // Check if allied to colony (unless bypassed for testing)
        if (!TaxConfig.shouldBypassAllianceChecks() && isRecruitAlliedToColony(entity, colony, entity.level())) {
            EntityRaidDebugLogger.logAllianceCheck(entity, colony, true, "ALLIANCE_CHECK_PASSED - Entity is allied, no raid");
            return false;
        } else if (TaxConfig.shouldBypassAllianceChecks()) {
            EntityRaidDebugLogger.logAllianceCheck(entity, colony, false, "ALLIANCE_CHECK_BYPASSED - Testing mode allows allied entities to trigger raids");
        }
        
        EntityRaidDebugLogger.logFilterStep(entity, colony, "FINAL_TRIGGER_CHECK", true, 
            "All conditions met, triggering entity raid");
        return true;
    }
    
    /**
     * Start an entity raid for a colony
     */
    public static void startEntityRaid(IColony colony, Entity triggerEntity) {
        if (colony == null || triggerEntity == null) {
            EntityRaidDebugLogger.logError("startEntityRaid", "Colony or trigger entity is null", null);
            return;
        }
        
        int colonyId = colony.getID();
        
        // Check if raid is already active
        if (activeEntityRaids.containsKey(colonyId)) {
            EntityRaidDebugLogger.logPrerequisiteCheck(colony, "RAID_NOT_ACTIVE", false, 
                "Entity raid already active for colony: " + colony.getName());
            return;
        }
        
        EntityRaidDebugLogger.logEntityDetection(colony, java.util.Arrays.asList(triggerEntity));
        
        // Create new active raid
        ActiveEntityRaid raid = new ActiveEntityRaid(colony, triggerEntity);
        activeEntityRaids.put(colonyId, raid);
        // Mark cooldown start
        markRaidTriggered(colonyId);
        
        // Apply glow effects to nearby entities
        applyGlowEffectToEntities(colony, triggerEntity.level());
        
        // Log raid start
        logFilterCompletion(colony, "RAID_STARTED", 1);
    }

    /**
     * Get the last raid trigger time for a colony (0L if none)
     */
    public static long getLastRaidTime(int colonyId) {
        return lastRaidTimeByColony.getOrDefault(colonyId, 0L);
    }

    /**
     * Mark current time as the last raid trigger time for a colony
     */
    public static void markRaidTriggered(int colonyId) {
        lastRaidTimeByColony.put(colonyId, System.currentTimeMillis());
    }

    /**
     * Returns true if the colony is on cooldown, given a cooldown window in ms
     */
    public static boolean isOnCooldown(int colonyId, long cooldownMs) {
        if (cooldownMs <= 0) return false;
        long last = getLastRaidTime(colonyId);
        return (System.currentTimeMillis() - last) < cooldownMs;
    }
    
    /**
     * End an entity raid for a colony
     */
    public static void endEntityRaid(int colonyId, String reason) {
        ActiveEntityRaid raid = activeEntityRaids.remove(colonyId);
        if (raid != null) {
            EntityRaidDebugLogger.logPrerequisiteCheck(raid.getColony(), "RAID_END", true, 
                "Ending entity raid - Reason: " + reason);
            
            // Clean up bossbar and notifications
            raid.cleanup();
            
            // Remove glow effects
            removeGlowEffectFromEntities(raid.getColony());
            
            // Final penalty capped at 20% of current colony revenue (only if not already deducted periodically)
            if (!"Expired".equals(reason)) { // Don't double-deduct for natural expiration
                try {
                    double pct = TaxConfig.RAID_PENALTY_PERCENTAGE.get() / 100.0;
                    if (pct > 0) {
                        pct = Math.min(pct, 0.20d);
                        TaxManager.deductColonyTax(raid.getColony(), pct);
                    }
                } catch (Exception ex) {
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.warn("[EntityRaid] Failed to apply penalty on raid end: {}", ex.toString());
                    }
                }
            }
            
            // Notify colony members that raid has ended
            notifyRaidEnded(raid.getColony(), reason);
            
            // Clear cached boundary states for this colony to avoid stale memory
            entityInsideByColony.remove(colonyId);
            
            // Log raid end
            logFilterCompletion(raid.getColony(), "RAID_ENDED", 0);
        }
    }
    
    private static void notifyRaidEnded(IColony colony, String reason) {
        try {
            if (colony.getWorld() == null || colony.getWorld().getServer() == null) return;
            
            MinecraftServer server = colony.getWorld().getServer();
            if (server == null) return;
            
            Component message = Component.literal("The ambush on your colony has ended! (" + reason + ")")
                .withStyle(ChatFormatting.GREEN);
            
            // Notify colony owner
            UUID ownerUUID = colony.getPermissions().getOwner();
            if (ownerUUID != null) {
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
                if (owner != null) {
                    owner.sendSystemMessage(message);
                }
            }
            
            // Notify colony officers and allies
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                    player.sendSystemMessage(message);
                }
            }
        } catch (Exception e) {
            if (TaxConfig.isEntityRaidDebugEnabled()) {
                LOGGER.warn("[EntityRaid] Failed to notify raid ended: {}", e.toString());
            }
        }
    }
    
    /**
     * Tick active entity raids
     */
    public static void tick() {
        // First, evaluate active raids and collect those that should end with reasons
        java.util.List<Integer> toEnd = new java.util.ArrayList<>();
        java.util.List<String> reasons = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, ActiveEntityRaid> entry : activeEntityRaids.entrySet()) {
            ActiveEntityRaid raid = entry.getValue();
            ActiveEntityRaid.EndReason reason = raid.tick();
            if (reason != ActiveEntityRaid.EndReason.NONE) {
                toEnd.add(entry.getKey());
                String r = (reason == ActiveEntityRaid.EndReason.EXPIRED) ? "Expired" : "Boundary timeout";
                reasons.add(r);
            }
        }
        // End raids outside the iteration to avoid concurrent modification
        for (int i = 0; i < toEnd.size(); i++) {
            endEntityRaid(toEnd.get(i), reasons.get(i));
        }
        
        // Clean up old recruitment cooldowns
        cleanupRecruitmentCooldowns();
    }
    
    /**
     * Check if an entity is a recruit using cached results for performance
     */
    private static boolean isRecruitEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        
        net.minecraft.world.entity.EntityType<?> entityType = entity.getType();
        
        // Check cache first - expire cache every 30 seconds in case config changes
        long now = System.currentTimeMillis();
        if (now - lastWhitelistCacheUpdate > WHITELIST_CACHE_DURATION) {
            whitelistCache.clear();
            lastWhitelistCacheUpdate = now;
        }
        
        // Return cached result if available
        Boolean cachedResult = whitelistCache.get(entityType);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // Compute result and cache it
        boolean match = computeWhitelistMatch(entity, entityType);
        whitelistCache.put(entityType, match);
        
        // Only log successful matches to avoid spam - failed matches are too numerous and not useful
        if (TaxConfig.isEntityRaidDebugEnabled() && match) {
            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
            String registryId = rl != null ? rl.toString() : "";
            LOGGER.info("[EntityRaid] ✅ WHITELIST MATCH: {} matches pattern in whitelist. registryId={}", 
                entityType.getDescriptionId(), registryId);
        }
        
        return match;
    }
    
    /**
     * Compute whitelist match for entity type (separated for clarity)
     */
    private static boolean computeWhitelistMatch(Entity entity, net.minecraft.world.entity.EntityType<?> entityType) {
        // Proper detection: match against configured whitelist
        // Supports patterns:
        //  - "*" (all entities)
        //  - "modid:*" (all entities from a given mod/namespace)
        //  - exact registry id (e.g., "minecraft:pillager", "recruits:recruit")
        //  - exact description id (e.g., "entity.minecraft.pillager")
        List<? extends String> whitelist = TaxConfig.getEntityRaidWhitelist();
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        String registryId = rl != null ? rl.toString() : "";
        String namespace = rl != null ? rl.getNamespace() : "";
        String descId = entityType.getDescriptionId();

        for (String pattern : whitelist) {
            if (pattern == null) continue;
            pattern = pattern.trim();
            if (pattern.isEmpty()) continue;

            // Global wildcard
            if (pattern.equals("*")) { 
                return true; 
            }

            // Namespace wildcard, e.g., "recruits:*"
            if (pattern.endsWith(":*")) {
                String ns = pattern.substring(0, pattern.length() - 2);
                if (ns.equalsIgnoreCase(namespace)) { 
                    return true; 
                }
            }

            // Exact registryId or descId
            if (pattern.equalsIgnoreCase(registryId) || pattern.equalsIgnoreCase(descId)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if an entity was recently recruited
     */
    private static boolean isRecentlyRecruited(Entity entity) {
        Long recruitTime = recentlyRecruitedEntities.get(entity.getUUID());
        if (recruitTime == null) {
            return false;
        }
        
        return (System.currentTimeMillis() - recruitTime) < RECRUITMENT_COOLDOWN_MS;
    }
    
    /**
     * Mark an entity as recently recruited
     */
    public static void markEntityAsRecruited(Entity entity) {
        if (entity != null) {
            recentlyRecruitedEntities.put(entity.getUUID(), System.currentTimeMillis());
        }
    }
    
    /**
     * Check if a recruit entity is allied to a colony
     */
    private static boolean isRecruitAlliedToColony(Entity entity, IColony colony, Level level) {
        long startTime = System.currentTimeMillis();
        try {
            if (colony.getWorld() == null || colony.getWorld().getServer() == null) {
                EntityRaidDebugLogger.logError("isRecruitAlliedToColony", 
                    "PREREQUISITE_FAIL: World or Server is null for colony: " + colony.getName(), null);
                return false;
            }
            
            MinecraftServer server = colony.getWorld().getServer();
            if (server == null) {
                EntityRaidDebugLogger.logAllianceCheck(entity, colony, false, 
                    "PREREQUISITE_FAIL: Server is null");
                return false;
            }
            
            UUID colonyOwnerUUID = colony.getPermissions().getOwner();
            if (colonyOwnerUUID == null) {
                EntityRaidDebugLogger.logAllianceCheck(entity, colony, false, 
                    "PREREQUISITE_FAIL: Colony has no owner");
                return false;
            }
            
            ServerPlayer colonyOwner = server.getPlayerList().getPlayer(colonyOwnerUUID);
            if (colonyOwner == null) {
                EntityRaidDebugLogger.logAllianceCheck(entity, colony, false, 
                    "PREREQUISITE_FAIL: Colony owner is offline");
                return false;
            }
            
            // Check Recruits mod diplomacy system first (if available)
            Boolean recruitsDiplomacy = checkRecruitsDiplomacy(entity, colonyOwner, colony);
            if (recruitsDiplomacy != null) {
                return recruitsDiplomacy;
            }
            
            Boolean teamAlliance = checkTeamBasedAlliance(entity, colonyOwner, colony);
            if (teamAlliance != null) {
                return teamAlliance;
            }
            
            Boolean ownershipAlliance = checkOwnershipBasedAlliance(entity, colonyOwnerUUID, colony, server);
            if (ownershipAlliance != null) {
                return ownershipAlliance;
            }
            
            EntityRaidDebugLogger.logAllianceCheck(entity, colony, false, 
                "ALL_METHODS_FAILED: No definitive alliance found.");
            return false;
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("isRecruitAlliedToColony", 
                "CRITICAL_ERROR: Unexpected error during alliance check for " + entity.getType().getDescriptionId(), e);
            return false;
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 2) {
                LOGGER.info("[EntityRaid-PERF] Alliance check took {}ms for entity {} vs colony {}", 
                    elapsed, entity.getType().getDescriptionId(), colony.getName());
            }
        }
    }
    
    /**
     * Check Recruits mod diplomacy system for faction relationships
     */
    private static Boolean checkRecruitsDiplomacy(Entity entity, ServerPlayer colonyOwner, IColony colony) {
        try {
            // Check if Recruits mod diplomacy managers are available
            if (!isRecruitsModLoaded()) {
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.info("[EntityRaid-RECRUITS] Recruits mod not detected, skipping diplomacy check");
                }
                return null;
            }
            
            // Get teams from Minecraft scoreboard system
            net.minecraft.world.scores.Team entityTeam = entity.getTeam();
            net.minecraft.world.scores.Team colonyOwnerTeam = colonyOwner.getTeam();
            
            if (entityTeam == null || colonyOwnerTeam == null) {
                // Only log team membership at highest debug level to avoid spam
                if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 3) {
                    LOGGER.debug("[EntityRaid-RECRUITS] Entity or colony owner not in teams (entity: {}, owner: {})", 
                        entityTeam != null ? entityTeam.getName() : "null", 
                        colonyOwnerTeam != null ? colonyOwnerTeam.getName() : "null");
                }
                return null;
            }
            
            String entityTeamName = entityTeam.getName();
            String colonyTeamName = colonyOwnerTeam.getName();
            
            // Check diplomatic relation using reflection to avoid hard dependency
            Object diplomacyStatus = getRecruitsDiplomaticRelation(entityTeamName, colonyTeamName);
            if (diplomacyStatus != null) {
                String statusName = diplomacyStatus.toString();
                boolean isAlly = "ALLY".equals(statusName);
                
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.info("[EntityRaid-RECRUITS] Diplomatic relation: {} -> {} = {} (isAlly: {})", 
                        entityTeamName, colonyTeamName, statusName, isAlly);
                }
                
                return isAlly;
            }
            
            return null; // Inconclusive
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("checkRecruitsDiplomacy", 
                "Failed to check Recruits diplomacy for " + entity.getType().getDescriptionId(), e);
            return null;
        }
    }
    
    /**
     * Check for team-based alliances (Minecraft teams or FTB Teams)
     */
    private static Boolean checkTeamBasedAlliance(Entity entity, ServerPlayer colonyOwner, IColony colony) {
        try {
            // Check Minecraft vanilla team system
            net.minecraft.world.scores.Team entityTeam = entity.getTeam();
            net.minecraft.world.scores.Team ownerTeam = colonyOwner.getTeam();
            
            if (entityTeam != null && ownerTeam != null) {
                boolean sameTeam = entityTeam.getName().equals(ownerTeam.getName());
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.info("[EntityRaid-ALLIANCE] Entity team: '{}', Owner team: '{}', Same: {}", 
                        entityTeam.getName(), ownerTeam.getName(), sameTeam);
                }
                return sameTeam;
            }
            
            return null; // Inconclusive
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("checkTeamBasedAlliance", 
                "Failed to check team alliance for " + entity.getType().getDescriptionId(), e);
            return null;
        }
    }
    
    /**
     * Check ownership-based alliance using reflection
     */
    private static Boolean checkOwnershipBasedAlliance(Entity entity, UUID colonyOwnerUUID, IColony colony, MinecraftServer server) {
        try {
            UUID entityOwnerUUID = ReflectionCache.getRecruitOwnerUUID(entity);
            if (entityOwnerUUID != null) {
                boolean isAllied = entityOwnerUUID.equals(colonyOwnerUUID);
                EntityRaidDebugLogger.logAllianceCheck(entity, colony, isAllied, 
                    "OWNERSHIP_ALLIANCE: Entity owner=" + entityOwnerUUID + 
                    ", Colony owner=" + colonyOwnerUUID);
                return isAllied;
            }
        } catch (Exception e) {
            EntityRaidDebugLogger.logError("checkOwnershipBasedAlliance", 
                "Error checking ownership alliance", e);
        }
        return null;
    }
    
    /**
     * Apply glow effect to entities near colony (owner-only)
     */
    private static void applyGlowEffectToEntities(IColony colony, Level level) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) level;
        UUID colonyOwnerUUID = colony.getPermissions().getOwner();
        if (colonyOwnerUUID == null) {
            return;
        }
        
        MinecraftServer server = serverLevel.getServer();
        ServerPlayer colonyOwner = server.getPlayerList().getPlayer(colonyOwnerUUID);
        if (colonyOwner == null) {
            return;
        }
        
        // Find entities near colony center
        List<Entity> nearbyEntities = new java.util.ArrayList<>();
        for (Entity entity : serverLevel.getEntities().getAll()) {
            double distanceSq = entity.distanceToSqr(colony.getCenter().getX(), 
                                                   colony.getCenter().getY(), 
                                                   colony.getCenter().getZ());
            if (distanceSq < 10000 && isRecruitEntity(entity)) { // 100 block radius squared
                nearbyEntities.add(entity);
            }
        }
        
        // Send glow packets to colony owner
        for (Entity entity : nearbyEntities) {
            EntityGlowPacket packet = new EntityGlowPacket(entity.getId(), true, 6000); // 5 minutes in ticks
            NetworkHandler.CHANNEL.sendTo(packet, colonyOwner.connection.connection, 
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        }
        
        // Log glow effect application - using generic logger since no specific method exists
        if (TaxConfig.isEntityRaidDebugEnabled()) {
            LOGGER.info("[EntityRaid-GLOW] Applied glow effect to {} entities for colony: {}", 
                nearbyEntities.size(), colony.getName());
        }
    }
    
    /**
     * Remove glow effect from entities
     */
    private static void removeGlowEffectFromEntities(IColony colony) {
        Level level = colony.getWorld();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) level;
        UUID colonyOwnerUUID = colony.getPermissions().getOwner();
        if (colonyOwnerUUID == null) {
            return;
        }
        
        MinecraftServer server = serverLevel.getServer();
        ServerPlayer colonyOwner = server.getPlayerList().getPlayer(colonyOwnerUUID);
        if (colonyOwner == null) {
            return;
        }
        
        // Find entities near colony center
        List<Entity> nearbyEntities = new java.util.ArrayList<>();
        for (Entity entity : serverLevel.getEntities().getAll()) {
            double distanceSq = entity.distanceToSqr(colony.getCenter().getX(), 
                                                   colony.getCenter().getY(), 
                                                   colony.getCenter().getZ());
            if (distanceSq < 10000 && isRecruitEntity(entity)) { // 100 block radius squared
                nearbyEntities.add(entity);
            }
        }
        
        // Send remove glow packets to colony owner
        for (Entity entity : nearbyEntities) {
            EntityGlowPacket packet = new EntityGlowPacket(entity.getId(), false, 0);
            NetworkHandler.CHANNEL.sendTo(packet, colonyOwner.connection.connection, 
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        }
        
        // Log glow effect removal - using generic logger since no specific method exists
        if (TaxConfig.isEntityRaidDebugEnabled()) {
            LOGGER.info("[EntityRaid-GLOW] Removed glow effect from {} entities for colony: {}", 
                nearbyEntities.size(), colony.getName());
        }
    }
    
    /**
     * Clean up old recruitment cooldowns
     */
    private static void cleanupRecruitmentCooldowns() {
        long currentTime = System.currentTimeMillis();
        recentlyRecruitedEntities.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > RECRUITMENT_COOLDOWN_MS);
    }
    
    /**
     * Boundary crossing helper for scanners: updates and returns the crossing event.
     */
    public static BoundaryEvent checkAndUpdateBoundary(IColony colony, Entity entity) {
        if (colony == null || entity == null || colony.getWorld() == null) {
            return BoundaryEvent.NONE;
        }
        Map<UUID, Boolean> map = entityInsideByColony.computeIfAbsent(colony.getID(), k -> new ConcurrentHashMap<>());
        boolean currentInside = false;
        try {
            currentInside = colony.isCoordInColony(colony.getWorld(), entity.blockPosition());
        } catch (Throwable t) {
            // Defensive: if API throws, treat as no change
            return BoundaryEvent.NONE;
        }
        UUID id = entity.getUUID();
        Boolean prev = map.put(id, currentInside);
        if (prev == null) {
            return BoundaryEvent.NONE;
        }
        if (!prev && currentInside) {
            return BoundaryEvent.ENTERED;
        }
        if (prev && !currentInside) {
            return BoundaryEvent.EXITED;
        }
        return BoundaryEvent.NONE;
    }
    
    public enum BoundaryEvent { NONE, ENTERED, EXITED }
    
    /**
     * Check if Recruits mod is loaded using reflection
     */
    private static boolean isRecruitsModLoaded() {
        try {
            Class.forName("com.talhanation.recruits.TeamEvents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Get diplomatic relation between two Recruits teams using reflection
     */
    private static Object getRecruitsDiplomaticRelation(String team1, String team2) {
        try {
            Class<?> teamEventsClass = Class.forName("com.talhanation.recruits.TeamEvents");
            java.lang.reflect.Field diplomacyManagerField = teamEventsClass.getDeclaredField("recruitsDiplomacyManager");
            diplomacyManagerField.setAccessible(true);
            Object diplomacyManager = diplomacyManagerField.get(null);
            
            if (diplomacyManager != null) {
                java.lang.reflect.Method getRelationMethod = diplomacyManager.getClass().getMethod("getRelation", String.class, String.class);
                return getRelationMethod.invoke(diplomacyManager, team1, team2);
            }
        } catch (Exception e) {
            if (TaxConfig.isEntityRaidDebugEnabled()) {
                LOGGER.warn("[EntityRaid-RECRUITS] Failed to access Recruits diplomacy: {}", e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Log filter completion (placeholder implementation)
     */
    private static void logFilterCompletion(IColony colony, String stage, int count) {
        // Log filter completion - using generic logger since no specific method exists
        if (TaxConfig.isEntityRaidDebugEnabled()) {
            LOGGER.info("[EntityRaid-FILTER] Colony: {} - Stage: {} - Count: {}", 
                colony.getName(), stage, count);
        }
    }
    
    /**
     * Get active entity raid for a colony
     */
    public static ActiveEntityRaid getActiveEntityRaid(int colonyId) {
        return activeEntityRaids.get(colonyId);
    }
    
    /**
     * Check if a colony has an active entity raid
     */
    public static boolean hasActiveEntityRaid(int colonyId) {
        return activeEntityRaids.containsKey(colonyId);
    }
    
    /**
     * Get all active entity raids (for commands)
     */
    public static java.util.Map<Integer, ActiveEntityRaid> getActiveEntityRaids() {
        return new java.util.HashMap<>(activeEntityRaids);
    }
    
    /**
     * Inner class representing an active entity raid
     */
    public static class ActiveEntityRaid {
        private final IColony colony;
        private final Entity triggerEntity;
        private final long startTime;
        private final int colonyId;
        private boolean isActive;
        private boolean hasLeftBoundary;
        // private long boundaryLeftAtMs = -1L; // Removed - no longer using boundary timeout
        private ServerBossEvent bossBar;
        private long lastRevenueDeduction = 0L;
        private int lastEntityCount = 0;
        // Grace countdown when ALL entities leave the boundary during an active raid.
        // Pause/resume semantics:
        //  - graceRemainingMs: remaining millis until timeout (initialized to 5000 on first leave)
        //  - graceActiveStartMs: when countdown last started/resumed; -1 when paused/not active
        private long graceRemainingMs = -1L;
        private long graceActiveStartMs = -1L;
        
        // Cache entity count to avoid expensive scanning every tick
        private int cachedEntityCount = 0;
        private long lastEntityCountUpdate = 0L;
        private static final long ENTITY_COUNT_CACHE_MS = 2000L; // Update every 2 seconds instead of every tick
        
        public ActiveEntityRaid(IColony colony, Entity triggerEntity) {
            this.colony = colony;
            this.triggerEntity = triggerEntity;
            this.startTime = System.currentTimeMillis();
            this.colonyId = colony.getID();
            this.isActive = true;
            this.hasLeftBoundary = false;
            this.lastRevenueDeduction = startTime;
            
            // Create bossbar for colony members
            createBossBar();
            
            // Send initial notifications
            notifyColonyMembers();
        }
        
        public EndReason tick() {
            long now = System.currentTimeMillis();
            long elapsed = now - startTime;
            
            // Check if raid has expired by duration (5 minutes)
            if (elapsed > (RAID_DURATION_SECONDS * 1000L)) {
                isActive = false;
                return EndReason.EXPIRED; // Raid expired
            }
            
            // Periodic revenue deduction (every minute)
            if (now - lastRevenueDeduction >= 60000L) { // 60 seconds
                deductRevenue();
                lastRevenueDeduction = now;
            }
            
            // Current whitelisted entity count inside boundary and threshold (cached for performance)
            int currentCount = getCachedEntityCount(now);
            int threshold = Math.max(1, TaxConfig.getEntityRaidThreshold());

            // New grace logic: only when ALL entities have left (currentCount == 0)
            if (currentCount == 0) {
                // Initialize remaining window to 5s if first time
                if (graceRemainingMs < 0L) {
                    graceRemainingMs = 5000L;
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid] All entities left boundary, starting 5s grace for colony '{}'", colony.getName());
                    }
                }
                // Start or continue countdown
                if (graceActiveStartMs < 0L) {
                    graceActiveStartMs = now; // resume countdown
                }
                long graceElapsed = now - graceActiveStartMs;
                if (graceElapsed >= graceRemainingMs) {
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid] Grace expired (no entities returned); ending raid for colony '{}'", colony.getName());
                    }
                    isActive = false;
                    return EndReason.BOUNDARY_TIMEOUT;
                }
            } else {
                // At least one entity is inside; pause countdown if it was running
                if (graceRemainingMs >= 0L && graceActiveStartMs >= 0L) {
                    long graceElapsed = now - graceActiveStartMs;
                    graceRemainingMs = Math.max(0L, graceRemainingMs - graceElapsed);
                    graceActiveStartMs = -1L; // paused
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid] Entity re-entered; pausing grace (remaining={} ms) for colony '{}'", graceRemainingMs, colony.getName());
                    }
                }
            }
            
            // Update bossbar with latest status
            updateBossBar(currentCount, threshold, now);
            
            return EndReason.NONE; // Raid continues
        }
        
        private void createBossBar() {
            if (bossBar != null) return; // Already created
            
            Component title = Component.literal("Ambush!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bossBar.setProgress(1.0f); // Start at full
            
            // Add colony members to bossbar
            addColonyMembersToBossBar();

            // Debug log
            if (TaxConfig.isEntityRaidDebugEnabled()) {
                LOGGER.info("[EntityRaid] Bossbar created for colony '{}'", colony.getName());
            }
        }
        
        private void updateBossBar(int currentCount, int threshold, long now) {
            if (bossBar == null) return;

            // Calculate time remaining in raid (5 minutes total)
            long raidElapsed = now - startTime;
            long raidTimeLeftMs = Math.max(0L, (RAID_DURATION_SECONDS * 1000L) - raidElapsed);
            
            // Fix timer calculation to avoid 5m 60s display
            int totalSecondsLeft = (int) Math.ceil(raidTimeLeftMs / 1000.0);
            int minutesLeft = totalSecondsLeft / 60;
            int secondsLeft = totalSecondsLeft % 60;
            String timeLeftStr = minutesLeft > 0 ? minutesLeft + "m " + secondsLeft + "s" : secondsLeft + "s";

            // Progress based on time remaining (starts at 1.0, decreases to 0.0)
            float progress = Math.max(0.0f, Math.min(1.0f, raidTimeLeftMs / (float)(RAID_DURATION_SECONDS * 1000L)));
            bossBar.setProgress(progress);

            // Title shows remaining entities and time left
            if (graceRemainingMs >= 0L && graceActiveStartMs >= 0L) {
                // During grace period (all entities left)
                long graceElapsed = now - graceActiveStartMs;
                long graceMillisLeft = Math.max(0L, graceRemainingMs - graceElapsed);
                int graceSecsLeft = (int) Math.ceil(graceMillisLeft / 1000.0);
                bossBar.setColor(BossEvent.BossBarColor.YELLOW);
                Component title = Component.literal("Ambush! 0 remaining — Return in " + graceSecsLeft + "s (" + timeLeftStr + " left)")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                bossBar.setName(title);
            } else {
                // Normal raid - show entity count and time remaining
                bossBar.setColor(BossEvent.BossBarColor.RED);
                Component title = Component.literal("Ambush! " + currentCount + " remaining — " + timeLeftStr + " left")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                bossBar.setName(title);
            }

            // Track for change detection (optional)
            lastEntityCount = currentCount;
        }
        
        /**
         * Get cached entity count to avoid expensive scanning every tick
         */
        private int getCachedEntityCount(long currentTime) {
            // Update cache if expired or never set
            if (currentTime - lastEntityCountUpdate > ENTITY_COUNT_CACHE_MS) {
                cachedEntityCount = countWhitelistedEntitiesInside();
                lastEntityCountUpdate = currentTime;
            }
            return cachedEntityCount;
        }
        
        private int countWhitelistedEntitiesInside() {
            if (colony.getWorld() == null || !(colony.getWorld() instanceof ServerLevel serverLevel)) {
                return 0;
            }
            
            int count = 0;
            try {
                for (Entity entity : serverLevel.getEntities().getAll()) {
                    if (isRecruitEntity(entity) && colony.isCoordInColony(colony.getWorld(), entity.blockPosition())) {
                        count++;
                    }
                }
            } catch (Exception e) {
                // Ignore errors during counting
            }
            return count;
        }
        
        private void deductRevenue() {
            try {
                // Config value is already a decimal (0.25 = 25%), no need to divide by 100
                double pct = TaxConfig.RAID_PENALTY_PERCENTAGE.get();
                if (pct > 0) {
                    TaxManager.deductColonyTax(colony, pct);
                    // Always log tax deduction for visibility (even without debug)
                    LOGGER.info("[EntityRaid] ⚠️ TAX DEDUCTED: {}% revenue from colony '{}' during raid", 
                        pct * 100, colony.getName());
                    
                    // Also notify colony members about the deduction
                    notifyTaxDeduction(pct * 100);
                }
            } catch (Exception ex) {
                LOGGER.warn("[EntityRaid] ❌ FAILED to deduct revenue during raid from '{}': {}", 
                    colony.getName(), ex.toString());
            }
        }
        
        private void notifyTaxDeduction(double percentage) {
            try {
                if (colony.getWorld() == null || colony.getWorld().getServer() == null) return;
                
                MinecraftServer server = colony.getWorld().getServer();
                Component message = Component.literal("⚠️ Raid tax deduction: " + String.format("%.1f", percentage) + "% of colony revenue lost!")
                    .withStyle(ChatFormatting.RED);
                
                Set<UUID> notifiedPlayers = new HashSet<>();
                
                // Notify colony owner first
                UUID ownerUUID = colony.getPermissions().getOwner();
                if (ownerUUID != null) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
                    if (owner != null) {
                        owner.sendSystemMessage(message);
                        notifiedPlayers.add(ownerUUID);
                    }
                }
                
                // Notify colony officers (avoid duplicating owner)
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    UUID playerUUID = player.getUUID();
                    if (!notifiedPlayers.contains(playerUUID) &&
                        colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                        player.sendSystemMessage(message);
                        notifiedPlayers.add(playerUUID);
                    }
                }
            } catch (Exception e) {
                // Silent fail for notifications
            }
        }
        
        private void notifyColonyMembers() {
            try {
                if (colony.getWorld() == null || colony.getWorld().getServer() == null) return;
                
                MinecraftServer server = colony.getWorld().getServer();
                if (server == null) return;
                
                Component message = Component.literal("Your colony is under attack! Defend against the ambush!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                
                Set<UUID> notifiedPlayers = new HashSet<>();
                
                // Notify colony owner first
                UUID ownerUUID = colony.getPermissions().getOwner();
                if (ownerUUID != null) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
                    if (owner != null) {
                        owner.sendSystemMessage(message);
                        owner.displayClientMessage(Component.literal("Ambush!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        notifiedPlayers.add(ownerUUID);
                        if (TaxConfig.isEntityRaidDebugEnabled()) {
                            LOGGER.info("[EntityRaid] Notified owner {} for colony '{}'",
                                owner.getGameProfile().getName(), colony.getName());
                        }
                    }
                }
                
                // Notify colony officers and allies (but avoid duplicating owner notification)
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    UUID playerUUID = player.getUUID();
                    if (!notifiedPlayers.contains(playerUUID) && 
                        colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                        player.sendSystemMessage(message);
                        player.displayClientMessage(Component.literal("Ambush!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        notifiedPlayers.add(playerUUID);
                        if (TaxConfig.isEntityRaidDebugEnabled()) {
                            LOGGER.info("[EntityRaid] Notified player {} (ACCESS_HUTS) for colony '{}'",
                                player.getGameProfile().getName(), colony.getName());
                        }
                    }
                }
            } catch (Exception e) {
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.warn("[EntityRaid] Failed to notify colony members: {}", e.toString());
                }
            }
        }
        
        private void addColonyMembersToBossBar() {
            if (bossBar == null) return;
            
            try {
                if (colony.getWorld() == null || colony.getWorld().getServer() == null) return;
                
                MinecraftServer server = colony.getWorld().getServer();
                
                // Add colony owner
                UUID ownerUUID = colony.getPermissions().getOwner();
                if (ownerUUID != null) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
                    if (owner != null) {
                        bossBar.addPlayer(owner);
                        if (TaxConfig.isEntityRaidDebugEnabled()) {
                            LOGGER.info("[EntityRaid] Added owner {} to bossbar for colony '{}'",
                                owner.getGameProfile().getName(), colony.getName());
                        }
                    }
                }
                
                // Add colony officers and allies
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                        bossBar.addPlayer(player);
                        if (TaxConfig.isEntityRaidDebugEnabled()) {
                            LOGGER.info("[EntityRaid] Added player {} to bossbar for colony '{}'",
                                player.getGameProfile().getName(), colony.getName());
                        }
                    }
                }
            } catch (Exception e) {
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.warn("[EntityRaid] Failed to add players to bossbar: {}", e.toString());
                }
            }
        }

        /**
         * Attach a single player to this raid's bossbar if they are eligible (owner or ACCESS_HUTS)
         */
        public void attachEligiblePlayer(ServerPlayer player) {
            if (bossBar == null || player == null) return;
            try {
                UUID ownerUUID = colony.getPermissions().getOwner();
                boolean isOwner = ownerUUID != null && ownerUUID.equals(player.getUUID());
                boolean hasAccess = colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS);
                if (isOwner || hasAccess) {
                    bossBar.addPlayer(player);
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid] Attached player {} to bossbar for colony '{}'",
                            player.getGameProfile().getName(), colony.getName());
                    }
                }
            } catch (Exception e) {
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.warn("[EntityRaid] Failed to attach player to bossbar: {}", e.toString());
                }
            }
        }
        
        public void cleanup() {
            if (bossBar != null) {
                bossBar.removeAllPlayers();
                bossBar = null;
            }
        }
        
        public IColony getColony() {
            return colony;
        }
        
        public Entity getTriggerEntity() {
            return triggerEntity;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public int getColonyId() {
            return colonyId;
        }
        
        public boolean isActive() {
            return isActive;
        }
        
        public boolean hasLeftBoundary() {
            return hasLeftBoundary;
        }
        
        public void setLeftBoundary(boolean leftBoundary) {
            this.hasLeftBoundary = leftBoundary;
        }
        
        public java.util.List<Entity> getTriggeringEntities() {
            // Return a list containing the trigger entity
            java.util.List<Entity> entities = new java.util.ArrayList<>();
            if (triggerEntity != null && triggerEntity.level() != null) {
                entities.add(triggerEntity);
            }
            return entities;
        }
        
        public enum EndReason { NONE, EXPIRED, BOUNDARY_TIMEOUT }
    }
}
