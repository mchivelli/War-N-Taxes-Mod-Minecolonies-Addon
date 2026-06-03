package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdatePlayerTaxPermissionPayload(int colonyId, java.util.UUID playerId, boolean allowed) implements CustomPacketPayload {
    
    public static final Type<UpdatePlayerTaxPermissionPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "update_player_tax_permission"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePlayerTaxPermissionPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, UpdatePlayerTaxPermissionPayload::colonyId,
            net.minecraft.network.codec.ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.STRING.xmap(java.util.UUID::fromString, java.util.UUID::toString)), UpdatePlayerTaxPermissionPayload::playerId,
            ByteBufCodecs.BOOL, UpdatePlayerTaxPermissionPayload::allowed,
            UpdatePlayerTaxPermissionPayload::new
        );
    
    @Override
    public Type<UpdatePlayerTaxPermissionPayload> type() {
        return TYPE;
    }
    
    public static void handle(UpdatePlayerTaxPermissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MineColonyTax.LOGGER.debug("Update player permission: colony={}, player={}, allowed={}", payload.colonyId, payload.playerId, payload.allowed);
        });
    }
}
