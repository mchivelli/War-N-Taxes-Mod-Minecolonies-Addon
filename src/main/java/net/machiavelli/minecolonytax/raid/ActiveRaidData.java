package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.TimerTask;
import java.util.UUID;

public class ActiveRaidData {
    final UUID raider;
    final IColony colony;
    private IColony raiderColony; // The colony of the raider (attacker)
    final ServerBossEvent bossEvent;
    int elapsedSeconds;
    TimerTask timerTask;
    boolean isActive;
    boolean warningSent;
    long totalTransferred;
    
    // Guard kill tracking for revenue calculation
    int totalGuards;
    int guardsKilled;
    boolean guardsInitialized;

    public ActiveRaidData(UUID raider, IColony colony, ServerBossEvent bossEvent, TimerTask timerTask) {
        this.raider     = raider;
        this.colony     = colony;
        this.bossEvent  = bossEvent;
        this.timerTask  = timerTask;
        this.totalTransferred = 0L;
        this.guardsKilled = 0;
        this.guardsInitialized = false;
    }

    public ActiveRaidData(UUID raider, IColony colony) {
        this.raider = raider;
        this.colony = colony;
        this.bossEvent = new ServerBossEvent(
                Component.literal("Raid in Progress: " + colony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        this.bossEvent.setProgress(0.0f);
        this.bossEvent.setVisible(true);
        this.elapsedSeconds = 0;
        this.isActive = true;
        this.warningSent = false;
        this.totalTransferred   = 0L;
        this.guardsKilled = 0;
        this.guardsInitialized = false;
        if (colony != null && colony.getPermissions() != null && colony.getWorld() != null && colony.getWorld().getServer() != null) {
            colony.getPermissions().getPlayers().keySet().forEach(uuid -> {
                ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    this.bossEvent.addPlayer(player);
                }
            });
        }
    }

    public long getTotalTransferred() { return totalTransferred; }
    public void addToTotalTransferred(long amt) { this.totalTransferred += amt; }


    public UUID getRaider() {
        return raider;
    }

    public IColony getColony() {
        return colony;
    }

    public ServerBossEvent getBossEvent() {
        return bossEvent;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void setElapsedSeconds(int elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    public TimerTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(TimerTask timerTask) {
        this.timerTask = timerTask;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isWarningSent() {
        return warningSent;
    }

    public void setWarningSent(boolean warningSent) {
        this.warningSent = warningSent;
    }
    
    public IColony getRaiderColony() {
        return raiderColony;
    }
    
    public void setRaiderColony(IColony raiderColony) {
        this.raiderColony = raiderColony;
    }
    
    // Guard kill tracking methods
    public int getTotalGuards() { return totalGuards; }
    public int getGuardsKilled() { return guardsKilled; }
    public boolean areGuardsInitialized() { return guardsInitialized; }
    
    public void initializeGuardCount(int totalGuards) {
        this.totalGuards = totalGuards;
        this.guardsInitialized = true;
    }
    
    public void incrementGuardsKilled() {
        this.guardsKilled++;
    }
    
    public double getGuardKillPercentage() {
        if (totalGuards <= 0) return 0.0;
        return (double) guardsKilled / totalGuards;
    }
    
    public boolean hasKilledAnyGuards() {
        return guardsKilled > 0;
    }
}