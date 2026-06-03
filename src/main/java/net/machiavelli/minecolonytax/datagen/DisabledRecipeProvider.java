package net.machiavelli.minecolonytax.datagen;

import com.minecolonies.api.blocks.ModBlocks;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.recipe.DisabledRecipe;
import net.machiavelli.minecolonytax.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data generator that creates disabled recipe files to override MineColonies hut recipes
 * when the configuration option is enabled.
 */
public class DisabledRecipeProvider extends RecipeProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisabledRecipeProvider.class);

    // Set of building hut blocks that have taxes or maintenance costs and should have recipes disabled
    private static final Set<ResourceLocation> DISABLED_HUT_RECIPES = new HashSet<>();

    static {
        // Buildings with taxes (from BUILDING_TAXES)
        addHutBlock(ModBlocks.blockHutAlchemist);
        addHutBlock(ModBlocks.blockHutConcreteMixer);
        addHutBlock(ModBlocks.blockHutFletcher);
        addHutBlock(ModBlocks.blockHutLumberjack);
        addHutBlock(ModBlocks.blockHutRabbitHutch);
        addHutBlock(ModBlocks.blockHutShepherd);
        addHutBlock(ModBlocks.blockHutSmeltery);
        addHutBlock(ModBlocks.blockHutSwineHerder);
        addHutBlock(ModBlocks.blockHutTownHall);
        addHutBlock(ModBlocks.blockHutWareHouse);
        addHutBlock(ModBlocks.blockHutBaker);
        addHutBlock(ModBlocks.blockHutBlacksmith);
        addHutBlock(ModBlocks.blockHutBuilder);
        addHutBlock(ModBlocks.blockHutChickenHerder);
        addHutBlock(ModBlocks.blockHutComposter);
        addHutBlock(ModBlocks.blockHutCook);
        addHutBlock(ModBlocks.blockHutCowboy);
        addHutBlock(ModBlocks.blockHutCrusher);
        addHutBlock(ModBlocks.blockHutDeliveryman);
        addHutBlock(ModBlocks.blockHutDyer);
        addHutBlock(ModBlocks.blockHutEnchanter);
        addHutBlock(ModBlocks.blockHutFarmer);
        addHutBlock(ModBlocks.blockHutFisherman);
        addHutBlock(ModBlocks.blockHutFlorist);
        addHutBlock(ModBlocks.blockHutGlassblower);
        addHutBlock(ModBlocks.blockHutHospital);
        addHutBlock(ModBlocks.blockHutLibrary);
        addHutBlock(ModBlocks.blockHutMechanic);
        addHutBlock(ModBlocks.blockHutMiner);
        addHutBlock(ModBlocks.blockHutPlantation);
        addHutBlock(ModBlocks.blockHutSawmill);
        addHutBlock(ModBlocks.blockHutStonemason);
        addHutBlock(ModBlocks.blockHutTavern);
        addHutBlock(ModBlocks.blockHutNetherWorker);
        addHutBlock(ModBlocks.blockHutGraveyard);
        addHutBlock(ModBlocks.blockHutBeekeeper);
        addHutBlock(ModBlocks.blockHutUniversity);
        addHutBlock(ModBlocks.blockHutHome);

        // Buildings with maintenance costs (from BUILDING_MAINTENANCE)
        addHutBlock(ModBlocks.blockHutBarracks);
        addHutBlock(ModBlocks.blockHutGuardTower);
        addHutBlock(ModBlocks.blockHutBarracksTower);
        addHutBlock(ModBlocks.blockHutArchery);
        addHutBlock(ModBlocks.blockHutCombatAcademy);
    }

    /**
     * Helper method to add a hut block to the disabled recipes set.
     */
    private static void addHutBlock(Object block) {
        if (block instanceof Block b) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(b);
            if (blockId != null) {
                DISABLED_HUT_RECIPES.add(blockId);
            }
        }
    }

    public DisabledRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput output) {
        if (!TaxConfig.isDisableHutRecipesEnabled()) {
            LOGGER.info("Recipe disabling is not enabled - skipping disabled recipe generation");
            return;
        }

        LOGGER.info("Recipe disabling is enabled - generating disabled recipe files for buildings with taxes/maintenance costs");

        for (ResourceLocation blockId : DISABLED_HUT_RECIPES) {
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "disabled_" + blockId.getPath());
            DisabledRecipe recipe = new DisabledRecipe(CraftingBookCategory.MISC);
            output.accept(recipeId, recipe, null);
            LOGGER.debug("Generated disabled recipe for: {} -> {}", blockId, recipeId);
        }

        LOGGER.info("Generated {} disabled recipe files", DISABLED_HUT_RECIPES.size());
    }

    /**
     * Get the set of disabled hut recipe IDs.
     */
    public static Set<ResourceLocation> getDisabledHutRecipes() {
        return new HashSet<>(DISABLED_HUT_RECIPES);
    }

    /**
     * Check if a specific block ID should have its recipe disabled.
     */
    public static boolean shouldDisableRecipe(ResourceLocation blockId) {
        return TaxConfig.isDisableHutRecipesEnabled() && DISABLED_HUT_RECIPES.contains(blockId);
    }
}
