package net.machiavelli.minecolonytax.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages raid penalties for colonies.
 * 
 * When a colony is successfully raided:
 * - They receive a tax generation penalty for a configurable duration
 * - The owner/officer can pay a fee to repair and remove the penalty
 */
@Mod.EventBusSubscriber(modid = "minecolonytax", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RaidPenaltyManager {

    private static final Logger LOGGER = LogManager.getLogger(RaidPenaltyManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/raid_penalties.json";

    /**
     * key = colonyId, value = timestamp when penalty expires
     * (System.currentTimeMillis())
     */
    private static final Map<Integer, Long> RAID_PENALTIES = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData(server);
        LOGGER.info("RaidPenaltyManager initialized with {} active penalties", RAID_PENALTIES.size());
    }

    public static void shutdown() {
        saveData();
    }

    // ==================== Helper Methods ====================

    /**
     * Get a colony by ID.
     */
    public static IColony getColony(int colonyId) {
        if (SERVER == null)
            return null;
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getColonyByWorld(colonyId, SERVER.overworld());
    }

    // ==================== Penalty Operations ====================

    /**
     * Apply a raid penalty to a colony after a successful raid.
     * 
     * @param colonyId The raided colony's ID
     */
    public static void applyRaidPenalty(int colonyId) {
        int durationHours = TaxConfig.getRaidPenaltyDurationHours();
        long expiryTime = System.currentTimeMillis() + (durationHours * 60L * 60L * 1000L);

        RAID_PENALTIES.put(colonyId, expiryTime);
        saveData();

        LOGGER.info("Applied raid penalty to colony {} - expires in {} hours", colonyId, durationHours);
    }

    /**
     * Check if a colony has an active raid penalty.
     * 
     * @param colonyId The colony ID to check
     * @return true if penalty is active
     */
    public static boolean hasRaidPenalty(int colonyId) {
        Long expiryTime = RAID_PENALTIES.get(colonyId);
        if (expiryTime == null) {
            return false;
        }

        // Clean up expired penalties
        if (System.currentTimeMillis() >= expiryTime) {
            RAID_PENALTIES.remove(colonyId);
            saveData();
            return false;
        }

        return true;
    }

    /**
     * Get the tax reduction multiplier for a colony (applies penalty if active).
     * 
     * @param colonyId The colony ID
     * @return multiplier (1.0 = no penalty, 0.75 = 25% reduction, etc.)
     */
    public static double getTaxMultiplier(int colonyId) {
        if (!hasRaidPenalty(colonyId)) {
            return 1.0;
        }

        double reductionPercent = TaxConfig.getRaidPenaltyTaxReductionPercent();
        return 1.0 - reductionPercent;
    }

    /**
     * Get remaining penalty time in hours.
     * 
     * @param colonyId The colony ID
     * @return hours remaining, or 0 if no penalty
     */
    public static int getRemainingPenaltyHours(int colonyId) {
        Long expiryTime = RAID_PENALTIES.get(colonyId);
        if (expiryTime == null || System.currentTimeMillis() >= expiryTime) {
            return 0;
        }

        long remainingMs = expiryTime - System.currentTimeMillis();
        return (int) (remainingMs / (60L * 60L * 1000L)) + 1; // Round up
    }

    // ==================== Repair Operations ====================

    /**
     * Calculate the repair cost for a colony.
     * 
     * @param colonyId The colony ID
     * @return repair cost in gold
     */
    public static int getRepairCost(int colonyId) {
        IColony colony = getColony(colonyId);
        if (colony == null)
            return 0;

        int taxBalance = TaxManager.getStoredTaxForColony(colony);
        double costPercent = TaxConfig.getRaidRepairCostPercent();
        return (int) Math.ceil(taxBalance * costPercent);
    }

    /**
     * Attempt to repair a colony and remove the raid penalty.
     * 
     * @param player   The player requesting repair
     * @param colonyId The colony ID to repair
     * @return true if repair succeeded
     */
    public static boolean repair(ServerPlayer player, int colonyId) {
        IColony colony = getColony(colonyId);
        if (colony == null) {
            player.sendSystemMessage(Component.literal("§cColony not found."));
            return false;
        }

        if (!hasRaidPenalty(colonyId)) {
            player.sendSystemMessage(Component.literal("§cThis colony has no raid damage to repair."));
            return false;
        }

        int repairCost = getRepairCost(colonyId);
        int taxBalance = TaxManager.getStoredTaxForColony(colony);

        if (taxBalance < repairCost) {
            player.sendSystemMessage(Component.literal(
                    String.format("§cInsufficient funds to repair! Need: %d, Have: %d", repairCost, taxBalance)));
            return false;
        }

        // Deduct repair cost from tax balance
        double repairPercent = (double) repairCost / taxBalance;
        TaxManager.deductColonyTax(colony, repairPercent);

        // Remove penalty
        RAID_PENALTIES.remove(colonyId);
        saveData();

        player.sendSystemMessage(Component.literal(
                String.format("§aColony repaired! Paid %d gold. Tax generation restored to normal.", repairCost)));
        LOGGER.info("Colony {} repaired for {} gold by {}", colonyId, repairCost, player.getName().getString());

        return true;
    }

    /**
     * Show repair status for a colony.
     * 
     * @param player   The player to show status to
     * @param colonyId The colony ID
     */
    public static void showRepairStatus(ServerPlayer player, int colonyId) {
        IColony colony = getColony(colonyId);
        if (colony == null) {
            player.sendSystemMessage(Component.literal("§cColony not found."));
            return;
        }

        if (!hasRaidPenalty(colonyId)) {
            player.sendSystemMessage(Component.literal("§aNo raid damage - colony is in good condition."));
            return;
        }

        int remainingHours = getRemainingPenaltyHours(colonyId);
        double reductionPercent = TaxConfig.getRaidPenaltyTaxReductionPercent() * 100;
        int repairCost = getRepairCost(colonyId);
        int taxBalance = TaxManager.getStoredTaxForColony(colony);

        player.sendSystemMessage(Component.literal("§6=== Raid Damage Status ==="));
        player.sendSystemMessage(Component.literal(String.format(
                "§cTax Penalty: §f%.0f%% reduction", reductionPercent)));
        player.sendSystemMessage(Component.literal(String.format(
                "§cTime Remaining: §f%d hours", remainingHours)));
        player.sendSystemMessage(Component.literal(String.format(
                "§eRepair Cost: §f%d gold", repairCost)));
        player.sendSystemMessage(Component.literal(String.format(
                "§eYour Balance: §f%d gold", taxBalance)));

        if (taxBalance >= repairCost) {
            player.sendSystemMessage(Component.literal("§aUse /wnt repair to restore normal tax generation."));
        } else {
            player.sendSystemMessage(Component.literal("§cInsufficient funds for repair."));
        }
    }

    // ==================== Persistence ====================

    private static void loadData(MinecraftServer server) {
        Path path = Paths.get(STORAGE_FILE);
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = new FileReader(path.toFile())) {
            Type type = new TypeToken<Map<Integer, Long>>() {
            }.getType();
            Map<Integer, Long> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                RAID_PENALTIES.clear();
                RAID_PENALTIES.putAll(loaded);

                // Clean up expired entries
                long now = System.currentTimeMillis();
                RAID_PENALTIES.entrySet().removeIf(e -> e.getValue() <= now);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load raid penalties data", e);
        }
    }

    private static void saveData() {
        Path path = Paths.get(STORAGE_FILE);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = new FileWriter(path.toFile())) {
                GSON.toJson(RAID_PENALTIES, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save raid penalties data", e);
        }
    }
}
