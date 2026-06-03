package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
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

            // Resolve attacker colony: any colony in this dimension where the player has manager rank.
            // getIColonyByOwner only matches the registered OWNER, which broke officer deployments
            // (the downstream isColonyManager check was unreachable). See audit/defensive_03_espionage.md C1.
            IColony colony = null;
            for (IColony c : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
                if (!c.getDimension().equals(player.level().dimension())) continue;
                Rank rank = c.getPermissions().getRank(player.getUUID());
                if (rank != null && rank.isColonyManager()) {
                    colony = c;
                    break;
                }
            }
            if (colony == null) {
                player.sendSystemMessage(
                        Component.literal("Only officers can deploy spies.").withStyle(ChatFormatting.RED));
                return;
            }

            // Defense-in-depth: null-guard the rank deref even though the loop above already filtered.
            // See audit/CODEX_INDEPENDENT.md HIGH-6 / adversary_C_crashes.md CRASH-5.
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(
                        Component.literal("Only officers can deploy spies.").withStyle(ChatFormatting.RED));
                return;
            }

            SpyManager.deploySpyMission(player, colony.getID(), targetColonyId, missionType);

            // Trigger a refresh after deploying so UI updates
            String playerId = player.getUUID().toString();
            java.util.List<SpyMission> missions = SpyManager.getMissionsForPlayer(playerId);
            net.machiavelli.minecolonytax.network.NetworkHandler.sendToPlayer(player,
                    new SpyDataResponsePacket(missions));
        });
        ctx.get().setPacketHandled(true);
    }
}
