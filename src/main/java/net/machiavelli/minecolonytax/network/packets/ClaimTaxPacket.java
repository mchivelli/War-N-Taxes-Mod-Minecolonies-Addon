package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to claim tax from a colony
 */
public class ClaimTaxPacket {
    private final int colonyId;
    private final int amount; // -1 for all
    
    public ClaimTaxPacket(int colonyId, int amount) {
        this.colonyId = colonyId;
        this.amount = amount;
    }
    
    public ClaimTaxPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.amount = buf.readInt();
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(amount);
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            
            // Find the colony
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
                
            if (colony == null) {
                player.sendSystemMessage(Component.literal("Colony not found!"));
                return;
            }
            
            // Handle different claim types
            int claimedAmount = 0;
            
            if (amount == -2) {
                // Vassal tribute claim - check if player owns this vassal
                claimedAmount = VassalManager.claimVassalTribute(player.getUUID(), colonyId);
                if (claimedAmount <= 0) {
                    player.sendSystemMessage(Component.literal("No tribute available from this vassal colony."));
                    return;
                }
            } else {
                // Regular tax claim - check colony permissions
                Rank playerRank = colony.getPermissions().getRank(player.getUUID());
                if (playerRank == null || !playerRank.isColonyManager()) {
                    player.sendSystemMessage(Component.literal("You don't have permission to claim tax from this colony!"));
                    return;
                }
                
                // Check for war restrictions
                if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
                    player.sendSystemMessage(Component.literal("Cannot claim tax while colony is at war!"));
                    return;
                }
                
                // Check for raid restrictions
                if (RaidManager.getActiveRaidForColony(colonyId) != null) {
                    player.sendSystemMessage(Component.literal("Cannot claim tax while colony is under raid!"));
                    return;
                }
                
                // Claim the tax
                claimedAmount = TaxManager.claimTax(colony, amount);
            }
            
            if (claimedAmount > 0) {
                // Give the player the claimed tax
                boolean paymentSuccessful = false;
                
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    // Use SDMShop integration
                    if (SDMShopIntegration.isAvailable()) {
                        long currentBalance = SDMShopIntegration.getMoney(player);
                        
                        paymentSuccessful = SDMShopIntegration.addMoney(player, claimedAmount);
                        
                        if (paymentSuccessful) {
                            long newBalance = SDMShopIntegration.getMoney(player);
                            player.sendSystemMessage(Component.literal("§a✓ Successfully added " + claimedAmount + " to your balance!"));
                            player.sendSystemMessage(Component.literal("§a  Balance: " + currentBalance + " → " + newBalance));
                        } else {
                            player.sendSystemMessage(Component.literal("§c✗ Failed to add money to SDMShop balance!"));
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§c✗ SDMShop integration is not available!"));
                        player.sendSystemMessage(Component.literal("§eCheck that SDMShop mod is installed and working"));
                    }
                } else {
                    // Use item-based currency
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName()));
                    if (item != null) {
                        ItemStack itemStack = new ItemStack(item, claimedAmount);
                        boolean added = player.getInventory().add(itemStack);
                        if (!added) {
                            // If inventory is full, drop items near player
                            player.drop(itemStack, false);
                            player.sendSystemMessage(Component.translatable("taxmanager.inventory_full", claimedAmount, TaxConfig.getCurrencyItemName()));
                        } else {
                            player.sendSystemMessage(Component.translatable("taxmanager.currency_received", claimedAmount, TaxConfig.getCurrencyItemName()));
                        }
                        paymentSuccessful = true;
                    } else {
                        player.sendSystemMessage(Component.literal("§c✗ Currency item not found: " + TaxConfig.getCurrencyItemName()));
                    }
                }
                
                if (paymentSuccessful) {
                    player.sendSystemMessage(Component.translatable("command.claimtax.success", claimedAmount, colony.getName()));
                } else {
                    player.sendSystemMessage(Component.literal("§c✗ Failed to claim tax - payment system error!"));
                }
            } else {
                player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
            }
        });
        return true;
    }
}
