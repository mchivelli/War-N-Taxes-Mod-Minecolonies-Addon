package net.machiavelli.minecolonytax.network.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.espionage.SpyIntelData;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SpyDataResponsePacket {

    private static final Gson GSON = new GsonBuilder().create();
    private final String jsonPayload;

    // Missions in these states have concluded — their intel (if any) is included in the DTO.
    // Active missions (DEPLOYING, ACTIVE, FLEEING, RETURNING) have intel stripped so the GUI
    // never reveals progressive data; intel is only delivered via the Written Book on return.
    private static final java.util.Set<String> TERMINAL_STATUSES =
            java.util.Set.of("COMPLETED", "ESCAPED", "RECALLED", "KILLED");

    public SpyDataResponsePacket(List<SpyMission> missions) {
        List<SpyMissionData> dtoList = new ArrayList<>();
        for (SpyMission m : missions) {
            boolean terminal = TERMINAL_STATUSES.contains(m.getStatus());
            // Only include intel for terminal missions — active missions show no intel in GUI
            SpyIntelData intel = terminal ? m.getMissionIntel() : null;
            String targetName;
            if (intel != null && intel.getTargetColonyName() != null) {
                targetName = intel.getTargetColonyName();
            } else if (m.getTargetColonyName() != null) {
                targetName = m.getTargetColonyName();
            } else {
                targetName = "Colony " + m.getTargetColonyId();
            }
            SpyMissionData data = new SpyMissionData(
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
                    m.getRecallStartTime());
            dtoList.add(data);
        }
        this.jsonPayload = GSON.toJson(dtoList);
    }

    public SpyDataResponsePacket(FriendlyByteBuf buf) {
        this.jsonPayload = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(jsonPayload, 32767);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Wrap all client-side work in DistExecutor so this class is safe to load on a
            // dedicated server. The double-lambda ensures Minecraft / TaxManagementScreen /
            // SpyJourneyMapPlugin references are only class-loaded on the physical client.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Type listType = new TypeToken<List<SpyMissionData>>() {
                }.getType();
                List<SpyMissionData> missions = GSON.fromJson(jsonPayload, listType);

                // Always update static cache and sync JourneyMap waypoints (event-driven, no tick polling)
                net.machiavelli.minecolonytax.gui.TaxManagementScreen.updateLatestMissions(missions);
                net.machiavelli.minecolonytax.compat.SpyJourneyMapPlugin.syncWaypoints(missions);
                // Also refresh the GUI if it is open
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen screen) {
                    screen.updateSpyData(missions);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
