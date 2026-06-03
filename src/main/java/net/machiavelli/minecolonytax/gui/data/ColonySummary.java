package net.machiavelli.minecolonytax.gui.data;

/** Lightweight colony identifier for client-side lists (e.g. spy target selection). */
public class ColonySummary {
    private final int id;
    private final String name;

    public ColonySummary(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
}
