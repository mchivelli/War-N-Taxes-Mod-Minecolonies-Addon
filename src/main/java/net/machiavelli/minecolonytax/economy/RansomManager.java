package net.machiavelli.minecolonytax.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;

/**
 * Manages the Ransom System for raids.
 * 
 * When a defender (colony owner/officer) dies during a raid:
 * - Attacker can demand a ransom payment
 * - Victim can accept (pay, raid ends, immunity granted) or deny (raid
 * continues)
 * - Ransom only offered when victim is ONLINE
 * 
 * Features:
 * - Cooldown between ransom demands (same attacker-victim pair)
 * - Immunity after payment (protection from further raids)
 * - Timeout for ransom offers (auto-expire)
 */
public class RansomManager {

    private static final Logger LOGGER = LogManager.getLogger(RansomManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/ransom_data.json";

    /**
     * Active ransom offers (victimUUID -> offer)
     */
    private static final Map<UUID, RansomOffer> ACTIVE_OFFERS = new ConcurrentHashMap<>();

    /**
     * Cooldowns between attacker-victim pairs (key: "attackerUUID:victimUUID" ->
     * expiry time)
     */
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    /**
     * Raid immunity after paying ransom (colonyOwnerUUID -> immunity expiry time)
     */
    private static final Map<UUID, Long> IMMUNITY = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    // ==================== Data Classes ====================

    public static class RansomOffer {
        public UUID attacker;
        public UUID victim;
        public int colonyId; // Victim's colony ID
        public int attackerColonyId; // Attacker's colony ID (for War Chest fallback)
        public int amount;
        public long expiresAt;
        public TimerTask expiryTask;

        public RansomOffer(UUID attacker, UUID victim, int colonyId, int attackerColonyId, int amount, long expiresAt) {
            this.attacker = attacker;
            this.victim = victim;
            this.colonyId = colonyId;
            this.attackerColonyId = attackerColonyId;
            this.amount = amount;
            this.expiresAt = expiresAt;
        }
    }

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        cleanupExpired();
        LOGGER.info("RansomManager initialized - {} active offers, {} cooldowns, {} immunities",
                ACTIVE_OFFERS.size(), COOLDOWNS.size(), IMMUNITY.size());
    }

    public static void shutdown() {
        // Cancel all active timer tasks
        for (RansomOffer offer : ACTIVE_OFFERS.values()) {
            if (offer.expiryTask != null) {
                offer.expiryTask.cancel();
            }
        }
        saveData();
        LOGGER.info("RansomManager shutdown complete");
    }

    // ==================== Core Logic ====================

