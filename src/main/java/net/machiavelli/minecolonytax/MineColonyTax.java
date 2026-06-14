package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.commands.FactionCommand;
import net.machiavelli.minecolonytax.commands.RaidRepairCommand;
import net.machiavelli.minecolonytax.commands.RandomEventsCommand;
import net.machiavelli.minecolonytax.commands.RecipeDisableTestCommand;
import net.machiavelli.minecolonytax.commands.TaxPolicyCommand;
import net.machiavelli.minecolonytax.commands.WarChestCommand;
import net.machiavelli.minecolonytax.commands.WntCommands;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.machiavelli.minecolonytax.economy.WarExhaustionManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.espionage.ModEntities;
import net.machiavelli.minecolonytax.espionage.SpyManager;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.faction.FactionManager;
import net.machiavelli.minecolonytax.occupation.OccupationManager;
import net.machiavelli.minecolonytax.attachment.PlayerWarDataAttachment;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import net.machiavelli.minecolonytax.recipe.ModRecipeSerializers;
import net.machiavelli.minecolonytax.trade.TradeRouteManager;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.webapi.WebAPIServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MineColonyTax.MOD_ID)
public class MineColonyTax {
    public static final String MOD_ID = "minecolonytax";
    public static final Logger LOGGER = LogManager.getLogger();

    private static WebAPIServer webAPIServer = null;

    public MineColonyTax(IEventBus modEventBus) {
        // Config
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, TaxConfig.CONFIG, "warntax/minecolonytax.toml");

        // DeferredRegisters — must be registered to MOD bus
        PlayerWarDataAttachment.ATTACHMENT_TYPES.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        net.machiavelli.minecolonytax.siege.ModSiegeBlocks.register(modEventBus);

        // Mod lifecycle
        modEventBus.addListener(this::setup);

        // Game (FORGE) event bus
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(net.machiavelli.minecolonytax.event.RaidKillTracker.class);

        LOGGER.info("MineColonyTax initialized");
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
            LOGGER.info("MineColonyTax setup complete - networking auto-registered via @EventBusSubscriber")
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var dispatcher = event.getServer().getCommands().getDispatcher();

        // Commands
        RecipeDisableTestCommand.register(dispatcher);
        WarChestCommand.register(dispatcher);
        RaidRepairCommand.register(dispatcher);
        FactionCommand.register(dispatcher);
        TaxPolicyCommand.register(dispatcher);
        RandomEventsCommand.register(dispatcher);
        WntCommands.register(dispatcher);

        // Core systems
        LOGGER.info("Initializing systems (tax interval: {} min)", TaxConfig.getTaxIntervalInMinutes());
        TaxManager.initialize(event.getServer());

        FirstColonyTracker.loadData();

        WarExhaustionManager.initialize(event.getServer());
        WarChestManager.initialize(event.getServer());
        VassalManager.initialize(event.getServer());
        FactionManager.init();
        TaxPolicyManager.initialize(event.getServer());
        TradeRouteManager.initialize(event.getServer());
        RandomEventManager.initialize(event.getServer());

        if (TaxConfig.isSpySystemEnabled()) {
            SpyManager.initialize(event.getServer());
        }

        // Restore war state: reset all colony war/raid permissions to config defaults first,
        // then resume any wars persisted on the previous shutdown (which re-enables permissions
        // for colonies that are still at war).
        WarSystem.restoreAllColonyPermissionsToDefaults();
        try {
            WarSystem.loadAndResumeActiveWars();
        } catch (Throwable t) {
            LOGGER.error("Failed to load/resume active wars: {}", t.toString());
        }

