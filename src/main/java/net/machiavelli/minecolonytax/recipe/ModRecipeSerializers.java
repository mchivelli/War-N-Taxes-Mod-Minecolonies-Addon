package net.machiavelli.minecolonytax.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registration class for custom recipe serializers
 */
public class ModRecipeSerializers {
    
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = 
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, "minecolonytax");
    
    public static final RegistryObject<RecipeSerializer<DisabledRecipe>> DISABLED_RECIPE = 
        RECIPE_SERIALIZERS.register("disabled_recipe", () -> DisabledRecipeSerializer.INSTANCE);
}
