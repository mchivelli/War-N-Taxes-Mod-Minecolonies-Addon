package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.machiavelli.minecolonytax.gui.data.OfficerData;

import java.util.function.Supplier;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class OfficerDataResponsePacket {
    private final List<OfficerData> officers;
    private final int colonyId;

    public OfficerDataResponsePacket(List<OfficerData> officers, int colonyId) {
        this.officers = officers;
        this.colonyId = colonyId;
    }

    public OfficerDataResponsePacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        int count = buf.readInt();
        this.officers = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            UUID playerId = buf.readUUID();
            String playerName = buf.readUtf();
            String rank = buf.readUtf();
            boolean canClaimTax = buf.readBoolean();
            boolean isOnline = buf.readBoolean();
            long lastSeen = buf.readLong();
            
            officers.add(new OfficerData(playerId, playerName, rank, canClaimTax, isOnline, lastSeen));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(officers.size());
        
        for (OfficerData officer : officers) {
            buf.writeUUID(officer.getPlayerId());
            buf.writeUtf(officer.getPlayerName());
            buf.writeUtf(officer.getRank());
            buf.writeBoolean(officer.canClaimTax());
            buf.writeBoolean(officer.isOnline());
            buf.writeLong(officer.getLastSeen());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Update the GUI with officer data — wrap to avoid class-loading client-only
            // classes (Minecraft, TaxManagementScreen) on a dedicated server.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen) {
                    net.machiavelli.minecolonytax.gui.TaxManagementScreen screen =
                            (net.machiavelli.minecolonytax.gui.TaxManagementScreen) mc.screen;
                    screen.updateOfficerData(officers, colonyId);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
