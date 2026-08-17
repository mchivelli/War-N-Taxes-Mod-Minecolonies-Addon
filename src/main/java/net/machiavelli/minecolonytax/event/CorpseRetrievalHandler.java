package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.compat.CorpseCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lets a player open their OWN Corpse-mod corpse inside a colony they have no rights in.
 *
 * <p>Dying in someone else's colony is normal here — that is what wars, raids and besieges are.
 * MineColonies cancels {@code EntityInteract} for anyone lacking {@code RIGHTCLICK_ENTITY} in that
 * colony (every non-member, and this mod additionally revokes it from the neutral rank during a
 * war), so the corpse sits there and its owner cannot open it. Their whole inventory is stuck
 * behind the colony border until someone with rights fetches it.
 *
 * <p>This handler runs LAST and, unusually, asks for already-cancelled events: its entire job is to
 * undo a cancel somebody else made. It only ever does that for a corpse that belongs to the
 * interacting player, so it grants no rights beyond retrieving one's own belongings — a besieger
 * still cannot loot the defenders' corpses, and nothing else in the colony becomes interactable.
 *
 * <p>Note it never cancels anything and never un-cancels an event that was not cancelled, so with
 * the Corpse mod absent it is inert.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CorpseRetrievalHandler {

    private static final Logger LOGGER = LogManager.getLogger(CorpseRetrievalHandler.class);

    private CorpseRetrievalHandler() {}

    // receiveCanceled = true is the whole point: by default a handler never sees an event another
    // mod already cancelled, and LOWEST puts us after MineColonies' permission handler.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        allowOwnCorpse(event, event.getTarget(), event.getEntity());
    }

    /**
     * EntityInteractSpecific fires before EntityInteract for some interactions, and MineColonies
     * cancels both, so both have to be un-cancelled or the corpse still will not open.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        allowOwnCorpse(event, event.getTarget(), event.getEntity());
    }

    private static void allowOwnCorpse(PlayerInteractEvent event, Entity target, Entity source) {
        try {
            // The base PlayerInteractEvent is not cancellable; only the concrete subclasses are,
            // via ICancellableEvent (same idiom BesiegeEntityInteractHandler uses to cancel).
            if (!(event instanceof ICancellableEvent cancellable)) return;
            if (!cancellable.isCanceled()) return;                 // nothing to undo
            if (!TaxConfig.isCorpseRetrievalInColoniesEnabled()) return;
            if (event.getLevel().isClientSide()) return;
            if (!(source instanceof ServerPlayer player)) return;
            if (!CorpseCompat.belongsTo(target, player.getUUID())) return;

            cancellable.setCanceled(false);

            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("[WnT] Allowed {} to retrieve their own corpse inside a colony they lack rights in.",
                        player.getName().getString());
            }
        } catch (Throwable t) {
            // Never let corpse convenience break interaction handling as a whole.
            LOGGER.warn("[WnT] Corpse retrieval check failed: {}", t.toString());
        }
    }
}
