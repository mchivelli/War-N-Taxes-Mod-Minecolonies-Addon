package net.machiavelli.minecolonytax.network.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload carrying colony tax data for the GUI.
 * Uses JSON serialization for the complex colony and vassal data.
 */
public record ColonyDataResponsePayload(String colonyJson, String vassalJson, String eventLogJson) implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    public static final Type<ColonyDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "colony_data_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyDataResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ColonyDataResponsePayload::colonyJson,
            ByteBufCodecs.STRING_UTF8, ColonyDataResponsePayload::vassalJson,
            ByteBufCodecs.STRING_UTF8, ColonyDataResponsePayload::eventLogJson,
            ColonyDataResponsePayload::new
        );

    @Override
    public Type<ColonyDataResponsePayload> type() {
        return TYPE;
    }

    /** Construct from live data on the server side (no event log). */
    public ColonyDataResponsePayload(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData) {
        this(GSON.toJson(colonyData), GSON.toJson(vassalData), "{}");
    }

    /** Construct from live data on the server side, including the per-colony event log. */
    public ColonyDataResponsePayload(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
            java.util.Map<Integer, List<net.machiavelli.minecolonytax.events.random.EventLogEntry>> eventLogData) {
        this(GSON.toJson(colonyData), GSON.toJson(vassalData),
             GSON.toJson(eventLogData != null ? eventLogData : new java.util.HashMap<>()));
    }

    public List<ColonyTaxData> getColonyData() {
        java.lang.reflect.Type listType = new TypeToken<List<ColonyTaxData>>() {}.getType();
        List<ColonyTaxData> result = GSON.fromJson(colonyJson, listType);
        return result != null ? result : new ArrayList<>();
    }

    public List<VassalIncomeData> getVassalData() {
        java.lang.reflect.Type listType = new TypeToken<List<VassalIncomeData>>() {}.getType();
        List<VassalIncomeData> result = GSON.fromJson(vassalJson, listType);
        return result != null ? result : new ArrayList<>();
    }

    public java.util.Map<Integer, List<net.machiavelli.minecolonytax.events.random.EventLogEntry>> getEventLogData() {
        java.lang.reflect.Type mapType =
            new TypeToken<java.util.Map<Integer, List<net.machiavelli.minecolonytax.events.random.EventLogEntry>>>() {}.getType();
        java.util.Map<Integer, List<net.machiavelli.minecolonytax.events.random.EventLogEntry>> result =
            GSON.fromJson(eventLogJson, mapType);
        return result != null ? result : new java.util.HashMap<>();
    }

    public static void handle(ColonyDataResponsePayload payload, IPayloadContext context) {
        // Client work delegated to a client-only class so this payload (loaded on the dedicated
        // server during network registration) never references net.minecraft.client.* types.
        context.enqueueWork(() -> net.machiavelli.minecolonytax.client.MctClientNetHandlers.colonyData(payload));
    }
}
