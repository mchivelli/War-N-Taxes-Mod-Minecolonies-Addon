package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.ransom.ConflictType;
import net.machiavelli.minecolonytax.ransom.RansomManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The single ransom trigger (v1 had two divergent ones): when a player-killer who is the
 * active raider / primary besieger of a colony kills that colony's owner or an officer,
 * {@link RansomManager#onDefenderKilled} creates the offer.
 *
 * <p>Priority LOWEST — deliberately after all three existing death listeners:
 * <ul>
 *   <li>{@code WarEventHandler} (HIGHEST) handles the RAIDER dying — the opposite role,
 *       no overlap; running last means we observe post-settled conflict state.</li>
 *   <li>{@code BesiegeLivesHandler} (LOW) drains the besiege life pool first. A ransomed
 *       kill still costs its life on purpose: the offer may be denied and the siege
 *       continues — a non-counting death would make dying-to-spawn-offers a free life.
 *       If that drain emptied the pool, {@link RansomManager} skips the offer (victory
 *       resolution is imminent in {@code BesiegeManager.tick()}).</li>
 *   <li>{@code PvPKillEconomyHandler} (NORMAL) pays its kill reward independently —
 *       intended stacking, each system has its own config gate.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RansomKillHandler {

    private RansomKillHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!TaxConfig.isRansomSystemEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        ServerPlayer killer = resolveKiller(event.getSource());
        if (killer == null || killer.getUUID().equals(victim.getUUID())) return;

        // --- Raid context: the killer must be THE active raider of the victim's colony ---
        ActiveRaidData raid = RaidManager.getActiveRaidForPlayer(killer.getUUID());
        if (raid != null && raid.isActive() && raid.getColony() != null) {
            IColony colony = raid.getColony();
            if (RansomManager.isOwnerOrOfficer(colony, victim.getUUID())) {
                RansomManager.onDefenderKilled(victim, killer, colony.getID(), ConflictType.RAID);
                return;
            }
        }

        // --- Besiege context: only the PRIMARY besieger triggers (allies never do) ---
        if (!TaxConfig.isRansomBesiegeEnabled()) return;
        int besiegedColonyId = BesiegeManager.getBesiegedColonyIdFor(killer.getUUID());
        if (besiegedColonyId >= 0) {
            IColony colony = getColonyById(besiegedColonyId);
            if (colony != null && RansomManager.isOwnerOrOfficer(colony, victim.getUUID())) {
                RansomManager.onDefenderKilled(victim, killer, besiegedColonyId, ConflictType.BESIEGE);
            }
        }
    }

    /**
     * Vanilla already reports the projectile OWNER via {@code getEntity()} for arrows and
     * tridents; the {@code getDirectEntity()} fallback covers modded projectiles that only
     * set the direct cause.
     */
    private static ServerPlayer resolveKiller(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    private static IColony getColonyById(int colonyId) {
        return com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager()
                .getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
    }
}
