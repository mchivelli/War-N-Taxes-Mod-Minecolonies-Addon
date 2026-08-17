package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.network.NetworkHandler;

/**
 * Client-to-Server packet to request treasury data for a colony.
 */
public class RequestTreasuryDataPacket {

    private final int colonyId;

    public RequestTreasuryDataPacket(int colonyId) {
        this.colonyId = colonyId;
    }

    public RequestTreasuryDataPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // These gates used to fail silently, so the war chest screen simply stayed
            // empty with no way to tell why. State the reason instead.
            if (!TaxConfig.isTreasuryEnabled()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("War chest is disabled (EnableWarChest = false).")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);

            if (colony == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("War chest: colony not found in this dimension.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            // Null-safe rank check: getRank(uuid) returns null for non-members and
            // would NPE on the main server thread when sender is not in this colony.
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("War chest: you need colony-manager rank in this colony.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            int balance = TreasuryManager.getTreasuryBalance(colonyId);
            int maxCapacity = TaxConfig.getTreasuryMaxCapacity();
            int drainPerMinute = TaxConfig.getTreasuryDrainPerMinute();
            int taxBalance = TaxManager.getStoredTaxForColony(colony);
            boolean autoSurrender = TaxConfig.isTreasuryAutoSurrenderEnabled();
            double minPercent = TaxConfig.getTreasuryMinPercentOfTarget();
            // -1 signals wallet not available; client uses this to disable wallet tab
            long walletBalance = SDMShopIntegration.isAvailable()
                    ? SDMShopIntegration.getMoney(player) : -1L;

            NetworkHandler.sendToPlayer(player, new TreasuryDataResponsePacket(
                    colonyId, balance, maxCapacity, drainPerMinute, taxBalance,
                    autoSurrender, minPercent, walletBalance));
        });
        ctx.get().setPacketHandled(true);
    }
}
