package net.machiavelli.minecolonytax.siege;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.util.TickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental "Plant the Banner" capture-and-hold victory objective for full wars.
 *
 * Player flow:
 *  1. Attacker carries a Siege Banner item ({@code minecolonytax:siege_banner})
 *     into the defender colony.
 *  2. Right-click to place. Place is validated to be inside the Town Hall building.
 *     Outside → cancelled with a chat message.
 *  3. Successful placement → a war-scoped boss bar appears for both sides showing
 *     remaining hold time (default 10 min via {@code BannerCaptureMinutes}).
 *  4. Defenders can break the banner to cancel the capture. The attacker can
 *     re-plant ONCE ({@code BannerMaxReplants = 1}); after that the capture path
 *     is locked for this war.
 *  5. If the timer reaches zero, attacker victory — triggers the same
 *     WarSystem.checkForVictory path used by Town Hall demolition.
 *
 * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
 * banner item is still registered but the place handler treats placement as
 * a vanilla block place (no boss bar, no win check).
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlantTheBannerObjective {

    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);

    /** Active banner captures, keyed by warId. */
    private static final Map<UUID, BannerCaptureState> ACTIVE_CAPTURES = new ConcurrentHashMap<>();
    /** Per-war replant counter so the limit can't be bypassed by serial places. */
    private static final Map<UUID, Integer> REPLANT_COUNT = new ConcurrentHashMap<>();

    private static final class BannerCaptureState {
        final UUID warId;
        final BlockPos bannerPos;
        final UUID attackerUUID;
        final long expiresAtMs;
        final ServerBossEvent bossEvent;
        final long taskId;

        BannerCaptureState(UUID warId, BlockPos pos, UUID attackerUUID, long expiresAtMs,
                           ServerBossEvent bossEvent, long taskId) {
            this.warId = warId;
            this.bannerPos = pos;
            this.attackerUUID = attackerUUID;
            this.expiresAtMs = expiresAtMs;
            this.bossEvent = bossEvent;
            this.taskId = taskId;
        }
    }

    private PlantTheBannerObjective() {}

    /**
     * Cancel invalid Siege Banner placements BEFORE the block settles so the
     * item is not consumed and no transient placed/removed update is broadcast.
     * Codex wave-16 fix: previously we let the place succeed via setPlacedBy
     * and then ran {@code destroyBannerSilently}, which consumed the item.
     * EntityPlaceEvent fires pre-place and is cancellable.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getPlacedBlock().getBlock() instanceof SiegeBannerBlock)) return;
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        PlacementResult result = validatePlacement(level, pos, attacker);
        if (result.allow) {
            // Place will succeed — schedule capture start AFTER vanilla finishes settling
            // the block (next tick is safest; setPlacedBy fires synchronously after this,
            // so we hand off there).
            // Nothing to do here — onBannerPlaced runs from setPlacedBy.
            return;
        }
        // Invalid placement — cancel + notify.
        event.setCanceled(true);
        if (result.message != null) {
            attacker.sendSystemMessage(Component.literal(result.message).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
     * By this point {@link #onBlockPlace} has already validated and any invalid
     * placement was cancelled — we just start the capture.
     */
    public static void onBannerPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (level == null || level.isClientSide()) return;
        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
        if (!(placer instanceof ServerPlayer attacker)) return;

        // Re-validate (defense in depth — onBlockPlace should have handled this
        // already, but if event ordering ever shifts we don't want a stuck banner).
        PlacementResult result = validatePlacement(level, pos, attacker);
        if (!result.allow) {
            // Should be unreachable; if we get here, scrub the block so it doesn't
            // appear as a stuck capture point with no boss bar.
            destroyBannerSilently(level, pos);
            return;
        }

        // Banner pos → target colony, so if the attacker fights in multiple wars we pick the one
        // targeting THIS colony (fallback: first match).
        IColony targetColony = com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        WarData war = findWarForAttacker(attacker.getUUID(), targetColony != null ? targetColony.getID() : -1);
        if (war == null) return; // already validated above; guard for static analysis
        startCapture(war, pos, attacker);
    }

    /** Outcome of {@link #validatePlacement}. */
    private static final class PlacementResult {
        final boolean allow;
        final String message;
        PlacementResult(boolean allow, String message) { this.allow = allow; this.message = message; }
        static PlacementResult ok() { return new PlacementResult(true, null); }
        static PlacementResult deny(String msg) { return new PlacementResult(false, msg); }
    }

    /**
     * Shared validation used by both the pre-place event and the post-place hook.
     * Returns the deny reason (player-facing) when invalid.
     */
    private static PlacementResult validatePlacement(Level level, BlockPos pos, ServerPlayer attacker) {
        // Resolve the colony at the banner position so, when the attacker is in multiple wars, we pick
        // the war whose defender colony is THIS one (fallback: first match).
        IColony targetColony = com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        WarData war = findWarForAttacker(attacker.getUUID(), targetColony != null ? targetColony.getID() : -1);
        if (war == null) {
            return PlacementResult.deny(
                    "You're not an attacker in any active war — the Siege Banner does nothing here.");
        }
        if (war.getDefenderLives().containsKey(attacker.getUUID())) {
            return PlacementResult.deny(
                    "You're listed on both sides of this war — banner placement refused.");
        }
        IColony defenderColony = war.getColony();
        if (defenderColony == null) {
            return PlacementResult.deny("Defender colony unavailable.");
        }
        ITownHall townHall = findTownHall(defenderColony);
        if (townHall == null) {
            return PlacementResult.deny("No Town Hall found in the defender colony — banner cannot be planted.");
        }
        if (!((IBuilding) townHall).isInBuilding(pos)) {
            return PlacementResult.deny("The Siege Banner must be planted INSIDE the Town Hall building.");
        }
        int replantsUsed = REPLANT_COUNT.getOrDefault(war.getWarID(), 0);
        int maxReplants = TaxConfig.getBannerMaxReplants();
        if (replantsUsed > maxReplants) {
            return PlacementResult.deny(
                    "The capture path is locked — you have exhausted your re-plants for this war.");
        }
        if (ACTIVE_CAPTURES.containsKey(war.getWarID())) {
            return PlacementResult.deny("A Siege Banner is already active for this war.");
        }
        return PlacementResult.ok();
    }

    private static void startCapture(WarData war, BlockPos pos, ServerPlayer attacker) {
        int holdMinutes = TaxConfig.getBannerCaptureMinutesOrDefault();
        long now = System.currentTimeMillis();
        long expiresAt = now + holdMinutes * 60_000L;

        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("SIEGE BANNER PLANTED — " + holdMinutes + ":00 remaining")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS);
        bossEvent.setProgress(1.0f);

        // Visible only to war participants (attackers + defenders).
        addWarParticipantsToBossBar(war, attacker.getServer(), bossEvent);

        // Tick-driven progress update + expiry check.
        final long taskId = TickScheduler.scheduleRepeating(() -> {
            BannerCaptureState st = ACTIVE_CAPTURES.get(war.getWarID());
            if (st == null) return; // already cleaned up
            long left = st.expiresAtMs - System.currentTimeMillis();
            if (left <= 0) {
                onCaptureExpired(war, st);
                return;
            }
            float progress = Math.max(0f, Math.min(1f, (float) left / (float) (holdMinutes * 60_000L)));
            long secs = left / 1000;
            String label = String.format("SIEGE BANNER — %d:%02d remaining", secs / 60, secs % 60);
            st.bossEvent.setProgress(progress);
            st.bossEvent.setName(Component.literal(label).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }, 1000, 1000);

        BannerCaptureState state = new BannerCaptureState(
                war.getWarID(), pos, attacker.getUUID(), expiresAt, bossEvent, taskId);
        ACTIVE_CAPTURES.put(war.getWarID(), state);

        // Broadcast event-flavour message to participants.
        Component banner = Component.literal("Siege Banner planted by ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(attacker.getName().getString()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" — defend or break it before " + holdMinutes + " minutes!")
                        .withStyle(ChatFormatting.GOLD));
        broadcastToWarParticipants(war, attacker.getServer(), banner);
        LOGGER.info("Siege Banner planted at {} by {} for war {}", pos, attacker.getUUID(), war.getWarID());
    }

    /**
     * Defender broke the banner — cancel capture, bump replant counter.
     *
     * Codex wave-16 fix: require the breaker to be a defender. An attacker
     * breaking their own banner shouldn't consume their replant budget.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getState().getBlock() instanceof SiegeBannerBlock)) return;
        BlockPos pos = event.getPos();

        for (Map.Entry<UUID, BannerCaptureState> e : ACTIVE_CAPTURES.entrySet()) {
            BannerCaptureState st = e.getValue();
            if (!st.bannerPos.equals(pos)) continue;
            UUID warId = e.getKey();
            WarData war = warById(warId);
            if (war == null) {
                clearCapture(warId);
                return;
            }
            // Only DEFENDER breaks count as a capture cancel + replant. Anyone
            // else breaking it (attacker, third-party, admin) is treated as a
            // no-op cancel — capture clears but replant is not bumped.
            UUID breakerUUID = event.getPlayer() != null ? event.getPlayer().getUUID() : null;
            boolean breakerIsDefender = breakerUUID != null
                    && war.getDefenderLives().containsKey(breakerUUID);

            clearCapture(warId);

            if (breakerIsDefender) {
                int replants = REPLANT_COUNT.getOrDefault(warId, 0) + 1;
                REPLANT_COUNT.put(warId, replants);

                Component msg = Component.literal("The Siege Banner has been broken by ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(event.getPlayer().getName().getString())
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal(" — capture cancelled.").withStyle(ChatFormatting.GREEN));
                if (event.getPlayer() instanceof ServerPlayer sp && sp.getServer() != null) {
                    broadcastToWarParticipants(war, sp.getServer(), msg);
                }
                LOGGER.info("Siege Banner at {} broken by defender for war {} (replants used: {})",
                        pos, warId, replants);
            } else {
                LOGGER.info("Siege Banner at {} broken by non-defender {} for war {} — replant counter unchanged",
                        pos, breakerUUID, warId);
            }
            return;
        }
    }

    /**
     * Give the player a Siege Banner if they're an attacker in an active war
     * with experimental objectives enabled, and they don't already have one.
     * Called from {@code WarSystem.finalizeWarStart} and from login/reconnect.
     */
    public static void giveSiegeBannerIfNeeded(ServerPlayer player, WarData war) {
        if (player == null || war == null) return;
        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
        if (!war.getAttackerLives().containsKey(player.getUUID())) return;
        try {
            var bannerItem = ModSiegeBlocks.SIEGE_BANNER_ITEM.get();
            ItemStack probe = new ItemStack(bannerItem);
            if (!player.getInventory().contains(probe)) {
                player.getInventory().add(new ItemStack(bannerItem, 1));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to give Siege Banner to {}: {}", player.getUUID(), e.getMessage());
        }
    }

    /** Retry banner handout on login — codex wave-16 fix for offline attackers. */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WarData war = findWarForAttacker(player.getUUID());
        if (war != null) giveSiegeBannerIfNeeded(player, war);
    }

    /** Capture timer reached zero — attacker wins via the same path as Town Hall demolition. */
    private static void onCaptureExpired(WarData war, BannerCaptureState st) {
        clearCapture(war.getWarID());

        // Guard symmetric to TownHallDemolitionObjective: refuse if defender-win
        // condition is already true (legacy resolver would flip the result).
        boolean hasAttackers = !war.getAttackerLives().isEmpty();
        boolean allAttackersDead = hasAttackers
                && war.getAttackerLives().values().stream().allMatch(v -> v <= 0);
        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;
        boolean defendersWouldWin =
                (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);
        if (defendersWouldWin) {
            LOGGER.info("Banner capture refused at expiry: defender-win condition is already true for war {}",
                    war.getWarID());
            return;
        }

        // Broadcast + trigger attacker victory.
        MinecraftServer server = null;
        if (war.getColony() != null && war.getColony().getWorld() != null) {
            server = war.getColony().getWorld().getServer();
        }
        if (server != null) {
            Component victoryMsg = Component.literal(
                    "EXPERIMENTAL VICTORY — Siege Banner held to completion!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            broadcastToWarParticipants(war, server, victoryMsg);
        }

        // Zero defender lives so checkForVictory resolves as attacker win.
        for (Map.Entry<UUID, Integer> e : new HashMap<>(war.getDefenderLives()).entrySet()) {
            war.getDefenderLives().put(e.getKey(), 0);
        }
        war.remainingDefenderGuards = 0;
        try {
            WarSystem.checkForVictory(war);
        } catch (Exception ex) {
            LOGGER.error("Failed to trigger banner-capture victory for war {}", war.getWarID(), ex);
        }
    }

    /** Cleanup hook from WarSystem.endWar — drop per-war state. */
    public static void onWarEnded(UUID warId) {
        clearCapture(warId);
        REPLANT_COUNT.remove(warId);
    }

    private static void clearCapture(UUID warId) {
        BannerCaptureState st = ACTIVE_CAPTURES.remove(warId);
        if (st == null) return;
        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
        try {
            if (st.bossEvent != null) {
                st.bossEvent.removeAllPlayers();
                st.bossEvent.setVisible(false);
            }
        } catch (Exception ignored) {}
    }

    private static void destroyBannerSilently(Level level, BlockPos pos) {
        try {
            level.removeBlock(pos, false);
        } catch (Exception ignored) {}
    }

    private static WarData findWarForAttacker(UUID attackerUUID) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (war.getAttackerLives().containsKey(attackerUUID)) return war;
        }
        return null;
    }

    /**
     * Prefer the war whose DEFENDER colony == targetColonyId (the colony the banner is planted in),
     * falling back to the first war the player attacks in. Disambiguates when a player is an attacker
     * in more than one active war. A negative/unmatched targetColonyId simply yields the first match.
     */
    private static WarData findWarForAttacker(UUID attackerUUID, int targetColonyId) {
        WarData fallback = null;
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (!war.getAttackerLives().containsKey(attackerUUID)) continue;
            if (war.getColony() != null && war.getColony().getID() == targetColonyId) return war;
            if (fallback == null) fallback = war;
        }
        return fallback;
    }

    private static WarData warById(UUID warId) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (warId.equals(war.getWarID())) return war;
        }
        return null;
    }

    private static ITownHall findTownHall(IColony colony) {
        try {
            for (IBuilding b : net.machiavelli.minecolonytax.compat.ColonyBuildingUtil.getBuildings(colony)) {
                if (b instanceof ITownHall th) return th;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void addWarParticipantsToBossBar(WarData war, MinecraftServer server, ServerBossEvent bossEvent) {
        if (server == null) return;
        for (UUID uuid : war.getAttackerLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) bossEvent.addPlayer(p);
        }
        for (UUID uuid : war.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) bossEvent.addPlayer(p);
        }
    }

    private static void broadcastToWarParticipants(WarData war, MinecraftServer server, Component msg) {
        if (server == null) return;
        for (UUID uuid : war.getAttackerLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(msg);
        }
        for (UUID uuid : war.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(msg);
        }
    }
}
