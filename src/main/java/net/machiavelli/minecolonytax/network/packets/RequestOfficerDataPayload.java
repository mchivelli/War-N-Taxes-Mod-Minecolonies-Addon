package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.machiavelli.minecolonytax.util.ColonyLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RequestOfficerDataPayload(int colonyId) implements CustomPacketPayload {

    public static final Type<RequestOfficerDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "request_officer_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestOfficerDataPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, RequestOfficerDataPayload::colonyId,
            RequestOfficerDataPayload::new
        );

    @Override
    public Type<RequestOfficerDataPayload> type() {
        return TYPE;
    }

    public static void handle(RequestOfficerDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            List<OfficerData> officers = new ArrayList<>();
            Map<ColonyPermission, Boolean> colonyDefaults = new EnumMap<>(ColonyPermission.class);
            int colonyId = payload.colonyId;

            if (colonyId == -1) {
                for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                    if (colony != null && hasColonyAccess(player, colony)) {
                        officers.addAll(buildOfficerList(colony));
                    }
                }
            } else {
                // Resolved via ColonyLookup: getColonyByWorld(id, level) reads only the player's
                // CURRENT level, so someone standing in the Nether got null for their overworld
                // colony and the tab sat on "Loading..." forever.
                IColony colony = ColonyLookup.byId(colonyId, player);
                if (colony != null && hasColonyAccess(player, colony)) {
                    officers.addAll(buildOfficerList(colony));
                    for (ColonyPermission permission : ColonyPermission.values()) {
                        colonyDefaults.put(permission,
                                TaxPermissionManager.getColonyDefault(colonyId, permission));
                    }
                }
            }

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new OfficerDataResponsePayload(officers, colonyId, colonyDefaults));
        });
    }

    private static boolean hasColonyAccess(ServerPlayer player, IColony colony) {
        return colony.getPermissions().hasPermission(player,
                com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS);
    }

    private static List<OfficerData> buildOfficerList(IColony colony) {
        List<OfficerData> result = new ArrayList<>();
        IPermissions permissions = colony.getPermissions();
        int id = colony.getID();

        // MineColonies rank ids are OWNER=0, OFFICER=1, FRIEND=2, NEUTRAL=3, HOSTILE=4, with
        // custom ranks numbered from 5 up. The previous code assumed the exact opposite order,
        // so it skipped everyone with id <= 1 — the owner and the officers, i.e. precisely the
        // players this tab exists to show — while listing neutral/hostile players under the
        // "Officer"/"Friend" labels. Compare against the named rank accessors instead; that is
        // what the rest of the mod already does and it keeps working for custom ranks.
        Rank rankOwner = permissions.getRankOwner();
        Rank rankOfficer = permissions.getRankOfficer();
        Rank rankNeutral = permissions.getRankNeutral();
        UUID ownerId = permissions.getOwner();

        for (ColonyPlayer colonyPlayer : permissions.getPlayers().values()) {
            UUID playerId = colonyPlayer.getID();
            Rank rank = colonyPlayer.getRank();
            if (playerId == null || rank == null) continue;

            // Skip neutral and hostile ranks (isHostile() also covers custom hostile ranks).
            if (rank.equals(rankNeutral) || rank.isHostile()) continue;

            String name = colonyPlayer.getName();
            if (name == null || name.isEmpty()) name = "Unknown Player";

            boolean isOwner = rank.equals(rankOwner) || playerId.equals(ownerId);
            boolean isOfficer = rank.equals(rankOfficer) || isOwner;
            boolean isManager = rank.isColonyManager() || isOwner;

            // Mirror the server-side gates exactly, so the tab cannot promise an action the
            // server would refuse. Non-managers fail every gate, matching the payload handlers.
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
                if (server != null) isOnline = server.getPlayerList().getPlayer(playerId) != null;
            } catch (Exception ignored) {
                isOnline = false;
            }

            // No per-player last-seen source exists, so send the honest "unknown" sentinel
            // instead of the request timestamp, which made every offline officer read "Just now".
            long lastSeen = isOnline ? System.currentTimeMillis() : OfficerData.LAST_SEEN_UNKNOWN;

            result.add(new OfficerData(playerId, name, rank.getName(),
                    isOwner, isManager, effective, granted, isOnline, lastSeen));
        }

        // Owner first, then officers/managers, then the rest — alphabetical within each group.
        result.sort((a, b) -> {
            if (a.isOwner() != b.isOwner()) return a.isOwner() ? -1 : 1;
            if (a.isManager() != b.isManager()) return a.isManager() ? -1 : 1;
            return a.getPlayerName().compareToIgnoreCase(b.getPlayerName());
        });

        return result;
    }
}
