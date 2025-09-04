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
            // Try to load the SDMShop class
            sdmShopClass = Class.forName("net.sixik.sdmshoprework.SDMShopR");
            
            // Get the methods we need
            getMoneyMethod = sdmShopClass.getMethod("getMoney", ServerPlayer.class);
            setMoneyMethod = sdmShopClass.getMethod("setMoney", ServerPlayer.class, long.class);
            
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
            return 0;
        }
        
        try {
            Object result = getMoneyMethod.invoke(null, player);
            return (Long) result;
        } catch (Exception e) {
            LOGGER.error("Failed to get money for player {}: {}", player.getName().getString(), e.getMessage());
            return 0;
        }
    }
    
    /**
     * Set money for a player using SDMShop API
     */
    public static boolean setMoney(ServerPlayer player, long amount) {
        if (!available || player == null) {
            return false;
        }
        
        try {
            setMoneyMethod.invoke(null, player, amount);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to set money for player {} to {}: {}", 
                    player.getName().getString(), amount, e.getMessage());
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
}
