package net.machiavelli.minecolonytax.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.machiavelli.minecolonytax.TaxConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for handling item operations, especially for modded items
 */
public class ItemUtils {
    
    private static final Logger LOGGER = LogManager.getLogger(ItemUtils.class);
    
    /**
     * Gives items to a player using direct inventory manipulation with fallback to give command
     * 
     * @param player The player to give items to
     * @param itemName The registry name of the item (e.g., "numismatics:spur")
     * @param amount The amount to give
     * @return true if items were successfully given, false otherwise
     */
    public static boolean giveItemsToPlayer(ServerPlayer player, String itemName, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        
        // Try to get the item from the registry
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
        if (item != null) {
            // Use direct inventory manipulation
            ItemStack itemStack = new ItemStack(item, amount);
            boolean added = player.getInventory().add(itemStack);
            if (!added) {
                // If inventory is full, drop items near player
                player.drop(itemStack, false);
                player.sendSystemMessage(Component.translatable("taxmanager.inventory_full", amount, itemName));
                LOGGER.debug("Player's inventory was full, dropped {} {} near them", amount, itemName);
            } else {
                player.sendSystemMessage(Component.translatable("taxmanager.currency_received", amount, itemName));
                LOGGER.debug("Successfully gave {} {} to player {}", amount, itemName, player.getName().getString());
            }
            return true;
        } else {
            // Fallback to give command if item not found in registry
            LOGGER.warn("Item {} not found in registry, falling back to give command", itemName);
            String giveCommand = String.format("give %s %s %d", player.getName().getString(), itemName, amount);
            try {
                player.getServer().getCommands().performPrefixedCommand(
                    player.getServer().createCommandSourceStack(), 
                    giveCommand
                );
                LOGGER.debug("Executed fallback give command: {}", giveCommand);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to execute give command: {}", giveCommand, e);
                return false;
            }
        }
    }
    
    /**
     * Gives currency items to a player using the configured currency item
     * 
     * @param player The player to give currency to
     * @param amount The amount of currency to give
     * @return true if currency was successfully given, false otherwise
     */
    public static boolean giveCurrencyToPlayer(ServerPlayer player, int amount) {
        return giveItemsToPlayer(player, TaxConfig.getCurrencyItemName(), amount);
    }
} 