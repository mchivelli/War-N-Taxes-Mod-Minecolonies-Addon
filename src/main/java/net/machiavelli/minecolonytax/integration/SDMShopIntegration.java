package net.machiavelli.minecolonytax.integration;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Integration wrapper for SDMShop/SDM-Economy to avoid compilation errors when
 * the mod is not present
 * while preserving full functionality when it is available.
 * 
 * Supports both old and new versions of SDMShop/SDM-Economy through multi-tier
 * fallback:
 * 1. NEW API: ShopUtils from SDMShop (net.sixik.sdmshop.utils.ShopUtils)
 * 2. NEW API: CurrencyPlayerData.SERVER from SDM-Economy (multi-currency
 * system)
 * 3. OLD API: Legacy getMoney/setMoney patterns for backwards compatibility
 */
public class SDMShopIntegration {
    private static final Logger LOGGER = LogManager.getLogger(SDMShopIntegration.class);

    // Default currency name for SDM-Economy multi-currency system
    private static final String DEFAULT_CURRENCY_NAME = "sdm_coin";

    // Integration mode enum
    private enum IntegrationMode {
        NONE, // Not available
        SHOP_UTILS, // New SDMShop ShopUtils API
        CURRENCY_DATA, // New SDM-Economy CurrencyPlayerData API
        LEGACY // Old legacy API
    }

    private static IntegrationMode mode = IntegrationMode.NONE;
    private static boolean initialized = false;

    // ShopUtils mode fields
    private static Class<?> shopUtilsClass = null;
    private static Method shopUtilsGetMoney = null;
    private static Method shopUtilsSetMoney = null;
    private static Method shopUtilsAddMoney = null;

    // CurrencyPlayerData mode fields
    private static Class<?> currencyPlayerDataClass = null;
    private static Object currencyPlayerDataServer = null;
    private static Method currencyDataGetBalance = null;
    private static Method currencyDataSetValue = null;
    private static Method currencyDataAddValue = null;

    // Sync method for updating client
    private static Class<?> economyApiClass = null;
    private static Method syncPlayerMethod = null;

    // Legacy mode fields
    private static Class<?> legacyClass = null;
    private static Method legacyGetMoney = null;
    private static Method legacySetMoney = null;

    static {
        initialize();
    }

    private static void initialize() {
        if (initialized)
            return;
        initialized = true;

        LOGGER.info("Attempting to initialize SDMShop/SDM-Economy integration...");

        // Try integration methods in priority order
        if (tryShopUtilsApi()) {
            mode = IntegrationMode.SHOP_UTILS;
            LOGGER.info("✓ SDMShop integration initialized using ShopUtils API (newest)");
            return;
        }

        if (tryCurrencyDataApi()) {
            mode = IntegrationMode.CURRENCY_DATA;
            LOGGER.info("✓ SDM-Economy integration initialized using CurrencyPlayerData API");
            return;
        }

        if (tryLegacyApi()) {
            mode = IntegrationMode.LEGACY;
            LOGGER.info("✓ SDMShop integration initialized using legacy API (backwards compatibility)");
            return;
        }

        LOGGER.warn("SDMShop/SDM-Economy integration not available - no compatible API found");
    }

