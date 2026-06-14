package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record RequestColonyDataPayload(int colonyId) implements CustomPacketPayload {
    
    public static final Type<RequestColonyDataPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "request_colony_data"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestColonyDataPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, RequestColonyDataPayload::colonyId,
            RequestColonyDataPayload::new
        );
    
    @Override
    public Type<RequestColonyDataPayload> type() {
        return TYPE;
    }
    
    public static void handle(RequestColonyDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<net.machiavelli.minecolonytax.gui.data.ColonyTaxData> colonyData =
                net.machiavelli.minecolonytax.server.ColonyDataCollector.collectColonyData(player);
            List<net.machiavelli.minecolonytax.gui.data.VassalIncomeData> vassalData =
                net.machiavelli.minecolonytax.server.ColonyDataCollector.collectVassalIncomeData(player);

            java.util.Map<Integer, List<net.machiavelli.minecolonytax.events.random.EventLogEntry>> eventLogData =
                new java.util.HashMap<>();
            for (net.machiavelli.minecolonytax.gui.data.ColonyTaxData data : colonyData) {
                eventLogData.put(data.getColonyId(), buildEventLog(data.getColonyId(), player));
            }

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ColonyDataResponsePayload(colonyData, vassalData, eventLogData));
        });
    }

    /**
     * Build the per-colony event log shown in the GUI Events view: the random-event history
     * (with dismiss support) plus raid history, newest-first. (Active war/besiege/occupation
     * rows and structured-war history are a documented follow-up; Neo's HistoryManager has no
     * structured-war records.)
     */
    public static List<net.machiavelli.minecolonytax.events.random.EventLogEntry> buildEventLog(
            int colonyId, ServerPlayer player) {
        java.util.List<net.machiavelli.minecolonytax.events.random.EventLogEntry> entries =
            new java.util.ArrayList<>(
                net.machiavelli.minecolonytax.events.random.RandomEventManager.getEventLog(colonyId));
        return entries;
    }
}
