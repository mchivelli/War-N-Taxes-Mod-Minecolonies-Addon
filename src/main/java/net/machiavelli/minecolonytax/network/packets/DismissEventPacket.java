package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.events.random.EventLogEntry;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.server.ColonyDataCollector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sent client → server to remove a single entry from a colony's event log.
 * Server verifies ACCESS_HUTS permission, then refreshes the client's colony data.
 */
public class DismissEventPacket {

    private final int colonyId;
    private final String eventId;

    public DismissEventPacket(int colonyId, String eventId) {
        this.colonyId = colonyId;
        this.eventId = eventId;
    }

    public DismissEventPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.eventId  = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeUtf(eventId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // Was pinned to the overworld, so events could not be dismissed for a colony in
            // any other dimension.
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
            if (colony == null) return;

            if (!colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS)) return;

            RandomEventManager.dismissFromEventLog(colonyId, eventId);

            // Refresh colony data so the GUI reflects the removal immediately
            List<ColonyTaxData> colonyData = ColonyDataCollector.collectColonyData(player);
            List<VassalIncomeData> vassalData = ColonyDataCollector.collectVassalIncomeData(player);
            Map<Integer, List<EventLogEntry>> eventLogData = new HashMap<>();
            for (ColonyTaxData data : colonyData) {
                eventLogData.put(data.getColonyId(), RandomEventManager.getEventLog(data.getColonyId()));
            }
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ColonyDataResponsePacket(colonyData, vassalData, eventLogData));
        });
        return true;
    }
}
