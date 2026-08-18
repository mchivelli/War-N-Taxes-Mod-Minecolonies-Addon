package net.machiavelli.minecolonytax.pvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.CurrencyService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coins the duel system owes a player but could not hand over yet.
 *
 * <p><b>Why this exists.</b> Duel wagers are escrowed up front, and the escrow is returned by
 * {@code PvPBattleManager.refundAllWagers} on "draw / cancel / disconnect / abort". The refund
 * path resolved the player and gave up on {@code null} — with no log line and no retry. The
 * dominant reason a duel aborts is a player <em>disconnecting</em>, which is precisely the case
 * where that lookup returns null, so the most common refund was the one guaranteed to be thrown
 * away. The battle was then marked settled, so the coins could never be recovered.
 *
 * <p>The same hole existed on the payout side: when no winner was online the code refunded every
 * participant "to conserve money", but skipped anyone offline — in a branch that only runs because
 * people are offline.
 *
 * <p>Entries are persisted, because a server restart between the disconnect and the next login is
 * exactly the window in which an in-memory queue would lose the debt. Mirrors the queue-and-deliver
 * pattern SpyManager already uses for intel rewards.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PendingWagerPayouts {

    private static final Logger LOGGER = LogManager.getLogger(PendingWagerPayouts.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/pending_wagers.json";

    /** playerId -> coins still owed. Amounts accumulate; a player may be owed several stakes. */
    private static final Map<String, Integer> OWED = new ConcurrentHashMap<>();

    private PendingWagerPayouts() {}

    // ==================== Lifecycle ====================

    public static void initialize() {
        load();
        if (!OWED.isEmpty()) {
            LOGGER.warn("[WnT] {} player(s) are still owed duel wager coins from a previous session.",
                    OWED.size());
        }
    }

    public static void shutdown() {
        save();
    }

    // ==================== Queue / drain ====================

    /**
     * Record that {@code amount} could not be handed to {@code playerId}. Safe to call repeatedly;
     * amounts accumulate.
     */
    public static void queue(UUID playerId, int amount, String reason) {
        if (playerId == null || amount <= 0) return;
        String key = playerId.toString();
        int total = OWED.merge(key, amount, Integer::sum);
        save();
        LOGGER.info("[WnT] Queued {} wager coins for {} ({}); total owed {}.",
                amount, key, reason, total);
    }

    /** Coins currently owed to a player (0 if none). */
    public static int owed(UUID playerId) {
        if (playerId == null) return 0;
        return OWED.getOrDefault(playerId.toString(), 0);
    }

    /**
     * Try to hand a player everything they are owed. The debt is only cleared once the coins were
     * actually delivered — a failed delivery keeps the debt so it is retried on the next login
     * rather than silently written off.
     *
     * @return the amount actually delivered
     */
    public static int deliverOwed(ServerPlayer player) {
        if (player == null) return 0;
        String key = player.getUUID().toString();
        Integer amount = OWED.get(key);
        if (amount == null || amount <= 0) return 0;

        if (!tryDeliver(player, amount)) {
            LOGGER.error("[WnT] Still cannot deliver {} owed wager coins to {}; keeping the debt.",
                    amount, player.getName().getString());
            return 0;
        }

        OWED.remove(key);
        save();
        player.sendSystemMessage(Component.literal(
                "You were owed " + amount + " coins (duel wager or war reparations). They have been paid out.")
                .withStyle(ChatFormatting.GOLD));
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("[WnT] Paid out {} owed wager coins to {}.", amount, player.getName().getString());
        }
        return amount;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            deliverOwed(player);
        } catch (Exception e) {
            LOGGER.error("[WnT] Failed to deliver owed wager coins to {}: {}",
                    player.getName().getString(), e.toString());
        }
    }

    /**
     * Deliver via the wallet when it is available, otherwise physical currency — and if the
     * preferred store refuses, try the other one. Same two-store fallback the live payout path
     * uses, so a wallet that is temporarily unavailable does not strand the coins.
     */
    private static boolean tryDeliver(ServerPlayer player, int amount) {
        CurrencyService.Source primary = CurrencyService.isAvailable(CurrencyService.Source.WALLET)
                ? CurrencyService.Source.WALLET
                : CurrencyService.Source.INVENTORY;
        if (CurrencyService.giveToPlayer(player, null, amount, primary) >= amount) return true;

        CurrencyService.Source alternate = (primary == CurrencyService.Source.WALLET)
                ? CurrencyService.Source.INVENTORY
                : CurrencyService.Source.WALLET;
        return CurrencyService.giveToPlayer(player, null, amount, alternate) >= amount;
    }

    // ==================== Persistence ====================

    private static void load() {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                OWED.clear();
                loaded.forEach((k, v) -> { if (k != null && v != null && v > 0) OWED.put(k, v); });
            }
        } catch (Exception e) {
            LOGGER.error("[WnT] Failed to load pending wager payouts: {}", e.getMessage());
        }
    }

    private static void save() {
        final Map<String, Integer> snapshot = new HashMap<>(OWED);
        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("pending-wagers", () -> write(snapshot));
    }

    private static void write(Map<String, Integer> data) {
        File file = new File(STORAGE_FILE);
        file.getParentFile().mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("[WnT] Failed to save pending wager payouts: {}", e.getMessage());
            if (tmp.exists()) {
                try { tmp.delete(); } catch (Exception ignored) { /* nothing else to do */ }
            }
        }
    }
}
