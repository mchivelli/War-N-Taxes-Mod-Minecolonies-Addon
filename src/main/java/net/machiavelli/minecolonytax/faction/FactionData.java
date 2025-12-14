package net.machiavelli.minecolonytax.faction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FactionData {
    private final UUID id;
    private String name;
    private int ownerColonyId;
    private final Set<Integer> memberColonyIds;
    private final Map<UUID, FactionRelation> relations;

    // Shared Tax Pool
    private long taxBalance;
    private double taxRate; // 0.0 to 1.0 (percent of colony tax diverted to pool)

    // Pending Invites (ColonyID -> Timestamp)
    private final Map<Integer, Long> pendingInvites;

    public FactionData(UUID id, String name, int ownerColonyId) {
        this.id = id;
        this.name = name;
        this.ownerColonyId = ownerColonyId;
        this.memberColonyIds = new HashSet<>();
        this.relations = new HashMap<>();
        this.pendingInvites = new HashMap<>();
        this.memberColonyIds.add(ownerColonyId); // Owner is always a member
        this.taxBalance = 0;
        this.taxRate = 0.10; // Default 10%
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOwnerColonyId() {
        return ownerColonyId;
    }

    public void setOwnerColonyId(int ownerColonyId) {
        this.ownerColonyId = ownerColonyId;
    }

    public Set<Integer> getMemberColonyIds() {
        return memberColonyIds;
    }

    public void addMember(int colonyId) {
        this.memberColonyIds.add(colonyId);
    }

    public void removeMember(int colonyId) {
        this.memberColonyIds.remove(colonyId);
    }

    public boolean isMember(int colonyId) {
        return this.memberColonyIds.contains(colonyId);
    }

    public Map<UUID, FactionRelation> getRelations() {
        return relations;
    }

    public FactionRelation getRelation(UUID factionId) {
        return relations.getOrDefault(factionId, FactionRelation.NEUTRAL);
    }

    public void setRelation(UUID factionId, FactionRelation relation) {
        if (relation == FactionRelation.NEUTRAL) {
            relations.remove(factionId);
        } else {
            relations.put(factionId, relation);
        }
    }

    public long getTaxBalance() {
        return taxBalance;
    }

    public void setTaxBalance(long taxBalance) {
        this.taxBalance = taxBalance;
    }

    public void addTax(long amount) {
        this.taxBalance += amount;
    }

    public boolean withdrawTax(long amount) {
        if (this.taxBalance >= amount) {
            this.taxBalance -= amount;
            return true;
        }
        return false;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0.0, Math.min(1.0, taxRate));
    }

    public Map<Integer, Long> getPendingInvites() {
        return pendingInvites;
    }

    public void addInvite(int colonyId) {
        this.pendingInvites.put(colonyId, System.currentTimeMillis());
    }

    public void removeInvite(int colonyId) {
        this.pendingInvites.remove(colonyId);
    }

    public boolean hasInvite(int colonyId) {
        // Simple check, could add expiration logic here later
        return this.pendingInvites.containsKey(colonyId);
    }
}
