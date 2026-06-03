package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client payload with War Chest data response.
 */
public record WarChestDataResponsePayload(
        int colonyId,
        int balance,
        int maxCapacity,
        int drainPerMinute,
        int taxBalance,
        boolean autoSurrender,
        double minPercentForWar
) implements CustomPacketPayload {

    public static final Type<WarChestDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "war_chest_data_response"));

    // StreamCodec.composite() supports at most 6 fields; use StreamCodec.of() for 7.
    public static final StreamCodec<RegistryFriendlyByteBuf, WarChestDataResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, val) -> {
                ByteBufCodecs.INT.encode(buf, val.colonyId());
                ByteBufCodecs.INT.encode(buf, val.balance());
                ByteBufCodecs.INT.encode(buf, val.maxCapacity());
                ByteBufCodecs.INT.encode(buf, val.drainPerMinute());
                ByteBufCodecs.INT.encode(buf, val.taxBalance());
                ByteBufCodecs.BOOL.encode(buf, val.autoSurrender());
                ByteBufCodecs.DOUBLE.encode(buf, val.minPercentForWar());
            },
            buf -> new WarChestDataResponsePayload(
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.DOUBLE.decode(buf)
            )
        );

    @Override
    public Type<WarChestDataResponsePayload> type() {
        return TYPE;
    }

    public static void handle(WarChestDataResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.machiavelli.minecolonytax.client.MctClientNetHandlers.warChestData(payload));
    }
}
