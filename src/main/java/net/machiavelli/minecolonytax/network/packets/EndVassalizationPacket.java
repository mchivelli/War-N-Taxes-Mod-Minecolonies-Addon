package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to end vassalization
 */
public class EndVassalizationPacket {
    private final int colonyId;
    
    public EndVassalizationPacket(int colonyId) {
        this.colonyId = colonyId;
    }
    
    public EndVassalizationPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            
            // Find the colony
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
                
            if (colony == null) {
                player.sendSystemMessage(Component.literal("Colony not found!"));
                return;
            }
            
            // Check if player has permission to end vassalization
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(Component.literal("You don't have permission to end vassalization for this colony!"));
                return;
            }
            
            // Check if colony is actually a vassal
            if (!VassalManager.isColonyVassal(colonyId)) {
                player.sendSystemMessage(Component.literal("This colony is not a vassal!"));
                return;
            }
            
            // End the vassalization with notification to overlord
            boolean success = VassalManager.endVassalizationWithNotification(colonyId, player, colony);
            
            if (success) {
                player.sendSystemMessage(Component.literal("§aVassalization ended for " + colony.getName()));
                player.sendSystemMessage(Component.literal("§eYour overlord has been notified of this decision."));
            } else {
                player.sendSystemMessage(Component.literal("§cFailed to end vassalization!"));
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
