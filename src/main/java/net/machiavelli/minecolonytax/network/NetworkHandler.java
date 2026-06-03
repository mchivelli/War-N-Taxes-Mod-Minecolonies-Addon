package net.machiavelli.minecolonytax.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Thin compatibility shim so GUI and server-side code can call
 * NetworkHandler.sendToServer / sendToPlayer without importing PacketDistributor directly.
 * All actual packet registration happens in ModNetworking.
 */
public class NetworkHandler {

    /** Send a payload from client to server. */
    public static <T extends CustomPacketPayload> void sendToServer(T payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Send a payload from server to a specific player. */
    public static <T extends CustomPacketPayload> void sendToPlayer(ServerPlayer player, T payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
