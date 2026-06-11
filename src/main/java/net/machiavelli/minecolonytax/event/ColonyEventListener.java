package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.raid.EntityRaidManager;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ColonyEventListener {

    private static final Logger LOGGER = LogManager.getLogger(ColonyEventListener.class);

    private static final Map<Integer, Map<IBuilding, Integer>> colonyBuildingLevels = new HashMap<>();
    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static int entityScanTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (TaxConfig.ENABLE_ENTITY_RAIDS.get()) {
            EntityRaidManager.tick();

            entityScanTickCounter++;
            if (entityScanTickCounter >= 200) {
                entityScanTickCounter = 0;
                scanColoniesForEntityRaids();
            }
        }

        // Building-level scan only needs to run once per second, not every tick.
        // Detection of new/upgraded buildings is purely cosmetic (logging) — the
        // tax/war systems read building data on their own schedules.
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        // This building-delta scan exists ONLY to log new/upgraded buildings. When that
        // logging is off (production default) the whole O(colonies x buildings) per-second
        // scan is pure waste — skip it entirely (audit H11). This also keeps
        // colonyBuildingLevels empty in production, so it can't leak there.
        if (!TaxConfig.showColonyInitializationLogs()) {
            return;
        }
        List<IColony> colonies = IColonyManager.getInstance().getAllColonies();

        for (IColony colony : colonies) {
            try {
                int colonyId = colony.getID();
                Map<IBuilding, Integer> buildingLevels = colonyBuildingLevels.computeIfAbsent(colonyId, k -> new HashMap<>());

                int guardTowerCount = 0;
                int newOrUpgradedBuildingsCount = 0;

                for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                    int currentLevel = building.getBuildingLevel();

                    if (isGuardTower(building)) {
                        guardTowerCount++;
                    }

                    Integer prev = buildingLevels.get(building);
                    if (prev == null || prev < currentLevel) {
                        newOrUpgradedBuildingsCount++;
                        buildingLevels.put(building, currentLevel);
                    }
                }

                if (newOrUpgradedBuildingsCount > 0) {
                    LOGGER.info("Colony '{}': Detected {} new/upgraded buildings (Guards: {})",
                               colony.getName(), newOrUpgradedBuildingsCount, guardTowerCount);
                }

            } catch (Exception e) {
                LOGGER.error("Error processing colony {} during tick: {}", colony.getID(), e.getMessage());
            }
        }
    }

    private static void scanColoniesForEntityRaids() {
        try {
            final int threshold = Math.max(1, TaxConfig.getEntityRaidThreshold());
            final long cooldownMs = Math.max(0, TaxConfig.getEntityRaidCooldownMinutes()) * 60_000L;

            List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
            for (IColony colony : colonies) {
                if (colony == null || colony.getWorld() == null || !(colony.getWorld() instanceof ServerLevel)) {
                    continue;
                }

                final int colonyId = colony.getID();

                if (EntityRaidManager.hasActiveEntityRaid(colonyId)) {
                    continue;
                }

                long now = System.currentTimeMillis();
                if (EntityRaidManager.isOnCooldown(colonyId, cooldownMs)) {
                    long last = EntityRaidManager.getLastRaidTime(colonyId);
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid-SCAN] Colony '{}' on cooldown ({}s left)",
                                colony.getName(), (cooldownMs - (now - last)) / 1000);
                    }
                    continue;
                }

                ServerLevel level = (ServerLevel) colony.getWorld();
                int eligibleCount = 0;
                Entity firstTrigger = null;

                if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 2) {
                    LOGGER.info("[EntityRaid-SCAN] Scanning colony '{}' for whitelisted entities currently INSIDE boundary",
                        colony.getName());
                }

                for (Entity e : level.getEntities().getAll()) {
                    try {
                        if (EntityRaidManager.shouldTriggerEntityRaid(e, colony)) {
                            boolean inside = false;
                            try {
                                inside = colony.isCoordInColony(colony.getWorld(), e.blockPosition());
                            } catch (Throwable t) {
                                inside = false;
                            }
                            if (!inside) {
                                continue;
                            }
                            eligibleCount++;
                            if (firstTrigger == null) firstTrigger = e;
                            if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 3) {
                                LOGGER.debug("[EntityRaid-SCAN] Entity {} is INSIDE boundary and qualifies (count: {})",
                                    e.getType().getDescriptionId(), eligibleCount);
                            }
                            if (eligibleCount >= threshold) {
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        if (TaxConfig.isEntityRaidDebugEnabled()) {
                            LOGGER.warn("[EntityRaid-SCAN] Error evaluating entity near colony '{}': {}",
                                    colony.getName(), ex.toString());
                        }
                    }
                }

                if (eligibleCount >= threshold && firstTrigger != null) {
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid-SCAN] Threshold met for colony '{}' (count={}, threshold={}) — starting raid.",
                                colony.getName(), eligibleCount, threshold);
                    }
                    EntityRaidManager.startEntityRaid(colony, firstTrigger);
                } else if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 2) {
                    LOGGER.info("[EntityRaid-SCAN] Colony '{}' count={} (threshold={}), no raid.",
                            colony.getName(), eligibleCount, threshold);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[EntityRaid-SCAN] Unexpected error during scan:", e);
        }
    }

    private static boolean isGuardTower(IBuilding building) {
        // Delegate to the cached WarSystem implementation to avoid duplicated
        // per-tick string allocations.
        return net.machiavelli.minecolonytax.WarSystem.isGuardTower(building);
    }
    
    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;
        var citizenData = citizen.getCitizenData();
        if (citizenData == null || citizenData.getJob() == null) return;
        if (!citizenData.getJob().isGuard()) return;
        IColony colony = citizenData.getColony();
        if (colony == null) return;
        ActiveRaidData raidData = RaidManager.getActiveRaidByColony(colony.getID());
        if (raidData == null) return;
        raidData.incrementGuardsKilled();
        if (TaxConfig.isNormalLogging()) LOGGER.info("Guard killed during raid! Colony: {}, Guards killed: {}/{}",
            colony.getName(), raidData.getGuardsKilled(), raidData.getTotalGuards());
        if (raidData.getBossEvent() != null) {
            double killPercentage = raidData.getGuardKillPercentage();
            String progressText = String.format("Raid Progress: %d/%d Guards Defeated (%.1f%%)", 
                raidData.getGuardsKilled(), raidData.getTotalGuards(), killPercentage * 100);
            
            raidData.getBossEvent().setName(net.minecraft.network.chat.Component.literal(progressText));
        }
    }
}
