package net.machiavelli.minecolonytax.compat;

import journeymap.client.api.display.Waypoint;
import journeymap.client.api.impl.ClientAPI;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct JourneyMap API calls for spy mission waypoint overlays.
 * This class is ONLY loaded when JourneyMap is present (guarded by SpyJourneyMapPlugin).
 */
class JmImpl {

    private static final Logger LOGGER = LogManager.getLogger(JmImpl.class);
    // Keys use the format "missionId:src" and "missionId:dst"
    private static final Map<String, Waypoint> MANAGED_WAYPOINTS = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_STATUS = new ConcurrentHashMap<>();

    static void syncOverlays(List<SpyMissionData> missions) {
        Set<String> activeIds = new HashSet<>();

        for (SpyMissionData m : missions) {
            String status = m.getStatus();
            if (!"DEPLOYING".equals(status) && !"ACTIVE".equals(status)
                    && !"FLEEING".equals(status) && !"RETURNING".equals(status))
                continue;

            activeIds.add(m.getMissionId());

            // Waypoints must be recreated on status change because color and label differ per status
            String prev = LAST_STATUS.get(m.getMissionId());
            if (!status.equals(prev)) {
                removeWaypoints(m.getMissionId());
                LAST_STATUS.put(m.getMissionId(), status);
            }

            String srcKey = m.getMissionId() + ":src";
            String dstKey = m.getMissionId() + ":dst";

            if (!MANAGED_WAYPOINTS.containsKey(srcKey)) {
                BlockPos source = new BlockPos(m.getSourceX(), 64, m.getSourceZ());
                BlockPos dest = new BlockPos(m.getDestX(), 64, m.getDestZ());
                int color = colorForStatus(status);

                addWaypoint(srcKey, buildSourceLabel(m), source, color);
                addWaypoint(dstKey, buildDestLabel(m), dest, color);
            }
        }

        Set<String> staleKeys = new HashSet<>();
        for (String key : MANAGED_WAYPOINTS.keySet()) {
            String missionId = key.substring(0, key.lastIndexOf(':'));
            if (!activeIds.contains(missionId)) {
                staleKeys.add(missionId);
            }
        }
        for (String id : staleKeys) {
            removeWaypoints(id);
            LAST_STATUS.remove(id);
        }
    }

    private static void addWaypoint(String key, String label, BlockPos pos, int color) {
        try {
            ClientAPI api = ClientAPI.INSTANCE;
            if (api == null) {
                LOGGER.warn("JourneyMap ClientAPI.INSTANCE is null");
                return;
            }

            Waypoint wp = new Waypoint(MineColonyTax.MOD_ID, label, Level.OVERWORLD, pos);
            wp.setColor(color);
            wp.setPersistent(false);
            wp.setEditable(false);

            api.show(wp);
            MANAGED_WAYPOINTS.put(key, wp);
            if (TaxConfig.isDebugLogging())
                LOGGER.debug("Created waypoint '{}' at ({},{}) color=0x{}", label, pos.getX(), pos.getZ(), Integer.toHexString(color));
        } catch (Exception e) {
            LOGGER.warn("Failed to create waypoint '{}': {}", label, e.getMessage(), e);
        }
    }

    private static void removeWaypoints(String missionId) {
        removeWaypoint(missionId + ":src");
        removeWaypoint(missionId + ":dst");
    }

    private static void removeWaypoint(String key) {
        Waypoint wp = MANAGED_WAYPOINTS.remove(key);
        if (wp == null) return;
        try {
            ClientAPI.INSTANCE.remove(wp);
        } catch (Exception e) {
            LOGGER.debug("[WnT-JM] removeWaypoint failed for {}: {}", key, e.getMessage());
        }
    }

    private static String buildSourceLabel(SpyMissionData m) {
        return switch (m.getStatus()) {
            case "DEPLOYING" -> "Spy departing for " + m.getTargetColonyName();
            case "RETURNING" -> "Spy returning to base";
            default -> "Spy base (-> " + m.getTargetColonyName() + ")";
        };
    }

    private static String buildDestLabel(SpyMissionData m) {
        return switch (m.getStatus()) {
            case "DEPLOYING" -> "Spy target: " + m.getTargetColonyName();
            case "ACTIVE" -> "Spy active at: " + m.getTargetColonyName();
            case "FLEEING" -> "Spy FLEEING: " + m.getTargetColonyName();
            case "RETURNING" -> "Spy returning from: " + m.getTargetColonyName();
            default -> "Spy [" + m.getMissionType() + "]: " + m.getTargetColonyName();
        };
    }

    private static int colorForStatus(String status) {
        return switch (status) {
            case "DEPLOYING" -> 0xFFAA00;
            case "ACTIVE" -> 0x55FF55;
            case "FLEEING" -> 0xFF5555;
            case "RETURNING" -> 0x55FFFF;
            default -> 0xFFFFFF;
        };
    }
}
