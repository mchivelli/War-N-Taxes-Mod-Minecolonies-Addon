package net.machiavelli.minecolonytax.integration;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Optional economy bridge to <b>SDM-Economy</b> (the money system behind SDMShop).
 *
 * <p>Accessed entirely via reflection so this mod has NO compile/runtime dependency on
 * SDM-Economy — if it is absent every call is a safe no-op and callers fall back to their own
 * logic. When present, money operations go through SDM-Economy's public API. Verified by
 * decompiling the deployed {@code sdmeconomy-neoforge-1.21.1-2.4.0.jar}:
 * <pre>
 *   net.sixik.sdmeconomy.api.EconomyAPI
 *     static CurrencyPlayerData$Server getPlayerCurrencyServerData()
 *     static ErrorCodes               syncPlayer(ServerPlayer)
 *   net.sixik.sdmeconomy.economyData.CurrencyPlayerData$Server
 *     ErrorCodes                addCurrencyValue(Player, String currencyId, double)
 *     ErrorCodes                setCurrencyValue(Player, String currencyId, double)
 *     ErrorCodeStruct&lt;Double&gt;  getBalance(Player, String currencyId)
 *   net.sixik.sdmeconomy.utils.ErrorCodes#isSuccess()
 *   net.sixik.sdmeconomy.utils.ErrorCodeStruct { public T value; public ErrorCodes codes; }
 * </pre>
 *
 * <p>Earlier builds reflected {@code net.sixik.sdm_economy.api.CurrencyHelper$Basic} — that is the
 * <i>1.20.1 Forge</i> package name; the 1.21.1 NeoForge economy is {@code net.sixik.sdmeconomy.*}
 * (no underscore) with an entirely different, currency-id-based API, so the old bridge never
 * resolved and every tax claim silently refunded instead of paying out. This bridge targets the
 * real 2.x API and pays into the currency named by the {@code SDMCurrencyName} config; a credit
 * that SDM rejects (e.g. an unknown currency id) returns {@code false} so the caller refunds the
 * tax rather than losing it.
 *
 * <p>This is the single canonical economy bridge; {@link SDMShopIntegration} delegates here.
 */
public final class SDMShopCompat {

    private SDMShopCompat() {}

    private static volatile boolean initialized = false;
    private static boolean available = false;

    private static Method getServerData;     // EconomyAPI.getPlayerCurrencyServerData() -> Server
    private static Method syncPlayer;        // EconomyAPI.syncPlayer(ServerPlayer) -> ErrorCodes
    private static Method addCurrencyValue;  // Server.addCurrencyValue(Player, String, double) -> ErrorCodes
    private static Method setCurrencyValue;  // Server.setCurrencyValue(Player, String, double) -> ErrorCodes
    private static Method getBalanceMethod;  // Server.getBalance(Player, String) -> ErrorCodeStruct<Double>
    private static Method errIsSuccess;      // ErrorCodes.isSuccess() -> boolean
    private static Field structValue;        // ErrorCodeStruct.value (public field)
    private static Field structCodes;        // ErrorCodeStruct.codes (public field)

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> api = Class.forName("net.sixik.sdmeconomy.api.EconomyAPI");
            Class<?> serverData = Class.forName("net.sixik.sdmeconomy.economyData.CurrencyPlayerData$Server");
            Class<?> struct = Class.forName("net.sixik.sdmeconomy.utils.ErrorCodeStruct");
            Class<?> errorCodes = Class.forName("net.sixik.sdmeconomy.utils.ErrorCodes");

            getServerData = api.getMethod("getPlayerCurrencyServerData");
            syncPlayer = api.getMethod("syncPlayer", ServerPlayer.class);
            addCurrencyValue = serverData.getMethod("addCurrencyValue", Player.class, String.class, double.class);
            setCurrencyValue = serverData.getMethod("setCurrencyValue", Player.class, String.class, double.class);
            getBalanceMethod = serverData.getMethod("getBalance", Player.class, String.class);
            errIsSuccess = errorCodes.getMethod("isSuccess");
            structValue = struct.getField("value");
            structCodes = struct.getField("codes");

