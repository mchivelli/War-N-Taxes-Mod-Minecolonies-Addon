package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class RecallSpyPacket {

    private final String missionId;

    public RecallSpyPacket(String missionId) {
        this.missionId = missionId;
    }

    public RecallSpyPacket(FriendlyByteBuf buf) {
        this.missionId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(missionId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isSpySystemEnabled())
                return;

            String playerId = player.getUUID().toString();
            List<SpyMission> missions = SpyManager.getActiveMissionsForPlayer(playerId);

            boolean ownsMission = missions.stream().anyMatch(m -> m.getMissionId().equals(missionId));
            if (ownsMission) {
                SpyManager.recallSpy(missionId);
                // Trigger a UI refresh
                net.machiavelli.minecolonytax.network.NetworkHandler.sendToPlayer(player, new RequestSpyDataPacket());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
