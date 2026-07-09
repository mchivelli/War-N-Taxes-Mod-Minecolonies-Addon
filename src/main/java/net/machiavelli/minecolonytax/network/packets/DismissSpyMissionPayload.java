package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Sent client -> server when a player clicks "Dismiss" on a completed spy mission entry.
 * Removes the mission from COMPLETED_MISSIONS so it no longer appears in the GUI.
 */
public record DismissSpyMissionPayload(String missionId) implements CustomPacketPayload {

    public static final Type<DismissSpyMissionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "dismiss_spy_mission"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DismissSpyMissionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DismissSpyMissionPayload::missionId,
            DismissSpyMissionPayload::new
        );

    @Override
    public Type<DismissSpyMissionPayload> type() {
        return TYPE;
    }

    public static void handle(DismissSpyMissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isSpySystemEnabled()) return;

            String playerId = player.getUUID().toString();
            SpyManager.dismissMission(payload.missionId, playerId);

            // Push the updated (shorter) mission list back to the client
            List<SpyMission> updated = SpyManager.getMissionsForPlayer(playerId);
            PacketDistributor.sendToPlayer(player, new SpyDataResponsePayload(updated, player));
        });
    }
}
