package net.machiavelli.minecolonytax.espionage;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SpyEntityRenderer extends HumanoidMobRenderer<SpyEntity, HumanoidModel<SpyEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/steve.png");

    public SpyEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SpyEntity entity) {
        return TEXTURE;
    }
}
