package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
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
 * Packet to handle debt payment for colonies
 */
public class PayDebtPacket {
    private final int colonyId;
    private final int amount;

    public PayDebtPacket(int colonyId, int amount) {
        this.colonyId = colonyId;
        this.amount = amount;
    }

    public static void encode(PayDebtPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.colonyId);
        buffer.writeInt(packet.amount);
    }

    public static PayDebtPacket decode(FriendlyByteBuf buffer) {
        return new PayDebtPacket(buffer.readInt(), buffer.readInt());
    }

    public static void handle(PayDebtPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getAllColonies().stream()
                .filter(c -> c.getID() == packet.colonyId)
                .findFirst()
                .orElse(null);
                
            if (colony == null) {
                player.sendSystemMessage(Component.literal("Colony not found!"));
                return;
            }
            
            // Check permissions - player must be colony manager
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(Component.literal("You don't have permission to pay debt for this colony!"));
                return;
            }
            
            // Get current debt amount
            int currentBalance = TaxManager.getStoredTaxForColony(colony);
            if (currentBalance >= 0) {
                player.sendSystemMessage(Component.literal("This colony has no debt to pay!"));
                return;
            }
            
            int currentDebt = Math.abs(currentBalance);
            int payAmount = packet.amount == -1 ? currentDebt : Math.min(packet.amount, currentDebt);
            
            if (payAmount <= 0) {
                player.sendSystemMessage(Component.literal("No debt to pay!"));
                return;
            }
            
            // Check if player has enough money/items to pay the debt
            boolean hasEnoughFunds = false;
            
            if (TaxConfig.isSDMShopConversionEnabled()) {
                // Check SDMShop balance
                double playerBalance = SDMShopIntegration.getMoney(player);
                hasEnoughFunds = playerBalance >= payAmount;
            } else {
                // Check item-based currency
                Item currencyItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName()));
                if (currencyItem != null) {
                    int itemCount = 0;
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() == currencyItem) {
                            itemCount += stack.getCount();
                        }
                    }
                    hasEnoughFunds = itemCount >= payAmount;
                }
            }
            
            if (!hasEnoughFunds) {
                player.sendSystemMessage(Component.literal("You don't have enough money to pay this debt!"));
                return;
            }
            
            // Deduct payment from player
            if (TaxConfig.isSDMShopConversionEnabled()) {
                SDMShopIntegration.removeMoney(player, (long) payAmount);
            } else {
                // Remove items from inventory
                Item currencyItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName()));
                if (currencyItem != null) {
                    int remainingToRemove = payAmount;
                    for (int i = 0; i < player.getInventory().items.size() && remainingToRemove > 0; i++) {
                        ItemStack stack = player.getInventory().items.get(i);
                        if (stack.getItem() == currencyItem) {
                            int toRemove = Math.min(remainingToRemove, stack.getCount());
                            stack.shrink(toRemove);
                            remainingToRemove -= toRemove;
                        }
                    }
                }
            }
            
            // Apply payment to colony debt
            TaxManager.payTaxDebt(colony, payAmount);
            
            player.sendSystemMessage(Component.literal("Paid " + payAmount + " towards colony debt. Remaining debt: " + 
                Math.max(0, currentDebt - payAmount)));
        });
        context.setPacketHandled(true);
    }
}
