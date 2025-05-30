package net.machiavelli.minecolonytax.peace;

import java.util.UUID;

public class PeaceProposal {
    public enum Type { WHITEPEACE, REPARATIONS }

    private final Type type;
    private final int amount;
    private final UUID proposer;

    public PeaceProposal(Type type, int amount, UUID proposer) {
        this.type = type;
        this.amount = amount;
        this.proposer = proposer;
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

    @Override
    public String toString() {
        return type.name();
    }
}