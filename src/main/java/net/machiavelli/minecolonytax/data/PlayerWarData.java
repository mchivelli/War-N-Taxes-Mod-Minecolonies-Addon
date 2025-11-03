package net.machiavelli.minecolonytax.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Player war statistics data.
 * Automatically serialized/deserialized by NeoForge Data Attachments system.
 */
public class PlayerWarData implements INBTSerializable<CompoundTag> {
    private int playersKilledInWar;
    private int raidedColonies;
    private long amountRaided;
    private int warsWon;
    private int warStalemates;

    public PlayerWarData() {
        this.playersKilledInWar = 0;
        this.raidedColonies = 0;
        this.amountRaided = 0;
        this.warsWon = 0;
        this.warStalemates = 0;
    }

    public void incrementPlayersKilledInWar() {
        this.playersKilledInWar++;
    }

    public void incrementRaidedColonies() {
        this.raidedColonies++;
    }

    public void addAmountRaided(long amount) {
        this.amountRaided += amount;
    }

    public void incrementWarsWon() {
        this.warsWon++;
    }

    public void incrementWarStalemates() {
        this.warStalemates++;
    }

    public int getPlayersKilledInWar() {
        return playersKilledInWar;
    }

    public int getRaidedColonies() {
        return raidedColonies;
    }

    public long getAmountRaided() {
        return amountRaided;
    }

    public int getWarsWon() {
        return warsWon;
    }

    public int getWarStalemates() {
        return warStalemates;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("playersKilledInWar", playersKilledInWar);
        tag.putInt("raidedColonies", raidedColonies);
        tag.putLong("amountRaided", amountRaided);
        tag.putInt("warsWon", warsWon);
        tag.putInt("warStalemates", warStalemates);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        playersKilledInWar = nbt.getInt("playersKilledInWar");
        raidedColonies = nbt.getInt("raidedColonies");
        amountRaided = nbt.getLong("amountRaided");
        warsWon = nbt.getInt("warsWon");
        warStalemates = nbt.getInt("warStalemates");
    }

    /**
     * Copy data from another PlayerWarData instance.
     * Used for player cloning (death, respawn, dimension change).
     * 
     * @param other The source data to copy from
     */
    public void copyFrom(PlayerWarData other) {
        this.playersKilledInWar = other.playersKilledInWar;
        this.raidedColonies = other.raidedColonies;
        this.amountRaided = other.amountRaided;
        this.warsWon = other.warsWon;
        this.warStalemates = other.warStalemates;
    }
} 
