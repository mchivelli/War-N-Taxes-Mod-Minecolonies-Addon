package net.machiavelli.minecolonytax.pvp.persistence;

import java.util.ArrayList;
import java.util.List;

public class ArenaMapData {
    public String name;
    public String dimension;
    public int maxPlayers;
    public List<SpawnPointData> spawnPoints = new ArrayList<>();
} 