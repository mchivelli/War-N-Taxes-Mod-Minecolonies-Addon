package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.compat.CombatSanction;
import net.machiavelli.minecolonytax.compat.HywCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.UUID;

/**
 * Makes <b>Hundred Years Warfare</b> (HYW) troops coexist with MineColonies colonists.
 *
 * <p>HYW soldiers do not recognise their commander's own colonists and will attack, or
 * accidentally arrow, the very town they are meant to protect - which then turns a colony's
 * own guards hostile and spirals into your army fighting your townsfolk. This guard fixes
 * that while preserving siege warfare:
 *
 * <ul>
 *   <li>An HYW troop can <b>never</b> damage or target a colonist of its own owner, that
 *       owner's allied colonies, or any neutral colony...</li>
 *   <li>...<b>unless</b> a sanctioned War 'n Taxes event (war / besiege / raid) puts that
 *       colony on the opposing side, in which case the troop is free to kill or pillage the
 *       enemy colonists exactly as a siege should.</li>
 *   <li>Protection is <b>symmetric</b>: a friendly player's colonists will not target or
 *       damage that player's HYW troops either, so a stray hit can't start a friendly war.</li>
 * </ul>
 *
 * <p>Two layers, per design:
 * <ol>
 *   <li>{@link LivingChangeTargetEvent} cancels friendly target acquisition on the vanilla
 *       targeting path (proactive - the troop never even aims at a friendly colonist).</li>
 *   <li>{@link LivingIncomingDamageEvent} cancels any friendly damage that slips through
 *       HYW's own custom AI (guaranteed backstop, covers melee and arrows), and strips the
 *       troop's target so it stops trying.</li>
 * </ol>
 *
 * <p>Entirely inert unless HYW is installed ({@link HywCompat#isAvailable()}) and the
 * {@code EnableHywFriendlyFireProtection} config is on. No HYW type is referenced here; all
 * HYW interaction is funnelled through {@link HywCompat}.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class HywFriendlyFireHandler {

    private HywFriendlyFireHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!TaxConfig.isHywFriendlyFireProtectionEnabled() || !HywCompat.isAvailable()) return;

        LivingEntity victim = event.getEntity();
        // For projectiles (archers), getEntity() resolves to the shooter, not the arrow.
        Entity source = event.getSource().getEntity();

        // Direction A: HYW troop -> friendly colonist. Cancel and disarm the troop.
        if (HywCompat.isHywSoldier(source) && victim instanceof AbstractEntityCitizen citizen) {
            if (isFriendly(HywCompat.getSoldierOwner(source), citizen)) {
                event.setCanceled(true);
                event.setAmount(0f);
                HywCompat.clearHostileTarget(source);
            }
            return;
        }

        // Direction B: friendly colonist -> HYW troop. Cancel so guards can't start the fight.
        if (HywCompat.isHywSoldier(victim) && source instanceof AbstractEntityCitizen citizen) {
            if (isFriendly(HywCompat.getSoldierOwner(victim), citizen)) {
                event.setCanceled(true);
                event.setAmount(0f);
            }
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!TaxConfig.isHywFriendlyFireProtectionEnabled() || !HywCompat.isAvailable()) return;

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) return;
        Entity self = event.getEntity();

        // HYW troop about to lock onto a friendly colonist: refuse the target.
        if (HywCompat.isHywSoldier(self) && newTarget instanceof AbstractEntityCitizen citizen) {
            if (isFriendly(HywCompat.getSoldierOwner(self), citizen)) {
                event.setCanceled(true);
            }
            return;
        }

        // Colonist about to lock onto a friendly HYW troop: refuse the target.
        if (self instanceof AbstractEntityCitizen citizen && HywCompat.isHywSoldier(newTarget)) {
            if (isFriendly(HywCompat.getSoldierOwner(newTarget), citizen)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * A colonist is FRIENDLY to an HYW troop's owner when no sanctioned war, besiege, or raid
     * authorizes that owner to attack the colonist's colony. Same-owner and allied colonies
     * are always friendly; an enemy colony under a sanctioned siege is not (so its colonists
     * remain fair game).
     *
     * <p>An unknown troop owner returns {@code false} (not friendly) so we never interfere
     * with combat we can't attribute.
     */
    private static boolean isFriendly(UUID troopOwner, AbstractEntityCitizen citizen) {
        if (troopOwner == null) return false;
        IColony colony = citizenColony(citizen);
        if (colony == null) return false;
        return !CombatSanction.isSanctionedAttack(troopOwner, colony);
    }

    private static IColony citizenColony(AbstractEntityCitizen citizen) {
        try {
            var data = citizen.getCitizenData();
            return data != null ? data.getColony() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
