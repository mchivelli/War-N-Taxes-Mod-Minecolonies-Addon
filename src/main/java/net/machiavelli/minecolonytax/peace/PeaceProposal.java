package net.machiavelli.minecolonytax.peace;

import java.util.UUID;

public class PeaceProposal {
    // SURRENDER = full capitulation: the proposer concedes total defeat. On accept, the
    // opposing side wins as if by total victory (conquest/vassalize of the loser's colony).
    public enum Type { WHITEPEACE, REPARATIONS, SURRENDER }

    private final Type type;
    private final int amount;
    private final UUID proposer;
    private final long createdTime;

    public PeaceProposal(Type type, int amount, UUID proposer) {
        this.type = type;
        this.amount = amount;
        this.proposer = proposer;
        this.createdTime = System.currentTimeMillis();
    }

    public Type getType() {
        return type;
    }
    public int getAmount() {
        return amount;
    }

    public UUID getProposer() {
        return proposer;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - createdTime > timeoutMillis;
    }

    @Override
    public String toString() {
        return type.name();
    }
}
