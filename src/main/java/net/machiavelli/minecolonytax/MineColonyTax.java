package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.capability.PlayerWarDataCapability;
import net.machiavelli.minecolonytax.commands.*;
import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
import net.machiavelli.minecolonytax.event.RaidEndEvent;
import net.machiavelli.minecolonytax.event.WarVictoryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
        
        // Register capability
        modEventBus.addListener(PlayerWarDataCapability::register);
    }

    /**
     * Common setup method for server-side initialization.
     */
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing MineColony Tax System");
        TaxConfig.loadConfig(TaxConfig.CONFIG, "minecolonytax.toml");
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
        
        // Set up scoreboards for tracking player statistics
        setupScoreboards(event.getServer().getScoreboard());
    }

    /**
     * Sets up the necessary scoreboard objectives for player statistics
     */
    private void setupScoreboards(net.minecraft.world.scores.Scoreboard scoreboard) {
        // These objectives track player stats
        createObjectiveIfNotExists(scoreboard, "playersKilled", "Players Killed in War");
        createObjectiveIfNotExists(scoreboard, "raidsCompleted", "Colonies Raided");
        createObjectiveIfNotExists(scoreboard, "amountRaided", "Amount Raided");
        createObjectiveIfNotExists(scoreboard, "warsWon", "Wars Won");
        createObjectiveIfNotExists(scoreboard, "warStalemates", "War Stalemates");
    }

    /**
     * Creates a scoreboard objective if it doesn't already exist
     */
    private void createObjectiveIfNotExists(net.minecraft.world.scores.Scoreboard scoreboard, String name, String displayName) {
        if (scoreboard.getObjective(name) == null) {
            scoreboard.addObjective(name, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, 
                                    net.minecraft.network.chat.Component.literal(displayName), 
                                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);
        }
    }

    /**
     * Registers commands for the mod
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WarCommands.register(event.getDispatcher()); // Register the PvP command
        LOGGER.info("MineColonyTax: PvP command registered.");
        ClaimTaxCommand.register(event.getDispatcher()); // Register the Claim Tax command
        CheckTaxRevenueCommand.register(event.getDispatcher());
        AdminTaxGenCommand.register(event.getDispatcher());
        WarHistoryCommand.register(event.getDispatcher());
        WarStatsCommand.register(event.getDispatcher()); // Register the War Stats command
        LOGGER.info("MineColonyTax: Commands registered.");
        loadArenaPositions();
        LOGGER.info("Loaded Arena Positions");
    }
}
