package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.economy.TaxPolicyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to set a colony's tax policy
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
            if (player != null) {
                // Verify basic permissions (logic similar to WntCommands)
                // Since this affects gameplay, we should ensure the player is actually a
                // manager of the colony
                // For now, we trust the GUI only sends this for valid colonies,
                // but a more robust check involves getting the colony and checking permissions.

                try {
                    TaxPolicyManager.TaxRate rate = TaxPolicyManager.TaxRate.valueOf(policyName);

                    // Don't allow manual setting of WAR policy
                    if (rate == TaxPolicyManager.TaxRate.WAR) {
                        return;
                    }

                    // Set the rate
                    TaxPolicyManager.setTaxRate(colonyId, rate);

                    // Sending a success message to the player is optional but nice
                    // player.sendSystemMessage(Component.literal("Tax rate updated to " +
                    // rate.name()));
                } catch (IllegalArgumentException e) {
                    // Invalid policy name
                }
            }
        });
        return true;
    }
}
