## STEP 5 — Asymmetric solo besiege damage shield

New file BesiegeDamageShieldHandler.java. Subscribes to LivingHurtEvent. If source is a player who is a colony-mate of any active besieger (but NOT the besieger themselves), and target is on the defender side (citizen or non-hostile player of the besieged colony), cancel the damage. One-way: defender allies are NOT blocked.

```java
package net.machiavelli.minecolonytax.besiege;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof ServerPlayer source)) return;

        LivingEntity target = event.getEntity();
        UUID sourceUUID = source.getUUID();

        // If the source player is themselves an active besieger, they can do whatever damage they want.
        BesiegeManager.BesiegeRaidData sourceOwnRaid = BesiegeManager.getRaidForBesieger(sourceUUID);
        if (sourceOwnRaid != null) return;

        // Look for any active besiege whose besieger is in the same colony as this source player.
        // If found, the source is an attacker's colony-mate and is blocked from helping.
        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
            if (raid.besiegingPlayerUUID == null) continue;
            // Skip if it's their own raid (already handled above)
            if (raid.besiegingPlayerUUID.equals(sourceUUID)) continue;

            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;

            // Confirm the target belongs to the besieged side: a defender citizen of the
            // besieged colony OR a player who is on the defender side (any non-besieger
            // player in the besieged colony's permission list, including notified allies).
            if (!isDefenderSideTarget(target, raid.colonyId)) continue;

            event.setCanceled(true);
            event.setAmount(0f);
            sendBlockedMessage(source);
            return;
        }
    }

    /**
     * True when both players hold an officer/friend rank in any single shared colony.
     * Best-effort — relies on MineColonies permissions. A null besieger lookup returns false.
     */
    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
        if (source.level().getServer() == null) return false;
        // Walk all colonies the source player has any rank in. If the besieger also has
        // any non-neutral rank in the same colony, they are colony-mates.
        try {
            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
                Rank sourceRank = colony.getPermissions().getRank(source.getUUID());
                Rank besiegerRank = colony.getPermissions().getRank(besiegerUUID);
                if (sourceRank == null || besiegerRank == null) continue;
                // Either party being neutral on this colony means they're not "mates" via this colony
                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
        // Citizen of the besieged colony
        if (target instanceof AbstractEntityCitizen citizen) {
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null
                        && data.getColony().getID() == besiegedColonyId) {
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }
        // Player on defender side: any player who has a non-hostile rank on the besieged colony
        if (target instanceof ServerPlayer player) {
            try {
                IColony besieged = com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().stream()
                        .filter(c -> c.getID() == besiegedColonyId)
                        .findFirst().orElse(null);
                if (besieged == null) return false;
                Rank targetRank = besieged.getPermissions().getRank(player.getUUID());
                if (targetRank == null) return false;
                return !targetRank.equals(besieged.getPermissions().getRankHostile())
                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static void sendBlockedMessage(ServerPlayer source) {
        long now = System.currentTimeMillis();
        Long last = LAST_BLOCK_MESSAGE.get(source.getUUID());
        if (last != null && now - last < BLOCK_MESSAGE_COOLDOWN_MS) return;
        LAST_BLOCK_MESSAGE.put(source.getUUID(), now);
        source.sendSystemMessage(Component.literal(
                "You cannot interfere in a solo besiege — your colony-mate must fight alone.")
                .withStyle(ChatFormatting.RED));
    }
}
```
