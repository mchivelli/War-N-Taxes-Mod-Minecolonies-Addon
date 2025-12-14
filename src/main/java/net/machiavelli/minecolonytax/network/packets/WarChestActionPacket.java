package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-Server packet for War Chest deposit/withdraw actions.
 */
public class WarChestActionPacket {

    public enum ActionType {
        DEPOSIT,
        WITHDRAW
    }

    private final int colonyId;
    private final ActionType action;
    private final int amount;

    public WarChestActionPacket(int colonyId, ActionType action, int amount) {
        this.colonyId = colonyId;
        this.action = action;
        this.amount = amount;
    }

    public WarChestActionPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.action = ActionType.values()[buf.readInt()];
        this.amount = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(action.ordinal());
        buf.writeInt(amount);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByWorld(colonyId, player.level());

            if (colony == null)
                return;

            // Check if player has access (owner or officer)
            boolean hasAccess = colony.getPermissions().getRank(player.getUUID()).isColonyManager();
            if (!hasAccess)
                return;

            // Perform action
            switch (action) {
                case DEPOSIT -> WarChestManager.deposit(player, colonyId, amount);
                case WITHDRAW -> WarChestManager.withdraw(player, colonyId, amount);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
