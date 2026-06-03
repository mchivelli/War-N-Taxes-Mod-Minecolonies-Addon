## STEP 11 — Experimental Town Hall demolition objective (banner objective DEFERRED)

New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed → count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).

```java
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
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ExplosionEvent;
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
 * Experimental victory objective: attacker wins by landing N explosive hits on
 * the defender's Town Hall building (not just the central block — anywhere in
 * the building's footprint, per IBuilding.isInBuilding).
 *
 * Each war tracks per-attacker hit counts and last-hit timestamps so a 5-minute
 * cooldown gates each counted hit. Attacker must be within MaxSiegeRadius of
 * the Town Hall center. On counted hit: GLOWING applied + coordinates broadcast
 * to all war participants. Reaching the threshold ends the war as attacker
 * victory.
 *
 * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
 * lives+guards victory system — first trigger wins.
 *
 * NOTE: Plant-the-Banner objective is not implemented in this step; that path
 * requires registering a custom item/block.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        Entity sourceEntity = event.getExplosion().getDirectSourceEntity();
        if (sourceEntity == null) sourceEntity = event.getExplosion().getIndirectSourceEntity();
        if (!(sourceEntity instanceof ServerPlayer attacker)) {
            // Could be unowned TNT — for now only count player-attributable explosions.
            // Future: walk source to a placing player.
            return;
        }

        // Find the war where this player is an attacker.
        WarData war = findWarForAttacker(attacker.getUUID());
        if (war == null) return;

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
     */
    private static void triggerVictory(WarData war, ServerPlayer winner) {
        IColony defenderColony = war.getColony();
        if (defenderColony == null) return;

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
```

### Related config (TaxConfig.java diff)
```diff
+                                                +
+                                                "TNT in one chunk to instawin. Hits during cooldown still damage blocks but don't count.")
+                                .defineInRange("TownHallHitCooldownMinutes", 5, 1, 60);
+
+                MAX_SIEGE_RADIUS = BUILDER.comment(
+                                "Maximum distance (blocks) from the Town Hall centre the attacker may be in order\n"
+                                                +
+                                                "for an explosion to count as a Town Hall hit. Prevents remote-triggering.")
+                                .defineInRange("MaxSiegeRadius", 500, 50, 2000);
+
+                ATTACKER_GLOW_SECONDS = BUILDER.comment(
+                                "Seconds the GLOWING effect is applied to an attacker after they land a counted hit.\n"
+                                                +
+                                                "Lets defenders see them through walls and converge.")
+                                .defineInRange("AttackerGlowSeconds", 30, 0, 600);
+
                 // ========== Colony Occupation Settings ==========
                 BUILDER.push("Colony Occupation");
 
@@ -2630,6 +2684,30 @@ public class TaxConfig {
                 return PRIMARY_COLONY_TAX_OCCUPATION_DAYS.get();
         }
 
+        public static int getBesiegeSpoilPercentOfLoserTreasury() {
+                return BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY.get();
+        }
+
+        public static boolean isExperimentalSiegeObjectivesEnabled() {
+                return ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES.get();
+        }
+
+        public static int getTownHallExplosiveHitsRequired() {
+                return TOWN_HALL_EXPLOSIVE_HITS_REQUIRED.get();
+        }
+
+        public static int getTownHallHitCooldownMinutes() {
+                return TOWN_HALL_HIT_COOLDOWN_MINUTES.get();
+        }
+
+        public static int getMaxSiegeRadius() {
+                return MAX_SIEGE_RADIUS.get();
+        }
+
+        public static int getAttackerGlowSeconds() {
+                return ATTACKER_GLOW_SECONDS.get();
+        }
+
         public static int getWarVassalizationDurationHours() {
                 return WAR_VASSALIZATION_DURATION_HOURS.get();
         }
```
