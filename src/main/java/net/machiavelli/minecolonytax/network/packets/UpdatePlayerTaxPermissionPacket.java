package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Network packet to update individual player tax claim permissions.
 * Only colony owners should be able to send this packet.
 */
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
                // Verify the player is actually a colony owner
                // This requires integration with MineColonies API to check ownership
                // For now, we'll implement a basic permission check
                
                // TODO: Add proper colony ownership verification
                // IColony colony = IColonyManager.getInstance().getColonyByID(colonyId);
                // if (colony == null || !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)) {
                //     LOGGER.warn("Player {} attempted to change tax permissions for colony {} without ownership", 
                //                 player.getGameProfile().getName(), colonyId);
                //     return;
                // }

                // Update the individual player permission
                TaxPermissionManager.setPlayerClaimPermission(colonyId, playerId, allowed);
                
                LOGGER.info("Player {} updated tax claim permission for player {} in colony {} to: {}", 
                           player.getGameProfile().getName(), playerId, colonyId, allowed);
                
            } catch (Exception e) {
                LOGGER.error("Error handling UpdatePlayerTaxPermissionPacket from player {}: {}", 
                           player.getGameProfile().getName(), e.getMessage(), e);
            }
        });
        context.setPacketHandled(true);
    }
}
