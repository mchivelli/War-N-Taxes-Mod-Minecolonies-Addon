package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.network.GlowClientHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EntityGlowPayload(int entityId, boolean shouldGlow, int durationTicks) implements CustomPacketPayload {

    public static final Type<EntityGlowPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "entity_glow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityGlowPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,     EntityGlowPayload::entityId,
            ByteBufCodecs.BOOL,    EntityGlowPayload::shouldGlow,
            ByteBufCodecs.INT,     EntityGlowPayload::durationTicks,
            EntityGlowPayload::new
        );

    @Override
    public Type<EntityGlowPayload> type() {
        return TYPE;
    }

    public static void handle(EntityGlowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GlowClientHandler.handleGlowPacket(
            payload.entityId(), payload.shouldGlow(), payload.durationTicks()
        ));
    }
}
