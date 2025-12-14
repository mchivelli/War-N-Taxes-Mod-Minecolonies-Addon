package net.machiavelli.minecolonytax.economy;

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
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages War Chest balances for colonies.
 * 
 * War Chest is a separate fund that colonies must maintain to declare and
 * sustain wars.
 * - Funds can be deposited from colony tax revenue
 * - Declaring war requires minimum war chest based on target's balance
 * - War chest drains during active wars
 * - Optional auto-surrender when war chest depletes
 */
@Mod.EventBusSubscriber(modid = "minecolonytax", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WarChestManager {

    private static final Logger LOGGER = LogManager.getLogger(WarChestManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/warchests.json";

    /** key = colonyId, value = war chest balance */
    private static final Map<Integer, Integer> WAR_CHESTS = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData(server);
        LOGGER.info("WarChestManager initialized with {} war chests", WAR_CHESTS.size());
    }

    public static void shutdown() {
        saveData();
    }

    // ==================== War Chest Operations ====================

    /**
     * Get the current war chest balance for a colony.
     */
    public static int getWarChestBalance(int colonyId) {
        return WAR_CHESTS.getOrDefault(colonyId, 0);
    }

    /**
     * Deposit funds into a colony's war chest from their tax balance.
     * 
     * @param player   The player making the deposit
     * @param colonyId The colony ID
     * @param amount   Amount to deposit
     * @return true if deposit succeeded, false otherwise
     */
    public static boolean deposit(ServerPlayer player, int colonyId, int amount) {
        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("Amount must be positive.").withStyle(ChatFormatting.RED));
            return false;
        }

        IColony colony = getColony(colonyId);
        if (colony == null) {
            player.sendSystemMessage(Component.literal("Colony not found.").withStyle(ChatFormatting.RED));
            return false;
        }

        int currentTax = TaxManager.getStoredTaxForColony(colony);
        if (currentTax < amount) {
            player.sendSystemMessage(
                    Component.literal("Insufficient tax balance. You have " + currentTax + " available.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        int currentChest = getWarChestBalance(colonyId);
        int maxCapacity = TaxConfig.getWarChestMaxCapacity();

        if (currentChest >= maxCapacity) {
            player.sendSystemMessage(Component.literal("War chest is at maximum capacity (" + maxCapacity + ").")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        // Cap deposit to not exceed max capacity
        int actualDeposit = Math.min(amount, maxCapacity - currentChest);

        // Deduct from tax balance
        TaxManager.adjustTax(colony, -actualDeposit);

        // Add to war chest
        WAR_CHESTS.put(colonyId, currentChest + actualDeposit);
        saveData();

        player.sendSystemMessage(Component.literal("Deposited " + actualDeposit + " into war chest. New balance: " +
                (currentChest + actualDeposit)).withStyle(ChatFormatting.GREEN));

        if (actualDeposit < amount) {
            player.sendSystemMessage(Component.literal("(Capped to max capacity of " + maxCapacity + ")")
                    .withStyle(ChatFormatting.GRAY));
        }

        return true;
    }

    /**
     * Withdraw funds from war chest back to colony tax balance.
     * 
     * @param player   The player making the withdrawal
     * @param colonyId The colony ID
     * @param amount   Amount to withdraw
     * @return true if withdrawal succeeded, false otherwise
     */
    public static boolean withdraw(ServerPlayer player, int colonyId, int amount) {
        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("Amount must be positive.").withStyle(ChatFormatting.RED));
            return false;
        }

        int currentChest = getWarChestBalance(colonyId);
        if (currentChest < amount) {
            player.sendSystemMessage(
                    Component.literal("Insufficient war chest balance. You have " + currentChest + " available.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        // Deduct from war chest
        WAR_CHESTS.put(colonyId, currentChest - amount);

        // Add to tax balance
        IColony colony = getColony(colonyId);
        if (colony != null) {
            TaxManager.adjustTax(colony, amount);
        }
        saveData();

        player.sendSystemMessage(Component.literal("Withdrew " + amount + " from war chest. New balance: " +
                (currentChest - amount)).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /**
     * Get the status of a colony's war chest.
     */
    public static void sendStatus(ServerPlayer player, int colonyId) {
        if (!TaxConfig.isWarChestEnabled()) {
            player.sendSystemMessage(Component.literal("War Chest system is disabled.").withStyle(ChatFormatting.RED));
            return;
        }

        int balance = getWarChestBalance(colonyId);
        int maxCapacity = TaxConfig.getWarChestMaxCapacity();
        int drainRate = TaxConfig.getWarChestDrainPerMinute();

        player.sendSystemMessage(Component.literal("=== War Chest Status ===").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(
                Component.literal("Balance: " + balance + " / " + maxCapacity).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(
                Component.literal("Drain Rate (during war): " + drainRate + "/min").withStyle(ChatFormatting.GRAY));

        if (balance > 0 && drainRate > 0) {
            int minutesOfWar = balance / drainRate;
            player.sendSystemMessage(Component.literal("Can sustain war for: ~" + minutesOfWar + " minutes")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // ==================== War Declaration Checks ====================

    /**
     * Check if a colony can declare war based on their war chest.
     * 
     * @param attackerColonyId The attacking colony ID
     * @param defenderColonyId The defending colony ID
     * @return true if war chest requirements are met
     */
    public static boolean canDeclareWar(int attackerColonyId, int defenderColonyId) {
        if (!TaxConfig.isWarChestEnabled()) {
            return true; // War chest disabled, allow war
        }

        int attackerChest = getWarChestBalance(attackerColonyId);
        IColony defenderColony = getColony(defenderColonyId);
        int defenderTax = defenderColony != null ? TaxManager.getStoredTaxForColony(defenderColony) : 0;
        double requiredPercent = TaxConfig.getWarChestMinPercentOfTarget();

        int requiredAmount = (int) Math.ceil(defenderTax * requiredPercent);

        return attackerChest >= requiredAmount;
    }

    /**
     * Get the required war chest amount to declare war on a target.
     */
    public static int getRequiredWarChest(int defenderColonyId) {
        if (!TaxConfig.isWarChestEnabled()) {
            return 0;
        }

        IColony defenderColony = getColony(defenderColonyId);
        int defenderTax = defenderColony != null ? TaxManager.getStoredTaxForColony(defenderColony) : 0;
        double requiredPercent = TaxConfig.getWarChestMinPercentOfTarget();

        return (int) Math.ceil(defenderTax * requiredPercent);
    }

    /**
     * Get a message explaining why war cannot be declared.
     */
    public static Component getWarDeclarationBlockedMessage(int attackerColonyId, int defenderColonyId) {
        int attackerChest = getWarChestBalance(attackerColonyId);
        int required = getRequiredWarChest(defenderColonyId);

        return Component.literal("Insufficient war chest! You have " + attackerChest + " but need " + required +
                " (" + (int) (TaxConfig.getWarChestMinPercentOfTarget() * 100) + "% of target's tax balance).")
                .withStyle(ChatFormatting.RED);
    }

    // ==================== War Drain Operations ====================

    /**
     * Drain war chest during active war. Called every minute during war.
     * 
     * @param colonyId The colony in war
     * @return remaining balance after drain, or -1 if depleted (triggers surrender)
     */
    public static int drainWarChest(int colonyId) {
        if (!TaxConfig.isWarChestEnabled()) {
            return Integer.MAX_VALUE; // No drain if disabled
        }

        int drainAmount = TaxConfig.getWarChestDrainPerMinute();
        int currentBalance = getWarChestBalance(colonyId);

        int newBalance = Math.max(0, currentBalance - drainAmount);
        WAR_CHESTS.put(colonyId, newBalance);
        saveData();

        if (newBalance <= 0 && TaxConfig.isWarChestAutoSurrenderEnabled()) {
            LOGGER.info("Colony {} war chest depleted - triggering auto-surrender", colonyId);
            return -1; // Signal auto-surrender
        }

        return newBalance;
    }

    /**
     * Deduct a specific amount from the war chest (for one-time raid costs).
     * 
     * @param colonyId The colony ID
     * @param amount   Amount to deduct
     * @return new balance after deduction
     */
    public static int deductFromWarChest(int colonyId, int amount) {
        int currentBalance = getWarChestBalance(colonyId);
        int newBalance = Math.max(0, currentBalance - amount);
        WAR_CHESTS.put(colonyId, newBalance);
        saveData();
        LOGGER.info("Deducted {} from colony {} war chest. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Add a specific amount to the war chest.
     * 
     * @param colonyId The colony ID
     * @param amount   Amount to add
     * @return new balance after addition
     */
    public static int addToWarChest(int colonyId, int amount) {
        int currentBalance = getWarChestBalance(colonyId);
        int maxCapacity = TaxConfig.getWarChestMaxCapacity();
        int newBalance = Math.min(maxCapacity, currentBalance + amount);

        WAR_CHESTS.put(colonyId, newBalance);
        saveData();
        LOGGER.info("Added {} to colony {} war chest. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Check if war chest is depleted (for auto-surrender).
     */
    public static boolean isWarChestDepleted(int colonyId) {
        return TaxConfig.isWarChestEnabled() && getWarChestBalance(colonyId) <= 0;
    }

    // ==================== Persistence ====================

    private static void loadData(MinecraftServer server) {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            LOGGER.info("No war chest data file found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<Integer, Integer>>() {
            }.getType();
            Map<Integer, Integer> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                WAR_CHESTS.clear();
                WAR_CHESTS.putAll(loaded);
            }
            LOGGER.info("Loaded {} war chest records", WAR_CHESTS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load war chest data: {}", e.getMessage());
        }
    }

    private static void saveData() {
        File file = new File(STORAGE_FILE);
        file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(WAR_CHESTS, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save war chest data: {}", e.getMessage());
        }
    }

    // ==================== Utility ====================

    /**
     * Get a colony by ID.
     */
    public static IColony getColony(int colonyId) {
        if (SERVER == null)
            return null;
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getColonyByWorld(colonyId, SERVER.overworld());
    }
}
