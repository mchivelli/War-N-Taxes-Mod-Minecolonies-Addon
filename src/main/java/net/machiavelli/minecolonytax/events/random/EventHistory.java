package net.machiavelli.minecolonytax.events.random;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks historical events for a colony.
 *
 * This class maintains a rolling history of the last 10 events that have
 * occurred for a colony, along with their timestamps and outcomes.
 * Used for:
 * - Displaying event history to players via commands
 * - Analyzing event frequency and patterns
 * - Detecting recent event activity for cooldown logic
 */
public class EventHistory {

    private static final int MAX_HISTORY_SIZE = 10;

    private final int colonyId;
    private final List<HistoricalEvent> eventHistory;

    /**
     * Create a new event history tracker for a colony.
     *
     * @param colonyId The colony ID to track
     */
    public EventHistory(int colonyId) {
        this.colonyId = colonyId;
        this.eventHistory = new ArrayList<>();
    }

    /**
     * Add an event to the history.
     * Automatically maintains the max history size by removing oldest entries.
     *
     * @param type The event type
     * @param outcome The event outcome (COMPLETED, EXPIRED, CLEARED, etc.)
     */
    public void addEvent(RandomEventType type, EventOutcome outcome) {
        HistoricalEvent event = new HistoricalEvent(
            type,
            System.currentTimeMillis(),
            outcome
        );

        eventHistory.add(0, event); // Add to front (most recent first)

        // Trim to max size
        while (eventHistory.size() > MAX_HISTORY_SIZE) {
            eventHistory.remove(eventHistory.size() - 1);
        }
    }

    /**
     * Check if there has been any event within the specified time window.
     * Used for global cooldown checking.
     *
     * @param withinMs Time window in milliseconds
     * @return true if any event occurred within the time window
     */
    public boolean hasRecentEvent(long withinMs) {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - withinMs;

        for (HistoricalEvent event : eventHistory) {
            if (event.timestamp >= cutoffTime) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a specific event type occurred within the time window.
     *
     * @param type The event type to check
     * @param withinMs Time window in milliseconds
     * @return true if the specified event type occurred within the time window
     */
    public boolean hasRecentEventOfType(RandomEventType type, long withinMs) {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - withinMs;

        for (HistoricalEvent event : eventHistory) {
            if (event.type == type && event.timestamp >= cutoffTime) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all historical events (most recent first).
     *
     * @return List of historical events
     */
    public List<HistoricalEvent> getRecentEvents() {
        return new ArrayList<>(eventHistory);
    }

    /**
     * Get events that occurred within a specific time window.
     *
     * @param withinMs Time window in milliseconds
     * @return List of events within the time window
     */
    public List<HistoricalEvent> getRecentEvents(long withinMs) {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - withinMs;

        List<HistoricalEvent> recentEvents = new ArrayList<>();
        for (HistoricalEvent event : eventHistory) {
            if (event.timestamp >= cutoffTime) {
                recentEvents.add(event);
            }
        }

        return recentEvents;
    }

    /**
     * Get the count of events in history.
     *
     * @return Number of events in history
     */
    public int getEventCount() {
        return eventHistory.size();
    }

    /**
     * Clear all event history.
     * Used for testing or admin commands.
     */
    public void clearHistory() {
        eventHistory.clear();
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a single historical event entry.
     */
    public static class HistoricalEvent {
        private final RandomEventType type;
        private final long timestamp;
        private final EventOutcome outcome;

        public HistoricalEvent(RandomEventType type, long timestamp, EventOutcome outcome) {
            this.type = type;
            this.timestamp = timestamp;
            this.outcome = outcome;
        }

        public RandomEventType getType() {
            return type;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public EventOutcome getOutcome() {
            return outcome;
        }

        /**
         * Get age of this event in milliseconds.
         *
         * @return Age in milliseconds
         */
        public long getAge() {
            return System.currentTimeMillis() - timestamp;
        }

        /**
         * Get age of this event in minutes.
         *
         * @return Age in minutes
         */
        public long getAgeInMinutes() {
            return getAge() / (60L * 1000L);
        }

        @Override
        public String toString() {
            return "HistoricalEvent{" +
                    "type=" + type.getDisplayName() +
                    ", timestamp=" + timestamp +
                    ", outcome=" + outcome +
                    ", ageMinutes=" + getAgeInMinutes() +
                    '}';
        }
    }

    /**
     * Possible outcomes for events.
     */
    public enum EventOutcome {
        COMPLETED,      // Event ran its full duration
        EXPIRED,        // Event timed out
        CLEARED,        // Event was cleared by player action (e.g., building hospital)
        ACCEPTED,       // Player accepted a choice event
        DECLINED,       // Player declined a choice event
        CANCELLED       // Event was cancelled by admin command
    }

    @Override
    public String toString() {
        return "EventHistory{" +
                "colonyId=" + colonyId +
                ", eventCount=" + eventHistory.size() +
                '}';
    }
}
