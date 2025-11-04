package net.machiavelli.minecolonytax.integration;

import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Integration wrapper for SDMShop to avoid compilation errors when the mod is not present
 * while preserving full functionality when it is available.
 */
public class SDMShopIntegration {
    private static final Logger LOGGER = LogManager.getLogger(SDMShopIntegration.class);
    
    private static Class<?> sdmShopClass = null;
    private static Method getMoneyMethod = null;
    private static Method setMoneyMethod = null;
    private static boolean initialized = false;
    private static boolean available = false;
    
    static {
        initialize();
    }
    
    private static void initialize() {
        if (initialized) return;
        
        try {
            LOGGER.info("Attempting to initialize SDMShop integration...");
            
            // Try different possible SDMShop class names
            String[] possibleClasses = {
                "net.sixik.SDMEconomyework.SDMEconomy",
                "net.sixik.sdmshop.SDMShop", 
                "net.sixik.sdmshop.api.ShopAPI",
                "net.sixik.SDMEconomyework.api.ShopAPI",
                "net.sixik.sdmshop.SDMEconomyework",
                "net.sixik.SDMShopCompat.api.EconomyAPI",
                "net.sixik.SDMShopCompat.SDMEconomy"
            };
            
            for (String className : possibleClasses) {
                try {
                    LOGGER.info("Trying SDMShop class: {}", className);
                    sdmShopClass = Class.forName(className);
                    LOGGER.info("✓ Found SDMShop class: {}", className);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.debug("✗ SDMShop class not found: {}", className);
                }
            }
            
            if (sdmShopClass == null) {
                LOGGER.warn("No SDMShop classes found. Tried: {}", String.join(", ", possibleClasses));
                throw new ClassNotFoundException("No SDMShop classes found");
            }
            
            // Try different possible method names and parameter types
            String[] getMoneyMethods = {"getMoney", "getBalance", "getPlayerMoney", "getPlayerBalance"};
            Class<?>[] getMoneyParamTypes = {
                net.minecraft.world.entity.player.Player.class,
                ServerPlayer.class, 
                java.util.UUID.class, 
                String.class
            };
            
            String[] setMoneyMethods = {"setMoney", "setBalance", "setPlayerMoney", "setPlayerBalance"};
            Class<?>[][] setMoneyParamTypes = {
                {net.minecraft.world.entity.player.Player.class, long.class},
                {net.minecraft.world.entity.player.Player.class, double.class},
                {net.minecraft.world.entity.player.Player.class, int.class},
                {ServerPlayer.class, long.class},
                {ServerPlayer.class, double.class},
                {ServerPlayer.class, int.class},
                {java.util.UUID.class, long.class},
                {java.util.UUID.class, double.class},
                {String.class, long.class},
                {String.class, double.class}
            };
            
            // Try to find getMoney method
            for (String methodName : getMoneyMethods) {
                for (Class<?> paramType : getMoneyParamTypes) {
                    try {
                        getMoneyMethod = sdmShopClass.getMethod(methodName, paramType);
                        LOGGER.info("✓ Found getMoney method: {} with parameter type: {}", methodName, paramType.getSimpleName());
                        break;
                    } catch (NoSuchMethodException e) {
                        LOGGER.debug("✗ Method not found: {}({})", methodName, paramType.getSimpleName());
                    }
                }
                if (getMoneyMethod != null) break;
            }
            
            // Try to find setMoney method
            for (String methodName : setMoneyMethods) {
                for (Class<?>[] paramTypes : setMoneyParamTypes) {
                    try {
                        setMoneyMethod = sdmShopClass.getMethod(methodName, paramTypes);
                        LOGGER.info("✓ Found setMoney method: {} with parameter types: {}", methodName, 
                                java.util.Arrays.toString(paramTypes));
                        break;
                    } catch (NoSuchMethodException e) {
                        LOGGER.debug("✗ Method not found: {}({})", methodName, 
                                java.util.Arrays.toString(paramTypes));
                    }
                }
                if (setMoneyMethod != null) break;
            }
            
            if (getMoneyMethod == null) {
                LOGGER.error("Could not find any getMoney method in class: {}", sdmShopClass.getName());
                // Log all available methods for debugging
                java.lang.reflect.Method[] methods = sdmShopClass.getMethods();
                LOGGER.error("Available methods in {}:", sdmShopClass.getName());
                for (java.lang.reflect.Method method : methods) {
                    LOGGER.error("  - {}", method.toString());
                }
            }
            
            if (setMoneyMethod == null) {
                LOGGER.error("Could not find any setMoney method in class: {}", sdmShopClass.getName());
            }
            
            if (getMoneyMethod == null || setMoneyMethod == null) {
                throw new NoSuchMethodException("Required SDMShop methods not found");
            }
            
            available = true;
            LOGGER.info("SDMShop integration successfully initialized");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("SDMShop not found - integration disabled");
        } catch (NoSuchMethodException e) {
            LOGGER.warn("SDMShop API methods not found - integration disabled: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize SDMShop integration: {}", e.getMessage());
        }
        
        initialized = true;
    }
    
