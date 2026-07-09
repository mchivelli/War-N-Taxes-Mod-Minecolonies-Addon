package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client-to-server payload to request spy mission data for the GUI.
 */
public record RequestSpyDataPayload() implements CustomPacketPayload {

    public static final Type<RequestSpyDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "request_spy_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSpyDataPayload> STREAM_CODEC =
        StreamCodec.unit(new RequestSpyDataPayload());

    @Override
    public Type<RequestSpyDataPayload> type() {
        return TYPE;
    }

    public static void handle(RequestSpyDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isSpySystemEnabled()) return;

            String playerId = player.getUUID().toString();
            // GUI only shows active deployments; completed missions deliver intel via Written Book
            List<SpyMission> missions = SpyManager.getActiveMissionsForPlayer(playerId);
            PacketDistributor.sendToPlayer(player, new SpyDataResponsePayload(missions, player));
        });
    }
}
