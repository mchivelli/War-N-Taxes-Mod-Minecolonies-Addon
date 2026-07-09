package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.recipe.ModRecipeSerializers;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import net.machiavelli.minecolonytax.db.WarStatsDB;
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

import java.util.UUID;

@Mod(MineColonyTax.MOD_ID)
public class MineColonyTax {
    public static final String MOD_ID = "minecolonytax";
    public static final Logger LOGGER = LogManager.getLogger();

    /**
     * Guards against registering FCT event handlers more than once.
     * DefaultEventBus is a JVM-lifetime singleton — in single-player, onServerStarting
     * fires on every world load without clearing the handler list. Without this flag,
     * each world load would add another duplicate handler.
     */
    private static boolean fctEventBusSubscribed = false;

    public MineColonyTax() {
        // COMMON config type writes to /config/warntax/ only, not world/serverconfig/
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TaxConfig.CONFIG, "warntax/minecolonytax.toml");

        ModRecipeSerializers.RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());

        net.machiavelli.minecolonytax.espionage.ModEntities.ENTITIES
                .register(FMLJavaModLoadingContext.get().getModEventBus());

        // Step 11 — siege banner block + item registration. Block is always
        // registered; the Plant-the-Banner objective gates behaviour at runtime
        // via EnableExperimentalSiegeObjectives.
        net.machiavelli.minecolonytax.siege.ModSiegeBlocks.register(
                FMLJavaModLoadingContext.get().getModEventBus());

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.event.RaidKillTracker.class);
        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.util.TickScheduler.class);
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
                if (TaxConfig.isNormalLogging()) LOGGER.info("Registered Patchouli flag 'minecolonytax:show_admin'");
            } catch (Exception e) {
                LOGGER.warn("Failed to set Patchouli config flag: {}", e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Command registration (TreasuryCommand, RaidRepairCommand, FactionCommand,
        // TaxPolicyCommand, RandomEventsCommand, RecipeDisableTestCommand) has moved to
        // PvPEventHandler.onRegisterCommands so the commands re-register on every dispatcher
        // rebuild, including after /reload — registering them here only ran at boot (H5).

        if (TaxConfig.isNormalLogging()) LOGGER.info("Server starting - initializing TaxManager with configured interval of {} minutes",
                TaxConfig.getTaxIntervalInMinutes());
        TaxManager.initialize(event.getServer());

        try {
            net.machiavelli.minecolonytax.economy.RaidPenaltyManager.initialize(event.getServer());
            if (TaxConfig.isNormalLogging()) LOGGER.info("RaidPenaltyManager initialized");
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize RaidPenaltyManager: {}", t.toString());
        }

        FirstColonyTracker.loadData();
        net.machiavelli.minecolonytax.deletion.ColonyDeletionManager.load();

        // Periodic pending-colony-deletion check every minute (deletion timers are day-scale, so a minute is fine)
        final net.minecraft.server.MinecraftServer deletionServer = event.getServer();
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
            try {
                net.machiavelli.minecolonytax.deletion.ColonyDeletionManager.tick(deletionServer);
            } catch (Throwable t) {
                LOGGER.error("Error checking pending colony deletions: {}", t.toString());
            }
        }, 60_000, 60_000);

        // Subscribe to colony lifecycle events so FirstColonyTracker stays accurate.
        // Guard with a static flag: DefaultEventBus is a JVM-lifetime singleton and
        // does not clear its handler list between server starts in single-player mode.
        if (!fctEventBusSubscribed) {
            fctEventBusSubscribed = true;
            com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus()
                    .subscribe(com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent.class, e -> {
                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
                        if (ownerUUID != null) {
                            FirstColonyTracker.addColony(ownerUUID, e.getColony().getID());
                        }
                    });
            com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus()
                    .subscribe(com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent.class, e -> {
                        // Event fires before cap.deleteColony() so permissions are still readable here
                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
                        if (ownerUUID != null) {
                            FirstColonyTracker.removeColony(ownerUUID, e.getColony().getID());
                        }
                    });
        }

        // Deferred bootstrap: seed FirstColonyTracker for colonies that existed before this
        // mod was installed. Runs after MineColonies finishes loading all colonies.
        // Orders colonies by ID ascending so the lowest-ID (oldest) colony becomes primary.
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
            try {
                com.minecolonies.api.colony.IColonyManager cm =
                        com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager();
                FirstColonyTracker.bootstrapFromExistingColonies(cm);
            } catch (Exception e) {
                LOGGER.error("FirstColonyTracker bootstrap failed", e);
            }
        }, 6000);

        net.machiavelli.minecolonytax.economy.WarExhaustionManager.initialize(event.getServer());

        // Load persisted permission snapshots BEFORE clearing bits so existing snapshots
        // survive the defaults restore and are available when wars re-enable permissions.
        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.loadFromFile();

        WarSystem.restoreAllColonyPermissionsToDefaults();

        // SAFETY (4.x world-brick fix): the legacy code called emergencyFixAllNullOwners()
        // IMMEDIATELY here, at ServerStartingEvent — BEFORE MineColonies finishes loading
        // colonies. A colony that was only transiently owner-null mid-load would then get a
        // synthetic '[AUTO_OWNER]' placeholder written into its permissions and be flagged
        // abandoned, corrupting its saved data and bricking the world on the next load.
        // That immediate pass is removed. Automatic owner-repair / abandoned-entry cleanup
        // now runs ONLY when the abandonment system is explicitly enabled, and ONLY on a
        // deferred pass that lets colonies finish loading first.
        if (TaxConfig.isColonyAbandonmentSystemEnabled()) {
            net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
                try {
                    net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
                    net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
                } catch (Exception e) {
                    LOGGER.error("Deferred null-owner repair failed", e);
                }
            }, 3000);
        }

        // Always-on, removal-ONLY migration: heal worlds that older versions corrupted with
        // synthetic '[AUTO_OWNER]'/system-owner placeholder entries. This never injects owners
        // and never flags colonies abandoned. Deferred so colonies are fully loaded first.
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
            try {
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.repairLegacySyntheticOwners();
            } catch (Exception e) {
                LOGGER.error("Legacy synthetic-owner repair failed", e);
            }
        }, 3000);

        VassalManager.initialize(event.getServer());

        TreasuryManager.initialize(event.getServer());
        if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager initialized");

        FactionManager.init();
        if (TaxConfig.isNormalLogging()) LOGGER.info("FactionManager initialized");

        TaxPolicyManager.initialize(event.getServer());
        if (TaxConfig.isNormalLogging()) LOGGER.info("TaxPolicyManager initialized");

        net.machiavelli.minecolonytax.events.random.RandomEventManager.initialize(event.getServer());
        if (TaxConfig.isNormalLogging()) LOGGER.info("RandomEventManager initialized");

        HistoryManager.loadHistory();
        if (TaxConfig.isNormalLogging()) LOGGER.info("HistoryManager loaded");

        if (TaxConfig.isSpySystemEnabled()) {
            net.machiavelli.minecolonytax.espionage.SpyManager.initialize(event.getServer());
            if (TaxConfig.isNormalLogging()) LOGGER.info("SpyManager initialized");
        }

        if (TaxConfig.isUpgradesEnabled()) {
            net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.initialize(event.getServer());
            if (TaxConfig.isNormalLogging()) LOGGER.info("ColonyUpgradeManager initialized");
        }

        GuardResistanceHandler.emergencyCleanup();

        // Load the explosion-damage ledger BEFORE resuming wars: a war that resumes
        // already downtime-expired ends immediately and calls restoreWarDamage(),
        // which needs the ledger already in memory or the broken blocks are lost.
        try {
            net.machiavelli.minecolonytax.siege.WarBlockLedger.loadFromDisk();
        } catch (Throwable t) {
            LOGGER.error("Failed to load WarBlockLedger: {}", t.toString());
        }

        // War persistence must be restored after the colony manager is ready
        try {
            WarSystem.loadAndResumeActiveWars();
            if (TaxConfig.isNormalLogging()) LOGGER.info("War persistence restoration complete");
        } catch (Throwable t) {
            LOGGER.error("Failed to restore active wars: {}", t.toString());
        }

        // Prune ledgers whose war did not survive the restart so they can't
        // resurrect blocks at a later, unrelated war's end. (Wars that ended during
        // resume already had their ledger consumed by restoreWarDamage.)
        try {
            java.util.Set<UUID> activeWarIds = new java.util.HashSet<>();
            for (net.machiavelli.minecolonytax.data.WarData w : WarSystem.ACTIVE_WARS.values()) {
                if (w != null && w.getWarID() != null) activeWarIds.add(w.getWarID());
            }
            net.machiavelli.minecolonytax.siege.WarBlockLedger.pruneOrphans(activeWarIds);
        } catch (Throwable t) {
            LOGGER.error("Failed to prune WarBlockLedger orphans: {}", t.toString());
        }

        // Restore Hostile rank to pre-conflict state for any colonies whose conflict
        // ended while the server was down (snapshot exists but no active war/raid).
        try {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreAllStale(event.getServer());
        } catch (Exception e) {
            LOGGER.error("Failed to run stale permission snapshot restore", e);
        }

        // Permissions health check: runs after wars are restored so that legitimately
        // hostile-ranked players in active wars are not incorrectly demoted.
        // Deferred ~5 s to let MineColonies finish loading all colony data.
        // This catches stale Hostile rank assignments left by crashes or older mod versions.
        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
            try {
                net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.Result result =
                        net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.run(event.getServer());
                if (result.hasIssues()) {
                    LOGGER.warn("[PermissionsHealthCheck] Corrected stale permission state on startup: {}", result.summary());
                } else if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("[PermissionsHealthCheck] {}", result.summary());
                }
            } catch (Exception e) {
                LOGGER.error("[PermissionsHealthCheck] Startup health check failed", e);
            }
        }, 5000);

        // Warn if MineColonies pvp_mode is enabled — it bypasses ALL colony permission
        // checks for players whose guards enter enemy territory, overriding our per-action
        // permission system. Wars and raids should use WNT's own permission management.
        // NOTE: com.minecolonies.core.MineColonies is an INTERNAL class (not the api package);
        // any refactor on the MC side can throw NoClassDefFoundError (an Error, not Exception),
        // so the catch must trap Throwable. The whole block is also gated on ModList.isLoaded.
        if (ModList.get().isLoaded("minecolonies")) {
            try {
                if (com.minecolonies.core.MineColonies.getConfig().getServer().pvp_mode.get()) {
                    LOGGER.warn("[WNT] MineColonies pvp_mode is ENABLED. This bypasses colony permission "
                            + "checks for players who rally guards into enemy colonies, overriding War 'N Taxes "
                            + "permission management. Set pvp_mode = false in minecolonies-server.toml for "
                            + "correct war/raid permission behavior.");
                }
            } catch (Throwable t) {
                // Config not available yet, or MineColonies internal class moved — ignore.
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.debug("[WNT] Could not read MineColonies pvp_mode (likely internal API change): {}", t.toString());
                }
            }
        }

        try {
            net.machiavelli.minecolonytax.occupation.OccupationManager.initialize(event.getServer());
            if (TaxConfig.isNormalLogging()) LOGGER.info("OccupationManager initialized");
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize OccupationManager: {}", t.toString());
        }

        if (TaxConfig.isBesiegeSystemEnabled()) {
            try {
                net.machiavelli.minecolonytax.besiege.BesiegeManager.initialize(event.getServer());
                if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
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

        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
            try {
                net.machiavelli.minecolonytax.occupation.OccupationManager.checkExpiredOccupations();
            } catch (Throwable t) {
                LOGGER.error("Error checking expired occupations: {}", t.toString());
            }
        }, 300_000, 300_000);

        if (TaxConfig.isDatabaseEnabled()) {
            try {
                WarStatsDB.initialize();
                if (TaxConfig.isNormalLogging()) LOGGER.info("WarStatsDB initialized");
                long snapshotMs = TaxConfig.getDatabaseSnapshotIntervalSeconds() * 1000L;
                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
                    try {
                        WarStatsDB.snapshotAllColonies(event.getServer());
                        WarStatsDB.updateServerState(event.getServer());
                    } catch (Throwable t) {
                        LOGGER.error("Error during colony DB snapshot: {}", t.toString());
                    }
                }, snapshotMs, snapshotMs);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize WarStatsDB: {}", e.getMessage());
            }
        } else {
            if (TaxConfig.isDebugLogging()) LOGGER.debug("WarStats database is disabled in configuration");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        try {
            WarStatsDB.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("WarStatsDB shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during WarStatsDB shutdown: {}", t.toString());
        }

        try {
            VassalManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("VassalManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during VassalManager shutdown: {}", t.toString());
        }

        try {
            TreasuryManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during TreasuryManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("WarExhaustionManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during WarExhaustionManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.economy.RaidPenaltyManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("RaidPenaltyManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during RaidPenaltyManager shutdown: {}", t.toString());
        }

        try {
            FactionManager.saveData();
            if (TaxConfig.isNormalLogging()) LOGGER.info("FactionManager data saved");
        } catch (Throwable t) {
            LOGGER.warn("Error saving FactionManager data: {}", t.toString());
        }

        try {
            TaxPolicyManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("TaxPolicyManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during TaxPolicyManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.events.random.RandomEventManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("RandomEventManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during RandomEventManager shutdown: {}", t.toString());
        }

        try {
            HistoryManager.saveHistory();
            if (TaxConfig.isNormalLogging()) LOGGER.info("HistoryManager saved");
        } catch (Throwable t) {
            LOGGER.warn("Error saving HistoryManager: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.espionage.SpyManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("SpyManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during SpyManager shutdown: {}", t.toString());
        }

        // Save active wars before TickScheduler shutdown — task IDs are still needed for cleanup
        try {
            WarSystem.saveActiveWars();
            if (TaxConfig.isNormalLogging()) LOGGER.info("Active wars saved to disk");
        } catch (Throwable t) {
            LOGGER.warn("Error saving active wars: {}", t.toString());
        }

        // Finish any in-progress block restoration synchronously (before TickScheduler
        // shutdown cancels the batched task), then persist the remaining active-war
        // ledgers so blocks broken this session are still restored after a restart
        // (mirrors active_wars.json).
        try {
            net.machiavelli.minecolonytax.siege.WarBlockLedger.flushPendingRestores();
            net.machiavelli.minecolonytax.siege.WarBlockLedger.saveToDisk();
            if (TaxConfig.isNormalLogging()) LOGGER.info("WarBlockLedger flushed and saved to disk");
        } catch (Throwable t) {
            LOGGER.warn("Error saving WarBlockLedger: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.occupation.OccupationManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("OccupationManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during OccupationManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("ColonyUpgradeManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during ColonyUpgradeManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.deletion.ColonyDeletionManager.save();
            if (TaxConfig.isNormalLogging()) LOGGER.info("ColonyDeletionManager save complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during ColonyDeletionManager save: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.besiege.BesiegeManager.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during BesiegeManager shutdown: {}", t.toString());
        }

        try {
            net.machiavelli.minecolonytax.util.TickScheduler.shutdown();
            if (TaxConfig.isNormalLogging()) LOGGER.info("TickScheduler shutdown complete");
        } catch (Throwable t) {
            LOGGER.warn("Error during TickScheduler shutdown: {}", t.toString());
        }

        // Must run AFTER all manager shutdowns above so any final saveData()
        // they queued is flushed to disk before the JVM exits.
        try {
            net.machiavelli.minecolonytax.util.AsyncSaveExecutor.shutdownAndFlush();
            if (TaxConfig.isNormalLogging()) LOGGER.info("AsyncSaveExecutor flushed");
        } catch (Throwable t) {
            LOGGER.warn("Error flushing AsyncSaveExecutor: {}", t.toString());
        }
    }
}
