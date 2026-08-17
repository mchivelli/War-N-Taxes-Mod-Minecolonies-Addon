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
    private final int requestedAmount; // -1 = pay full debt, >0 = pay this exact amount

    /** Backwards-compatible: pay the full debt. */
    public PayTaxDebtPacket(int colonyId) {
        this(colonyId, -1);
    }

    /** Pay a specific amount; -1 means full debt. */
    public PayTaxDebtPacket(int colonyId, int requestedAmount) {
        this.colonyId = colonyId;
        this.requestedAmount = requestedAmount;
    }

    public PayTaxDebtPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        // SECURITY: allow GUI to request a partial-debt payment. -1 means "pay full debt"; any other negative
        // value is treated as 0 (no-op) to prevent malicious deltas. Validated against actual debt and balance below.
        int amt = buf.readInt();
        if (amt < -1) {
            amt = 0;
        }
        this.requestedAmount = amt;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(requestedAmount);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            
            // Find the colony
            // Colony ids are only unique per dimension, so a bare getAllColonies() filter can
            // resolve a same-id colony from another dimension.
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
                
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

            // SECURITY (audit HIGH): honor the client-requested partial payment amount. Validate:
            //   - must be > 0 (sentinel -1 means "pay full debt")
            //   - must be <= remaining debt (can't overpay)
            //   - must be <= player balance (can't pay more than you have)
            int payAmount;
            if (requestedAmount == -1) {
                payAmount = debtAmount;
            } else if (requestedAmount > 0) {
                payAmount = Math.min(requestedAmount, debtAmount);
            } else {
                player.sendSystemMessage(Component.literal("Invalid debt payment amount."));
                return;
            }

            // Check if SDMShop conversion is enabled in config
            if (!TaxConfig.isSDMShopConversionEnabled()) {
                player.sendSystemMessage(Component.literal("§c✗ SDMShop conversion is disabled in config!"));
                player.sendSystemMessage(Component.literal("§eDebt payment requires SDMShop integration to be enabled"));
                return;
            }

            // Check if player has sufficient funds and deduct payment
            if (!SDMShopIntegration.isAvailable()) {
                player.sendSystemMessage(Component.literal("§c✗ SDMShop integration is not available!"));
                player.sendSystemMessage(Component.literal("§eCheck that SDMShop mod is installed and working"));
                return;
            }

            long currentBalance = SDMShopIntegration.getMoney(player);

            // SECURITY: also reject if the requested partial payment exceeds the player's balance
            if (currentBalance < payAmount) {
                player.sendSystemMessage(Component.literal("§c✗ Insufficient funds!"));
                player.sendSystemMessage(Component.literal("§c  Need: " + payAmount + ", Have: " + currentBalance));
                return;
            }

            // Deduct the validated payment amount from player's balance
            int deductedAmount = SDMShopIntegration.deductPlayerBalance(player, payAmount);
        
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
