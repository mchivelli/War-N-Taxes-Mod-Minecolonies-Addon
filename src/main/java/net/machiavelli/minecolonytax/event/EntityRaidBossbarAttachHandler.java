package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.raid.EntityRaidManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures eligible players see the active entity raid bossbar when they
 * log in, respawn, or change dimensions.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityRaidBossbarAttachHandler {
    private static final Logger LOGGER = LogManager.getLogger(EntityRaidBossbarAttachHandler.class);

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!TaxConfig.ENABLE_ENTITY_RAIDS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        attachToActiveRaids(player, "login");
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!TaxConfig.ENABLE_ENTITY_RAIDS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        attachToActiveRaids(player, "respawn");
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!TaxConfig.ENABLE_ENTITY_RAIDS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        attachToActiveRaids(player, "dimension-change");
    }

    private static void attachToActiveRaids(ServerPlayer player, String cause) {
        try {
            for (EntityRaidManager.ActiveEntityRaid raid : EntityRaidManager.getActiveEntityRaids().values()) {
                // Optionally limit to same dimension/world; bossbar is global but we keep it relevant
                if (raid.getColony() != null && raid.getColony().getWorld() == player.level()) {
                    raid.attachEligiblePlayer(player);
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid] Bossbar attach check for {} on {} — colony '{}'", 
                            player.getGameProfile().getName(), cause, raid.getColony().getName());
                    }
                }
            }
        } catch (Exception e) {
            if (TaxConfig.isEntityRaidDebugEnabled()) {
                LOGGER.warn("[EntityRaid] Failed during bossbar attach on {}: {}", cause, e.toString());
            }
        }
    }
}
