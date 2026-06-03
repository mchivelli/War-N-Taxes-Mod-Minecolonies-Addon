package net.machiavelli.minecolonytax.network.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
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
 * Server-to-client payload carrying officer data for the GUI.
 */
public record OfficerDataResponsePayload(int colonyId, String officerJson) implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    public static final Type<OfficerDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "officer_data_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OfficerDataResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, OfficerDataResponsePayload::colonyId,
            ByteBufCodecs.STRING_UTF8, OfficerDataResponsePayload::officerJson,
            OfficerDataResponsePayload::new
        );

    @Override
    public Type<OfficerDataResponsePayload> type() {
        return TYPE;
    }

    /** Construct from live data on the server side. */
    public OfficerDataResponsePayload(List<OfficerData> officers, int colonyId) {
        this(colonyId, GSON.toJson(officers));
    }

    public List<OfficerData> getOfficers() {
        java.lang.reflect.Type listType = new TypeToken<List<OfficerData>>() {}.getType();
        List<OfficerData> result = GSON.fromJson(officerJson, listType);
        return result != null ? result : new ArrayList<>();
    }

    public static void handle(OfficerDataResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.machiavelli.minecolonytax.client.MctClientNetHandlers.officerData(payload));
    }
}
