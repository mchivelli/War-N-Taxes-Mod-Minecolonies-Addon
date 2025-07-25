package net.machiavelli.minecolonytax.pvp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BattleRequest {
    private final UUID challengerId;
    private final List<UUID> targetPlayers;
    private final int amount;
    private final String mapName;
    private final long expiryTime;

    public BattleRequest(UUID challengerId, List<UUID> targetPlayers, int amount, String mapName) {
        this.challengerId = challengerId;
        this.targetPlayers = new ArrayList<>(targetPlayers);
        this.amount = amount;
        this.mapName = mapName;
        this.expiryTime = System.currentTimeMillis() + 60000; // 1 minute expiry
    }

    public UUID getChallengerId() { return challengerId; }
    public List<UUID> getTargetPlayers() { return targetPlayers; }
    public int getAmount() { return amount; }
    public String getMapName() { return mapName; }
    public boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
} 