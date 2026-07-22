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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
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
public class WarChestManager {

    private static final Logger LOGGER = LogManager.getLogger(WarChestManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/warchests.json";

    /** key = colonyId, value = war chest balance */
    private static final Map<Integer, Integer> WAR_CHESTS = new ConcurrentHashMap<>();

    /**
     * Tracks colonies that are the DEFENDER in an active war (home-field advantage).
     * WarSystem manages entries here via setColonyAsDefender / clearColonyRole.
     */
    private static final Set<Integer> DEFENDER_COLONIES = ConcurrentHashMap.newKeySet();

    private static MinecraftServer SERVER;

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData(server);
        if (TaxConfig.isNormalLogging()) LOGGER.info("WarChestManager initialized with {} war chests", WAR_CHESTS.size());
    }

    public static void shutdown() {
        saveData();
    }

    // ==================== War Role Tracking ====================

    /**
     * Mark a colony as the DEFENDER in a war (grants drain reduction benefit).
     * Called by WarSystem when war starts.
     */
    public static void setColonyAsDefender(int colonyId) {
        DEFENDER_COLONIES.add(colonyId);
    }

    /**
     * Clear the war role for a colony (called when war ends).
     */
    public static void clearColonyRole(int colonyId) {
        DEFENDER_COLONIES.remove(colonyId);
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
        int maxCapacity = getEffectiveMaxCapacity(colonyId);

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
        int maxCapacity = getEffectiveMaxCapacity(colonyId);
        int effectiveDrain = computeEffectiveDrain(colonyId);

        player.sendSystemMessage(Component.literal("=== War Chest Status ===").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(
                Component.literal("Balance: " + balance + " / " + maxCapacity).withStyle(ChatFormatting.WHITE));

        // "% of base capacity", not "% of capacity": the drain percent is applied to the configured
        // base cap (see computeEffectiveDrain), NOT the effective cap shown on the Balance line above.
        // A TREASURY_CAP vault upgrade deliberately does not speed up the drain, so labelling it plain
        // "% of capacity" next to the larger effective figure would misstate the actual drain.
        String drainDesc = TaxConfig.isWarChestDrainUsePercent()
                ? String.format("%d/min (%.0f%% of base capacity)", effectiveDrain, TaxConfig.getWarChestDrainPercent() * 100)
                : effectiveDrain + "/min (flat)";
        player.sendSystemMessage(
                Component.literal("Drain Rate (during war): " + drainDesc).withStyle(ChatFormatting.GRAY));

        boolean isDefender = DEFENDER_COLONIES.contains(colonyId);
        if (isDefender) {
            player.sendSystemMessage(
                    Component.literal("Home Field Advantage: drain reduced by "
                            + (int)(TaxConfig.getWarDefenderDrainReduction() * 100) + "% (defending colony)")
                            .withStyle(ChatFormatting.GREEN));
        }

        if (balance > 0 && effectiveDrain > 0) {
            int minutesOfWar = balance / effectiveDrain;
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
        int required = getRequiredWarChest(attackerColonyId, defenderColonyId);

        return attackerChest >= required;
    }

    /**
     * Get the required war chest amount to declare war on a target.
     * Returns the higher of: (defender's tax * targetPercent) vs (attacker's tax * ownPercent).
     */
    public static int getRequiredWarChest(int attackerColonyId, int defenderColonyId) {
        if (!TaxConfig.isWarChestEnabled()) {
            return 0;
        }

        IColony defenderColony = getColony(defenderColonyId);
        IColony attackerColony = getColony(attackerColonyId);
        int defenderTax = defenderColony != null ? TaxManager.getStoredTaxForColony(defenderColony) : 0;
        int attackerTax = attackerColony != null ? TaxManager.getStoredTaxForColony(attackerColony) : 0;

        int fromDefender = (int) Math.ceil(Math.max(0, defenderTax) * TaxConfig.getWarChestMinPercentOfTarget());
        int fromOwn = (int) Math.ceil(Math.max(0, attackerTax) * TaxConfig.getWarChestMinPercentOfOwnTax());

        return Math.max(fromDefender, fromOwn);
    }

    /**
     * Legacy overload for callers that only know the defender ID.
     */
    public static int getRequiredWarChest(int defenderColonyId) {
        return getRequiredWarChest(0, defenderColonyId);
    }

    /**
     * Get a message explaining why war cannot be declared.
     */
    public static Component getWarDeclarationBlockedMessage(int attackerColonyId, int defenderColonyId) {
        int attackerChest = getWarChestBalance(attackerColonyId);
        int required = getRequiredWarChest(attackerColonyId, defenderColonyId);

        IColony attackerColony = getColony(attackerColonyId);
        IColony defenderColony = getColony(defenderColonyId);
        int attackerTax = attackerColony != null ? TaxManager.getStoredTaxForColony(attackerColony) : 0;
        int defenderTax = defenderColony != null ? TaxManager.getStoredTaxForColony(defenderColony) : 0;
        int fromDefender = (int) Math.ceil(Math.max(0, defenderTax) * TaxConfig.getWarChestMinPercentOfTarget());
        int fromOwn = (int) Math.ceil(Math.max(0, attackerTax) * TaxConfig.getWarChestMinPercentOfOwnTax());

        String reason = fromOwn >= fromDefender
                ? String.format("%.0f%% of your own tax (%d)", TaxConfig.getWarChestMinPercentOfOwnTax() * 100, fromOwn)
                : String.format("%.0f%% of target's tax (%d)", TaxConfig.getWarChestMinPercentOfTarget() * 100, fromDefender);

        return Component.literal("Insufficient war chest! You have " + attackerChest + " but need " + required +
                " (" + reason + ").")
                .withStyle(ChatFormatting.RED);
    }

    // ==================== War Drain Operations ====================

    /**
     * Compute the effective drain per minute for a colony, accounting for
     * percentage-based drain and the defender drain reduction (home field advantage).
     */
    public static int computeEffectiveDrain(int colonyId) {
        int baseDrain;
        if (TaxConfig.isWarChestDrainUsePercent()) {
            baseDrain = (int) Math.ceil(TaxConfig.getWarChestMaxCapacity() * TaxConfig.getWarChestDrainPercent());
        } else {
            baseDrain = TaxConfig.getWarChestDrainPerMinute();
        }

        // Defenders enjoy a drain reduction (home field advantage)
        if (DEFENDER_COLONIES.contains(colonyId)) {
            double reduction = TaxConfig.getWarDefenderDrainReduction();
            baseDrain = (int) Math.ceil(baseDrain * (1.0 - reduction));
        }

        return Math.max(0, baseDrain);
    }

    public static int drainWarChest(int colonyId) {
        if (!TaxConfig.isWarChestEnabled()) {
            return Integer.MAX_VALUE; // No drain if disabled
        }

        int drainAmount = computeEffectiveDrain(colonyId);
        int currentBalance = getWarChestBalance(colonyId);

        int newBalance = Math.max(0, currentBalance - drainAmount);
        WAR_CHESTS.put(colonyId, newBalance);

        if (newBalance <= 0 && TaxConfig.isWarChestAutoSurrenderEnabled()) {
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} war chest depleted - triggering auto-surrender", colonyId);
            return -1; // Signal auto-surrender
        }

        return newBalance;
    }

    /**
     * Deduct a specific amount from the war chest (for one-time raid costs).
     */
    public static int deductFromWarChest(int colonyId, int amount) {
        int currentBalance = getWarChestBalance(colonyId);
        int newBalance = Math.max(0, currentBalance - amount);
        WAR_CHESTS.put(colonyId, newBalance);
        if (TaxConfig.isNormalLogging()) LOGGER.info("Deducted {} from colony {} war chest. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Effective war-chest ceiling for a colony: the configured base capacity plus any
     * TREASURY_CAP investment bonus (Treasury Vault, flat per level). Used at every site
     * that enforces or displays the cap so the boosted ceiling stays consistent across the
     * deposit path, automated funding, and the GUIs. Returns the base cap when the upgrade
     * system is disabled or the colony has no levels.
     */
    public static int getEffectiveMaxCapacity(int colonyId) {
        int base = TaxConfig.getWarChestMaxCapacity();
        if (!TaxConfig.isUpgradesEnabled()) return base;
        // Saturating add in long, then clamp to [0, Integer.MAX_VALUE]. Both base (WarChestMaxCapacity)
        // and the per-level flat bonus are admin-configurable up to Integer.MAX_VALUE, so int math
        // could overflow into a NEGATIVE effective cap — which would make deposits reject everything
        // and Math.min in addToWarChest return the negative cap. getTreasuryCapBonus already returns
        // long (its own level*flat can exceed int range); doing the sum in long keeps the ceiling
        // monotonic so the upgrade only ever grows the cap.
        long effective = (long) base + net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getTreasuryCapBonus(colonyId);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, effective));
    }

    /**
     * Add a specific amount to the war chest.
     */
    public static int addToWarChest(int colonyId, int amount) {
        int currentBalance = getWarChestBalance(colonyId);
        int maxCapacity = getEffectiveMaxCapacity(colonyId);
        int newBalance = Math.min(maxCapacity, currentBalance + amount);

        WAR_CHESTS.put(colonyId, newBalance);
        saveData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("Added {} to colony {} war chest. New balance: {}", amount, colonyId, newBalance);
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
            if (TaxConfig.isNormalLogging()) LOGGER.info("No war chest data file found, starting fresh");
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
            if (TaxConfig.isNormalLogging()) LOGGER.info("Loaded {} war chest records", WAR_CHESTS.size());
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
