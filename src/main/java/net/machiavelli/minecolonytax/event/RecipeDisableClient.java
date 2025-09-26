package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.blocks.ModBlocks;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Client-side recipe removal so the recipe book/JEI no longer show hut recipes when disabled.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeDisableClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeDisableClient.class);

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
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        if (!TaxConfig.isDisableHutRecipesEnabled()) {
            return;
        }
        try {
            RecipeManager manager = event.getRecipeManager();
            List<? extends Recipe<?>> craftingRecipes = manager.getAllRecipesFor(RecipeType.CRAFTING);
            Set<ResourceLocation> toRemove = craftingRecipes.stream()
                .filter(RecipeDisableClient::isHutOutput)
                .map(Recipe::getId)
                .collect(Collectors.toSet());

            if (toRemove.isEmpty()) {
                return;
            }

            removeFromManager(manager, toRemove);
            LOGGER.info("RecipeDisabler(Client): Disabled {} MineColonies hut crafting recipes on client.", toRemove.size());
        } catch (Throwable t) {
            LOGGER.error("RecipeDisabler(Client): Failed to disable hut recipes on client.", t);
        }
    }

    private static boolean isHutOutput(Recipe<?> recipe) {
        try {
            ItemStack out = recipe.getResultItem(null);
            if (out.isEmpty()) {
                return false;
            }
            return HUT_BLOCK_ITEMS.contains(out.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromManager(RecipeManager manager, Set<ResourceLocation> toRemove) throws Exception {
        String[] recipesFieldNames = new String[] {"recipes", "byType", "f_44006_"};
        String[] byNameFieldNames = new String[] {"byName", "f_44007_"};

        Field recipesField = findField(manager.getClass(), recipesFieldNames);
        Field byNameField = findField(manager.getClass(), byNameFieldNames);

        if (recipesField == null || byNameField == null) {
            throw new IllegalStateException("RecipeManager fields not found (client)");
        }

        recipesField.setAccessible(true);
        byNameField.setAccessible(true);

        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipesByType =
            (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(manager);
        Map<ResourceLocation, Recipe<?>> byName =
            (Map<ResourceLocation, Recipe<?>>) byNameField.get(manager);

        Map<ResourceLocation, Recipe<?>> craftingMap = recipesByType.get(RecipeType.CRAFTING);
        if (craftingMap == null) {
            return;
        }

        for (ResourceLocation id : toRemove) {
            craftingMap.remove(id);
            byName.remove(id);
        }
    }

    private static Field findField(Class<?> clazz, String[] names) {
        for (String n : names) {
            try {
                return clazz.getDeclaredField(n);
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }
}








