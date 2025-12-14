package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.client.TaxGUIClientUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client packet that tells the client to open the Tax Management GUI.
 * Used by /wnt taxgui command.
 */
public class OpenTaxGUIPacket {

    public OpenTaxGUIPacket() {
    }

    public OpenTaxGUIPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Run on client thread
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TaxGUIClientUtils::openTaxGUI);
        });
        ctx.get().setPacketHandled(true);
    }
}
