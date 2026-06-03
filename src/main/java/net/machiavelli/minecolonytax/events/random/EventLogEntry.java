package net.machiavelli.minecolonytax.events.random;

/**
 * A single entry in the per-colony event log shown in the GUI.
 *
 * compactStat — short key-value string shown inline on the row, e.g. "+15% tax", "Looted 50$".
 *               Empty string if there is no numeric stat to show.
 * description — full explanation shown in the hover tooltip.
 */
public class EventLogEntry {

    private final String eventId;
    private final String displayName;
    private final String description;
    private final String compactStat;
    private final int colorCode;
    private boolean isActive;
    private int remainingCycles;

    public EventLogEntry(String eventId, String displayName, String description, String compactStat,
                         int colorCode, boolean isActive, int remainingCycles) {
        this.eventId = eventId;
        this.displayName = displayName;
        this.description = description;
        this.compactStat = compactStat != null ? compactStat : "";
        this.colorCode = colorCode;
        this.isActive = isActive;
        this.remainingCycles = remainingCycles;
    }

    /** Legacy constructor — compactStat derived automatically from description. */
    public EventLogEntry(String eventId, String displayName, String description,
                         int colorCode, boolean isActive, int remainingCycles) {
        this(eventId, displayName, description, "", colorCode, isActive, remainingCycles);
    }

    public String getEventId()         { return eventId; }
    public String getDisplayName()     { return displayName; }
    public String getDescription()     { return description; }
    public String getCompactStat()     { return compactStat; }
    public int    getColorCode()       { return colorCode; }
    public boolean isActive()          { return isActive; }
    public int    getRemainingCycles() { return remainingCycles; }

    public void setActive(boolean active)         { this.isActive = active; }
    public void setRemainingCycles(int cycles)    { this.remainingCycles = cycles; }
}
