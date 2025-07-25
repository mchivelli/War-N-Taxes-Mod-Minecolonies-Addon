package net.machiavelli.minecolonytax.data;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Language provider for MineColonyTax mod.
 * This class is used to generate language files during development
 * and can be used as a reference for creating datapack-based language overrides.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MCTLanguageProvider {

    /**
     * Event handler for data generation.
     * Registers language providers for the mod.
     * 
     * @param event The gather data event
     */
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator dataGenerator = event.getGenerator();
        PackOutput packOutput = dataGenerator.getPackOutput();

        dataGenerator.addProvider(event.includeClient(), new EnglishLanguageProvider(packOutput));
        dataGenerator.addProvider(event.includeClient(), new RussianLanguageProvider(packOutput));
    }

    /**
     * Base abstract language provider to define common translation keys.
     */
    public abstract static class BaseLanguageProvider extends LanguageProvider {
        public BaseLanguageProvider(PackOutput output, String locale) {
            super(output, MineColonyTax.MOD_ID, locale);
        }

        /**
         * Add commonly used translation keys to make it easier to override in datapacks.
         * This method demonstrates the keys available to override.
         */
        @Override
        protected abstract void addTranslations();
    }

    /**
     * English language provider for reference.
     */
    public static class EnglishLanguageProvider extends BaseLanguageProvider {
        public EnglishLanguageProvider(PackOutput output) {
            super(output, "en_us");
        }

        @Override
        protected void addTranslations() {
            // Tax related translations
            add("command.checktax.self", "Colony: %s - Stored Tax Revenue: %d");
            add("command.checktax.other", "%s's colony: %s - Stored Tax Revenue: %d coins");
            add("command.checktax.no_colonies", "You are not an owner or officer of any colonies.");
            add("command.claimtax.success", "You have claimed %d in tax revenue from colony %s.");
            add("command.claimtax.no_tax", "No taxes available to claim for colony %s.");
            
            // This is a small subset - in a real data generation scenario,
            // all keys would be defined here.
            
            // War related translations
            add("war.declare.title", "📜 WAR DECLARED! 📜");
            add("war.declare.body", "%s has declared WAR upon %s!");
            add("war.begin.title", "🔥 THE WAR BEGINS! 🔥");
            
            // This is just an example showing the structure, not all translations are included
        }
    }

    /**
     * Russian language provider for reference.
     */
    public static class RussianLanguageProvider extends BaseLanguageProvider {
        public RussianLanguageProvider(PackOutput output) {
            super(output, "ru_ru");
        }

        @Override
        protected void addTranslations() {
            // Tax related translations
            add("command.checktax.self", "Колония: %s - Накопленный налоговый доход: %d");
            add("command.checktax.other", "Колония %s: %s - Накопленный налоговый доход: %d монет");
            add("command.checktax.no_colonies", "Вы не являетесь владельцем или офицером ни одной колонии.");
            add("command.claimtax.success", "Вы получили %d налогового дохода от колонии %s.");
            add("command.claimtax.no_tax", "В колонии %s нет налогов для получения.");
            
            // This is a small subset - in a real data generation scenario,
            // all keys would be defined here.
            
            // War related translations
            add("war.declare.title", "📜 ОБЪЯВЛЕНА ВОЙНА! 📜");
            add("war.declare.body", "%s объявил ВОЙНУ %s!");
            add("war.begin.title", "🔥 ВОЙНА НАЧАЛАСЬ! 🔥");
            
            // This is just an example showing the structure, not all translations are included
        }
    }
}
