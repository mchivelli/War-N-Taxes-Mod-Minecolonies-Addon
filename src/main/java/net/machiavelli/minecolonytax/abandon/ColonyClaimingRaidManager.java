package net.machiavelli.minecolonytax.abandon;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.mobs.EntityMercenary;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the raid system when players attempt to claim abandoned colonies.
 * When a player claims an abandoned colony, all citizens become hostile militia
 * and attack the claiming player. If there are too few defenders, mercenaries are spawned.
 */
public class ColonyClaimingRaidManager {
    
    private static final Logger LOGGER = LogManager.getLogger(ColonyClaimingRaidManager.class);
    
    // Track active claiming raids
    private static final Map<Integer, ClaimingRaidData> activeClaimingRaids = new ConcurrentHashMap<>();
    
    /**
     * Data class to track a claiming raid.
     */
    public static class ClaimingRaidData {
        public final int colonyId;
        public final UUID claimingPlayerId;
        public final long startTime;
        public final long endTime;
        public final Set<Integer> hostileCitizens;
        public final Set<Entity> spawnedMercenaries;
        public final BlockPos colonyCenter;
        public ServerBossEvent bossEvent;
        
        public ClaimingRaidData(int colonyId, UUID claimingPlayerId, BlockPos colonyCenter) {
            this.colonyId = colonyId;
            this.claimingPlayerId = claimingPlayerId;
            this.colonyCenter = colonyCenter;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + (TaxConfig.getClaimingRaidDurationMinutes() * 60 * 1000);
            this.hostileCitizens = ConcurrentHashMap.newKeySet();
            this.spawnedMercenaries = ConcurrentHashMap.newKeySet();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() >= endTime;
        }
        
        public long getRemainingTime() {
            return Math.max(0, endTime - System.currentTimeMillis());
        }
    }
    
