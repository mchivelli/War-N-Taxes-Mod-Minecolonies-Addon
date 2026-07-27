package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Central "is this a legal attack?" resolver, shared by the combat guards
 * (HYW friendly-fire protection today; unprompted-colonist-kill protection next).
 *
 * <p>Given the UUID of the player behind an attack and the colony of the victim
 * colonist, it answers whether a <b>sanctioned</b> War 'n Taxes event currently
 * authorizes that player to harm that colony's citizens. The three sanctioned
 * sources are a formal war ({@link WarSystem}), a besiege ({@link BesiegeManager}),
 * and a raid ({@link RaidManager}).
 *
 * <ul>
 *   <li>{@code false} &rarr; friendly fire or an off-the-books assault; the caller
 *       should cancel the damage / targeting.</li>
 *   <li>{@code true}  &rarr; the attacker is a legitimate belligerent against that
 *       colony (e.g. a siege is in progress); damage is allowed through.</li>
 * </ul>
 *
 * You can never wage a sanctioned attack on your own colony, so same-owner always
 * resolves to {@code false}.
 */
public final class CombatSanction {

    private CombatSanction() {}

    /**
     * @param attackerOwner the player driving the attack (an HYW troop's owner, or an attacking player)
     * @param victimColony  the colony the victim colonist belongs to
     * @return true if a sanctioned war, besiege, or raid authorizes {@code attackerOwner}
     *         to attack {@code victimColony}
     */
    public static boolean isSanctionedAttack(UUID attackerOwner, IColony victimColony) {
        if (attackerOwner == null || victimColony == null) return false;

        final int victimColonyId = victimColony.getID();

        // A player can never wage a sanctioned attack on a colony they own.
        UUID victimOwner = victimColony.getPermissions().getOwner();
        if (attackerOwner.equals(victimOwner)) return false;

        // 1) Active formal war with attacker and victim colony on OPPOSITE sides.
        if (isWarOpponentOf(attackerOwner, victimColonyId)) return true;

        // 2) Active besiege led by attackerOwner (or with them as a tracked ally).
        if (BesiegeManager.isBesiegingPlayer(attackerOwner, victimColonyId)) return true;
        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getRaidsForColony(victimColonyId)) {
            if (attackerOwner.equals(raid.besiegingPlayerUUID)) return true;
            // alliedPlayers is final + initialised to ConcurrentHashMap.newKeySet() → never null; check dropped.
            if (raid.alliedPlayers.contains(attackerOwner)) return true;
        }

        // 3) Active raid on the colony led by attackerOwner.
        if (RaidManager.isPlayerCurrentlyRaiding(attackerOwner, victimColony)) return true;

        // 4) Active abandoned-colony claiming raid led by attackerOwner (separate system).
        if (ColonyClaimingRaidManager.isPlayerInClaimingRaid(attackerOwner, victimColonyId)) return true;

        return false;
    }

    /**
     * True when an active war has {@code player} on one side and {@code colonyId} on the
     * other. Sides are read from the war's principals and their ally sets; the colony is
     * matched against both the defender colony ({@link WarData#getColony()}) and the
     * attacker colony ({@link WarData#getAttackerColony()}).
     */
    /**
     * True if MineColonies itself currently grants {@code player} permission to attack this
     * colony's citizens. Every War 'n Taxes player-vs-colony event (war, besiege, raid, claiming
     * raid) assigns the belligerent the colony's <b>Hostile</b> rank and enables {@code
     * ATTACK_CITIZEN}/{@code HURT_CITIZEN} on it, so this is the single authoritative "prompted and
     * allowed" test — the same permission MineColonies' own damage guard reads. It stays true for
     * every authorized participant (lead attacker, co-besiegers, granted allies) and false for
     * anyone with no active event, so no-event colonists remain protected.
     */
    public static boolean holdsAttackPermission(IColony colony, Player player) {
        if (colony == null || player == null) return false;
        try {
            var perms = colony.getPermissions();
            return perms.hasPermission(player, Action.ATTACK_CITIZEN)
                    || perms.hasPermission(player, Action.HURT_CITIZEN);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The combined authority used by the colonist-damage guards: a player may harm this colony's
     * citizens if MineColonies has granted them the attack permission (via any event) <i>or</i> a
     * War 'n Taxes sanction independently authorizes it. Checking the permission first keeps every
     * event-authorized attacker in sync with MineColonies' own rules; the sanction fallback covers
     * the brief window before a grant propagates or if permission toggling is disabled in config.
     */
    public static boolean mayHarmColonists(IColony colony, Player player) {
        if (player == null) return false;
        if (holdsAttackPermission(colony, player)) return true;
        return isSanctionedAttack(player.getUUID(), colony);
    }

    private static boolean isWarOpponentOf(UUID player, int colonyId) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            IColony defender = war.getColony();
            IColony attacker = war.getAttackerColony();

            boolean colonyIsDefender = defender != null && defender.getID() == colonyId;
            boolean colonyIsAttacker = attacker != null && attacker.getID() == colonyId;
            if (!colonyIsDefender && !colonyIsAttacker) continue;

            boolean playerOnAttackerSide =
                    player.equals(war.getAttacker()) || war.getAttackerAllies().contains(player);
            boolean playerOnDefenderSide =
                    player.equals(war.getDefender()) || war.getDefenderAllies().contains(player);

            if (colonyIsDefender && playerOnAttackerSide) return true;
            if (colonyIsAttacker && playerOnDefenderSide) return true;
        }
        return false;
    }
}
