package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.capability.PlayerWarDataCapability;
import net.machiavelli.minecolonytax.commands.*;
import net.machiavelli.minecolonytax.event.ColonyEventListener;
import net.machiavelli.minecolonytax.event.RaidLoginNotifier;
import net.machiavelli.minecolonytax.peace.PeaceProposalManager;
import net.machiavelli.minecolonytax.pvp.PvPEventHandler;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.util.TranslationUtil;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;

@Mod(MineColonyTax.MOD_ID)
public class MineColonyTax {

    public static final String MOD_ID = "minecolonytax";
    public static final Logger LOGGER = LogManager.getLogger();

    // Constructor
    public MineColonyTax() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        LOGGER.info("[DEBUG] Registering config with path: warntaxmod/minecolonytax.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TaxConfig.CONFIG, "warntaxmod/minecolonytax.toml");
        LOGGER.info("[DEBUG] Config registered successfully");

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Common setup method for server-side initialization.
     */
    private void setup(final FMLCommonSetupEvent event) {
        migrateOldConfigFiles();
        LOGGER.info("Initializing War N Taxes Mod");
        
        // Debug: Check config values during setup
        LOGGER.info("[DEBUG] Config loaded status: {}", TaxConfig.CONFIG.isLoaded());
        if (TaxConfig.CONFIG.isLoaded()) {
            LOGGER.info("[DEBUG] Join Phase Duration from config: {} minutes", TaxConfig.JOIN_PHASE_DURATION_MINUTES.get());
        } else {
            LOGGER.warn("[DEBUG] Config not loaded yet during setup!");
        }
    }

    private static void migrateOldConfigFiles() {
        List<String> filesToMove = List.of(
            "pvp_arena_data.json",
            "vassals.json",
            "colony_history.json",
            "colonyTaxData.json"
        );
        Path configDir = Paths.get("config");
        Path newDir = configDir.resolve("warntaxmod");

        try {
            if (!Files.exists(newDir)) {
                Files.createDirectories(newDir);
            }

            for (String fileName : filesToMove) {
                Path oldFile = configDir.resolve(fileName);
                Path newFile = newDir.resolve(fileName);
                if (Files.exists(oldFile) && !Files.exists(newFile)) {
                    Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Migrated '{}' to the 'warntaxmod' directory.", fileName);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to migrate old config files.", e);
        }
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
        
        // Initialize VassalManager
        VassalManager.initialize(event.getServer());
        
        // Set up scoreboards for tracking player statistics
        setupScoreboards(event.getServer().getScoreboard());
        
        // Reset war and raid permissions on server startup to prevent griefing after crashes
        LOGGER.info("Resetting all colony war and raid permissions to prevent post-crash griefing");
        com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().forEach(colony -> {
            WarSystem.setWarInteractionPermissions(colony, false);
            WarSystem.setRaidInteractionPermissions(colony, false);
        });
        // Clear any active wars that might have been in progress during crash
        WarSystem.ACTIVE_WARS.clear();
        LOGGER.info("War and raid permissions reset successfully");
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
        WntCommands.register(event.getDispatcher()); // Register the unified /wnt command
        LOGGER.info("MineColonyTax: /wnt commands registered.");
        
        LOGGER.info("Loaded Arena Positions");
    }

    /**
     * Method to handle server stopping event.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        VassalManager.shutdown();
    }
}
