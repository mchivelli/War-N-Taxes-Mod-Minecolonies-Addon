package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.server.ColonyDataCollector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Packet sent from client to server to request colony tax data for the GUI
 */
public class RequestColonyDataPacket {
    
    public RequestColonyDataPacket() {
        // Empty constructor for packet registration
    }
    
    public RequestColonyDataPacket(FriendlyByteBuf buf) {
        // No data to read for this packet
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        // No data to write for this packet
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // Collect colony data for this player
                List<ColonyTaxData> colonyData = ColonyDataCollector.collectColonyData(player);
                List<VassalIncomeData> vassalData = ColonyDataCollector.collectVassalIncomeData(player);
                
                // Send response back to client
                NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), 
                    new ColonyDataResponsePacket(colonyData, vassalData));
            }
        });
        return true;
    }
}
