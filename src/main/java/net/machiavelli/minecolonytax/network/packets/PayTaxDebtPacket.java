package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PayTaxDebtPacket {
    private final int colonyId;

    public PayTaxDebtPacket(int colonyId) {
        this.colonyId = colonyId;
    }

    public PayTaxDebtPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
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
            
            // Check permissions - must be colony manager or officer with tax claim permission
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            boolean canPayDebt = false;
            
            if (playerRank != null && playerRank.isColonyManager()) {
                canPayDebt = true;
            } else if (playerRank != null && playerRank.getId() > 0 && 
                      TaxPermissionManager.canOfficersClaim(colonyId)) {
                canPayDebt = true;
            }
            
            if (!canPayDebt) {
                player.sendSystemMessage(Component.literal("You don't have permission to pay debt for this colony!"));
                return;
            }
            
            // Get current colony tax balance
            int currentTax = TaxManager.getStoredTaxForColony(colony);
            if (currentTax >= 0) {
                player.sendSystemMessage(Component.literal("Colony has no debt to pay!"));
                return;
            }
            
            int debtAmount = Math.abs(currentTax);
            
            // Check if SDMShop conversion is enabled in config
            if (!TaxConfig.isSDMShopConversionEnabled()) {
                player.sendSystemMessage(Component.literal("§c✗ SDMShop conversion is disabled in config!"));
                player.sendSystemMessage(Component.literal("§eDebt payment requires SDMShop integration to be enabled"));
                return;
            }
            
            // Check if player has sufficient funds and deduct payment
            long currentBalance = SDMShopIntegration.getMoney(player);
        
            if (!SDMShopIntegration.isAvailable()) {
                player.sendSystemMessage(Component.literal("§c✗ SDMShop integration is not available!"));
                player.sendSystemMessage(Component.literal("§eCheck that SDMShop mod is installed and working"));
                return;
            }
        
            // Check if player has enough balance
            if (currentBalance < debtAmount) {
                player.sendSystemMessage(Component.literal("§c✗ Insufficient funds!"));
                player.sendSystemMessage(Component.literal("§c  Need: " + debtAmount + ", Have: " + currentBalance));
                return;
            }
        
            // Deduct the debt amount from player's balance
            int deductedAmount = SDMShopIntegration.deductPlayerBalance(player, debtAmount);
        
            if (deductedAmount <= 0) {
                player.sendSystemMessage(Component.literal("§c✗ Failed to deduct payment from your balance!"));
                return;
            }
        
            long newBalance = SDMShopIntegration.getMoney(player);
            player.sendSystemMessage(Component.literal("§a✓ Successfully deducted " + deductedAmount + " from balance"));
            player.sendSystemMessage(Component.literal("§a  Balance: " + currentBalance + " → " + newBalance));
            
            // Apply payment to colony debt
            int paidAmount = TaxManager.payTaxDebt(colony, deductedAmount);
            
            // Send confirmation message
            player.sendSystemMessage(Component.literal(
                "Paid " + paidAmount + " towards " + colony.getName() + "'s debt. " +
                "Remaining debt: " + Math.max(0, debtAmount - paidAmount)));
        });
        ctx.get().setPacketHandled(true);
    }
}
