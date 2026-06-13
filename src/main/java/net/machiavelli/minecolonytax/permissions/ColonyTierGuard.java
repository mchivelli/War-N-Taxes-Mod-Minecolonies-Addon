package net.machiavelli.minecolonytax.permissions;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.FirstColonyTracker;
import net.machiavelli.minecolonytax.TaxConfig;

import java.util.UUID;

/**
 * Central guard for colony ownership-transfer decisions.
 *
 * Every code path that flips a colony's deed to a new player must route through
 * {@link #canTransferOwnership(IColony)}. Primary colonies (a player's first
 * colony, per {@link FirstColonyTracker}) are protected by default and only
 * transferable when {@code EnablePrimaryColonyTransfer} is set to {@code true}
 * in the config.
 *
 * Vassalage is intentionally NOT gated here — losing a war can still vassalize
 * a primary colony (the loser pays tribute) without the deed moving. Only
 * permanent ownership changes flow through this guard.
 */
public final class ColonyTierGuard {

    private ColonyTierGuard() {}

    /**
     * Whether the colony's ownership may be transferred to a new player.
     *
     * @param colony the colony in question (may be null — returns false)
     * @return true when transfer is permitted; false when the colony is a
     *         primary and {@code EnablePrimaryColonyTransfer} is off
     */
    public static boolean canTransferOwnership(IColony colony) {
        if (colony == null) {
            return false;
        }
        // Permissions.getOwner() can be stale, null, or hold a placeholder UUID
        // from the abandonment system. Use the FCT reverse lookup FIRST — it
        // tracks the true first-colony owner regardless of permissions state —
        // then fall back to the permissions owner only when FCT has no record.
        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
        if (trackedFirstOwner != null) {
            return TaxConfig.isPrimaryColonyTransferEnabled();
        }
        UUID currentOwner = colony.getPermissions().getOwner();
        if (currentOwner == null) {
            return true;
        }
        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
            return TaxConfig.isPrimaryColonyTransferEnabled();
        }
        return true;
    }

    /**
     * Whether a besiege victory may convert into a permanent ownership claim
     * (as opposed to ongoing tax-occupation).
     *
     * Same rules as {@link #canTransferOwnership(IColony)}; named separately so
     * besiege code reads clearly.
     */
    public static boolean canBesiegePermanentClaim(IColony colony) {
        return canTransferOwnership(colony);
    }

    /**
     * Human-readable explanation for why a transfer was denied, suitable for
     * logging or for messaging the player who attempted the action.
     */
    public static String getTransferDenialReason(IColony colony) {
        if (colony == null) {
            return "Colony reference is null.";
        }
        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
        UUID owner = colony.getPermissions().getOwner();
        if (trackedFirstOwner != null
                || (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID()))) {
            return colony.getName() + " is a Primary colony — ownership transfer is blocked by config "
                    + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
        }
        return "Transfer denied (no specific reason).";
    }

    /**
     * Documented exemptions from the central guard. These are NOT war-time
     * player-to-player transfers — they're system-owner placeholder flows:
     *   - {@code ColonyAbandonmentManager} sets a fake owner UUID when a
     *     colony is auto-abandoned (the colony is owner-less in spirit).
     *   - {@code ColonyClaimingRaidManager} flips ownership when a player
     *     successfully claims a previously abandoned colony (the placeholder
     *     UUID isn't a real player, so the FCT primary-protection doesn't apply).
     *   - {@code WntCommands} admin paths that set a system owner.
     * Bypassing the guard in those files is intentional. If you add a NEW
     * code path that flips ownership for a real player-on-player conflict,
     * route it through {@link net.machiavelli.minecolonytax.WarSystem#transferOwnership(IColony, java.util.UUID)}
     * so this guard applies.
     */
    public static void documentedExemptionsBeyondTransferOwnership() {
        // marker method — see javadoc
    }
}
