package net.machiavelli.minecolonytax.data;

import java.util.UUID;
import java.util.List;

public record RaidData(
        UUID attackerUUID,       // UUID of the attacking player
        long startTime,          // Timestamp when the raid started
        long gracePeriodEnd,     // Timestamp when the grace period ends
        boolean completed,       // Whether the raid has been completed
        List<UUID> participants  // List of participating players

) {
}
