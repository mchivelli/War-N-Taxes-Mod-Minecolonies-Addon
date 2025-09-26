package net.machiavelli.minecolonytax.datagen;

import com.google.gson.JsonObject;
import com.minecolonies.api.blocks.ModBlocks;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.recipe.DisabledRecipeSerializer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Data generator that creates disabled recipe files to override Minecolonies hut recipes
 * when the configuration option is enabled.
 */
public class DisabledRecipeProvider extends RecipeProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DisabledRecipeProvider.class);
    
    // Set of building hut blocks that have taxes or maintenance costs and should have recipes disabled
    private static final Set<ResourceLocation> DISABLED_HUT_RECIPES = new HashSet<>();
    
    static {
        // Initialize the set of hut blocks that should have recipes disabled
        // These are all the buildings that have taxes or maintenance costs defined in TaxConfig
        
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
     * Helper method to add a hut block to the disabled recipes set
     */
    private static void addHutBlock(Object block) {
        if (block != null) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey((net.minecraft.world.level.block.Block) block);
            if (blockId != null) {
                DISABLED_HUT_RECIPES.add(blockId);
            }
        }
    }
    
    public DisabledRecipeProvider(PackOutput output) {
        super(output);
    }
    
    @Override
    protected void buildRecipes(@NotNull Consumer<FinishedRecipe> consumer) {
        // Only generate disabled recipes if the feature is enabled
        if (!TaxConfig.isDisableHutRecipesEnabled()) {
            LOGGER.info("Recipe disabling is not enabled - skipping disabled recipe generation");
            return;
        }
        
        LOGGER.info("Recipe disabling is enabled - generating disabled recipe files for buildings with taxes/maintenance costs");
        
        // Generate disabled recipes for each hut block
        for (ResourceLocation blockId : DISABLED_HUT_RECIPES) {
            // Create a disabled recipe that overrides the original
            ItemStack resultItem = new ItemStack(ForgeRegistries.BLOCKS.getValue(blockId));
            if (!resultItem.isEmpty()) {
                ResourceLocation recipeId = new ResourceLocation("minecolonytax", "disabled_" + blockId.getPath());
                
                consumer.accept(new FinishedRecipe() {
                    @Override
                    public void serializeRecipeData(@NotNull JsonObject json) {
                        json.addProperty("type", "minecolonytax:disabled_recipe");
                        json.add("result", itemStackToJson(resultItem));
                    }
                    
                    @Override
                    public @NotNull ResourceLocation getId() {
                        return recipeId;
                    }
                    
                    @Override
                    public @NotNull RecipeSerializer<?> getType() {
                        return DisabledRecipeSerializer.INSTANCE;
                    }
                    
                    
                    @Override
                    public @NotNull ResourceLocation getAdvancementId() {
                        return new ResourceLocation("");
                    }
                    
                    @Override
                    public @NotNull JsonObject serializeAdvancement() {
                        return new JsonObject();
                    }
                });
                
                LOGGER.debug("Generated disabled recipe for: {} -> {}", blockId, recipeId);
            }
        }
        
        LOGGER.info("Generated {} disabled recipe files", DISABLED_HUT_RECIPES.size());
    }
    
    /**
     * Convert an ItemStack to JSON for recipe serialization
     */
    private @NotNull JsonObject itemStackToJson(@NotNull ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        if (stack.getCount() > 1) {
            json.addProperty("count", stack.getCount());
        }
        return json;
    }
    
    /**
     * Get the set of disabled hut recipe IDs
     * @return Set of ResourceLocation IDs for disabled hut recipes
     */
    public static Set<ResourceLocation> getDisabledHutRecipes() {
        return new HashSet<>(DISABLED_HUT_RECIPES);
    }
    
    /**
     * Check if a specific block ID should have its recipe disabled
     * @param blockId The block ID to check
     * @return true if the recipe should be disabled
     */
    public static boolean shouldDisableRecipe(ResourceLocation blockId) {
        return TaxConfig.isDisableHutRecipesEnabled() && DISABLED_HUT_RECIPES.contains(blockId);
    }
}
