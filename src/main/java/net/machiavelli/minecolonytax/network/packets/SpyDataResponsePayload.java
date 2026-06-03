package net.machiavelli.minecolonytax.network.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.compat.SpyJourneyMapPlugin;
import net.machiavelli.minecolonytax.espionage.SpyIntelData;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
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
import java.util.Set;

/**
 * Server-to-client payload carrying spy mission data for the GUI.
 */
public record SpyDataResponsePayload(String jsonPayload) implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    // Missions in these states have concluded — their intel (if any) is included in the DTO.
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "ESCAPED", "RECALLED", "KILLED");

    public static final Type<SpyDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "spy_data_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpyDataResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpyDataResponsePayload::jsonPayload,
            SpyDataResponsePayload::new
        );

    @Override
    public Type<SpyDataResponsePayload> type() {
        return TYPE;
    }

    /** Construct from live mission data on the server side. */
    public SpyDataResponsePayload(List<SpyMission> missions) {
        this(buildJson(missions));
    }

    private static String buildJson(List<SpyMission> missions) {
        List<SpyMissionData> dtoList = new ArrayList<>();
        for (SpyMission m : missions) {
            boolean terminal = TERMINAL_STATUSES.contains(m.getStatus());
            SpyIntelData intel = terminal ? m.getMissionIntel() : null;
            String targetName = (intel != null && intel.getTargetColonyName() != null)
                    ? intel.getTargetColonyName()
                    : "Colony " + m.getTargetColonyId();
            dtoList.add(new SpyMissionData(
                    m.getMissionId(),
                    targetName,
                    m.getTargetColonyId(),
                    m.getAttackerColonyId(),
                    m.getMissionType(),
                    m.getStatus(),
                    m.getStartTime(),
                    m.getMaxDurationMs(),
                    m.getCost(),
                    intel,
                    m.getSourceX(),
                    m.getSourceZ(),
                    m.getDestX(),
                    m.getDestZ(),
                    m.getTravelDurationMs(),
                    m.getRecallStartTime()));
        }
        return GSON.toJson(dtoList);
    }

    public static void handle(SpyDataResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            java.lang.reflect.Type listType = new TypeToken<List<SpyMissionData>>() {}.getType();
            List<SpyMissionData> missions = GSON.fromJson(payload.jsonPayload, listType);

            TaxManagementScreen.updateLatestMissions(missions);
            SpyJourneyMapPlugin.syncWaypoints(missions);
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TaxManagementScreen screen) {
                screen.updateSpyData(missions);
            }
        });
    }
}
