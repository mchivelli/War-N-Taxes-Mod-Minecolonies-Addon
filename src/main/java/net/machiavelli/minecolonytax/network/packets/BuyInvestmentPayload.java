package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.machiavelli.minecolonytax.upgrade.UpgradeType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client-to-server payload to purchase one level of a colony investment (upgrade).
 * The upgrade type is sent as its enum name. On success the server replies with a
 * refreshed {@link InvestmentDataResponsePayload} so the open GUI updates live.
 */
public record BuyInvestmentPayload(int colonyId, String upgradeTypeName) implements CustomPacketPayload {

    public static final Type<BuyInvestmentPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "buy_investment"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyInvestmentPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, BuyInvestmentPayload::colonyId,
            ByteBufCodecs.STRING_UTF8, BuyInvestmentPayload::upgradeTypeName,
            BuyInvestmentPayload::new
        );

    @Override
    public Type<BuyInvestmentPayload> type() {
        return TYPE;
    }

    public static void handle(BuyInvestmentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!TaxConfig.isUpgradesEnabled()) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getColonyByWorld(payload.colonyId, player.level());
            if (colony == null) return;

            // Investment purchases are OWNER-ONLY (aligns with the /wnt invest buy command).
            // Officers can manage the colony but not spend the treasury on permanent upgrades.
            // Null-guard getOwner() defensively; on an abandoned/recycled colony it can return null.
            UUID ownerUuid = colony.getPermissions().getOwner();
            if (ownerUuid == null || !ownerUuid.equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "Only the colony owner can purchase investments."));
                return;
            }

            UpgradeType type;
            try {
                type = UpgradeType.valueOf(payload.upgradeTypeName);
            } catch (IllegalArgumentException e) {
                return;
            }

            boolean success = ColonyUpgradeManager.purchase(payload.colonyId, type, player.getServer());
            if (success) {
                player.sendSystemMessage(Component.literal(
                        "Purchased " + type.name().toLowerCase().replace('_', ' ') + " investment."));
            } else {
                player.sendSystemMessage(Component.literal(
                        "Cannot purchase: insufficient war chest funds or already at max level."));
            }

            // Send refreshed data back so levels and balance update live.
            PacketDistributor.sendToPlayer(player,
                    RequestInvestmentDataPayload.buildResponse(payload.colonyId));
        });
    }
}
