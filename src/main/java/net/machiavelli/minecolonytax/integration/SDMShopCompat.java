package net.machiavelli.minecolonytax.integration;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Optional economy bridge to SDM-Economy (the money system behind SDMShop).
 *
 * <p>Accessed entirely via reflection so this mod has NO compile/runtime dependency on
 * SDM-Economy — if it is absent every call is a safe no-op and callers fall back to their
 * own logic. When present, money operations go through SDM-Economy's public API
 * {@code net.sixik.sdm_economy.api.CurrencyHelper.Basic} (the "basic_money" default currency):
 * <pre>
 *   static long getMoney(Player)
 *   static void setMoney(Player, long)
 *   static void addMoney(Player, long)
 * </pre>
 *
 * <p>This is the single canonical economy bridge; {@link SDMShopIntegration} delegates here.
 */
public final class SDMShopCompat {

    private SDMShopCompat() {}

    private static volatile boolean initialized = false;
    private static boolean available = false;
    private static Method getMoneyMethod;   // CurrencyHelper.Basic.getMoney(Player) -> long
    private static Method setMoneyMethod;    // CurrencyHelper.Basic.setMoney(Player, long)
    private static Method addMoneyMethod;    // CurrencyHelper.Basic.addMoney(Player, long)

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> basic = Class.forName("net.sixik.sdm_economy.api.CurrencyHelper$Basic");
            getMoneyMethod = basic.getMethod("getMoney", Player.class);
            setMoneyMethod = basic.getMethod("setMoney", Player.class, long.class);
            addMoneyMethod = basic.getMethod("addMoney", Player.class, long.class);
            available = true;
            MineColonyTax.LOGGER.info("SDM-Economy detected (CurrencyHelper) - economy integration enabled");
        } catch (ClassNotFoundException e) {
            MineColonyTax.LOGGER.info("SDM-Economy not installed - currency integration disabled (using fallback)");
        } catch (Throwable t) {
            // Present but the API differs from what we expect (e.g. a future package rename).
            MineColonyTax.LOGGER.warn("SDM-Economy present but its API did not match - integration disabled: {}", t.toString());
        }
    }

    /** True if SDM-Economy is installed and its currency API was resolved. */
    public static boolean isAvailable() {
        init();
        return available;
    }

    /** Player's balance in the default ("basic_money") currency, or 0 if SDM-Economy is unavailable. */
    public static long getMoney(ServerPlayer player) {
        init();
        if (!available || player == null) return 0L;
        try {
            Object result = getMoneyMethod.invoke(null, player);
            return (result instanceof Number n) ? n.longValue() : 0L;
        } catch (Throwable t) {
            MineColonyTax.LOGGER.error("SDM-Economy getMoney failed for {}: {}",
                    player.getName().getString(), t.toString());
            return 0L;
        }
    }

    /** Alias of {@link #getMoney(ServerPlayer)}. */
    public static long getBalance(ServerPlayer player) {
        return getMoney(player);
    }

    /** Sets the player's balance. Returns false if SDM-Economy is unavailable. */
    public static boolean setMoney(ServerPlayer player, long amount) {
        init();
        if (!available || player == null) return false;
        try {
            setMoneyMethod.invoke(null, player, amount);
            return true;
        } catch (Throwable t) {
            MineColonyTax.LOGGER.error("SDM-Economy setMoney failed for {}: {}",
                    player.getName().getString(), t.toString());
            return false;
        }
    }

    /** Adds money to the player. Returns false if SDM-Economy is unavailable. */
    public static boolean addMoney(ServerPlayer player, long amount) {
        init();
        if (!available || player == null) return false;
        try {
            addMoneyMethod.invoke(null, player, amount);
            return true;
        } catch (Throwable t) {
            MineColonyTax.LOGGER.error("SDM-Economy addMoney failed for {}: {}",
                    player.getName().getString(), t.toString());
            return false;
        }
    }

    /** Removes money if the player can afford it. Returns false if unavailable or insufficient funds. */
    public static boolean removeMoney(ServerPlayer player, long amount) {
        init();
        if (!available || player == null) return false;
        long current = getMoney(player);
        if (current < amount) return false;
        return setMoney(player, current - amount);
    }

    /** Transfers money between players; rolls back the debit if the credit fails. */
    public static boolean transferMoney(ServerPlayer from, ServerPlayer to, long amount) {
        if (!removeMoney(from, amount)) return false;
        if (!addMoney(to, amount)) {
            addMoney(from, amount); // rollback the debit
            return false;
        }
        return true;
    }
}
