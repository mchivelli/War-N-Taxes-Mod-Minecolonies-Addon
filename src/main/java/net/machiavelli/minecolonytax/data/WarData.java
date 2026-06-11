package net.machiavelli.minecolonytax.data;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.peace.PeaceProposal;
import net.minecraft.server.level.ServerBossEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.machiavelli.minecolonytax.util.TickScheduler;
import net.minecraft.world.entity.Entity;

public class WarData {

    /**
     * Militia-upgrade reinforcements spawned at INWAR transition. Transient —
     * not persisted across server restart (entities can't be either; a restored
     * war is on the operator to manually re-spawn if desired). Tracked here so
     * they can be cleanly despawned in {@code WarSystem.endWar}. By design
     * these do NOT count toward victory; only real guards + player lives do.
     */
    public final Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
    private final UUID warID;
    private final UUID attacker;
    private final UUID defender;
    private final UUID attackerTeamID;
    private final UUID defenderTeamID;
    private final IColony colony;
    public long warStartTime;
    public long joinPhaseEndTime;
    private final Map<UUID, Integer> attackerLives = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> defenderLives = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<Integer> defenderGuardIDs = ConcurrentHashMap.newKeySet();
    private final Set<Integer> attackerGuardIDs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> attackerAllies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defenderAllies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> lastLifeInventoryPreservation = ConcurrentHashMap.newKeySet();

    public long countdownTaskId = -1;
    public long warChestDrainTaskId = -1;
    /** Join-phase countdown-sound timer. Captured so it can be cancelled when the
     *  join phase ends — otherwise the repeating task leaks forever (audit C4). */
    public long joinCountdownTaskId = -1;
    /** Main delayed JOINING->INWAR start timer. Captured so endWar() can cancel it and
     *  so it can't resurrect a war that ended during the join phase (audit C4 follow-up). */
    public long joinStartTaskId = -1;
    public ServerBossEvent bossEvent;
    public ServerBossEvent alliesBossEvent;
    private String penaltyReport = "";

    private final Set<UUID> acceptedAllies = new HashSet<>();
    private final Set<UUID> declinedAllies = new HashSet<>();

    public Map<Action, Boolean> originalHostilePerms;
    public Map<Action, Boolean> originalHostilePermsForAttacker;

    public int totalGuards;
    public int remainingGuards;
    private PeaceProposal activeProposal;
    private boolean stalemateTriggered;
    private boolean offlineOutpostWar = false;
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

        // Track defender and attacker guards in separate sets to prevent ID collisions
        // (MineColonies assigns sequential per-colony citizen IDs, so colony A and B
        // can both have a guard with citizen ID 3)
        initializeGuards(colony, defenderGuardIDs);
        if (attackerColony != null) {
            initializeGuards(attackerColony, attackerGuardIDs);
        }
    }

    /**
     * Restoration constructor - used when loading saved wars from disk after a server restart.
     * Does NOT recalculate guards or generate a new warID; all values come from the save file.
     *
     * <p><b>Parameter count: 34.</b> (was 28 — increased 2026-05-25 to include the
     * previously-dropped originalHostilePerms / originalHostilePermsForAttacker /
     * acceptedAllies / declinedAllies / offlineOutpostWar / activeProposal fields.)
     *
     * <p>When modifying WarData, also update this constructor AND the matching
     * WarSaveEntry serialization in WarSystem so persisted wars round-trip correctly.
     * TODO: add a CI test that builds a WarData, serializes it, deserializes it,
     * and asserts equality (no field silently dropped).
     */
    public WarData(UUID warID, UUID attacker, UUID defender, UUID attackerTeamID, UUID defenderTeamID,
                   long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
                   IColony colony, IColony attackerColony, WarStatus status, boolean accepted,
                   int initialAttackerGuards, int remainingAttackerGuards,
                   int initialDefenderGuards, int remainingDefenderGuards,
                   int initialAttackerTotalLives, int initialDefenderTotalLives,
                   Map<UUID, Integer> attackerLivesData, Map<UUID, Integer> defenderLivesData,
                   Set<Integer> defenderGuardIDsData, Set<Integer> attackerGuardIDsData,
                   Set<UUID> attackerAlliesData, Set<UUID> defenderAlliesData,
                   Set<UUID> spectatorsData, Set<UUID> lastLifeData,
                   String penaltyReport, boolean stalemateTriggered,
                   Map<Action, Boolean> originalHostilePerms,
                   Map<Action, Boolean> originalHostilePermsForAttacker,
                   Set<UUID> acceptedAlliesData, Set<UUID> declinedAlliesData,
                   boolean offlineOutpostWar,
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
        if (defenderGuardIDsData != null) this.defenderGuardIDs.addAll(defenderGuardIDsData);
        if (attackerGuardIDsData != null) this.attackerGuardIDs.addAll(attackerGuardIDsData);
        if (attackerAlliesData != null) this.attackerAllies.addAll(attackerAlliesData);
        if (defenderAlliesData != null) this.defenderAllies.addAll(defenderAlliesData);
        if (spectatorsData != null) this.spectators.addAll(spectatorsData);
        if (lastLifeData != null) this.lastLifeInventoryPreservation.addAll(lastLifeData);
        if (originalHostilePerms != null) this.originalHostilePerms = new java.util.HashMap<>(originalHostilePerms);
        if (originalHostilePermsForAttacker != null) this.originalHostilePermsForAttacker = new java.util.HashMap<>(originalHostilePermsForAttacker);
        if (acceptedAlliesData != null) this.acceptedAllies.addAll(acceptedAlliesData);
        if (declinedAlliesData != null) this.declinedAllies.addAll(declinedAlliesData);
        this.offlineOutpostWar = offlineOutpostWar;
        this.activeProposal = activeProposal;
    }

    private void initializeGuards(IColony colony, Set<Integer> targetSet) {
        colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .forEach(citizen -> targetSet.add(citizen.getId()));
    }

    public void setStalemateTriggered(boolean stalemateTriggered) { this.stalemateTriggered = stalemateTriggered; }

    public boolean isOfflineOutpostWar() { return offlineOutpostWar; }
    public void setOfflineOutpostWar(boolean offlineOutpostWar) { this.offlineOutpostWar = offlineOutpostWar; }

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
    public Set<Integer> getDefenderGuardIDs() { return defenderGuardIDs; }
    public Set<Integer> getAttackerGuardIDs() { return attackerGuardIDs; }
    public boolean isJoinPhaseActive() { return System.currentTimeMillis() < joinPhaseEndTime; }
    public boolean isWarTimeExpired() { return System.currentTimeMillis() - warStartTime > TimeUnit.MINUTES.toMillis(TaxConfig.WAR_DURATION_MINUTES.get()); }
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