    /**
     * Try to initialize using SDMShop's ShopUtils class (newest API)
     */
    private static boolean tryShopUtilsApi() {
        try {
            LOGGER.debug("Trying ShopUtils API...");
            shopUtilsClass = Class.forName("net.sixik.sdmshop.utils.ShopUtils");

            // getMoney(Player) returns double
            shopUtilsGetMoney = shopUtilsClass.getMethod("getMoney", Player.class);
            LOGGER.debug("✓ Found ShopUtils.getMoney(Player)");

            // setMoney(Player, double) returns boolean
            shopUtilsSetMoney = shopUtilsClass.getMethod("setMoney", Player.class, double.class);
            LOGGER.debug("✓ Found ShopUtils.setMoney(Player, double)");

            // addMoney(Player, double) returns boolean
            shopUtilsAddMoney = shopUtilsClass.getMethod("addMoney", Player.class, double.class);
            LOGGER.debug("✓ Found ShopUtils.addMoney(Player, double)");

            LOGGER.info("✓ Successfully initialized ShopUtils API");
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("ShopUtils class not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.debug("ShopUtils method not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("Failed to initialize ShopUtils API: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Try to initialize using SDM-Economy's CurrencyPlayerData API
     */
    private static boolean tryCurrencyDataApi() {
        try {
            LOGGER.debug("Trying CurrencyPlayerData API...");
            currencyPlayerDataClass = Class.forName("net.sixik.sdmeconomy.currencies.data.CurrencyPlayerData");

            // Get the static SERVER field
            Field serverField = currencyPlayerDataClass.getField("SERVER");
            currencyPlayerDataServer = serverField.get(null);

            if (currencyPlayerDataServer == null) {
                LOGGER.debug("CurrencyPlayerData.SERVER is null (server not yet initialized)");
                // This might happen during early loading - we'll check again later
            }

            // Get the Server inner class
            Class<?> serverClass = Class.forName("net.sixik.sdmeconomy.currencies.data.CurrencyPlayerData$Server");

            // getBalance(Player, String) returns ErrorCodeStruct<Double>
            currencyDataGetBalance = serverClass.getMethod("getBalance", Player.class, String.class);
            LOGGER.debug("✓ Found CurrencyPlayerData.Server.getBalance(Player, String)");

            // setCurrencyValue(Player, String, double) returns ErrorCodes
            currencyDataSetValue = serverClass.getMethod("setCurrencyValue", Player.class, String.class, double.class);
            LOGGER.debug("✓ Found CurrencyPlayerData.Server.setCurrencyValue(Player, String, double)");

            // addCurrencyValue(Player, String, double) returns ErrorCodes
            currencyDataAddValue = serverClass.getMethod("addCurrencyValue", Player.class, String.class, double.class);
            LOGGER.debug("✓ Found CurrencyPlayerData.Server.addCurrencyValue(Player, String, double)");

            // Try to get EconomyAPI.syncPlayer for client sync
            try {
                economyApiClass = Class.forName("net.sixik.sdmeconomy.api.EconomyAPI");
                syncPlayerMethod = economyApiClass.getMethod("syncPlayer", ServerPlayer.class);
                LOGGER.debug("✓ Found EconomyAPI.syncPlayer(ServerPlayer)");
            } catch (Exception e) {
                LOGGER.debug("Could not find EconomyAPI.syncPlayer - client sync disabled");
            }

            LOGGER.info("✓ Successfully initialized CurrencyPlayerData API");
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("CurrencyPlayerData class not found: {}", e.getMessage());
        } catch (NoSuchFieldException e) {
            LOGGER.debug("CurrencyPlayerData.SERVER field not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.debug("CurrencyPlayerData method not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("Failed to initialize CurrencyPlayerData API: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Try to initialize using legacy SDMShop API (backwards compatibility)
     */
    private static boolean tryLegacyApi() {
        LOGGER.debug("Trying legacy API...");

        String[] possibleClasses = {
                "net.sixik.sdmshoprework.SDMShopR",
                "net.sixik.sdmshop.SDMShop",
                "net.sixik.sdmshop.api.ShopAPI",
                "net.sixik.sdmshoprework.api.ShopAPI",
                "net.sixik.sdmshop.SDMShopRework",
                "net.sixik.sdmeconomy.api.EconomyAPI",
                "net.sixik.sdmeconomy.SDMEconomy"
        };

        for (String className : possibleClasses) {
            try {
                legacyClass = Class.forName(className);
                LOGGER.debug("Found legacy class: {}", className);

                // Try to find getMoney method
                String[] getMoneyNames = { "getMoney", "getBalance", "getPlayerMoney", "getPlayerBalance" };
                Class<?>[] paramTypes = { Player.class, ServerPlayer.class, java.util.UUID.class, String.class };

                for (String methodName : getMoneyNames) {
                    for (Class<?> paramType : paramTypes) {
                        try {
                            legacyGetMoney = legacyClass.getMethod(methodName, paramType);
                            LOGGER.debug("✓ Found legacy getMoney: {}({})", methodName, paramType.getSimpleName());
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (legacyGetMoney != null)
                        break;
                }

                // Try to find setMoney method
                String[] setMoneyNames = { "setMoney", "setBalance", "setPlayerMoney", "setPlayerBalance" };
                Class<?>[][] setParamTypes = {
                        { Player.class, long.class }, { Player.class, double.class }, { Player.class, int.class },
                        { ServerPlayer.class, long.class }, { ServerPlayer.class, double.class },
                        { ServerPlayer.class, int.class },
                        { java.util.UUID.class, long.class }, { java.util.UUID.class, double.class },
                        { String.class, long.class }, { String.class, double.class }
                };

                for (String methodName : setMoneyNames) {
                    for (Class<?>[] params : setParamTypes) {
                        try {
                            legacySetMoney = legacyClass.getMethod(methodName, params);
                            LOGGER.debug("✓ Found legacy setMoney: {}({}, {})", methodName,
                                    params[0].getSimpleName(), params[1].getSimpleName());
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (legacySetMoney != null)
                        break;
                }

                if (legacyGetMoney != null && legacySetMoney != null) {
                    LOGGER.info("✓ Successfully initialized legacy API from {}", className);
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        LOGGER.debug("Legacy API not available");
        return false;
    }

    /**
     * Refresh the SERVER instance if it was null during initialization
     */
    private static void refreshServerInstance() {
        if (mode == IntegrationMode.CURRENCY_DATA && currencyPlayerDataServer == null) {
            try {
                Field serverField = currencyPlayerDataClass.getField("SERVER");
                currencyPlayerDataServer = serverField.get(null);
            } catch (Exception e) {
                LOGGER.debug("Could not refresh SERVER instance: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if SDMShop/SDM-Economy integration is available
     */
    public static boolean isAvailable() {
        refreshServerInstance();
        return mode != IntegrationMode.NONE &&
                (mode != IntegrationMode.CURRENCY_DATA || currencyPlayerDataServer != null);
    }

    /**
     * Get money from a player using SDMShop/SDM-Economy API
     */
    public static long getMoney(ServerPlayer player) {
        if (player == null) {
            LOGGER.debug("Player is null");
            return 0;
        }

        refreshServerInstance();

        try {
            switch (mode) {
                case SHOP_UTILS:
                    return getMoneyViaShopUtils(player);
                case CURRENCY_DATA:
                    return getMoneyViaCurrencyData(player);
                case LEGACY:
                    return getMoneyViaLegacy(player);
                default:
                    return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get money for player {}: {}", player.getName().getString(), e.getMessage());
            return 0;
        }
    }

    private static long getMoneyViaShopUtils(ServerPlayer player) throws Exception {
        Object result = shopUtilsGetMoney.invoke(null, player);
        return Math.round((Double) result);
    }

    private static long getMoneyViaCurrencyData(ServerPlayer player) throws Exception {
        if (currencyPlayerDataServer == null) {
            LOGGER.debug("CurrencyPlayerData.SERVER is null");
            return 0;
        }

        // Returns ErrorCodeStruct<Double>
        Object result = currencyDataGetBalance.invoke(currencyPlayerDataServer, player, DEFAULT_CURRENCY_NAME);

        // Get the 'value' field from ErrorCodeStruct
        Field valueField = result.getClass().getField("value");
        Object value = valueField.get(result);

        if (value instanceof Double) {
            return Math.round((Double) value);
        }
        return 0;
    }

    private static long getMoneyViaLegacy(ServerPlayer player) throws Exception {
        Class<?> paramType = legacyGetMoney.getParameterTypes()[0];
        Object param;

        if (paramType == Player.class || paramType == ServerPlayer.class) {
            param = player;
        } else if (paramType == java.util.UUID.class) {
            param = player.getUUID();
        } else if (paramType == String.class) {
            param = player.getName().getString();
        } else {
            return 0;
        }

        Object result = legacyGetMoney.invoke(null, param);

        if (result instanceof Long)
            return (Long) result;
        if (result instanceof Double)
            return Math.round((Double) result);
        if (result instanceof Integer)
            return (Integer) result;
        return 0;
    }

    /**
     * Set money for a player using SDMShop/SDM-Economy API
     */
    public static boolean setMoney(ServerPlayer player, long amount) {
        if (player == null) {
            LOGGER.debug("Player is null");
            return false;
        }

        refreshServerInstance();

        try {
            boolean success;
            switch (mode) {
                case SHOP_UTILS:
                    success = setMoneyViaShopUtils(player, amount);
                    break;
                case CURRENCY_DATA:
                    success = setMoneyViaCurrencyData(player, amount);
                    break;
                case LEGACY:
                    success = setMoneyViaLegacy(player, amount);
                    break;
                default:
                    return false;
            }

            if (success) {
                LOGGER.debug("Successfully set money for {}: {}", player.getName().getString(), amount);
            }
            return success;
        } catch (Exception e) {
            LOGGER.error("Failed to set money for player {} to {}: {}",
                    player.getName().getString(), amount, e.getMessage());
            return false;
        }
    }

    private static boolean setMoneyViaShopUtils(ServerPlayer player, long amount) throws Exception {
        Object result = shopUtilsSetMoney.invoke(null, player, (double) amount);
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean setMoneyViaCurrencyData(ServerPlayer player, long amount) throws Exception {
        if (currencyPlayerDataServer == null) {
            return false;
        }

        // Returns ErrorCodes enum
        Object result = currencyDataSetValue.invoke(currencyPlayerDataServer, player, DEFAULT_CURRENCY_NAME,
                (double) amount);

        // Check if result is SUCCESS
        boolean success = result.toString().equals("SUCCESS");

        // Sync player if available
        if (success && syncPlayerMethod != null) {
            try {
                syncPlayerMethod.invoke(null, player);
            } catch (Exception e) {
                LOGGER.debug("Could not sync player: {}", e.getMessage());
            }
        }

        return success;
    }

    private static boolean setMoneyViaLegacy(ServerPlayer player, long amount) throws Exception {
        Class<?>[] paramTypes = legacySetMoney.getParameterTypes();
        Object firstParam;
        Object secondParam;

        if (paramTypes[0] == Player.class || paramTypes[0] == ServerPlayer.class) {
            firstParam = player;
        } else if (paramTypes[0] == java.util.UUID.class) {
            firstParam = player.getUUID();
        } else if (paramTypes[0] == String.class) {
            firstParam = player.getName().getString();
        } else {
            return false;
        }

        if (paramTypes[1] == long.class || paramTypes[1] == Long.class) {
            secondParam = amount;
        } else if (paramTypes[1] == double.class || paramTypes[1] == Double.class) {
            secondParam = (double) amount;
        } else if (paramTypes[1] == int.class || paramTypes[1] == Integer.class) {
            secondParam = (int) amount;
        } else {
            return false;
        }

        legacySetMoney.invoke(null, firstParam, secondParam);
        return true;
    }

    /**
     * Add money to a player using SDMShop/SDM-Economy API
     */
    public static boolean addMoney(ServerPlayer player, long amount) {
        if (player == null) {
            return false;
        }

        refreshServerInstance();

        try {
            // Try direct addMoney if available (more efficient)
            if (mode == IntegrationMode.SHOP_UTILS && shopUtilsAddMoney != null) {
                Object result = shopUtilsAddMoney.invoke(null, player, (double) amount);
                return result instanceof Boolean && (Boolean) result;
            }

            if (mode == IntegrationMode.CURRENCY_DATA && currencyPlayerDataServer != null
                    && currencyDataAddValue != null) {
                Object result = currencyDataAddValue.invoke(currencyPlayerDataServer, player, DEFAULT_CURRENCY_NAME,
                        (double) amount);
                boolean success = result.toString().equals("SUCCESS");

                if (success && syncPlayerMethod != null) {
                    try {
                        syncPlayerMethod.invoke(null, player);
                    } catch (Exception e) {
                        LOGGER.debug("Could not sync player: {}", e.getMessage());
                    }
                }
                return success;
            }

            // Fallback: get current balance and set new value
            long currentBalance = getMoney(player);
            return setMoney(player, currentBalance + amount);
        } catch (Exception e) {
            LOGGER.error("Failed to add money {} to player {}: {}",
                    amount, player.getName().getString(), e.getMessage());
            return false;
        }
    }

    /**
     * Remove money from a player using SDMShop/SDM-Economy API
     */
    public static boolean removeMoney(ServerPlayer player, long amount) {
        if (player == null) {
            return false;
        }

        try {
            long currentBalance = getMoney(player);
            if (currentBalance < amount) {
                return false; // Not enough money
            }
            return setMoney(player, currentBalance - amount);
        } catch (Exception e) {
            LOGGER.error("Failed to remove money {} from player {}: {}",
                    amount, player.getName().getString(), e.getMessage());
            return false;
        }
    }

    /**
     * Deduct player balance and return the amount actually deducted
     * Returns 0 if player doesn't have enough funds or if SDMShop is not available
     */
    public static int deductPlayerBalance(ServerPlayer player, int amount) {
        if (player == null || amount <= 0 || !isAvailable()) {
            return 0;
        }

        long currentBalance = getMoney(player);
        if (currentBalance < amount) {
            return 0; // Not enough funds
        }

        if (removeMoney(player, amount)) {
            return amount;
        }

        return 0;
    }

    /**
     * Get the current integration mode (for debugging)
     */
    public static String getIntegrationMode() {
        return mode.toString();
    }
}
