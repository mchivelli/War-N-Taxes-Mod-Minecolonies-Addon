package net.machiavelli.minecolonytax.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.integration.CurrencyService;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Treasury balances for colonies.
 *
 * The Treasury is a separate fund that colonies must maintain to declare and
 * sustain wars. Funds are deposited from colony tax revenue. Declaring war
 * requires a minimum treasury balance relative to the target's holdings.
 * The treasury drains during active wars; optional auto-surrender triggers
 * when it runs dry.
 */
@Mod.EventBusSubscriber(modid = "minecolonytax", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TreasuryManager {

    private static final Logger LOGGER = LogManager.getLogger(TreasuryManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/warchests.json";

    /** key = colonyId, value = treasury balance */
    private static final Map<Integer, Integer> TREASURIES = new ConcurrentHashMap<>();

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
        if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager initialized with {} treasury records", TREASURIES.size());
    }

    public static void shutdown() {
        // Route the final save through the async executor too, so warchests.json is only
        // ever written by the single async thread — never concurrently from the main
        // thread (which could race an in-flight async write of the same .tmp). The
        // ServerStopping hook calls AsyncSaveExecutor.shutdownAndFlush() afterwards,
        // which barriers on the worker then flushes this pending write to disk.
        save();
    }

    /** Public runtime save trigger — async + coalesced (audit H1/H2). */
    public static void save() {
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

    // ==================== Treasury Operations ====================

    /**
     * Get the current treasury balance for a colony.
     */
    public static int getTreasuryBalance(int colonyId) {
        return TREASURIES.getOrDefault(colonyId, 0);
    }

    /**
     * Deposit funds into the treasury from the colony's tax balance (default source).
     * Kept for backwards-compatible callers (e.g. FactionCommand).
     *
     * CONTRACT: when this returns {@code false}, NO funds have been taken from the
     * caller-side source by this method — however callers that already debited
     * a separate ledger (e.g. {@code FactionData#withdrawTax}) BEFORE invoking
     * this method are responsible for refunding that ledger on a false return.
     * Ignoring the return value can permanently destroy coins. See FactionCommand
     * for the canonical "withdraw-then-deposit-then-refund-on-fail" pattern.
     */
    public static boolean deposit(ServerPlayer player, int colonyId, int amount) {
        return deposit(player, colonyId, amount, CurrencyService.Source.TAX_BALANCE);
    }

    /**
     * Deposit funds into the treasury from the specified source.
     *
     * Sources:
     *   TAX_BALANCE — deducted from the colony's accumulated tax ledger (default)
     *   WALLET      — deducted from the player's SDMShop / SDMEconomy balance
     *   INVENTORY   — physical currency items are consumed from the player's inventory
     *
     * CONTRACT: a {@code false} return means NOTHING was moved from the source
     * by this method. Causes include treasury disabled, non-positive amount,
     * colony not found, source unavailable, insufficient source balance, or
     * treasury already at max capacity. Callers must check this return value;
     * upstream ledger debits (faction pool, etc.) must be refunded by the caller
     * when this returns false.
     *
     * @param player   Player performing the deposit
     * @param colonyId Target colony
     * @param amount   Amount to deposit (before cap)
     * @param source   Where the funds come from
     * @return true if the deposit succeeded
     */
    public static boolean deposit(ServerPlayer player, int colonyId, int amount, CurrencyService.Source source) {
        if (!TaxConfig.isTreasuryEnabled()) {
            player.sendSystemMessage(Component.literal("Treasury system is disabled.").withStyle(ChatFormatting.RED));
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

        if (!CurrencyService.isAvailable(source)) {
            player.sendSystemMessage(
                    Component.literal("The '" + CurrencyService.label(source) + "' source is not available on this server.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        long available = CurrencyService.getAvailableBalance(player, colony, source);
        if (available < amount) {
            player.sendSystemMessage(
                    Component.literal("Insufficient " + CurrencyService.label(source) + " balance. You have "
                            + available + " available.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        int currentBalance = getTreasuryBalance(colonyId);
        int maxCapacity = getEffectiveMaxCapacity(colonyId);
        if (currentBalance >= maxCapacity) {
            player.sendSystemMessage(Component.literal("Treasury is at maximum capacity (" + maxCapacity + ").")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        int actualDeposit = Math.min(amount, maxCapacity - currentBalance);

        int taken = CurrencyService.takeFromPlayer(player, colony, actualDeposit, source);
        if (taken == 0) {
            player.sendSystemMessage(
                    Component.literal("Failed to withdraw from " + CurrencyService.label(source) + ".")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        TREASURIES.put(colonyId, currentBalance + taken);
        saveData();

        player.sendSystemMessage(Component.literal(
                "Deposited " + taken + " from " + CurrencyService.label(source)
                + " into treasury. Balance: " + (currentBalance + taken) + " / " + maxCapacity)
                .withStyle(ChatFormatting.GREEN));

        if (taken < amount) {
            player.sendSystemMessage(Component.literal("(Capped to max capacity of " + maxCapacity + ")")
                    .withStyle(ChatFormatting.GRAY));
        }

        return true;
    }

    /**
     * Withdraw funds from the treasury back to the colony's tax balance (default destination).
     * Kept for backwards-compatible callers.
     */
    public static boolean withdraw(ServerPlayer player, int colonyId, int amount) {
        return withdraw(player, colonyId, amount, CurrencyService.Source.TAX_BALANCE);
    }

    /**
     * Withdraw funds from the treasury to the specified destination.
     *
     * Destinations:
     *   TAX_BALANCE — added back to the colony's tax ledger (default)
     *   WALLET      — credited to the player's SDMShop / SDMEconomy balance
     *   INVENTORY   — physical currency items are given to the player's inventory
     *
     * @param player      Player performing the withdrawal
     * @param colonyId    Source colony
     * @param amount      Amount to withdraw
     * @param destination Where the funds go
     * @return true if the withdrawal succeeded
     */
    public static boolean withdraw(ServerPlayer player, int colonyId, int amount, CurrencyService.Source destination) {
        if (!TaxConfig.isTreasuryEnabled()) {
            player.sendSystemMessage(Component.literal("Treasury system is disabled.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("Amount must be positive.").withStyle(ChatFormatting.RED));
            return false;
        }

        int currentBalance = getTreasuryBalance(colonyId);
        if (currentBalance < amount) {
            player.sendSystemMessage(
                    Component.literal("Insufficient treasury. You have " + currentBalance + " available.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        if (!CurrencyService.isAvailable(destination)) {
            player.sendSystemMessage(
                    Component.literal("The '" + CurrencyService.label(destination) + "' destination is not available on this server.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        TREASURIES.put(colonyId, currentBalance - amount);

        IColony colony = getColony(colonyId);
        int given = CurrencyService.giveToPlayer(player, colony, amount, destination);

        if (given == 0) {
            // Refund the treasury — giving funds failed (e.g. wallet API error)
            TREASURIES.put(colonyId, currentBalance);
            player.sendSystemMessage(
                    Component.literal("Failed to deliver funds to " + CurrencyService.label(destination)
                            + ". Treasury unchanged.")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        saveData();

        player.sendSystemMessage(Component.literal(
                "Withdrew " + given + " from treasury to " + CurrencyService.label(destination)
                + ". Treasury balance: " + (currentBalance - given))
                .withStyle(ChatFormatting.GREEN));

        return true;
    }

    /**
     * Send treasury status to a player, including available deposit sources.
     */
    public static void sendStatus(ServerPlayer player, int colonyId) {
        if (!TaxConfig.isTreasuryEnabled()) {
            player.sendSystemMessage(Component.literal("Treasury system is disabled.").withStyle(ChatFormatting.RED));
            return;
        }

        IColony colony = getColony(colonyId);
        int balance = getTreasuryBalance(colonyId);
        int maxCapacity = getEffectiveMaxCapacity(colonyId);
        int effectiveDrain = computeEffectiveDrain(colonyId);

        player.sendSystemMessage(Component.literal("=== Treasury Status ===").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(
                Component.literal("Balance: " + balance + " / " + maxCapacity).withStyle(ChatFormatting.WHITE));

        String drainDesc = TaxConfig.isTreasuryDrainUsePercent()
                ? String.format("%d/min (%.0f%% of capacity)", effectiveDrain, TaxConfig.getTreasuryDrainPercent() * 100)
                : effectiveDrain + "/min (flat)";
        player.sendSystemMessage(
                Component.literal("Drain rate (during war): " + drainDesc).withStyle(ChatFormatting.GRAY));

        boolean isDefender = DEFENDER_COLONIES.contains(colonyId);
        if (isDefender) {
            player.sendSystemMessage(
                    Component.literal("Home field advantage: drain reduced by "
                            + (int)(TaxConfig.getWarDefenderDrainReduction() * 100) + "%")
                            .withStyle(ChatFormatting.GREEN));
        }

        if (balance > 0 && effectiveDrain > 0) {
            int minutesOfWar = balance / effectiveDrain;
            player.sendSystemMessage(Component.literal("Can sustain war for: ~" + minutesOfWar + " minutes")
                    .withStyle(ChatFormatting.AQUA));
        }

        // Show available deposit sources
        player.sendSystemMessage(Component.literal("Available to deposit:").withStyle(ChatFormatting.YELLOW));
        for (CurrencyService.Source src : CurrencyService.Source.values()) {
            if (!CurrencyService.isAvailable(src)) {
                player.sendSystemMessage(Component.literal(
                        "  " + CurrencyService.label(src) + ": not available").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                long avail = CurrencyService.getAvailableBalance(player, colony, src);
                player.sendSystemMessage(Component.literal(
                        "  " + CurrencyService.label(src) + ": " + avail).withStyle(ChatFormatting.WHITE));
            }
        }
    }

    // ==================== War Declaration Checks ====================

    /**
     * Check if a colony can declare war based on their treasury.
     *
     * @param attackerColonyId The attacking colony ID
     * @param defenderColonyId The defending colony ID
     * @return true if treasury requirements are met
     */
    public static boolean canDeclareWar(int attackerColonyId, int defenderColonyId) {
        if (!TaxConfig.isTreasuryEnabled()) {
            return true;
        }

        int attackerBalance = getTreasuryBalance(attackerColonyId);
        int required = getRequiredTreasury(attackerColonyId, defenderColonyId);

        return attackerBalance >= required;
    }

    /**
     * Get the required treasury amount to declare war on a target.
     * Returns the higher of: (defender's tax * targetPercent) vs (attacker's tax * ownPercent).
     * This prevents wealthy colonies from cheaply bullying poor ones.
     */
    public static int getRequiredTreasury(int attackerColonyId, int defenderColonyId) {
        if (!TaxConfig.isTreasuryEnabled()) {
            return 0;
        }

        IColony defenderColony = getColony(defenderColonyId);
        IColony attackerColony = getColony(attackerColonyId);
        int defenderTax = defenderColony != null ? TaxManager.getStoredTaxForColony(defenderColony) : 0;
        int attackerTax = attackerColony != null ? TaxManager.getStoredTaxForColony(attackerColony) : 0;

        int fromDefender = (int) Math.ceil(Math.max(0, defenderTax) * TaxConfig.getTreasuryMinPercentOfTarget());
        int fromOwn = (int) Math.ceil(Math.max(0, attackerTax) * TaxConfig.getTreasuryMinPercentOfOwnTax());

        return Math.max(fromDefender, fromOwn);
    }

    /**
     * Legacy overload for callers that only know the defender ID.
     * Uses 0 for attacker tax (falls back to target-only calculation).
     */
    public static int getRequiredTreasury(int defenderColonyId) {
        return getRequiredTreasury(0, defenderColonyId);
    }

    /**
     * Get a message explaining why war cannot be declared.
     */
    public static Component getTreasuryBlockedMessage(int attackerColonyId, int defenderColonyId) {
        int attackerBalance = getTreasuryBalance(attackerColonyId);
        int required = getRequiredTreasury(attackerColonyId, defenderColonyId);

        IColony attackerColony = getColony(attackerColonyId);
        IColony defenderColony = getColony(defenderColonyId);
        int attackerTax = attackerColony != null ? TaxManager.getStoredTaxForColony(attackerColony) : 0;
        int defenderTax = defenderColony != null ? TaxManager.getStoredTaxForColony(defenderColony) : 0;
        int fromDefender = (int) Math.ceil(Math.max(0, defenderTax) * TaxConfig.getTreasuryMinPercentOfTarget());
        int fromOwn = (int) Math.ceil(Math.max(0, attackerTax) * TaxConfig.getTreasuryMinPercentOfOwnTax());

        String reason = fromOwn >= fromDefender
                ? String.format("%.0f%% of your own tax (%d)", TaxConfig.getTreasuryMinPercentOfOwnTax() * 100, fromOwn)
                : String.format("%.0f%% of target's tax (%d)", TaxConfig.getTreasuryMinPercentOfTarget() * 100, fromDefender);

        return Component.literal("Insufficient treasury! You have " + attackerBalance + " but need " + required +
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
        if (TaxConfig.isTreasuryDrainUsePercent()) {
            baseDrain = (int) Math.ceil(getEffectiveMaxCapacity(colonyId) * TaxConfig.getTreasuryDrainPercent());
        } else {
            baseDrain = TaxConfig.getTreasuryDrainPerMinute();
        }

        // Defenders enjoy a drain reduction (home field advantage)
        if (DEFENDER_COLONIES.contains(colonyId)) {
            double reduction = TaxConfig.getWarDefenderDrainReduction();
            baseDrain = (int) Math.ceil(baseDrain * (1.0 - reduction));
        }

        return Math.max(0, baseDrain);
    }

    /**
     * Drain the treasury during an active war. Called every minute per colony.
     *
     * @param colonyId The colony in war
     * @return remaining balance after drain, or -1 if depleted (signals auto-surrender)
     */
    public static int drainTreasury(int colonyId) {
        if (!TaxConfig.isTreasuryEnabled()) {
            return Integer.MAX_VALUE;
        }

        int drainAmount = computeEffectiveDrain(colonyId);
        int currentBalance = getTreasuryBalance(colonyId);

        int newBalance = Math.max(0, currentBalance - drainAmount);
        TREASURIES.put(colonyId, newBalance);
        // Periodic save handled by WarSystem drain loop

        if (newBalance <= 0 && TaxConfig.isTreasuryAutoSurrenderEnabled()) {
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} treasury depleted - triggering auto-surrender", colonyId);
            return -1;
        }

        return newBalance;
    }

    /**
     * Deduct a specific amount from the treasury (for one-time raid costs).
     *
     * @param colonyId The colony ID
     * @param amount   Amount to deduct
     * @return new balance after deduction
     */
    public static int deductFromTreasury(int colonyId, int amount) {
        int currentBalance = getTreasuryBalance(colonyId);
        int newBalance = Math.max(0, currentBalance - amount);
        TREASURIES.put(colonyId, newBalance);
        // Periodic save handled by WarSystem drain loop
        if (TaxConfig.isNormalLogging()) LOGGER.info("Deducted {} from colony {} treasury. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Add a specific amount to the treasury.
     *
     * @param colonyId The colony ID
     * @param amount   Amount to add
     * @return new balance after addition
     */
    public static int addToTreasury(int colonyId, int amount) {
        int currentBalance = getTreasuryBalance(colonyId);
        int maxCapacity = getEffectiveMaxCapacity(colonyId);
        int newBalance = Math.min(maxCapacity, currentBalance + amount);

        TREASURIES.put(colonyId, newBalance);
        saveData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("Added {} to colony {} treasury. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Check if the treasury is depleted (for auto-surrender).
     */
    public static boolean isTreasuryDepleted(int colonyId) {
        return TaxConfig.isTreasuryEnabled() && getTreasuryBalance(colonyId) <= 0;
    }

    /**
     * Deduct cost from a colony's treasury. Used by the upgrade system.
     *
     * @param colonyId The colony ID
     * @param cost     Amount to deduct
     * @param server   MinecraftServer instance (unused, reserved for future use)
     * @return true if the colony had sufficient funds and the deduction succeeded
     */
    public static boolean purchase(int colonyId, int cost, MinecraftServer server) {
        // Defensive guard: a negative cost (e.g. caused by upstream int overflow in
        // ColonyUpgradeManager.getUpgradeCost) would otherwise pass the
        // `currentBalance < cost` check and CREDIT the colony by |cost|.
        if (cost < 0) {
            LOGGER.warn("TreasuryManager.purchase rejected negative cost {} for colony {}", cost, colonyId);
            return false;
        }
        int currentBalance = getTreasuryBalance(colonyId);
        if (currentBalance < cost) {
            return false;
        }
        TREASURIES.put(colonyId, currentBalance - cost);
        saveData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} purchased upgrade for {} from treasury. New balance: {}", colonyId, cost, currentBalance - cost);
        return true;
    }

    // ==================== Persistence ====================

    private static void loadData(MinecraftServer server) {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            if (TaxConfig.isNormalLogging()) LOGGER.info("No treasury data file found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<Integer, Integer>>() {
            }.getType();
            Map<Integer, Integer> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                TREASURIES.clear();
                TREASURIES.putAll(loaded);
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("Loaded {} treasury records", TREASURIES.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load treasury data: {}", e.getMessage());
        }
    }

    private static void saveData() {
        // Snapshot on the calling (main) thread, write off-thread + coalesced so a
        // deposit/withdraw/purchase storm no longer blocks ticks on disk I/O (audit H1).
        final Map<Integer, Integer> snapshot = new java.util.HashMap<>(TREASURIES);
        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("treasury", () -> writeData(snapshot));
    }

    private static void writeData(Map<Integer, Integer> data) {
        File file = new File(STORAGE_FILE);
        file.getParentFile().mkdirs();

        // Atomic write: serialize to a sibling .tmp file, fsync via close, then
        // rename over the final path. Prevents a crash mid-write from leaving a
        // truncated/empty warchests.json (which would silently zero every
        // colony's treasury on the next load).
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (FileWriter writer = new FileWriter(tmpFile)) {
                GSON.toJson(data, writer);
            }
            Path tmpPath = tmpFile.toPath();
            Path finalPath = file.toPath();
            try {
                Files.move(tmpPath, finalPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicEx) {
                // Some filesystems (notably across drive letters on Windows) cannot
                // ATOMIC_MOVE — fall back to a regular replace.
                Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save treasury data: {}", e.getMessage());
            // Best-effort cleanup of the temp file so it doesn't accumulate.
            if (tmpFile.exists()) {
                try { tmpFile.delete(); } catch (Exception ignored) { /* nothing else to do */ }
            }
        }
    }

    // ==================== Utility ====================

    public static int getEffectiveMaxCapacity(int colonyId) {
        int base = TaxConfig.getTreasuryMaxCapacity();
        if (!TaxConfig.isUpgradesEnabled()) return base;
        // Saturating add in long, then clamp to [0, Integer.MAX_VALUE]. Both base (TreasuryMaxCapacity)
        // and the per-level flat bonus are admin-configurable up to Integer.MAX_VALUE, so int math
        // could overflow into a NEGATIVE effective cap — which would make deposits reject everything
        // and cap-headroom math go haywire. getTreasuryCapBonus already returns long; doing the sum in
        // long keeps the ceiling monotonic so the upgrade only ever grows the cap.
        long effective = (long) base + net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getTreasuryCapBonus(colonyId);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, effective));
    }

    public static IColony getColony(int colonyId) {
        if (SERVER == null)
            return null;
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getColonyByWorld(colonyId, SERVER.overworld());
    }
}
