package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.commands.CheckTaxRevenueCommand;
import net.machiavelli.minecolonytax.commands.ClaimTaxCommand;
import net.machiavelli.minecolonytax.commands.PvPArenaCommand;
import net.machiavelli.minecolonytax.commands.WarCommands;  // Import the new PvP command class
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

import static net.machiavelli.minecolonytax.commands.PvPArenaCommand.loadArenaPositions;

@Mod(MineColonyTax.MOD_ID)
public class MineColonyTax {

    public static final String MOD_ID = "minecolonytax";
    public static final Logger LOGGER = LogManager.getLogger(MineColonyTax.class);

    // Constructor
    public MineColonyTax() {
        // Register to Forge event bus
        MinecraftForge.EVENT_BUS.register(this);

        // Register to the mod event bus
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    /**
     * Common setup method for server-side initialization.
     */
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing MineColony Tax System");
        TaxConfig.loadConfig(TaxConfig.CONFIG, "minecolonytax.toml");
        // Additional setup if needed for PvP system+
    }

    /**
     * Client-specific setup method.
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Client setup for MineColonyTax Mod");
        // Add any client-specific configurations or setups here
    }

    /**
     * Method to handle server starting event.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting: Initializing Tax System and PvP System");
        // Initialize TaxManager after the config is loaded
        TaxManager.initialize(event.getServer());
    }

    /**
     * Registers commands for the mod, including PvP and Tax commands.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WarCommands.register(event.getDispatcher()); // Register the PvP command
        LOGGER.info("MineColonyTax: PvP command registered.");
        ClaimTaxCommand.register(event.getDispatcher()); // Register the Claim Tax command
        CheckTaxRevenueCommand.register(event.getDispatcher()); // Register the Check Tax Revenue command
        LOGGER.info("MineColonyTax: Commands registered.");
        loadArenaPositions();

    }


}
