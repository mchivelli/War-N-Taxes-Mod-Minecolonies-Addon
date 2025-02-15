package net.machiavelli.minecolonytax;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public class TaxConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static ForgeConfigSpec CONFIG;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SDM_SHOP_CONVERSION;
    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM_TAG;
    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM_NAME;

    public static final ForgeConfigSpec.IntValue MAX_TAX_REVENUE;

    // Maps for storing building taxes and upgrade taxes
    public static final Map<String, ForgeConfigSpec.DoubleValue> BUILDING_TAXES = new HashMap<>();
    public static final Map<String, ForgeConfigSpec.DoubleValue> UPGRADE_TAXES = new HashMap<>();

    // Map to link full building class names to short config names
    private static final Map<String, String> CLASS_NAME_TO_SHORT_NAME = new HashMap<>();

    // Define the tax interval in minutes
    public static final ForgeConfigSpec.IntValue TAX_INTERVAL_MINUTES;

    public static final ForgeConfigSpec.IntValue ATTACKER_GRACE_PERIOD_MINUTES;
    public static final ForgeConfigSpec.IntValue RAID_GRACE_PERIOD_MINUTES;
    public static final ForgeConfigSpec.IntValue MAX_RAID_DURATION_MINUTES;
    public static final ForgeConfigSpec.IntValue RAID_TAX_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<List<Double>> RAID_TAX_PERCENTAGES;
    public static final ForgeConfigSpec.IntValue WAR_DURATION_MINUTES;
    public static final ForgeConfigSpec.IntValue MIN_GUARDS_TO_WAGE_WAR;


    static {

        BUILDER.push("War Settings");

        ATTACKER_GRACE_PERIOD_MINUTES = BUILDER.comment("Grace period between declaring wars (minutes)")
                .defineInRange("AttackerGracePeriodMinutes", 120, 1, 1440); // Default 2 hours

        RAID_GRACE_PERIOD_MINUTES = BUILDER.comment("Grace period between raids (minutes)")
                .defineInRange("RaidGracePeriodMinutes", 1, 1, 1440); // Default 1 minute

        MAX_RAID_DURATION_MINUTES = BUILDER.comment("Maximum raid duration (minutes)")
                .defineInRange("MaxRaidDurationMinutes", 20, 1, 1440);

        RAID_TAX_INTERVAL_SECONDS = BUILDER.comment("Interval between tax transfers during raids (seconds)")
                .defineInRange("RaidTaxIntervalSeconds", 10, 5, 3600);

        RAID_TAX_PERCENTAGES = BUILDER.comment("Tax transfer percentages during raids (comma-separated decimals)")
                .define("RaidTaxPercentages", List.of(0.1, 0.3, 0.5, 0.7));

        WAR_DURATION_MINUTES = BUILDER.comment("War duration (minutes)")
                .defineInRange("WarDurationMinutes", 10, 1, 1440);

        MIN_GUARDS_TO_WAGE_WAR = BUILDER.comment("Minimum guards required to declare war")
                .defineInRange("MinGuardsToWageWar", 5, 1, 100);

        BUILDER.pop();


        BUILDER.push("Building Taxes");

        // Add base and upgrade taxes for all buildings
        BUILDING_TAXES.put("archery", BUILDER.comment("Base tax for Archery")
                .defineInRange("archery", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("archery", BUILDER.comment("Tax increase per level for Archery")
                .defineInRange("archeryUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("alchemist", BUILDER.comment("Base tax for Alchemist")
                .defineInRange("alchemist", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("alchemist", BUILDER.comment("Tax increase per level for Alchemist")
                .defineInRange("alchemistUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("concretemixer", BUILDER.comment("Base tax for Concrete Mixer")
                .defineInRange("concretemixer", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("concretemixer", BUILDER.comment("Tax increase per level for Concrete Mixer")
                .defineInRange("concretemixerUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("fletcher", BUILDER.comment("Base tax for Fletcher")
                .defineInRange("fletcher", 9.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("fletcher", BUILDER.comment("Tax increase per level for Fletcher")
                .defineInRange("fletcherUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("lumberjack", BUILDER.comment("Base tax for Lumberjack")
                .defineInRange("lumberjack", 11.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("lumberjack", BUILDER.comment("Tax increase per level for Lumberjack")
                .defineInRange("lumberjackUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("rabbithutch", BUILDER.comment("Base tax for Rabbit Hutch")
                .defineInRange("rabbithutch", 8.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("rabbithutch", BUILDER.comment("Tax increase per level for Rabbit Hutch")
                .defineInRange("rabbithutchUpgrade", 2.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("shepherd", BUILDER.comment("Base tax for Shepherd")
                .defineInRange("shepherd", 9.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("shepherd", BUILDER.comment("Tax increase per level for Shepherd")
                .defineInRange("shepherdUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("smeltery", BUILDER.comment("Base tax for Smeltery")
                .defineInRange("smeltery", 15.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("smeltery", BUILDER.comment("Tax increase per level for Smeltery")
                .defineInRange("smelteryUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("swineherder", BUILDER.comment("Base tax for Swine Herder")
                .defineInRange("swineherder", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("swineherder", BUILDER.comment("Tax increase per level for Swine Herder")
                .defineInRange("swineherderUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("townhall", BUILDER.comment("Base tax for Town Hall")
                .defineInRange("townhall", 20.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("townhall", BUILDER.comment("Tax increase per level for Town Hall")
                .defineInRange("townhallUpgrade", 8.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("warehousedeliveryman", BUILDER.comment("Base tax for Warehouse Deliveryman")
                .defineInRange("warehousedeliveryman", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("warehousedeliveryman", BUILDER.comment("Tax increase per level for Warehouse Deliveryman")
                .defineInRange("warehousedeliverymanUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("bakery", BUILDER.comment("Base tax for Bakery")
                .defineInRange("bakery", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("bakery", BUILDER.comment("Tax increase per level for Bakery")
                .defineInRange("bakeryUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("barracks", BUILDER.comment("Base tax for Barracks")
                .defineInRange("barracks", 15.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("barracks", BUILDER.comment("Tax increase per level for Barracks")
                .defineInRange("barracksUpgrade", 7.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("barrackstower", BUILDER.comment("Base tax for Barracks Tower")
                .defineInRange("barrackstower", 14.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("barrackstower", BUILDER.comment("Tax increase per level for Barracks Tower")
                .defineInRange("barrackstowerUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("blacksmith", BUILDER.comment("Base tax for Blacksmith")
                .defineInRange("blacksmith", 18.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("blacksmith", BUILDER.comment("Tax increase per level for Blacksmith")
                .defineInRange("blacksmithUpgrade", 8.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("builder", BUILDER.comment("Base tax for Builder")
                .defineInRange("builder", 8.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("builder", BUILDER.comment("Tax increase per level for Builder")
                .defineInRange("builderUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("chickenherder", BUILDER.comment("Base tax for Chicken Herder")
                .defineInRange("chickenherder", 9.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("chickenherder", BUILDER.comment("Tax increase per level for Chicken Herder")
                .defineInRange("chickenherderUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("combatacademy", BUILDER.comment("Base tax for Combat Academy")
                .defineInRange("combatacademy", 14.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("combatacademy", BUILDER.comment("Tax increase per level for Combat Academy")
                .defineInRange("combatacademyUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("composter", BUILDER.comment("Base tax for Composter")
                .defineInRange("composter", 6.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("composter", BUILDER.comment("Tax increase per level for Composter")
                .defineInRange("composterUpgrade", 2.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("cook", BUILDER.comment("Base tax for Cook")
                .defineInRange("cook", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("cook", BUILDER.comment("Tax increase per level for Cook")
                .defineInRange("cookUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("cowboy", BUILDER.comment("Base tax for Cowboy")
                .defineInRange("cowboy", 9.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("cowboy", BUILDER.comment("Tax increase per level for Cowboy")
                .defineInRange("cowboyUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("crusher", BUILDER.comment("Base tax for Crusher")
                .defineInRange("crusher", 13.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("crusher", BUILDER.comment("Tax increase per level for Crusher")
                .defineInRange("crusherUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("deliveryman", BUILDER.comment("Base tax for Deliveryman")
                .defineInRange("deliveryman", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("deliveryman", BUILDER.comment("Tax increase per level for Deliveryman")
                .defineInRange("deliverymanUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("farmer", BUILDER.comment("Base tax for Farmer")
                .defineInRange("farmer", 11.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("farmer", BUILDER.comment("Tax increase per level for Farmer")
                .defineInRange("farmerUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("fisherman", BUILDER.comment("Base tax for Fisherman")
                .defineInRange("fisherman", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("fisherman", BUILDER.comment("Tax increase per level for Fisherman")
                .defineInRange("fishermanUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("guardtower", BUILDER.comment("Base tax for Guard Tower")
                .defineInRange("guardtower", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("guardtower", BUILDER.comment("Tax increase per level for Guard Tower")
                .defineInRange("guardtowerUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("home", BUILDER.comment("Base tax for Residence")
                .defineInRange("home", 5.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("home", BUILDER.comment("Tax increase per level for Residence")
                .defineInRange("homeUpgrade", 2.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("library", BUILDER.comment("Base tax for Library")
                .defineInRange("library", 13.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("library", BUILDER.comment("Tax increase per level for Library")
                .defineInRange("libraryUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("university", BUILDER.comment("Base tax for University")
                .defineInRange("university", 20.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("university", BUILDER.comment("Tax increase per level for University")
                .defineInRange("universityUpgrade", 10.0, 0.0, Double.MAX_VALUE));

        // Additional buildings
        BUILDING_TAXES.put("warehouse", BUILDER.comment("Base tax for Warehouse")
                .defineInRange("warehouse", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("warehouse", BUILDER.comment("Tax increase per level for Warehouse")
                .defineInRange("warehouseUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("tavern", BUILDER.comment("Base tax for Tavern")
                .defineInRange("tavern", 14.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("tavern", BUILDER.comment("Tax increase per level for Tavern")
                .defineInRange("tavernUpgrade", 6.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("miner", BUILDER.comment("Base tax for Miner")
                .defineInRange("miner", 11.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("miner", BUILDER.comment("Tax increase per level for Miner")
                .defineInRange("minerUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("sawmill", BUILDER.comment("Base tax for Sawmill")
                .defineInRange("sawmill", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("sawmill", BUILDER.comment("Tax increase per level for Sawmill")
                .defineInRange("sawmillUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("stonemason", BUILDER.comment("Base tax for Stonemason")
                .defineInRange("stonemason", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("stonemason", BUILDER.comment("Tax increase per level for Stonemason")
                .defineInRange("stonemasonUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("florist", BUILDER.comment("Base tax for Florist")
                .defineInRange("florist", 8.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("florist", BUILDER.comment("Tax increase per level for Florist")
                .defineInRange("floristUpgrade", 2.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("enchanter", BUILDER.comment("Base tax for Enchanter")
                .defineInRange("enchanter", 15.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("enchanter", BUILDER.comment("Tax increase per level for Enchanter")
                .defineInRange("enchanterUpgrade", 5.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("hospital", BUILDER.comment("Base tax for Hospital")
                .defineInRange("hospital", 20.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("hospital", BUILDER.comment("Tax increase per level for Hospital")
                .defineInRange("hospitalUpgrade", 8.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("glassblower", BUILDER.comment("Base tax for Glassblower")
                .defineInRange("glassblower", 10.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("glassblower", BUILDER.comment("Tax increase per level for Glassblower")
                .defineInRange("glassblowerUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("dyer", BUILDER.comment("Base tax for Dyer")
                .defineInRange("dyer", 9.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("dyer", BUILDER.comment("Tax increase per level for Dyer")
                .defineInRange("dyerUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("mechanic", BUILDER.comment("Base tax for Mechanic")
                .defineInRange("mechanic", 11.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("mechanic", BUILDER.comment("Tax increase per level for Mechanic")
                .defineInRange("mechanicUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("plantation", BUILDER.comment("Base tax for Plantation")
                .defineInRange("plantation", 12.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("plantation", BUILDER.comment("Tax increase per level for Plantation")
                .defineInRange("plantationUpgrade", 4.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("graveyard", BUILDER.comment("Base tax for Graveyard")
                .defineInRange("graveyard", 7.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("graveyard", BUILDER.comment("Tax increase per level for Graveyard")
                .defineInRange("graveyardUpgrade", 2.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("beekeeper", BUILDER.comment("Base tax for Beekeeper")
                .defineInRange("beekeeper", 9.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("beekeeper", BUILDER.comment("Tax increase per level for Beekeeper")
                .defineInRange("beekeeperUpgrade", 3.0, 0.0, Double.MAX_VALUE));

        BUILDING_TAXES.put("netherworker", BUILDER.comment("Base tax for Nether Worker")
                .defineInRange("netherworker", 15.0, 0.0, Double.MAX_VALUE));
        UPGRADE_TAXES.put("netherworker", BUILDER.comment("Tax increase per level for Nether Worker")
                .defineInRange("netherworkerUpgrade", 6.0, 0.0, Double.MAX_VALUE));


        BUILDER.pop();

        // Add mapping for full class names to short names used in config
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.barracks", "barracks");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.guardtower", "guardtower");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.archery", "archery");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.bakery", "bakery");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.blacksmith", "blacksmith");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.builder", "builder");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.chickenherder", "chickenherder");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.combatacademy", "combatacademy");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.composter", "composter");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.cook", "cook");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.cowboy", "cowboy");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.crusher", "crusher");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.deliveryman", "deliveryman");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.farmer", "farmer");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.fisherman", "fisherman");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.home", "residence");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.library", "library");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.university", "university");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.warehouse", "warehouse");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.tavern", "tavern");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.miner", "miner");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.sawmill", "sawmill");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.stonemason", "stonemason");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.florist", "florist");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.enchanter", "enchanter");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.hospital", "hospital");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.glassblower", "glassblower");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.dyer", "dyer");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.mechanic", "mechanic");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.plantation", "plantation");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.graveyard", "graveyard");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.beekeeper", "beekeeper");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.netherworker", "netherworker");
        //Added
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.alchemist", "alchemist");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.concretemixer", "concretemixer");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.fletcher", "fletcher");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.lumberjack", "lumberjack");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.rabbithutch", "rabbithutch");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.shepherd", "shepherd");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.smeltery", "smeltery");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.swineherder", "swineherder");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.townhall", "townhall");
        CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.warehousedeliveryman", "warehousedeliveryman");


        // Add other mappings as needed for all buildings

        // Define general settings
        TAX_INTERVAL_MINUTES = BUILDER.comment("Tax generation interval in minutes")
                .defineInRange("TaxIntervalMinutes", 60, 1, 1440); // Default 60 minutes, min 1, max 1440 (1 day)

        MAX_TAX_REVENUE = BUILDER.comment("Maximum tax revenue a colony can store before it stops generating further taxes")
                .defineInRange("MaxTaxRevenue", 5000, 1, Integer.MAX_VALUE);

        ENABLE_SDM_SHOP_CONVERSION = BUILDER.comment("Enable SDMShop conversion (true = enable, false = disable).")
                .define("EnableSDMShopConversion", true);

        CURRENCY_ITEM_TAG = BUILDER.comment("The item tag for the custom currency (e.g., 'forge:ingots/gold').")
                .define("CurrencyItemTag", "minecraft:gold_ingot");

        CURRENCY_ITEM_NAME = BUILDER.comment("The item name for the custom currency (e.g., 'minecraft:emerald').")
                .define("CurrencyItemName", "minecraft:emerald");


        CONFIG = BUILDER.build();
    }

    /**
     * Loads the configuration file.
     */
    public static void loadConfig(ForgeConfigSpec config, String path) {
        final Path configPath = FMLPaths.CONFIGDIR.get().resolve(path);
        final CommentedFileConfig file = CommentedFileConfig.builder(configPath)
                .sync()
                .autosave()
                .preserveInsertionOrder()
                .build();

        file.load();
        config.setConfig(file);
    }


    public static boolean isSDMShopConversionEnabled() {
        return ENABLE_SDM_SHOP_CONVERSION.get();
    }

    public static String getCurrencyItemTag() {
        return CURRENCY_ITEM_TAG.get();
    }

    public static String getCurrencyItemName() {
        return CURRENCY_ITEM_NAME.get();
    }

    /**
     * Retrieves the base tax for a given building type using its full class name.
     *
     * @param fullClassName The full class name of the building type.
     * @return The base tax amount.
     */
    public static double getBaseTaxForBuilding(String fullClassName) {
        String shortName = getShortBuildingName(fullClassName);
        ForgeConfigSpec.DoubleValue taxValue = BUILDING_TAXES.get(shortName);
        return (taxValue != null) ? taxValue.get() : 0.0;
    }

    public static int getMaxTaxRevenue() {
        return MAX_TAX_REVENUE.get();
    }

    /**
     * Retrieves the upgrade tax for a given building type using its full class name.
     *
     * @param fullClassName The full class name of the building type.
     * @return The upgrade tax amount per level.
     */
    public static double getUpgradeTaxForBuilding(String fullClassName) {
        String shortName = getShortBuildingName(fullClassName);
        ForgeConfigSpec.DoubleValue upgradeValue = UPGRADE_TAXES.get(shortName);
        return (upgradeValue != null) ? upgradeValue.get() : 0.0;
    }

    /**
     * Retrieves the tax interval in minutes.
     *
     * @return The tax interval in minutes.
     */
    public static int getTaxIntervalInMinutes() {
        return TAX_INTERVAL_MINUTES.get();
    }

    /**
     * Helper method to convert full class name to short config name.
     *
     * @param fullClassName Full class name of the building (e.g., com.minecolonies.building.barracks).
     * @return The corresponding short name (e.g., barracks).
     */
    private static String getShortBuildingName(String fullClassName) {
        return CLASS_NAME_TO_SHORT_NAME.getOrDefault(fullClassName, "unknown");
    }
}
