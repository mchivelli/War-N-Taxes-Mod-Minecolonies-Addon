package net.machiavelli.minecolonytax.data;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.attachment.PlayerWarDataAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;

/**
 * Manager for player war statistics using NeoForge Data Attachments.
 * Data is automatically persisted - no need for manual markDirty() calls.
 */
public class PlayerWarDataManager {

    /**
     * Increment the number of players killed in war for a player
     *
     * @param player The player who killed someone
     */
    public static void incrementPlayersKilledInWar(ServerPlayer player) {
        PlayerWarData data = PlayerWarDataAttachment.get(player);
        int oldValue = data.getPlayersKilledInWar();
        data.incrementPlayersKilledInWar();
        int newValue = data.getPlayersKilledInWar();
        MineColonyTax.LOGGER.info("📊 STAT UPDATE: {} players killed in war: {} -> {}", 
            player.getName().getString(), oldValue, newValue);
        updateScoreboard(player, "playersKilled", newValue);
        // Data is automatically saved by NeoForge attachments
    }

    /**
     * Increment the number of colonies raided by the player
     *
     * @param player The player who raided a colony
     */
    public static void incrementRaidedColonies(ServerPlayer player) {
        PlayerWarData data = PlayerWarDataAttachment.get(player);
        int oldValue = data.getRaidedColonies();
        data.incrementRaidedColonies();
        int newValue = data.getRaidedColonies();
        MineColonyTax.LOGGER.info("📊 STAT UPDATE: {} raided colonies: {} -> {}", 
            player.getName().getString(), oldValue, newValue);
        updateScoreboard(player, "raidsCompleted", newValue);
        // Data is automatically saved by NeoForge attachments
    }

    /**
     * Add to the total amount raided by the player
     *
     * @param player The player who raided
     * @param amount The amount raided
     */
    public static void addAmountRaided(ServerPlayer player, long amount) {
        PlayerWarData data = PlayerWarDataAttachment.get(player);
        long oldValue = data.getAmountRaided();
        data.addAmountRaided(amount);
        long newValue = data.getAmountRaided();
        MineColonyTax.LOGGER.info("📊 STAT UPDATE: {} amount raided: {} -> {} (+{})", 
            player.getName().getString(), oldValue, newValue, amount);
        updateScoreboard(player, "amountRaided", (int)newValue);
        // Data is automatically saved by NeoForge attachments
    }

    /**
     * Increment the number of wars won by the player
     *
     * @param player The player who won a war
     */
    public static void incrementWarsWon(ServerPlayer player) {
        PlayerWarData data = PlayerWarDataAttachment.get(player);
        int oldValue = data.getWarsWon();
        data.incrementWarsWon();
        int newValue = data.getWarsWon();
        MineColonyTax.LOGGER.info("📊 STAT UPDATE: {} wars won: {} -> {}", 
            player.getName().getString(), oldValue, newValue);
        updateScoreboard(player, "warsWon", newValue);
        // Data is automatically saved by NeoForge attachments
    }

    /**
     * Increment the number of war stalemates the player was involved in
     *
     * @param player The player involved in a stalemated war
     */
    public static void incrementWarStalemates(ServerPlayer player) {
        PlayerWarData data = PlayerWarDataAttachment.get(player);
        int oldValue = data.getWarStalemates();
        data.incrementWarStalemates();
        int newValue = data.getWarStalemates();
        MineColonyTax.LOGGER.info("📊 STAT UPDATE: {} war stalemates: {} -> {}", 
            player.getName().getString(), oldValue, newValue);
        updateScoreboard(player, "warStalemates", newValue);
        // Data is automatically saved by NeoForge attachments
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
} 