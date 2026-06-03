package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PayDebtPayload(int colonyId, int amount) implements CustomPacketPayload {
    
    public static final Type<PayDebtPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "pay_debt"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PayDebtPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, PayDebtPayload::colonyId,
            ByteBufCodecs.INT, PayDebtPayload::amount,
            PayDebtPayload::new
        );
    
    @Override
    public Type<PayDebtPayload> type() {
        return TYPE;
    }
    
    public static void handle(PayDebtPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MineColonyTax.LOGGER.debug("Pay debt: colony={}, amount={}", payload.colonyId, payload.amount);
        });
    }
}
