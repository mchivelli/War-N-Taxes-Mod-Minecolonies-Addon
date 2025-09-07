package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;

import java.util.function.Supplier;

public class UpdateTaxPermissionPacket {
    private final int colonyId;
    private final boolean allowOfficers;

    public UpdateTaxPermissionPacket(int colonyId, boolean allowOfficers) {
        this.colonyId = colonyId;
        this.allowOfficers = allowOfficers;
    }

    public UpdateTaxPermissionPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.allowOfficers = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeBoolean(allowOfficers);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Verify player owns the colony
                IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, player.serverLevel());
                if (colony != null) {
                    IPermissions permissions = colony.getPermissions();
                    if (permissions.hasPermission(player, com.minecolonies.api.colony.permissions.Action.MANAGE_HUTS)) {
                        // Update tax permission
                        TaxPermissionManager.setOfficerClaimPermission(colonyId, allowOfficers);
                        
                        String status = allowOfficers ? "allowed" : "blocked";
                        player.sendSystemMessage(Component.literal("§aTax claiming permissions updated! Officers are now " + status + " from claiming taxes for " + colony.getName()));
                    } else {
                        player.sendSystemMessage(Component.literal("§cYou must be the colony owner to change tax permissions!"));
                    }
                } else {
                    player.sendSystemMessage(Component.literal("§cColony not found!"));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
