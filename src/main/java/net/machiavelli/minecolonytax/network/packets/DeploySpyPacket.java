package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DeploySpyPacket {

    private final int targetColonyId;
    private final String missionType;

    public DeploySpyPacket(int targetColonyId, String missionType) {
        this.targetColonyId = targetColonyId;
        this.missionType = missionType;
    }

    public DeploySpyPacket(FriendlyByteBuf buf) {
        this.targetColonyId = buf.readInt();
        this.missionType = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(targetColonyId);
        buf.writeUtf(missionType);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isSpySystemEnabled())
                return;

            // Resolve attacker colony from the player
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getIColonyByOwner(player.level(),
                    player);
            if (colony == null)
                return;

            if (!colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
                player.sendSystemMessage(
                        Component.literal("Only officers can deploy spies.").withStyle(ChatFormatting.RED));
                return;
            }

            SpyManager.deploySpyMission(player, colony.getID(), targetColonyId, missionType);

            // Trigger a refresh after deploying so UI updates
            net.machiavelli.minecolonytax.network.NetworkHandler.sendToPlayer(player, new RequestSpyDataPacket());
        });
        ctx.get().setPacketHandled(true);
    }
}
