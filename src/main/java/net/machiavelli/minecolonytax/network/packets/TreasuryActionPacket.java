package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.integration.CurrencyService;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-Server packet for treasury deposit/withdraw actions.
 * Carries the currency source/destination so the server routes funds correctly.
 */
public class TreasuryActionPacket {

    public enum ActionType {
        DEPOSIT,
        WITHDRAW
    }

    private final int colonyId;
    private final ActionType action;
    private final int amount;
    private final CurrencyService.Source source;

    public TreasuryActionPacket(int colonyId, ActionType action, int amount, CurrencyService.Source source) {
        this.colonyId = colonyId;
        this.action = action;
        this.amount = amount;
        this.source = source;
    }

    public TreasuryActionPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        // Bounds-check the ActionType ordinal — an attacker-supplied out-of-range int
        // would otherwise throw ArrayIndexOutOfBoundsException during decode on the netty
        // thread. Null sentinel is filtered out in handle().
        int actionOrd = buf.readInt();
        ActionType[] actions = ActionType.values();
        this.action = (actionOrd >= 0 && actionOrd < actions.length) ? actions[actionOrd] : null;
        this.amount = buf.readInt();
        int sourceOrdinal = buf.readInt();
        CurrencyService.Source[] sources = CurrencyService.Source.values();
        this.source = sourceOrdinal >= 0 && sourceOrdinal < sources.length
                ? sources[sourceOrdinal]
                : CurrencyService.Source.TAX_BALANCE;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        // action is never null on the encoding side (constructor parameter); decoding
        // can produce null for a malformed ordinal — handled in handle().
        buf.writeInt(action != null ? action.ordinal() : -1);
        buf.writeInt(amount);
        buf.writeInt(source.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Reject malformed packets early — decoder leaves `action == null`
            // when the wire ordinal was out of range.
            if (action == null) return;

            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Resolved via ColonyLookup rather than getColonyByWorld(id, level), which only sees
            // colonies registered in the level the player currently stands in.
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
            if (colony == null) return;

            // Null-safe rank check: getRank(uuid) returns null for non-members and would
            // NPE on the main server thread.
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) return;

            if (action == ActionType.WITHDRAW) {
                boolean isOwner = playerRank.equals(colony.getPermissions().getRankOwner());
                boolean isOfficer = playerRank.equals(colony.getPermissions().getRankOfficer()) || isOwner;
                if (!TaxPermissionManager.can(colonyId, player.getUUID(),
                        ColonyPermission.WITHDRAW_FUNDS, isOwner, isOfficer)) {
                    player.sendSystemMessage(Component.literal(
                            "§cYou do not have permission to withdraw from this colony's treasury."));
                    return;
                }
            }

            switch (action) {
                case DEPOSIT  -> TreasuryManager.deposit(player, colonyId, amount, source);
                case WITHDRAW -> TreasuryManager.withdraw(player, colonyId, amount, source);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
