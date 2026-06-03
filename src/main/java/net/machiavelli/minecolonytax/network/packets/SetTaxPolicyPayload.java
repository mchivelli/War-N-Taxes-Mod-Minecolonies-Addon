package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload to change a colony's tax policy.
 */
public record SetTaxPolicyPayload(int colonyId, String policyName) implements CustomPacketPayload {

    public static final Type<SetTaxPolicyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "set_tax_policy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTaxPolicyPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, SetTaxPolicyPayload::colonyId,
            ByteBufCodecs.STRING_UTF8, SetTaxPolicyPayload::policyName,
            SetTaxPolicyPayload::new
        );

    @Override
    public Type<SetTaxPolicyPayload> type() {
        return TYPE;
    }

    public static void handle(SetTaxPolicyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getAllColonies().stream()
                    .filter(c -> c.getID() == payload.colonyId)
                    .findFirst()
                    .orElse(null);

            if (colony == null) {
                player.sendSystemMessage(Component.literal("\u00a7cColony not found!"));
                return;
            }

            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(Component.literal("\u00a7cYou don't have permission to change tax policy for this colony!"));
                return;
            }

            TaxPolicy policy = TaxPolicy.fromString(payload.policyName);
            if (policy == null) {
                player.sendSystemMessage(Component.literal("\u00a7cInvalid policy: " + payload.policyName));
                return;
            }

            TaxPolicyManager.setPolicy(payload.colonyId, policy);

            double revMod = policy.getRevenueModifier();
            String revModStr = revMod > 1.0
                    ? "\u00a7a+" + String.format("%.0f", (revMod - 1) * 100) + "%"
                    : revMod < 1.0
                    ? "\u00a7c" + String.format("%.0f", (revMod - 1) * 100) + "%"
                    : "\u00a7fnormal";

            double hapMod = policy.getHappinessModifier();
            String hapModStr = hapMod > 0
                    ? "\u00a7a+" + String.format("%.0f", hapMod * 100) + "%"
                    : hapMod < 0
                    ? "\u00a7c" + String.format("%.0f", hapMod * 100) + "%"
                    : "\u00a7fnormal";

            player.sendSystemMessage(Component.literal("\u00a76Tax policy changed to: " + policy.getColorCode() + policy.getDisplayName()));
            player.sendSystemMessage(Component.literal("\u00a77Revenue: " + revModStr + " \u00a77| Happiness: " + hapModStr));
        });
    }
}