            available = true;
            MineColonyTax.LOGGER.info(
                    "SDM-Economy detected (sdmeconomy 2.x API) - economy integration enabled, paying currency '{}'",
                    currencyId());
        } catch (ClassNotFoundException e) {
            MineColonyTax.LOGGER.info("SDM-Economy not installed - currency integration disabled (using fallback)");
        } catch (Throwable t) {
            // Present but the API differs from what we expect (e.g. a future package rename).
            MineColonyTax.LOGGER.warn("SDM-Economy present but its API did not match - integration disabled: {}",
                    t.toString());
        }
    }

    /** True if SDM-Economy is installed and its currency API was resolved. */
    public static boolean isAvailable() {
        init();
        return available;
    }

    /** The configured currency id claimed taxes are paid into (falls back to {@code sdmcoin}). */
    private static String currencyId() {
        try {
            String c = TaxConfig.getSDMCurrencyName();
            if (c != null && !c.isBlank()) return c.trim();
        } catch (Throwable ignored) {
            // config not loaded yet (e.g. during early init logging) — use the default below
        }
        return "sdmcoin";
    }

    private static boolean isSuccess(Object errorCode) {
        if (errorCode == null) return false;
        try {
            return Boolean.TRUE.equals(errIsSuccess.invoke(errorCode));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Player's balance in the configured currency, or 0 if SDM-Economy is unavailable / not credited. */
    public static long getMoney(ServerPlayer player) {
        init();
        if (!available || player == null) return 0L;
        try {
            Object server = getServerData.invoke(null);
            if (server == null) return 0L;
            Object struct = getBalanceMethod.invoke(server, player, currencyId());
            if (struct == null || !isSuccess(structCodes.get(struct))) return 0L;
            Object val = structValue.get(struct);
            return (val instanceof Number n) ? (long) n.doubleValue() : 0L;
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

    /** Sets the player's balance. Returns false if SDM-Economy is unavailable or the credit was rejected. */
    public static boolean setMoney(ServerPlayer player, long amount) {
        init();
        if (!available || player == null) return false;
        try {
            Object server = getServerData.invoke(null);
            if (server == null) return false;
            Object code = setCurrencyValue.invoke(server, player, currencyId(), (double) amount);
            if (!isSuccess(code)) {
                warnCurrency("setMoney", player, code);
                return false;
            }
            syncPlayer.invoke(null, player);
            return true;
        } catch (Throwable t) {
            MineColonyTax.LOGGER.error("SDM-Economy setMoney failed for {}: {}",
                    player.getName().getString(), t.toString());
            return false;
        }
    }

    /** Adds money to the player. Returns false (so the caller refunds) if unavailable or the credit was rejected. */
    public static boolean addMoney(ServerPlayer player, long amount) {
        init();
        if (!available || player == null) return false;
        try {
            Object server = getServerData.invoke(null);
            if (server == null) return false;
            Object code = addCurrencyValue.invoke(server, player, currencyId(), (double) amount);
            if (!isSuccess(code)) {
                warnCurrency("addMoney", player, code);
                return false;
            }
            syncPlayer.invoke(null, player);
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

    /**
     * Logs a clear, throttle-free diagnostic when SDM rejects a credit — almost always because the
     * configured {@code SDMCurrencyName} does not match any currency on the server. Lists the
     * currency ids SDM knows about so the operator can correct the config.
     */
    private static void warnCurrency(String op, ServerPlayer player, Object code) {
        String known = "<unknown>";
        try {
            Object map = Class.forName("net.sixik.sdmeconomy.api.CustomCurrencies")
                    .getField("CURRENCIES").get(null);
            if (map instanceof Map<?, ?> m) known = m.keySet().toString();
        } catch (Throwable ignored) {
            // registry not reachable — leave "<unknown>"
        }
        MineColonyTax.LOGGER.warn(
                "SDM-Economy {} for {} returned {} for currency '{}'. Set config 'SDMCurrencyName' to a valid id "
                        + "(registered currencies: {}).",
                op, player.getName().getString(), code, currencyId(), known);
    }
}
