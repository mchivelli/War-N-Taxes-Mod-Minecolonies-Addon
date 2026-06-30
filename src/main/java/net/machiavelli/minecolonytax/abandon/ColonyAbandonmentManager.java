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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages automatic colony abandonment based on owner/officer inactivity.
 * This system tracks when owners and officers last visited their colonies
 * and automatically abandons colonies that have been inactive for too long.
 */
public class ColonyAbandonmentManager {

    private static final Logger LOGGER = LogManager.getLogger(ColonyAbandonmentManager.class);

    // AUDIT FIX (defensive_04 M2 / Codex MED-11): persist abandoned-state across restarts.
    // Without this, server restart wipes abandoned-flag and former-member tracking; reclaim
    // bypass for former owners stops working and isColonyAbandoned() returns false until
    // the periodic abandonment scan re-derives state.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/abandonment.json";
    /**
     * Lazy one-shot load. We don't have a hook in MineColonyTax for this manager (it has no
     * initialize() in the lifecycle wiring), so we trigger load on first read/write of any
     * persisted collection. Subsequent calls are no-ops.
     */
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final Map<Integer, Long> warnedColonies = new ConcurrentHashMap<>();
    private static final Set<Integer> abandonedColonies = ConcurrentHashMap.newKeySet();
    // Former owners/officers are tracked so they can bypass claiming requirements for their own colony.
    private static final Map<Integer, Set<UUID>> formerColonyMembers = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Component>> pendingNotifications = new ConcurrentHashMap<>();

    /** Serializable snapshot of the persisted state. */
    private static class AbandonmentSaveData {
        Set<Integer> abandonedColonies = new HashSet<>();
        Map<Integer, Set<UUID>> formerColonyMembers = new HashMap<>();
    }

