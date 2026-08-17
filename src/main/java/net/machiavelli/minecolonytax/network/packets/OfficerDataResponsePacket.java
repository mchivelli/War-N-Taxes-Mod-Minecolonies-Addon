package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;

import java.util.function.Supplier;
import java.util.EnumMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class OfficerDataResponsePacket {
    private final List<OfficerData> officers;
    private final int colonyId;
    /** Colony-wide default policy per action. Server-only state, so the client must be told. */
    private final Map<ColonyPermission, Boolean> colonyDefaults;

    public OfficerDataResponsePacket(List<OfficerData> officers, int colonyId,
                                     Map<ColonyPermission, Boolean> colonyDefaults) {
        this.officers = officers;
        this.colonyId = colonyId;
        this.colonyDefaults = colonyDefaults;
    }

    public OfficerDataResponsePacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();

        int defaultCount = buf.readInt();
        this.colonyDefaults = new EnumMap<>(ColonyPermission.class);
        for (int p = 0; p < defaultCount; p++) {
            boolean allowed = buf.readBoolean();
            ColonyPermission permission = ColonyPermission.byOrdinal(p);
            if (permission != null) colonyDefaults.put(permission, allowed);
        }

        int count = buf.readInt();
        this.officers = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            UUID playerId = buf.readUUID();
            String playerName = buf.readUtf();
            String rank = buf.readUtf();
            boolean isOwner = buf.readBoolean();
            boolean isManager = buf.readBoolean();

            // Permission block: sender-declared length, decoded by ordinal. Entries the
            // receiver doesn't know are skipped rather than throwing.
            int permCount = buf.readInt();
            Map<ColonyPermission, Boolean> effective = new EnumMap<>(ColonyPermission.class);
            Map<ColonyPermission, Boolean> granted = new EnumMap<>(ColonyPermission.class);
            for (int p = 0; p < permCount; p++) {
                boolean canDo = buf.readBoolean();
                boolean isGranted = buf.readBoolean();
                ColonyPermission permission = ColonyPermission.byOrdinal(p);
                if (permission == null) continue;
                effective.put(permission, canDo);
                granted.put(permission, isGranted);
            }

            boolean isOnline = buf.readBoolean();
            long lastSeen = buf.readLong();

            officers.add(new OfficerData(playerId, playerName, rank, isOwner, isManager,
                    effective, granted, isOnline, lastSeen));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);

        ColonyPermission[] allPermissions = ColonyPermission.values();
        buf.writeInt(allPermissions.length);
        for (ColonyPermission permission : allPermissions) {
            Boolean value = colonyDefaults != null ? colonyDefaults.get(permission) : null;
            buf.writeBoolean(value != null ? value : permission.isDefaultAllowed());
        }

        buf.writeInt(officers.size());

        for (OfficerData officer : officers) {
            buf.writeUUID(officer.getPlayerId());
            buf.writeUtf(officer.getPlayerName());
            buf.writeUtf(officer.getRank());
            buf.writeBoolean(officer.isOwner());
            buf.writeBoolean(officer.isManager());

            ColonyPermission[] permissions = ColonyPermission.values();
            buf.writeInt(permissions.length);
            for (ColonyPermission permission : permissions) {
                buf.writeBoolean(officer.can(permission));
                buf.writeBoolean(officer.isGranted(permission));
            }

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
                    screen.updateOfficerData(officers, colonyId, colonyDefaults);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
