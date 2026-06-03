package net.machiavelli.minecolonytax.espionage;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MineColonyTax.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpyEntity>> SPY =
            ENTITIES.register("spy",
                    () -> EntityType.Builder.of(SpyEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F) // Same size as a player/citizen
                            .build("spy"));

    @EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEventSubscriber {

        @SubscribeEvent
        public static void onAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(SPY.get(), SpyEntity.createAttributes().build());
        }
    }

    @EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEventSubscriber {

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SPY.get(), SpyEntityRenderer::new);
        }
    }
}
