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
        
        int lastContactHours = colony.getLastContactInHours();
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
        // Defensive guard (4.x/5.0 world-brick fix): the most dangerous owner/permission writer in
        // the mod. Callers are gated on the master switch, but guarding here keeps the safety
        // invariant local. (Admin force-abandon uses forceAbandonColony, not this method.)
        if (!TaxConfig.isColonyAbandonmentSystemEnabled()) {
            LOGGER.warn("abandonColony() called for colony {} while the abandonment system is disabled — refusing.",
                    colony != null ? colony.getName() : "null");
            return false;
        }
        try {
            LOGGER.info("Abandoning colony {} ({}) due to {} hours of inactivity",
                       colony.getName(), colony.getID(), colony.getLastContactInHours());
            
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
            
            // Keep a valid owner to prevent GUI crashes — WITHOUT injecting a synthetic placeholder.
            // 4.x/5.0 world-brick fix: never write '[AUTO_OWNER]' and never assign ownership via
            // setPlayerRank (which doesn't update the cached owner). Delegate to fixNullOwnerColony(),
            // which promotes a real online manager via setOwner() or leaves the colony genuinely
            // owner-less (the same state MineColonies uses for abandoned colonies).
            UUID colonyOwner = permissions.getOwner();
            if (colonyOwner == null) {
                LOGGER.warn("Null owner detected during abandonment of colony {} - promoting a real online manager (no placeholder)", colony.getName());
                fixNullOwnerColony(colony);
                colonyOwner = permissions.getOwner(); // may still be null if no manager online
            } else {
                LOGGER.info("Valid owner {} exists - keeping to prevent GUI crashes", colonyOwner);
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
            int daysUntilAbandon = TaxConfig.getColonyAutoAbandonDays() - (colony.getLastContactInHours() / 24);
            
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
        // Pure read (4.x/5.0 world-brick fix): this used to mutate colony state on EVERY status
        // check — repairing null owners and even injecting an [AUTO_EMERGENCY_OWNER] placeholder —
        // which could corrupt a colony that was only transiently owner-null mid-load. A status
        // check must never write. Owner repair now runs only through the gated, deferred paths.
        return abandonedColonies.contains(colony.getID());
    }
    
    /**
     * EMERGENCY FIX: Fix colonies that have null owners to prevent GUI crashes.
     */
    private static void fixNullOwnerColony(IColony colony) {
        // 4.x/5.0 world-brick fix: the old implementation assigned ownership via setPlayerRank
        // (which does NOT update the cached ownerUUID, so getOwner() stayed null) AND flagged the
        // colony abandoned as a side effect — turning a transient null-owner blip into a
        // permanently-corrupted, falsely-abandoned colony. New behavior: promote a REAL online
        // colony-manager to owner via setOwner (the only call that actually updates the cached
        // owner). If none is online, leave the colony untouched — never inject a placeholder,
        // never flag abandoned. It is repaired naturally when a manager next logs in.
        try {
            IPermissions permissions = colony.getPermissions();
            Level world = colony.getWorld();
            if (world == null || world.getServer() == null) {
                return; // cannot resolve online players without a server context
            }
            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (player.getID() == null || isSystemOwner(player.getID())) continue;
                if (player.getRank() == null || !player.getRank().isColonyManager()) continue;
                ServerPlayer online = world.getServer().getPlayerList().getPlayer(player.getID());
                if (online != null && permissions.setOwner(online)) {
                    LOGGER.info("Restored {} as owner of null-owner colony {}", player.getName(), colony.getName());
                    return;
                }
            }
            LOGGER.debug("Null-owner colony {} has no online manager to promote; leaving untouched", colony.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to repair null-owner colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * 🚨 AUTOMATIC: Fix ALL null owner colonies (runs every 5 seconds automatically).
     * Optimized for frequent use - minimal logging unless issues found.
     */
    public static void emergencyFixAllNullOwners() {
        // 4.x/5.0 world-brick fix: gated behind the master switch and NO LONGER injects a synthetic
        // '[AUTO_OWNER]' placeholder or flags colonies abandoned. Repair is delegated to
        // fixNullOwnerColony(), which only ever promotes a real online manager via setOwner().
        if (!TaxConfig.isColonyAbandonmentSystemEnabled()) {
            return;
        }
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int repaired = 0;

            for (IColony colony : colonyManager.getAllColonies()) {
                try {
                    if (colony.getPermissions().getOwner() != null) continue;
                    fixNullOwnerColony(colony);
                    if (colony.getPermissions().getOwner() != null) repaired++;
                } catch (Exception e) {
                    LOGGER.error("Error repairing null owner in colony {}: {}", colony.getName(), e.getMessage());
                }
            }

            if (repaired > 0) {
                LOGGER.info("Null-owner repair complete: {} colonies restored to a real owner", repaired);
            }

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
     */
    public static void markColonyAsClaimed(int colonyId) {
        abandonedColonies.remove(colonyId);
        warnedColonies.remove(colonyId);
        formerColonyMembers.remove(colonyId); // Clear former members tracking
        
        // Also reset the abandonment cooldown for this colony
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByDimension(colonyId, null);
            if (colony != null) {
                resetAbandonmentCooldown(colony);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not reset cooldown when marking colony {} as claimed: {}", colonyId, e.getMessage());
        }
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
                
                // 4.x/5.0 world-brick fix: match ONLY the exact synthetic markers the mod itself
                // writes. The old broad heuristics (any name lowercase-containing "abandoned",
                // '~'/'#' prefixes, empty/odd names, UUID-length checks) could delete a REAL
                // player and leave the colony ownerless.
                boolean isProblematic = false;
                String reason = "";

                if (playerId == null) {
                    isProblematic = true;
                    reason = "null UUID";
                } else if (player == null) {
                    isProblematic = true;
                    reason = "null player object";
                } else if (isSystemOwner(playerId)) {
                    isProblematic = true;
                    reason = "system owner placeholder";
                } else if (playerId.equals(new UUID(0L, 0L))) {
                    isProblematic = true;
                    reason = "zero UUID";
                } else if (player.getName() != null
                        && (player.getName().equals("[AUTO_OWNER]")
                            || player.getName().equals("[AUTO_EMERGENCY_OWNER]")
                            || player.getName().equals("[SYSTEM_ABANDONED]")
                            || player.getName().contains("[abandoned]"))) {
                    isProblematic = true;
                    reason = "synthetic placeholder name";
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
            
            // CRITICAL: Reset the abandonment cooldown timer to prevent immediate re-abandonment
            resetAbandonmentCooldown(colony);
            
            // Clean up system owner and set real owner
            cleanupSystemOwnerAndSetRealOwner(colony);
            
            // Restore normal permissions for the colony
            restoreNormalPermissions(colony);
        }
    }
    
    /**
     * Reset the abandonment cooldown timer for a colony.
     * This is critical when a colony is reclaimed to prevent it from being immediately abandoned again.
     */
    private static void resetAbandonmentCooldown(IColony colony) {
        try {
            // Access the colony's package manager to reset the lastContactInHours counter
            // This uses reflection to access MineColonies' internal API
            java.lang.reflect.Method getPackageManagerMethod = colony.getClass().getMethod("getPackageManager");
            Object packageManager = getPackageManagerMethod.invoke(colony);
            
            if (packageManager != null) {
                // Get the setLastContactInHours method
                java.lang.reflect.Method setLastContactMethod = packageManager.getClass()
                        .getMethod("setLastContactInHours", int.class);
                
                // Reset to 0 (colony is now active)
                setLastContactMethod.invoke(packageManager, 0);
                
                LOGGER.info("✅ COOLDOWN RESET: Colony {} abandonment timer reset to 0 hours", colony.getName());
                
                // Mark colony as dirty to save the change
                colony.markDirty();
            } else {
                LOGGER.error("❌ COOLDOWN RESET FAILED: Could not access package manager for colony {}", colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("❌ COOLDOWN RESET FAILED: Error resetting abandonment cooldown for colony {}: {}", 
                    colony.getName(), e.getMessage());
            
            // Try alternative approach using direct field access
            try {
                java.lang.reflect.Field packageManagerField = colony.getClass().getDeclaredField("packageManager");
                packageManagerField.setAccessible(true);
                Object packageManager = packageManagerField.get(colony);
                
                if (packageManager != null) {
                    java.lang.reflect.Method setLastContactMethod = packageManager.getClass()
                            .getMethod("setLastContactInHours", int.class);
                    setLastContactMethod.invoke(packageManager, 0);
                    
                    LOGGER.info("✅ COOLDOWN RESET (alt): Colony {} abandonment timer reset to 0 hours", colony.getName());
                    colony.markDirty();
                }
            } catch (Exception e2) {
                LOGGER.error("❌ COOLDOWN RESET FAILED (alt): Could not reset abandonment cooldown for colony {}: {}", 
                        colony.getName(), e2.getMessage());
            }
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
                    // [1.21-PORT] setOwner takes a Player now, not a UUID. Resolve the online player
                    // and use setOwner(Player); for an offline player there is no public API to set
                    // the cached ownerUUID, so fall back to best-effort OWNER rank. See PORTING_NOTES.md §1.
                    try {
                        ServerPlayer online = (colony.getWorld() != null && colony.getWorld().getServer() != null)
                                ? colony.getWorld().getServer().getPlayerList().getPlayer(player.getID())
                                : null;
                        if (online != null && permissions.setOwner(online)) {
                            LOGGER.info("🏛️ NEW OWNER SET: {} is now the owner of reactivated colony {}", player.getName(), colony.getName());
                        } else {
                            permissions.setPlayerRank(player.getID(), permissions.getRankOwner(), colony.getWorld());
                            LOGGER.info("NEW OWNER (rank-only, player offline): {} on reactivated colony {}", player.getName(), colony.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e.toString());
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
     * One-time, REMOVAL-ONLY migration that heals colonies corrupted by older versions which
     * injected synthetic '[AUTO_OWNER]' / '[AUTO_EMERGENCY_OWNER]' / '[SYSTEM_ABANDONED]' /
     * system-UUID placeholder entries into MineColonies permissions.
     *
     * Runs regardless of the abandonment master switch because it only ever REMOVES the mod's own
     * synthetic data — it never adds owners and never flags colonies abandoned. Safety rule: never
     * leave a colony ownerless. If a synthetic entry is the current owner, it is removed only after
     * a real online colony-manager is promoted to owner; if none is available, the synthetic owner
     * is kept (an ownerless colony is worse).
     */
    public static void repairLegacySyntheticOwners() {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int coloniesHealed = 0;

            for (IColony colony : colonyManager.getAllColonies()) {
                try {
                    IPermissions permissions = colony.getPermissions();
                    UUID owner = permissions.getOwner();

                    List<UUID> synthetic = new ArrayList<>();
                    for (ColonyPlayer p : permissions.getPlayers().values()) {
                        UUID id = p.getID();
                        if (id == null) continue;
                        String name = p.getName();
                        boolean isSynthetic = isSystemOwner(id)
                                || (name != null && (name.equals("[AUTO_OWNER]")
                                        || name.equals("[AUTO_EMERGENCY_OWNER]")
                                        || name.equals("[SYSTEM_ABANDONED]")
                                        || name.contains("[abandoned]")));
                        if (isSynthetic) synthetic.add(id);
                    }
                    if (synthetic.isEmpty()) continue;

                    if (owner != null && synthetic.contains(owner)) {
                        ServerPlayer replacement = findOnlineRealManager(colony, synthetic);
                        if (replacement != null) {
                            permissions.setOwner(replacement);
                            LOGGER.info("Legacy repair: handed ownership of colony {} from a synthetic placeholder to {}",
                                    colony.getName(), replacement.getName().getString());
                        } else {
                            // No safe online replacement — keep the synthetic owner, strip the rest.
                            synthetic.remove(owner);
                        }
                    }

                    int removed = 0;
                    for (UUID id : synthetic) {
                        try { permissions.removePlayer(id); removed++; } catch (Exception ignored) {}
                    }
                    if (removed > 0) coloniesHealed++;
                } catch (Exception e) {
                    LOGGER.error("Legacy synthetic-owner repair failed for colony {}: {}", colony.getName(), e.getMessage());
                }
            }
            if (coloniesHealed > 0) {
                LOGGER.info("Legacy synthetic-owner repair complete: {} colonies healed", coloniesHealed);
            }
        } catch (Exception e) {
            LOGGER.warn("Error during legacy synthetic-owner repair: {}", e.getMessage());
        }
    }

    /** Returns an online colony-manager player who is NOT one of the given synthetic UUIDs, or null. */
    private static ServerPlayer findOnlineRealManager(IColony colony, java.util.Collection<UUID> exclude) {
        Level world = colony.getWorld();
        if (world == null || world.getServer() == null) return null;
        for (ColonyPlayer p : colony.getPermissions().getPlayers().values()) {
            UUID id = p.getID();
            if (id == null || exclude.contains(id) || isSystemOwner(id)) continue;
            if (p.getRank() == null || !p.getRank().isColonyManager()) continue;
            ServerPlayer online = world.getServer().getPlayerList().getPlayer(id);
            if (online != null) return online;
        }
        return null;
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