package net.machiavelli.minecolonytax.data;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.capability.PlayerWarDataCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;

public class PlayerWarDataManager {

    /**
     * Increment the number of players killed in war for a player
     *
     * @param player The player who killed someone
     */
    public static void incrementPlayersKilledInWar(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            int oldValue = data.getPlayersKilledInWar();
            data.incrementPlayersKilledInWar();
            int newValue = data.getPlayersKilledInWar();
            MineColonyTax.LOGGER.info("STAT UPDATE: {} players killed in war: {} -> {}", 
                player.getName().getString(), oldValue, newValue);
            updateScoreboard(player, "playersKilled", newValue);
            // Mark data as dirty to ensure it's saved
            markDirty(player);
        });
    }

    /**
     * Increment the number of colonies raided by the player
     *
     * @param player The player who raided a colony
     */
    public static void incrementRaidedColonies(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            int oldValue = data.getRaidedColonies();
            data.incrementRaidedColonies();
            int newValue = data.getRaidedColonies();
            MineColonyTax.LOGGER.info("STAT UPDATE: {} raided colonies: {} -> {}", 
                player.getName().getString(), oldValue, newValue);
            updateScoreboard(player, "raidsCompleted", newValue);
            // Mark data as dirty to ensure it's saved
            markDirty(player);
        });
    }

    /**
     * Add to the total amount raided by the player
     *
     * @param player The player who raided
     * @param amount The amount raided
     */
    public static void addAmountRaided(ServerPlayer player, long amount) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            long oldValue = data.getAmountRaided();
            data.addAmountRaided(amount);
            long newValue = data.getAmountRaided();
            MineColonyTax.LOGGER.info("STAT UPDATE: {} amount raided: {} -> {} (+{})", 
                player.getName().getString(), oldValue, newValue, amount);
            updateScoreboard(player, "amountRaided", (int)newValue);
            // Mark data as dirty to ensure it's saved
            markDirty(player);
        });
    }

    /**
     * Increment the number of wars won by the player
     *
     * @param player The player who won a war
     */
    public static void incrementWarsWon(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            int oldValue = data.getWarsWon();
            data.incrementWarsWon();
            int newValue = data.getWarsWon();
            MineColonyTax.LOGGER.info("STAT UPDATE: {} wars won: {} -> {}", 
                player.getName().getString(), oldValue, newValue);
            updateScoreboard(player, "warsWon", newValue);
            // Mark data as dirty to ensure it's saved
            markDirty(player);
        });
    }

    /**
     * Increment the number of war stalemates the player was involved in
     *
     * @param player The player involved in a stalemated war
     */
    public static void incrementWarStalemates(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            int oldValue = data.getWarStalemates();
            data.incrementWarStalemates();
            int newValue = data.getWarStalemates();
            MineColonyTax.LOGGER.info("STAT UPDATE: {} war stalemates: {} -> {}", 
                player.getName().getString(), oldValue, newValue);
            updateScoreboard(player, "warStalemates", newValue);
            // Mark data as dirty to ensure it's saved
            markDirty(player);
        });
    }

    /**
     * Update a player's scoreboard to show their statistics
     *
     * @param player The player to update the scoreboard for
     * @param objective The scoreboard objective to update
     * @param value The value to set for the objective
     */
    private static void updateScoreboard(ServerPlayer player, String objective, int value) {
        if (player.getServer() != null) {
            Scoreboard sb = player.getServer().getScoreboard();
            var obj = sb.getObjective(objective);
            if (obj != null) {
                sb.getOrCreatePlayerScore(player.getName().getString(), obj).setScore(value);
            }
        }
    }
    
    /**
     * Force the player to save their data.
     * This ensures capability data is persisted to the player's .dat file.
     * 
     * @param player The player whose data should be saved
     */
    private static void markDirty(ServerPlayer player) {
        try {
            MineColonyTax.LOGGER.debug("Marking player data dirty for " + player.getName().getString());
            
            // Get the latest data from the capability
            PlayerWarDataCapability.get(player).ifPresent(data -> {
                try {
                    // Serialize the current war data
                    CompoundTag nbt = data.serializeNBT();
                    
                    // Save directly to player's persistent data to ensure it gets written to UUID.dat
                    CompoundTag persistentData = player.getPersistentData();
                    if (!persistentData.contains("ForgeData")) {
                        persistentData.put("ForgeData", new CompoundTag());
                    }
                    CompoundTag forgeData = persistentData.getCompound("ForgeData");
                    forgeData.put(MineColonyTax.MOD_ID + "_war_data", nbt);
                    
                    // Validate the save was successful
                    CompoundTag verifyData = forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data");
                    if (verifyData.isEmpty()) {
                        MineColonyTax.LOGGER.error("Failed to save war data to persistent storage for player " + player.getName().getString());
                    } else {
                        MineColonyTax.LOGGER.debug("Updated persistent data for player " + 
                            player.getName().getString() + ": " + nbt);
                    }
                    
                    // Set a flag to indicate data has changed
                    player.getPersistentData().putBoolean("minecolonytax:data_changed", true);
                    
                    // Mark that data needs to be saved - the save will be handled automatically by Minecraft
                    player.getPersistentData().putBoolean("minecolonytax:data_changed", true);
                    MineColonyTax.LOGGER.debug("Marked player data as dirty for " + player.getName().getString());
                } catch (Exception e) {
                    MineColonyTax.LOGGER.error("Error updating persistent data: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Failed to save player data: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 