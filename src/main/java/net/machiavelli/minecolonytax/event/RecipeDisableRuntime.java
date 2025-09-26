package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.blocks.ModBlocks;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runtime disabler that removes MineColonies hut crafting recipes when the config is enabled.
 * This runs after datapacks have loaded (server start and datapack reload) and edits the RecipeManager maps via reflection.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeDisableRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeDisableRuntime.class);

    private static final Set<ResourceLocation> HUT_BLOCK_IDS = new HashSet<>();
    private static final Set<Item> HUT_BLOCK_ITEMS = new HashSet<>();

    static {
        // Populate hut block ids/items (tax/maintenance buildings)
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
        // Maintenance buildings (guards/military)
        add(ModBlocks.blockHutBarracks);
        add(ModBlocks.blockHutGuardTower);
        add(ModBlocks.blockHutBarracksTower);
        add(ModBlocks.blockHutArchery);
        add(ModBlocks.blockHutCombatAcademy);
    }

    private static void add(Object blockObj) {
        if (blockObj instanceof net.minecraft.world.level.block.Block block) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id != null) {
                HUT_BLOCK_IDS.add(id);
            }
            Item item = block.asItem();
            if (item != null) {
                HUT_BLOCK_ITEMS.add(item);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!TaxConfig.isDisableHutRecipesEnabled()) {
            return;
        }
        disableHutRecipes(event.getServer());
    }

    // Optional: handle datapack reloads via a separate listener if needed

    private static void disableHutRecipes(MinecraftServer server) {
        try {
            RecipeManager manager = server.getRecipeManager();

            // Collect crafting recipes that output hut blocks
            List<? extends Recipe<?>> craftingRecipes = manager.getAllRecipesFor(RecipeType.CRAFTING);
            Set<ResourceLocation> toRemove = craftingRecipes.stream()
                .filter(r -> isHutOutput(r, server))
                .map(Recipe::getId)
                .collect(Collectors.toSet());

            if (toRemove.isEmpty()) {
                LOGGER.info("RecipeDisabler: No hut crafting recipes found to remove (maybe already removed).");
                return;
            }

            // Reflectively remove from RecipeManager maps
            removeFromManager(manager, toRemove);
            LOGGER.info("RecipeDisabler: Disabled {} MineColonies hut crafting recipes.", toRemove.size());
        } catch (Throwable t) {
            LOGGER.error("RecipeDisabler: Failed to disable hut recipes.", t);
        }
    }

    private static boolean isHutOutput(Recipe<?> recipe, MinecraftServer server) {
        try {
            ItemStack out = recipe.getResultItem(server.registryAccess());
            return !out.isEmpty() && HUT_BLOCK_ITEMS.contains(out.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromManager(RecipeManager manager, Set<ResourceLocation> toRemove) throws Exception {
        // Potential field names across mappings
        String[] recipesFieldNames = new String[] {"recipes", "byType", "f_44006_"};
        String[] byNameFieldNames = new String[] {"byName", "f_44007_"};

        Field recipesField = findField(manager.getClass(), recipesFieldNames);
        Field byNameField = findField(manager.getClass(), byNameFieldNames);

        if (recipesField == null || byNameField == null) {
            throw new IllegalStateException("RecipeManager fields not found (mappings mismatch)");
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

        // Remove from both maps
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


