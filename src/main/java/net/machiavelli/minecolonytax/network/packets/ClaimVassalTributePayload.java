package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClaimVassalTributePayload(int vassalColonyId) implements CustomPacketPayload {
    
    public static final Type<ClaimVassalTributePayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "claim_vassal_tribute"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimVassalTributePayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, ClaimVassalTributePayload::vassalColonyId,
            ClaimVassalTributePayload::new
        );
    
    @Override
    public Type<ClaimVassalTributePayload> type() {
        return TYPE;
    }
    
    public static void handle(ClaimVassalTributePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MineColonyTax.LOGGER.debug("Claim vassal tribute: {}", payload.vassalColonyId);
        });
    }
}
