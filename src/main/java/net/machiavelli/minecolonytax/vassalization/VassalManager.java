package net.machiavelli.minecolonytax.vassalization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles vassal relationships and tribute payments.
 */
@EventBusSubscriber(modid = "minecolonytax", bus = EventBusSubscriber.Bus.GAME)
public class VassalManager {

    private static final Logger LOGGER = LogManager.getLogger(VassalManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/vassals.json";

    /** key = vassal colonyId */
    private static final Map<Integer, VassalRelation> ACTIVE_VASSALS = new ConcurrentHashMap<>();
    /** key = vassal colonyId (same as proposal target) */
    private static final Map<Integer, VassalProposal> PENDING_PROPOSALS = new ConcurrentHashMap<>();

    /** pending offline messages */
    private static final Map<UUID, List<Component>> OFFLINE_MESSAGES = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData(server);
        // Sweep expired war vassalizations once a minute (so expiry isn't gated on tax cycles).
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
            try { sweepExpiredVassalizations(); } catch (Throwable t) {
                LOGGER.warn("Vassalization expiry sweep error: {}", t.toString());
            }
        }, 60_000, 60_000);
        LOGGER.info("VassalManager initialized");
    }

    public static void shutdown() {
        saveData();
    }

    /* ---------------- proposal handling ------------- */
    public static int requestVassalization(ServerPlayer overlord, IColony targetColony, int percent) {
        if (percent <= 0 || percent > 100) {
            overlord.sendSystemMessage(Component.literal("Percentage must be between 1 and 100"));
            return 0;
        }
        if (ACTIVE_VASSALS.containsKey(targetColony.getID()) || PENDING_PROPOSALS.containsKey(targetColony.getID())) {
            overlord.sendSystemMessage(Component.literal("A vassalization relation or proposal already exists for this colony."));
            return 0;
        }
        VassalProposal proposal = new VassalProposal(targetColony.getID(), overlord.getUUID(), percent);
        PENDING_PROPOSALS.put(targetColony.getID(), proposal);

        try {
            // notify target colony managers (owner/officers and fallbacks)
            sendToColonyManagers(targetColony, Component.literal(overlord.getName().getString() +
                    " requests that your colony become a vassal, paying " + percent + "% of its tax income."));

            Component accept = Component.literal("[Accept]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/wnt vasalaccept " + targetColony.getID())));
            Component decline = Component.literal("[Decline]").withStyle(style -> style.withColor(ChatFormatting.RED)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/wnt vasaldecline " + targetColony.getID())));
            sendToColonyManagers(targetColony, Component.literal(" ").append(accept).append(Component.literal(" ")).append(decline));

            overlord.sendSystemMessage(Component.literal("Vassalization proposal sent."));
            return 1;
        } catch (Throwable t) {
            // Roll back pending state if notifications fail, so users can retry
            PENDING_PROPOSALS.remove(targetColony.getID());
            LOGGER.warn("Failed to deliver vassalization request notifications for colony {}: {}", targetColony.getName(), t.toString());
            overlord.sendSystemMessage(Component.literal("Failed to send vassalization request. Please try again in a moment."));
            return 0;
        }
    }

    public static int acceptProposal(ServerPlayer executor, int colonyId) {
        VassalProposal prop = PENDING_PROPOSALS.get(colonyId);
        if (prop == null) {
            executor.sendSystemMessage(Component.literal("No pending proposal for this colony."));
            return 0;
        }
        IColony colony = getColonyById(colonyId);
        if (colony == null) {
            executor.sendSystemMessage(Component.literal("Colony not found."));
            return 0;
        }
        if (!isPlayerManagerOfColony(executor, colony)) {
            executor.sendSystemMessage(Component.literal("You are not authorized to accept proposals for this colony."));
            return 0;
        }
        VassalRelation rel = new VassalRelation(colonyId, prop.overlordUUID, prop.percent, System.currentTimeMillis());
        ACTIVE_VASSALS.put(colonyId, rel);
        PENDING_PROPOSALS.remove(colonyId);
        saveData();

        ServerPlayer overlord = SERVER.getPlayerList().getPlayer(prop.overlordUUID);
        if (overlord != null) {
            overlord.sendSystemMessage(Component.literal("Your vassalization proposal for colony " + colony.getName() + " has been accepted."));
        } else {
            queueMessage(prop.overlordUUID, Component.literal("Your vassalization proposal for colony " + colony.getName() + " has been accepted."));
        }
        executor.sendSystemMessage(Component.literal("Colony is now a vassal. It will pay " + prop.percent + "% of its taxes."));
        return 1;
    }

    public static int declineProposal(ServerPlayer executor, int colonyId) {
        VassalProposal prop = PENDING_PROPOSALS.get(colonyId);
        if (prop == null) {
            executor.sendSystemMessage(Component.literal("No pending proposal."));
            return 0;
        }
        IColony colony = getColonyById(colonyId);
        if (colony == null) {
            PENDING_PROPOSALS.remove(colonyId);
            return 1;
        }
        if (!isPlayerManagerOfColony(executor, colony)) {
            executor.sendSystemMessage(Component.literal("You are not authorized to respond."));
            return 0;
        }
        PENDING_PROPOSALS.remove(colonyId);
        ServerPlayer overlord = SERVER.getPlayerList().getPlayer(prop.overlordUUID);
        if (overlord != null) {
            overlord.sendSystemMessage(Component.literal("Your vassalization proposal for colony " + colony.getName() + " was declined."));
        } else {
            queueMessage(prop.overlordUUID, Component.literal("Your vassalization proposal for colony " + colony.getName() + " was declined."));
        }
        executor.sendSystemMessage(Component.literal("You declined the vassalization proposal."));

        // Tribune rule: declining a vassalization offer can break out into war between the
        // offering overlord (attacker) and the declining colony. Requires the overlord online
        // and able to meet the normal war requirements; otherwise the decline stays peaceful.
        if (net.machiavelli.minecolonytax.TaxConfig.isVassalDeclineWarEnabled()) {
            if (overlord != null) {
                overlord.sendSystemMessage(Component.literal(
                        "Your vassalization demand on " + colony.getName() + " was refused — you march to war!")
                        .withStyle(ChatFormatting.RED));
                try {
                    net.machiavelli.minecolonytax.WarSystem.processWageWarRequest(
                            overlord, colony, overlord.createCommandSourceStack());
                } catch (Throwable t) {
                    LOGGER.warn("Tribune decline-war failed to start for colony {}: {}", colony.getName(), t.toString());
                }
            } else {
                executor.sendSystemMessage(Component.literal(
                        "The would-be overlord is offline; no war breaks out (for now).")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return 1;
    }

    public static int revokeRelation(ServerPlayer executor, String overlordNameOrTarget) {
        UUID executorId = executor.getUUID();
        // determine if executor is overlord or vassal side
        boolean found = false;
        Iterator<Map.Entry<Integer, VassalRelation>> it = ACTIVE_VASSALS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, VassalRelation> e = it.next();
            VassalRelation rel = e.getValue();
            IColony colony = getColonyById(e.getKey());
            if (colony == null) continue;
            String overlordName = getPlayerName(rel.overlordUUID);
            if (rel.overlordUUID.equals(executorId) || (isPlayerManagerOfColony(executor, colony) && overlordName.equalsIgnoreCase(overlordNameOrTarget))) {
                it.remove();
                saveData();
                // notify players
                ServerPlayer overlord = SERVER.getPlayerList().getPlayer(rel.overlordUUID);
                String msg = "Vassalization between colony " + colony.getName() + " and player " + overlordName + " has been revoked.";
                executor.sendSystemMessage(Component.literal("You revoked the vassalization."));
                if (overlord != null) {
                    overlord.sendSystemMessage(Component.literal(msg));
                } else {
                    queueMessage(rel.overlordUUID, Component.literal(msg));
                }
                found = true;
            }
        }
        if (!found) {
            executor.sendSystemMessage(Component.literal("No matching vassalization found."));
            return 0;
        }
        return 1;
    }

    /* list command */
    public static int listVassals(ServerPlayer player) {
        UUID id = player.getUUID();
        player.sendSystemMessage(Component.literal("§6§l=== Vassalization Status ==="));
        
        // Check if player is a vassal of someone
        boolean isVassal = false;
        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
            IColony playerColony = getPrimaryColonyOfPlayer(id);
            if (playerColony != null && rel.colonyId == playerColony.getID()) {
                isVassal = true;
                // Find overlord name
                ServerPlayer overlord = SERVER.getPlayerList().getPlayer(rel.overlordUUID);
                String overlordName = overlord != null ? overlord.getGameProfile().getName() : "Unknown";
                
                String currencyName = getCurrencyName();
                player.sendSystemMessage(Component.literal("§c[VASSAL STATUS] Your colony is a vassal to " + overlordName + 
                    "'s colony (" + rel.percent + "% tribute rate)"));
                player.sendSystemMessage(Component.literal("§c[VASSAL STATUS] Last tribute: " + rel.lastTribute + " " + currencyName));
                break;
            }
        }
        
        if (!isVassal) {
            player.sendSystemMessage(Component.literal("§a[VASSAL STATUS] Your colony is independent"));
        }
        
        // List player's vassals
        boolean hasVassals = false;
        player.sendSystemMessage(Component.literal("§e--- Your Vassals ---"));
        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
            if (rel.overlordUUID.equals(id)) {
                IColony colony = getColonyById(rel.colonyId);
                if (colony != null) {
                    long minutes = (System.currentTimeMillis() - rel.lastPayment) / 60000;
                    player.sendSystemMessage(Component.literal("§a- " + colony.getName() + ": " + rel.percent + "% tribute rate"));
                    player.sendSystemMessage(Component.literal("  §7Last payment: " + minutes + "m ago, Amount: " + rel.lastTribute + " " + getCurrencyName()));
                    hasVassals = true;
                }
            }
        }
        if (!hasVassals) {
            player.sendSystemMessage(Component.literal("§7You have no vassals."));
        }
        
        return 1;
    }

    /* ---------------- tax handling ------------- */
    public static int handleTaxIncome(IColony colony, int generatedTax) {
        VassalRelation rel = ACTIVE_VASSALS.get(colony.getID());
        if (rel == null) return 0;
        // War vassalization auto-expiry: drop the relation once its duration elapses.
        if (rel.expirationTime > 0 && System.currentTimeMillis() >= rel.expirationTime) {
            expireVassalization(colony.getID(), rel);
            return 0;
        }
        int tribute = (int) (generatedTax * rel.percent / 100.0);
        if (tribute <= 0) return 0;

        // deduct from vassal colony
        TaxManager.adjustTax(colony, -tribute);

        // deposit to overlord's first colony
        IColony overlordColony = getPrimaryColonyOfPlayer(rel.overlordUUID);
        if (overlordColony != null) {
            TaxManager.adjustTax(overlordColony, tribute);
        }
        rel.lastPayment = System.currentTimeMillis();
        rel.lastTribute = tribute; // Store the last tribute amount

        // message to overlord
        ServerPlayer overlordPlayer = SERVER.getPlayerList().getPlayer(rel.overlordUUID);
        Component msg = Component.literal("Received tribute of " + tribute + " coins from vassal colony " + colony.getName());
        if (overlordPlayer != null) {
            overlordPlayer.sendSystemMessage(msg);
        } else {
            queueMessage(rel.overlordUUID, msg);
        }
        return tribute;
    }
    
    /**
     * Gets the tribute paid by a colony in the last tax cycle
     * @param colonyId The colony ID
     * @return The amount of tribute paid, or 0 if not a vassal
     */
    public static int getTributePaid(int colonyId) {
        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
        if (rel == null) return 0;
        return rel.lastTribute;
    }

    /** The overlord UUID a colony currently pays tribute to, or null if it is not a vassal. */
    public static UUID getVassalOverlordUUID(int colonyId) {
        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
        return rel != null ? rel.overlordUUID : null;
    }

    /** Remaining hours on a war vassalization, 0 if expired this instant, -1 if permanent or not a vassal. */
    public static int getRemainingVassalizationHours(int colonyId) {
        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
        if (rel == null || rel.expirationTime <= 0) return -1;
        long remaining = rel.expirationTime - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) (remaining / (60L * 60L * 1000L));
    }

    /** Periodic sweep so war vassalizations expire even when the vassal colony generates no tax. */
    public static void sweepExpiredVassalizations() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, VassalRelation> e : new java.util.ArrayList<>(ACTIVE_VASSALS.entrySet())) {
            VassalRelation rel = e.getValue();
            if (rel.expirationTime > 0 && now >= rel.expirationTime) {
                expireVassalization(e.getKey(), rel);
            }
        }
    }

    private static void expireVassalization(int colonyId, VassalRelation rel) {
        ACTIVE_VASSALS.remove(colonyId);
        saveData();
        IColony colony = getColonyById(colonyId);
        if (colony != null) {
            sendToColonyManagers(colony, Component.literal(
                    "§aYour colony is no longer a vassal — the war vassalization has expired.")
                    .withStyle(ChatFormatting.GREEN));
        }
        sendOrQueue(rel.overlordUUID, Component.literal(
                "§7A war vassalization you held has expired.").withStyle(ChatFormatting.GRAY));
        LOGGER.info("War vassalization expired for colony {}", colonyId);
    }

    /**
     * Forces a colony to become a vassal as a result of war victory, bypassing the normal
     * proposal/acceptance flow. Used by the WarSystem vassalize-only outcome.
     *
     * NOTE: the NeoForge VassalRelation has no expiry field yet, so forced vassalization is
     * permanent-until-revoked. WarVassalizationDurationHours expiry is a parity follow-up
     * (see PARITY_PORT_ROADMAP.md, Stage 1b).
     *
     * @return true if vassalization was established, false if the colony was already a vassal.
     */
    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
            int durationHours) {
        if (vassalColony == null || overlordUUID == null) {
            LOGGER.warn("forceVassalize called with null colony or overlord");
            return false;
        }
        int colonyId = vassalColony.getID();
        VassalRelation rel = new VassalRelation(colonyId, overlordUUID, tributePercent, System.currentTimeMillis());
        // War vassalization auto-expires after durationHours (0 = permanent until reclaimed/revoked).
        rel.expirationTime = durationHours > 0
                ? System.currentTimeMillis() + (durationHours * 60L * 60L * 1000L)
                : 0L;
        // Atomic check-then-insert: a non-null return means an active vassalization already existed.
        if (ACTIVE_VASSALS.putIfAbsent(colonyId, rel) != null) {
            return false;
        }
        saveData();

        String overlordName = getPlayerName(overlordUUID);
        sendToColonyManagers(vassalColony, Component.literal(
                "§c⚔ WAR DEFEAT: Your colony has been vassalized by " + overlordName
                        + "! You will pay " + tributePercent + "% of your tax income as tribute.")
                .withStyle(ChatFormatting.RED));
        sendOrQueue(overlordUUID, Component.literal(
                "§a⚔ WAR VICTORY: Colony '" + vassalColony.getName()
                        + "' is now your vassal! They pay you " + tributePercent + "% tribute.")
                .withStyle(ChatFormatting.GREEN));

        LOGGER.info("War vassalization created: colony {} is now vassal to {} at {}% tribute",
                vassalColony.getName(), overlordName, tributePercent);
        return true;
    }

    /* ---------------- helpers ------------- */
    private static boolean isPlayerManagerOfColony(ServerPlayer player, IColony colony) {
        var rank = colony.getPermissions().getRank(player.getUUID());
        return rank != null && rank.isColonyManager();
    }

    private static void sendToColonyManagers(IColony colony, Component message) {
        if (colony == null) return;
        IPermissions perms = colony.getPermissions();

        // Build robust recipient set with multiple fallbacks
        java.util.Set<java.util.UUID> recipients = new java.util.HashSet<>();

        // Owner
        java.util.UUID ownerId = perms.getOwner();
        if (ownerId != null) recipients.add(ownerId);

        // Officers (if rank exists)
        try {
            var officerRank = perms.getRankOfficer();
            if (officerRank != null) {
                for (ColonyPlayer cp : perms.getPlayersByRank(officerRank)) {
                    if (cp != null && cp.getID() != null) recipients.add(cp.getID());
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: anyone with ACCESS_HUTS (managers and trusted members)
        try {
            if (SERVER != null && colony.getWorld() != null && colony.getWorld().getServer() != null) {
                for (ServerPlayer p : colony.getWorld().getServer().getPlayerList().getPlayers()) {
                    try {
                        if (perms.hasPermission(p, Action.ACCESS_HUTS)) {
                            recipients.add(p.getUUID());
                        }
                    } catch (Throwable ignoredInner) {}
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: all known colony members from permissions map if still empty
        if (recipients.isEmpty()) {
            try {
                var playersMap = perms.getPlayers();
                if (playersMap != null) {
                    recipients.addAll(playersMap.keySet());
                }
            } catch (Throwable ignored) {}
        }

        // FTB Teams extension: also notify members of the owner's FTB party (if installed)
        try {
            if (ownerId != null && WarSystem.FTB_TEAMS_INSTALLED && WarSystem.FTB_TEAM_MANAGER != null) {
                var teamOpt = WarSystem.FTB_TEAM_MANAGER.getTeamForPlayerID(ownerId);
                if (teamOpt.isPresent()) {
                    var team = teamOpt.get();
                    // For party teams, notify direct members
                    try {
                        var members = team.getMembers();
                        if (members != null) recipients.addAll(members);
                    } catch (Throwable ignoredInner) {}
                }
            }
        } catch (Throwable ignored) {}

        // Finally, deliver message
        for (java.util.UUID id : recipients) {
            sendOrQueue(id, message);
        }
    }

    private static void sendOrQueue(UUID playerId, Component msg) {
        ServerPlayer player = SERVER.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(msg);
        } else {
            queueMessage(playerId, msg);
        }
    }

    private static void queueMessage(UUID playerId, Component msg) {
        OFFLINE_MESSAGES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(msg);
    }

    private static String getPlayerName(UUID uuid) {
        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : uuid.toString();
    }

    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
        for (IColony c : cm.getAllColonies()) {
            if (c.getPermissions().getOwner().equals(playerId)) return c;
        }
        return null;
    }

    private static IColony getColonyById(int colonyId) {
        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
    }

    /* ---------------- data persistence ------------- */
    private static void loadData(MinecraftServer server) {
        File f = server.getServerDirectory().resolve(STORAGE_FILE).toFile();
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            Type type = new TypeToken<List<VassalRelation>>() {}.getType();
            List<VassalRelation> list = GSON.fromJson(r, type);
            if (list != null) {
                for (VassalRelation rel : list) {
                    ACTIVE_VASSALS.put(rel.colonyId, rel);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load vassal data", e);
        }
    }

    private static void saveData() {
        if (SERVER == null) return;
        File f = SERVER.getServerDirectory().resolve(STORAGE_FILE).toFile();
        try (FileWriter w = new FileWriter(f)) {
            List<VassalRelation> list = new ArrayList<>(ACTIVE_VASSALS.values());
            GSON.toJson(list, w);
        } catch (Exception e) {
            LOGGER.error("Failed to save vassal data", e);
        }
    }

    /* deliver offline messages */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        List<Component> msgs = OFFLINE_MESSAGES.remove(player.getUUID());
        if (msgs != null) {
            msgs.forEach(player::sendSystemMessage);
        }
    }

    /* ----- data classes ----- */
    private static class VassalRelation {
        int colonyId;
        UUID overlordUUID;
        int percent;
        long lastPayment;
        int lastTribute;
        /** Wall-clock millis when a war vassalization auto-expires; 0 = permanent (proposal-based or until revoked). */
        long expirationTime;
        public VassalRelation(int colonyId, UUID overlordUUID, int percent, long lastPayment) {
            this.colonyId = colonyId;
            this.overlordUUID = overlordUUID;
            this.percent = percent;
            this.lastPayment = lastPayment;
            this.lastTribute = 0;
            this.expirationTime = 0;
        }
    }

    private static class VassalProposal {
        int colonyId;
        UUID overlordUUID;
        int percent;
        public VassalProposal(int colonyId, UUID overlordUUID, int percent) {
            this.colonyId = colonyId;
            this.overlordUUID = overlordUUID;
            this.percent = percent;
        }
    }
    
    /**
     * Gets the appropriate currency name based on config settings
     * @return the currency name to display
     */
    private static String getCurrencyName() {
        if (TaxConfig.isSDMShopConversionEnabled()) {
            return "$";
        } else {
            String currencyName = TaxConfig.getCurrencyItemName();
            if (currencyName.contains(":")) {
                currencyName = currencyName.substring(currencyName.indexOf(":") + 1);
            }
            return currencyName;
        }
    }
    
    /**
     * Public method to check if a colony is a vassal (for GUI integration)
     */
    public static boolean isColonyVassal(int colonyId) {
        return ACTIVE_VASSALS.containsKey(colonyId);
    }
    
    /**
     * Public method to get vassal tribute rate (for GUI integration)
     */
    public static int getVassalTributeRate(int colonyId) {
        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
        return rel != null ? rel.percent : 0;
    }
    
    /**
     * Public method to count how many vassals a player has (for GUI integration)
     */
    public static int countVassalsForPlayer(UUID playerId) {
        int count = 0;
        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
            if (rel.overlordUUID.equals(playerId)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Remove a vassal relation (end vassalization)
     */
    public static boolean removeVassalRelation(int vassalColonyId) {
        VassalRelation relation = ACTIVE_VASSALS.remove(vassalColonyId);
        if (relation != null) {
            saveData();
            LOGGER.info("Vassalization ended for colony " + vassalColonyId);
            return true;
        }
        return false;
    }
    
    /**
     * End vassalization with notification to overlord (for GUI integration)
     */
    public static boolean endVassalizationWithNotification(int vassalColonyId, ServerPlayer player, IColony colony) {
        VassalRelation relation = ACTIVE_VASSALS.get(vassalColonyId);
        if (relation == null) {
            return false;
        }
        
        // Store overlord info before removing relation
        UUID overlordId = relation.overlordUUID;
        String playerName = player.getName().getString();
        String colonyName = colony.getName();
        
        // Remove the vassalization
        boolean success = removeVassalRelation(vassalColonyId);
        
        if (success) {
            // Notify the overlord (online or offline)
            Component notificationMsg = Component.literal("§c⚠ Vassalization Ended: Colony '" + colonyName + 
                "' (managed by " + playerName + ") has ended their vassalage to you.");
            
            ServerPlayer overlord = SERVER.getPlayerList().getPlayer(overlordId);
            if (overlord != null) {
                // Online notification
                overlord.sendSystemMessage(notificationMsg);
                overlord.sendSystemMessage(Component.literal("§7You will no longer receive tribute from this colony."));
            } else {
                // Offline notification
                addOfflineMessage(overlordId, notificationMsg);
                addOfflineMessage(overlordId, Component.literal("§7You will no longer receive tribute from this colony."));
            }
            
            LOGGER.info("Vassalization ended for colony {} with notification sent to overlord {}", 
                vassalColonyId, overlordId);
        }
        
        return success;
    }
    
    /**
     * Add offline message for a player
     */
    public static void addOfflineMessage(UUID playerId, Component message) {
        OFFLINE_MESSAGES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(message);
    }
    
    /**
     * Get vassal income data for a player (for GUI integration)
     */
    public static List<VassalIncomeData> getVassalIncomeForPlayer(UUID overlordId) {
        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
            if (rel.overlordUUID.equals(overlordId)) {
                IColony colony = getColonyById(rel.colonyId);
                if (colony != null) {
                    int currentTaxBalance = TaxManager.getStoredTaxForColony(colony);
                    int tributeOwed = (int) (currentTaxBalance * rel.percent / 100.0);
                    
                    vassalIncomes.add(new VassalIncomeData(
                        rel.colonyId,
                        colony.getName(),
                        rel.percent,
                        tributeOwed,
                        rel.lastTribute,
                        rel.lastPayment,
                        false  // Manual collection disabled - tributes are auto-collected
                    ));
                }
            }
        }
        return vassalIncomes;
    }
    
    /**
     * Claim tribute from a specific vassal colony
     */
    public static int claimVassalTribute(UUID overlordId, int vassalColonyId) {
        VassalRelation rel = ACTIVE_VASSALS.get(vassalColonyId);
        if (rel == null || !rel.overlordUUID.equals(overlordId)) {
            return 0;
        }
        
        IColony vassalColony = getColonyById(vassalColonyId);
        if (vassalColony == null) return 0;
        
        int currentTaxBalance = TaxManager.getStoredTaxForColony(vassalColony);
        int tributeOwed = (int) (currentTaxBalance * rel.percent / 100.0);
        
        if (tributeOwed <= 0) return 0;
        
        // Transfer tribute from vassal to overlord
        TaxManager.adjustTax(vassalColony, -tributeOwed);
        
        IColony overlordColony = getPrimaryColonyOfPlayer(overlordId);
        if (overlordColony != null) {
            TaxManager.adjustTax(overlordColony, tributeOwed);
        }
        
        rel.lastPayment = System.currentTimeMillis();
        rel.lastTribute = tributeOwed;
        saveData();
        
        return tributeOwed;
    }
    
}
