package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EndVassalizationPayload(int colonyId) implements CustomPacketPayload {
    
    public static final Type<EndVassalizationPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "end_vassalization"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, EndVassalizationPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, EndVassalizationPayload::colonyId,
            EndVassalizationPayload::new
        );
    
    @Override
    public Type<EndVassalizationPayload> type() {
        return TYPE;
    }
    
    public static void handle(EndVassalizationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MineColonyTax.LOGGER.debug("End vassalization: {}", payload.colonyId);
        });
    }
}
