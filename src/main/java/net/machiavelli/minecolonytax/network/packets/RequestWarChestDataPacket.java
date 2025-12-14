package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.machiavelli.minecolonytax.network.NetworkHandler;

/**
 * Client-to-Server packet to request War Chest data for a colony.
 */
public class RequestWarChestDataPacket {

    private final int colonyId;

    public RequestWarChestDataPacket(int colonyId) {
        this.colonyId = colonyId;
    }

    public RequestWarChestDataPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isWarChestEnabled())
                return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByWorld(colonyId, player.level());

            if (colony == null)
                return;

            // Check if player has access (owner or officer)
            boolean hasAccess = colony.getPermissions().getRank(player.getUUID()).isColonyManager();
            if (!hasAccess)
                return;

            // Get war chest data
            int balance = WarChestManager.getWarChestBalance(colonyId);
            int maxCapacity = TaxConfig.getWarChestMaxCapacity();
            int drainPerMinute = TaxConfig.getWarChestDrainPerMinute();
            int taxBalance = TaxManager.getStoredTaxForColony(colony);
            boolean autoSurrender = TaxConfig.isWarChestAutoSurrenderEnabled();
            double minPercent = TaxConfig.getWarChestMinPercentOfTarget();

            // Send response back to client
            NetworkHandler.sendToPlayer(player, new WarChestDataResponsePacket(
                    colonyId, balance, maxCapacity, drainPerMinute, taxBalance, autoSurrender, minPercent));
        });
        ctx.get().setPacketHandled(true);
    }
}
