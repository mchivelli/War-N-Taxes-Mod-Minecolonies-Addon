package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.machiavelli.minecolonytax.util.ColonyLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sets one player's override for one {@link ColonyPermission}. Colony managers only. */
public record UpdatePlayerTaxPermissionPayload(int colonyId, java.util.UUID playerId,
                                               int permissionOrdinal, boolean allowed)
        implements CustomPacketPayload {

    public static final Type<UpdatePlayerTaxPermissionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "update_player_tax_permission"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePlayerTaxPermissionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, UpdatePlayerTaxPermissionPayload::colonyId,
            net.minecraft.network.codec.ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.STRING.xmap(java.util.UUID::fromString, java.util.UUID::toString)), UpdatePlayerTaxPermissionPayload::playerId,
            ByteBufCodecs.INT, UpdatePlayerTaxPermissionPayload::permissionOrdinal,
            ByteBufCodecs.BOOL, UpdatePlayerTaxPermissionPayload::allowed,
            UpdatePlayerTaxPermissionPayload::new
        );

    public UpdatePlayerTaxPermissionPayload(int colonyId, java.util.UUID playerId,
                                            ColonyPermission permission, boolean allowed) {
        this(colonyId, playerId, permission != null ? permission.ordinal() : -1, allowed);
    }

    @Override
    public Type<UpdatePlayerTaxPermissionPayload> type() {
        return TYPE;
    }

    public static void handle(UpdatePlayerTaxPermissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Bounds-checked: an attacker-supplied ordinal must not index out of the enum.
            ColonyPermission permission = ColonyPermission.byOrdinal(payload.permissionOrdinal);
            if (permission == null) {
                MineColonyTax.LOGGER.warn("UpdatePlayerTaxPermissionPayload from {} carried an unknown permission ordinal {}",
                        player.getGameProfile().getName(), payload.permissionOrdinal);
                return;
            }

            // SECURITY: this handler previously only wrote a debug log line — the toggle changed
            // nothing, and nothing verified the sender. Resolve the colony server-side and require
            // the sender to be a colony manager of it, otherwise any online player could grant or
            // revoke permissions on any colony for any UUID.
            IColony colony = ColonyLookup.byId(payload.colonyId, player);
            if (colony == null) {
                player.sendSystemMessage(Component.literal("§cColony not found!"));
                return;
            }

            Rank senderRank = colony.getPermissions().getRank(player.getUUID());
            if (senderRank == null || !senderRank.isColonyManager()) {
                MineColonyTax.LOGGER.warn("UpdatePlayerTaxPermissionPayload REJECTED: player {} is not a colony manager of colony {}",
                        player.getGameProfile().getName(), payload.colonyId);
                player.sendSystemMessage(Component.literal(
                        "§cYou don't have permission to manage permissions for this colony!"));
                return;
            }

            TaxPermissionManager.setPlayerPermission(payload.colonyId, payload.playerId,
                    permission, payload.allowed);
        });
    }
}
