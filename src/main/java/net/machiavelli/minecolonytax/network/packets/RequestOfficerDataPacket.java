package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.server.MinecraftServer;
import java.util.function.Supplier;
import java.util.EnumMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
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
                Map<ColonyPermission, Boolean> colonyDefaults = new EnumMap<>(ColonyPermission.class);

                if (colonyId == -1) {
                    // Get officers from all colonies player has access to
                    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                        if (colony != null && hasColonyAccess(player, colony)) {
                            officers.addAll(getColonyOfficers(colony));
                        }
                    }
                } else {
                    // Resolved via ColonyLookup: getColonyByWorld(id, level) reads only the
                    // player's CURRENT level, so someone standing in the Nether got null for their
                    // overworld colony and the tab sat on "Loading..." forever.
                    IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
                    if (colony != null && hasColonyAccess(player, colony)) {
                        officers.addAll(getColonyOfficers(colony));
                        for (ColonyPermission permission : ColonyPermission.values()) {
                            colonyDefaults.put(permission,
                                    TaxPermissionManager.getColonyDefault(colonyId, permission));
                        }
                    }
                }

                // Send response back to client
                NetworkHandler.sendToPlayer(player,
                        new OfficerDataResponsePacket(officers, colonyId, colonyDefaults));
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
        int id = colony.getID();

        // MineColonies rank ids are OWNER=0, OFFICER=1, FRIEND=2, NEUTRAL=3, HOSTILE=4, with
        // custom ranks numbered from 5 up. The previous code assumed the exact opposite order,
        // so it skipped everyone with id <= 1 — the owner and the officers, i.e. precisely the
        // players this tab exists to show — while listing neutral/hostile players under the
        // "Officer"/"Friend" labels. Compare against the named rank accessors instead; that is
        // what the rest of the mod (ClaimTaxPacket, WarSystem, BesiegeManager) already does and
        // it keeps working for custom ranks.
        Rank rankOwner = permissions.getRankOwner();
        Rank rankOfficer = permissions.getRankOfficer();
        Rank rankNeutral = permissions.getRankNeutral();
        UUID ownerId = permissions.getOwner();

        for (ColonyPlayer colonyPlayer : permissions.getPlayers().values()) {
            UUID playerId = colonyPlayer.getID();
            Rank rank = colonyPlayer.getRank();
            if (playerId == null || rank == null) {
                continue;
            }

            // Skip neutral and hostile ranks (isHostile() also covers custom hostile ranks).
            if (rank.equals(rankNeutral) || rank.isHostile()) {
                continue;
            }

            String playerName = colonyPlayer.getName();
            if (playerName == null || playerName.isEmpty()) {
                playerName = "Unknown Player";
            }

            boolean isOwner = rank.equals(rankOwner) || playerId.equals(ownerId);
            boolean isOfficer = rank.equals(rankOfficer) || isOwner;
            boolean isManager = rank.isColonyManager() || isOwner;

            // Mirror the server-side gates exactly, so the tab cannot promise an action the
            // server would refuse. Non-managers fail every gate, matching the packets.
            Map<ColonyPermission, Boolean> effective = new EnumMap<>(ColonyPermission.class);
            Map<ColonyPermission, Boolean> granted = new EnumMap<>(ColonyPermission.class);
            for (ColonyPermission permission : ColonyPermission.values()) {
                effective.put(permission, isManager
                        && TaxPermissionManager.can(id, playerId, permission, isOwner, isOfficer));
                granted.put(permission, isOwner || TaxPermissionManager.isGranted(id, playerId, permission));
            }

            boolean isOnline = false;
            try {
                MinecraftServer server = colony.getWorld().getServer();
                if (server != null) {
                    isOnline = server.getPlayerList().getPlayer(playerId) != null;
                }
            } catch (Exception e) {
                // If we can't determine online status, default to offline
                isOnline = false;
            }

            // No per-player last-seen source exists (OfficerColonyVisitTracker is per colony,
            // not per player). Send the honest "unknown" sentinel instead of the request
            // timestamp, which made every offline officer read "Just now".
            long lastSeen = isOnline ? System.currentTimeMillis() : OfficerData.LAST_SEEN_UNKNOWN;

            officers.add(new OfficerData(
                playerId,
                playerName,
                rank.getName(),
                isOwner,
                isManager,
                effective,
                granted,
                isOnline,
                lastSeen
            ));
        }

        // Owner first, then officers/managers, then the rest — alphabetical within each group.
        officers.sort((a, b) -> {
            if (a.isOwner() != b.isOwner()) return a.isOwner() ? -1 : 1;
            if (a.isManager() != b.isManager()) return a.isManager() ? -1 : 1;
            return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
        });

        return officers;
    }
}
