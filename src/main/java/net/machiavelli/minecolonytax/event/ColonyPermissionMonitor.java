package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class ColonyPermissionMonitor {

    private static final Logger LOGGER = LogManager.getLogger(ColonyPermissionMonitor.class);

    private static final Map<Integer, Set<UUID>> lastKnownOfficers = new ConcurrentHashMap<>();
    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 1200;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Gated by the master abandonment switch (4.x world-brick fix): this monitor exists
        // solely to auto-reactivate abandoned colonies (a permission-mutating action). When the
        // abandonment system is off it must not run, and skipping it also avoids a periodic
        // all-colony scan.
        if (!net.machiavelli.minecolonytax.TaxConfig.isColonyAbandonmentSystemEnabled()) {
            return;
        }

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        checkColonyOfficerChanges(event.getServer());
    }

    private static void checkColonyOfficerChanges(MinecraftServer server) {
        try {
            com.minecolonies.api.colony.IColonyManager colonyManager = com.minecolonies.api.IMinecoloniesAPI
                    .getInstance().getColonyManager();

            for (net.minecraft.world.level.Level world : server.getAllLevels()) {
                for (IColony colony : colonyManager.getColonies(world)) {
                    checkColonyForOfficerChanges(colony);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("Error checking colony officer changes", e);
        }
    }

    private static void checkColonyForOfficerChanges(IColony colony) {
        try {
            int colonyId = colony.getID();

            Set<UUID> currentOfficers = colony.getPermissions().getPlayers().values().stream()
                    .filter(player -> player.getRank() != null && player.getRank().isColonyManager())
                    .map(ColonyPlayer::getID)
                    .collect(Collectors.toSet());

            Set<UUID> previousOfficers = lastKnownOfficers.get(colonyId);
            lastKnownOfficers.put(colonyId, currentOfficers);
            if (previousOfficers == null) {
                return;
            }

            if (currentOfficers.equals(previousOfficers)) {
                return;
            }
            boolean newOfficersAdded = currentOfficers.size() > previousOfficers.size() ||
                    !previousOfficers.containsAll(currentOfficers);

            if (newOfficersAdded) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("Detected new officers in colony {}: {} -> {} officers",
                            colony.getName(), previousOfficers.size(), currentOfficers.size());
                }

                if (ColonyAbandonmentManager.isColonyAbandoned(colony) && !currentOfficers.isEmpty()) {
                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("Colony {} was abandoned but now has officers - marking as no longer abandoned",
                                colony.getName());
                    }

                    ColonyAbandonmentManager.checkForNewOfficers(colony);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("Error checking colony {} for officer changes", colony.getID(), e);
        }
    }

    public static void refreshColonyOfficerTracking(IColony colony) {
        try {
            Set<UUID> currentOfficers = colony.getPermissions().getPlayers().values().stream()
                    .filter(player -> player.getRank() != null && player.getRank().isColonyManager())
                    .map(ColonyPlayer::getID)
                    .collect(Collectors.toSet());

            lastKnownOfficers.put(colony.getID(), currentOfficers);

            LOGGER.debug("Refreshed officer tracking for colony {}: {} officers",
                    colony.getName(), currentOfficers.size());

        } catch (Exception e) {
            LOGGER.error("Error refreshing officer tracking for colony {}", colony.getID(), e);
        }
    }

    public static void clearColonyTracking(int colonyId) {
        lastKnownOfficers.remove(colonyId);
    }
}
