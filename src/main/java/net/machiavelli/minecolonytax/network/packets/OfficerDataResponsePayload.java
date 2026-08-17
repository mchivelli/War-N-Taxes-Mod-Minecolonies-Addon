package net.machiavelli.minecolonytax.network.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-to-client payload carrying officer data for the GUI.
 *
 * <p>Also carries the colony-wide permission defaults: those live in server-only state
 * (TaxPermissionManager), so a connected client has no way to read them and used to render
 * the built-in fallback for every colony regardless of the real setting.
 */
public record OfficerDataResponsePayload(int colonyId, String officerJson, String defaultsJson)
        implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    public static final Type<OfficerDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "officer_data_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OfficerDataResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, OfficerDataResponsePayload::colonyId,
            ByteBufCodecs.STRING_UTF8, OfficerDataResponsePayload::officerJson,
            ByteBufCodecs.STRING_UTF8, OfficerDataResponsePayload::defaultsJson,
            OfficerDataResponsePayload::new
        );

    @Override
    public Type<OfficerDataResponsePayload> type() {
        return TYPE;
    }

    /** Construct from live data on the server side. */
    public OfficerDataResponsePayload(List<OfficerData> officers, int colonyId,
                                      Map<ColonyPermission, Boolean> colonyDefaults) {
        this(colonyId, GSON.toJson(officers),
                GSON.toJson(colonyDefaults != null ? colonyDefaults : new HashMap<ColonyPermission, Boolean>()));
    }

    public List<OfficerData> getOfficers() {
        java.lang.reflect.Type listType = new TypeToken<List<OfficerData>>() {}.getType();
        List<OfficerData> result = GSON.fromJson(officerJson, listType);
        return result != null ? result : new ArrayList<>();
    }

    /** Colony-wide defaults; missing entries fall back to the action's built-in default. */
    public Map<ColonyPermission, Boolean> getColonyDefaults() {
        Map<ColonyPermission, Boolean> result = new EnumMap<>(ColonyPermission.class);
        if (defaultsJson == null || defaultsJson.isEmpty()) return result;
        try {
            java.lang.reflect.Type mapType = new TypeToken<Map<ColonyPermission, Boolean>>() {}.getType();
            Map<ColonyPermission, Boolean> parsed = GSON.fromJson(defaultsJson, mapType);
            if (parsed != null) {
                parsed.forEach((permission, allowed) -> {
                    if (permission != null && allowed != null) result.put(permission, allowed);
                });
            }
        } catch (Exception ignored) {
            // Unknown permission name from a mismatched build: fall back to defaults rather
            // than dropping the whole officer list.
        }
        return result;
    }

    public static void handle(OfficerDataResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.machiavelli.minecolonytax.client.MctClientNetHandlers.officerData(payload));
    }
}
