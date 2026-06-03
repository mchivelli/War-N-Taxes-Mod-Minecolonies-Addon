package net.machiavelli.minecolonytax.events.random;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an active random event instance for a colony.
 *
 * Each active event tracks:
 * - The colony it affects
 * - The event type
 * - When it started
 * - How many tax cycles remain
 * - Which citizens are affected (for deep integration events)
 */
public class ActiveEvent {

    private final UUID eventId;
    private final int colonyId;
    private final RandomEventType type;
    private final long startTime;
    private int remainingCycles;
    private List<Integer> affectedCitizens;

    /**
     * Create a new active event.
     *
     * @param colonyId The colony this event affects
     * @param type The type of event
     */
    public ActiveEvent(int colonyId, RandomEventType type) {
        this.eventId = UUID.randomUUID();
        this.colonyId = colonyId;
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.remainingCycles = type.getDurationCycles();
        this.affectedCitizens = new ArrayList<>();
    }

    /**
     * Constructor for deserialization.
     */
    public ActiveEvent(UUID eventId, int colonyId, RandomEventType type,
                      long startTime, int remainingCycles, List<Integer> affectedCitizens) {
        this.eventId = eventId;
        this.colonyId = colonyId;
        this.type = type;
        this.startTime = startTime;
        this.remainingCycles = remainingCycles;
        this.affectedCitizens = affectedCitizens != null ? affectedCitizens : new ArrayList<>();
    }

    /**
     * Decrement the remaining cycles.
     * Called at the end of each tax cycle.
     */
    public void decrementCycle() {
        if (remainingCycles > 0) {
            remainingCycles--;
        }
    }

    /**
     * Check if this event has expired.
     *
     * @return true if the event has no remaining cycles
     */
    public boolean hasExpired() {
        return remainingCycles <= 0;
    }

    /**
     * Get a display string for this event.
     * Used in tax reports and commands.
     *
     * @return Formatted string like "Merchant Caravan (2 cycles remaining)"
     */
    public String getDisplayInfo() {
        if (remainingCycles == 1) {
            return type.getDisplayName() + " (1 cycle remaining)";
        } else {
            return type.getDisplayName() + " (" + remainingCycles + " cycles remaining)";
        }
    }

    /**
     * Get a short display string for this event.
     *
     * @return Formatted string like "Merchant Caravan (2)"
     */
    public String getShortDisplayInfo() {
        return type.getDisplayName() + " (" + remainingCycles + ")";
    }

    /**
     * Get the age of this event in milliseconds.
     *
     * @return Time since event started
     */
    public long getAge() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Add a citizen to the affected list.
     * Used for deep integration events (strikes, plague, etc.)
     *
     * @param citizenId The ID of the affected citizen
     */
    public void addAffectedCitizen(Integer citizenId) {
        if (!affectedCitizens.contains(citizenId)) {
            affectedCitizens.add(citizenId);
        }
    }

    /**
     * Set the list of affected citizens.
     * Used when triggering deep integration events.
     *
     * @param citizenIds List of citizen IDs
     */
    public void setAffectedCitizens(List<Integer> citizenIds) {
        this.affectedCitizens = new ArrayList<>(citizenIds);
    }

    /**
     * Get all affected citizens.
     *
     * @return List of citizen IDs
     */
    public List<Integer> getAffectedCitizens() {
        return new ArrayList<>(affectedCitizens);
    }

    // ==================== Getters ====================

    public UUID getEventId() {
        return eventId;
    }

    public int getColonyId() {
        return colonyId;
    }

    public RandomEventType getType() {
        return type;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getRemainingCycles() {
        return remainingCycles;
    }

    public void setRemainingCycles(int cycles) {
        this.remainingCycles = cycles;
    }

    @Override
    public String toString() {
        return "ActiveEvent{" +
                "eventId=" + eventId +
                ", colonyId=" + colonyId +
                ", type=" + type +
                ", remainingCycles=" + remainingCycles +
                ", affectedCitizens=" + affectedCitizens.size() +
                '}';
    }
}
