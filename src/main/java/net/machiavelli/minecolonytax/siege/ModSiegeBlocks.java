package net.machiavelli.minecolonytax.siege;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DeferredRegister wiring for siege-system blocks and their BlockItems
 * (NeoForge 1.21.1 port of the Forge {@code ModSiegeBlocks}).
 *
 * Currently only registers the Siege Banner used by the experimental
 * Plant-the-Banner victory objective (step 11).
 *
 * Registration is opt-in via {@link MineColonyTax}'s mod-bus subscription —
 * the registries are always created and registered to the mod bus, but the
 * Plant-the-Banner objective is gated behind
 * {@code EnableExperimentalSiegeObjectives} at runtime.
 *
 * NeoForge differences from Forge:
 *  - {@code DeferredRegister.create(Registries.BLOCK, MODID)} replaces
 *    {@code DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)}.
 *  - {@link DeferredHolder} replaces {@code RegistryObject}.
 */
public final class ModSiegeBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MineColonyTax.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineColonyTax.MOD_ID);

    public static final DeferredHolder<Block, SiegeBannerBlock> SIEGE_BANNER =
            BLOCKS.register("siege_banner", SiegeBannerBlock::new);

    public static final DeferredHolder<Item, BlockItem> SIEGE_BANNER_ITEM =
            ITEMS.register("siege_banner",
                    () -> new BlockItem(SIEGE_BANNER.get(),
                            new Item.Properties().stacksTo(1).fireResistant()));

    private ModSiegeBlocks() {}

    /** Register both DeferredRegisters to the supplied (mod) event bus. */
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
