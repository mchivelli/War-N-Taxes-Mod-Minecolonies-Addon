package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet for claiming vassal tribute from the GUI
 */
public class ClaimVassalTributePacket {
    private final int vassalColonyId;

    public ClaimVassalTributePacket(int vassalColonyId) {
        this.vassalColonyId = vassalColonyId;
    }

    public ClaimVassalTributePacket(FriendlyByteBuf buf) {
        this.vassalColonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(vassalColonyId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                handlePacket(player, vassalColonyId);
            }
        });
        context.setPacketHandled(true);
        return true;
    }

    private void handlePacket(ServerPlayer player, int vassalColonyId) {
        try {
            int tributeAmount = VassalManager.claimVassalTribute(player.getUUID(), vassalColonyId);
            
            if (tributeAmount > 0) {
                // Give currency to player
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    // Use SDMShop integration
                    long currentBalance = SDMShopIntegration.getMoney(player);
                    SDMShopIntegration.setMoney(player, currentBalance + tributeAmount);
                    player.sendSystemMessage(Component.literal("§a[Vassal Tribute] Claimed " + tributeAmount + " coins from vassal colony!"));
                } else {
                    // Fallback to item currency
                    String currencyItemName = TaxConfig.getCurrencyItemName();
                    // For now, just send a message - item dropping logic would be more complex
                    player.sendSystemMessage(Component.literal("§a[Vassal Tribute] Claimed " + tributeAmount + " " + currencyItemName + " from vassal colony!"));
                }
            } else {
                player.sendSystemMessage(Component.literal("§c[Vassal Tribute] No tribute available to claim from this vassal."));
            }
            
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c[Error] Failed to claim vassal tribute: " + e.getMessage()));
        }
    }
}
