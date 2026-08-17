package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

public class UpdatePlayerTaxPermissionPacket {
    private static final Logger LOGGER = LogManager.getLogger(UpdatePlayerTaxPermissionPacket.class);
    
    private final int colonyId;
    private final UUID playerId;
    private final ColonyPermission permission;
    private final boolean allowed;

    public UpdatePlayerTaxPermissionPacket(int colonyId, UUID playerId,
                                           ColonyPermission permission, boolean allowed) {
        this.colonyId = colonyId;
        this.playerId = playerId;
        this.permission = permission;
        this.allowed = allowed;
    }

    public UpdatePlayerTaxPermissionPacket(FriendlyByteBuf buffer) {
        this.colonyId = buffer.readInt();
        this.playerId = buffer.readUUID();
        // Bounds-checked: an attacker-supplied ordinal must not index out of the enum.
        this.permission = ColonyPermission.byOrdinal(buffer.readInt());
        this.allowed = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(colonyId);
        buffer.writeUUID(playerId);
        buffer.writeInt(permission != null ? permission.ordinal() : -1);
        buffer.writeBoolean(allowed);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                LOGGER.warn("UpdatePlayerTaxPermissionPacket received with null sender");
                return;
            }
            if (permission == null) {
                LOGGER.warn("UpdatePlayerTaxPermissionPacket from {} carried an unknown permission ordinal",
                        player.getGameProfile().getName());
                return;
            }

            try {
                // SECURITY: resolve the colony server-side and require the sender to be a colony manager
                // (owner or officer) of that colony. Previously this packet had ZERO authorization, allowing any
                // online player to grant/revoke tax-claim permission on any colony for any UUID.
                // Colony ids are only unique per dimension, so a bare getAllColonies() filter
                // could resolve a same-id colony from another dimension.
                IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);

                if (colony == null) {
                    LOGGER.warn("UpdatePlayerTaxPermissionPacket from {} for unknown colony id {}",
                            player.getGameProfile().getName(), colonyId);
                    player.sendSystemMessage(Component.literal("Colony not found!"));
                    return;
                }

                Rank senderRank = colony.getPermissions().getRank(player.getUUID());
                if (senderRank == null || !senderRank.isColonyManager()) {
                    LOGGER.warn("UpdatePlayerTaxPermissionPacket REJECTED: player {} is not a colony manager of colony {}",
                            player.getGameProfile().getName(), colonyId);
                    player.sendSystemMessage(Component.literal("You don't have permission to manage tax permissions for this colony!"));
                    return;
                }

                TaxPermissionManager.setPlayerPermission(colonyId, playerId, permission, allowed);

                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.debug("Player {} updated {} for player {} in colony {} to: {}",
                            player.getGameProfile().getName(), permission, playerId, colonyId, allowed);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling UpdatePlayerTaxPermissionPacket from player {}: {}",
                           player.getGameProfile().getName(), e.getMessage(), e);
            }
        });
        context.setPacketHandled(true);
    }
}
