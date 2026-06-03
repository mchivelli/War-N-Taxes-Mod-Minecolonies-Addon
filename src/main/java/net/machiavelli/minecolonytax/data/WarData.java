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

    /**
     * Restoration constructor used by {@code WarSystem.loadAndResumeActiveWars()} to rebuild a
     * persisted war from disk. Unlike the primary constructor it does NOT recompute guard counts
     * or guard IDs from live citizens — all state is supplied from the save entry.
     *
     * <p>Note: this NeoForge port's data model carries a single {@code guardIDs} set (the 1.20.1
     * line splits attacker/defender) and has no {@code offlineOutpostWar} flag, so this signature
     * intentionally differs from the 1.20.1 27-parameter constructor.
     */
    public WarData(UUID warID, UUID attacker, UUID defender, UUID attackerTeamID, UUID defenderTeamID,
                   long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
                   IColony colony, IColony attackerColony, WarStatus status, boolean accepted,
                   int initialAttackerGuards, int remainingAttackerGuards,
                   int initialDefenderGuards, int remainingDefenderGuards,
                   int initialAttackerTotalLives, int initialDefenderTotalLives,
                   Map<UUID, Integer> attackerLivesData, Map<UUID, Integer> defenderLivesData,
                   Set<Integer> guardIDsData,
                   Set<UUID> attackerAlliesData, Set<UUID> defenderAlliesData,
                   Set<UUID> spectatorsData, Set<UUID> lastLifeData,
                   String penaltyReport, boolean stalemateTriggered,
                   Map<Action, Boolean> originalHostilePerms,
                   Map<Action, Boolean> originalHostilePermsForAttacker,
                   Set<UUID> acceptedAlliesData, Set<UUID> declinedAlliesData,
                   PeaceProposal activeProposal) {
        this.warID = warID;
        this.attacker = attacker;
        this.defender = defender;
        this.attackerTeamID = attackerTeamID;
        this.defenderTeamID = defenderTeamID;
        this.colony = colony;
        this.attackerColony = attackerColony;
        this.warStartTime = warStartTime;
        this.joinPhaseEndTime = joinPhaseEndTime;
        this.bossEvent = bossEvent;
        this.status = status;
        this.accepted = accepted;
        this.initialAttackerGuards = initialAttackerGuards;
        this.remainingAttackerGuards = remainingAttackerGuards;
        this.initialDefenderGuards = initialDefenderGuards;
        this.remainingDefenderGuards = remainingDefenderGuards;
        this.initialAttackerTotalLives = initialAttackerTotalLives;
        this.initialDefenderTotalLives = initialDefenderTotalLives;
        this.penaltyReport = penaltyReport != null ? penaltyReport : "";
        this.stalemateTriggered = stalemateTriggered;
        if (attackerLivesData != null) this.attackerLives.putAll(attackerLivesData);
        if (defenderLivesData != null) this.defenderLives.putAll(defenderLivesData);
        if (guardIDsData != null) this.guardIDs.addAll(guardIDsData);
        if (attackerAlliesData != null) this.attackerAllies.addAll(attackerAlliesData);
        if (defenderAlliesData != null) this.defenderAllies.addAll(defenderAlliesData);
        if (spectatorsData != null) this.spectators.addAll(spectatorsData);
        if (lastLifeData != null) this.lastLifeInventoryPreservation.addAll(lastLifeData);
        if (originalHostilePerms != null) this.originalHostilePerms = new HashMap<>(originalHostilePerms);
        if (originalHostilePermsForAttacker != null) this.originalHostilePermsForAttacker = new HashMap<>(originalHostilePermsForAttacker);
        if (acceptedAlliesData != null) this.acceptedAllies.addAll(acceptedAlliesData);
        if (declinedAlliesData != null) this.declinedAllies.addAll(declinedAlliesData);
        this.activeProposal = activeProposal;
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