        // Occupation
        try {
            OccupationManager.initialize(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize OccupationManager: {}", t.toString());
        }

        // Periodic occupation expiration check every 5 minutes
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
            try {
                OccupationManager.checkExpiredOccupations();
            } catch (Throwable t) {
                LOGGER.error("Error checking expired occupations: {}", t.toString());
            }
        }, 300_000, 300_000);

        // Besiege system — init + drive tick() ~every second via TickScheduler (Neo has no tick mixin)
        if (TaxConfig.isBesiegeSystemEnabled()) {
            try {
                net.machiavelli.minecolonytax.besiege.BesiegeManager.initialize(event.getServer());
                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
                    try {
                        net.machiavelli.minecolonytax.besiege.BesiegeManager.tick();
                    } catch (Throwable t) {
                        LOGGER.error("Error in BesiegeManager tick: {}", t.toString());
                    }
                }, 1000, 1000);
            } catch (Throwable t) {
                LOGGER.error("Failed to initialize BesiegeManager: {}", t.toString());
            }
        }

        // War block ledger (persistent siege damage) — load + prune orphans for ended wars
        try {
            net.machiavelli.minecolonytax.siege.WarBlockLedger.loadFromDisk();
            java.util.Set<java.util.UUID> activeWarIds = new java.util.HashSet<>();
            for (net.machiavelli.minecolonytax.data.WarData w : WarSystem.ACTIVE_WARS.values()) {
                try { activeWarIds.add(w.getWarID()); } catch (Throwable ignored) {}
            }
            net.machiavelli.minecolonytax.siege.WarBlockLedger.pruneOrphans(activeWarIds);
        } catch (Throwable t) {
            LOGGER.error("WarBlockLedger load failed: {}", t.toString());
        }

        // Null owner protection
        try {
            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
        } catch (Exception e) {
            LOGGER.error("Immediate null owner fix failed", e);
        }
        event.getServer().execute(() -> {
            try {
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
            } catch (Exception e) {
                LOGGER.error("Delayed null owner fix failed", e);
            }
        });

        GuardResistanceHandler.emergencyCleanup();

        // PvP player-state crash recovery — load persisted stats/game-modes/positions
        try {
            net.machiavelli.minecolonytax.pvp.PvPStatsPersistence.load();
        } catch (Throwable t) {
            LOGGER.error("PvPStatsPersistence load failed: {}", t.toString());
        }

        // Web API
        if (TaxConfig.isWebAPIEnabled()) {
            try {
                webAPIServer = new WebAPIServer(event.getServer());
                webAPIServer.start();
            } catch (Exception e) {
                LOGGER.error("Failed to start Web API Server: {}", e.getMessage());
            }
        }

        LOGGER.info("MineColonyTax server startup complete");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (webAPIServer != null && webAPIServer.isRunning()) {
            try { webAPIServer.stop(); } catch (Throwable t) { LOGGER.warn("WebAPI stop error: {}", t.toString()); }
        }

        // Persist in-progress wars before anything else shuts down, while ACTIVE_WARS is intact.
        try { WarSystem.saveActiveWars(); }         catch (Throwable t) { LOGGER.warn("WarSystem saveActiveWars error: {}", t.toString()); }

        try { VassalManager.shutdown(); }          catch (Throwable t) { LOGGER.warn("VassalManager shutdown error: {}", t.toString()); }
        try { WarChestManager.shutdown(); }         catch (Throwable t) { LOGGER.warn("WarChestManager shutdown error: {}", t.toString()); }
        try { WarExhaustionManager.shutdown(); }    catch (Throwable t) { LOGGER.warn("WarExhaustionManager shutdown error: {}", t.toString()); }
        try { FactionManager.saveData(); }          catch (Throwable t) { LOGGER.warn("FactionManager save error: {}", t.toString()); }
        try { TaxPolicyManager.shutdown(); }        catch (Throwable t) { LOGGER.warn("TaxPolicyManager shutdown error: {}", t.toString()); }
        try { TradeRouteManager.shutdown(); }       catch (Throwable t) { LOGGER.warn("TradeRouteManager shutdown error: {}", t.toString()); }
        try { RandomEventManager.shutdown(); }      catch (Throwable t) { LOGGER.warn("RandomEventManager shutdown error: {}", t.toString()); }
        try { SpyManager.shutdown(); }              catch (Throwable t) { LOGGER.warn("SpyManager shutdown error: {}", t.toString()); }
        try { OccupationManager.shutdown(); }       catch (Throwable t) { LOGGER.warn("OccupationManager shutdown error: {}", t.toString()); }
        try { net.machiavelli.minecolonytax.besiege.BesiegeManager.shutdown(); } catch (Throwable t) { LOGGER.warn("BesiegeManager shutdown error: {}", t.toString()); }
        try { net.machiavelli.minecolonytax.siege.WarBlockLedger.flushPendingRestores(); } catch (Throwable t) { LOGGER.warn("WarBlockLedger flush error: {}", t.toString()); }
        try { net.machiavelli.minecolonytax.siege.WarBlockLedger.saveToDisk(); } catch (Throwable t) { LOGGER.warn("WarBlockLedger save error: {}", t.toString()); }
        // Flush async file writes (besiege uses AsyncSaveExecutor) before the scheduler stops.
        try { net.machiavelli.minecolonytax.pvp.PvPStatsPersistence.save(); } catch (Throwable t) { LOGGER.warn("PvPStatsPersistence save error: {}", t.toString()); }
        try { net.machiavelli.minecolonytax.util.AsyncSaveExecutor.shutdownAndFlush(); } catch (Throwable t) { LOGGER.warn("AsyncSaveExecutor flush error: {}", t.toString()); }

        // TickScheduler last — other shutdowns may still enqueue tasks
        try { net.machiavelli.minecolonytax.util.TickScheduler.shutdown(); } catch (Throwable t) { LOGGER.warn("TickScheduler shutdown error: {}", t.toString()); }

        LOGGER.info("MineColonyTax server shutdown complete");
    }
}
