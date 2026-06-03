package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload for War Chest deposit/withdraw actions.
 */
public record WarChestActionPayload(int colonyId, ActionType action, int amount) implements CustomPacketPayload {

    public enum ActionType {
        DEPOSIT,
        WITHDRAW
    }

    public static final Type<WarChestActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "war_chest_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarChestActionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            WarChestActionPayload::colonyId,
            ByteBufCodecs.INT.map(i -> ActionType.values()[i], ActionType::ordinal),
            WarChestActionPayload::action,
            ByteBufCodecs.INT,
            WarChestActionPayload::amount,
            WarChestActionPayload::new
        );

    @Override
    public Type<WarChestActionPayload> type() {
        return TYPE;
    }

    public static void handle(WarChestActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByWorld(payload.colonyId, player.level());
            if (colony == null) return;

            boolean hasAccess = colony.getPermissions().getRank(player.getUUID()).isColonyManager();
            if (!hasAccess) return;

            switch (payload.action) {
                case DEPOSIT  -> WarChestManager.deposit(player, payload.colonyId, payload.amount);
                case WITHDRAW -> WarChestManager.withdraw(player, payload.colonyId, payload.amount);
            }
        });
    }
}