    /**
     * Start a claiming raid for the specified colony and player.
     */
    public static boolean startClaimingRaid(IColony colony, ServerPlayer claimingPlayer) {
        if (!TaxConfig.isAbandonedColonyClaimingEnabled()) {
            claimingPlayer.sendSystemMessage(Component.literal("Colony claiming is disabled!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        
        if (!ColonyAbandonmentManager.isColonyAbandoned(colony)) {
            claimingPlayer.sendSystemMessage(Component.literal("This colony is not abandoned!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        
        if (activeClaimingRaids.containsKey(colony.getID())) {
            claimingPlayer.sendSystemMessage(Component.literal("A claiming raid is already in progress for this colony!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        
        // Check claiming requirements (includes former owner/officer bypass)
        ClaimingRequirementResult requirementResult = checkClaimingRequirements(claimingPlayer, colony);
        if (!requirementResult.canClaim) {
            claimingPlayer.sendSystemMessage(Component.literal("Cannot claim colony: " + requirementResult.message)
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        
        // Log if this is a former owner/officer claiming back their colony
        boolean isFormerMember = ColonyAbandonmentManager.wasFormerOwnerOrOfficer(colony.getID(), claimingPlayer.getUUID());
        if (isFormerMember) {
            LOGGER.info("RECLAIM ATTEMPT: Former owner/officer {} is attempting to reclaim their abandoned colony {}", 
                claimingPlayer.getName().getString(), colony.getName());
            claimingPlayer.sendSystemMessage(Component.literal("You are reclaiming your former colony. Requirements bypassed but you must complete the claiming raid!")
                    .withStyle(ChatFormatting.YELLOW));
        }
        
        try {
            // Create raid data
            ClaimingRaidData raidData = new ClaimingRaidData(colony.getID(), claimingPlayer.getUUID(), colony.getCenter());
            activeClaimingRaids.put(colony.getID(), raidData);
            
            // Convert citizens to hostile militia
            int citizenCount = convertCitizensToMilitia(colony, claimingPlayer, raidData);
            
            // Spawn mercenaries if needed
            int mercenaryCount = 0;
            if (TaxConfig.shouldSpawnMercenariesIfLowDefenders() && citizenCount < 5) {
                mercenaryCount = spawnDefendingMercenaries(colony, claimingPlayer, raidData);
                
                // Update total defender count to include mercenaries
                if (mercenaryCount > 0) {
                    int totalDefenders = citizenCount + mercenaryCount;
                    net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                            .setTotalDefenders(colony.getID(), totalDefenders);
                    LOGGER.info("Updated total defenders to {} (citizens: {}, mercenaries: {}) for claiming raid in colony {}", 
                            totalDefenders, citizenCount, mercenaryCount, colony.getName());
                }
            }
            
            // Create boss bar
            createRaidBossBar(raidData, claimingPlayer);
            
            // Set claimer as hostile and enable claiming permissions
            colony.getPermissions().setPlayerRank(claimingPlayer.getUUID(), colony.getPermissions().getRankHostile(), colony.getWorld());
            setClaimingInteractionPermissions(colony, true);
            
            // Notify players with clear victory conditions
            MutableComponent startMessage = Component.literal("COLONY CLAIMING RAID STARTED!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("\n" + claimingPlayer.getName().getString() + " is attempting to claim the abandoned colony of ")
                           .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(colony.getName() + "!")
                           .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("\nVICTORY CONDITION: Kill ALL " + (citizenCount + mercenaryCount) + " defenders!")
                           .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\nDefenders: " + citizenCount + " citizen militia")
                           .withStyle(ChatFormatting.RED));
            
            if (mercenaryCount > 0) {
                startMessage.append(Component.literal(" + " + mercenaryCount + " mercenaries")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            
            startMessage.append(Component.literal("\nTimer expiration = DEFENDER VICTORY!")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            
            // Broadcast to all players in the area
            broadcastToNearbyPlayers(colony, startMessage, 200);
            
            LOGGER.info("Started claiming raid for colony {} ({}) by player {} with {} defenders", 
                       colony.getName(), colony.getID(), claimingPlayer.getName().getString(), 
                       citizenCount + mercenaryCount);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to start claiming raid for colony {} ({})", 
                        colony.getName(), colony.getID(), e);
            activeClaimingRaids.remove(colony.getID());
            return false;
        }
    }
    
    /**
     * Convert all citizens in the colony to hostile militia that attack the claiming player.
     */
    private static int convertCitizensToMilitia(IColony colony, ServerPlayer claimingPlayer, ClaimingRaidData raidData) {
        int convertedCount = 0;
        
        // Initialize militia system for this colony if needed
        net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance().initializeColonyMilitia(colony.getID());
        
        try {
            for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
                Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
                if (entityOpt.isPresent()) {
                    AbstractEntityCitizen citizen = entityOpt.get();
                    
                    // Add resistance effect
                    citizen.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 
                            TaxConfig.getClaimingRaidDurationMinutes() * 60 * 20, 2));
                    
                    // Add speed and strength
                    citizen.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 
                            TaxConfig.getClaimingRaidDurationMinutes() * 60 * 20, 1));
                    citizen.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 
                            TaxConfig.getClaimingRaidDurationMinutes() * 60 * 20, 1));
                    
                    // Make citizen hostile to the claiming player
                    citizen.setTarget(claimingPlayer);
                    
                    // Add targeting goal to actively hunt the claiming player
                    citizen.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(citizen, Player.class, 
                            10, true, false, (entity) -> entity.equals(claimingPlayer)));
                    
                    // Register this citizen as militia for kill tracking
                    net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                            .addMilitiaMember(colony.getID(), citizenData.getId());
                    
                    raidData.hostileCitizens.add(citizenData.getId());
                    convertedCount++;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error converting citizens to militia for colony {}", colony.getID(), e);
        }
        
        // Set the total defender count for this claiming raid
        if (convertedCount > 0) {
            net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .setTotalDefenders(colony.getID(), convertedCount);
            LOGGER.info("Registered {} citizens as militia defenders for claiming raid in colony {}", 
                    convertedCount, colony.getName());
        }
        
        return convertedCount;
    }
    
    /**
     * Spawn mercenaries to defend the colony if there are too few citizens.
     */
    private static int spawnDefendingMercenaries(IColony colony, ServerPlayer claimingPlayer, ClaimingRaidData raidData) {
        try {
            int mercenaryCount = Math.max(1, 5 - raidData.hostileCitizens.size());
            Level world = colony.getWorld();
            
            for (int i = 0; i < mercenaryCount; i++) {
                EntityMercenary mercenary = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                if (mercenary == null) continue;
                
                // Position mercenary near colony center
                BlockPos spawnPos = findMercenarySpawnPosition(colony.getCenter(), world);
                mercenary.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                
                // Make mercenary hostile to claiming player
                mercenary.setTarget(claimingPlayer);
                
                // Add effects
                mercenary.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 
                        TaxConfig.getClaimingRaidDurationMinutes() * 60 * 20, 2));
                mercenary.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 
                        TaxConfig.getClaimingRaidDurationMinutes() * 60 * 20, 1));
                
                world.addFreshEntity(mercenary);
                raidData.spawnedMercenaries.add(mercenary);
            }
            
            return mercenaryCount;
            
        } catch (Exception e) {
            LOGGER.error("Error spawning defending mercenaries for colony {}", colony.getID(), e);
            return 0;
        }
    }
    
    /**
     * Find a suitable spawn position for mercenaries near the colony center.
     */
    private static BlockPos findMercenarySpawnPosition(BlockPos center, Level world) {
        Random random = new Random();
        
        for (int attempts = 0; attempts < 10; attempts++) {
            int x = center.getX() + random.nextInt(20) - 10;
            int z = center.getZ() + random.nextInt(20) - 10;
            int y = world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 
                    new BlockPos(x, 0, z)).getY();
            
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (world.getBlockState(spawnPos).isAir() && !world.getBlockState(spawnPos.below()).isAir()) {
                return spawnPos;
            }
        }
        
        return center; // Fallback to colony center
    }
    
    /**
     * Create a boss bar to track the claiming raid progress.
     */
    private static void createRaidBossBar(ClaimingRaidData raidData, ServerPlayer claimingPlayer) {
        IColony colony = getColonyById(raidData.colonyId);
        if (colony == null) return;
        
        Component bossBarText = Component.literal("Claiming Colony: " + colony.getName())
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        
        raidData.bossEvent = new ServerBossEvent(bossBarText, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        raidData.bossEvent.addPlayer(claimingPlayer);
        
        // Note: Additional players can be added to boss bar during raid updates if needed
    }
    
    /**
     * Update all active claiming raids.
     * VICTORY CONDITION: Attackers can ONLY win by killing ALL defenders.
     * Timer expiration always results in failure.
     */
    public static void updateClaimingRaids() {
        Iterator<Map.Entry<Integer, ClaimingRaidData>> iterator = activeClaimingRaids.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<Integer, ClaimingRaidData> entry = iterator.next();
            ClaimingRaidData raidData = entry.getValue();
            
            // CRITICAL: Timer expiration is ALWAYS a failure - attackers must kill all defenders to win
            if (raidData.isExpired()) {
                endClaimingRaid(raidData, "Time expired - defenders successfully held the colony!");
                iterator.remove();
            } else {
                updateRaidBossBar(raidData);
                checkRaidConditions(raidData);
            }
        }
    }
    
    /**
     * Update the boss bar for a claiming raid.
     * Shows defender count to emphasize that ALL must be killed to win.
     */
    private static void updateRaidBossBar(ClaimingRaidData raidData) {
        if (raidData.bossEvent == null) return;
        
        long remaining = raidData.getRemainingTime();
        long total = TaxConfig.getClaimingRaidDurationMinutes() * 60 * 1000;
        float progress = (float) remaining / total;
        
        raidData.bossEvent.setProgress(Math.max(0.0f, progress));
        
        int minutes = (int) (remaining / 60000);
        int seconds = (int) ((remaining % 60000) / 1000);
        
        IColony colony = getColonyById(raidData.colonyId);
        String colonyName = colony != null ? colony.getName() : "Unknown";
        
        // Count remaining defenders to show progress
        int aliveDefenderCount = 0;
        if (colony != null) {
            // Count living citizens
            for (Integer citizenId : raidData.hostileCitizens) {
                ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                if (citizenData != null && citizenData.getEntity().isPresent() && 
                    citizenData.getEntity().get().isAlive()) {
                    aliveDefenderCount++;
                }
            }
            
            // Count living mercenaries
            for (Entity mercenary : raidData.spawnedMercenaries) {
                if (mercenary.isAlive()) {
                    aliveDefenderCount++;
                }
            }
        }
        
        Component newText = Component.literal("Claiming " + colonyName + " - Defenders: " + aliveDefenderCount + 
                                            " - Time: " + String.format("%02d:%02d", minutes, seconds))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        
        raidData.bossEvent.setName(newText);
    }
    
    /**
     * Check if the raid conditions have been met.
     * VICTORY CONDITION: Attackers can ONLY win by killing ALL defenders - no other victory conditions exist!
     */
    private static void checkRaidConditions(ClaimingRaidData raidData) {
        try {
            ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
            IColony colony = getColonyById(raidData.colonyId);
            
            if (claimingPlayer == null || colony == null) {
                endClaimingRaid(raidData, "Player or colony not found");
                return;
            }
            
            // Check if claiming player is still in the area - they must stay and fight!
            double distance = claimingPlayer.distanceToSqr(raidData.colonyCenter.getX(), 
                    raidData.colonyCenter.getY(), raidData.colonyCenter.getZ());
            if (distance > 100 * 100) { // 100 block radius
                endClaimingRaid(raidData, "Claiming player left the area - defenders win!");
                return;
            }
            
            // CRITICAL CHECK: Count all living defenders - attackers must kill EVERY SINGLE ONE
            int aliveDefenderCount = 0;
            
            // Count living citizens that were converted to militia
            for (Integer citizenId : raidData.hostileCitizens) {
                ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                if (citizenData != null && citizenData.getEntity().isPresent() && 
                    citizenData.getEntity().get().isAlive()) {
                    aliveDefenderCount++;
                }
            }
            
            // Count living mercenaries
            for (Entity mercenary : raidData.spawnedMercenaries) {
                if (mercenary.isAlive()) {
                    aliveDefenderCount++;
                }
            }
            
            // VICTORY CONDITION: All defenders must be dead - no shortcuts, no idle victories!
            if (aliveDefenderCount == 0) {
                LOGGER.info("CLAIMING RAID VICTORY: All {} defenders eliminated in colony {} by {}", 
                    raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size(),
                    colony.getName(), 
                    claimingPlayer.getName().getString());
                completeClaimingRaid(raidData, true);
            } else {
                // Log remaining defenders for transparency
                LOGGER.debug("Claiming raid progress - {} defenders remaining in colony {}", 
                    aliveDefenderCount, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Error checking raid conditions for colony {}", raidData.colonyId, e);
        }
    }
    
    /**
     * End a claiming raid without success.
     */
    public static void endClaimingRaid(ClaimingRaidData raidData, String reason) {
        try {
            IColony colony = getColonyById(raidData.colonyId);
            ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
            
            // Remove boss bar
            if (raidData.bossEvent != null) {
                raidData.bossEvent.removeAllPlayers();
            }
            
            // Clean up hostile effects from citizens
            if (colony != null) {
                for (Integer citizenId : raidData.hostileCitizens) {
                    ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                    if (citizenData != null && citizenData.getEntity().isPresent()) {
                        AbstractEntityCitizen citizen = citizenData.getEntity().get();
                        citizen.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                        citizen.removeEffect(MobEffects.MOVEMENT_SPEED);
                        citizen.removeEffect(MobEffects.DAMAGE_BOOST);
                        citizen.setTarget(null);
                    }
                }
            }
            
            // Remove mercenaries
            for (Entity mercenary : raidData.spawnedMercenaries) {
                if (mercenary.isAlive()) {
                    mercenary.remove(Entity.RemovalReason.DISCARDED);
                }
            }
            
            // Clean up militia system for this colony
            if (colony != null) {
                net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                        .clearColonyMilitia(colony.getID());
                
                // Clean up permissions (remove claiming permissions)
                setClaimingInteractionPermissions(colony, false);
            }
            
            // Notify about failure
            Component failureMessage = Component.literal("COLONY CLAIMING RAID FAILED")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                    .append(Component.literal("\nReason: " + reason)
                           .withStyle(ChatFormatting.RED));
            
            if (colony != null) {
                broadcastToNearbyPlayers(colony, failureMessage, 200);
            }
            
            if (claimingPlayer != null) {
                claimingPlayer.sendSystemMessage(Component.literal("Colony claiming failed: " + reason)
                        .withStyle(ChatFormatting.RED));
            }
            
            LOGGER.info("Claiming raid for colony {} ended unsuccessfully: {}", raidData.colonyId, reason);
            
        } catch (Exception e) {
            LOGGER.error("Error ending claiming raid for colony {}", raidData.colonyId, e);
        }
    }
    
    /**
     * Complete a claiming raid successfully.
     */
    private static void completeClaimingRaid(ClaimingRaidData raidData, boolean success) {
        try {
            IColony colony = getColonyById(raidData.colonyId);
            ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
            
            if (colony == null || claimingPlayer == null) {
                endClaimingRaid(raidData, "Colony or player not found");
                return;
            }
            
            // Remove boss bar
            if (raidData.bossEvent != null) {
                raidData.bossEvent.removeAllPlayers();
            }
            
            // Determine rank and messaging based on whether this is a former owner/officer
            boolean isFormerMember = ColonyAbandonmentManager.wasFormerOwnerOrOfficer(colony.getID(), claimingPlayer.getUUID());
            Rank newRank = colony.getPermissions().getRankOfficer(); // Default to officer
            
            if (isFormerMember) {
                // Former owners get officer rank initially, but can work toward ownership again
                newRank = colony.getPermissions().getRankOfficer();
                LOGGER.info("RECLAIMED: Former owner/officer {} has reclaimed colony {} and set as Officer", 
                    claimingPlayer.getName().getString(), colony.getName());
            }
            
            // Add claimer with appropriate rank
            colony.getPermissions().addPlayer(claimingPlayer.getUUID(), claimingPlayer.getName().getString(), newRank);
            
            // Mark colony as no longer abandoned
            ColonyAbandonmentManager.markColonyAsClaimed(colony.getID());
            
            // Clean up effects and hostility
            for (Integer citizenId : raidData.hostileCitizens) {
                ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                if (citizenData != null && citizenData.getEntity().isPresent()) {
                    AbstractEntityCitizen citizen = citizenData.getEntity().get();
                    citizen.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                    citizen.removeEffect(MobEffects.MOVEMENT_SPEED);
                    citizen.removeEffect(MobEffects.DAMAGE_BOOST);
                    citizen.setTarget(null);
                }
            }
            
            // Remove any remaining mercenaries
            for (Entity mercenary : raidData.spawnedMercenaries) {
                if (mercenary.isAlive()) {
                    mercenary.remove(Entity.RemovalReason.DISCARDED);
                }
            }
            
            // Clean up permissions (remove claiming permissions)
            setClaimingInteractionPermissions(colony, false);
            
            // Clean up militia system for this colony
            net.machiavelli.minecolonytax.militia.CitizenMilitiaManager.getInstance()
                    .clearColonyMilitia(colony.getID());
            
            // Broadcast success with different messages for former owners vs new claimers
            Component successMessage;
            Component personalMessage;
            
            if (isFormerMember) {
                // Former owner/officer reclaimed their colony
                successMessage = Component.literal("COLONY RECLAIMED!")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("\n" + claimingPlayer.getName().getString() + " has reclaimed their former colony ")
                               .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(colony.getName() + " and is now an Officer!")
                               .withStyle(ChatFormatting.GOLD));
                
                personalMessage = Component.literal("Welcome back! You have successfully reclaimed your former colony " + 
                        colony.getName() + " and are now an Officer! Rebuild your colony to earn back full ownership.")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            } else {
                // New claimer
                successMessage = Component.literal("COLONY CLAIMED SUCCESSFULLY!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("\n" + claimingPlayer.getName().getString() + " has successfully claimed the abandoned colony of ")
                               .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(colony.getName() + " and is now an Officer!")
                               .withStyle(ChatFormatting.GOLD));
                
                personalMessage = Component.literal("Congratulations! You have successfully claimed the colony of " + 
                        colony.getName() + " and are now an Officer! Work with the remaining citizens to rebuild this colony.")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            }
            
            broadcastToNearbyPlayers(colony, successMessage, 200);
            claimingPlayer.sendSystemMessage(personalMessage);
            
            LOGGER.info("Player {} successfully claimed colony {} ({})", 
                       claimingPlayer.getName().getString(), colony.getName(), colony.getID());
            
        } catch (Exception e) {
            LOGGER.error("Error completing claiming raid for colony {}", raidData.colonyId, e);
        } finally {
            activeClaimingRaids.remove(raidData.colonyId);
        }
    }
    
    /**
     * Get a player's colony.
     */
    private static IColony getPlayerColony(ServerPlayer player) {
        try {
            return IColonyManager.getInstance().getIColonyByOwner(player.level(), player);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get a colony by ID.
     */
    private static IColony getColonyById(int colonyId) {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            return colonyManager.getColonyByWorld(colonyId, null);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get a player by UUID.
     */
    private static ServerPlayer getPlayerById(UUID playerId) {
        try {
            // This would need access to the server instance
            // For now, we'll return null and handle it gracefully
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Broadcast a message to all players near a colony.
     */
    private static void broadcastToNearbyPlayers(IColony colony, Component message, double radius) {
        try {
            Level world = colony.getWorld();
            if (world instanceof ServerLevel serverLevel) {
                BlockPos center = colony.getCenter();
                
                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                    if (player.level() == world) {
                        double distance = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
                        if (distance <= radius * radius) {
                            player.sendSystemMessage(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error broadcasting message near colony {}", colony.getID(), e);
        }
    }
    
    /**
     * Check if a colony is currently under a claiming raid.
     */
    public static boolean isColonyUnderClaimingRaid(int colonyId) {
        return activeClaimingRaids.containsKey(colonyId);
    }
    
    /**
     * Get the claiming raid data for a colony.
     */
    public static ClaimingRaidData getClaimingRaid(int colonyId) {
        return activeClaimingRaids.get(colonyId);
    }
    
    /**
     * End all active claiming raids (for server shutdown, etc.)
     */
    public static void endAllClaimingRaids() {
        for (ClaimingRaidData raidData : activeClaimingRaids.values()) {
            endClaimingRaid(raidData, "Server shutdown");
        }
        activeClaimingRaids.clear();
    }
    
    /**
     * Check if a player is currently in a claiming raid for a specific colony.
     */
    public static boolean isPlayerInClaimingRaid(UUID playerId, int colonyId) {
        ClaimingRaidData raidData = activeClaimingRaids.get(colonyId);
        return raidData != null && raidData.claimingPlayerId.equals(playerId) && 
               (System.currentTimeMillis() < raidData.endTime);
    }
    
    /**
     * Get all active claiming raid colony IDs.
     */
    public static Set<Integer> getActiveClaimingRaidIds() {
        return new HashSet<>(activeClaimingRaids.keySet());
    }
    
    /**
     * Check if a player can claim an abandoned colony.
     */
    public static boolean canPlayerClaimColony(ServerPlayer player) {
        return canPlayerClaimColony(player, null);
    }
    
    /**
     * Check if a player can claim a specific abandoned colony.
     */
    public static boolean canPlayerClaimColony(ServerPlayer player, IColony targetColony) {
        ClaimingRequirementResult result = checkClaimingRequirements(player, targetColony);
        return result.canClaim;
    }
    
    /**
     * Check if a player meets the claiming requirements and return detailed info.
     */
    public static ClaimingRequirementResult checkClaimingRequirements(ServerPlayer player) {
        return checkClaimingRequirements(player, null);
    }
    
    /**
     * Check if a player meets the claiming requirements for a specific colony and return detailed info.
     */
    public static ClaimingRequirementResult checkClaimingRequirements(ServerPlayer player, IColony targetColony) {
        UUID playerId = player.getUUID();
        
        // Check if this player was a former owner/officer of this specific colony
        boolean isFormerOwnerOrOfficer = false;
        if (targetColony != null) {
            isFormerOwnerOrOfficer = ColonyAbandonmentManager.wasFormerOwnerOrOfficer(targetColony.getID(), playerId);
        }
        
        if (isFormerOwnerOrOfficer) {
            LOGGER.info("CLAIMING BYPASS: Player {} was former owner/officer of colony {} - bypassing requirements but must complete raid", 
                player.getName().getString(), targetColony != null ? targetColony.getName() : "unknown");
            return new ClaimingRequirementResult(true, "Former owner/officer of this colony - requirements bypassed! You must still complete the claiming raid to reclaim control.");
        }
        
        IColony playerColony = getPlayerColony(player);
        if (playerColony == null) {
            return new ClaimingRequirementResult(false, "You must own a colony to claim abandoned colonies.");
        }
        
        // Check guard requirement
        int guardCount = WarSystem.countGuards(playerColony);
        int requiredGuards = TaxConfig.getMinGuardsForClaimingRaid();
        if (guardCount < requiredGuards) {
            return new ClaimingRequirementResult(false, 
                    "You need at least " + requiredGuards + " guards (you have " + guardCount + ").");
        }
        
        // Check building requirements using the new manager
        net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult buildingCheck = 
                net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.checkClaimingRequirements(playerColony);
        
        if (!buildingCheck.meetsRequirements) {
            return new ClaimingRequirementResult(false, buildingCheck.message);
        }
        
        return new ClaimingRequirementResult(true, "All requirements met!");
    }
    
    /**
     * Set colony interaction permissions for claiming raids.
     * Similar to raid permissions but uses configurable claiming actions.
     */
    public static void setClaimingInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.isAbandonedColonyClaimingEnabled()) {
            return;
        }
        
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        
        // Apply claiming actions from config
        Set<Action> claimingActions = TaxConfig.getClaimingActions();
        for (Action action : claimingActions) {
            perms.setPermission(hostile, action, allowed);
        }
        
        LOGGER.info("Set claiming interaction permissions for colony {} to: {}", colony.getName(), allowed);
    }
    
    /**
     * Result class for claiming requirement checks.
     */
    public static class ClaimingRequirementResult {
        public final boolean canClaim;
        public final String message;
        
        public ClaimingRequirementResult(boolean canClaim, String message) {
            this.canClaim = canClaim;
            this.message = message;
        }
    }
}
