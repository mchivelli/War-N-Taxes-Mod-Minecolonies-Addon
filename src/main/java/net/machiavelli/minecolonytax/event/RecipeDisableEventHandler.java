package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.blocks.ModBlocks;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Event handler for disabling Minecolonies building hut recipes when configured.
 * This ensures that buildings with taxes/maintenance costs must be obtained through shops.
 */
@Mod.EventBusSubscriber
public class RecipeDisableEventHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeDisableEventHandler.class);
    
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
    
    /**
     * Event handler that logs when recipe disabling is enabled
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        // Check if recipe disabling is enabled
        if (TaxConfig.isDisableHutRecipesEnabled()) {
            LOGGER.info("Recipe disabling is enabled - hut recipes for buildings with taxes/maintenance costs should be disabled");
            LOGGER.info("The following building hut recipes should be disabled:");
            for (ResourceLocation blockId : DISABLED_HUT_RECIPES) {
                LOGGER.info("  - {} (block: {})", blockId, blockId);
            }
        }
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
