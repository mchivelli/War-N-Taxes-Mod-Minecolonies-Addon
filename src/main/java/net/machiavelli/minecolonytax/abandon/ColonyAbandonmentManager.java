package net.machiavelli.minecolonytax.abandon;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
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
            
            // STEP 1: Set all former owners and officers as hostile BEFORE calling setOwnerAbandoned
            for (UUID playerId : removedPlayers) {
                try {
                    permissions.setPlayerRank(playerId, permissions.getRankHostile(), colony.getWorld());
                    LOGGER.info("✅ Set former owner/officer {} as HOSTILE to abandoned colony {}", playerId, colony.getName());
                } catch (Exception e) {
                    LOGGER.error("❌ Failed to set player {} as hostile: {}", playerId, e.getMessage());
                }
            }
            
            // STEP 2: Store former members for claiming bypass
            formerColonyMembers.put(colony.getID(), new HashSet<>(removedPlayers));
            
            // STEP 3: NOW set colony as abandoned (this removes the owner but hostiles should remain)
            permissions.setOwnerAbandoned();
            
            // STEP 4: Verify hostility was preserved
            for (UUID playerId : removedPlayers) {
                ColonyPlayer playerData = permissions.getPlayers().get(playerId);
                if (playerData != null) {
                    LOGGER.info("✅ Verified: Player {} has rank {} in abandoned colony {}", 
                        playerId, playerData.getRank().getName(), colony.getName());
                } else {
                    LOGGER.warn("❌ WARNING: Player {} was removed from abandoned colony {} (should be hostile!)", 
                        playerId, colony.getName());
                    // Re-add them as hostile if they were removed
                    permissions.addPlayer(playerId, "Former Owner/Officer", permissions.getRankHostile());
                    LOGGER.info("🔧 FIXED: Re-added player {} as hostile to colony {}", playerId, colony.getName());
                }
            }
            
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
            
            Component warningMessage = Component.literal("⚠ WARNING: Your colony ")
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
     * Check if a colony is considered abandoned (can be claimed).
     */
    public static boolean isColonyAbandoned(IColony colony) {
        if (colony == null || colony.getPermissions() == null) {
            return false;
        }
        
        // Check if manually marked as abandoned
        if (abandonedColonies.contains(colony.getID())) {
            return true;
        }
        
        // Check if minecolonies considers it abandoned
        UUID owner = colony.getPermissions().getOwner();
        return owner == null || colony.getPermissions().getOwnerEntry() == null;
    }
    
    /**
     * Mark a colony as claimed (remove from abandoned list).
     */
    public static void markColonyAsClaimed(int colonyId) {
        abandonedColonies.remove(colonyId);
        warnedColonies.remove(colonyId);
        formerColonyMembers.remove(colonyId); // Clear former members tracking
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
            
            // STEP 1: Set all former owners and officers as hostile BEFORE abandoning
            for (UUID playerId : managersToNotify) {
                try {
                    permissions.setPlayerRank(playerId, permissions.getRankHostile(), colony.getWorld());
                    LOGGER.info("✅ Force abandon: Set former owner/officer {} as HOSTILE to colony {}", playerId, colony.getName());
                } catch (Exception e) {
                    LOGGER.error("❌ Failed to set player {} as hostile during force abandon: {}", playerId, e.getMessage());
                }
            }
            
            // STEP 2: Store former members for claiming bypass
            formerColonyMembers.put(colony.getID(), new HashSet<>(managersToNotify));
            
            // STEP 3: Set colony as abandoned
            permissions.setOwnerAbandoned();
            
            // STEP 4: Verify hostility was preserved (same verification as regular abandonment)
            for (UUID playerId : managersToNotify) {
                ColonyPlayer playerData = permissions.getPlayers().get(playerId);
                if (playerData != null) {
                    LOGGER.info("✅ Force abandon verified: Player {} has rank {} in abandoned colony {}", 
                        playerId, playerData.getRank().getName(), colony.getName());
                } else {
                    LOGGER.warn("❌ WARNING: Player {} was removed during force abandon {} (should be hostile!)", 
                        playerId, colony.getName());
                    // Re-add them as hostile if they were removed
                    permissions.addPlayer(playerId, "Former Owner/Officer", permissions.getRankHostile());
                    LOGGER.info("🔧 FIXED: Re-added player {} as hostile to force-abandoned colony {}", playerId, colony.getName());
                }
            }
            
            // Mark colony as abandoned
            abandonedColonies.add(colony.getID());
            
            // Schedule notifications for all former managers
            Component forceAbandonMessage = Component.literal("⚠ ADMIN ACTION: Your colony ")
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

    public enum AbandonmentStatus {
        ACTIVE,
        SHOULD_WARN,
        SHOULD_ABANDON
    }
}
