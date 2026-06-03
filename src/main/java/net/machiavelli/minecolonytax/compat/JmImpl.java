package net.machiavelli.minecolonytax.compat;

import net.machiavelli.minecolonytax.gui.data.SpyMissionData;

import java.util.List;

/**
 * JourneyMap integration stub.
 * Full implementation requires journeymap as a compile-time dependency.
 * JourneyMap is an optional runtime dep guarded by SpyJourneyMapPlugin.
 */
class JmImpl {

    static void syncOverlays(List<SpyMissionData> missions) {
        // No-op: JourneyMap dependency not available at compile time
    }
}
