package net.machiavelli.minecolonytax.militia;

import com.minecolonies.api.colony.IColony;
// Intentionally do NOT import com.minecolonies.core.entity.mobs.EntityMercenary —
// it lives in the internal core package, not the api/* surface. Match the pattern
// used by BesiegeManager and ColonyClaimingRaidManager: operate on the result of
// ModEntities.MERCENARY.create() as a PathfinderMob via the vanilla API only.
import net.minecraft.world.entity.PathfinderMob;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Shared spawn helper for militia-upgrade reinforcements. Used by both
 * BesiegeManager (besiege start) and WarSystem (war start) so the math,
 * spawn pattern, and tracking are identical.
 *
 * Reinforcements are tracked in a caller-provided Set so the caller owns
 * the despawn lifecycle. They are intentionally NOT counted toward victory
 * objectives — only real guards and player lives are.
 */
public final class MilitiaSpawner {

    private static final Logger LOGGER = LogManager.getLogger(MilitiaSpawner.class);
    private static final Random RNG = new Random();

    // EntityMercenary.shouldDespawn() discards a mercenary whose colony is null. We
    // spawn through the api ModEntities.MERCENARY.create() and intentionally do NOT
    // compile-link the internal core EntityMercenary type, so the colony is assigned
    // reflectively. The Method handle is resolved once and cached.
    private static java.lang.reflect.Method setColonyMethod;
    private static boolean setColonyResolved = false;

    private MilitiaSpawner() {}

    /**
     * Spawn militia-upgrade reinforcements for a colony.
     *
     * @param colony            the colony whose upgrade level + center are used
     * @param guardCount        current real guard count (multiplier scales off this)
     * @param attackerTarget    optional initial aggro target (the besieger/attacker), may be null
     * @param trackingSet       caller-owned set; spawned entities are added here for despawn-on-end
     * @param durationMinutes   conflict duration in minutes, used to scope DAMAGE_RESISTANCE buff
     * @return the number of militia actually spawned (may be 0 if upgrade not purchased or guardCount=0)
     */
    public static int spawnReinforcements(IColony colony, int guardCount,
                                          Player attackerTarget,
                                          Set<Entity> trackingSet,
                                          int durationMinutes) {
        if (colony == null || trackingSet == null) return 0;
        Level world = colony.getWorld();
        if (!(world instanceof ServerLevel)) return 0;
        if (guardCount <= 0) return 0;

        double multiplier = ColonyUpgradeManager.getMilitiaMultiplier(colony.getID());
        int bonus = (int) Math.floor(guardCount * (multiplier - 1.0));
        if (bonus <= 0) return 0;

        int durationTicks = Math.max(1, durationMinutes) * 60 * 20;
        BlockPos center = colony.getCenter();
        int spawned = 0;
        for (int i = 0; i < bonus; i++) {
            try {
                PathfinderMob militia = com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                if (militia == null) continue;
                BlockPos spawnPos = findSpawnPos(center, world);
                militia.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                if (attackerTarget != null) {
                    militia.setTarget(attackerTarget);
                }
                militia.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
                world.addFreshEntity(militia);
                trackingSet.add(militia);
                assignColony(militia, colony);
                spawned++;
            } catch (NoClassDefFoundError ncdfe) {
                // ModEntities.MERCENARY uses internal types; degrade silently if absent.
                LOGGER.debug("Militia spawn: ModEntities.MERCENARY unresolvable, skipping: {}", ncdfe.getMessage());
                break;
            } catch (Exception e) {
                LOGGER.warn("Failed to spawn militia reinforcement {} for colony {}", i, colony.getName(), e);
            }
        }
        if (spawned > 0 && TaxConfig.isNormalLogging()) {
            LOGGER.info("Militia reinforcements for {}: spawned {} (guards {}, multiplier {})",
                    colony.getName(), spawned, guardCount,
                    String.format(Locale.ROOT, "%.2f", multiplier));
        }
        return spawned;
    }

    /**
     * Assign the colony to a freshly-spawned militia mercenary so MineColonies'
     * {@code EntityMercenary.shouldDespawn()} (which discards mercenaries whose
     * colony is null) does not remove it ~8 seconds after spawn. Done reflectively
     * to keep this class free of the internal core EntityMercenary import; degrades
     * silently if the method is unavailable.
     */
    private static void assignColony(PathfinderMob militia, IColony colony) {
        try {
            if (!setColonyResolved) {
                setColonyResolved = true;
                try {
                    setColonyMethod = militia.getClass().getMethod("setColony", IColony.class);
                } catch (NoSuchMethodException nsme) {
                    setColonyMethod = null;
                    LOGGER.debug("Militia setColony unavailable; reinforcements may self-despawn early");
                }
            }
            if (setColonyMethod != null) {
                setColonyMethod.invoke(militia, colony);
            }
        } catch (Throwable t) {
            LOGGER.debug("Militia setColony failed (non-fatal): {}", t.getMessage());
        }
    }

    /**
     * Despawn all militia in the tracking set. Idempotent — call from
     * endWar / cleanupRaid. Clears the set after iteration.
     */
    public static void despawnAll(Set<Entity> trackingSet) {
        if (trackingSet == null || trackingSet.isEmpty()) return;
        for (Entity militia : trackingSet) {
            try {
                if (militia != null && militia.isAlive()) {
                    militia.remove(Entity.RemovalReason.DISCARDED);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to despawn militia reinforcement: {}", e.getMessage());
            }
        }
        trackingSet.clear();
    }

    /**
     * Find a sky-exposed spawn position within ~5 blocks of the colony center.
     * Uses {@code MOTION_BLOCKING_NO_LEAVES} so spawns land on solid ground.
     */
    private static BlockPos findSpawnPos(BlockPos center, Level world) {
        int dx = RNG.nextInt(11) - 5; // -5..+5
        int dz = RNG.nextInt(11) - 5;
        int x = center.getX() + dx;
        int z = center.getZ() + dz;
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
