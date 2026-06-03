package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.machiavelli.minecolonytax.upgrade.UpgradeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RequestInvestmentDataPacket {

    private final int colonyId;

    public RequestInvestmentDataPacket(int colonyId) {
        this.colonyId = colonyId;
    }

    public RequestInvestmentDataPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isUpgradesEnabled()) return;

            IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = mgr.getColonyByWorld(colonyId, player.level());
            if (colony == null) return;
            // Null-safe rank check: getRank(uuid) returns null for non-members and
            // would NPE on the main server thread.
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) return;

            int maxLevel = TaxConfig.getUpgradeMaxLevel();
            Map<String, Integer> levels = new LinkedHashMap<>();
            Map<String, Integer> costs = new LinkedHashMap<>();
            for (UpgradeType type : UpgradeType.values()) {
                int level = ColonyUpgradeManager.getLevel(colonyId, type);
                levels.put(type.name(), level);
                int cost = level >= maxLevel ? 0 : ColonyUpgradeManager.getUpgradeCost(colonyId, type);
                costs.put(type.name(), cost);
            }

            int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
            int maxCapacity = TreasuryManager.getEffectiveMaxCapacity(colonyId);

            NetworkHandler.sendToPlayer(player, new InvestmentDataResponsePacket(
                    colonyId, levels, costs, treasuryBalance, maxCapacity, maxLevel));
        });
        ctx.get().setPacketHandled(true);
    }
}
