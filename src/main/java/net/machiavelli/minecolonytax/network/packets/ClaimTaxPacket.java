package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
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
        int rawAmount = buf.readInt();
        // SECURITY: reject malicious/negative amounts. Only -1 (claim all), -2 (vassal claim), and strictly positive
        // values are valid. Any other negative value (including Integer.MIN_VALUE) is clamped to 0 so the handler
        // returns a no-op instead of letting TaxManager.claimTax compute storedTax - (negative) and inflate the
        // ledger. See audit/CODEX_INDEPENDENT.md CRIT-1.
        if (rawAmount != -1 && rawAmount != -2 && rawAmount <= 0) {
            rawAmount = 0;
        }
        this.amount = rawAmount;
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

            // SECURITY: reject any amount that is not a valid sentinel (-1 claim-all, -2 vassal) or strictly positive.
            // The decoder already clamps invalid values to 0; this is a defense-in-depth check.
            if (amount == 0 || (amount < 0 && amount != -1 && amount != -2)) {
                player.sendSystemMessage(Component.literal("Invalid tax claim amount."));
                return;
            }

            // Find the colony
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            // Colony ids are only unique per dimension, so a bare getAllColonies() filter could
            // pick a same-id colony from another dimension — on this path that would move real
            // money. ColonyLookup resolves the asking player's own dimension first.
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
                
            if (colony == null) {
                player.sendSystemMessage(Component.literal("Colony not found!"));
                return;
            }
            
            // Handle different claim types
            int claimedAmount = 0;
            
            if (amount == -2) {
                // Vassal tribute claim - check if player owns this vassal.
                // SECURITY (audit Top-12 #3): VassalManager.claimVassalTribute already deducts from the vassal AND
                // credits the overlord's stored-tax ledger. Previously this path then ALSO added money directly to
                // the player's SDMShop balance / dropped a currency item, double-paying every tribute. Now we only
                // confirm the transfer and let the player claim the overlord ledger via the normal regular-claim path.
                int tribute = VassalManager.claimVassalTribute(player.getUUID(), colonyId);
                if (tribute <= 0) {
                    player.sendSystemMessage(Component.literal("No tribute available from this vassal colony."));
                    return;
                }
                player.sendSystemMessage(Component.literal("§aTransferred " + tribute + " tribute from " + colony.getName() + " to your overlord colony's tax balance."));
                return;
            } else {
                // Regular tax claim - check colony permissions
                Rank playerRank = colony.getPermissions().getRank(player.getUUID());
                if (playerRank == null || !playerRank.isColonyManager()) {
                    player.sendSystemMessage(Component.literal("You don't have permission to claim tax from this colony!"));
                    return;
                }

                // SECURITY: also honor per-player / officer-toggle settings managed by TaxPermissionManager.
                // The slash-command path (ClaimTaxCommand) gates on this; the packet path must too, otherwise
                // owners who revoke an officer's tax-claim permission see the officer still claim via GUI.
                boolean isOwner = playerRank.equals(colony.getPermissions().getRankOwner());
                boolean isOfficer = playerRank.equals(colony.getPermissions().getRankOfficer()) || isOwner;
                if (!TaxPermissionManager.canPlayerClaimTax(colony.getID(), player.getUUID(), isOwner, isOfficer)) {
                    player.sendSystemMessage(Component.literal("You do not have permission to claim taxes for this colony. Contact a colony owner."));
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
                // Deliver the claimed tax, refunding the colony ledger if delivery fails so
                // taxes are never silently lost (4.x "coins never appear" fix). The helper
                // emits its own failure/refund message; we only confirm on success.
                long before = TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()
                        ? SDMShopIntegration.getMoney(player) : -1L;
                if (net.machiavelli.minecolonytax.integration.CurrencyService
                        .deliverClaimedTaxOrRefund(player, colony, claimedAmount)) {
                    if (before >= 0L) {
                        long after = SDMShopIntegration.getMoney(player);
                        player.sendSystemMessage(Component.literal("§aAdded " + claimedAmount
                                + " to your balance (" + before + " -> " + after + ")"));
                    }
                    player.sendSystemMessage(Component.translatable("command.claimtax.success", claimedAmount, colony.getName()));
                }
            } else {
                player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
            }
        });
        return true;
    }
}
