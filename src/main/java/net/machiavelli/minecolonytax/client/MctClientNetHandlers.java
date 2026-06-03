package net.machiavelli.minecolonytax.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.compat.SpyJourneyMapPlugin;
import net.machiavelli.minecolonytax.gui.SpyDialogScreen;
import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.machiavelli.minecolonytax.network.packets.ColonyDataResponsePayload;
import net.machiavelli.minecolonytax.network.packets.OfficerDataResponsePayload;
import net.machiavelli.minecolonytax.network.packets.SpyDataResponsePayload;
import net.machiavelli.minecolonytax.network.packets.WarChestDataResponsePayload;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Client-only handlers for server -> client GUI payloads.
 *
 * <p>This class exists so the {@code CustomPacketPayload} classes themselves never reference
 * client-only types. The payloads are loaded on the dedicated server during network registration
 * (their {@code ::handle} method references are resolved by {@code ModNetworking}), and NeoForge's
 * {@code RuntimeDistCleaner} aborts the server if a class loaded there touches {@code net.minecraft.client.*}.
 * Each payload's {@code handle()} therefore delegates here via {@code context.enqueueWork(() -> ...)}; the
 * {@code invokestatic} to these methods is resolved lazily and only ever executed on the physical client,
 * so this class is never classloaded on a dedicated server.
 */
public final class MctClientNetHandlers {

    private MctClientNetHandlers() {}

    private static final Gson GSON = new GsonBuilder().create();

    public static void openTaxGui() {
        Minecraft.getInstance().setScreen(new TaxManagementScreen());
    }

    public static void openSpyDialog() {
        Minecraft.getInstance().setScreen(new SpyDialogScreen());
    }

    public static void colonyData(ColonyDataResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof TaxManagementScreen screen) {
            screen.updateColonyData(payload.getColonyData());
            screen.updateVassalData(payload.getVassalData());
        }
    }

    public static void officerData(OfficerDataResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof TaxManagementScreen screen) {
            screen.updateOfficerData(payload.getOfficers(), payload.colonyId());
        }
    }

    public static void warChestData(WarChestDataResponsePayload p) {
        if (Minecraft.getInstance().screen instanceof TaxManagementScreen screen) {
            screen.updateWarChestData(p.colonyId(), p.balance(), p.maxCapacity(),
                    p.drainPerMinute(), p.taxBalance(), p.autoSurrender(), p.minPercentForWar());
        }
    }

    public static void spyData(SpyDataResponsePayload payload) {
        java.lang.reflect.Type listType = new TypeToken<List<SpyMissionData>>() {}.getType();
        List<SpyMissionData> missions = GSON.fromJson(payload.jsonPayload(), listType);
        TaxManagementScreen.updateLatestMissions(missions);
        SpyJourneyMapPlugin.syncWaypoints(missions);
        if (Minecraft.getInstance().screen instanceof TaxManagementScreen screen) {
            screen.updateSpyData(missions);
        }
    }
}
