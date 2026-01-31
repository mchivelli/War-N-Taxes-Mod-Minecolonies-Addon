package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to change a colony's tax policy
 */
public class SetTaxPolicyPacket {
    private final int colonyId;
    private final String policyName;

    public SetTaxPolicyPacket(int colonyId, String policyName) {
        this.colonyId = colonyId;
        this.policyName = policyName;
    }

    public SetTaxPolicyPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.policyName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeUtf(policyName);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // Find the colony
            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = colonyManager.getAllColonies().stream()
                    .filter(c -> c.getID() == colonyId)
                    .findFirst()
                    .orElse(null);

            if (colony == null) {
                player.sendSystemMessage(Component.literal("§cColony not found!"));
                return;
            }

            // Check permissions - only colony managers can change policy
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                player.sendSystemMessage(Component.literal("§cYou don't have permission to change tax policy for this colony!"));
                return;
            }

            // Parse and validate policy
            TaxPolicy policy = TaxPolicy.fromString(policyName);
            if (policy == null) {
                player.sendSystemMessage(Component.literal("§cInvalid policy: " + policyName));
                return;
            }

            // Set the policy
            TaxPolicyManager.setPolicy(colonyId, policy);

            // Send confirmation
            double revMod = policy.getRevenueModifier();
            String revModStr = revMod > 1.0
                    ? "§a+" + String.format("%.0f", (revMod - 1) * 100) + "%"
                    : revMod < 1.0
                    ? "§c" + String.format("%.0f", (revMod - 1) * 100) + "%"
                    : "§fnormal";

            double hapMod = policy.getHappinessModifier();
            String hapModStr = hapMod > 0
                    ? "§a+" + String.format("%.0f", hapMod * 100) + "%"
                    : hapMod < 0
                    ? "§c" + String.format("%.0f", hapMod * 100) + "%"
                    : "§fnormal";

            player.sendSystemMessage(Component.literal("§6Tax policy changed to: " + policy.getColorCode() + policy.getDisplayName()));
            player.sendSystemMessage(Component.literal("§7Revenue: " + revModStr + " §7| Happiness: " + hapModStr));
        });
        return true;
    }
}