    /**
     * Create a ransom offer when a defender dies during a raid.
     * Only creates offer if victim is ONLINE.
     * 
     * @param attackerUUID The raider's UUID
     * @param victimUUID   The dead defender's UUID
     * @param colonyId     The colony being raided
     * @return true if offer was created, false if skipped
     */
    public static boolean createRansomOffer(UUID attackerUUID, UUID victimUUID, int colonyId) {
        if (!TaxConfig.isRansomSystemEnabled()) {
            return false;
        }

        // Check victim is online (ransom only for online players)
        if (SERVER == null)
            return false;
        ServerPlayer victim = SERVER.getPlayerList().getPlayer(victimUUID);
        if (victim == null || !victim.isAlive()) {
            LOGGER.debug("Ransom skipped - victim {} is offline", victimUUID);
            return false;
        }

        // Check cooldown
        String cooldownKey = attackerUUID.toString() + ":" + victimUUID.toString();
        if (isOnCooldown(cooldownKey)) {
            LOGGER.debug("Ransom skipped - cooldown active for {}", cooldownKey);
            return false;
        }

        // Check if victim already has active offer
        if (ACTIVE_OFFERS.containsKey(victimUUID)) {
            LOGGER.debug("Ransom skipped - victim {} already has active offer", victimUUID);
            return false;
        }

        // Calculate ransom amount - need to look up the colony
        IColony colony = getColonyById(colonyId);
        if (colony == null) {
            LOGGER.debug("Ransom skipped - colony {} not found", colonyId);
            return false;
        }
        int victimTaxBalance = TaxManager.getStoredTaxForColony(colony);
        int ransomAmount = calculateRansomAmount(victimTaxBalance);

        if (ransomAmount <= 0) {
            LOGGER.debug("Ransom skipped - calculated amount is 0");
            return false;
        }

        // Get attacker's colony ID from active raid data
        int attackerColonyId = -1;
        ActiveRaidData activeRaid = RaidManager.getActiveRaidForPlayer(attackerUUID);
        if (activeRaid != null && activeRaid.getRaiderColony() != null) {
            attackerColonyId = activeRaid.getRaiderColony().getID();
        }

        // Create offer
        long expiresAt = System.currentTimeMillis() + (TaxConfig.getRansomTimeoutSeconds() * 1000L);
        RansomOffer offer = new RansomOffer(attackerUUID, victimUUID, colonyId, attackerColonyId, ransomAmount,
                expiresAt);
        ACTIVE_OFFERS.put(victimUUID, offer);

        // Schedule expiry timer
        Timer timer = new Timer(true);
        offer.expiryTask = new TimerTask() {
            @Override
            public void run() {
                expireOffer(victimUUID);
            }
        };
        timer.schedule(offer.expiryTask, TaxConfig.getRansomTimeoutSeconds() * 1000L);

        // Send ransom message to victim
        sendRansomOffer(victim, offer);

        // Notify attacker
        ServerPlayer attacker = SERVER.getPlayerList().getPlayer(attackerUUID);
        if (attacker != null) {
            attacker.sendSystemMessage(Component
                    .literal("⚔ Ransom demand sent to " + victim.getName().getString() + " for " + ransomAmount
                            + " gold!")
                    .withStyle(ChatFormatting.GOLD));
        }

        LOGGER.info("Ransom offer created: {} demands {} gold from {} (colony {})",
                attackerUUID, ransomAmount, victimUUID, colonyId);
        return true;
    }

    /**
     * Calculate ransom amount based on victim's tax balance.
     */
    private static int calculateRansomAmount(int taxBalance) {
        double percent = TaxConfig.getRansomDefaultPercent();
        int calculated = (int) (taxBalance * percent);

        // Apply min/max bounds
        int min = TaxConfig.getRansomMinAmount();
        int max = TaxConfig.getRansomMaxAmount();

        return Math.max(min, Math.min(max, calculated));
    }

