package net.machiavelli.minecolonytax.data;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.peace.PeaceProposal;
import net.minecraft.server.level.ServerBossEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class WarData {
    private final UUID warID;
    private final UUID attacker;
    private final UUID defender;
    private final UUID attackerTeamID;
    private final UUID defenderTeamID;
    private final IColony colony;
    // warStartTime is initially the join-phase start time; it is reset when war officially begins.
    public long warStartTime;
    public long joinPhaseEndTime;
    private final Map<UUID, Integer> attackerLives = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> defenderLives = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<Integer> guardIDs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> attackerAllies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defenderAllies = ConcurrentHashMap.newKeySet();
    // Set to track players who should keep their inventory on last life
    private final Set<UUID> lastLifeInventoryPreservation = ConcurrentHashMap.newKeySet();

    public TimerTask timerTask;
    public ServerBossEvent bossEvent;
    public ServerBossEvent alliesBossEvent;
    private String penaltyReport = "";

    // Tracking ally join responses
    private final Set<UUID> acceptedAllies = new HashSet<>();
    private final Set<UUID> declinedAllies = new HashSet<>();

    public Map<Action, Boolean> originalHostilePerms;
    public Map<Action, Boolean> originalHostilePermsForAttacker;

    public int totalGuards;
    public int remainingGuards;
    private PeaceProposal activeProposal;
    private boolean stalemateTriggered;
    public enum WarStatus { JOINING, INWAR, ERROR }
    private WarStatus status;
    private boolean accepted = false;
    private final IColony attackerColony;
    public final int initialAttackerGuards;
    public int remainingAttackerGuards;
    public final int initialDefenderGuards;
    public int remainingDefenderGuards;
    public int initialAttackerTotalLives;
    public int initialDefenderTotalLives;

    public WarData(UUID attacker, UUID defender, UUID attackerTeamID, UUID defenderTeamID,
                   long joinPhaseStart, ServerBossEvent bossEvent, IColony colony, IColony attackerColony) {
        this.attacker = attacker;
        this.defender = defender;
        this.warID = UUID.randomUUID();
        this.attackerTeamID = attackerTeamID;
        this.defenderTeamID = defenderTeamID;
        this.colony = colony;
        this.attackerColony = attackerColony;
        this.warStartTime = joinPhaseStart;
        this.joinPhaseEndTime = joinPhaseStart + TimeUnit.MINUTES.toMillis(TaxConfig.JOIN_PHASE_DURATION_MINUTES.get());
        this.bossEvent = bossEvent;
        this.status = WarStatus.JOINING;

        // Calculate guard counts
        this.initialDefenderGuards = colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .mapToInt(c -> 1)
                .sum();
        this.remainingDefenderGuards = initialDefenderGuards;
        this.initialAttackerGuards = attackerColony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .mapToInt(c -> 1)
                .sum();
        this.remainingAttackerGuards = initialAttackerGuards;

        // Track defender and attacker guards for proper recognition during the war
        initializeGuards(colony); // Defender guards
        if (attackerColony != null) {
            initializeGuards(attackerColony); // Attacker guards
        }
    }

    private void initializeGuards(IColony colony) {
        colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .forEach(citizen -> guardIDs.add(citizen.getId()));
    }

    public void setActiveProposal(PeaceProposal proposal) {
        this.activeProposal = proposal;
    }
    public void setPenaltyReport(String report) {
        this.penaltyReport = report;
    }
    public String getPenaltyReport() {
        return penaltyReport;
    }
    public UUID getAttacker() { return attacker; }
    public UUID getDefender() { return defender; }
    public WarStatus getStatus() { return status; }
    public void setStatus(WarStatus status) { this.status = status; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public int getRemainingAttackerGuards() { return remainingAttackerGuards; }
    public int getRemainingDefenderGuards() { return remainingDefenderGuards; }
    public IColony getAttackerColony() { return attackerColony; }
    public UUID getWarID() { return warID; }
    public UUID getAttackerTeamID() { return attackerTeamID; }
    public UUID getDefenderTeamID() { return defenderTeamID; }
    public IColony getColony() { return colony; }
    public Map<UUID, Integer> getAttackerLives() { return attackerLives; }
    public Map<UUID, Integer> getDefenderLives() { return defenderLives; }
    public Set<UUID> getSpectators() { return spectators; }
    public Set<Integer> getGuardIDs() { return guardIDs; }
    public boolean isJoinPhaseActive() { return System.currentTimeMillis() < joinPhaseEndTime; }
    public boolean isWarTimeExpired() { return System.currentTimeMillis() - warStartTime > TimeUnit.HOURS.toMillis(2); }
    public boolean isStalemateTriggered() { return stalemateTriggered; }
    public PeaceProposal getActiveProposal() { return activeProposal; }
    public Set<UUID> getAcceptedAllies() { return acceptedAllies; }
    public Set<UUID> getDeclinedAllies() { return declinedAllies; }
    public long getJoinPhaseEndTime() { return joinPhaseEndTime; }
    public void setJoinPhaseEndTime(long joinPhaseEndTime) { this.joinPhaseEndTime = joinPhaseEndTime; }
    public Set<UUID> getAttackerAllies() { return attackerAllies; }
    public Set<UUID> getDefenderAllies() { return defenderAllies; }
    public Set<UUID> getLastLifeInventoryPreservation() { return lastLifeInventoryPreservation; }
}
