package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.server.MinecraftServer;
import java.util.function.Supplier;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class RequestOfficerDataPacket {
    private final int colonyId;

    public RequestOfficerDataPacket() {
        this.colonyId = -1; // Request all colonies
    }
    
    public RequestOfficerDataPacket(int colonyId) {
        this.colonyId = colonyId;
    }

    public RequestOfficerDataPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                List<OfficerData> officers = new ArrayList<>();
                
                if (colonyId == -1) {
                    // Get officers from all colonies player has access to
                    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                        if (hasColonyAccess(player, colony)) {
                            officers.addAll(getColonyOfficers(colony));
                        }
                    }
                } else {
                    // Get officers from specific colony
                    IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, player.serverLevel());
                    if (colony != null && hasColonyAccess(player, colony)) {
                        officers.addAll(getColonyOfficers(colony));
                    }
                }
                
                // Send response back to client
                NetworkHandler.sendToPlayer(player, new OfficerDataResponsePacket(officers, colonyId));
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    private boolean hasColonyAccess(ServerPlayer player, IColony colony) {
        IPermissions permissions = colony.getPermissions();
        return permissions.hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS);
    }
    
    private List<OfficerData> getColonyOfficers(IColony colony) {
        List<OfficerData> officers = new ArrayList<>();
        IPermissions permissions = colony.getPermissions();
        
        // Get all players with permissions
        for (ColonyPlayer colonyPlayer : permissions.getPlayers().values()) {
            UUID playerId = colonyPlayer.getID();
            Rank rank = colonyPlayer.getRank();
            
            // Skip neutral and hostile ranks
            if (rank.getId() <= 1) {
                continue;
            }
            
            String playerName = colonyPlayer.getName();
            if (playerName == null || playerName.isEmpty()) {
                playerName = "Unknown Player";
            }
            
            boolean canClaimTax = rank.getId() >= 3; // Officers (rank 3) and above can claim
            boolean isOnline = false;
            long lastSeen = System.currentTimeMillis(); // Use current time as default
            
            // Safely check if player is online
            try {
                MinecraftServer server = colony.getWorld().getServer();
                if (server != null) {
                    isOnline = server.getPlayerList().getPlayer(playerId) != null;
                }
            } catch (Exception e) {
                // If we can't determine online status, default to offline
                isOnline = false;
            }
            
            officers.add(new OfficerData(
                playerId,
                playerName,
                getRankName(rank.getId()),
                canClaimTax,
                isOnline,
                lastSeen
            ));
        }
        
        return officers;
    }
    
    private String getRankName(int rankId) {
        switch (rankId) {
            case 5: return "Owner";
            case 4: return "Officer";
            case 3: return "Friend";
            case 2: return "Neutral";
            case 1: return "Hostile";
            default: return "Unknown";
        }
    }
}
