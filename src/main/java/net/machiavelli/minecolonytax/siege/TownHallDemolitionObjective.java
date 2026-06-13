package net.machiavelli.minecolonytax.siege;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental victory objective: attacker wins by landing N explosive hits on
 * the defender's Town Hall building (not just the central block — anywhere in
 * the building's footprint, per IBuilding.isInBuilding). NeoForge 1.21.1 port
 * of the Forge {@code TownHallDemolitionObjective}.
 *
 * Each war tracks per-attacker hit counts and last-hit timestamps so a cooldown
 * gates each counted hit. Attacker must be within MaxSiegeRadius of the Town
 * Hall center. On counted hit: GLOWING applied + coordinates broadcast to all
 * war participants. Reaching the threshold ends the war as attacker victory.
 *
 * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
 * lives+guards victory system — first trigger wins.
 *
 * NeoForge differences from Forge:
 *  - NeoForge {@code ExplosionEvent.Detonate} + {@code @EventBusSubscriber} +
 *    {@code net.neoforged.bus.api} event annotations.
 *  - In 1.21 {@code Explosion.getIndirectSourceEntity()} returns a
 *    {@code LivingEntity} (was {@code Entity} in 1.20.1); typed accordingly.
 *    {@code instanceof ServerPlayer} still narrows correctly.
 *  - GLOWING is applied via the vanilla {@code MobEffects.GLOWING} effect on the
 *    ServerPlayer (replicates server→client automatically); no glow packet is
 *    used here, matching the Forge source.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TownHallDemolitionObjective {

    private static final Logger LOGGER = LogManager.getLogger(TownHallDemolitionObjective.class);

    /** Per-war hit progress, keyed by warId then attacker UUID. */
    private static final Map<UUID, Map<UUID, AttackerHitState>> WAR_HITS = new ConcurrentHashMap<>();

    private static final class AttackerHitState {
        int hits = 0;
        long lastHitMs = 0L;
    }

    private TownHallDemolitionObjective() {}

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
        if (WarSystem.ACTIVE_WARS.isEmpty()) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        // Resolve to the player owner of the explosion. For player-lit TNT/projectiles
        // the DIRECT source is the PrimedTnt/arrow entity and the INDIRECT source is
        // the player who lit/fired it. Prefer indirect-ServerPlayer first, then direct.
        ServerPlayer attacker = null;
        LivingEntity indirect = event.getExplosion().getIndirectSourceEntity();
        if (indirect instanceof ServerPlayer sp) {
            attacker = sp;
        } else {
            Entity direct = event.getExplosion().getDirectSourceEntity();
            if (direct instanceof ServerPlayer sp2) attacker = sp2;
        }
        if (attacker == null) {
            // Unattributable explosion (creeper, dispenser TNT, etc.) — don't count.
            return;
        }

        // Find the war where this player is an attacker.
        WarData war = findWarForAttacker(attacker.getUUID());
        if (war == null) return;
        // Hard-reject: if the player is somehow also a defender, refuse to count
        // the hit. Prevents accidental self-sabotage AND deliberate self-victory.
        if (war.getDefenderLives().containsKey(attacker.getUUID())) return;

        IColony defenderColony = war.getColony();
        if (defenderColony == null) return;
        // Per CLAUDE.md, never call getBuildingManager() directly — route through ColonyBuildingUtil.
        ITownHall townHall = null;
        try {
            for (IBuilding b : net.machiavelli.minecolonytax.compat.ColonyBuildingUtil.getBuildings(defenderColony)) {
                if (b instanceof ITownHall th) { townHall = th; break; }
            }
        } catch (Exception ignored) {}
        if (townHall == null) return;

        // Did any block in the affected list actually fall inside the Town Hall building?
        boolean hitTownHall = false;
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (((IBuilding) townHall).isInBuilding(pos)) {
                hitTownHall = true;
                break;
            }
        }
        if (!hitTownHall) return;

        // Attacker must be within MaxSiegeRadius of the Town Hall center.
        BlockPos thCenter = townHall.getPosition();
        int maxRadius = TaxConfig.getMaxSiegeRadius();
        double distSq = attacker.distanceToSqr(thCenter.getX() + 0.5, thCenter.getY() + 0.5, thCenter.getZ() + 0.5);
        if (distSq > (double) maxRadius * maxRadius) {
            attacker.sendSystemMessage(Component.literal(
                    "Town Hall hit registered but you are outside the siege radius (" + maxRadius + " blocks).")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        // Per-attacker cooldown gate.
        Map<UUID, AttackerHitState> warMap = WAR_HITS.computeIfAbsent(war.getWarID(), k -> new ConcurrentHashMap<>());
        AttackerHitState state = warMap.computeIfAbsent(attacker.getUUID(), k -> new AttackerHitState());
        long now = System.currentTimeMillis();
        long cooldownMs = TaxConfig.getTownHallHitCooldownMinutes() * 60_000L;
        if (now - state.lastHitMs < cooldownMs) {
            long remainingSec = (cooldownMs - (now - state.lastHitMs)) / 1000L;
            attacker.sendSystemMessage(Component.literal(
                    "Town Hall hit registered but on cooldown — " + remainingSec + "s remaining before it counts.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        state.hits++;
        state.lastHitMs = now;

        // Apply glow + broadcast coords.
        int glowSec = TaxConfig.getAttackerGlowSeconds();
        if (glowSec > 0) {
            attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowSec * 20, 0, false, true));
        }
        int required = TaxConfig.getTownHallExplosiveHitsRequired();
        broadcastHit(war, attacker, state.hits, required);

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Experimental: attacker {} hit Town Hall of colony {} ({}/{})",
                    attacker.getName().getString(), defenderColony.getName(), state.hits, required);
        }

        // Victory check.
        if (state.hits >= required) {
            triggerVictory(war, attacker);
        }
    }

    /** Broadcast counted-hit info to all war participants. */
    private static void broadcastHit(WarData war, ServerPlayer attacker, int hits, int required) {
        if (attacker.getServer() == null) return;
        BlockPos pos = attacker.blockPosition();
        Component msg = Component.literal("Town Hall struck! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(attacker.getName().getString()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") — hits: ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(hits + "/" + required)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        for (UUID uuid : war.getAttackerLives().keySet()) {
            ServerPlayer p = attacker.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(msg);
        }
        for (UUID uuid : war.getDefenderLives().keySet()) {
            ServerPlayer p = attacker.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    /**
     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
     * defender lives to zero so the existing checkForVictory + endWar pipeline
     * concludes naturally with the attacker as winner.
     *
     * Race guard (bug #9 fix): if attackers are already at 0 lives/guards,
     * checkForVictory would resolve as DEFENDER victory the moment we zero
     * the defender lives. We detect that case before mutating and refuse to
     * trigger — the legacy resolution will play out on its own; demolishing
     * the Town Hall in the dying breath shouldn't flip the result.
     */
    private static void triggerVictory(WarData war, ServerPlayer winner) {
        IColony defenderColony = war.getColony();
        if (defenderColony == null) return;

        // Mirror WarSystem.checkForVictory's defender-win condition exactly. If we
        // proceed when defenders would win, the legacy resolver will flip the result
        // to defender victory the moment we zero defender lives (which is exactly
        // what triggers the resolver). Use the SAME logic so we never race.
        boolean hasAttackers = !war.getAttackerLives().isEmpty();
        boolean allAttackersDead = hasAttackers
                && war.getAttackerLives().values().stream().allMatch(v -> v <= 0);
        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;
        boolean defendersWouldWin =
                (hasAttackers && allAttackersDead)
                || (!hasAttackers && allAttackerGuardsDead);
        if (defendersWouldWin) {
            LOGGER.info("Experimental victory refused: defender-win condition is already true for war {}; "
                    + "the legacy resolver will run.", war.getWarID());
            WAR_HITS.remove(war.getWarID());
            return;
        }

        // Broadcast the victory cause.
        Component victoryMsg = Component.literal("EXPERIMENTAL VICTORY — Town Hall demolished!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (winner.getServer() != null) {
            for (UUID uuid : war.getAttackerLives().keySet()) {
                ServerPlayer p = winner.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(victoryMsg);
            }
            for (UUID uuid : war.getDefenderLives().keySet()) {
                ServerPlayer p = winner.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(victoryMsg);
            }
        }

        // Zero defender lives + nudge checkForVictory to drive the existing flow.
        for (Map.Entry<UUID, Integer> e : new HashMap<>(war.getDefenderLives()).entrySet()) {
            war.getDefenderLives().put(e.getKey(), 0);
        }
        war.remainingDefenderGuards = 0;

        try {
            WarSystem.checkForVictory(war);
        } catch (Exception e) {
            LOGGER.error("Failed to trigger experimental victory for war {}", war.getWarID(), e);
        }

        // Cleanup per-war state.
        WAR_HITS.remove(war.getWarID());
    }

    /**
     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
     * a war ends through normal (non-demolition) resolution.
     */
    public static void onWarEnded(UUID warId) {
        WAR_HITS.remove(warId);
    }

    private static WarData findWarForAttacker(UUID attackerUUID) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (war.getAttackerLives().containsKey(attackerUUID)) return war;
        }
        return null;
    }

    /** Drop all state — for server shutdown or war end. */
    public static void clearAll() {
        WAR_HITS.clear();
    }
}
