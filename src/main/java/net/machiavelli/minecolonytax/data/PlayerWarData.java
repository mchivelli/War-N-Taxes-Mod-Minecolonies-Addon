package net.machiavelli.minecolonytax.data;

import net.minecraft.nbt.CompoundTag;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Player war statistics data.
 * Automatically serialized/deserialized by NeoForge Data Attachments system using Codec.
 */
public class PlayerWarData {
    
    public static final Codec<PlayerWarData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("playersKilledInWar").forGetter(d -> d.playersKilledInWar),
            Codec.INT.fieldOf("raidedColonies").forGetter(d -> d.raidedColonies),
            Codec.LONG.fieldOf("amountRaided").forGetter(d -> d.amountRaided),
            Codec.INT.fieldOf("warsWon").forGetter(d -> d.warsWon),
            Codec.INT.fieldOf("warStalemates").forGetter(d -> d.warStalemates)
        ).apply(instance, PlayerWarData::new)
    );
    
    private int playersKilledInWar;
    private int raidedColonies;
    private long amountRaided;
    private int warsWon;
    private int warStalemates;
    
    public PlayerWarData() {
        this(0, 0, 0, 0, 0);
    }
    
    private PlayerWarData(int playersKilled, int coloniesRaided, long amountRaided, int warsWon, int warStalemates) {
        this.playersKilledInWar = playersKilled;
        this.raidedColonies = coloniesRaided;
        this.amountRaided = amountRaided;
        this.warsWon = warsWon;
        this.warStalemates = warStalemates;
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
    
    /**
     * Serialize to NBT for compatibility with old systems (e.g., Web API cache).
     * NeoForge attachments use the Codec automatically, so this is just for manual NBT access.
     */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("playersKilledInWar", playersKilledInWar);
        tag.putInt("raidedColonies", raidedColonies);
        tag.putLong("amountRaided", amountRaided);
        tag.putInt("warsWon", warsWon);
        tag.putInt("warStalemates", warStalemates);
        return tag;
    }
    
    /**
     * Deserialize from NBT for compatibility with old systems (e.g., Web API cache).
     * NeoForge attachments use the Codec automatically, so this is just for manual NBT access.
     */
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
