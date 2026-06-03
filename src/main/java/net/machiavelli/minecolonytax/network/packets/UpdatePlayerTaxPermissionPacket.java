package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
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
    private final boolean allowed;

    public UpdatePlayerTaxPermissionPacket(int colonyId, UUID playerId, boolean allowed) {
        this.colonyId = colonyId;
        this.playerId = playerId;
        this.allowed = allowed;
    }

    public UpdatePlayerTaxPermissionPacket(FriendlyByteBuf buffer) {
        this.colonyId = buffer.readInt();
        this.playerId = buffer.readUUID();
        this.allowed = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(colonyId);
        buffer.writeUUID(playerId);
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

            try {
                // SECURITY: resolve the colony server-side and require the sender to be a colony manager
                // (owner or officer) of that colony. Previously this packet had ZERO authorization, allowing any
                // online player to grant/revoke tax-claim permission on any colony for any UUID.
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                IColony colony = colonyManager.getAllColonies().stream()
                        .filter(c -> c.getID() == colonyId)
                        .findFirst()
                        .orElse(null);

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

                TaxPermissionManager.setPlayerClaimPermission(colonyId, playerId, allowed);

                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.debug("Player {} updated tax claim permission for player {} in colony {} to: {}",
                            player.getGameProfile().getName(), playerId, colonyId, allowed);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling UpdatePlayerTaxPermissionPacket from player {}: {}",
                           player.getGameProfile().getName(), e.getMessage(), e);
            }
        });
        context.setPacketHandled(true);
    }
}
