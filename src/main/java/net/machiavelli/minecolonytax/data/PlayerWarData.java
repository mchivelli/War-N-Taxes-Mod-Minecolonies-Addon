package net.machiavelli.minecolonytax.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

public class PlayerWarData implements INBTSerializable<CompoundTag> {
    private int playersKilledInWar;
    private int timesKilledInWar;
    private int raidedColonies;
    private long amountRaided;
    private int warsWon;
    private int warsLost;
    private int warStalemates;

    public PlayerWarData() {
        this.playersKilledInWar = 0;
        this.timesKilledInWar = 0;
        this.raidedColonies = 0;
        this.amountRaided = 0;
        this.warsWon = 0;
        this.warsLost = 0;
        this.warStalemates = 0;
    }

    public void incrementPlayersKilledInWar() { this.playersKilledInWar++; }
    public void incrementTimesKilledInWar()   { this.timesKilledInWar++; }
    public void incrementRaidedColonies()      { this.raidedColonies++; }
    public void addAmountRaided(long amount)   { this.amountRaided += amount; }
    public void incrementWarsWon()             { this.warsWon++; }
    public void incrementWarsLost()            { this.warsLost++; }
    public void incrementWarStalemates()       { this.warStalemates++; }

    public int  getPlayersKilledInWar() { return playersKilledInWar; }
    public int  getTimesKilledInWar()   { return timesKilledInWar; }
    public int  getRaidedColonies()     { return raidedColonies; }
    public long getAmountRaided()       { return amountRaided; }
    public int  getWarsWon()            { return warsWon; }
    public int  getWarsLost()           { return warsLost; }
    public int  getWarStalemates()      { return warStalemates; }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("playersKilledInWar", playersKilledInWar);
        tag.putInt("timesKilledInWar",   timesKilledInWar);
        tag.putInt("raidedColonies",     raidedColonies);
        tag.putLong("amountRaided",      amountRaided);
        tag.putInt("warsWon",            warsWon);
        tag.putInt("warsLost",           warsLost);
        tag.putInt("warStalemates",      warStalemates);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        playersKilledInWar = nbt.getInt("playersKilledInWar");
        timesKilledInWar   = nbt.getInt("timesKilledInWar");
        raidedColonies     = nbt.getInt("raidedColonies");
        amountRaided       = nbt.getLong("amountRaided");
        warsWon            = nbt.getInt("warsWon");
        warsLost           = nbt.getInt("warsLost");
        warStalemates      = nbt.getInt("warStalemates");
    }
} 
