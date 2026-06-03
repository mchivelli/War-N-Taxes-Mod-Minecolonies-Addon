package net.machiavelli.minecolonytax.upgrade;

import java.util.HashMap;
import java.util.Map;

public class ColonyUpgradeData {
    public final int colonyId;
    // String keys instead of enum keys — Gson deserializes enum-keyed maps as LinkedHashMap<String,?> which breaks enum lookups
    private final Map<String, Integer> levels;

    public ColonyUpgradeData(int colonyId) {
        this.colonyId = colonyId;
        this.levels = new HashMap<>();
    }

    // Required by Gson
    ColonyUpgradeData() {
        this.colonyId = 0;
        this.levels = new HashMap<>();
    }

    public int getLevel(UpgradeType type) {
        return levels.getOrDefault(type.name(), 0);
    }

    public void setLevel(UpgradeType type, int level) {
        levels.put(type.name(), level);
    }

    public Map<String, Integer> getLevels() {
        return new HashMap<>(levels);
    }
}
