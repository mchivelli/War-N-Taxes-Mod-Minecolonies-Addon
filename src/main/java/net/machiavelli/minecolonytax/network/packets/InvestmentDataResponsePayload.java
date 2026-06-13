package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-to-client payload carrying Investment (colony upgrade) data:
 * per-type levels + costs, the colony's war-chest balance, max capacity and max upgrade level.
 *
 * <p>The two {@code Map<String,Integer>} fields can't be expressed with the fixed-arity
 * {@link StreamCodec#composite} helpers, so this uses {@link StreamCodec#of} with explicit
 * map serialization (same approach {@code WarChestDataResponsePayload} uses for its 7th field).
 */
public record InvestmentDataResponsePayload(
        int colonyId,
        Map<String, Integer> upgradeLevels,
        Map<String, Integer> upgradeCosts,
        int treasuryBalance,
        int maxCapacity,
        int maxLevel
) implements CustomPacketPayload {

    public static final Type<InvestmentDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "investment_data_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InvestmentDataResponsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, val) -> {
                ByteBufCodecs.INT.encode(buf, val.colonyId());
                writeMap(buf, val.upgradeLevels());
                writeMap(buf, val.upgradeCosts());
                ByteBufCodecs.INT.encode(buf, val.treasuryBalance());
                ByteBufCodecs.INT.encode(buf, val.maxCapacity());
                ByteBufCodecs.INT.encode(buf, val.maxLevel());
            },
            buf -> new InvestmentDataResponsePayload(
                ByteBufCodecs.INT.decode(buf),
                readMap(buf),
                readMap(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.INT.decode(buf)
            )
        );

    private static void writeMap(RegistryFriendlyByteBuf buf, Map<String, Integer> map) {
        ByteBufCodecs.INT.encode(buf, map.size());
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, e.getKey());
            ByteBufCodecs.INT.encode(buf, e.getValue());
        }
    }

    private static Map<String, Integer> readMap(RegistryFriendlyByteBuf buf) {
        int size = ByteBufCodecs.INT.decode(buf);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = ByteBufCodecs.STRING_UTF8.decode(buf);
            int value = ByteBufCodecs.INT.decode(buf);
            map.put(key, value);
        }
        return map;
    }

    @Override
    public Type<InvestmentDataResponsePayload> type() {
        return TYPE;
    }

    public static void handle(InvestmentDataResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            net.machiavelli.minecolonytax.client.MctClientNetHandlers.investmentData(payload));
    }
}
