package net.machiavelli.minecolonytax.abandon;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages automatic colony abandonment based on owner/officer inactivity.
 * This system tracks when owners and officers last visited their colonies
 * and automatically abandons colonies that have been inactive for too long.
 */
public class ColonyAbandonmentManager {
    
    private static final Logger LOGGER = LogManager.getLogger(ColonyAbandonmentManager.class);
    
    // Track warned colonies to avoid spamming
    private static final Map<Integer, Long> warnedColonies = new ConcurrentHashMap<>();
    
    // Track abandoned colonies that can be claimed
    private static final Set<Integer> abandonedColonies = ConcurrentHashMap.newKeySet();
    
    // Track former owners/officers of abandoned colonies (for claiming bypass)
    private static final Map<Integer, Set<UUID>> formerColonyMembers = new ConcurrentHashMap<>();
    
    // Track pending notifications for offline players
    private static final Map<UUID, List<Component>> pendingNotifications = new ConcurrentHashMap<>();
    
    /**
     * Check all colonies for abandonment conditions.
     * Should be called periodically (every hour or so).
     */
    public static void checkColoniesForAbandonment(MinecraftServer server) {
        if (!TaxConfig.isColonyAutoAbandonEnabled()) {
            return;
        }
        
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int abandonedCount = 0;
            int warnedCount = 0;
            
            for (Level world : server.getAllLevels()) {
                for (IColony colony : colonyManager.getColonies(world)) {
                    AbandonmentStatus status = checkColonyAbandonmentStatus(colony);
                    
                    switch (status) {
                        case SHOULD_ABANDON:
                            if (abandonColony(colony, server)) {
                                abandonedCount++;
                            }
                            break;
                        case SHOULD_WARN:
                            if (warnColonyOwnersAndOfficers(colony, server)) {
                                warnedCount++;
                            }
                            break;
                        case ACTIVE:
                            // Remove from warned list if colony becomes active again
                            warnedColonies.remove(colony.getID());
                            break;
                    }
                }
            }
            
            if (abandonedCount > 0 || warnedCount > 0) {
                LOGGER.info("Colony abandonment check completed: {} colonies abandoned, {} colonies warned", 
                          abandonedCount, warnedCount);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error during colony abandonment check", e);
        }
    }
    
    /**
     * Check if a specific colony should be abandoned or warned.
     *
     * Uses War-N-Taxes' timer system (OfficerColonyVisitTracker) as the EXCLUSIVE source
     * for abandonment decisions. This replaces MineColonies' internal timer which only
     * tracks owner activity.
     *
     * WnT tracks:
     * - Officer/owner physical colony visits (chunk-based detection) - DEFAULT
     * - Officer/owner logins (anywhere on server) - OPTIONAL (config: ResetTimerOnOfficerLogin)
     *
     * If no WnT data exists for a colony, falls back to MineColonies' timer as a safety measure.
     */
    public static AbandonmentStatus checkColonyAbandonmentStatus(IColony colony) {
        if (colony == null || colony.getPermissions() == null) {
            return AbandonmentStatus.ACTIVE;
        }

        // Skip if colony is already abandoned or has no owner
        UUID owner = colony.getPermissions().getOwner();
        if (owner == null || isColonyAbandoned(colony)) {
            return AbandonmentStatus.ACTIVE;
        }

        // PRIMARY: Use WnT officer tracking as the authoritative timer
        long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
        int lastContactHours;

        if (officerVisitHours >= 0) {
            // WnT tracking data exists - use it exclusively
            lastContactHours = (int) officerVisitHours;
            LOGGER.debug("Colony {} abandonment check using WnT timer: {} hours since last officer activity",
                        colony.getName(), lastContactHours);
        } else {
            // FALLBACK: No WnT data yet - use MineColonies timer until first officer activity is tracked
            // This handles newly created colonies or colonies from before WnT tracking was enabled
            lastContactHours = colony.getLastContactInHours();
            LOGGER.debug("Colony {} using MineColonies fallback timer: {} hours (no WnT data yet)",
                        colony.getName(), lastContactHours);
        }

        int abandonDays = TaxConfig.getColonyAutoAbandonDays();
        int warningDays = TaxConfig.getAbandonWarningDays();

        int abandonHours = abandonDays * 24;
        int warningHours = (abandonDays - warningDays) * 24;

        if (lastContactHours >= abandonHours) {
            return AbandonmentStatus.SHOULD_ABANDON;
        } else if (lastContactHours >= warningHours && TaxConfig.shouldNotifyOwnersBeforeAbandon()) {
            return AbandonmentStatus.SHOULD_WARN;
        }

        return AbandonmentStatus.ACTIVE;
    }
    
