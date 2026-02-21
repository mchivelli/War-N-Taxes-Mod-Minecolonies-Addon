package net.machiavelli.minecolonytax.espionage;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,
            MineColonyTax.MOD_ID);

    public static final RegistryObject<EntityType<SpyEntity>> SPY = ENTITIES.register("spy",
            () -> EntityType.Builder.of(SpyEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F) // Same size as a player/citizen
                    .build("spy"));

    @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventSubscriber {

        @SubscribeEvent
        public static void onAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(SPY.get(), SpyEntity.createAttributes().build());
        }

    }

    @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEventSubscriber {

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SPY.get(), SpyEntityRenderer::new);
        }
    }
}
