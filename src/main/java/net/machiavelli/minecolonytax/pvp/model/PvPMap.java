package net.machiavelli.minecolonytax.pvp.model;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class PvPMap {
    private final String name;
    private final List<GlobalPos> spawnPoints;
    private final ResourceKey<Level> dimension;
    private int maxPlayers;

    public PvPMap(String name, ResourceKey<Level> dimension) {
        this.name = name;
        this.dimension = dimension;
        this.spawnPoints = new ArrayList<>();
        this.maxPlayers = 16; // Default max players
    }

    public String getName() { return name; }
    public List<GlobalPos> getSpawnPoints() { return spawnPoints; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public void addSpawnPoint(GlobalPos pos) {
        if (pos == null) {
            throw new IllegalArgumentException("Cannot add null spawn point");
        }
        if (spawnPoints.size() >= maxPlayers) {
            throw new IllegalStateException("Cannot add more spawn points than max players");
        }
        spawnPoints.add(pos);
    }

    public boolean hasEnoughSpawnsForTeams(int teamSize, int teamCount) {
        return spawnPoints.size() >= (teamSize * teamCount);
    }
} 