    /**
     * Send clickable ransom offer to victim.
     */
    private static void sendRansomOffer(ServerPlayer victim, RansomOffer offer) {
        int timeoutSeconds = TaxConfig.getRansomTimeoutSeconds();

        MutableComponent header = Component.literal("\n⚠ RANSOM DEMAND ⚠\n")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        MutableComponent body = Component.literal("A raider demands ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(offer.amount + " gold")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" to end this raid peacefully.\n")
                        .withStyle(ChatFormatting.YELLOW));

        MutableComponent timeInfo = Component.literal("You have " + timeoutSeconds + " seconds to respond.\n")
                .withStyle(ChatFormatting.GRAY);

        MutableComponent acceptBtn = Component.literal("[ACCEPT - Pay Ransom]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt ransom accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Pay " + offer.amount + " gold, end raid, gain 24h immunity"))));

        MutableComponent denyBtn = Component.literal(" [DENY - Continue Fighting]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt ransom deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Reject ransom, raid continues"))));

        victim.sendSystemMessage(
                header.append(body).append(timeInfo).append(acceptBtn).append(denyBtn).append(Component.literal("\n")));
    }

    /**
     * Accept a ransom offer - pay and end raid.
     */
    public static boolean acceptRansom(UUID victimUUID) {
        RansomOffer offer = ACTIVE_OFFERS.remove(victimUUID);
        if (offer == null) {
            return false;
        }

        // Cancel expiry timer
        if (offer.expiryTask != null) {
            offer.expiryTask.cancel();
        }

        // Deduct payment from colony tax
        IColony colony = getColonyById(offer.colonyId);
        if (colony == null) {
            LOGGER.warn("Ransom accept failed - colony {} not found", offer.colonyId);
            return false;
        }
        int currentTax = TaxManager.getStoredTaxForColony(colony);
        if (currentTax < offer.amount) {
            LOGGER.info("Ransom accept failed - colony {} has insufficient funds (has {}, needs {})",
                    offer.colonyId, currentTax, offer.amount);
            // Notify victim of failure
            if (SERVER != null) {
                ServerPlayer victim = SERVER.getPlayerList().getPlayer(victimUUID);
                if (victim != null) {
                    victim.sendSystemMessage(Component
                            .literal("✗ Ransom payment failed - insufficient colony funds! (Have: " + currentTax
                                    + ", Need: " + offer.amount + ")")
                            .withStyle(ChatFormatting.RED));
                }
            }
            return false;
        }
        // Deduct the payment from colony
        TaxManager.payTaxDebt(colony, -offer.amount); // Negative to deduct

        // Transfer ransom to attacker
        boolean transferSuccess = false;
        String currencyType = "gold";

        if (TaxConfig.isSDMShopConversionEnabled()) {
            // === SDMShop ENABLED - Transfer to attacker's personal balance ===
            if (SDMShopIntegration.isAvailable()) {
                ServerPlayer attackerPlayer = SERVER != null ? SERVER.getPlayerList().getPlayer(offer.attacker) : null;
                if (attackerPlayer != null) {
                    long currentBalance = SDMShopIntegration.getMoney(attackerPlayer);
                    if (SDMShopIntegration.setMoney(attackerPlayer, currentBalance + offer.amount)) {
                        transferSuccess = true;
                        currencyType = "coins";
                        LOGGER.info("Ransom paid: {} coins from colony {} to attacker {} SDMShop balance",
                                offer.amount, offer.colonyId, offer.attacker);
                    } else {
                        LOGGER.error("Failed to add ransom {} to attacker {} SDMShop balance", offer.amount,
                                offer.attacker);
                    }
                } else {
                    // Attacker is offline - use War Chest fallback
                    if (offer.attackerColonyId > 0) {
                        WarChestManager.addToWarChest(offer.attackerColonyId, offer.amount);
                        transferSuccess = true;
                        currencyType = "coins (added to attacker's War Chest - attacker offline)";
                        LOGGER.info(
                                "Ransom paid: {} coins from colony {} to attacker colony {} War Chest (attacker offline)",
                                offer.amount, offer.colonyId, offer.attackerColonyId);
                    } else {
                        // Attacker has no colony - money absorbed as war cost
                        LOGGER.info(
                                "Ransom paid: {} coins from colony {} - attacker {} offline with no colony (money absorbed)",
                                offer.amount, offer.colonyId, offer.attacker);
                        transferSuccess = true;
                        currencyType = "coins (absorbed - attacker offline with no colony)";
                    }
                }
            } else {
                LOGGER.warn("SDMShop enabled in config but not available for ransom transfer");
            }
        }

        if (!transferSuccess && !TaxConfig.isSDMShopConversionEnabled()) {
            // === SDMShop DISABLED - Transfer to attacker's colony tax balance or War Chest
            // ===
            if (SERVER != null) {
                ServerPlayer attackerPlayer = SERVER.getPlayerList().getPlayer(offer.attacker);
                if (attackerPlayer != null) {
                    IColony attackerColony = IMinecoloniesAPI.getInstance().getColonyManager()
                            .getColonies(attackerPlayer.level()).stream()
                            .filter(c -> c.getPermissions().getOwner().equals(offer.attacker))
                            .findFirst()
                            .orElse(null);

                    if (attackerColony != null) {
                        TaxManager.incrementTaxRevenue(attackerColony, offer.amount);
                        transferSuccess = true;
                        currencyType = "gold (added to colony tax)";
                        LOGGER.info("Ransom paid: {} gold from colony {} to attacker colony {} tax balance",
                                offer.amount, offer.colonyId, attackerColony.getID());
                    } else {
                        // Attacker online but has no colony - use War Chest if we have their colony ID
                        if (offer.attackerColonyId > 0) {
                            WarChestManager.addToWarChest(offer.attackerColonyId, offer.amount);
                            transferSuccess = true;
                            currencyType = "gold (added to War Chest)";
                            LOGGER.info("Ransom paid: {} gold from colony {} to attacker colony {} War Chest",
                                    offer.amount, offer.colonyId, offer.attackerColonyId);
                        } else {
                            LOGGER.info(
                                    "Ransom paid: {} gold from colony {} - attacker {} has no colony (money absorbed)",
                                    offer.amount, offer.colonyId, offer.attacker);
                            transferSuccess = true;
                            currencyType = "gold (absorbed - attacker has no colony)";
                        }
                    }
                } else {
                    // Attacker offline - use War Chest fallback
                    if (offer.attackerColonyId > 0) {
                        WarChestManager.addToWarChest(offer.attackerColonyId, offer.amount);
                        transferSuccess = true;
                        currencyType = "gold (added to attacker's War Chest - attacker offline)";
                        LOGGER.info(
                                "Ransom paid: {} gold from colony {} to attacker colony {} War Chest (attacker offline)",
                                offer.amount, offer.colonyId, offer.attackerColonyId);
                    } else {
                        LOGGER.info(
                                "Ransom paid: {} gold from colony {} - attacker {} offline with no colony (money absorbed)",
                                offer.amount, offer.colonyId, offer.attacker);
                        transferSuccess = true;
                        currencyType = "gold (absorbed - attacker offline with no colony)";
                    }
                }
            }
        }

        // Grant immunity
        long immunityDuration = TaxConfig.getRansomImmunityAfterPaymentHours() * 60L * 60L * 1000L;
        IMMUNITY.put(victimUUID, System.currentTimeMillis() + immunityDuration);

        // Set cooldown
        String cooldownKey = offer.attacker.toString() + ":" + victimUUID.toString();
        long cooldownDuration = TaxConfig.getRansomCooldownMinutes() * 60L * 1000L;
        COOLDOWNS.put(cooldownKey, System.currentTimeMillis() + cooldownDuration);

        saveData();

        // Notify both players with detailed feedback
        if (SERVER != null) {
            ServerPlayer victim = SERVER.getPlayerList().getPlayer(victimUUID);
            if (victim != null) {
                victim.sendSystemMessage(Component
                        .literal("✓ Ransom paid! " + offer.amount + " " + currencyType
                                + " deducted from colony treasury.")
                        .withStyle(ChatFormatting.GREEN));
                victim.sendSystemMessage(Component
                        .literal("You have " + TaxConfig.getRansomImmunityAfterPaymentHours()
                                + " hours of raid immunity.")
                        .withStyle(ChatFormatting.GOLD));
            }

            ServerPlayer attacker = SERVER.getPlayerList().getPlayer(offer.attacker);
            if (attacker != null) {
                String receivedMessage = TaxConfig.isSDMShopConversionEnabled()
                        ? offer.amount + " coins added to your account!"
                        : offer.amount + " gold added to your colony treasury!";
                attacker.sendSystemMessage(
                        Component.literal("✓ Ransom accepted! " + receivedMessage + " Raid ending.")
                                .withStyle(ChatFormatting.GOLD));
            }
        }

        // End the raid - this will be handled by the caller (RaidManager)
        return true;
    }

    /**
     * Deny a ransom offer - raid continues.
     */
    public static boolean denyRansom(UUID victimUUID) {
        RansomOffer offer = ACTIVE_OFFERS.remove(victimUUID);
        if (offer == null) {
            return false;
        }

        // Cancel expiry timer
        if (offer.expiryTask != null) {
            offer.expiryTask.cancel();
        }

        // Set cooldown anyway (prevents spam)
        String cooldownKey = offer.attacker.toString() + ":" + victimUUID.toString();
        long cooldownDuration = TaxConfig.getRansomCooldownMinutes() * 60L * 1000L;
        COOLDOWNS.put(cooldownKey, System.currentTimeMillis() + cooldownDuration);

        saveData();

        LOGGER.info("Ransom denied by {}", victimUUID);

        // Notify both players
        if (SERVER != null) {
            ServerPlayer victim = SERVER.getPlayerList().getPlayer(victimUUID);
            if (victim != null) {
                victim.sendSystemMessage(Component.literal("✗ Ransom rejected. The raid continues!")
                        .withStyle(ChatFormatting.RED));
            }

            ServerPlayer attacker = SERVER.getPlayerList().getPlayer(offer.attacker);
            if (attacker != null) {
                attacker.sendSystemMessage(Component.literal("✗ Ransom rejected by defender. Continue the raid!")
                        .withStyle(ChatFormatting.RED));
            }
        }

        return true;
    }

    /**
     * Expire an offer that wasn't responded to.
     */
    private static void expireOffer(UUID victimUUID) {
        RansomOffer offer = ACTIVE_OFFERS.remove(victimUUID);
        if (offer == null)
            return;

        LOGGER.info("Ransom offer expired for {}", victimUUID);

        if (SERVER != null) {
            ServerPlayer victim = SERVER.getPlayerList().getPlayer(victimUUID);
            if (victim != null) {
                victim.sendSystemMessage(Component.literal("⏰ Ransom offer expired. The raid continues!")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ==================== Immunity & Cooldown Checks ====================

    /**
     * Check if a colony owner has raid immunity from paying ransom.
     */
    public static boolean hasImmunity(UUID colonyOwnerUUID) {
        Long expiryTime = IMMUNITY.get(colonyOwnerUUID);
        if (expiryTime == null)
            return false;

        if (System.currentTimeMillis() >= expiryTime) {
            IMMUNITY.remove(colonyOwnerUUID);
            saveData();
            return false;
        }
        return true;
    }

    /**
     * Get remaining immunity hours.
     */
    public static int getRemainingImmunityHours(UUID colonyOwnerUUID) {
        Long expiryTime = IMMUNITY.get(colonyOwnerUUID);
        if (expiryTime == null)
            return 0;

        long remaining = expiryTime - System.currentTimeMillis();
        return Math.max(0, (int) (remaining / (60L * 60L * 1000L)));
    }

    /**
     * Check if cooldown is active for attacker-victim pair.
     */
    private static boolean isOnCooldown(String cooldownKey) {
        Long expiryTime = COOLDOWNS.get(cooldownKey);
        if (expiryTime == null)
            return false;

        if (System.currentTimeMillis() >= expiryTime) {
            COOLDOWNS.remove(cooldownKey);
            return false;
        }
        return true;
    }

    /**
     * Check if victim has pending ransom offer.
     */
    public static boolean hasPendingOffer(UUID victimUUID) {
        return ACTIVE_OFFERS.containsKey(victimUUID);
    }

    /**
     * Get pending offer for victim.
     */
    public static RansomOffer getPendingOffer(UUID victimUUID) {
        return ACTIVE_OFFERS.get(victimUUID);
    }

    // ==================== Persistence ====================

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();

        // Cleanup expired offers
        ACTIVE_OFFERS.entrySet().removeIf(e -> now >= e.getValue().expiresAt);

        // Cleanup expired cooldowns
        COOLDOWNS.entrySet().removeIf(e -> now >= e.getValue());

        // Cleanup expired immunities
        IMMUNITY.entrySet().removeIf(e -> now >= e.getValue());
    }

    private static void loadData() {
        Path path = Paths.get(STORAGE_FILE);
        if (!Files.exists(path))
            return;

        try (Reader reader = new FileReader(path.toFile())) {
            Type type = new TypeToken<RansomSaveData>() {
            }.getType();
            RansomSaveData data = GSON.fromJson(reader, type);

            if (data != null) {
                if (data.cooldowns != null)
                    COOLDOWNS.putAll(data.cooldowns);
                if (data.immunity != null)
                    IMMUNITY.putAll(data.immunity);
                // Note: Active offers are NOT persisted (they expire on restart)
            }

            LOGGER.debug("Loaded ransom data from {}", STORAGE_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to load ransom data", e);
        }
    }

    private static void saveData() {
        try {
            Path path = Paths.get(STORAGE_FILE);
            Files.createDirectories(path.getParent());

            RansomSaveData data = new RansomSaveData();
            data.cooldowns = new ConcurrentHashMap<>(COOLDOWNS);
            data.immunity = new ConcurrentHashMap<>(IMMUNITY);

            try (Writer writer = new FileWriter(path.toFile())) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save ransom data", e);
        }
    }

    /**
     * Data class for JSON persistence
     */
    private static class RansomSaveData {
        Map<String, Long> cooldowns;
        Map<UUID, Long> immunity;
    }

    /**
     * Get a colony by ID (helper method).
     */
    private static IColony getColonyById(int colonyId) {
        try {
            return IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId, null);
        } catch (Exception e) {
            return null;
        }
    }
}
