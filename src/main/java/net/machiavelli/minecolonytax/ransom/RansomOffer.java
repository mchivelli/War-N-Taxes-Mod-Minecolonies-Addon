package net.machiavelli.minecolonytax.ransom;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A single pending ransom offer — an immutable identity plus a CAS-guarded state machine.
 *
 * <p>State machine: {@code PENDING → ACCEPTED | DENIED | CANCELLED | EXPIRED}.
 * {@link #resolve(State)} transitions atomically exactly once; a second resolution
 * attempt (double-clicked chat button, command spam, tick-expiry racing a click)
 * returns {@code false} and has no effect.
 *
 * <p>No Minecraft imports in here on purpose — the lifecycle is unit-testable.
 */
public final class RansomOffer {

    /** Terminal states end the offer; only {@link #PENDING} is live. */
    public enum State {
        PENDING,
        ACCEPTED,
        DENIED,
        CANCELLED,
        EXPIRED
    }

    public final UUID attackerUUID;
    public final UUID victimUUID;
    public final int colonyId;
    public final int amount;
    public final long createdAtMs;
    public final long expiresAtMs;
    public final ConflictType conflictType;

    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

    public RansomOffer(UUID attackerUUID, UUID victimUUID, int colonyId, int amount,
                       long createdAtMs, long expiresAtMs, ConflictType conflictType) {
        this.attackerUUID = attackerUUID;
        this.victimUUID = victimUUID;
        this.colonyId = colonyId;
        this.amount = amount;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
        this.conflictType = conflictType;
    }

    /**
     * Atomically moves the offer from {@link State#PENDING} to the given terminal state.
     *
     * @param target a terminal state (not {@code PENDING})
     * @return {@code true} if this call performed the transition; {@code false} if the
     *         offer was already resolved
     * @throws IllegalArgumentException if {@code target} is {@code PENDING}
     */
    public boolean resolve(State target) {
        if (target == State.PENDING) {
            throw new IllegalArgumentException("Cannot resolve an offer back to PENDING");
        }
        return state.compareAndSet(State.PENDING, target);
    }

    public State getState() {
        return state.get();
    }

    public boolean isPending() {
        return state.get() == State.PENDING;
    }

    /** True once the wall clock has passed the offer deadline (state-independent check). */
    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAtMs;
    }
}
