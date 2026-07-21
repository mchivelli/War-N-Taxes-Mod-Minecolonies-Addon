package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.machiavelli.minecolonytax.upgrade.UpgradeType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-to-server payload to request Investment (colony upgrade) data for a colony.
 * The server gathers per-type levels + costs and replies with an {@link InvestmentDataResponsePayload}.
 */
public record RequestInvestmentDataPayload(int colonyId) implements CustomPacketPayload {

    public static final Type<RequestInvestmentDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "request_investment_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestInvestmentDataPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, RequestInvestmentDataPayload::colonyId,
            RequestInvestmentDataPayload::new
        );

    @Override
    public Type<RequestInvestmentDataPayload> type() {
        return TYPE;
    }

    public static void handle(RequestInvestmentDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isUpgradesEnabled()) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByWorld(payload.colonyId, player.level());
            if (colony == null) return;

            // Null-safe rank check: getRank(uuid) returns null for non-members and
            // would NPE on the main server thread.
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) return;

            PacketDistributor.sendToPlayer(player, buildResponse(payload.colonyId));
        });
    }

    /**
     * Shared builder for the response payload, reused by {@link BuyInvestmentPayload}.
     */
    static InvestmentDataResponsePayload buildResponse(int colonyId) {
        int maxLevel = TaxConfig.getUpgradeMaxLevel();
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Integer> costs = new LinkedHashMap<>();
        for (UpgradeType type : UpgradeType.values()) {
            int level = ColonyUpgradeManager.getLevel(colonyId, type);
            levels.put(type.name(), level);
            int cost = level >= maxLevel ? 0 : ColonyUpgradeManager.getUpgradeCost(colonyId, type);
            costs.put(type.name(), cost);
        }
        int treasuryBalance = WarChestManager.getWarChestBalance(colonyId);
        int maxCapacity = WarChestManager.getEffectiveMaxCapacity(colonyId);
        return new InvestmentDataResponsePayload(colonyId, levels, costs, treasuryBalance, maxCapacity, maxLevel);
    }
}
