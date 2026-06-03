package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sent client → server when a player clicks "Dismiss" on a completed spy mission entry.
 * Removes the mission from COMPLETED_MISSIONS so it no longer appears in the GUI.
 */
public class DismissSpyMissionPacket {

    private final String missionId;

    public DismissSpyMissionPacket(String missionId) {
        this.missionId = missionId;
    }

    public DismissSpyMissionPacket(FriendlyByteBuf buf) {
        this.missionId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(missionId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isSpySystemEnabled()) return;

            String playerId = player.getUUID().toString();
            SpyManager.dismissMission(missionId, playerId);

            // Push the updated (shorter) mission list back to the client
            List<SpyMission> updated = SpyManager.getMissionsForPlayer(playerId);
            net.machiavelli.minecolonytax.network.NetworkHandler.sendToPlayer(player,
                    new SpyDataResponsePacket(updated));
        });
        ctx.get().setPacketHandled(true);
    }
}
