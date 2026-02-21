package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class RequestSpyDataPacket {

    public RequestSpyDataPacket() {
    }

    public RequestSpyDataPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isSpySystemEnabled())
                return;

            String playerId = player.getUUID().toString();
            List<SpyMission> missions = SpyManager.getActiveMissionsForPlayer(playerId);

            // Gather fresh intel for each target
            for (SpyMission mission : missions) {
                SpyManager.gatherIntel(mission.getTargetColonyId());
            }

            SpyDataResponsePacket response = new SpyDataResponsePacket(missions);
            net.machiavelli.minecolonytax.network.NetworkHandler.sendToPlayer(player, response);
        });
        ctx.get().setPacketHandled(true);
    }
}
