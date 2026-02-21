package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.recipe.ModRecipeSerializers;
import net.machiavelli.minecolonytax.commands.RecipeDisableTestCommand;
import net.machiavelli.minecolonytax.commands.WarChestCommand;
import net.machiavelli.minecolonytax.commands.RaidRepairCommand;
import net.machiavelli.minecolonytax.commands.FactionCommand;
import net.machiavelli.minecolonytax.commands.TaxPolicyCommand;
import net.machiavelli.minecolonytax.commands.RandomEventsCommand;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import net.machiavelli.minecolonytax.webapi.WebAPIServer;
import net.machiavelli.minecolonytax.faction.FactionManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MineColonyTax.MOD_ID)
public class MineColonyTax {
    public static final String MOD_ID = "minecolonytax";
    public static final Logger LOGGER = LogManager.getLogger();

    // Web API Server instance (SERVER-SIDE ONLY)
    private static WebAPIServer webAPIServer = null;

    public MineColonyTax() {
        // Register configuration - Use COMMON type to prevent world-directory
        // serverconfig creation
        // This ensures config goes ONLY to /config/warntax/ and NOT to
        // world/serverconfig/
        // Single registration prevents duplicate config files and .bak file
        // proliferation
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TaxConfig.CONFIG, "warntax/minecolonytax.toml");

