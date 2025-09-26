package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.blocks.ModBlocks;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Hard block crafting of MineColonies hut blocks when DisableHutRecipes = true.
 * This guarantees players cannot obtain the items via crafting even if a recipe slips through.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeCraftBlocker {

    private static final Set<Item> HUT_BLOCK_ITEMS = new HashSet<>();

    static {
        add(ModBlocks.blockHutAlchemist);
        add(ModBlocks.blockHutConcreteMixer);
        add(ModBlocks.blockHutFletcher);
        add(ModBlocks.blockHutLumberjack);
        add(ModBlocks.blockHutRabbitHutch);
        add(ModBlocks.blockHutShepherd);
        add(ModBlocks.blockHutSmeltery);
        add(ModBlocks.blockHutSwineHerder);
        add(ModBlocks.blockHutTownHall);
        add(ModBlocks.blockHutWareHouse);
        add(ModBlocks.blockHutBaker);
        add(ModBlocks.blockHutBlacksmith);
        add(ModBlocks.blockHutBuilder);
        add(ModBlocks.blockHutChickenHerder);
        add(ModBlocks.blockHutComposter);
        add(ModBlocks.blockHutCook);
        add(ModBlocks.blockHutCowboy);
        add(ModBlocks.blockHutCrusher);
        add(ModBlocks.blockHutDeliveryman);
        add(ModBlocks.blockHutDyer);
        add(ModBlocks.blockHutEnchanter);
        add(ModBlocks.blockHutFarmer);
        add(ModBlocks.blockHutFisherman);
        add(ModBlocks.blockHutFlorist);
        add(ModBlocks.blockHutGlassblower);
        add(ModBlocks.blockHutHospital);
        add(ModBlocks.blockHutLibrary);
        add(ModBlocks.blockHutMechanic);
        add(ModBlocks.blockHutMiner);
        add(ModBlocks.blockHutPlantation);
        add(ModBlocks.blockHutSawmill);
        add(ModBlocks.blockHutStonemason);
        add(ModBlocks.blockHutTavern);
        add(ModBlocks.blockHutNetherWorker);
        add(ModBlocks.blockHutGraveyard);
        add(ModBlocks.blockHutBeekeeper);
        add(ModBlocks.blockHutUniversity);
        add(ModBlocks.blockHutHome);
        add(ModBlocks.blockHutBarracks);
        add(ModBlocks.blockHutGuardTower);
        add(ModBlocks.blockHutBarracksTower);
        add(ModBlocks.blockHutArchery);
        add(ModBlocks.blockHutCombatAcademy);
    }

    private static void add(Object blockObj) {
        if (blockObj instanceof net.minecraft.world.level.block.Block block) {
            Item item = block.asItem();
            if (item != null) {
                HUT_BLOCK_ITEMS.add(item);
            }
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!TaxConfig.isDisableHutRecipesEnabled()) {
            return;
        }

        ItemStack output = event.getCrafting();
        if (output.isEmpty()) {
            return;
        }

        if (!HUT_BLOCK_ITEMS.contains(output.getItem())) {
            return;
        }

        // Block crafting by clearing result
        output.setCount(0);

        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide) {
            player.sendSystemMessage(Component.literal("Crafting of MineColonies hut blocks is disabled on this server.")
                .withStyle(ChatFormatting.RED));
        }
    }
}








