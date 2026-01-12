package net.machiavelli.minecolonytax.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.IMinecoloniesAPI;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages trade routes between colonies for passive income generation.
 */
public class TradeRouteManager {

    private static final Logger LOGGER = LogManager.getLogger(TradeRouteManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/warntax/trade_routes.json";

    // Active trade routes indexed by lower colony ID for deduplication
    private static final Map<String, TradeRouteData> ACTIVE_ROUTES = new ConcurrentHashMap<>();

    // Pending proposals: key = "proposer_target"
    private static final Map<String, TradeRouteProposal> PENDING_PROPOSALS = new ConcurrentHashMap<>();

    private static MinecraftServer server;

    /* ============== Data Models ============== */

    public static class TradeRouteData {
        public int colonyId1;
        public int colonyId2;
        public int distanceChunks;
        public boolean active;
        public long createdTime;

        public TradeRouteData() {
        }

        public TradeRouteData(int c1, int c2, int distance) {
            this.colonyId1 = Math.min(c1, c2);
            this.colonyId2 = Math.max(c1, c2);
            this.distanceChunks = distance;
            this.active = true;
            this.createdTime = System.currentTimeMillis();
        }

        public String getKey() {
            return colonyId1 + "_" + colonyId2;
        }

        public int getPartner(int myId) {
            return myId == colonyId1 ? colonyId2 : colonyId1;
        }

        public boolean involves(int colonyId) {
            return colonyId1 == colonyId || colonyId2 == colonyId;
        }
    }

    public static class TradeRouteProposal {
        public int proposerColonyId;
        public UUID proposerPlayerId;
        public int targetColonyId;
        public long proposalTime;
        public long expirationTime;

        public TradeRouteProposal() {
        }

        public TradeRouteProposal(int proposer, UUID player, int target) {
            this.proposerColonyId = proposer;
            this.proposerPlayerId = player;
            this.targetColonyId = target;
            this.proposalTime = System.currentTimeMillis();
            this.expirationTime = proposalTime + (30 * 60 * 1000); // 30 min
        }

        public String getKey() {
            return proposerColonyId + "_" + targetColonyId;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }

    private static class PersistenceData {
        List<TradeRouteData> routes = new ArrayList<>();
        List<TradeRouteProposal> proposals = new ArrayList<>();
    }

    /* ============== Lifecycle ============== */

    public static void initialize(MinecraftServer srv) {
        server = srv;
        loadData();
        LOGGER.info("TradeRouteManager initialized with {} active routes", ACTIVE_ROUTES.size());
    }

    public static void shutdown() {
        saveData();
        ACTIVE_ROUTES.clear();
        PENDING_PROPOSALS.clear();
        LOGGER.info("TradeRouteManager shut down");
    }

    private static void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists())
            return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<PersistenceData>() {
            }.getType();
            PersistenceData data = GSON.fromJson(reader, type);
            if (data != null) {
                for (TradeRouteData route : data.routes) {
                    ACTIVE_ROUTES.put(route.getKey(), route);
                }
                for (TradeRouteProposal prop : data.proposals) {
                    if (!prop.isExpired()) {
                        PENDING_PROPOSALS.put(prop.getKey(), prop);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load trade route data", e);
        }
    }

    private static void saveData() {
        try {
            File file = new File(DATA_FILE);
            file.getParentFile().mkdirs();

            PersistenceData data = new PersistenceData();
            data.routes = new ArrayList<>(ACTIVE_ROUTES.values());
            data.proposals = new ArrayList<>(PENDING_PROPOSALS.values());

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save trade route data", e);
        }
    }

    /* ============== Proposal Management ============== */

    public static String proposeTradeRoute(ServerPlayer player, int targetColonyId) {
        if (!TaxConfig.isTradeRoutesEnabled()) {
            return "§cTrade routes are disabled.";
        }

        IColony myColony = getPlayerPrimaryColony(player);
        if (myColony == null) {
            return "§cYou must own a colony to propose trade routes.";
        }

        int myId = myColony.getID();
        if (myId == targetColonyId) {
            return "§cYou cannot create a trade route with yourself.";
        }

        if (!isColonyOwner(myColony, player)) {
            return "§cOnly colony owners can propose trade routes.";
        }

        IColony targetColony = getColonyById(targetColonyId);
        if (targetColony == null) {
            return "§cColony #" + targetColonyId + " not found.";
        }

        if (hasTradeRoute(myId, targetColonyId)) {
            return "§cYou already have a trade route with this colony.";
        }

        String proposalKey = myId + "_" + targetColonyId;
        if (PENDING_PROPOSALS.containsKey(proposalKey)) {
            return "§cYou already have a pending proposal with this colony.";
        }

        int myCount = getActiveRouteCount(myId);
        if (myCount >= TaxConfig.getMaxTradeRoutesPerColony()) {
            return "§cMax routes reached (" + TaxConfig.getMaxTradeRoutesPerColony() + ").";
        }

        int targetCount = getActiveRouteCount(targetColonyId);
        if (targetCount >= TaxConfig.getMaxTradeRoutesPerColony()) {
            return "§cTarget has max routes.";
        }

        int distance = calculateDistance(myColony, targetColony);
        if (distance > TaxConfig.getTradeRouteMaxDistanceChunks()) {
            return "§cToo far! Max: " + TaxConfig.getTradeRouteMaxDistanceChunks() + " chunks, actual: " + distance;
        }

        TradeRouteProposal proposal = new TradeRouteProposal(myId, player.getUUID(), targetColonyId);
        PENDING_PROPOSALS.put(proposalKey, proposal);
        saveData();

        ServerPlayer targetOwner = getColonyOwner(targetColony);
        if (targetOwner != null) {
            int income = distance * TaxConfig.getTradeRouteIncomePerChunk();
            targetOwner.sendSystemMessage(Component.literal(
                    "§6Trade route proposal from " + myColony.getName() + "! §e" + distance + " chunks, +" + income
                            + " income/cycle. §6/wnt traderoute accept " + myId));
        }

        return "§aProposal sent to " + targetColony.getName() + " (" + distance + " chunks).";
    }

    public static String acceptTradeRoute(ServerPlayer player, int proposerColonyId) {
        IColony myColony = getPlayerPrimaryColony(player);
        if (myColony == null) {
            return "§cYou must own a colony.";
        }

        int myId = myColony.getID();
        if (!isColonyOwner(myColony, player)) {
            return "§cOnly owners can accept routes.";
        }

        String key = proposerColonyId + "_" + myId;
        TradeRouteProposal proposal = PENDING_PROPOSALS.get(key);
        if (proposal == null) {
            return "§cNo proposal from colony #" + proposerColonyId + ".";
        }

        if (proposal.isExpired()) {
            PENDING_PROPOSALS.remove(key);
            saveData();
            return "§cProposal expired.";
        }

        IColony proposerColony = getColonyById(proposerColonyId);
        if (proposerColony == null) {
            PENDING_PROPOSALS.remove(key);
            saveData();
            return "§cProposer colony no longer exists.";
        }

        int distance = calculateDistance(myColony, proposerColony);
        TradeRouteData route = new TradeRouteData(myId, proposerColonyId, distance);
        ACTIVE_ROUTES.put(route.getKey(), route);
        PENDING_PROPOSALS.remove(key);
        saveData();

        ServerPlayer proposerPlayer = server.getPlayerList().getPlayer(proposal.proposerPlayerId);
        if (proposerPlayer != null) {
            proposerPlayer
                    .sendSystemMessage(Component.literal("§a" + myColony.getName() + " accepted your trade route!"));
        }

        int income = distance * TaxConfig.getTradeRouteIncomePerChunk();
        return "§aTrade route established! Income: " + income + "/cycle";
    }

    public static String denyTradeRoute(ServerPlayer player, int proposerColonyId) {
        IColony myColony = getPlayerPrimaryColony(player);
        if (myColony == null)
            return "§cYou must own a colony.";

        String key = proposerColonyId + "_" + myColony.getID();
        if (PENDING_PROPOSALS.remove(key) != null) {
            saveData();
            return "§aProposal denied.";
        }
        return "§cNo proposal from #" + proposerColonyId + ".";
    }

    public static String cancelTradeRoute(ServerPlayer player, int partnerColonyId) {
        IColony myColony = getPlayerPrimaryColony(player);
        if (myColony == null)
            return "§cYou must own a colony.";

        if (!isColonyOwner(myColony, player)) {
            return "§cOnly owners can cancel routes.";
        }

        int myId = myColony.getID();
        String routeKey = Math.min(myId, partnerColonyId) + "_" + Math.max(myId, partnerColonyId);
        TradeRouteData removed = ACTIVE_ROUTES.remove(routeKey);

        if (removed != null) {
            saveData();
            IColony partnerColony = getColonyById(partnerColonyId);
            if (partnerColony != null) {
                ServerPlayer partnerOwner = getColonyOwner(partnerColony);
                if (partnerOwner != null) {
                    partnerOwner.sendSystemMessage(
                            Component.literal("§6Trade route with " + myColony.getName() + " cancelled."));
                }
            }
            return "§aTrade route cancelled.";
        }
        return "§cNo route with #" + partnerColonyId + ".";
    }

    /* ============== Income/Maintenance ============== */

    public static int calculateTradeRouteIncome(int colonyId) {
        if (!TaxConfig.isTradeRoutesEnabled())
            return 0;

        int income = 0;
        for (TradeRouteData route : ACTIVE_ROUTES.values()) {
            if (route.involves(colonyId) && route.active) {
                income += route.distanceChunks * TaxConfig.getTradeRouteIncomePerChunk();
            }
        }
        // Divide by 2 since each route is counted once per colony
        return income / 2;
    }

    public static int deductMaintenance(IColony colony) {
        if (!TaxConfig.isTradeRoutesEnabled())
            return 0;

        int maintenanceCost = TaxConfig.getTradeRouteMaintenanceCost();
        if (maintenanceCost <= 0)
            return 0;

        int colonyId = colony.getID();
        int totalMaintenance = 0;

        for (TradeRouteData route : ACTIVE_ROUTES.values()) {
            if (route.involves(colonyId) && route.active) {
                totalMaintenance += maintenanceCost;
            }
        }

        // Divide by 2 since routes are shared
        return totalMaintenance / 2;
    }

    /* ============== Query Methods ============== */

    public static List<TradeRouteData> getRoutesForColony(int colonyId) {
        return ACTIVE_ROUTES.values().stream()
                .filter(r -> r.involves(colonyId))
                .collect(Collectors.toList());
    }

    public static List<TradeRouteProposal> getProposalsForColony(int colonyId) {
        return PENDING_PROPOSALS.values().stream()
                .filter(p -> p.targetColonyId == colonyId && !p.isExpired())
                .collect(Collectors.toList());
    }

    public static int getActiveRouteCount(int colonyId) {
        return (int) ACTIVE_ROUTES.values().stream()
                .filter(r -> r.involves(colonyId) && r.active)
                .count();
    }

    public static boolean hasTradeRoute(int c1, int c2) {
        String key = Math.min(c1, c2) + "_" + Math.max(c1, c2);
        return ACTIVE_ROUTES.containsKey(key);
    }

    /* ============== Helpers ============== */

    public static int calculateDistance(IColony c1, IColony c2) {
        BlockPos p1 = c1.getCenter();
        BlockPos p2 = c2.getCenter();
        ChunkPos ch1 = new ChunkPos(p1);
        ChunkPos ch2 = new ChunkPos(p2);
        int dx = ch1.x - ch2.x;
        int dz = ch1.z - ch2.z;
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static IColony getPlayerPrimaryColony(ServerPlayer player) {
        IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
        for (ServerLevel world : server.getAllLevels()) {
            for (IColony colony : mgr.getColonies(world)) {
                if (colony.getPermissions().getOwner().equals(player.getUUID())) {
                    return colony;
                }
            }
        }
        return null;
    }

    public static IColony getColonyById(int colonyId) {
        IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
        for (ServerLevel world : server.getAllLevels()) {
            IColony colony = mgr.getColonyByWorld(colonyId, world);
            if (colony != null)
                return colony;
        }
        return null;
    }

    private static ServerPlayer getColonyOwner(IColony colony) {
        UUID ownerId = colony.getPermissions().getOwner();
        return server.getPlayerList().getPlayer(ownerId);
    }

    private static boolean isColonyOwner(IColony colony, ServerPlayer player) {
        return colony.getPermissions().getOwner().equals(player.getUUID());
    }

    public static void cleanupExpiredProposals() {
        PENDING_PROPOSALS.entrySet().removeIf(e -> e.getValue().isExpired());
        saveData();
    }
}
