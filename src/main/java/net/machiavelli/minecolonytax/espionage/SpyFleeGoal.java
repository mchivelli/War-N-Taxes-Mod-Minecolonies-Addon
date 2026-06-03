package net.machiavelli.minecolonytax.espionage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * AI Goal for SpyEntity flee state.
 * Active only when the spy is in FLEEING state and has a valid flee target.
 * Overrides RandomStrollGoal at a higher priority via Goal.Flag.MOVE.
 */
public class SpyFleeGoal extends Goal {

    private final SpyEntity spy;
    private final double speedMod;
    private int repathTimer = 0;
    private static final int REPATH_INTERVAL = 40; // re-path every 40 ticks (2 sec)

    public SpyFleeGoal(SpyEntity spy, double speedMod) {
        this.spy = spy;
        this.speedMod = speedMod;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return spy.isFleeing() && spy.getFleeTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return spy.isFleeing() && spy.getFleeTarget() != null;
    }

    @Override
    public void start() {
        BlockPos target = spy.getFleeTarget();
        if (target != null) {
            spy.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speedMod);
        }
        repathTimer = 0;
    }

    @Override
    public void tick() {
        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            BlockPos target = spy.getFleeTarget();
            if (target != null) {
                spy.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speedMod);
            }
        }
    }

    @Override
    public void stop() {
        spy.getNavigation().stop();
    }
}
