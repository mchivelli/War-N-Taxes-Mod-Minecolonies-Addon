package net.machiavelli.minecolonytax.besiege;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces the Siege SMP "solo besiege" rule on the attacker side only:
 *
 * If the damage source is a player belonging to an active besieger's colony
 * (but is NOT the besieger themselves), cancel the damage. The besieger fights
 * alone — their friends and officers can be present but cannot deal damage to
 * defenders.
 *
 * The defender side is asymmetric: defenders may rally allies freely (the
 * call-to-arms message from step 4 invites them). This shield does NOT block
 * defender damage to the attacker.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BesiegeDamageShieldHandler {

    // Throttle the chat message so spam-clicking attacks doesn't flood the chat
    private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
    private static final long BLOCK_MESSAGE_COOLDOWN_MS = 3000;

    // How long a cached colony-mate determination stays valid. Short enough to pick
    // up mid-besiege rank changes, long enough to collapse a combat hit-burst.
    private static final long COLONY_MATE_CACHE_TTL_MS = 5000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof ServerPlayer source)) return;

        LivingEntity target = event.getEntity();
        UUID sourceUUID = source.getUUID();

        // A source who is themselves an authorized belligerent against the TARGET citizen's own
        // colony (their own besiege/war/raid granted them the attack permission) is not a mere
        // helper — the solo-besiege shield must never block them, or two colony-mates co-besieging
        // the same colony would cancel each other's hits. A bystander colony-mate who never
        // declared has no such permission and still falls through to the solo-shield check below.
        if (target instanceof AbstractEntityCitizen citizen) {
            IColony targetColony = citizenColony(citizen);
            if (targetColony != null && CombatSanction.mayHarmColonists(targetColony, source)) return;
        }

        // For EACH active besiege, ask "is this source helping someone else's besiege?"
        // We do NOT short-circuit on the source being a besieger themselves — that was
        // the bug. An active besieger can still be a colony-mate of ANOTHER besieger,
        // and damage they deal in the other besiege's target colony is also blocked.
        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
            if (raid.besiegingPlayerUUID == null) continue;
            // Skip the source's own raid — they're allowed to deal damage in their own besiege.
            if (raid.besiegingPlayerUUID.equals(sourceUUID)) continue;

            if (!areColonyMates(source, raid)) continue;

            // Confirm the target belongs to the besieged side: defender citizen,
            // defender-side player, or a mercenary spawned for this raid.
            if (!isDefenderSideTarget(target, raid)) continue;

            event.setCanceled(true);
            event.setAmount(0f);
            sendBlockedMessage(source);
            return;
        }
    }

    /** The colony a citizen belongs to, or {@code null} if unavailable. */
    private static IColony citizenColony(AbstractEntityCitizen citizen) {
        try {
            var data = citizen.getCitizenData();
            return data != null ? data.getColony() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * True when both players hold a non-neutral, non-hostile rank in any single
     * shared colony. Uses {@link Rank#isHostile()} so custom hostile ranks are
     * caught (not just the default Hostile rank instance).
     */
    private static boolean areColonyMates(ServerPlayer source, BesiegeManager.BesiegeRaidData raid) {
        UUID besiegerUUID = raid.besiegingPlayerUUID;
        if (besiegerUUID == null) return false;
        // Short-TTL cache per source — LivingHurtEvent is extremely high-frequency and
        // the colony scan below is O(allColonies). The TTL collapses a combat hit-burst
        // to one scan per few seconds per attacker while still re-checking often enough
        // to catch a mid-besiege rank change (audit C3 + codex follow-up).
        long now = System.currentTimeMillis();
        long[] entry = raid.colonyMateCache.get(source.getUUID());
        if (entry != null && now < entry[1]) return entry[0] != 0;
        boolean result = computeColonyMates(source, besiegerUUID);
        raid.colonyMateCache.put(source.getUUID(), new long[]{ result ? 1 : 0, now + COLONY_MATE_CACHE_TTL_MS });
        return result;
    }

    /**
     * True when both players hold a non-neutral, non-hostile rank in any single
     * shared colony. Uses {@link Rank#isHostile()} so custom hostile ranks are
     * caught (not just the default Hostile rank instance). O(allColonies) — only
     * called once per (raid, source) thanks to {@link #areColonyMates} caching.
     */
    private static boolean computeColonyMates(ServerPlayer source, UUID besiegerUUID) {
        if (source.level().getServer() == null) return false;
        try {
            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
                Rank sourceRank = colony.getPermissions().getRank(source.getUUID());
                Rank besiegerRank = colony.getPermissions().getRank(besiegerUUID);
                if (sourceRank == null || besiegerRank == null) continue;
                // Skip if either party is neutral (not really a colony member).
                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
                // Skip if either party is hostile — including custom hostile ranks.
                if (sourceRank.isHostile()) continue;
                if (besiegerRank.isHostile()) continue;
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * True if {@code target} is a defender-side combatant for the given raid:
     *  - Citizen of the besieged colony
     *  - Mercenary spawned for this raid (NOT militia-upgrade reinforcements —
     *    those don't count as objectives, so the shield ignores them too to
     *    keep behavior consistent across the rules)
     *  - Player with a non-hostile rank on the besieged colony
     */
    private static boolean isDefenderSideTarget(LivingEntity target, BesiegeManager.BesiegeRaidData raid) {
        // Mercenary spawned for this raid?
        if (raid.spawnedMercenaries.contains(target)) return true;

        // Citizen of the besieged colony?
        if (target instanceof AbstractEntityCitizen citizen) {
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null
                        && data.getColony().getID() == raid.colonyId) {
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }
        // Player on defender side?
        if (target instanceof ServerPlayer player) {
            try {
                IColony besieged = com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().stream()
                        .filter(c -> c.getID() == raid.colonyId)
                        .findFirst().orElse(null);
                if (besieged == null) return false;
                Rank targetRank = besieged.getPermissions().getRank(player.getUUID());
                if (targetRank == null) return false;
                if (targetRank.isHostile()) return false;
                if (targetRank.equals(besieged.getPermissions().getRankNeutral())) return false;
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static void sendBlockedMessage(ServerPlayer source) {
        long now = System.currentTimeMillis();
        // Opportunistic cleanup so this throttle map can't grow unbounded over a long session.
        // Entries past the cooldown window no longer throttle anything, so dropping them is a no-op.
        LAST_BLOCK_MESSAGE.values().removeIf(ts -> now - ts >= BLOCK_MESSAGE_COOLDOWN_MS);
        Long last = LAST_BLOCK_MESSAGE.get(source.getUUID());
        if (last != null && now - last < BLOCK_MESSAGE_COOLDOWN_MS) return;
        LAST_BLOCK_MESSAGE.put(source.getUUID(), now);
        source.sendSystemMessage(Component.literal(
                "You cannot interfere in a solo besiege — your colony-mate must fight alone.")
                .withStyle(ChatFormatting.RED));
    }
}
