package net.machiavelli.minecolonytax.siege;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * DeferredRegister wiring for siege-system blocks and their BlockItems.
 * Currently only registers the Siege Banner used by the experimental
 * Plant-the-Banner victory objective (step 11).
 *
 * Registration is opt-in via {@link MineColonyTax}'s mod-bus subscription —
 * the registries are always created, but the Plant-the-Banner objective is
 * gated behind {@code EnableExperimentalSiegeObjectives} at runtime.
 */
public final class ModSiegeBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MineColonyTax.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MineColonyTax.MOD_ID);

    public static final RegistryObject<Block> SIEGE_BANNER = BLOCKS.register("siege_banner",
            SiegeBannerBlock::new);

    public static final RegistryObject<Item> SIEGE_BANNER_ITEM = ITEMS.register("siege_banner",
            () -> new BlockItem(SIEGE_BANNER.get(),
                    new Item.Properties().stacksTo(1).fireResistant()));

    private ModSiegeBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
