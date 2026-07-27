package net.machiavelli.minecolonytax.besiege;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Completes the besiege interaction profile: during a besiege, right-clicking
 * a villager (trade) or a MineColonies citizen is denied for any player on the
 * besieged colony's combat radius. Combat-only — no trading, no recruiting,
 * no information gathering through normal NPC interactions.
 *
 * Pairs with the container-block deny in BlockInteractionFilterHandler.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class BesiegeEntityInteractHandler {

    private static final Logger LOGGER = LogManager.getLogger(BesiegeEntityInteractHandler.class);

    // Throttle the deny message so spam-clicking doesn't flood chat
    private static final Map<UUID, Long> LAST_DENY_MESSAGE = new HashMap<>();
    private static final long DENY_MESSAGE_COOLDOWN_MS = 3000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleEntityInteraction(event, event.getTarget(), event.getEntity());
    }

    /**
     * EntityInteractSpecific fires before EntityInteract for some interactions
     * (notably villager trade UI on some mod combos). Cover both events so the
     * besiege lockdown can't be bypassed by event-ordering quirks.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleEntityInteraction(event, event.getTarget(), event.getEntity());
    }

    private static void handleEntityInteraction(PlayerInteractEvent event, Entity target, Entity sourceEntity) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(sourceEntity instanceof ServerPlayer player)) return;

        boolean isVillager = target instanceof AbstractVillager;
        boolean isCitizen = target instanceof AbstractEntityCitizen;
        if (!isVillager && !isCitizen) return;

        // Find any active besiege the player is involved in (either as besieger
        // or as a defender on the besieged colony).
        int besiegedColonyId = -1;

        // Is the player a besieger?
        BesiegeManager.BesiegeRaidData own = BesiegeManager.getRaidForBesieger(player.getUUID());
        if (own != null) besiegedColonyId = own.colonyId;

        // If not, are they inside a colony that's being besieged?
        if (besiegedColonyId < 0 && isCitizen) {
            AbstractEntityCitizen citizen = (AbstractEntityCitizen) target;
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null) {
                    int cid = data.getColony().getID();
                    if (BesiegeManager.isActiveRaidOnColony(cid)) besiegedColonyId = cid;
                }
            } catch (Exception ignored) {}
        }

        if (besiegedColonyId < 0) return;

        // Deny — combat-only during besiege. Both concrete callers (EntityInteract /
        // EntityInteractSpecific) are cancelable; the base PlayerInteractEvent is not,
        // so cancel via the ICancellableEvent interface.
        if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }

        long now = System.currentTimeMillis();
        // Opportunistic cleanup so this throttle map can't grow unbounded over a long session.
        // Entries past the cooldown window no longer throttle anything, so dropping them is a no-op.
        LAST_DENY_MESSAGE.values().removeIf(ts -> now - ts >= DENY_MESSAGE_COOLDOWN_MS);
        Long last = LAST_DENY_MESSAGE.get(player.getUUID());
        if (last == null || now - last >= DENY_MESSAGE_COOLDOWN_MS) {
            LAST_DENY_MESSAGE.put(player.getUUID(), now);
            String label = isVillager ? "trade with this villager" : "interact with this citizen";
            player.sendSystemMessage(Component.literal(
                    "You cannot " + label + " during a besiege — combat only.")
                    .withStyle(ChatFormatting.RED));
        }
        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("BESIEGE DENIED (ENTITY INTERACT): {} blocked from interacting with {} on colony {}",
                    player.getName().getString(), target.getClass().getSimpleName(), besiegedColonyId);
        }
    }
}
