package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.recipe.ModRecipeSerializers;
import net.machiavelli.minecolonytax.commands.RecipeDisableTestCommand;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    public MineColonyTax() {
        // Register configuration - Use COMMON type to prevent world-directory serverconfig creation
        // This ensures config goes ONLY to /config/warntax/ and NOT to world/serverconfig/
        // Single registration prevents duplicate config files and .bak file proliferation
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TaxConfig.CONFIG, "warntax/minecolonytax.toml");
        
        // Register recipe serializers
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        
        // Register event listeners
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        
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
    
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Register commands
        RecipeDisableTestCommand.register(event.getServer().getCommands().getDispatcher());
        
        LOGGER.info("Server starting - initializing TaxManager with configured interval of {} minutes", TaxConfig.getTaxIntervalInMinutes());
        TaxManager.initialize(event.getServer());
        LOGGER.info("TaxManager initialization complete");
        
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
    }

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		try {
			VassalManager.shutdown();
			LOGGER.info("VassalManager shutdown complete");
		} catch (Throwable t) {
			LOGGER.warn("Error during VassalManager shutdown: {}", t.toString());
		}
	}
}
