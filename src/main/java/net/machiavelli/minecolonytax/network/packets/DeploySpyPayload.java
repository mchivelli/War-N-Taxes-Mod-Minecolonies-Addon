package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client-to-server payload to deploy a spy mission.
 */
public record DeploySpyPayload(int targetColonyId, String missionType) implements CustomPacketPayload {

    public static final Type<DeploySpyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "deploy_spy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeploySpyPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, DeploySpyPayload::targetColonyId,
            ByteBufCodecs.STRING_UTF8, DeploySpyPayload::missionType,
            DeploySpyPayload::new
        );

    @Override
    public Type<DeploySpyPayload> type() {
        return TYPE;
    }

    public static void handle(DeploySpyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isSpySystemEnabled()) return;

            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                    .getIColonyByOwner(player.level(), player);
            if (colony == null) return;

            if (!colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
                player.sendSystemMessage(
                        Component.literal("Only officers can deploy spies.").withStyle(ChatFormatting.RED));
                return;
            }

            SpyManager.deploySpyMission(player, colony.getID(), payload.targetColonyId, payload.missionType);

            // Trigger a UI refresh after deploying
            String playerId = player.getUUID().toString();
            List<SpyMission> missions = SpyManager.getMissionsForPlayer(playerId);
            PacketDistributor.sendToPlayer(player, new SpyDataResponsePayload(missions, player));
        });
    }
}
