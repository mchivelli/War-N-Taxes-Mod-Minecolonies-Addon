package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client payload that tells the client to open the Tax Management GUI.
 * Used by /wnt taxgui command.
 */
public record OpenTaxGUIPayload() implements CustomPacketPayload {

    public static final Type<OpenTaxGUIPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "open_tax_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTaxGUIPayload> STREAM_CODEC =
        StreamCodec.unit(new OpenTaxGUIPayload());

    @Override
    public Type<OpenTaxGUIPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTaxGUIPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.machiavelli.minecolonytax.client.MctClientNetHandlers.openTaxGui());
    }
}
