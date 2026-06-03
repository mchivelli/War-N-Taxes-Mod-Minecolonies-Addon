package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

            java.util.List<net.machiavelli.minecolonytax.gui.data.OfficerData> officers = new java.util.ArrayList<>();
            int colonyId = payload.colonyId;

            if (colonyId == -1) {
                for (com.minecolonies.api.colony.IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
                    if (colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                        officers.addAll(buildOfficerList(colony, player));
                    }
                }
            } else {
                com.minecolonies.api.colony.IColony colony =
                    com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByWorld(colonyId, player.serverLevel());
                if (colony != null && colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                    officers.addAll(buildOfficerList(colony, player));
                }
            }

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new OfficerDataResponsePayload(officers, colonyId));
        });
    }

    private static java.util.List<net.machiavelli.minecolonytax.gui.data.OfficerData> buildOfficerList(
            com.minecolonies.api.colony.IColony colony, ServerPlayer requestingPlayer) {
        java.util.List<net.machiavelli.minecolonytax.gui.data.OfficerData> result = new java.util.ArrayList<>();
        for (com.minecolonies.api.colony.permissions.ColonyPlayer cp : colony.getPermissions().getPlayers().values()) {
            com.minecolonies.api.colony.permissions.Rank rank = cp.getRank();
            if (rank.getId() <= 1) continue;
            String name = cp.getName();
            if (name == null || name.isEmpty()) name = "Unknown Player";
            boolean canClaimTax = rank.getId() >= 3;
            boolean isOnline = false;
            try {
                net.minecraft.server.MinecraftServer server = colony.getWorld().getServer();
                if (server != null) isOnline = server.getPlayerList().getPlayer(cp.getID()) != null;
            } catch (Exception ignored) {}
            String rankName = switch (rank.getId()) {
                case 5 -> "Owner";
                case 4 -> "Officer";
                case 3 -> "Friend";
                case 2 -> "Neutral";
                case 1 -> "Hostile";
                default -> "Unknown";
            };
            result.add(new net.machiavelli.minecolonytax.gui.data.OfficerData(
                cp.getID(), name, rankName, canClaimTax, isOnline, System.currentTimeMillis()));
        }
        return result;
    }
}
