package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.machiavelli.minecolonytax.upgrade.UpgradeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BuyInvestmentPacket {

    private final int colonyId;
    private final String upgradeTypeName;

    public BuyInvestmentPacket(int colonyId, String upgradeTypeName) {
        this.colonyId = colonyId;
        this.upgradeTypeName = upgradeTypeName;
    }

    public BuyInvestmentPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.upgradeTypeName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeUtf(upgradeTypeName);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !TaxConfig.isUpgradesEnabled()) return;

            IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
            if (colony == null) return;
            // Align packet auth with the /wnt invest buy slash command (WntCommands.java:3833):
            // investment purchases are OWNER-ONLY. Officers can manage the colony but not
            // spend the treasury on permanent upgrades.
            // Null-guard getOwner() defensively; on an abandoned/recycled colony it can return null.
            java.util.UUID ownerUuid = colony.getPermissions().getOwner();
            if (ownerUuid == null || !ownerUuid.equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "Only the colony owner can purchase investments."));
                return;
            }

            UpgradeType type;
            try {
                type = UpgradeType.valueOf(upgradeTypeName);
            } catch (IllegalArgumentException e) {
                return;
            }

            boolean success = ColonyUpgradeManager.purchase(colonyId, type, player.getServer());
            if (success) {
                player.sendSystemMessage(Component.literal(
                        "Purchased " + type.name().toLowerCase().replace('_', ' ') + " investment."));
            } else {
                player.sendSystemMessage(Component.literal(
                        "Cannot purchase: insufficient treasury funds or already at max level."));
            }

            // Send refreshed data back to client
            int maxLevel = TaxConfig.getUpgradeMaxLevel();
            Map<String, Integer> levels = new LinkedHashMap<>();
            Map<String, Integer> costs = new LinkedHashMap<>();
            for (UpgradeType t : UpgradeType.values()) {
                int level = ColonyUpgradeManager.getLevel(colonyId, t);
                levels.put(t.name(), level);
                int cost = level >= maxLevel ? 0 : ColonyUpgradeManager.getUpgradeCost(colonyId, t);
                costs.put(t.name(), cost);
            }
            int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
            int maxCapacity = TreasuryManager.getEffectiveMaxCapacity(colonyId);

            NetworkHandler.sendToPlayer(player, new InvestmentDataResponsePacket(
                    colonyId, levels, costs, treasuryBalance, maxCapacity, maxLevel));
        });
        ctx.get().setPacketHandled(true);
    }
}
