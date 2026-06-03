package net.machiavelli.minecolonytax.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The Siege Banner — placed by an attacker inside the defender's Town Hall to
 * trigger the capture-and-hold victory objective. The block itself is a simple
 * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
 *
 * Indestructible to vanilla explosions to prevent it being blown up by stray TNT,
 * but explicitly breakable by defenders via right-click melee (handled via the
 * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
 * left-click destroys it for defenders who get past attackers.
 */
public class SiegeBannerBlock extends Block {

    public SiegeBannerBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(0.5f, 1200000.0f) // hardness 0.5 (quick to break by hand)
                                            // blast resistance very high (siege banners don't fall to TNT)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)); // soft glow so it's visible at night
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
    }
}
