package net.machiavelli.minecolonytax.data;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.capability.PlayerWarDataCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;

public class PlayerWarDataManager {

    public static void incrementPlayersKilledInWar(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.incrementPlayersKilledInWar();
            updateScoreboard(player, "playersKilled", data.getPlayersKilledInWar());
            markDirty(player);
        });
    }

    public static void incrementRaidedColonies(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.incrementRaidedColonies();
            updateScoreboard(player, "raidsCompleted", data.getRaidedColonies());
            markDirty(player);
        });
    }

    public static void addAmountRaided(ServerPlayer player, long amount) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.addAmountRaided(amount);
            updateScoreboard(player, "amountRaided", (int) data.getAmountRaided());
            markDirty(player);
        });
    }

    public static void incrementTimesKilledInWar(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.incrementTimesKilledInWar();
            updateScoreboard(player, "timesKilled", data.getTimesKilledInWar());
            markDirty(player);
        });
    }

    public static void incrementWarsLost(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.incrementWarsLost();
            updateScoreboard(player, "warsLost", data.getWarsLost());
            markDirty(player);
        });
    }

    public static void incrementWarsWon(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.incrementWarsWon();
            updateScoreboard(player, "warsWon", data.getWarsWon());
            markDirty(player);
        });
    }

    public static void incrementWarStalemates(ServerPlayer player) {
        PlayerWarDataCapability.get(player).ifPresent(data -> {
            data.incrementWarStalemates();
            updateScoreboard(player, "warStalemates", data.getWarStalemates());
            markDirty(player);
        });
    }

    private static void updateScoreboard(ServerPlayer player, String objective, int value) {
        if (player.getServer() != null) {
            Scoreboard sb = player.getServer().getScoreboard();
            var obj = sb.getObjective(objective);
            if (obj != null) {
                sb.getOrCreatePlayerScore(player.getName().getString(), obj).setScore(value);
            }
        }
    }
    
    private static void markDirty(ServerPlayer player) {
        try {
            PlayerWarDataCapability.get(player).ifPresent(data -> {
                try {
                    CompoundTag nbt = data.serializeNBT();
                    CompoundTag persistentData = player.getPersistentData();
                    if (!persistentData.contains("ForgeData")) {
                        persistentData.put("ForgeData", new CompoundTag());
                    }
                    CompoundTag forgeData = persistentData.getCompound("ForgeData");
                    forgeData.put(MineColonyTax.MOD_ID + "_war_data", nbt);
                    player.getPersistentData().putBoolean("minecolonytax:data_changed", true);
                } catch (Exception e) {
                    MineColonyTax.LOGGER.error("Error updating persistent data: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Failed to save player data: " + e.getMessage());
        }
    }
} 