    /**
     * Check if SDMShop integration is available
     */
    public static boolean isAvailable() {
        return available;
    }
    
    /**
     * Get money from a player using SDMShop API
     */
    public static long getMoney(ServerPlayer player) {
        if (!available || player == null) {
            LOGGER.debug("SDMShop not available or player is null");
            return 0;
        }
        
        try {
            LOGGER.debug("Getting money for player: {}", player.getName().getString());
            
            // Handle different parameter types the method might expect
            Object result;
            Class<?> paramType = getMoneyMethod.getParameterTypes()[0];
            
            if (paramType == net.minecraft.world.entity.player.Player.class || paramType == ServerPlayer.class) {
                result = getMoneyMethod.invoke(null, player);
            } else if (paramType == java.util.UUID.class) {
                result = getMoneyMethod.invoke(null, player.getUUID());
            } else if (paramType == String.class) {
                result = getMoneyMethod.invoke(null, player.getName().getString());
            } else {
                LOGGER.error("Unknown parameter type for getMoney: {}", paramType);
                return 0;
            }
            
            // Handle different return types (long, double, int)
            long amount;
            if (result instanceof Long) {
                amount = (Long) result;
            } else if (result instanceof Double) {
                amount = Math.round((Double) result);
            } else if (result instanceof Integer) {
                amount = (Integer) result;
            } else {
                LOGGER.error("Unknown return type from getMoney: {}", result.getClass());
                return 0;
            }
            
            LOGGER.debug("Retrieved balance for {}: {}", player.getName().getString(), amount);
            return amount;
        } catch (Exception e) {
            LOGGER.error("Failed to get money for player {}: {}", player.getName().getString(), e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Set money for a player using SDMShop API
     */
    public static boolean setMoney(ServerPlayer player, long amount) {
        if (!available || player == null) {
            LOGGER.debug("SDMShop not available or player is null");
            return false;
        }
        
        try {
            LOGGER.debug("Setting money for player {}: {}", player.getName().getString(), amount);
            
            // Handle different parameter types the method might expect
            Class<?>[] paramTypes = setMoneyMethod.getParameterTypes();
            Class<?> firstParamType = paramTypes[0];
            Class<?> secondParamType = paramTypes[1];
            
            Object firstParam;
            Object secondParam;
            
            // Handle first parameter (player identifier)
            if (firstParamType == net.minecraft.world.entity.player.Player.class || firstParamType == ServerPlayer.class) {
                firstParam = player;
            } else if (firstParamType == java.util.UUID.class) {
                firstParam = player.getUUID();
            } else if (firstParamType == String.class) {
                firstParam = player.getName().getString();
            } else {
                LOGGER.error("Unknown first parameter type for setMoney: {}", firstParamType);
                return false;
            }
            
            // Handle second parameter (amount)
            if (secondParamType == long.class || secondParamType == Long.class) {
                secondParam = amount;
            } else if (secondParamType == double.class || secondParamType == Double.class) {
                secondParam = (double) amount;
            } else if (secondParamType == int.class || secondParamType == Integer.class) {
                secondParam = (int) amount;
            } else {
                LOGGER.error("Unknown second parameter type for setMoney: {}", secondParamType);
                return false;
            }
            
            setMoneyMethod.invoke(null, firstParam, secondParam);
            LOGGER.debug("Successfully set money for {}: {}", player.getName().getString(), amount);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to set money for player {} to {}: {}", 
                    player.getName().getString(), amount, e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Add money to a player using SDMShop API
     */
    public static boolean addMoney(ServerPlayer player, long amount) {
        if (!available || player == null) {
            return false;
        }
        
        try {
            long currentBalance = getMoney(player);
            return setMoney(player, currentBalance + amount);
        } catch (Exception e) {
            LOGGER.error("Failed to add money {} to player {}: {}", 
                    amount, player.getName().getString(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove money from a player using SDMShop API
     */
    public static boolean removeMoney(ServerPlayer player, long amount) {
        if (!available || player == null) {
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
        if (!available || player == null || amount <= 0) {
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
}
