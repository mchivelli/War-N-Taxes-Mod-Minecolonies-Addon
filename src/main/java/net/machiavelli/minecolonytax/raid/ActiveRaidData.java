package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.TimerTask;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public class ActiveRaidData {
    private static final Logger LOGGER = LogManager.getLogger();
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
    
    // Snapshot of original guard citizens at raid start
    private final Set<Integer> originalGuardIds = new HashSet<>();
    private final Set<Integer> killedGuardIds = new HashSet<>();
    
    // Boundary enforcement tracking
    private boolean hasLeftBoundaries = false;
    private long timeLeftBoundaries = 0;
    private int potentialStolenAmount = 0;

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
            // Add the RAIDER to the boss bar first so they can see the timer
            ServerPlayer raiderPlayer = colony.getWorld().getServer().getPlayerList().getPlayer(raider);
            if (raiderPlayer != null) {
                this.bossEvent.addPlayer(raiderPlayer);
                LOGGER.info("🎯 BOSS BAR: Added raider {} to boss bar for colony {}", raiderPlayer.getName().getString(), colony.getName());
            } else {
                LOGGER.warn("⚠ BOSS BAR: Could not add raider {} to boss bar - player is NULL!", raider);
            }
            
            // Then add colony allies to boss bar: Owner, Officers, and Friends
            IPermissions perms = colony.getPermissions();
            colony.getPermissions().getPlayers().keySet().forEach(uuid -> {
                // Only add colony allies to boss bar: Owner, Officers, and Friends
                // Excludes: Hostile and Neutral players (and raider already added above)
                if (!uuid.equals(raider)) { // Don't add raider twice
                    Rank rank = perms.getRank(uuid);
                    if (rank != null && (rank.equals(perms.getRankOwner()) || 
                                        rank.equals(perms.getRankOfficer()) || 
                                        rank.equals(perms.getRankFriend()))) {
                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (player != null) {
                            this.bossEvent.addPlayer(player);
                        }
                    }
                }
            });
            LOGGER.info("🎯 BOSS BAR: Initialized for colony {} with raider {} (Boss bar visible: {})", 
                colony.getName(), raiderPlayer != null ? raiderPlayer.getName().getString() : "NULL", this.bossEvent.isVisible());
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
    
    // Guard snapshot APIs
    public void snapshotOriginalGuardIds() {
        if (colony == null || colony.getCitizenManager() == null) {
            return;
        }
        originalGuardIds.clear();
        colony.getCitizenManager().getCitizens().forEach(c -> {
            boolean isGuard = false;
            var job = c.getJob();
            if (job != null && job.isGuard()) {
                isGuard = true;
            } else if (c.getWorkBuilding() != null) {
                String name = c.getWorkBuilding().getBuildingDisplayName().toLowerCase();
                if (name.contains("guard") || name.contains("barracks") || name.contains("archery") || name.contains("combat")) {
                    isGuard = true;
                }
            }
            if (isGuard) {
                originalGuardIds.add(c.getId());
            }
        });
    }
    
    public boolean isOriginalGuard(int citizenId) {
        return originalGuardIds.contains(citizenId);
    }
    
    public boolean markGuardKilled(int citizenId) {
        if (!isOriginalGuard(citizenId)) {
            return false;
        }
        if (killedGuardIds.add(citizenId)) {
            // Keep legacy counter in sync for other systems
            incrementGuardsKilled();
            return true;
        }
        return false;
    }
    
    public int getOriginalGuardCount() { return originalGuardIds.size(); }
    public int getKilledGuardCount() { return killedGuardIds.size(); }
    public Set<Integer> getOriginalGuardIds() { return originalGuardIds; }
    public Set<Integer> getKilledGuardIds() { return killedGuardIds; }
    
    // Boundary enforcement methods
    public boolean hasLeftBoundaries() {
        return hasLeftBoundaries;
    }
    
    public void markLeftBoundaries() {
        if (!hasLeftBoundaries) {
            this.hasLeftBoundaries = true;
            this.timeLeftBoundaries = System.currentTimeMillis();
        }
    }
    
    public long getTimeLeftBoundaries() {
        return timeLeftBoundaries;
    }
    
    public boolean isEligibleForRewards() {
        return !hasLeftBoundaries && hasKilledAnyGuards();
    }
    
    public void setPotentialStolenAmount(int amount) {
        this.potentialStolenAmount = amount;
    }
    
    public int getPotentialStolenAmount() {
        return potentialStolenAmount;
    }
}