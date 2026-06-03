package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateTaxPermissionPayload(int colonyId, boolean allowOfficers) implements CustomPacketPayload {
    
    public static final Type<UpdateTaxPermissionPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "update_tax_permission"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTaxPermissionPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, UpdateTaxPermissionPayload::colonyId,
            ByteBufCodecs.BOOL, UpdateTaxPermissionPayload::allowOfficers,
            UpdateTaxPermissionPayload::new
        );
    
    @Override
    public Type<UpdateTaxPermissionPayload> type() {
        return TYPE;
    }
    
    public static void handle(UpdateTaxPermissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MineColonyTax.LOGGER.debug("Update tax permission: colony={}, allow={}", payload.colonyId, payload.allowOfficers);
        });
    }
}
