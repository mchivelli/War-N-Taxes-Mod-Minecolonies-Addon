package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles PvP kill economy rewards - transfers a percentage of victim's balance to killer
 * Compatible with SDMShop and SDMEconomy systems
 */
@Mod.EventBusSubscriber
public class PvPKillEconomyHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(PvPKillEconomyHandler.class);
    
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        // Check if PvP kill economy is enabled
        if (!TaxConfig.ENABLE_PVP_KILL_ECONOMY.get()) {
            return;
        }
        
        // Only handle player deaths
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        
        // Check if death was caused by another player
        DamageSource damageSource = event.getSource();
        if (damageSource == null || !(damageSource.getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        
        // Don't reward self-kills
        if (victim.getUUID().equals(killer.getUUID())) {
            return;
        }
        
        // Get configured reward percentage
        double rewardPercentage = TaxConfig.PVP_KILL_REWARD_PERCENTAGE.get();
        if (rewardPercentage <= 0) {
            return;
        }
        
        // Calculate and transfer reward
        if (TaxConfig.isSDMShopConversionEnabled()) {
            handleSDMShopTransfer(victim, killer, rewardPercentage);
        } else {
            handleItemTransfer(victim, killer, rewardPercentage);
        }
    }
    
    /**
     * Handles money transfer using SDMShop API
     */
    private static void handleSDMShopTransfer(ServerPlayer victim, ServerPlayer killer, double percentage) {
        if (!SDMShopIntegration.isAvailable()) {
            LOGGER.warn("SDMShop integration enabled but SDMShop mod not available for PvP kill reward");
            return;
        }
        
        try {
            long victimBalance = SDMShopIntegration.getMoney(victim);
            if (victimBalance <= 0) {
                killer.sendSystemMessage(Component.literal("PvP Kill: " + victim.getName().getString() + " had no money to transfer.").withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }
            
            long transferAmount = Math.max(1, (long) (victimBalance * percentage));
            long killerBalance = SDMShopIntegration.getMoney(killer);
            
            // Transfer money
            if (SDMShopIntegration.setMoney(victim, victimBalance - transferAmount) &&
                SDMShopIntegration.setMoney(killer, killerBalance + transferAmount)) {
                
                // Notify both players
                victim.sendSystemMessage(Component.literal("PvP Death: Lost $" + transferAmount + " to " + killer.getName().getString()).withStyle(net.minecraft.ChatFormatting.RED));
                killer.sendSystemMessage(Component.literal("PvP Kill: Earned $" + transferAmount + " from " + victim.getName().getString()).withStyle(net.minecraft.ChatFormatting.GREEN));
                
                LOGGER.info("PvP kill economy: {} transferred ${} from {} to {}", 
                    percentage * 100 + "%", transferAmount, victim.getName().getString(), killer.getName().getString());
            } else {
                LOGGER.error("Failed to transfer ${} from {} to {} via SDMShop API", 
                    transferAmount, victim.getName().getString(), killer.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error in SDMShop PvP kill transfer: {}", e.getMessage());
        }
    }
    
    /**
     * Handles item-based transfer when SDMShop is disabled
     */
    private static void handleItemTransfer(ServerPlayer victim, ServerPlayer killer, double percentage) {
        try {
            String currencyItemName = TaxConfig.getCurrencyItemName();
            Item currencyItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(currencyItemName));
            
            if (currencyItem == null) {
                LOGGER.warn("Currency item '{}' not found for PvP kill reward", currencyItemName);
                return;
            }
            
            // Count victim's currency items in inventory
            int victimCurrencyCount = 0;
            for (ItemStack stack : victim.getInventory().items) {
                if (stack.getItem() == currencyItem) {
                    victimCurrencyCount += stack.getCount();
                }
            }
            
            if (victimCurrencyCount <= 0) {
                killer.sendSystemMessage(Component.literal("PvP Kill: " + victim.getName().getString() + " had no " + currencyItemName + " to transfer.").withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }
            
            int transferAmount = Math.max(1, (int) (victimCurrencyCount * percentage));
            
            // Remove items from victim
            int remainingToRemove = transferAmount;
            for (int i = 0; i < victim.getInventory().items.size() && remainingToRemove > 0; i++) {
                ItemStack stack = victim.getInventory().items.get(i);
                if (stack.getItem() == currencyItem) {
                    int toRemove = Math.min(remainingToRemove, stack.getCount());
                    stack.shrink(toRemove);
                    remainingToRemove -= toRemove;
                }
            }
            
            // Give items to killer
            ItemStack rewardStack = new ItemStack(currencyItem, transferAmount);
            boolean added = killer.getInventory().add(rewardStack);
            if (!added) {
                // Drop items if inventory full
                killer.drop(rewardStack, false);
            }
            
            // Notify both players
            victim.sendSystemMessage(Component.literal("PvP Death: Lost " + transferAmount + " " + currencyItemName + " to " + killer.getName().getString()).withStyle(net.minecraft.ChatFormatting.RED));
            killer.sendSystemMessage(Component.literal("PvP Kill: Earned " + transferAmount + " " + currencyItemName + " from " + victim.getName().getString()).withStyle(net.minecraft.ChatFormatting.GREEN));
            
            LOGGER.info("PvP kill economy: {} transferred {} {} from {} to {}", 
                percentage * 100 + "%", transferAmount, currencyItemName, victim.getName().getString(), killer.getName().getString());
                
        } catch (Exception e) {
            LOGGER.error("Error in item-based PvP kill transfer: {}", e.getMessage());
        }
    }
}
