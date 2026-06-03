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
 * Client-to-server payload to recall an active spy mission.
 */
public record RecallSpyPayload(String missionId) implements CustomPacketPayload {

    public static final Type<RecallSpyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "recall_spy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecallSpyPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RecallSpyPayload::missionId,
            RecallSpyPayload::new
        );

    @Override
    public Type<RecallSpyPayload> type() {
        return TYPE;
    }

    public static void handle(RecallSpyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isSpySystemEnabled()) return;

            String playerId = player.getUUID().toString();
            List<SpyMission> activeMissions = SpyManager.getActiveMissionsForPlayer(playerId);

            boolean ownsMission = activeMissions.stream()
                    .anyMatch(m -> m.getMissionId().equals(payload.missionId));
            if (ownsMission) {
                SpyManager.recallSpy(payload.missionId);
                // Trigger a UI refresh — include active + completed (intel preserved on recall)
                List<SpyMission> updatedMissions = SpyManager.getMissionsForPlayer(playerId);
                PacketDistributor.sendToPlayer(player, new SpyDataResponsePayload(updatedMissions));
            }
        });
    }
}
