package net.machiavelli.minecolonytax.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

/**
 * Serializer for disabled recipes that cannot be crafted.
 */
public class DisabledRecipeSerializer implements RecipeSerializer<DisabledRecipe> {
    
    public static final DisabledRecipeSerializer INSTANCE = new DisabledRecipeSerializer();
    
    private DisabledRecipeSerializer() {}
    
    @Override
    public @NotNull DisabledRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        // Parse the result item from JSON
        ItemStack resultItem = ShapedRecipe.itemStackFromJson(json.getAsJsonObject("result"));
        return new DisabledRecipe(recipeId, resultItem);
    }
    
    @Override
    public @NotNull DisabledRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        ItemStack resultItem = buffer.readItem();
        return new DisabledRecipe(recipeId, resultItem);
    }
    
    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull DisabledRecipe recipe) {
        buffer.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
    }
}
