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
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ColonyDataResponsePayload(colonyData, vassalData));
        });
    }
}
