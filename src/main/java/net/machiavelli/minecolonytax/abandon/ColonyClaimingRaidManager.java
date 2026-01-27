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
    
    // Track claiming grace periods per player (UUID -> timestamp of last successful claim)
    private static final Map<UUID, Long> claimingGracePeriods = new ConcurrentHashMap<>();
    
    // Track protected colonies that cannot be claimed (colony ID -> admin who protected it)
    private static final Map<Integer, String> protectedColonies = new ConcurrentHashMap<>();
    
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
            LOGGER.info("Creating boss bar for claiming raid in colony {} for player {}", 
                colony.getName(), claimingPlayer.getName().getString());
            createRaidBossBar(raidData, claimingPlayer);
            
            // Verify boss bar was created
            if (raidData.bossEvent != null) {
                LOGGER.info("Boss bar successfully created for claiming raid");
            } else {
                LOGGER.error("Failed to create boss bar for claiming raid");
            }
            
            // Enable claiming permissions without adding player to colony permissions
            // The player will be added as Officer only upon successful completion
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
            
            // Send detailed message to claiming player
            claimingPlayer.sendSystemMessage(startMessage);
            
            // Send brief notification to nearby players (smaller radius)
            Component nearbyMessage = Component.literal("⚔ CLAIMING RAID STARTED ⚔")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("\n" + claimingPlayer.getName().getString() + " is claiming " + colony.getName())
                           .withStyle(ChatFormatting.YELLOW));
            
            broadcastToNearbyPlayers(colony, nearbyMessage, 100); // Reduced radius
            
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
     * Create a boss bar to track the claiming raid progress with timer and defender count.
     * ENHANCED VERSION: More robust boss bar creation with better error handling.
     */
    private static void createRaidBossBar(ClaimingRaidData raidData, ServerPlayer claimingPlayer) {
        if (raidData == null) {
            LOGGER.error("Cannot create boss bar - raid data is null");
            return;
        }
        
        if (claimingPlayer == null) {
            LOGGER.error("Cannot create boss bar - claiming player is null");
            return;
        }
        
        IColony colony = getColonyById(raidData.colonyId);
        if (colony == null) {
            LOGGER.error("Cannot create boss bar - colony {} not found", raidData.colonyId);
            return;
        }
        
        try {
            // Clean up any existing boss bar
            if (raidData.bossEvent != null) {
                LOGGER.debug("Cleaning up existing boss bar before creating new one");
                try {
                    raidData.bossEvent.removeAllPlayers();
                } catch (Exception e) {
                    LOGGER.debug("Failed to clean up existing boss bar: {}", e.getMessage());
                }
                raidData.bossEvent = null;
            }
            
            // Create compact boss bar text - just defender count and time
            int totalDefenders = raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size();
            int minutes = TaxConfig.getClaimingRaidDurationMinutes();
            
            Component bossBarText = Component.literal(String.format("⚔️ Defenders: %d | %02d:%02d", 
                    totalDefenders, minutes, 0))
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            
            raidData.bossEvent = new ServerBossEvent(bossBarText, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            
            // Set initial progress to full (represents time remaining)
            raidData.bossEvent.setProgress(1.0f);
            
            // Add the claiming player
            try {
                raidData.bossEvent.addPlayer(claimingPlayer);
                LOGGER.info("BOSS BAR CREATED: Player {} added to boss bar for colony {} ({} defenders, {} minute timer)", 
                    claimingPlayer.getName().getString(), colony.getName(), totalDefenders, minutes);
            } catch (Exception e) {
                LOGGER.error("Failed to add claiming player to boss bar: {}", e.getMessage());
                return;
            }
            
            // Add nearby players to the boss bar as well
            Level world = colony.getWorld();
            if (world instanceof ServerLevel serverLevel) {
                try {
                    BlockPos center = colony.getCenter();
                    int playersAdded = 0;
                    
                    for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                        if (player.level() == world && !player.equals(claimingPlayer)) {
                            double distance = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
                            if (distance <= 100 * 100) { // 100 block radius
                                try {
                                    raidData.bossEvent.addPlayer(player);
                                    playersAdded++;
                                    LOGGER.debug("Added nearby player {} to claiming raid boss bar", player.getName().getString());
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to add nearby player {} to boss bar: {}", player.getName().getString(), e.getMessage());
                                }
                            }
                        }
                    }
                    
                    LOGGER.info("BOSS BAR SETUP: Added {} nearby players to boss bar", playersAdded);
                } catch (Exception e) {
                    LOGGER.error("Error adding nearby players to boss bar: {}", e.getMessage());
                }
            }
            
            // Force an immediate update to ensure the boss bar shows correctly with current time
            try {
                updateRaidBossBar(raidData);
                LOGGER.info("BOSS BAR INITIALIZED: Successfully created and updated boss bar for colony {}", colony.getName());
            } catch (Exception e) {
                LOGGER.error("Failed to perform initial boss bar update: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("BOSS BAR CREATION FAILED: Error creating boss bar for claiming raid in colony {}", 
                colony != null ? colony.getName() : "unknown", e);
            
            // Ensure boss bar is null if creation failed
            if (raidData != null) {
                raidData.bossEvent = null;
            }
        }
    }
    
    /**
     * Update all active claiming raids.
     * VICTORY CONDITION: Attackers can ONLY win by killing ALL defenders.
     * Timer expiration always results in failure.
     */
    public static void updateClaimingRaids() {
        // Clean up old grace periods periodically
        cleanupOldGracePeriods();
        
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
     * Shows time as a countdown timer and defender count for clear victory conditions.
     */
    private static void updateRaidBossBar(ClaimingRaidData raidData) {
        if (raidData.bossEvent == null) {
            LOGGER.warn("Boss bar is null for claiming raid in colony {} - attempting to recreate", raidData.colonyId);
            
            // Try to recreate the boss bar
            ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
            if (claimingPlayer != null) {
                createRaidBossBar(raidData, claimingPlayer);
            }
            return;
        }
        
        // Ensure the claiming player is still on the boss bar
        ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
        if (claimingPlayer != null) {
            if (!raidData.bossEvent.getPlayers().contains(claimingPlayer)) {
                raidData.bossEvent.addPlayer(claimingPlayer);
                LOGGER.debug("Re-added claiming player {} to boss bar", claimingPlayer.getName().getString());
            }
        } else {
            LOGGER.debug("Claiming player not found for boss bar update in colony {}", raidData.colonyId);
        }
        
        // TIMER DISPLAY: Show time remaining as countdown
        long remaining = raidData.getRemainingTime();
        long total = TaxConfig.getClaimingRaidDurationMinutes() * 60 * 1000;
        float progress = (float) remaining / total;
        
        // Progress bar represents TIME REMAINING (full = lots of time, empty = time almost up)
        raidData.bossEvent.setProgress(Math.max(0.0f, progress));
        
        int minutes = (int) (remaining / 60000);
        int seconds = (int) ((remaining % 60000) / 1000);
        
        IColony colony = getColonyById(raidData.colonyId);
        String colonyName = colony != null ? colony.getName() : "Unknown";
        
        // Count remaining defenders to show progress
        int aliveCitizenCount = 0;
        int aliveMercenaryCount = 0;
        
        if (colony != null) {
            // Count living citizens
            for (Integer citizenId : raidData.hostileCitizens) {
                ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                if (citizenData != null && citizenData.getEntity().isPresent() && 
                    citizenData.getEntity().get().isAlive()) {
                    aliveCitizenCount++;
                }
            }
            
            // Count living mercenaries
            for (Entity mercenary : raidData.spawnedMercenaries) {
                if (mercenary.isAlive()) {
                    aliveMercenaryCount++;
                }
            }
        }
        
        int totalAliveDefenders = aliveCitizenCount + aliveMercenaryCount;
        
        // COMPACT BOSS BAR: Just defenders left and time
        String bossBarText;
        
        if (totalAliveDefenders == 0) {
            bossBarText = "🎉 VICTORY! - All Defenders Eliminated";
        } else {
            bossBarText = String.format("⚔️ Defenders: %d | %02d:%02d", totalAliveDefenders, minutes, seconds);
        }
        
        // Color coding based on situation
        ChatFormatting textColor;
        if (totalAliveDefenders == 0) {
            textColor = ChatFormatting.GREEN; // Victory
        } else if (remaining <= 60000) {
            textColor = ChatFormatting.DARK_RED; // Critical time
        } else if (remaining <= 300000) {
            textColor = ChatFormatting.RED; // Low time
        } else {
            textColor = ChatFormatting.YELLOW; // Normal
        }
        
        Component newText = Component.literal(bossBarText)
                .withStyle(textColor, ChatFormatting.BOLD);
        
        raidData.bossEvent.setName(newText);
        
        // Change boss bar color based on time remaining
        if (remaining <= 60000) {
            raidData.bossEvent.setColor(BossEvent.BossBarColor.RED);
        } else if (remaining <= 300000) {
            raidData.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
        } else {
            raidData.bossEvent.setColor(BossEvent.BossBarColor.GREEN);
        }
    }
    
    /**
     * Check if the raid conditions have been met.
     * VICTORY CONDITION: Attackers can ONLY win by killing ALL defenders - no other victory conditions exist!
     */
    private static void checkRaidConditions(ClaimingRaidData raidData) {
        try {
            IColony colony = getColonyById(raidData.colonyId);
            if (colony == null) {
                LOGGER.error("Colony {} not found during raid condition check - ending raid", raidData.colonyId);
                endClaimingRaid(raidData, "Colony not found");
                return;
            }
            
            ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
            if (claimingPlayer == null) {
                // Player might be offline or in a different dimension - don't end the raid immediately
                // Just skip this update cycle and try again next time
                LOGGER.debug("Claiming player {} not found during raid condition check - skipping update", raidData.claimingPlayerId);
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
            Set<Integer> deadCitizens = new HashSet<>();
            Set<Entity> deadMercenaries = new HashSet<>();
            
            // Count living citizens that were converted to militia
            for (Integer citizenId : raidData.hostileCitizens) {
                ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                if (citizenData != null && citizenData.getEntity().isPresent() && 
                    citizenData.getEntity().get().isAlive()) {
                    aliveDefenderCount++;
                } else {
                    // Mark as dead for cleanup
                    deadCitizens.add(citizenId);
                }
            }
            
            // Count living mercenaries
            for (Entity mercenary : raidData.spawnedMercenaries) {
                if (mercenary.isAlive()) {
                    aliveDefenderCount++;
                } else {
                    // Mark as dead for cleanup
                    deadMercenaries.add(mercenary);
                }
            }
            
            // Clean up dead defenders from tracking (fallback mechanism)
            if (!deadCitizens.isEmpty() || !deadMercenaries.isEmpty()) {
                LOGGER.info("CLEANUP: Removing {} dead citizens and {} dead mercenaries from tracking in colony {}", 
                    deadCitizens.size(), deadMercenaries.size(), colony.getName());
                
                raidData.hostileCitizens.removeAll(deadCitizens);
                raidData.spawnedMercenaries.removeAll(deadMercenaries);
            }
            
            // VICTORY CONDITION: All defenders must be dead - no shortcuts, no idle victories!
            if (aliveDefenderCount == 0) {
                LOGGER.info("CLAIMING RAID VICTORY: All {} defenders eliminated in colony {} by {}", 
                    raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size(),
                    colony.getName(), 
                    claimingPlayer.getName().getString());
                
                // Remove from active raids immediately to prevent duplicate processing
                activeClaimingRaids.remove(raidData.colonyId);
                completeClaimingRaid(raidData, true);
                return; // Exit immediately after victory
            } else {
                // Debug logging (reduced frequency to prevent spam)
                LOGGER.debug("CLAIMING RAID PROGRESS - {} defenders remaining in colony {}", 
                    aliveDefenderCount, colony.getName());
                
                // Only log detailed defender info every 10 seconds to reduce spam
                if (System.currentTimeMillis() % 10000 < 1000) {
                    LOGGER.debug("Detailed defender status for colony {}:", colony.getName());
                    for (Integer citizenId : raidData.hostileCitizens) {
                        ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            boolean isAlive = citizenData.getEntity().get().isAlive();
                            LOGGER.debug("  - Citizen {} (ID: {}): {}", 
                                citizenData.getName(), citizenId, isAlive ? "ALIVE" : "DEAD");
                        } else {
                            LOGGER.debug("  - Citizen ID {}: NO ENTITY", citizenId);
                        }
                    }
                    
                    for (Entity mercenary : raidData.spawnedMercenaries) {
                        LOGGER.debug("  - Mercenary {}: {}", 
                            mercenary.getId(), mercenary.isAlive() ? "ALIVE" : "DEAD");
                    }
                }
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
                // Only broadcast significant failures, not routine cleanup
                if (!reason.equals("Colony not found") && !reason.equals("Player or colony not found")) {
                    broadcastToNearbyPlayers(colony, failureMessage, 100); // Reduced radius
                }
            }
            
            // Only send failure message to player if it's not a routine check failure
            if (claimingPlayer != null && !reason.equals("Player or colony not found") && !reason.equals("Colony not found")) {
                claimingPlayer.sendSystemMessage(Component.literal("Colony claiming failed: " + reason)
                        .withStyle(ChatFormatting.RED));
            }
            
            LOGGER.info("Claiming raid for colony {} ended unsuccessfully: {}", raidData.colonyId, reason);
            
        } catch (Exception e) {
            LOGGER.error("Error ending claiming raid for colony {}", raidData.colonyId, e);
        } finally {
            // CRITICAL: Always remove the raid from active raids to prevent spam
            activeClaimingRaids.remove(raidData.colonyId);
        }
    }
    
    /**
     * Complete a claiming raid successfully.
     * CRITICAL: This method ensures NO COLONY CORRUPTION by preserving all players and only changing ranks.
     */
    public static void completeClaimingRaid(ClaimingRaidData raidData, boolean success) {
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
            
            // CRITICAL: COLONY CORRUPTION PREVENTION + SYSTEM OWNER CLEANUP
            // We NEVER remove players from colonies - only change their ranks
            IPermissions permissions = colony.getPermissions();
            
            LOGGER.info("COLONY CORRUPTION PREVENTION: Safely updating permissions for colony {} without removing any players", colony.getName());
            
            // STEP 0: Clean up system owner from abandoned state
            UUID systemOwnerUUID = ColonyAbandonmentManager.createSystemOwner();
            if (permissions.getPlayers().containsKey(systemOwnerUUID)) {
                try {
                    permissions.removePlayer(systemOwnerUUID);
                    LOGGER.info("CLEANUP: Removed system owner from claimed colony {}", colony.getName());
                } catch (Exception e) {
                    LOGGER.error("Failed to remove system owner from colony {}: {}", colony.getName(), e.getMessage());
                }
            }
            
            // Determine rank and messaging based on whether this is a former owner/officer
            boolean isFormerMember = ColonyAbandonmentManager.wasFormerOwnerOrOfficer(colony.getID(), claimingPlayer.getUUID());
            Rank officerRank = permissions.getRankOfficer();
            
            // STEP 1: Set claimer as OWNER (they've earned it by completing the raid!)
            boolean wasAlreadyInColony = permissions.getPlayers().containsKey(claimingPlayer.getUUID());
            if (wasAlreadyInColony) {
                // Player was already in colony - promote to Owner
                permissions.setPlayerRank(claimingPlayer.getUUID(), permissions.getRankOwner(), colony.getWorld());
                LOGGER.info("CLAIMING SUCCESS: Promoted existing player {} to OWNER of colony {}", 
                    claimingPlayer.getName().getString(), colony.getName());
            } else {
                // Player was not in colony - add them as Owner
                permissions.addPlayer(claimingPlayer.getUUID(), claimingPlayer.getName().getString(), permissions.getRankOwner());
                LOGGER.info("CLAIMING SUCCESS: Added new player {} as OWNER of colony {}", 
                    claimingPlayer.getName().getString(), colony.getName());
            }
            
            // CRITICAL: Set the claiming player as the actual owner to prevent GUI crashes
            try {
                java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
                setOwnerMethod.invoke(permissions, claimingPlayer.getUUID());
                LOGGER.info("🏛️ CLAIMING OWNER SET: {} is now the actual owner of claimed colony {}", 
                    claimingPlayer.getName().getString(), colony.getName());
            } catch (Exception e) {
                LOGGER.warn("Could not set claiming player as actual owner directly, trying alternative: {}", e.getMessage());
                try {
                    for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
                        if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
                            method.setAccessible(true);
                            method.invoke(permissions, claimingPlayer.getUUID());
                            LOGGER.info("🏛️ CLAIMING OWNER SET (alt): {} is now the actual owner of claimed colony {}", 
                                claimingPlayer.getName().getString(), colony.getName());
                            break;
                        }
                    }
                } catch (Exception e2) {
                    LOGGER.error("Failed to set claiming player as actual owner: {}", e2.getMessage());
                }
            }
            
            // STEP 2: Restore normal permissions for neutral players (they were restricted during abandonment)
            Rank neutralRank = permissions.getRankNeutral();
            
            LOGGER.info("Restoring normal neutral permissions for abandoned colony {}", colony.getName());
            
            // Restore basic interaction permissions for neutral players
            permissions.setPermission(neutralRank, Action.ACCESS_HUTS, true);
            permissions.setPermission(neutralRank, Action.RIGHTCLICK_BLOCK, true);
            permissions.setPermission(neutralRank, Action.OPEN_CONTAINER, true);
            permissions.setPermission(neutralRank, Action.PICKUP_ITEM, true);
            permissions.setPermission(neutralRank, Action.TOSS_ITEM, true);
            
            // Keep building restrictions to prevent griefing
            permissions.setPermission(neutralRank, Action.BREAK_BLOCKS, false);
            permissions.setPermission(neutralRank, Action.PLACE_BLOCKS, false);
            
            LOGGER.info("Colony {} permissions safely updated - claimer is Officer, all players preserved", colony.getName());
            
            if (isFormerMember) {
                LOGGER.info("RECLAIMED: Former owner/officer {} has reclaimed colony {} and set as Officer", 
                    claimingPlayer.getName().getString(), colony.getName());
            } else {
                LOGGER.info("CLAIMED: New claimer {} has claimed colony {} and set as Officer", 
                    claimingPlayer.getName().getString(), colony.getName());
            }
            
            // Mark colony as no longer abandoned
            ColonyAbandonmentManager.markColonyAsClaimed(colony.getID());
            
            // 🎯 CLEANUP: Remove attack permissions after successful claiming
            setClaimingInteractionPermissions(colony, false);
            
            // Clean up effects and hostility from citizens
            for (Integer citizenId : raidData.hostileCitizens) {
                ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
                if (citizenData != null && citizenData.getEntity().isPresent()) {
                    AbstractEntityCitizen citizen = citizenData.getEntity().get();
                    citizen.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                    citizen.removeEffect(MobEffects.MOVEMENT_SPEED);
                    citizen.removeEffect(MobEffects.DAMAGE_BOOST);
                    citizen.setTarget(null);
                    
                    // Clear any targeting goals that were added
                    citizen.targetSelector.removeAllGoals(goal -> true);
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
                               .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\nAll existing players remain with their current ranks.")
                               .withStyle(ChatFormatting.GRAY));
                
                personalMessage = Component.literal("Welcome back! You have successfully reclaimed your former colony " + 
                        colony.getName() + " and are now an Officer! All existing players remain in the colony. " +
                        "Work together to rebuild your colony.")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            } else {
                // New claimer
                successMessage = Component.literal("COLONY CLAIMED SUCCESSFULLY!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("\n" + claimingPlayer.getName().getString() + " has successfully claimed the abandoned colony of ")
                               .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(colony.getName() + " and is now an Officer!")
                               .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\nAll existing players remain with their current ranks.")
                               .withStyle(ChatFormatting.GRAY));
                
                personalMessage = Component.literal("Congratulations! You have successfully claimed the colony of " + 
                        colony.getName() + " and are now an Officer! All existing players remain in the colony. " +
                        "Work with the remaining citizens to rebuild this colony.")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            }
            
            broadcastToNearbyPlayers(colony, successMessage, 100);
            claimingPlayer.sendSystemMessage(personalMessage);
            
            // Set grace period for the claiming player
            claimingGracePeriods.put(claimingPlayer.getUUID(), System.currentTimeMillis());
            
            LOGGER.info("Player {} successfully claimed colony {} ({}) - grace period set for {} hours", 
                       claimingPlayer.getName().getString(), colony.getName(), colony.getID(), 
                       TaxConfig.getClaimingGracePeriodHours());
            
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
     * Get a colony by ID - FIXED VERSION that actually works.
     */
    private static IColony getColonyById(int colonyId) {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            
            // CRITICAL FIX: Search through all worlds instead of using null
            for (IColony colony : colonyManager.getAllColonies()) {
                if (colony.getID() == colonyId) {
                    LOGGER.debug("COLONY LOOKUP SUCCESS: Found colony {} (ID: {})", colony.getName(), colonyId);
                    return colony;
                }
            }
            
            LOGGER.error("COLONY LOOKUP FAILED: Colony with ID {} not found among {} total colonies", 
                colonyId, colonyManager.getAllColonies().size());
            return null;
        } catch (Exception e) {
            LOGGER.error("COLONY LOOKUP ERROR: Exception while finding colony {}: {}", colonyId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get a player by UUID - improved version with better server access.
     */
    private static ServerPlayer getPlayerById(UUID playerId) {
        if (playerId == null) return null;
        
        try {
            // Try to get server from any active colony
            for (ClaimingRaidData raidData : activeClaimingRaids.values()) {
                IColony colony = getColonyById(raidData.colonyId);
                if (colony != null && colony.getWorld() instanceof ServerLevel serverLevel) {
                    ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        return player;
                    }
                }
            }
            
            // Fallback: try from colony manager
            try {
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                if (colonyManager != null) {
                    // Get any colony to access the server
                    for (IColony colony : colonyManager.getAllColonies()) {
                        if (colony.getWorld() instanceof ServerLevel serverLevel) {
                            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) {
                                return player;
                            }
                            break; // Only need one server instance
                        }
                    }
                }
            } catch (Exception fallbackException) {
                LOGGER.debug("Fallback player lookup failed for UUID {}", playerId);
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.debug("Error getting player by UUID {} - player might be offline", playerId);
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
     * Force check victory conditions for a specific colony (can be called externally).
     */
    public static void forceCheckVictoryCondition(int colonyId) {
        ClaimingRaidData raidData = activeClaimingRaids.get(colonyId);
        if (raidData != null) {
            LOGGER.info("FORCE CHECKING victory condition for colony {}", colonyId);
            checkRaidConditions(raidData);
        }
    }
    
    /**
     * Force refresh boss bar for a claiming raid (can be called externally).
     */
    public static void forceRefreshBossBar(int colonyId) {
        ClaimingRaidData raidData = activeClaimingRaids.get(colonyId);
        if (raidData != null) {
            LOGGER.info("FORCE REFRESHING boss bar for colony {}", colonyId);
            
            ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
            if (claimingPlayer == null) {
                LOGGER.warn("Cannot refresh boss bar - claiming player not found");
                return;
            }
            
            // Always recreate the boss bar to ensure it works
            if (raidData.bossEvent != null) {
                raidData.bossEvent.removeAllPlayers();
            }
            
            createRaidBossBar(raidData, claimingPlayer);
            LOGGER.info("Recreated boss bar for claiming raid in colony {}", colonyId);
        } else {
            LOGGER.warn("No active claiming raid found for colony {}", colonyId);
        }
    }
    
    /**
     * Debug method to check claiming raid status.
     */
    public static void debugClaimingRaid(int colonyId) {
        ClaimingRaidData raidData = activeClaimingRaids.get(colonyId);
        if (raidData != null) {
            LOGGER.info("=== CLAIMING RAID DEBUG for Colony {} ===", colonyId);
            LOGGER.info("Claiming Player: {}", raidData.claimingPlayerId);
            LOGGER.info("Hostile Citizens: {}", raidData.hostileCitizens.size());
            LOGGER.info("Spawned Mercenaries: {}", raidData.spawnedMercenaries.size());
            LOGGER.info("Boss Event: {}", raidData.bossEvent != null ? "EXISTS" : "NULL");
            LOGGER.info("Time Remaining: {} ms", raidData.getRemainingTime());
            
            if (raidData.bossEvent != null) {
                LOGGER.info("Boss Bar Players: {}", raidData.bossEvent.getPlayers().size());
            }
        } else {
            LOGGER.info("No active claiming raid for colony {}", colonyId);
        }
    }
    
    /**
     * Mark a colony as protected from claiming.
     * @param colonyId The colony ID to protect
     * @param adminName The name of the admin who protected it
     */
    public static void protectColony(int colonyId, String adminName) {
        protectedColonies.put(colonyId, adminName);
        LOGGER.info("Colony {} marked as protected from claiming by admin {}", colonyId, adminName);
    }
    
    /**
     * Remove protection from a colony, making it claimable again.
     * @param colonyId The colony ID to unprotect
     */
    public static void unprotectColony(int colonyId) {
        String adminName = protectedColonies.remove(colonyId);
        if (adminName != null) {
            LOGGER.info("Colony {} protection removed (was protected by {})", colonyId, adminName);
        }
    }
    
    /**
     * Check if a colony is protected from claiming.
     * @param colonyId The colony ID to check
     * @return true if the colony is protected, false otherwise
     */
    public static boolean isColonyProtected(int colonyId) {
        return protectedColonies.containsKey(colonyId);
    }
    
    /**
     * Get the admin who protected a colony.
     * @param colonyId The colony ID
     * @return The admin name, or null if not protected
     */
    public static String getProtectedBy(int colonyId) {
        return protectedColonies.get(colonyId);
    }
    
    /**
     * Get all protected colonies.
     * @return Map of colony ID to admin name
     */
    public static Map<Integer, String> getProtectedColonies() {
        return new HashMap<>(protectedColonies);
    }
    
    /**
     * Clean up old grace periods to prevent memory leaks.
     */
    public static void cleanupOldGracePeriods() {
        long gracePeriodMs = TaxConfig.getClaimingGracePeriodHours() * 60 * 60 * 1000L;
        long currentTime = System.currentTimeMillis();
        
        claimingGracePeriods.entrySet().removeIf(entry -> {
            long timeSinceLastClaim = currentTime - entry.getValue();
            return timeSinceLastClaim > gracePeriodMs;
        });
    }
    
    /**
     * Get the remaining grace period for a player in milliseconds.
     * @param playerId The player's UUID
     * @return Remaining grace period in milliseconds, or 0 if no grace period
     */
    public static long getRemainingGracePeriod(UUID playerId) {
        Long lastClaimTime = claimingGracePeriods.get(playerId);
        if (lastClaimTime == null) {
            return 0;
        }
        
        long gracePeriodMs = TaxConfig.getClaimingGracePeriodHours() * 60 * 60 * 1000L;
        long timeSinceLastClaim = System.currentTimeMillis() - lastClaimTime;
        
        return Math.max(0, gracePeriodMs - timeSinceLastClaim);
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
        
        // Check grace period (applies to everyone, including former owners)
        Long lastClaimTime = claimingGracePeriods.get(playerId);
        if (lastClaimTime != null) {
            long gracePeriodMs = TaxConfig.getClaimingGracePeriodHours() * 60 * 60 * 1000L;
            long timeSinceLastClaim = System.currentTimeMillis() - lastClaimTime;
            
            if (timeSinceLastClaim < gracePeriodMs) {
                long remainingMs = gracePeriodMs - timeSinceLastClaim;
                long remainingHours = remainingMs / (60 * 60 * 1000);
                long remainingMinutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000);
                
                String timeRemaining = remainingHours > 0 ? 
                    remainingHours + "h " + remainingMinutes + "m" : 
                    remainingMinutes + "m";
                
                return new ClaimingRequirementResult(false, 
                    "You must wait " + timeRemaining + " before claiming another colony (24-hour cooldown).");
            }
        }
        
        // Check if colony is protected from claiming
        if (targetColony != null && isColonyProtected(targetColony.getID())) {
            String protectedBy = getProtectedBy(targetColony.getID());
            return new ClaimingRequirementResult(false, 
                "This colony is protected from claiming by admin " + protectedBy + ". Contact an administrator for assistance.");
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
     * Handles both neutral and hostile ranks + attack permissions.
     */
    public static void setClaimingInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.isAbandonedColonyClaimingEnabled()) {
            return;
        }
        
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        Rank neutral = perms.getRankNeutral();
        
        // Apply claiming actions from config to hostile rank
        Set<Action> claimingActions = TaxConfig.getClaimingActions();
        for (Action action : claimingActions) {
            perms.setPermission(hostile, action, allowed);
        }
        
        // 🎯 CRITICAL: Also handle attack permissions for neutral rank during raids
        if (allowed) {
            // Grant attack permissions during raid
            perms.setPermission(neutral, Action.HURT_CITIZEN, true);
            perms.setPermission(neutral, Action.ATTACK_CITIZEN, true);
            // GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
            perms.setPermission(neutral, Action.HURT_VISITOR, true);
            perms.setPermission(neutral, Action.ATTACK_ENTITY, true);
            perms.setPermission(neutral, Action.SHOOT_ARROW, true);
            perms.setPermission(neutral, Action.THROW_POTION, true);
            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, true);
            perms.setPermission(neutral, Action.FILL_BUCKET, true);
        } else {
            // Revoke attack permissions after raid
            perms.setPermission(neutral, Action.HURT_CITIZEN, false);
            perms.setPermission(neutral, Action.ATTACK_CITIZEN, false);
            // GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
            perms.setPermission(neutral, Action.HURT_VISITOR, false);
            perms.setPermission(neutral, Action.ATTACK_ENTITY, false);
            perms.setPermission(neutral, Action.SHOOT_ARROW, false);
            perms.setPermission(neutral, Action.THROW_POTION, false);
            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, false);
            perms.setPermission(neutral, Action.FILL_BUCKET, false);
        }
        
        LOGGER.info("Set claiming raid permissions for colony {} to: {} (includes attack permissions)", colony.getName(), allowed);
    }
    

    

    

    
    /**
     * Clean up all failed claiming raids (for emergency fix command).
     */
    public static void cleanupAllFailedRaids() {
        try {
            LOGGER.info("EMERGENCY CLEANUP: Starting cleanup of all failed claiming raids");
            
            // Make a copy to avoid concurrent modification
            Map<Integer, ClaimingRaidData> raidsCopy = new HashMap<>(activeClaimingRaids);
            int cleanedRaids = 0;
            
            for (Map.Entry<Integer, ClaimingRaidData> entry : raidsCopy.entrySet()) {
                int colonyId = entry.getKey();
                ClaimingRaidData raidData = entry.getValue();
                
                try {
                    // Check if this raid is problematic
                    IColony colony = getColonyById(colonyId);
                    ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
                    
                    boolean needsCleanup = false;
                    String reason = "";
                    
                    if (colony == null) {
                        needsCleanup = true;
                        reason = "colony not found";
                    } else if (claimingPlayer == null) {
                        needsCleanup = true;
                        reason = "claiming player offline";
                    } else if (raidData.bossEvent == null) {
                        needsCleanup = true;
                        reason = "boss bar is null";
                    } else if (System.currentTimeMillis() - raidData.startTime > (TaxConfig.getClaimingRaidDurationMinutes() * 60000L + 300000L)) {
                        needsCleanup = true;
                        reason = "raid overtime (+" + ((System.currentTimeMillis() - raidData.startTime) / 60000L) + " minutes)";
                    }
                    
                    if (needsCleanup) {
                        LOGGER.info("EMERGENCY CLEANUP: Ending failed raid for colony {} - {}", 
                            colony != null ? colony.getName() : "Unknown", reason);
                        
                        // Clean up boss bar
                        if (raidData.bossEvent != null) {
                            raidData.bossEvent.removeAllPlayers();
                        }
                        
                        // Clean up permissions
                        if (colony != null) {
                            setClaimingInteractionPermissions(colony, false);
                        }
                        
                        // Remove from active raids
                        activeClaimingRaids.remove(colonyId);
                        
                        cleanedRaids++;
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("EMERGENCY CLEANUP: Error cleaning raid for colony {}: {}", colonyId, e.getMessage());
                    // Force remove problematic raid
                    activeClaimingRaids.remove(colonyId);
                    cleanedRaids++;
                }
            }
            
            LOGGER.info("EMERGENCY CLEANUP: Cleaned up {} failed claiming raids", cleanedRaids);
            
        } catch (Exception e) {
            LOGGER.error("EMERGENCY CLEANUP: Error during cleanup of failed raids: {}", e.getMessage());
        }
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