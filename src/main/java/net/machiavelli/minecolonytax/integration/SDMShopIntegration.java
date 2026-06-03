package net.machiavelli.minecolonytax.integration;

import net.minecraft.server.level.ServerPlayer;

/**
 * Thin wrapper over {@link SDMShopCompat} for the SDM-Economy money system.
 *
 * <p>Historically this class did its own reflection against guessed SDMShop class names (none of
 * which matched the real API), so the integration never actually worked. It now delegates to the
 * single canonical bridge in {@link SDMShopCompat}, which reflects on
 * {@code net.sixik.sdm_economy.api.CurrencyHelper}. Kept as a separate type only because several
 * callers (PvP/raid kill economy, extortion, spy intel) already reference it.
 */
public final class SDMShopIntegration {

    private SDMShopIntegration() {}

    /** @see SDMShopCompat#isAvailable() */
    public static boolean isAvailable() {
        return SDMShopCompat.isAvailable();
    }

    /** @see SDMShopCompat#getMoney(ServerPlayer) */
    public static long getMoney(ServerPlayer player) {
        return SDMShopCompat.getMoney(player);
    }

    /** @see SDMShopCompat#setMoney(ServerPlayer, long) */
    public static boolean setMoney(ServerPlayer player, long amount) {
        return SDMShopCompat.setMoney(player, amount);
    }

    /** @see SDMShopCompat#addMoney(ServerPlayer, long) */
    public static boolean addMoney(ServerPlayer player, long amount) {
        return SDMShopCompat.addMoney(player, amount);
    }

    /** @see SDMShopCompat#removeMoney(ServerPlayer, long) */
    public static boolean removeMoney(ServerPlayer player, long amount) {
        return SDMShopCompat.removeMoney(player, amount);
    }

    /**
     * Deducts up to {@code amount} from the player; returns the amount actually deducted
     * (0 if SDM-Economy is unavailable or the player cannot afford it).
     */
    public static int deductPlayerBalance(ServerPlayer player, int amount) {
        if (amount <= 0) return 0;
        return SDMShopCompat.removeMoney(player, amount) ? amount : 0;
    }
}
