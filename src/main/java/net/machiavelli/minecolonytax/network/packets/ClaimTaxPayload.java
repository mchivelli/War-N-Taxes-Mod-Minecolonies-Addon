package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet for claiming taxes from a colony.
 * Sent from client to server when player uses the claim tax GUI or command.
 */
public record ClaimTaxPayload(int colonyId, int amount) implements CustomPacketPayload {

    public static final Type<ClaimTaxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "claim_tax"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimTaxPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, ClaimTaxPayload::colonyId,
            ByteBufCodecs.INT, ClaimTaxPayload::amount,
            ClaimTaxPayload::new
        );

    @Override
    public Type<ClaimTaxPayload> type() {
        return TYPE;
    }

    public static void handle(ClaimTaxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getAllColonies().stream()
                .filter(c -> c.getID() == payload.colonyId)
                .findFirst()
                .orElse(null);

            if (colony == null) {
                player.sendSystemMessage(Component.literal("Colony not found!"));
                return;
            }

            int claimedAmount = 0;

            if (payload.amount == -2) {
                // Vassal tribute claim
                claimedAmount = VassalManager.claimVassalTribute(player.getUUID(), payload.colonyId);
                if (claimedAmount <= 0) {
                    player.sendSystemMessage(Component.literal("No tribute available from this vassal colony."));
                    return;
                }
            } else {
                Rank playerRank = colony.getPermissions().getRank(player.getUUID());
                if (playerRank == null || !playerRank.isColonyManager()) {
                    player.sendSystemMessage(Component.literal("You don't have permission to claim tax from this colony!"));
                    return;
                }
                if (WarSystem.ACTIVE_WARS.containsKey(payload.colonyId)) {
                    player.sendSystemMessage(Component.literal("Cannot claim tax while colony is at war!"));
                    return;
                }
                if (RaidManager.getActiveRaidForColony(payload.colonyId) != null) {
                    player.sendSystemMessage(Component.literal("Cannot claim tax while colony is under raid!"));
                    return;
                }
                claimedAmount = TaxManager.claimTax(colony, payload.amount);
            }

            if (claimedAmount > 0) {
                boolean paymentSuccessful = false;

                if (TaxConfig.isSDMShopConversionEnabled()) {
                    if (SDMShopIntegration.isAvailable()) {
                        long currentBalance = SDMShopIntegration.getMoney(player);
                        paymentSuccessful = SDMShopIntegration.addMoney(player, claimedAmount);
                        if (paymentSuccessful) {
                            long newBalance = SDMShopIntegration.getMoney(player);
                            player.sendSystemMessage(Component.literal("\u00a7a\u2713 Successfully added " + claimedAmount + " to your balance!"));
                            player.sendSystemMessage(Component.literal("\u00a7a  Balance: " + currentBalance + " \u2192 " + newBalance));
                        } else {
                            player.sendSystemMessage(Component.literal("\u00a7c\u2717 Failed to add money to SDMShop balance!"));
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("\u00a7c\u2717 SDMShop integration is not available!"));
                        player.sendSystemMessage(Component.literal("\u00a7eCheck that SDMShop mod is installed and working"));
                    }
                } else {
                    ResourceLocation itemId = ResourceLocation.tryParse(TaxConfig.getCurrencyItemName());
                    Item item = itemId != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId) : null;
                    if (item != null) {
                        ItemStack itemStack = new ItemStack(item, claimedAmount);
                        boolean added = player.getInventory().add(itemStack);
                        if (!added) {
                            player.drop(itemStack, false);
                            player.sendSystemMessage(Component.translatable("taxmanager.inventory_full", claimedAmount, TaxConfig.getCurrencyItemName()));
                        } else {
                            player.sendSystemMessage(Component.translatable("taxmanager.currency_received", claimedAmount, TaxConfig.getCurrencyItemName()));
                        }
                        paymentSuccessful = true;
                    } else {
                        player.sendSystemMessage(Component.literal("\u00a7c\u2717 Currency item not found: " + TaxConfig.getCurrencyItemName()));
                    }
                }

                if (paymentSuccessful) {
                    player.sendSystemMessage(Component.translatable("command.claimtax.success", claimedAmount, colony.getName()));
                } else {
                    // Payout failed AFTER claimTax already deducted + persisted the tax \u2014 refund it so
                    // the colony's tax is not silently destroyed (same fix as /wnt claimtax and
                    // /claimtax). This is the GUI-button path, the most likely one a player uses.
                    // The vassal-tribute branch (payload.amount == -2) drew its amount from
                    // VassalManager, NOT the tax pool, so it must NOT be refunded into the tax here.
                    if (payload.amount != -2) {
                        TaxManager.refundClaimedTax(colony, claimedAmount);
                        player.sendSystemMessage(Component.literal("\u00a7c\u2717 Failed to claim tax - payment system error! Tax returned to colony."));
                    } else {
                        player.sendSystemMessage(Component.literal("\u00a7c\u2717 Failed to pay out vassal tribute - payment system error!"));
                    }
                }
            } else {
                player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
            }
        });
    }
}
