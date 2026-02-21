package net.machiavelli.minecolonytax.network.packets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.espionage.SpyIntelData;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.espionage.SpyMission;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SpyDataResponsePacket {

    private static final Gson GSON = new GsonBuilder().create();
    private final String jsonPayload;

    public SpyDataResponsePacket(List<SpyMission> missions) {
        List<SpyMissionData> dtoList = new ArrayList<>();
        for (SpyMission m : missions) {
            SpyIntelData intel = SpyManager.getIntel(m.getTargetColonyId());
            String targetName = intel != null ? intel.getTargetColonyName() : "Colony " + m.getTargetColonyId();
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
                    intel);
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
            Type listType = new TypeToken<List<SpyMissionData>>() {
            }.getType();
            List<SpyMissionData> missions = GSON.fromJson(jsonPayload, listType);

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TaxManagementScreen screen) {
                screen.updateSpyData(missions);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
