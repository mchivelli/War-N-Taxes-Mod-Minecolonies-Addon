package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;

import java.util.function.Supplier;

/** Sets the colony-wide default for one {@link ColonyPermission}. Owner/manage-huts only. */
public class UpdateTaxPermissionPacket {
    private final int colonyId;
    private final ColonyPermission permission;
    private final boolean allowOfficers;

    public UpdateTaxPermissionPacket(int colonyId, ColonyPermission permission, boolean allowOfficers) {
        this.colonyId = colonyId;
        this.permission = permission;
        this.allowOfficers = allowOfficers;
    }

    public UpdateTaxPermissionPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        // Bounds-checked: an attacker-supplied ordinal must not index out of the enum.
        this.permission = ColonyPermission.byOrdinal(buf.readInt());
        this.allowOfficers = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(permission != null ? permission.ordinal() : -1);
        buf.writeBoolean(allowOfficers);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (permission == null) return; // malformed ordinal

            // Verify player owns the colony. Resolved via ColonyLookup because
            // getColonyByWorld(id, level) only sees colonies registered in the level the player
            // currently stands in, so toggling from another dimension failed with "Colony not found!".
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
            if (colony == null) {
                player.sendSystemMessage(Component.literal("§cColony not found!"));
                return;
            }

            IPermissions permissions = colony.getPermissions();
            if (!permissions.hasPermission(player, com.minecolonies.api.colony.permissions.Action.MANAGE_HUTS)) {
                player.sendSystemMessage(
                        Component.literal("§cYou must be the colony owner to change permissions!"));
                return;
            }

            TaxPermissionManager.setColonyDefault(colonyId, permission, allowOfficers);

            String status = allowOfficers ? "allowed" : "blocked";
            player.sendSystemMessage(Component.literal("§aOfficers are now " + status + " from '"
                    + permission.getDisplayName() + "' for " + colony.getName()));
        });
        ctx.get().setPacketHandled(true);
    }
}