        // Register recipe serializers
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());

        // Register entities
        net.machiavelli.minecolonytax.espionage.ModEntities.ENTITIES
                .register(FMLJavaModLoadingContext.get().getModEventBus());

        // Register event listeners
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        // Register server events (including ServerStartingEvent)
        MinecraftForge.EVENT_BUS.register(this);

        // Manually register RaidKillTracker to ensure it works
        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.event.RaidKillTracker.class);
        LOGGER.error("MANUALLY REGISTERED RaidKillTracker event handler!");

        LOGGER.info("MineColonyTax mod initialized with COMMON config type - no serverconfig creation");
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            LOGGER.info("MineColonyTax setup complete");
        });
    }

    private void clientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Set Patchouli config flag if admin pages are enabled
        if (ModList.get().isLoaded("patchouli") && TaxConfig.SHOW_ADMIN_PAGES_IN_BOOK.get()) {
            try {
                Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
                Object instance = apiClass.getMethod("get").invoke(null);
                instance.getClass().getMethod("setConfigFlag", String.class, boolean.class)
                        .invoke(instance, "minecolonytax:show_admin", true);
                LOGGER.info("Registered Patchouli flag 'minecolonytax:show_admin'");
            } catch (Exception e) {
                LOGGER.warn("Failed to set Patchouli config flag: {}", e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Register commands
        RecipeDisableTestCommand.register(event.getServer().getCommands().getDispatcher());
        WarChestCommand.register(event.getServer().getCommands().getDispatcher());
        RaidRepairCommand.register(event.getServer().getCommands().getDispatcher());
        FactionCommand.register(event.getServer().getCommands().getDispatcher());
        TaxPolicyCommand.register(event.getServer().getCommands().getDispatcher());
        RandomEventsCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("WarChestCommand registered");
        LOGGER.info("TaxPolicyCommand registered");
        LOGGER.info("RandomEventsCommand registered");

        LOGGER.info("Server starting - initializing TaxManager with configured interval of {} minutes",
                TaxConfig.getTaxIntervalInMinutes());
        TaxManager.initialize(event.getServer());
        LOGGER.info("TaxManager initialization complete");

        // Initialize FirstColonyTracker to manage primary colony ownership
        FirstColonyTracker.loadData();
        LOGGER.info("FirstColonyTracker initialization complete");

        // Register colony ownership event handler for multi-colony support
        // TODO: Fix ColonyOwnershipHandler - MineColonies API events changed
        // net.machiavelli.minecolonytax.event.ColonyOwnershipHandler.register();
        // LOGGER.info("ColonyOwnershipHandler registered");

        // Initialize War Exhaustion Manager for penalty tracking
        net.machiavelli.minecolonytax.economy.WarExhaustionManager.initialize(event.getServer());
        LOGGER.info("WarExhaustionManager initialization complete");

        // Restore all colony permissions to config defaults (disable war/raid actions)
        // This ensures clean state after server restarts/crashes
        LOGGER.info("Restoring all colony war/raid permissions to config defaults...");
        WarSystem.restoreAllColonyPermissionsToDefaults();
        LOGGER.info("Colony permissions restoration complete");

        // 🚨 AUTOMATIC: Immediate null owner fixes - NO DELAYS, NO MANUAL INTERVENTION
        LOGGER.error("🚨 AUTOMATIC NULL OWNER PROTECTION: Fixing ALL null owners immediately on startup...");

        // IMMEDIATE fix - run right now, no delays
        try {
            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
            LOGGER.info("✅ IMMEDIATE null owner fix completed");
        } catch (Exception e) {
            LOGGER.error("💥 IMMEDIATE null owner fix failed", e);
        }

        // Schedule additional safety fixes
        event.getServer().execute(() -> {
            try {
                Thread.sleep(1000); // 1 second
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
                LOGGER.info("✅ DELAYED null owner fix completed");
            } catch (Exception e) {
                LOGGER.error("💥 DELAYED null owner fix failed", e);
            }
        });

        // Final safety net
        event.getServer().execute(() -> {
            try {
                Thread.sleep(3000); // 3 seconds
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
                LOGGER.info("✅ FINAL null owner verification completed - ALL colonies should be safe now");
            } catch (Exception e) {
                LOGGER.error("💥 FINAL null owner verification failed", e);
            }
        });

        // Initialize VassalManager so server reference is available for notifications
        VassalManager.initialize(event.getServer());

        // Initialize WarChestManager for war chest tracking
        WarChestManager.initialize(event.getServer());
        LOGGER.info("WarChestManager initialized");

        // Initialize FactionManager
        FactionManager.init();
        LOGGER.info("FactionManager initialized");

        // Initialize TaxPolicyManager
        TaxPolicyManager.initialize(event.getServer());
        LOGGER.info("TaxPolicyManager initialized");

        // Initialize RandomEventManager
        net.machiavelli.minecolonytax.events.random.RandomEventManager.initialize(event.getServer());
        LOGGER.info("RandomEventManager initialized");

        // Initialize SpyManager
        if (TaxConfig.isSpySystemEnabled()) {
            net.machiavelli.minecolonytax.espionage.SpyManager.initialize(event.getServer());
            LOGGER.info("SpyManager initialized");
        }

        // Emergency cleanup of guard resistance effects on startup
        GuardResistanceHandler.emergencyCleanup();
        LOGGER.info("Guard resistance effects cleanup completed");

        // Start Web API Server if enabled (SERVER-SIDE ONLY)
        if (TaxConfig.isWebAPIEnabled()) {
            try {
                webAPIServer = new WebAPIServer(event.getServer());
                webAPIServer.start();
                LOGGER.info("Web API Server initialization complete");
            } catch (Exception e) {
                LOGGER.error("Failed to start Web API Server: {}", e.getMessage());
                e.printStackTrace();
            }
        } else {
            LOGGER.debug("Web API Server is disabled in configuration");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Stop Web API Server
        if (webAPIServer != null && webAPIServer.isRunning()) {
            try {
                webAPIServer.stop();
                LOGGER.info("Web API Server shutdown complete");
            } catch (Throwable t) {
                LOGGER.warn("Error during Web API Server shutdown: {}", t.toString());
            }
        }

        try {
            VassalManager.shutdown();
            LOGGER.info("VassalManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during VassalManager shutdown: {}", t.toString());
        }

        try {
            WarChestManager.shutdown();
            LOGGER.info("WarChestManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during WarChestManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.shutdown();
            LOGGER.info("WarExhaustionManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during WarExhaustionManager shutdown: {}", t.toString());
        }

        try {
            FactionManager.saveData();
            LOGGER.info("FactionManager data saved");
        } catch (Throwable t) {
            LOGGER.warn("Error saving FactionManager data: {}", t.toString());
        }

        try {
            TaxPolicyManager.shutdown();
            LOGGER.info("TaxPolicyManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during TaxPolicyManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.events.random.RandomEventManager.shutdown();
            LOGGER.info("RandomEventManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during RandomEventManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.espionage.SpyManager.shutdown();
            LOGGER.info("SpyManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during SpyManager shutdown: {}", t.toString());
        }
    }
}
