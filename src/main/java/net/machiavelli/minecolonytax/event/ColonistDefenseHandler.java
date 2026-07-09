package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.compat.CombatSanction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wartime colonist protection: no off-the-books colonist kills.
 *
 * <p>A player cannot simply walk into someone's colony and start slaughtering colonists. Player
 * damage to a citizen is cancelled unless the attacker is a member of that colony (managing their
 * own town) or an active War 'n Taxes event authorizes the attack. Authorization is read as the
 * MineColonies {@code ATTACK_CITIZEN}/{@code HURT_CITIZEN} permission that every event (war,
 * besiege, raid, claiming raid) grants the belligerent on the Hostile rank, with {@link
 * CombatSanction} as a fallback — see {@link CombatSanction#mayHarmColonists}. Vanilla monsters,
 * environment, and MineColonies' own raiders are never touched, so natural raids still work; and
 * once a war/besiege/raid is declared, every authorized attacker can kill freely. HYW troops are
 * handled separately by {@link HywFriendlyFireHandler}.
 *
 * <p><b>Note:</b> there is deliberately no "shelter" rule here. An earlier version forced
 * MineColonies' {@code isRaided()} state on the defending colony to herd civilians home, but
 * MineColonies treats {@code isRaided()} as blanket damage immunity for the whole colony
 * ({@code EntityCitizen.checkIfValidDamageSource} rejects <i>all</i> player damage while raided),
 * which made besieged/raided colonists unhittable and defeated the very events that granted the
 * attack permission. Sheltering can never coexist with "attackers may hit colonists," so it was
 * removed; guards and militia now defend normally during an event.
 *
 * <p>The kill-protection rule is toggleable in config and defaults on.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ColonistDefenseHandler {

    // Throttle the "protected" chat nudge so spam-clicking doesn't flood the attacker.
    private static final Map<UUID, Long> LAST_PROTECT_MESSAGE = new HashMap<>();
    private static final long PROTECT_MESSAGE_COOLDOWN_MS = 3000;

    private ColonistDefenseHandler() {}

    // ---------------------------------------------------------------------
    // Block unsanctioned player damage to colonists
    // ---------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!TaxConfig.isColonistKillProtectionEnabled()) return;

        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;

        // Only guard against PLAYER grief (direct or via a player's projectile, where
        // getEntity() resolves to the shooter). Vanilla mobs and the environment are left alone
        // so natural MineColonies raids continue to threaten colonists.
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        IColony colony = citizenColony(citizen);
        if (colony == null) return;

        // A member of the colony may act within their own town.
        if (isColonyMember(player.getUUID(), colony)) return;

        // Outsiders may only harm colonists when an active war/besiege/raid authorizes it —
        // read as the MineColonies attack permission the event grants, with a sanction fallback.
        // This keeps every authorized belligerent (lead attacker, co-besiegers, granted allies)
        // able to hit, while anyone with no active event is still cancelled below.
        if (CombatSanction.mayHarmColonists(colony, player)) return;

        event.setCanceled(true);
        event.setAmount(0f);
        nudge(player);
    }

    private static IColony citizenColony(AbstractEntityCitizen citizen) {
        try {
            var data = citizen.getCitizenData();
            return data != null ? data.getColony() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** True if the player holds a real (non-neutral, non-hostile) rank in the colony. */
    private static boolean isColonyMember(UUID uuid, IColony colony) {
        try {
            Rank rank = colony.getPermissions().getRank(uuid);
            if (rank == null) return false;
            if (rank.equals(colony.getPermissions().getRankNeutral())) return false;
            if (rank.isHostile()) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void nudge(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_PROTECT_MESSAGE.get(player.getUUID());
        if (last != null && now - last < PROTECT_MESSAGE_COOLDOWN_MS) return;
        LAST_PROTECT_MESSAGE.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(
                        "This colonist is protected. Declare war, besiege, or raid the colony first.")
                .withStyle(ChatFormatting.RED));
    }
}
