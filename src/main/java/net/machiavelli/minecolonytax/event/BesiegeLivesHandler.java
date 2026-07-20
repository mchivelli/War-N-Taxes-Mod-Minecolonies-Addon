package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Drains besiege LIFE pools when a besieging or defending player dies. A besieger's death drains
 * the attacker pool; a defending player's death drains the defender pool. The win resolution
 * itself (attacker lives 0 -> defenders win; defender lives 0 -> attackers win) is evaluated in
 * {@link BesiegeManager#tick()} so all victory routing (spoils, cleanup, co-besieger handling)
 * stays on a single path.
 *
 * <p>Runs at LOW priority so any last-life inventory-preservation / event-cancel handlers settle
 * first. The event is never cancelled here — natural death processing (corpses, death messages,
 * respawn) is left untouched; only the life tally is decremented.</p>
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class BesiegeLivesHandler {

    private BesiegeLivesHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BesiegeManager.onBesiegeCombatantDeath(player.getUUID());
    }
}
