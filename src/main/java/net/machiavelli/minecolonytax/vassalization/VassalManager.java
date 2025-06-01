package net.machiavelli.minecolonytax.vassalization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = "minecolonytax", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VassalManager {

    private static final Logger LOGGER = LogManager.getLogger(VassalManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/vassals.json";

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

        // notify target colony owner/officers
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

    /* ---------------- helpers ------------- */
    private static boolean isPlayerManagerOfColony(ServerPlayer player, IColony colony) {
        var rank = colony.getPermissions().getRank(player.getUUID());
        return rank != null && rank.isColonyManager();
    }

    private static void sendToColonyManagers(IColony colony, Component message) {
        colony.getPermissions().getPlayersByRank(colony.getPermissions().getRankOfficer())
                .forEach(cp -> sendOrQueue(cp.getID(), message));
        sendOrQueue(colony.getPermissions().getOwner(), message);
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
        File f = new File(server.getServerDirectory(), STORAGE_FILE);
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
        File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
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
        public VassalRelation(int colonyId, UUID overlordUUID, int percent, long lastPayment) {
            this.colonyId = colonyId;
            this.overlordUUID = overlordUUID;
            this.percent = percent;
            this.lastPayment = lastPayment;
            this.lastTribute = 0;
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
}
