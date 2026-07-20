package net.machiavelli.minecolonytax.util;

import com.minecolonies.api.colony.IColony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Small colony-geometry helpers shared by the besiege and war retreat logic.
 *
 * <p>Colony claims are NOT rectangles — they are arbitrary polygons of claimed chunks (an L-shape,
 * a ring around a lake, etc.). Measuring "how far has the attacker strayed" from the colony CENTER
 * is therefore wrong: a player standing just outside a far arm of the claim can be a long way from
 * the center while being right on the border. These helpers measure from the nearest claimed
 * BORDER instead, using MineColonies' own {@code isCoordInColony} membership test (which already
 * understands the real claimed shape).</p>
 */
public final class ColonyGeometry {

    private ColonyGeometry() {}

    /** Step (blocks) used when marching toward the colony to locate the border. Half a chunk keeps
     *  the border estimate tight while staying cheap (<= radius/8 membership checks). */
    private static final int MARCH_STEP = 8;

    /**
     * True if the player is still "in the fight": either standing inside the colony's claimed area,
     * or within {@code radiusBlocks} of its nearest claimed border. False once they are farther than
     * {@code radiusBlocks} beyond the border (a retreat).
     *
     * <p>Distance is measured from the border, not the center, so it is fair on irregularly shaped
     * colonies. Implementation: if the player is inside the claim the distance is zero; otherwise we
     * march from the player toward the colony center in {@link #MARCH_STEP}-block steps and take the
     * first point that lies inside the claim as the nearest border along that ray. For the near-blob
     * colonies MineColonies produces this is effectively exact, and it degrades gracefully (only ever
     * slightly over-estimating the distance) for pathological concave shapes.</p>
     */
    public static boolean isWithinBattleRange(IColony colony, ServerPlayer player, int radiusBlocks) {
        if (colony == null || player == null) return false;
        Level world = colony.getWorld();
        if (world == null || player.level() != world) return false;

        BlockPos ppos = player.blockPosition();
        // Inside the actual claimed shape — distance to border is zero.
        if (colony.isCoordInColony(world, ppos)) return true;

        BlockPos center = colony.getCenter();
        double dx = center.getX() - ppos.getX();
        double dz = center.getZ() - ppos.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-6) return true; // effectively at the center already

        double ux = dx / horiz, uz = dz / horiz;
        int y = ppos.getY();
        int limit = Math.max(MARCH_STEP, radiusBlocks);
        for (int d = MARCH_STEP; d <= limit; d += MARCH_STEP) {
            int x = ppos.getX() + (int) Math.round(ux * d);
            int z = ppos.getZ() + (int) Math.round(uz * d);
            if (colony.isCoordInColony(world, new BlockPos(x, y, z))) {
                // Border found d blocks away; within range iff that is within the allowed radius.
                return d <= radiusBlocks;
            }
        }
        // Never entered the claim within the allowed distance — the attacker has retreated.
        return false;
    }
}
