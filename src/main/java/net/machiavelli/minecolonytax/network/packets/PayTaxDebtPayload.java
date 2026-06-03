package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PayTaxDebtPayload(int colonyId) implements CustomPacketPayload {
    
    public static final Type<PayTaxDebtPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "pay_tax_debt"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PayTaxDebtPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, PayTaxDebtPayload::colonyId,
            PayTaxDebtPayload::new
        );
    
    @Override
    public Type<PayTaxDebtPayload> type() {
        return TYPE;
    }
    
    public static void handle(PayTaxDebtPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MineColonyTax.LOGGER.debug("Pay tax debt: colony={}", payload.colonyId);
        });
    }
}
