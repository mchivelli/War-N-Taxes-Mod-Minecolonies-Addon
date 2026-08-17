package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
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

/** Sets the colony-wide default for one {@link ColonyPermission}. Owner/manage-huts only. */
public record UpdateTaxPermissionPayload(int colonyId, int permissionOrdinal, boolean allowOfficers)
        implements CustomPacketPayload {

    public static final Type<UpdateTaxPermissionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "update_tax_permission"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTaxPermissionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, UpdateTaxPermissionPayload::colonyId,
            ByteBufCodecs.INT, UpdateTaxPermissionPayload::permissionOrdinal,
            ByteBufCodecs.BOOL, UpdateTaxPermissionPayload::allowOfficers,
            UpdateTaxPermissionPayload::new
        );

    public UpdateTaxPermissionPayload(int colonyId, ColonyPermission permission, boolean allowOfficers) {
        this(colonyId, permission != null ? permission.ordinal() : -1, allowOfficers);
    }

    @Override
    public Type<UpdateTaxPermissionPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateTaxPermissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Bounds-checked: an attacker-supplied ordinal must not index out of the enum.
            ColonyPermission permission = ColonyPermission.byOrdinal(payload.permissionOrdinal);
            if (permission == null) {
                MineColonyTax.LOGGER.warn("UpdateTaxPermissionPayload from {} carried an unknown permission ordinal {}",
                        player.getGameProfile().getName(), payload.permissionOrdinal);
                return;
            }

            // This handler previously only wrote a debug log line: the toggle changed nothing at
            // all, and there was no authorization check either.
            IColony colony = ColonyLookup.byId(payload.colonyId, player);
            if (colony == null) {
                player.sendSystemMessage(Component.literal("§cColony not found!"));
                return;
            }

            if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)) {
                player.sendSystemMessage(
                        Component.literal("§cYou must be the colony owner to change permissions!"));
                return;
            }

            TaxPermissionManager.setColonyDefault(payload.colonyId, permission, payload.allowOfficers);

            String status = payload.allowOfficers ? "allowed" : "blocked";
            player.sendSystemMessage(Component.literal("§aOfficers are now " + status + " from '"
                    + permission.getDisplayName() + "' for " + colony.getName()));
        });
    }
}
