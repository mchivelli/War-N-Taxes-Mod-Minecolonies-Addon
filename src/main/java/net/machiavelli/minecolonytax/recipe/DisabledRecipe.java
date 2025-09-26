package net.machiavelli.minecolonytax.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A disabled recipe that cannot be crafted and serves as a placeholder
 * for disabled Minecolonies building hut recipes.
 */
public class DisabledRecipe extends CustomRecipe {
    
    private final ItemStack resultItem;
    
    public DisabledRecipe(ResourceLocation id, ItemStack resultItem) {
        super(id, CraftingBookCategory.MISC);
        this.resultItem = resultItem;
    }
    
    @Override
    public boolean matches(@NotNull CraftingContainer container, @NotNull Level level) {
        // This recipe never matches, effectively disabling it
        return false;
    }
    
    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registryAccess) {
        // Return empty stack since this recipe is disabled
        return ItemStack.EMPTY;
    }
    
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        // This recipe cannot be crafted in any dimensions
        return false;
    }
    
    @Override
    public @NotNull ItemStack getResultItem(@NotNull RegistryAccess registryAccess) {
        // Return the result item for display purposes, but it can't actually be crafted
        return resultItem.copy();
    }
    
    @Override
    public @NotNull String getGroup() {
        return "disabled_hut_recipe";
    }
    
    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return DisabledRecipeSerializer.INSTANCE;
    }
}