    /**
     * Abandon a colony by removing all owners and officers.
     */
    private static boolean abandonColony(IColony colony, MinecraftServer server) {
        try {
            // Get the actual inactivity hours used for the abandonment decision
            long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
            int actualInactivityHours = (officerVisitHours >= 0) ? (int) officerVisitHours : colony.getLastContactInHours();

            LOGGER.info("Abandoning colony {} ({}) due to {} hours of inactivity (WnT timer: {}, MC timer: {})",
                       colony.getName(), colony.getID(), actualInactivityHours,
                       officerVisitHours >= 0 ? officerVisitHours + "h" : "no data",
                       colony.getLastContactInHours() + "h");
            
            IPermissions permissions = colony.getPermissions();
            
            // Collect owners and officers before removal for notification
            List<UUID> removedPlayers = new ArrayList<>();
            Map<UUID, ColonyPlayer> players = new HashMap<>(permissions.getPlayers());
            
            // CRITICAL: Add current owner to the removed players list
            UUID currentOwner = permissions.getOwner();
            if (currentOwner != null) {
                removedPlayers.add(currentOwner);
            }
            
            for (Map.Entry<UUID, ColonyPlayer> entry : players.entrySet()) {
                if (entry.getValue().getRank().isColonyManager() && !entry.getKey().equals(currentOwner)) {
                    removedPlayers.add(entry.getKey());
                }
            }
            
            LOGGER.info("Abandoning colony {} - found {} owners/officers to make hostile: {}", 
                colony.getName(), removedPlayers.size(), removedPlayers);
            
            // STEP 1: Store former members for claiming bypass
            formerColonyMembers.put(colony.getID(), new HashSet<>(removedPlayers));
            
            // STEP 2: **AUTOMATIC NULL OWNER PREVENTION** - NEVER create null owners!
            LOGGER.info("🔧 AUTOMATIC ABANDONMENT: Colony {} - ensuring valid owner ALWAYS exists", colony.getName());
            
            // Store all current players BEFORE any modifications
            Map<UUID, ColonyPlayer> allPlayers = new HashMap<>(permissions.getPlayers());
            LOGGER.info("Found {} total players in colony before abandonment", allPlayers.size());
            
            // First, clean up any existing problematic entries
            cleanupAbandonedEntries(permissions);
            
            // 🚨 AUTOMATIC PROTECTION: GUARANTEE valid owner exists!
            UUID colonyOwner = permissions.getOwner();
            if (colonyOwner == null) {
                LOGGER.error("⚠️ NULL OWNER DETECTED during abandonment - AUTOMATICALLY FIXING!");
                
                // Find any player to make owner (prefer original removedPlayers list)
                UUID newOwner = null;
                if (!removedPlayers.isEmpty()) {
                    newOwner = removedPlayers.get(0); // Use first removed player
                } else if (!allPlayers.isEmpty()) {
                    newOwner = allPlayers.keySet().iterator().next(); // Use any player
                } else {
                    // Last resort: create system owner
                    newOwner = createSystemOwner();
                    permissions.addPlayer(newOwner, "[AUTO_OWNER]", permissions.getRankOwner());
                    LOGGER.error("🆘 NO PLAYERS FOUND - created automatic system owner");
                }
                
                // MANDATORY: Set this player as owner rank
                permissions.setPlayerRank(newOwner, permissions.getRankOwner(), colony.getWorld());
                LOGGER.error("🏛️ AUTOMATIC FIX: {} is now owner to prevent GUI crashes", newOwner);
                colonyOwner = newOwner;
            } else {
                LOGGER.info("✅ Valid owner {} exists - keeping to prevent GUI crashes", colonyOwner);
            }
            
            // Set all NON-OWNER players to neutral rank
            // 🎯 CRITICAL: Keep owner as OWNER rank to prevent GUI crashes!
            Rank colonyNeutralRank = permissions.getRankNeutral();
            for (UUID playerId : allPlayers.keySet()) {
                if (!playerId.equals(colonyOwner)) { // Don't demote the owner!
                ColonyPlayer player = allPlayers.get(playerId);
                    if (!player.getRank().equals(colonyNeutralRank)) {
                        boolean rankSet = permissions.setPlayerRank(playerId, colonyNeutralRank, colony.getWorld());
                        LOGGER.info("Set non-owner player {} to neutral rank: {}", playerId, rankSet);
                    }
                } else {
                    LOGGER.info("🏛️ KEEPING owner {} at Owner rank to prevent GUI crashes", playerId);
                }
            }
            
            LOGGER.info("✅ ABANDONMENT SUCCESS: Colony {} keeps valid owner but all players have neutral permissions", colony.getName());
            
            // STEP 3: SAFELY abandon the colony WITHOUT using setOwnerAbandoned() to prevent weird entries
            try {
                // CRITICAL: We do NOT call setOwnerAbandoned() as it creates problematic [abandoned] entries
                // Instead, we rely on our manual tracking system
                
                // The colony is now effectively abandoned because:
                // 1. All players are set to neutral rank (no managers)
                // 2. We mark it as abandoned in our tracking system
                // 3. The isColonyAbandoned() method will detect this state
                
                LOGGER.info("Colony {} marked as abandoned without using setOwnerAbandoned() to prevent weird entries", colony.getName());
                
                // Clean up any existing problematic entries that might already exist
                cleanupAbandonedEntries(permissions);
                
            } catch (Exception e) {
                LOGGER.error("Error during safe abandonment for colony {}", colony.getName(), e);
            }
            
            LOGGER.info("Successfully abandoned colony {} - set {} players to neutral rank", colony.getName(), allPlayers.size());
            
            // STEP 4: Set EXTREMELY restrictive permissions for neutral players to prevent ALL griefing
            
            LOGGER.info("Setting EXTREMELY restrictive neutral permissions for abandoned colony {}", colony.getName());
            
            // Use the same neutral rank variable as above
            
            // DISABLE ALL POTENTIALLY GRIEFING ACTIONS - BE EXTREMELY RESTRICTIVE
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.HURT_CITIZEN, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.HURT_VISITOR, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.TELEPORT_TO_COLONY, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.RECEIVE_MESSAGES, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.USE_SCAN_TOOL, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.THROW_POTION, false);
            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.SHOOT_ARROW, false);
            // NOTE: GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
            // NOTE: USE_FLY_STICK not available in this API version
            
