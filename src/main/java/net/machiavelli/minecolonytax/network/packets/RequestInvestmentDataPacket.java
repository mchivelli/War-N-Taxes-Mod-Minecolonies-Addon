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
            if (player == null) return;
            // Every bail-out below used to return silently, leaving the GUI with an empty
            // cost map that renders as "Cost: 0" — indistinguishable from a broken feature.
            // Tell the player which gate stopped them instead.
            if (!TaxConfig.isUpgradesEnabled()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("Investments are disabled (EnableColonyUpgrades = false).")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
            // Keeps 5.0.4's "say why it was refused" message, but resolves through ColonyLookup:
            // getColonyByWorld(id, player.level()) only sees the dimension the player is standing
            // in, so an overworld colony was "not found" the moment its owner stepped into the
            // Nether. The message is worded accordingly — this now really is unknown, not merely
            // absent from the current dimension.
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId, player);
            if (colony == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("Investments: colony not found.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }
            // Null-safe rank check: getRank(uuid) returns null for non-members and
            // would NPE on the main server thread.
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("Investments: you need colony-manager rank in this colony.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

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
