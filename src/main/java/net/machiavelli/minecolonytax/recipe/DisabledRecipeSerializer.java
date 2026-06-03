package net.machiavelli.minecolonytax.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class DisabledRecipeSerializer implements RecipeSerializer<DisabledRecipe> {

    public static final DisabledRecipeSerializer INSTANCE = new DisabledRecipeSerializer();

    private static final DisabledRecipe DUMMY = new DisabledRecipe(CraftingBookCategory.MISC);

    public static final MapCodec<DisabledRecipe> CODEC = MapCodec.unit(DUMMY);

    public static final StreamCodec<RegistryFriendlyByteBuf, DisabledRecipe> STREAM_CODEC =
        StreamCodec.unit(DUMMY);

    private DisabledRecipeSerializer() {}

    @Override
    public MapCodec<DisabledRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DisabledRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