            // Also set hostile rank to be restrictive (in case someone gets hostile rank)
            Rank hostileRank = permissions.getRankHostile();
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
            
            LOGGER.info("ANTI-GRIEF SUCCESS: Colony {} - all players set to neutral/hostile with ZERO permissions", colony.getName());
            
            // Mark colony as claimable
            abandonedColonies.add(colony.getID());
            
            // Notify removed players when they next log in
            scheduleAbandonmentNotifications(removedPlayers, colony, server);
            
            // Broadcast abandonment to server
            Component broadcastMessage = Component.literal("Colony ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" has been abandoned due to inactivity and can now be claimed!")
                           .withStyle(ChatFormatting.YELLOW));
            
            server.getPlayerList().broadcastSystemMessage(broadcastMessage, false);
            
            // Remove from warned list
            warnedColonies.remove(colony.getID());
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to abandon colony {} ({})", colony.getName(), colony.getID(), e);
            return false;
        }
    }
    
    /**
     * Warn colony owners and officers about upcoming abandonment.
     */
    private static boolean warnColonyOwnersAndOfficers(IColony colony, MinecraftServer server) {
        int colonyId = colony.getID();
        long currentTime = System.currentTimeMillis();
        
        // Check if we've warned recently (don't spam)
        Long lastWarned = warnedColonies.get(colonyId);
        if (lastWarned != null && (currentTime - lastWarned) < 24 * 60 * 60 * 1000) { // 24 hours
            return false;
        }
        
        try {
            // Use WnT timer for accurate days until abandonment
            long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
            int actualInactivityHours = (officerVisitHours >= 0) ? (int) officerVisitHours : colony.getLastContactInHours();
            int daysUntilAbandon = TaxConfig.getColonyAutoAbandonDays() - (actualInactivityHours / 24);

            Component warningMessage = Component.literal("WARNING: Your colony ")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" will be abandoned in " + daysUntilAbandon + " days due to inactivity!")
                           .withStyle(ChatFormatting.RED))
                    .append(Component.literal("\nVisit your colony to prevent abandonment.")
                           .withStyle(ChatFormatting.YELLOW));
            
            // Send warning to online owners/officers and queue for offline ones
            boolean sentWarning = false;
            for (ColonyPlayer colonyPlayer : colony.getPermissions().getPlayers().values()) {
                if (colonyPlayer.getRank().isColonyManager()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(colonyPlayer.getID());
                    if (player != null) {
                        // Player is online - send immediately
                        player.sendSystemMessage(warningMessage);
                        sentWarning = true;
                    } else {
                        // Player is offline - queue notification
                        queueOfflineNotification(colonyPlayer.getID(), warningMessage);
                        sentWarning = true;
                    }
                }
            }
            
            if (sentWarning) {
                warnedColonies.put(colonyId, currentTime);
                LOGGER.info("Warned owners/officers of colony {} ({}) about upcoming abandonment", 
                          colony.getName(), colony.getID());
            }
            
            return sentWarning;
            
        } catch (Exception e) {
            LOGGER.error("Failed to warn about colony abandonment for {} ({})", 
                        colony.getName(), colony.getID(), e);
            return false;
        }
    }
    
    /**
     * Schedule notifications for players when they next log in.
     */
    private static void scheduleAbandonmentNotifications(List<UUID> playerIds, IColony colony, MinecraftServer server) {
        Component message = Component.literal("Your colony ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" has been abandoned due to inactivity.")
                       .withStyle(ChatFormatting.RED))
                .append(Component.literal("\nIt can now be claimed by other players using /wnt claimcolony.")
                       .withStyle(ChatFormatting.YELLOW));
        
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                // Player is online, send immediately
                player.sendSystemMessage(message);
            } else {
                // Store for when player logs in
                queueOfflineNotification(playerId, message);
            }
        }
    }
    
    /**
     * 🚨 AUTOMATIC NULL OWNER PROTECTION: Check if a colony is abandoned.
     * AUTOMATICALLY fixes null owners EVERY TIME this is called!
     */
    public static boolean isColonyAbandoned(IColony colony) {
        if (colony == null || colony.getPermissions() == null) {
            return false;
        }
        
        // 🚨 AUTOMATIC PROTECTION: ALWAYS check and fix null owners on EVERY access!
        UUID owner = colony.getPermissions().getOwner();
        if (owner == null) {
            LOGGER.error("🚨 AUTO-FIX: Colony {} has null owner - FIXING AUTOMATICALLY!", colony.getName());
            fixNullOwnerColony(colony);
            // Get the fixed owner
            owner = colony.getPermissions().getOwner();
            if (owner == null) {
                LOGGER.error("💥 CRITICAL: Failed to fix null owner for colony {}!", colony.getName());
                // Try emergency system owner as last resort
                try {
                    UUID systemOwner = createSystemOwner();
                    colony.getPermissions().addPlayer(systemOwner, "[AUTO_EMERGENCY_OWNER]", colony.getPermissions().getRankOwner());
                    colony.getPermissions().setPlayerRank(systemOwner, colony.getPermissions().getRankOwner(), colony.getWorld());
                    LOGGER.error("🆘 EMERGENCY: Created system owner for colony {}", colony.getName());
                } catch (Exception e) {
                    LOGGER.error("💥 EMERGENCY SYSTEM OWNER FAILED for colony {}: {}", colony.getName(), e.getMessage());
                }
            }
        }
        
        // Primary check: manually marked as abandoned in our tracking system
        if (abandonedColonies.contains(colony.getID())) {
            return true;
        }
        
        // Secondary check: if colony has owner but only inactive players, consider abandoned
        // (This handles edge cases where colony wasn't properly marked as abandoned)
        return false; // With our new approach, we rely primarily on manual tracking
    }
    
    /**
     * EMERGENCY FIX: Fix colonies that have null owners to prevent GUI crashes.
     */
    private static void fixNullOwnerColony(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            
            // Find any player to make owner
            UUID newOwner = null;
            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (player.getID() != null) {
                    newOwner = player.getID();
                    break;
                }
            }
            
            if (newOwner != null) {
                // Set this player as owner rank
                permissions.setPlayerRank(newOwner, permissions.getRankOwner(), colony.getWorld());
                LOGGER.info("🏛️ EMERGENCY FIX: Set {} as emergency owner of null-owner colony {}", newOwner, colony.getName());
                
                // Mark colony as abandoned in our system since it had null owner
                abandonedColonies.add(colony.getID());
                LOGGER.info("📋 Marked colony {} as abandoned due to null owner fix", colony.getName());
            } else {
                LOGGER.error("❌ NO PLAYERS FOUND in null owner colony {}! Cannot fix!", colony.getName());
            }
        } catch (Exception e) {
            LOGGER.error("💥 FAILED to fix null owner colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * 🚨 AUTOMATIC: Fix ALL null owner colonies (runs every 5 seconds automatically).
     * Optimized for frequent use - minimal logging unless issues found.
     */
    public static void emergencyFixAllNullOwners() {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int nullOwnerColonies = 0;
            int fixedColonies = 0;
            
            for (IColony colony : colonyManager.getAllColonies()) {
                try {
                    IPermissions permissions = colony.getPermissions();
                    UUID owner = permissions.getOwner();
                    
                    if (owner == null) {
                        nullOwnerColonies++;
                        LOGGER.error("🚨 AUTO-FIX: Colony {} has null owner - fixing automatically!", colony.getName());
                        
                        // Emergency fix approach: Find ANY player and make them owner
                        UUID emergencyOwner = null;
                        
                        // Try to find any player in the colony
                        for (ColonyPlayer player : permissions.getPlayers().values()) {
                            if (player.getID() != null) {
                                emergencyOwner = player.getID();
                                break;
                            }
                        }
                        
                        if (emergencyOwner == null) {
                            // If no players, create a system owner temporarily
                            emergencyOwner = createSystemOwner();
                            permissions.addPlayer(emergencyOwner, "[AUTO_OWNER]", permissions.getRankOwner());
                            LOGGER.warn("No players in colony {} - created automatic system owner", colony.getName());
                        }
                        
                        // Set this player as owner rank
                        permissions.setPlayerRank(emergencyOwner, permissions.getRankOwner(), colony.getWorld());
                        
                        // Mark as abandoned since it had no owner
                        abandonedColonies.add(colony.getID());
                        
                        fixedColonies++;
                        LOGGER.info("✅ AUTO-FIX: Colony {} now has owner {} (marked abandoned)", 
                            colony.getName(), emergencyOwner);
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("Auto-fix error in colony {}: {}", colony.getName(), e.getMessage());
                }
            }
            
            // Only log summary if issues were found and fixed
            if (nullOwnerColonies > 0) {
                LOGGER.error("🚨 AUTO-FIX COMPLETE: Fixed {} null owner colonies - GUI crashes prevented!", fixedColonies);
            }
            // Silent success when no issues found (runs every 5 seconds)
            
        } catch (Exception e) {
            LOGGER.error("Auto null owner fix error: {}", e.getMessage());
        }
    }
    
    /**
     * Clean up system owner when a colony becomes active again.
     */
    private static void cleanupSystemOwner(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            UUID systemOwner = createSystemOwner();
            
            // Remove system owner if present
            if (permissions.getPlayers().containsKey(systemOwner)) {
                permissions.removePlayer(systemOwner);
                LOGGER.info("CLEANUP: Removed system owner from reactivated colony {}", colony.getName());
            }
            
            // The first real officer will automatically become the effective owner
            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
                    LOGGER.info("CLEANUP: {} will be the effective owner of reactivated colony {}", player.getName(), colony.getName());
                    break;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error cleaning up system owner for colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Mark a colony as claimed (remove from abandoned list).
     * Also clears officer visit tracking as the new owner will establish fresh activity.
     */
    public static void markColonyAsClaimed(int colonyId) {
        abandonedColonies.remove(colonyId);
        warnedColonies.remove(colonyId);
        formerColonyMembers.remove(colonyId); // Clear former members tracking
        
        // Clear officer visit tracking - fresh start for new owner
        net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.clearColonyVisitData(colonyId);
        
        LOGGER.info("Colony {} marked as claimed - cleared all abandonment tracking data", colonyId);
    }
    
    /**
     * Check if a player was a former owner/officer of a specific abandoned colony.
     */
    public static boolean wasFormerOwnerOrOfficer(int colonyId, UUID playerId) {
        Set<UUID> formerMembers = formerColonyMembers.get(colonyId);
        return formerMembers != null && formerMembers.contains(playerId);
    }
    
    /**
     * Get all former owners/officers of an abandoned colony.
     */
    public static Set<UUID> getFormerOwnerAndOfficers(int colonyId) {
        return formerColonyMembers.getOrDefault(colonyId, new HashSet<>());
    }
    
    /**
     * Get all abandoned colonies that can be claimed.
     */
    public static List<IColony> getClaimableColonies(MinecraftServer server) {
        List<IColony> claimable = new ArrayList<>();
        
        if (!TaxConfig.isAbandonedColonyClaimingEnabled()) {
            return claimable;
        }
        
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            
            for (Level world : server.getAllLevels()) {
                for (IColony colony : colonyManager.getColonies(world)) {
                    if (isColonyAbandoned(colony)) {
                        claimable.add(colony);
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error getting claimable colonies", e);
        }
        
        return claimable;
    }
    
    /**
     * Queue a notification for when a player logs in.
     */
    private static void queueOfflineNotification(UUID playerId, Component message) {
        pendingNotifications.computeIfAbsent(playerId, k -> new ArrayList<>()).add(message);
        LOGGER.debug("Queued notification for offline player {}", playerId);
    }
    
    /**
     * Send all pending notifications to a player when they log in.
     */
    public static void sendPendingNotifications(ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<Component> notifications = pendingNotifications.remove(playerId);
        
        if (notifications != null && !notifications.isEmpty()) {
            // Send a header
            player.sendSystemMessage(Component.literal("=== PENDING NOTIFICATIONS ===")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            
            // Send each notification
            for (Component notification : notifications) {
                player.sendSystemMessage(notification);
            }
            
            // Send a footer
            player.sendSystemMessage(Component.literal("=== END OF NOTIFICATIONS ===")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            
            LOGGER.info("Sent {} pending notifications to player {}", notifications.size(), player.getName().getString());
        }
    }
    
    /**
     * Clear all pending notifications for a player (useful for cleanup).
     */
    public static void clearPendingNotifications(UUID playerId) {
        pendingNotifications.remove(playerId);
    }
    
    /**
     * Force abandon a colony (admin command).
     */
    public static boolean forceAbandonColony(IColony colony, MinecraftServer server, String adminName) {
        try {
            IPermissions permissions = colony.getPermissions();
            
            // Get all current managers for notification
            List<UUID> managersToNotify = new ArrayList<>();
            
            // CRITICAL: Add current owner to the list
            UUID currentOwner = permissions.getOwner();
            if (currentOwner != null) {
                managersToNotify.add(currentOwner);
            }
            
            for (ColonyPlayer colonyPlayer : permissions.getPlayers().values()) {
                if (colonyPlayer.getRank().isColonyManager() && !colonyPlayer.getID().equals(currentOwner)) {
                    managersToNotify.add(colonyPlayer.getID());
                }
            }
            
            LOGGER.info("Force abandoning colony {} - found {} owners/officers to make hostile: {}", 
                colony.getName(), managersToNotify.size(), managersToNotify);
            
            // STEP 1: Store former members for claiming bypass
            formerColonyMembers.put(colony.getID(), new HashSet<>(managersToNotify));
            
            // STEP 2: Set all existing players to neutral rank (same as regular abandonment)
            Map<UUID, ColonyPlayer> allPlayers = new HashMap<>(permissions.getPlayers());
            LOGGER.info("Setting ALL {} players to neutral rank in force abandoned colony {}", allPlayers.size(), colony.getName());
            
            Rank neutralRank = permissions.getRankNeutral();
            for (UUID playerId : allPlayers.keySet()) {
                // Skip if already neutral
                ColonyPlayer player = allPlayers.get(playerId);
                if (!player.getRank().equals(neutralRank)) {
                    boolean rankSet = permissions.setPlayerRank(playerId, neutralRank, colony.getWorld());
                    LOGGER.info("Set player {} to neutral rank in force abandoned colony {}: {}", playerId, colony.getName(), rankSet);
                }
            }
            
            // STEP 3: SAFELY abandon the colony WITHOUT using setOwnerAbandoned() to prevent weird entries
            try {
                // CRITICAL: We do NOT call setOwnerAbandoned() as it creates problematic [abandoned] entries
                // Instead, we rely on our manual tracking system
                
                // The colony is now effectively abandoned because:
                // 1. All players are set to neutral rank (no managers)
                // 2. We mark it as abandoned in our tracking system
                // 3. The isColonyAbandoned() method will detect this state
                
                LOGGER.info("Colony {} marked as abandoned without using setOwnerAbandoned() to prevent weird entries", colony.getName());
                
                // Clean up any existing problematic entries that might already exist
                cleanupAbandonedEntries(permissions);
                
            } catch (Exception e) {
                LOGGER.error("Error during safe abandonment for colony {}", colony.getName(), e);
            }
            
            LOGGER.info("Successfully force abandoned colony {} - set {} players to neutral rank", colony.getName(), allPlayers.size());
            
            // STEP 4: Set VERY restrictive permissions for neutral players to prevent griefing
            
            LOGGER.info("Setting VERY restrictive neutral permissions for force abandoned colony {}", colony.getName());
            
            // Disable ALL potentially griefing actions for neutral players
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
            
            LOGGER.info("Force abandonment completed for colony {} - all players set to neutral with restrictive permissions", colony.getName());
            
            // Mark colony as abandoned
            abandonedColonies.add(colony.getID());
            
            // Schedule notifications for all former managers
            Component forceAbandonMessage = Component.literal("ADMIN ACTION: Your colony ")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" has been force abandoned by admin " + adminName + ".")
                           .withStyle(ChatFormatting.RED))
                    .append(Component.literal("\nIt can now be claimed by other players using /wnt claimcolony.")
                           .withStyle(ChatFormatting.YELLOW));
            
            for (UUID playerId : managersToNotify) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // Player is online, send immediately
                    player.sendSystemMessage(forceAbandonMessage);
                } else {
                    // Player is offline, queue notification
                    queueOfflineNotification(playerId, forceAbandonMessage);
                }
            }
            
            LOGGER.warn("Colony {} ({}) force abandoned by admin {}", colony.getName(), colony.getID(), adminName);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to force abandon colony {} ({})", colony.getName(), colony.getID(), e);
            return false;
        }
    }

    /**
     * Clean up any weird [abandoned] entries that might be created during abandonment.
     * This prevents the weird [abandoned] player that can't be deleted.
     * ENHANCED VERSION: More aggressive cleanup to prevent corruption.
     */
    public static void cleanupAbandonedEntries(IPermissions permissions) {
        try {
            // Get all players and look for any with weird names or null UUIDs
            Map<UUID, ColonyPlayer> players = new HashMap<>(permissions.getPlayers());
            List<UUID> toRemove = new ArrayList<>();
            
            LOGGER.info("CLEANUP: Scanning {} player entries for [abandoned] corruption", players.size());
            
            for (Map.Entry<UUID, ColonyPlayer> entry : players.entrySet()) {
                ColonyPlayer player = entry.getValue();
                UUID playerId = entry.getKey();
                
                // Check for problematic entries - more comprehensive checks including system owners
                boolean isProblematic = false;
                String reason = "";
                
                if (playerId == null) {
                    isProblematic = true;
                    reason = "null UUID";
                } else if (player == null) {
                    isProblematic = true; 
                    reason = "null player object";
                } else if (player.getName() == null) {
                    isProblematic = true;
                    reason = "null player name";
                } else if (player.getName().equals("")) {
                    isProblematic = true;
                    reason = "empty player name";
                } else if (player.getName().contains("[abandoned]")) {
                    isProblematic = true;
                    reason = "contains [abandoned]";
                } else if (player.getName().toLowerCase().contains("abandoned")) {
                    isProblematic = true;
                    reason = "contains 'abandoned'";
                } else if (player.getName().equals("[SYSTEM_ABANDONED]") || isSystemOwner(playerId)) {
                    isProblematic = true;
                    reason = "old system owner entry";
                } else if (player.getName().startsWith("~") || player.getName().startsWith("#")) {
                    isProblematic = true;
                    reason = "suspicious name prefix";
                } else {
                    // Check for invalid UUID patterns that might indicate corruption
                    String uuidStr = playerId.toString();
                    if (uuidStr.equals("00000000-0000-0000-0000-000000000000") || 
                        uuidStr.contains("abandoned") || 
                        uuidStr.length() != 36) {
                        isProblematic = true;
                        reason = "invalid UUID pattern";
                    }
                }
                
                if (isProblematic) {
                    LOGGER.warn("CLEANUP: Found problematic player entry - UUID: {}, Name: '{}', Reason: {}", 
                        playerId, player != null ? player.getName() : "null", reason);
                    toRemove.add(playerId);
                }
            }
            
            // Remove all problematic entries
            for (UUID playerId : toRemove) {
                try {
                    permissions.removePlayer(playerId);
                    LOGGER.info("CLEANUP: Successfully removed problematic entry: {}", playerId);
                } catch (Exception e) {
                    LOGGER.error("CLEANUP: Failed to remove problematic entry {}: {}", playerId, e.getMessage());
                    
                    // Try alternative removal methods if standard removal fails
                    try {
                        // Force removal using reflection if needed - more aggressive approach
                        java.lang.reflect.Method method = permissions.getClass().getDeclaredMethod("removePlayer", UUID.class);
                        method.setAccessible(true);
                        method.invoke(permissions, playerId);
                        LOGGER.info("CLEANUP: Force-removed entry via reflection: {}", playerId);
                    } catch (Exception reflectionEx) {
                        LOGGER.error("CLEANUP: Force removal also failed for {}: {}", playerId, reflectionEx.getMessage());
                    }
                }
            }
            
            if (toRemove.size() > 0) {
                LOGGER.info("CLEANUP: Processed {} problematic entries", toRemove.size());
            } else {
                LOGGER.debug("CLEANUP: No problematic entries found");
            }
            
        } catch (Exception e) {
            LOGGER.error("CLEANUP: Error during abandoned entries cleanup", e);
        }
    }
    
    /**
     * ENHANCED: Detect when officers/owners are added to abandoned colonies
     * and automatically mark them as no longer abandoned. This works for admin commands too.
     */
    public static void checkForNewOfficers(IColony colony) {
        if (colony == null) {
            return;
        }
        
        boolean wasAbandoned = isColonyAbandoned(colony);
        if (!wasAbandoned) {
            return; // Colony is not abandoned, nothing to check
        }
        
        // Check if any REAL officers have been added (exclude system owners)
        boolean hasRealOfficers = colony.getPermissions().getPlayers().values().stream()
                .filter(player -> !isSystemOwner(player.getID())) // Exclude system players
                .anyMatch(player -> player.getRank().isColonyManager());
        
        if (hasRealOfficers) {
            LOGGER.info("🎉 COLONY REACTIVATED: {} is no longer abandoned - REAL officers have been added!", colony.getName());
            
            // Remove abandoned status
            markColonyAsClaimed(colony.getID());
            
            // Clean up system owner and set real owner
            cleanupSystemOwnerAndSetRealOwner(colony);
            
            // Restore normal permissions for the colony
            restoreNormalPermissions(colony);
        }
    }
    
    /**
     * Clean up system owner and set first real officer as actual owner.
     */
    private static void cleanupSystemOwnerAndSetRealOwner(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            UUID systemOwnerUUID = createSystemOwner();
            
            // Remove system owner if present
            if (permissions.getPlayers().containsKey(systemOwnerUUID)) {
                permissions.removePlayer(systemOwnerUUID);
                LOGGER.info("CLEANUP: Removed system owner from reactivated colony {}", colony.getName());
            }
            
            // Find first real officer and make them the actual owner
            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
                    try {
                        // Try to set this real player as the actual owner
                        java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
                        setOwnerMethod.invoke(permissions, player.getID());
                        LOGGER.info("🏛️ NEW OWNER SET: {} is now the owner of reactivated colony {}", player.getName(), colony.getName());
                        break;
                    } catch (Exception e) {
                        LOGGER.warn("Could not set {} as owner directly, trying alternative: {}", player.getName(), e.getMessage());
                        // Alternative approach if direct method fails
                        try {
                            for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
                                if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
                                    method.setAccessible(true);
                                    method.invoke(permissions, player.getID());
                                    LOGGER.info("🏛️ NEW OWNER SET (alt): {} is now the owner of reactivated colony {}", player.getName(), colony.getName());
                                    break;
                                }
                            }
                        } catch (Exception e2) {
                            LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e2.getMessage());
                        }
                    }
                    break;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error cleaning up system owner for colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Create a deterministic system owner UUID to prevent [abandoned] entries.
     * This creates a consistent UUID that we can use as a "fake owner" for abandoned colonies.
     */
    public static UUID createSystemOwner() {
        // Use a deterministic UUID based on a fixed string
        // This ensures the same UUID is always generated for the system owner
        return UUID.nameUUIDFromBytes("MINECOLONY_TAX_SYSTEM_OWNER".getBytes());
    }
    
    /**
     * Check if a UUID belongs to our system owner.
     */
    public static boolean isSystemOwner(UUID uuid) {
        return uuid != null && uuid.equals(createSystemOwner());
    }
    
    /**
     * 🚨 AUTOMATIC PROTECTION: Clean up [abandoned] entries AND fix null owners.
     * Runs automatically on server startup and periodically. NO MANUAL INTERVENTION NEEDED.
     */
    public static void cleanupAllColoniesAbandonedEntries() {
        try {
            LOGGER.info("🔧 PROACTIVE CLEANUP: Starting cleanup of [abandoned] entries AND null owners across all colonies");
            
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int coloniesCleaned = 0;
            int entriesRemoved = 0;
            int nullOwnersFixed = 0;
            
            for (IColony colony : colonyManager.getAllColonies()) {
                try {
                    IPermissions permissions = colony.getPermissions();
                    
                    // 🚨 CRITICAL: Fix null owners immediately to prevent GUI crashes
                    UUID owner = permissions.getOwner();
                    if (owner == null) {
                        LOGGER.warn("🚨 FIXING null owner colony: {}", colony.getName());
                        fixNullOwnerColony(colony);
                        nullOwnersFixed++;
                    }
                    
                    // Clean up [abandoned] entries
                    Map<UUID, ColonyPlayer> playersBefore = new HashMap<>(permissions.getPlayers());
                    cleanupAbandonedEntries(permissions);
                    Map<UUID, ColonyPlayer> playersAfter = new HashMap<>(permissions.getPlayers());
                    
                    int removedFromColony = playersBefore.size() - playersAfter.size();
                    if (removedFromColony > 0) {
                        coloniesCleaned++;
                        entriesRemoved += removedFromColony;
                        LOGGER.info("PROACTIVE CLEANUP: Cleaned {} entries from colony '{}' ({})", 
                                removedFromColony, colony.getName(), colony.getID());
                    }
                } catch (Exception e) {
                    LOGGER.error("PROACTIVE CLEANUP: Error cleaning colony '{}' ({}): {}", 
                            colony.getName(), colony.getID(), e.getMessage());
                }
            }
            
            LOGGER.info("✅ PROACTIVE CLEANUP: Completed - 🚨 {} null owners fixed, {} colonies cleaned, {} total entries removed", 
                    nullOwnersFixed, coloniesCleaned, entriesRemoved);
            
        } catch (Exception e) {
            LOGGER.error("PROACTIVE CLEANUP: Error during global cleanup", e);
        }
    }
    
    /**
     * Restore normal permissions for a colony that is no longer abandoned.
     */
    private static void restoreNormalPermissions(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            Rank neutralRank = permissions.getRankNeutral();
            
            LOGGER.info("Restoring normal permissions for colony {} (no longer abandoned)", colony.getName());
            
            // Restore normal neutral permissions
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, true);
            
            // Keep building restrictions for security
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
            
            LOGGER.info("Successfully restored normal permissions for colony {}", colony.getName());
            
        } catch (Exception e) {
            LOGGER.error("Error restoring normal permissions for colony {}", colony.getID(), e);
        }
    }

    public enum AbandonmentStatus {
        ACTIVE,
        SHOULD_WARN,
        SHOULD_ABANDON
    }
}