    /** Load abandonment state from disk. Idempotent — safe to call multiple times. */
    public static void loadData() {
        if (!LOADED.compareAndSet(false, true)) return;
        try {
            Path path = Path.of(STORAGE_FILE);
            if (!Files.exists(path)) return;
            try (FileReader r = new FileReader(path.toFile())) {
                AbandonmentSaveData data = GSON.fromJson(r, AbandonmentSaveData.class);
                if (data != null) {
                    if (data.abandonedColonies != null) {
                        abandonedColonies.addAll(data.abandonedColonies);
                    }
                    if (data.formerColonyMembers != null) {
                        for (Map.Entry<Integer, Set<UUID>> e : data.formerColonyMembers.entrySet()) {
                            if (e.getKey() != null && e.getValue() != null) {
                                formerColonyMembers.put(e.getKey(), ConcurrentHashMap.newKeySet());
                                formerColonyMembers.get(e.getKey()).addAll(e.getValue());
                            }
                        }
                    }
                    if (TaxConfig.isNormalLogging()) {
                        LOGGER.info("Loaded abandonment state: {} abandoned colonies, {} former-member entries",
                                abandonedColonies.size(), formerColonyMembers.size());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load abandonment data: {}", e.getMessage());
        }
    }

    /**
     * Persist current abandonment state. Uses temp-file + atomic-move so a crash mid-write
     * leaves the prior file intact (AUDIT FIX — Codex MED-11). Synchronous and short; called
     * from the mutating paths.
     */
    public static void saveData() {
        // Make sure we don't blat a load that hasn't happened yet.
        if (LOADED.compareAndSet(false, true)) {
            // First touch is a save with nothing previously loaded — load first so we don't
            // overwrite existing on-disk state with an empty snapshot.
            LOADED.set(false);
            loadData();
        }
        try {
            AbandonmentSaveData data = new AbandonmentSaveData();
            data.abandonedColonies = new HashSet<>(abandonedColonies);
            data.formerColonyMembers = new HashMap<>();
            for (Map.Entry<Integer, Set<UUID>> e : formerColonyMembers.entrySet()) {
                data.formerColonyMembers.put(e.getKey(), new HashSet<>(e.getValue()));
            }
            File target = new File(STORAGE_FILE);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            File tmp = (parent != null)
                    ? new File(parent, target.getName() + ".tmp")
                    : new File(target.getAbsolutePath() + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) {
                GSON.toJson(data, w);
            }
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomicEx) {
                Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save abandonment data: {}", e.getMessage());
        }
    }
    
    /**
     * Check all colonies for abandonment conditions.
     * Should be called periodically (every hour or so).
     */
    public static void checkColoniesForAbandonment(MinecraftServer server) {
        if (!TaxConfig.isColonyAutoAbandonEnabled()) {
            return;
        }
        loadData(); // lazy one-shot load — see LOADED flag

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
                if (TaxConfig.isNormalLogging()) LOGGER.info("Colony abandonment check completed: {} colonies abandoned, {} colonies warned",
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
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Colony {} abandonment check using WnT timer: {} hours since last officer activity",
                        colony.getName(), lastContactHours);
        } else {
            // FALLBACK: No WnT data yet - use MineColonies timer until first officer activity is tracked
            // This handles newly created colonies or colonies from before WnT tracking was enabled
            lastContactHours = colony.getLastContactInHours();
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Colony {} using MineColonies fallback timer: {} hours (no WnT data yet)",
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
        // Defensive guard (4.x world-brick fix): this is the most dangerous owner/permission
        // writer in the mod. All current callers are already gated on the abandonment master
        // switch, but guarding here makes the safety invariant local — a future caller that
        // forgets the gate cannot reintroduce unconditional owner writes. (Admin force-abandon
        // uses forceAbandonColony, which does NOT route through here.)
        if (!TaxConfig.isColonyAbandonmentSystemEnabled()) {
            LOGGER.warn("abandonColony() called for colony {} while the abandonment system is disabled — refusing.",
                    colony != null ? colony.getName() : "null");
            return false;
        }
        try {
            // Get the actual inactivity hours used for the abandonment decision
            long officerVisitHours = net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
            int actualInactivityHours = (officerVisitHours >= 0) ? (int) officerVisitHours : colony.getLastContactInHours();

            if (TaxConfig.isNormalLogging()) LOGGER.info("Abandoning colony {} ({}) due to {} hours of inactivity (WnT timer: {}, MC timer: {})",
                       colony.getName(), colony.getID(), actualInactivityHours,
                       officerVisitHours >= 0 ? officerVisitHours + "h" : "no data",
                       colony.getLastContactInHours() + "h");
            
            IPermissions permissions = colony.getPermissions();
            
            List<UUID> removedPlayers = new ArrayList<>();
            Map<UUID, ColonyPlayer> players = new HashMap<>(permissions.getPlayers());
            
            UUID currentOwner = permissions.getOwner();
            if (currentOwner != null) {
                removedPlayers.add(currentOwner);
            }
            
            for (Map.Entry<UUID, ColonyPlayer> entry : players.entrySet()) {
                if (entry.getValue().getRank().isColonyManager() && !entry.getKey().equals(currentOwner)) {
                    removedPlayers.add(entry.getKey());
                }
            }
            
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Abandoning colony {} - found {} owners/officers to demote: {}",
                colony.getName(), removedPlayers.size(), removedPlayers);
            
            formerColonyMembers.put(colony.getID(), new HashSet<>(removedPlayers));
            
            Map<UUID, ColonyPlayer> allPlayers = new HashMap<>(permissions.getPlayers());
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Found {} total players in colony before abandonment", allPlayers.size());

            cleanupAbandonedEntries(permissions);

            // Keep a valid owner in the colony to prevent GUI crashes in MineColonies.
            // 4.x world-brick fix: NEVER inject a synthetic '[AUTO_OWNER]' placeholder and NEVER
            // assign ownership via setPlayerRank (which does NOT update the cached owner that
            // getOwner() returns — that was the historical brick: a colony whose rank said
            // "owner" but whose cached owner stayed null). If the cached owner is null, delegate
            // to fixNullOwnerColony(), which promotes a real ONLINE colony-manager via
            // setOwner() (the only call that updates the cached owner). If no manager is online,
            // the colony is left genuinely owner-less — the same state MineColonies itself uses
            // for abandoned colonies — and a real owner is restored on next manager login.
            UUID colonyOwner = permissions.getOwner();
            if (colonyOwner == null) {
                LOGGER.warn("Null owner detected during abandonment of colony {} - attempting to promote a real online manager (no placeholder will be injected)", colony.getName());
                fixNullOwnerColony(colony);
                colonyOwner = permissions.getOwner(); // re-read; may still be null if no manager was online
                if (colonyOwner == null && TaxConfig.isDebugLogging()) {
                    LOGGER.debug("Colony {} remains owner-less after abandonment (no online manager to promote); not injecting a placeholder", colony.getName());
                }
            } else {
                if (TaxConfig.isDebugLogging()) LOGGER.debug("Keeping existing owner {} to prevent GUI crashes in colony {}", colonyOwner, colony.getName());
            }
            
            // Keep the owner at Owner rank; demote all other players to neutral.
            Rank colonyNeutralRank = permissions.getRankNeutral();
            for (UUID playerId : allPlayers.keySet()) {
                if (!playerId.equals(colonyOwner)) {
                    ColonyPlayer player = allPlayers.get(playerId);
                    if (!player.getRank().equals(colonyNeutralRank)) {
                        boolean rankSet = safeSetPlayerRank(colony, permissions, playerId, colonyNeutralRank);
                        if (TaxConfig.isDebugLogging()) LOGGER.debug("Set non-owner player {} to neutral rank: {}", playerId, rankSet);
                    }
                }
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} abandoned: all non-owner players set to neutral rank", colony.getName());
            
            // We do not call setOwnerAbandoned() — it creates problematic [abandoned]
            // player entries. Instead we rely on the abandonedColonies set.
            try {
                cleanupAbandonedEntries(permissions);
            } catch (Exception e) {
                LOGGER.error("Error during safe abandonment for colony {}", colony.getName(), e);
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("Successfully abandoned colony {} - set {} players to neutral rank", colony.getName(), allPlayers.size());
            
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Setting restrictive neutral permissions for abandoned colony {}", colony.getName());
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
            
            Rank hostileRank = permissions.getRankHostile();
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
            
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Colony {} - all players set to neutral/hostile with zero permissions", colony.getName());
            
            // Mark colony as claimable
            abandonedColonies.add(colony.getID());
            saveData(); // AUDIT FIX (defensive_04 M2): persist abandonment + former-member state

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
                if (TaxConfig.isNormalLogging()) LOGGER.info("Warned owners/officers of colony {} ({}) about upcoming abandonment",
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
    
    /** "Are there any abandoned colonies at all?" — for hot-path early-outs (audit H7).
     *  Calls the lazy one-shot loadData() (guarded by a LOADED flag, so cheap after the
     *  first call) so a fresh server start sees persisted abandonment state before the
     *  first block event — matching isColonyAbandoned(). */
    public static boolean hasAbandonedColonies() {
        loadData();
        return !abandonedColonies.isEmpty();
    }

    /**
     * Pure read — returns whether the colony is currently flagged abandoned.
     *
     * 4.x world-brick fix: this used to MUTATE colony state as a side effect of a status
     * read — it injected a synthetic '[AUTO_EMERGENCY_OWNER]' into any colony that momentarily
     * reported a null owner (e.g. while still loading from disk), which corrupted the colony's
     * saved permission data and could brick the world on the next load. A status check must
     * never write. Any null-owner repair now lives exclusively in the gated, deferred
     * {@link #emergencyFixAllNullOwners()} pass.
     */
    public static boolean isColonyAbandoned(IColony colony) {
        if (colony == null || colony.getPermissions() == null) {
            return false;
        }
        loadData(); // lazy one-shot load — see LOADED flag
        return abandonedColonies.contains(colony.getID());
    }

    /**
     * Null-world-safe wrapper around {@link IPermissions#setPlayerRank}. Skips the operation
     * (returns false) when the colony world is null, so we never pass a null Level into
     * MineColonies' permission system (4.x world-brick hardening).
     */
    private static boolean safeSetPlayerRank(IColony colony, IPermissions permissions, UUID playerId, Rank rank) {
        Level world = colony.getWorld();
        if (world == null) {
            LOGGER.warn("Skipping setPlayerRank for colony {} — world is null", colony.getName());
            return false;
        }
        return permissions.setPlayerRank(playerId, rank, world);
    }
    
    /**
     * Best-effort repair for a colony whose cached owner is null.
     *
     * 4.x world-brick fix: the old implementation wrote a placeholder owner via setPlayerRank
     * (which does NOT update the cached ownerUUID, so it didn't even fix getOwner()) AND
     * flagged the colony abandoned as a side effect — turning a transient null-owner blip into
     * a permanently-corrupted, falsely-abandoned colony. The new behavior:
     *   - promote an existing REAL colony-manager player to owner via setOwner (the only call
     *     that actually updates the cached owner) when one is currently online;
     *   - otherwise leave the colony completely untouched (no synthetic placeholder, no
     *     abandoned flag). It will be repaired naturally when a manager next logs in.
     */
    private static void fixNullOwnerColony(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            Level world = colony.getWorld();
            if (world == null || world.getServer() == null) {
                return; // cannot safely resolve online players without a server context
            }
            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (player.getID() == null || isSystemOwner(player.getID())) continue;
                if (player.getRank() == null || !player.getRank().isColonyManager()) continue;
                ServerPlayer online = world.getServer().getPlayerList().getPlayer(player.getID());
                if (online != null) {
                    permissions.setOwner(online);
                    if (TaxConfig.isNormalLogging()) LOGGER.info("Restored {} as owner of null-owner colony {}", player.getName(), colony.getName());
                    return;
                }
            }
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Null-owner colony {} has no online manager to promote; leaving untouched", colony.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to repair null-owner colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Scans all colonies and assigns a placeholder owner to any that have null owners.
     * Idempotent — safe to call multiple times. Logs only when repairs are needed.
     */
    /**
     * Scans all colonies and attempts to restore a real owner for any whose cached owner is
     * null. Idempotent and gated by the abandonment master switch at its call sites.
     *
     * 4.x world-brick fix: this NO LONGER injects a synthetic '[AUTO_OWNER]' placeholder into
     * colonies with no players, and NO LONGER flags any colony abandoned. Both behaviors
     * previously wrote corrupt/fake data into MineColonies colony state. Repair is now delegated
     * to {@link #fixNullOwnerColony(IColony)} which only ever promotes a real online manager.
     */
    public static void emergencyFixAllNullOwners() {
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

            if (repaired > 0 && TaxConfig.isNormalLogging()) {
                LOGGER.info("Null-owner repair complete: {} colonies restored to a real owner", repaired);
            }
        } catch (Exception e) {
            LOGGER.warn("Error during null-owner scan: {}", e.getMessage());
        }
    }

    /**
     * One-time, REMOVAL-ONLY migration that heals colonies corrupted by older versions which
     * injected synthetic '[AUTO_OWNER]' / '[AUTO_EMERGENCY_OWNER]' / '[SYSTEM_ABANDONED]' /
     * system-UUID placeholder entries into MineColonies permissions.
     *
     * Runs regardless of the abandonment master switch because it only ever REMOVES the mod's
     * own synthetic data — it never adds owners and never flags colonies abandoned. Safety
     * rule: never leave a colony ownerless. If a synthetic entry is the current owner, it is
     * only removed after a real online colony-manager is promoted to owner; if none is
     * available, the synthetic owner is left in place (an ownerless colony is worse).
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
                            if (TaxConfig.isNormalLogging()) LOGGER.info("Legacy repair: handed ownership of colony {} from a synthetic placeholder to {}",
                                    colony.getName(), replacement.getName().getString());
                        } else {
                            // No safe online replacement — keep the synthetic owner for now and
                            // only strip the non-owner synthetic entries below.
                            synthetic.remove(owner);
                        }
                    }

                    int removed = 0;
                    for (UUID id : synthetic) {
                        try {
                            permissions.removePlayer(id);
                            removed++;
                        } catch (Exception ignored) {}
                    }
                    if (removed > 0) {
                        coloniesHealed++;
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Legacy repair: removed {} synthetic placeholder entr{} from colony {}",
                                removed, removed == 1 ? "y" : "ies", colony.getName());
                    }
                } catch (Exception e) {
                    LOGGER.error("Legacy synthetic-owner repair failed for colony {}: {}", colony.getName(), e.getMessage());
                }
            }
            if (coloniesHealed > 0 && TaxConfig.isNormalLogging()) {
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
     * Clean up system owner when a colony becomes active again.
     */
    private static void cleanupSystemOwner(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            UUID systemOwner = createSystemOwner();
            
            if (permissions.getPlayers().containsKey(systemOwner)) {
                permissions.removePlayer(systemOwner);
                if (TaxConfig.isDebugLogging()) LOGGER.debug("Removed system owner placeholder from reactivated colony {}", colony.getName());
            }
            
            // The first real officer will automatically become the effective owner
            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
                    if (TaxConfig.isDebugLogging()) LOGGER.info("CLEANUP: {} will be the effective owner of reactivated colony {}", player.getName(), colony.getName());
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
        saveData(); // AUDIT FIX (defensive_04 M2): persist removal of abandoned state

        // Clear officer visit tracking - fresh start for new owner
        net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.clearColonyVisitData(colonyId);
        
        if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} marked as claimed - cleared all abandonment tracking data", colonyId);
    }
    
    /**
     * Check if a player was a former owner/officer of a specific abandoned colony.
     */
    public static boolean wasFormerOwnerOrOfficer(int colonyId, UUID playerId) {
        loadData(); // lazy one-shot load — see LOADED flag
        Set<UUID> formerMembers = formerColonyMembers.get(colonyId);
        return formerMembers != null && formerMembers.contains(playerId);
    }
    
    /**
     * Get all former owners/officers of an abandoned colony.
     */
    public static Set<UUID> getFormerOwnerAndOfficers(int colonyId) {
        loadData(); // lazy one-shot load — see LOADED flag
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
        loadData(); // lazy one-shot load — see LOADED flag

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
            
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Sent {} pending notifications to player {}", notifications.size(), player.getName().getString());
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
    /**
     * Abandons a colony due to sustained tax debt bankruptcy.
     * Uses the same core abandonment logic as inactivity abandonment, but with debt-specific messaging.
     */
    public static boolean abandonColonyForDebt(IColony colony, MinecraftServer server) {
        LOGGER.warn("Colony {} is being abandoned due to tax debt bankruptcy", colony.getName());
        boolean success = abandonColony(colony, server);
        if (success) {
            // Override the inactivity broadcast with a debt-specific one
            Component broadcastMsg = Component.literal("Colony ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" has fallen into tax bankruptcy and can now be claimed by other players!")
                           .withStyle(ChatFormatting.RED));
            server.getPlayerList().broadcastSystemMessage(broadcastMsg, false);
        }
        return success;
    }

    public static boolean forceAbandonColony(IColony colony, MinecraftServer server, String adminName) {
        try {
            IPermissions permissions = colony.getPermissions();
            
            List<UUID> managersToNotify = new ArrayList<>();
            UUID currentOwner = permissions.getOwner();
            if (currentOwner != null) {
                managersToNotify.add(currentOwner);
            }
            for (ColonyPlayer colonyPlayer : permissions.getPlayers().values()) {
                if (colonyPlayer.getRank().isColonyManager() && !colonyPlayer.getID().equals(currentOwner)) {
                    managersToNotify.add(colonyPlayer.getID());
                }
            }

            formerColonyMembers.put(colony.getID(), new HashSet<>(managersToNotify));

            Map<UUID, ColonyPlayer> allPlayers = new HashMap<>(permissions.getPlayers());
            Rank neutralRank = permissions.getRankNeutral();
            for (UUID playerId : allPlayers.keySet()) {
                ColonyPlayer player = allPlayers.get(playerId);
                if (!player.getRank().equals(neutralRank)) {
                    safeSetPlayerRank(colony, permissions, playerId, neutralRank);
                }
            }

            try {
                cleanupAbandonedEntries(permissions);
            } catch (Exception e) {
                LOGGER.error("Error during safe abandonment for colony {}", colony.getName(), e);
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("Successfully force abandoned colony {} - set {} players to neutral rank", colony.getName(), allPlayers.size());
            
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Setting restrictive neutral permissions for force abandoned colony {}", colony.getName());
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
            
            if (TaxConfig.isNormalLogging()) LOGGER.info("Force abandonment completed for colony {} - all players set to neutral with restrictive permissions", colony.getName());

            abandonedColonies.add(colony.getID());
            saveData(); // AUDIT FIX (defensive_04 M2): persist abandonment + former-member state

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
                    player.sendSystemMessage(forceAbandonMessage);
                } else {
                    queueOfflineNotification(playerId, forceAbandonMessage);
                }
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} ({}) force abandoned by admin {}", colony.getName(), colony.getID(), adminName);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to force abandon colony {} ({})", colony.getName(), colony.getID(), e);
            return false;
        }
    }

    /**
     * Removes any corrupt player entries from colony permissions (null UUIDs,
     * [abandoned] placeholder names, invalid UUID patterns). Called after
     * abandonment to avoid the stuck [abandoned] player bug.
     */
    public static void cleanupAbandonedEntries(IPermissions permissions) {
        try {
            Map<UUID, ColonyPlayer> players = new HashMap<>(permissions.getPlayers());
            List<UUID> toRemove = new ArrayList<>();

            for (Map.Entry<UUID, ColonyPlayer> entry : players.entrySet()) {
                ColonyPlayer player = entry.getValue();
                UUID playerId = entry.getKey();

                boolean isProblematic = false;
                String reason = "";

                // 4.x world-brick fix: match ONLY the exact synthetic markers the mod itself
                // ever wrote. The old heuristics — ANY name containing "abandoned", names
                // starting with ~ or #, empty/null names, and a bogus UUID-length check —
                // risked deleting a legitimate (possibly just-added or name-unresolved) player
                // and leaving the colony ownerless, which crashes the town hall GUI.
                if (playerId == null) {
                    isProblematic = true;
                    reason = "null UUID";
                } else if (player == null) {
                    isProblematic = true;
                    reason = "null player object";
                } else if (isSystemOwner(playerId)) {
                    isProblematic = true;
                    reason = "synthetic system-owner UUID";
                } else if (playerId.equals(new UUID(0L, 0L))) {
                    isProblematic = true;
                    reason = "zero UUID";
                } else if (player.getName() != null
                        && (player.getName().equals("[AUTO_OWNER]")
                                || player.getName().equals("[AUTO_EMERGENCY_OWNER]")
                                || player.getName().equals("[SYSTEM_ABANDONED]")
                                || player.getName().contains("[abandoned]"))) {
                    isProblematic = true;
                    reason = "synthetic placeholder entry (" + player.getName() + ")";
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
                    if (TaxConfig.isDebugLogging()) LOGGER.debug("Removed corrupt permissions entry: {}", playerId);
                } catch (Exception e) {
                    LOGGER.warn("Could not remove corrupt entry {}: {}", playerId, e.getMessage());
                    // Fallback: try via reflection
                    try {
                        java.lang.reflect.Method method = permissions.getClass().getDeclaredMethod("removePlayer", UUID.class);
                        method.setAccessible(true);
                        method.invoke(permissions, playerId);
                        if (TaxConfig.isDebugLogging()) LOGGER.debug("Force-removed corrupt entry via reflection: {}", playerId);
                    } catch (Exception reflectionEx) {
                        LOGGER.error("Reflection removal also failed for {}: {}", playerId, reflectionEx.getMessage());
                    }
                }
            }
            if (!toRemove.isEmpty() && TaxConfig.isDebugLogging()) {
                LOGGER.debug("Removed {} corrupt permissions entries", toRemove.size());
            }
        } catch (Exception e) {
            LOGGER.error("Error during abandoned entries cleanup", e);
        }
    }
    
    /**
     * Detects when a real officer or owner has been added back to an abandoned colony
     * and removes the abandoned state, restoring normal permissions.
     */
    public static void checkForNewOfficers(IColony colony) {
        if (colony == null || !isColonyAbandoned(colony)) {
            return;
        }

        boolean hasRealOfficers = colony.getPermissions().getPlayers().values().stream()
                .filter(player -> !isSystemOwner(player.getID()))
                .anyMatch(player -> player.getRank().isColonyManager());

        if (hasRealOfficers) {
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} reactivated - real officers/owners present, removing abandoned status", colony.getName());
            markColonyAsClaimed(colony.getID());
            cleanupSystemOwnerAndSetRealOwner(colony);
            restoreNormalPermissions(colony);
        }
    }
    
    private static void cleanupSystemOwnerAndSetRealOwner(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            UUID systemOwnerUUID = createSystemOwner();

            if (permissions.getPlayers().containsKey(systemOwnerUUID)) {
                permissions.removePlayer(systemOwnerUUID);
                if (TaxConfig.isDebugLogging()) LOGGER.debug("Removed system owner placeholder from reactivated colony {}", colony.getName());
            }

            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
                    try {
                        // MineColonies changed IPermissions.setOwner(UUID) -> setOwner(Player) in
                        // 1.1.1237, which broke the old reflection ("argument type mismatch") and
                        // left colonies ownerless. setOwner(Player) updates the cached ownerUUID
                        // that getOwner() returns; setPlayerRank does NOT. Prefer setOwner when the
                        // target manager is online; fall back to a best-effort rank assignment when
                        // offline (the new API cannot set an offline player as the cached owner).
                        // [1.21-PORT] same limitation on NeoForge/1.21 — see PORTING_NOTES.md.
                        net.minecraft.server.level.ServerPlayer online =
                                (colony.getWorld() != null && colony.getWorld().getServer() != null)
                                        ? colony.getWorld().getServer().getPlayerList().getPlayer(player.getID())
                                        : null;
                        if (online != null) {
                            permissions.setOwner(online);
                        } else {
                            safeSetPlayerRank(colony, permissions, player.getID(), permissions.getRankOwner());
                        }
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Set {} as owner of reactivated colony {}", player.getName(), colony.getName());
                    } catch (Exception e) {
                        LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e.getMessage());
                    }
                    break;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error cleaning up system owner for colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Returns a deterministic placeholder UUID used as a temporary colony owner
     * for abandoned colonies that have no real players. Consistent across restarts.
     */
    public static UUID createSystemOwner() {
        return UUID.nameUUIDFromBytes("MINECOLONY_TAX_SYSTEM_OWNER".getBytes());
    }

    public static boolean isSystemOwner(UUID uuid) {
        return uuid != null && uuid.equals(createSystemOwner());
    }

    /**
     * Scans all colonies and removes corrupt [abandoned] permission entries, then
     * fixes any null owners. Safe to call on startup and periodically.
     */
    public static void cleanupAllColoniesAbandonedEntries() {
        try {
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            int coloniesCleaned = 0;
            int entriesRemoved = 0;
            int nullOwnersFixed = 0;

            for (IColony colony : colonyManager.getAllColonies()) {
                try {
                    IPermissions permissions = colony.getPermissions();

                    if (permissions.getOwner() == null) {
                        fixNullOwnerColony(colony);
                        nullOwnersFixed++;
                    }

                    Map<UUID, ColonyPlayer> playersBefore = new HashMap<>(permissions.getPlayers());
                    cleanupAbandonedEntries(permissions);
                    Map<UUID, ColonyPlayer> playersAfter = new HashMap<>(permissions.getPlayers());

                    int removed = playersBefore.size() - playersAfter.size();
                    if (removed > 0) {
                        coloniesCleaned++;
                        entriesRemoved += removed;
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Cleaned {} corrupt entries from colony {} ({})",
                                removed, colony.getName(), colony.getID());
                    }
                } catch (Exception e) {
                    LOGGER.error("Error cleaning colony {} ({}): {}", colony.getName(), colony.getID(), e.getMessage());
                }
            }

            if ((coloniesCleaned > 0 || nullOwnersFixed > 0) && TaxConfig.isNormalLogging()) {
                LOGGER.info("Startup colony cleanup: {} null owners fixed, {} colonies cleaned, {} entries removed",
                        nullOwnersFixed, coloniesCleaned, entriesRemoved);
            }
        } catch (Exception e) {
            LOGGER.error("Error during global colony cleanup", e);
        }
    }
    
    private static void restoreNormalPermissions(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            Rank neutralRank = permissions.getRankNeutral();

            if (TaxConfig.isNormalLogging()) LOGGER.info("Restoring normal permissions for colony {} (no longer abandoned)", colony.getName());

            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, true);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, true);
            // block/place remain false for security
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
            permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);

            if (TaxConfig.isNormalLogging()) LOGGER.info("Successfully restored normal permissions for colony {}", colony.getName());
            
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
