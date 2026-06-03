package net.machiavelli.minecolonytax.compat;

import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * JourneyMap integration for spy mission visualization.
 *
 * <p>This outer class contains NO JourneyMap imports. It guards all JM access
 * behind {@code ModList.isLoaded("journeymap")} so that the inner
 * implementation class ({@link JmImpl}) is only loaded by the JVM when
 * JourneyMap is actually present — avoiding {@code NoClassDefFoundError}.
 *
 * <p>Called from {@code SpyDataResponsePayload.handle()} whenever mission data arrives.
 */
public class SpyJourneyMapPlugin {

    private static final Logger LOGGER = LogManager.getLogger(SpyJourneyMapPlugin.class);
    private static Boolean jmPresent = null;

    public static void syncWaypoints(List<SpyMissionData> missions) {
        if (jmPresent == null) {
            jmPresent = ModList.get().isLoaded("journeymap");
            if (jmPresent) LOGGER.info("[WnT] JourneyMap detected — spy travel lines enabled.");
        }
        if (!jmPresent) return;

        try {
            JmImpl.syncOverlays(missions);
        } catch (NoClassDefFoundError e) {
            LOGGER.warn("[WnT-JM] JourneyMap classes not available at runtime: {}", e.getMessage());
            jmPresent = false;
        }
    }
}
