package net.machiavelli.minecolonytax.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Data generator for MineColonyTax mod
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class ModDataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();

        // Add recipe providers
        generator.addProvider(event.includeServer(), new DisabledRecipeProvider(packOutput, event.getLookupProvider()));
    }
}
