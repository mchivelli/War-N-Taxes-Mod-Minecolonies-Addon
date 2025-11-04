package net.machiavelli.minecolonytax.integration;

import net.minecraft.server.level.ServerPlayer;
import net.machiavelli.minecolonytax.MineColonyTax;

/**
 * Compatibility layer for SDMShop integration.
 * TODO: Update to SDMShop 2.0 API once package structure is confirmed.
 * 
 * This class provides a safe interface that compiles and falls back gracefully
 * if SDMShop is not available or the API has changed.
 */
public class SDMShopCompat {
    
    private static Boolean sdmShopAvailable = null;
    
    /**
     * Check if SDMShop is available
     */
    public static boolean isAvailable() {
        if (sdmShopAvailable == null) {
            try {
                // Try to load SDMShop class
                Class.forName("net.sixik.sdm_economy.api.EconomyAPI");
                sdmShopAvailable = true;
                MineColonyTax.LOGGER.info("SDMShop detected and available");
            } catch (ClassNotFoundException e) {
                sdmShopAvailable = false;
                MineColonyTax.LOGGER.warn("SDMShop not found - using fallback currency system");
            }
        }
        return sdmShopAvailable;
    }
    
    /**
     * Get player balance (returns 0 if SDMShop unavailable)
     */
    public static long getBalance(ServerPlayer player) {
        if (!isAvailable()) {
            return 0;
        }
        
        try {
            // TODO: Implement actual SDMShop API call when package is confirmed
            // return SDMShopCompat.getBalance(player);
            MineColonyTax.LOGGER.debug("SDMShop balance check - using fallback");
            return 0;
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error getting SDMShop balance: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get player money (returns 0 if SDMShop unavailable)
     */
    public static long getMoney(ServerPlayer player) {
        if (!isAvailable()) {
            return 0;
        }
        
        try {
            // TODO: Implement actual SDMShop API call when package is confirmed
            // return SDMEconomy.getMoney(player);
            MineColonyTax.LOGGER.debug("SDMShop getMoney - using fallback");
            return 0;
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error getting SDMShop money: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Set player money (returns false if SDMShop unavailable)
     */
    public static boolean setMoney(ServerPlayer player, long amount) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // TODO: Implement actual SDMShop API call when package is confirmed
            // return SDMEconomy.setMoney(player, amount);
            MineColonyTax.LOGGER.debug("SDMShop setMoney - using fallback");
            return false;
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error setting SDMShop money: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Add money to player (returns false if SDMShop unavailable)
     */
    public static boolean addMoney(ServerPlayer player, long amount) {
        long current = getMoney(player);
        return setMoney(player, current + amount);
    }
    
    /**
     * Remove money from player (returns false if SDMShop unavailable)
     */
    public static boolean removeMoney(ServerPlayer player, long amount) {
        long current = getMoney(player);
        if (current < amount) {
            return false;
        }
        return setMoney(player, current - amount);
    }
    
    /**
     * Transfer money between players (returns false if SDMShop unavailable)
     */
    public static boolean transferMoney(ServerPlayer from, ServerPlayer to, long amount) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // TODO: Implement actual SDMShop API call when package is confirmed
            // return SDMShopCompat.transferMoney(from, to, amount);
            MineColonyTax.LOGGER.debug("SDMShop transfer money - using fallback");
            return false;
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error transferring SDMShop money: " + e.getMessage());
            return false;
        }
    }
}
