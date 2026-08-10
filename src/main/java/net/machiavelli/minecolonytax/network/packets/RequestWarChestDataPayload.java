package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload to request War Chest data for a colony.
 */
public record RequestWarChestDataPayload(int colonyId) implements CustomPacketPayload {

    public static final Type<RequestWarChestDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "request_war_chest_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWarChestDataPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, RequestWarChestDataPayload::colonyId,
            RequestWarChestDataPayload::new
        );

    @Override
    public Type<RequestWarChestDataPayload> type() {
        return TYPE;
    }

    public static void handle(RequestWarChestDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isWarChestEnabled()) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByWorld(payload.colonyId, player.level());
            if (colony == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("War chest: colony not found in this dimension.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            // Null-safe rank check: getRank(uuid) returns null for non-members and would
            // NPE on the main server thread (parity with the investment payload).
            com.minecolonies.api.colony.permissions.Rank playerRank =
                    colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("War chest: you need colony-manager rank in this colony.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            int balance = WarChestManager.getWarChestBalance(payload.colonyId);
            int maxCapacity = WarChestManager.getEffectiveMaxCapacity(payload.colonyId);
            int drainPerMinute = TaxConfig.getWarChestDrainPerMinute();
            int taxBalance = TaxManager.getStoredTaxForColony(colony);
            boolean autoSurrender = TaxConfig.isWarChestAutoSurrenderEnabled();
            double minPercent = TaxConfig.getWarChestMinPercentOfTarget();

            PacketDistributor.sendToPlayer(player, new WarChestDataResponsePayload(
                    payload.colonyId, balance, maxCapacity, drainPerMinute, taxBalance, autoSurrender, minPercent));
        });
    }
}
