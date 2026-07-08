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
public record SpyDataResponsePayload(String jsonPayload, String targetsJson) implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    // Missions in these states have concluded — their intel (if any) is included in the DTO.
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "ESCAPED", "RECALLED", "KILLED");

    public static final Type<SpyDataResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "spy_data_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpyDataResponsePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpyDataResponsePayload::jsonPayload,
            ByteBufCodecs.STRING_UTF8, SpyDataResponsePayload::targetsJson,
            SpyDataResponsePayload::new
        );

    @Override
    public Type<SpyDataResponsePayload> type() {
        return TYPE;
    }

    /**
     * Construct from live mission data on the server side. Also snapshots the colonies the
     * player may target with a spy (every colony they do not manage) so the espionage UI can
     * offer real targets instead of only the player's own colonies.
     */
    public SpyDataResponsePayload(List<SpyMission> missions, net.minecraft.server.level.ServerPlayer player) {
        this(buildJson(missions),
             GSON.toJson(net.machiavelli.minecolonytax.server.ColonyDataCollector.collectSpyTargetColonies(player)));
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
        context.enqueueWork(() -> net.machiavelli.minecolonytax.client.MctClientNetHandlers.spyData(payload));
    }
}
