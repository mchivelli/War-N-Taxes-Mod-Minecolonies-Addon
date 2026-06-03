Reading additional input from stdin...
OpenAI Codex v0.133.0
--------
workdir: C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: xhigh
reasoning summaries: none
session id: 019e5cf2-aac9-7b70-b1e9-6be4ebfca9a3
--------
user
Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.

<stdin>
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
</stdin>
codex
I’ll check the existing victory path and war data shape so the review is based on the repo behavior, then I’ll keep the answer within your requested limits.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "checkForVictory|endWar|ACTIVE_WARS|class WarData|defenderLives|attackerLives" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ExplosionEvent\\.Detonate|getDirectSourceEntity|getIndirectSourceEntity|TownHallDemolitionObjective|EnableExperimentalSiegeObjectives|TownHallHitCooldown\" -S ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 1460ms:
.\CHANGELOG.md:537:- Integrated with `WarSystem.checkForVictory()` to trigger vassalization on attacker victory
.\reviews\step-01-codex.md:261:+                    WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:310:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class WarSystem|public static void transferOwnership|sendNotificationToWarParticipants|class VassalManager|forceVassalize|ACTIVE_WARS|ENABLE_COLONY_TRANSFER|isColonyTransferEnabled" src/main/java/net/machiavelli/minecolonytax' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-01-codex.md:572:src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:233:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-01-codex.md:574:src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:160:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)
.\reviews\step-01-codex.md:575:src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:204:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:576:src/main/java/net/machiavelli/minecolonytax\commands\ClaimTaxCommand.java:119:                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:577:src/main/java/net/machiavelli/minecolonytax\commands\ClaimTaxCommand.java:121:                    for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:579:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:96:    public static final Map<Integer, WarData> ACTIVE_WARS = new ConcurrentHashMap<>();
.\reviews\step-01-codex.md:580:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:301:        ACTIVE_WARS.put(colony.getID(), data);
.\reviews\step-01-codex.md:581:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\step-01-codex.md:590:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1201:                    WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:592:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1218:            WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:594:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1227:        WarData warData = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:595:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1260:        warData = ACTIVE_WARS.remove(colony.getID());
.\reviews\step-01-codex.md:599:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2035:        for (WarData war : ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:600:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2114:                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
.\reviews\step-01-codex.md:603:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2415:            WarData war = ACTIVE_WARS.get(defenderColony.getID());
.\reviews\step-01-codex.md:604:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2461:            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
.\reviews\step-01-codex.md:605:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2710:        if (ACTIVE_WARS.containsKey(attackerColony.getID())) {
.\reviews\step-01-codex.md:606:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2716:        boolean attackerAlreadyWaging = ACTIVE_WARS.values().stream()
.\reviews\step-01-codex.md:607:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2935:        ACTIVE_WARS.put(targetColony.getID(), warData);
.\reviews\step-01-codex.md:608:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:3341:        for (WarData activeWar : ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:609:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:3759:        for (WarData warData : ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:610:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:3919:            for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
.\reviews\step-01-codex.md:611:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4106:        ACTIVE_WARS.put(e.defenderColonyId, warData);
.\reviews\step-01-codex.md:612:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4166:                    if (!ACTIVE_WARS.containsKey(colonyId))
.\reviews\step-01-codex.md:613:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4168:                    WarData w = ACTIVE_WARS.get(colonyId);
.\reviews\step-01-codex.md:614:src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java:545:        final int activeWars    = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.size();
.\reviews\step-01-codex.md:618:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:122:        if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
.\reviews\step-01-codex.md:619:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:262:        if (WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-01-codex.md:620:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:268:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:621:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:314:        Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
.\reviews\step-01-codex.md:622:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:342:        if (!WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
.\reviews\step-01-codex.md:623:src/main/java/net/machiavelli/minecolonytax\events\random\EventTriggerSystem.java:189:            if (net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-01-codex.md:624:src/main/java/net/machiavelli/minecolonytax\events\random\EventTriggerSystem.java:194:            for (net.machiavelli.minecolonytax.data.WarData war : net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:625:src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:109:                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
.\reviews\step-01-codex.md:626:src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:235:        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-01-codex.md:627:src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:236:            for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:628:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:192:                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
.\reviews\step-01-codex.md:629:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:211:                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
.\reviews\step-01-codex.md:630:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1314:                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
.\reviews\step-01-codex.md:631:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1349:                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
.\reviews\step-01-codex.md:632:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1686:                if (WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-01-codex.md:633:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1692:                                () -> Component.literal("=== War Debug (" + WarSystem.ACTIVE_WARS.size() + " active) ===")
.\reviews\step-01-codex.md:634:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1695:                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:635:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1758:                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:636:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1770:                Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
.\reviews\step-01-codex.md:637:src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimTaxPacket.java:83:                if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-01-codex.md:638:src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:151:                    WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
.\reviews\step-01-codex.md:639:src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:153:                        for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:640:src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:160:                        boolean isDefender = WarSystem.ACTIVE_WARS.containsKey(colonyId);
.\reviews\step-01-codex.md:641:src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:77:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:642:src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:62:            net.machiavelli.minecolonytax.data.WarData warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS
.\reviews\step-01-codex.md:643:src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:93:            warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:644:src/main/java/net/machiavelli/minecolonytax\TaxManager.java:280:        WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
.\reviews\step-01-codex.md:645:src/main/java/net/machiavelli/minecolonytax\TaxManager.java:282:            for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:646:src/main/java/net/machiavelli/minecolonytax\event\WarEconomyHandler.java:41:            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:647:src/main/java/net/machiavelli/minecolonytax\event\WarEconomyHandler.java:231:            for (WarData w : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:648:src/main/java/net/machiavelli/minecolonytax\event\WarEconomyHandler.java:251:                WarData war = WarSystem.ACTIVE_WARS.get(losingColony.getID());
.\reviews\step-01-codex.md:656:src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:269:                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:657:src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:314:        WarData war = WarSystem.ACTIVE_WARS.get(citizenColony.getID());
.\reviews\step-01-codex.md:658:src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:426:        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-01-codex.md:659:src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:427:            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:660:src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:211:        WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
.\reviews\step-01-codex.md:661:src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:218:        for (WarData activeWar : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:662:src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:264:        boolean warOrRaidActive = WarSystem.ACTIVE_WARS.containsKey(colonyId)
.\reviews\step-01-codex.md:663:src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:38: *      (i.e., after ACTIVE_WARS.remove() or activeRaids.remove()).
.\reviews\step-01-codex.md:664:src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:88:     * (after ACTIVE_WARS.remove() or activeRaids.remove()), otherwise the conflict
.\reviews\step-01-codex.md:665:src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:174:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:666:src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:60:                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
.\reviews\step-01-codex.md:667:src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:140:            WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
.\reviews\step-01-codex.md:668:src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:143:                for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-01-codex.md:669:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:142:            if (WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
.\reviews\step-01-codex.md:794:                    WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:811:            WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:818:    public static void endWar(IColony colony) {
.\reviews\step-01-codex.md:820:        WarData warData = ACTIVE_WARS.get(colony.getID());
.\reviews\step-01-codex.md:914:        endWar(war.getColony());
.\reviews\step-01-codex.md:1236:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-01-bundle.md:245:+                    WarData war = ACTIVE_WARS.get(colony.getID());
.\wiki\War_Persistence.md:9:Prior to this feature, **all active wars were lost on server stop or crash**. The `ACTIVE_WARS` map lived entirely in memory, meaning any server restart during an ongoing war would silently erase the conflict â€” no winner, no loser, no consequences. Players could also exploit this by forcing a restart to escape a losing war.
.\wiki\War_Persistence.md:53:2. Iterates every entry in the `ACTIVE_WARS` concurrent map
.\wiki\War_Persistence.md:107:- Puts the `WarData` into `ACTIVE_WARS` keyed by defender colony ID
.\wiki\War_Persistence.md:169:      "attackerLives": {
.\wiki\War_Persistence.md:173:      "defenderLives": {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:96:    public static final Map<Integer, WarData> ACTIVE_WARS = new ConcurrentHashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:301:        ACTIVE_WARS.put(colony.getID(), data);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:348:                    endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:532:            int attackerLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:533:            int defenderLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:537:                    " | Attackers: " + attackerLives +
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:538:                    " | Defenders: " + defenderLives;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:558:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:668:    public static void checkForVictory(WarData war) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:791:        endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1214:                    WarData war = ACTIVE_WARS.get(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1232:            WarData war = ACTIVE_WARS.get(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1241:    public static void endWar(IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1243:        WarData warData = ACTIVE_WARS.get(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1276:        warData = ACTIVE_WARS.remove(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1586:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1611:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1647:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1757:        endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1777:        checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2051:        for (WarData war : ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2130:                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2431:            WarData war = ACTIVE_WARS.get(defenderColony.getID());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2477:            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2726:        if (ACTIVE_WARS.containsKey(attackerColony.getID())) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2732:        boolean attackerAlreadyWaging = ACTIVE_WARS.values().stream()
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2947:        // Without this, the victory check (attackerLives==0 && attackerGuards==0)
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2951:        ACTIVE_WARS.put(targetColony.getID(), warData);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3357:        for (WarData activeWar : ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3775:        for (WarData warData : ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3906:        Map<String, Integer> attackerLives;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3907:        Map<String, Integer> defenderLives;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3935:            for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3958:                e.attackerLives = new HashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3959:                war.getAttackerLives().forEach((uuid, lives) -> e.attackerLives.put(uuid.toString(), lives));
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3960:                e.defenderLives = new HashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3961:                war.getDefenderLives().forEach((uuid, lives) -> e.defenderLives.put(uuid.toString(), lives));
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4068:        Map<UUID, Integer> attackerLives = new HashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4069:        if (e.attackerLives != null) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4070:            e.attackerLives.forEach((k, v) -> attackerLives.put(UUID.fromString(k), v));
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4072:        Map<UUID, Integer> defenderLives = new HashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4073:        if (e.defenderLives != null) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4074:            e.defenderLives.forEach((k, v) -> defenderLives.put(UUID.fromString(k), v));
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4117:                attackerLives, defenderLives,
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4122:        ACTIVE_WARS.put(e.defenderColonyId, warData);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4182:                    if (!ACTIVE_WARS.containsKey(colonyId))
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4184:                    WarData w = ACTIVE_WARS.get(colonyId);
.\reviews\step-06-codex.md:72:+                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
.\reviews\step-06-codex.md:130:+        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-06-codex.md:131:             for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-06-bundle.md:56:+                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
.\reviews\step-06-bundle.md:114:+        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-06-bundle.md:115:             for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-05-codex.md:983:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-05-codex.md:1089:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:231:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-03-codex.md:674:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-426-        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-03-codex.md:675:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-427-            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-03-codex.md:693:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-03-codex.md:2268:77-        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-03-codex.md:2382:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-426-        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
.\reviews\step-03-codex.md:2383:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-427-            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-03-codex.md:2401:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-03-codex.md:2426:src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-151-                    WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
.\reviews\step-03-codex.md:2428:src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-153-                        for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-03-codex.md:2435:src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-160-                        boolean isDefender = WarSystem.ACTIVE_WARS.containsKey(colonyId);
.\reviews\step-02-codex.md:224:+                    WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-02-codex.md:244:             WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-02-bundle.md:208:+                    WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-02-bundle.md:228:             WarData war = ACTIVE_WARS.get(colony.getID());
.\reviews\step-07-codex.md:419:src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:60:                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
.\reviews\step-07-codex.md:1503:231-        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-10-bundle.md:3:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet â€” caller integration is a follow-up. NOT persisted across server restarts yet â€” ledger lost on crash.
.\reviews\step-10-bundle.md:38: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-10-bundle.md:77:        if (WarSystem.ACTIVE_WARS.isEmpty()) return;
.\reviews\step-10-bundle.md:124:     * Called from WarSystem.endWar() â€” safe to call when no ledger exists.
.\reviews\step-10-bundle.md:180:     * ACTIVE_WARS map.
.\reviews\step-10-bundle.md:183:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-10-codex.md:14:Reviewing step 10 of 11. New WarBlockLedger captures explosion-damaged blocks per active war. Architecture mirrors harmonised/explosiont's BlockInfo pattern. Review for: (1) interaction with the explosion event - we snapshot but DON'T clear the affected blocks ourselves (explosion proceeds normally and clears them), so restoration just rewrites them back; is this correct? (2) BlockEntity NBT serialize/deserialize - is serializeNBT()/deserializeNBT() correct API for MC 1.20.1?; (3) thread safety of ledger access between event thread and TickScheduler; (4) memory bound if many explosions, (5) wiring gap (not called from endWar yet). Respond: STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-10-codex.md:19:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet â€” caller integration is a follow-up. NOT persisted across server restarts yet â€” ledger lost on crash.
.\reviews\step-10-codex.md:54: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-10-codex.md:93:        if (WarSystem.ACTIVE_WARS.isEmpty()) return;
.\reviews\step-10-codex.md:140:     * Called from WarSystem.endWar() â€” safe to call when no ledger exists.
.\reviews\step-10-codex.md:196:     * ACTIVE_WARS map.
.\reviews\step-10-codex.md:199:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-10-codex.md:221:- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.
.\reviews\step-10-codex.md:226:3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
.\reviews\step-10-codex.md:235:- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.
.\reviews\step-10-codex.md:240:3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-bundle.md:70:        if (WarSystem.ACTIVE_WARS.isEmpty()) return;
.\reviews\step-11-bundle.md:176:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-bundle.md:177:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-bundle.md:198:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-bundle.md:205:            WarSystem.checkForVictory(war);
.\reviews\step-11-bundle.md:215:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:280:        WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:282:            for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:33: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:72:        if (WarSystem.ACTIVE_WARS.isEmpty()) return;
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:119:     * Called from WarSystem.endWar() â€” safe to call when no ledger exists.
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:175:     * ACTIVE_WARS map.
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:178:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:65:        if (WarSystem.ACTIVE_WARS.isEmpty()) return;
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:171:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:172:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:193:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:200:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:210:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:60:                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
.\src\main\java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:140:            WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:143:                for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:142:            if (WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:186:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:200:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:269:                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:314:        WarData war = WarSystem.ACTIVE_WARS.get(citizenColony.getID());
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:356:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:426:        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:427:            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:445:    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEconomyHandler.java:41:            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEconomyHandler.java:231:            for (WarData w : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEconomyHandler.java:251:                WarData war = WarSystem.ACTIVE_WARS.get(losingColony.getID());
.\src\main\java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:77:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\event\RaidKillTracker.java:62:            net.machiavelli.minecolonytax.data.WarData warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS
.\src\main\java\net\machiavelli\minecolonytax\event\RaidKillTracker.java:93:            warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:109:                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
.\src\main\java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:247:        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
.\src\main\java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:248:            for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:38: *      (i.e., after ACTIVE_WARS.remove() or activeRaids.remove()).
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:88:     * (after ACTIVE_WARS.remove() or activeRaids.remove()), otherwise the conflict
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:174:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:211:        WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:218:        for (WarData activeWar : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:264:        boolean warOrRaidActive = WarSystem.ACTIVE_WARS.containsKey(colonyId)
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:248:                // Set penalty report before endWar so war history logging captures peace outcome
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:250:                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:301:                // Set penalty report before endWar so war history logging captures peace outcome
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:304:                WarSystem.endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:332:                // Set penalty report before endWar
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:335:                WarSystem.endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:151:                    WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:153:                        for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:160:                        boolean isDefender = WarSystem.ACTIVE_WARS.containsKey(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:336:     * @param warData        The WarData captured at endWar() time (still valid after remove).
.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:545:        final int activeWars    = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.size();
.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:573:    /** Determine the DB outcome string from a WarData at the moment endWar() is called. */
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:160:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:204:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:14:public class WarData {
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:23:    private final Map<UUID, Integer> attackerLives = new ConcurrentHashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:24:    private final Map<UUID, Integer> defenderLives = new ConcurrentHashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:104:                   Map<UUID, Integer> attackerLivesData, Map<UUID, Integer> defenderLivesData,
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:129:        if (attackerLivesData != null) this.attackerLives.putAll(attackerLivesData);
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:130:        if (defenderLivesData != null) this.defenderLives.putAll(defenderLivesData);
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:172:    public Map<UUID, Integer> getAttackerLives() { return attackerLives; }
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:173:    public Map<UUID, Integer> getDefenderLives() { return defenderLives; }
.\src\main\java\net\machiavelli\minecolonytax\network\packets\ClaimTaxPacket.java:83:                if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:192:                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:211:                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1314:                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1349:                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1686:                if (WarSystem.ACTIVE_WARS.isEmpty()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1692:                                () -> Component.literal("=== War Debug (" + WarSystem.ACTIVE_WARS.size() + " active) ===")
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1695:                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1758:                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1764:                WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1770:                Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1783:                        WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:122:        if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:262:        if (WarSystem.ACTIVE_WARS.isEmpty()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:268:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:314:        Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:324:            WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:342:        if (!WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:347:        WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\events\random\EventTriggerSystem.java:189:            if (net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\src\main\java\net\machiavelli\minecolonytax\events\random\EventTriggerSystem.java:194:            for (net.machiavelli.minecolonytax.data.WarData war : net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\ClaimTaxCommand.java:119:                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
.\src\main\java\net\machiavelli\minecolonytax\commands\ClaimTaxCommand.java:121:                    for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:231:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
.\reviews\step-11-codex.md:14:Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:86:        if (WarSystem.ACTIVE_WARS.isEmpty()) return;
.\reviews\step-11-codex.md:192:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-codex.md:193:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-codex.md:214:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-codex.md:221:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:231:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
.\reviews\step-11-codex.md:301:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "checkForVictory|endWar|ACTIVE_WARS|class WarData|defenderLives|attackerLives" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 1469ms:
.\reviews\step-10-bundle.md:3:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet â€” caller integration is a follow-up. NOT persisted across server restarts yet â€” ledger lost on crash.
.\reviews\step-10-bundle.md:76:    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
.\reviews\step-10-codex.md:19:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet â€” caller integration is a follow-up. NOT persisted across server restarts yet â€” ledger lost on crash.
.\reviews\step-10-codex.md:92:    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
.\reviews\step-10-codex.md:217:- Explosion handling is conceptually correct: in `ExplosionEvent.Detonate`, snapshotting `event.getAffectedBlocks()` and leaving the list intact lets vanilla/Forge explosion destruction proceed, then restoration rewrites blocks later. The code comment claiming â€œsnapshot AND clearâ€ is wrong and should be corrected.
.\reviews\step-10-codex.md:231:- Explosion handling is conceptually correct: in `ExplosionEvent.Detonate`, snapshotting `event.getAffectedBlocks()` and leaving the list intact lets vanilla/Forge explosion destruction proceed, then restoration rewrites blocks later. The code comment claiming â€œsnapshot AND clearâ€ is wrong and should be corrected.
.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-bundle.md:46: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-bundle.md:53:public final class TownHallDemolitionObjective {
.\reviews\step-11-bundle.md:55:    private static final Logger LOGGER = LogManager.getLogger(TownHallDemolitionObjective.class);
.\reviews\step-11-bundle.md:65:    private TownHallDemolitionObjective() {}
.\reviews\step-11-bundle.md:68:    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
.\reviews\step-11-bundle.md:75:        Entity sourceEntity = event.getExplosion().getDirectSourceEntity();
.\reviews\step-11-bundle.md:76:        if (sourceEntity == null) sourceEntity = event.getExplosion().getIndirectSourceEntity();
.\reviews\step-11-bundle.md:123:        long cooldownMs = TaxConfig.getTownHallHitCooldownMinutes() * 60_000L;
.\reviews\step-11-bundle.md:232:+                                .defineInRange("TownHallHitCooldownMinutes", 5, 1, 60);
.\reviews\step-11-bundle.md:265:+        public static int getTownHallHitCooldownMinutes() {
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:666:                                .define("EnableExperimentalSiegeObjectives", false);
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:676:                                .defineInRange("TownHallHitCooldownMinutes", 5, 1, 60);
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:2699:        public static int getTownHallHitCooldownMinutes() {
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:71:    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:41: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:48:public final class TownHallDemolitionObjective {
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:50:    private static final Logger LOGGER = LogManager.getLogger(TownHallDemolitionObjective.class);
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:60:    private TownHallDemolitionObjective() {}
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:63:    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:70:        Entity sourceEntity = event.getExplosion().getDirectSourceEntity();
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:71:        if (sourceEntity == null) sourceEntity = event.getExplosion().getIndirectSourceEntity();
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:118:        long cooldownMs = TaxConfig.getTownHallHitCooldownMinutes() * 60_000L;
.\reviews\step-11-codex.md:14:Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:62: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-codex.md:69:public final class TownHallDemolitionObjective {
.\reviews\step-11-codex.md:71:    private static final Logger LOGGER = LogManager.getLogger(TownHallDemolitionObjective.class);
.\reviews\step-11-codex.md:81:    private TownHallDemolitionObjective() {}
.\reviews\step-11-codex.md:84:    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
.\reviews\step-11-codex.md:91:        Entity sourceEntity = event.getExplosion().getDirectSourceEntity();
.\reviews\step-11-codex.md:92:        if (sourceEntity == null) sourceEntity = event.getExplosion().getIndirectSourceEntity();
.\reviews\step-11-codex.md:139:        long cooldownMs = TaxConfig.getTownHallHitCooldownMinutes() * 60_000L;
.\reviews\step-11-codex.md:248:+                                .defineInRange("TownHallHitCooldownMinutes", 5, 1, 60);
.\reviews\step-11-codex.md:281:+        public static int getTownHallHitCooldownMinutes() {
.\reviews\step-11-codex.md:303:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ExplosionEvent\\.Detonate|getDirectSourceEntity|getIndirectSourceEntity|TownHallDemolitionObjective|EnableExperimentalSiegeObjectives|TownHallHitCooldown\" -S ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:272:     * ExplosionEvent.Detonate handler.
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:274:     * @param event ExplosionEvent.Detonate
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:277:    public void on(final ExplosionEvent.Detonate event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:318:     * @param event ExplosionEvent.Detonate
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The existing `checkForVictory` path is the key part here: whether this is just a state nudge or a semantic shortcut depends on how it derives winner/loser from lives and guards.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 650 -First 170' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/data/WarData.java'; Get-Content "'$p | Select-Object -First 220' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 650 -First 170'` rejected: blocked by policy
2026-05-25T02:25:02.100958Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 650 -First 170'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/data/WarData.java'; Get-Content "'$p | Select-Object -First 220'` rejected: blocked by policy
2026-05-25T02:25:02.101779Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/data/WarData.java'; Get-Content "'$p | Select-Object -First 220'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 1230 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 1230 -First 90'` rejected: blocked by policy
2026-05-25T02:25:02.107460Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 1230 -First 90'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java'; Get-Content "'$p | Select-Object -Skip 260 -First 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java'; Get-Content "'$p | Select-Object -Skip 260 -First 80'` rejected: blocked by policy
2026-05-25T02:25:02.120798Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java'; Get-Content "'$p | Select-Object -Skip 260 -First 80'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path \"src\\main\\java\\net\\machiavelli\\minecolonytax\\WarSystem.java\" | Select-Object -Skip 650 -First 170" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 416ms:
        if (player == null || player.getServer() == null)
            return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set war";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void resetWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null)
            return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set default";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void checkForVictory(WarData war) {
        boolean allAttackersDead = war.getAttackerLives().values().stream().allMatch(lives -> lives <= 0);
        boolean allDefendersDead = war.getDefenderLives().values().stream().allMatch(lives -> lives <= 0);
        boolean allDefenderGuardsDead = war.getRemainingDefenderGuards() <= 0;
        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;

        // Check if we have any participants at all
        boolean hasAttackers = !war.getAttackerLives().isEmpty();
        boolean hasDefenders = !war.getDefenderLives().isEmpty();

        // If no participants, don't end the war
        if (!hasAttackers && !hasDefenders) {
            return;
        }

        // Victory conditions:
        // - Attackers win if all defenders are dead (0 lives) OR if all defender guards
        // are dead
        // - Defenders win if all attackers are dead (0 lives) OR if all attacker guards
        // are dead
        // - Priority: Player deaths take precedence over guard deaths for ending wars
        boolean attackersWin = (hasDefenders && allDefendersDead) || (!hasDefenders && allDefenderGuardsDead);
        boolean defendersWin = (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);

        // Only proceed if there's a clear victory condition
        if (!attackersWin && !defendersWin) {
            return;
        }

        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;

        if (TaxConfig.isDebugLogging()) {
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                    "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: "
                            + defendersWin);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                    "[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: "
                    + war.getRemainingDefenderGuards());
        }

        if (defendersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component victoryMsg = Component.empty()
                    .append(Component.translatable("war.defenders.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.defenders.win.body", defenderColonyName, attackerColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), victoryMsg);
            for (UUID defenderUUID : war.getDefenderLives().keySet()) {
                ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
                if (defender != null) {
                }
            }
            // Record war loss BEFORE economic transfers so immunity check uses pre-war
            // balance
            if (war.getAttackerColony() != null) {
                net.machiavelli.minecolonytax.economy.WarExhaustionManager
                        .recordWarLoss(war.getAttackerColony().getID());
            }
            applyWarEconomyTransfers(war, false);

        } else if (attackersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component conquestMsg = Component.empty()
                    .append(Component.translatable("war.attackers.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.attackers.win.body", attackerColonyName, defenderColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), conquestMsg);
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(attackerUUID);
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsWon(attackerPlayer);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                }
            }
            // Record war loss for defender (they lost when attackers won)
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
            // Apply victory/defeat balance transfers - attackers win, defenders pay
            applyWarEconomyTransfers(war, true);

            if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                if (TaxConfig.isOccupationSystemEnabled()) {
                    // Occupation phase: occupier collects taxes but can't interact, original owner
                    // has time to reclaim
                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                            war.getColony(), war.getAttacker(), war.getAttackerColony());
                    WARSYSTEM_LOGGER.info("Colony {} is now occupied by {} (occupation system active)",
                            war.getColony().getName(), war.getAttacker());
                } else {
                    // Direct transfer (legacy behavior)
                    transferOwnership(war.getColony(), war.getAttacker());
                }
            } else if (TaxConfig.isWarVassalizationEnabled()) {
                // Vassalize the losing colony instead of transferring ownership
                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                int durationHours = TaxConfig.getWarVassalizationDurationHours();
                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                        war.getColony(),
                        war.getAttacker(),
                        tributePercent,
                        durationHours);
                if (vassalized) {
                    WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} for {} hours at {}% tribute",
                            war.getColony().getName(), war.getAttacker(), durationHours, tributePercent);
                }
            }
        }
        endWar(war.getColony());
    }

    /**
     * Applies economic penalties to both sides during a stalemate.
     * 
     * @param war The war data containing information about the conflict
     */
    private static void applyStalematePenalties(WarData war) {
        double stalematePenalty = TaxConfig.getWarStalematePercentage();
        if (stalematePenalty > 0) {
            // Apply stalemate penalties to both sides
            long attackerDeducted = 0;
            long defenderDeducted = 0;

            // Process attacker side
            if (war.getAttackerTeamID() != null) {
                attackerDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getAttackerTeamID(),
                        stalematePenalty);
            } else {
                for (UUID uuid : war.getAttackerLives().keySet()) {
                    attackerDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }

            // Process defender side
            if (war.getDefenderTeamID() != null) {
                defenderDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getDefenderTeamID(),
                        stalematePenalty);
            } else {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path \"src\\main\\java\\net\\machiavelli\\minecolonytax\\data\\WarData.java\" | Select-Object -First 210" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path \"minecolonies\\src\\main\\java\\com\\minecolonies\\core\\colony\\permissions\\ColonyPermissionEventHandler.java\" | Select-Object -Skip 270 -First 70" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path \"src\\main\\java\\net\\machiavelli\\minecolonytax\\WarSystem.java\" | Select-Object -Skip 1235 -First 120" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 738ms:
    /**
     * ExplosionEvent.Detonate handler.
     *
     * @param event ExplosionEvent.Detonate
     */
    @SubscribeEvent
    public void on(final ExplosionEvent.Detonate event)
    {
        if (MineColonies.getConfig().getServer().turnOffExplosionsInColonies.get() == Explosions.DAMAGE_EVERYTHING)
        {
            return;
        }

        final Level eventWorld = event.getLevel();
        final Predicate<BlockPos> getBlocksInColony = pos -> colony.isCoordInColony(eventWorld, pos);
        Predicate<Entity> getEntitiesInColony = entity -> (!(entity instanceof Enemy) || (entity instanceof Llama))
                                                            && colony.isCoordInColony(entity.getCommandSenderWorld(), entity.blockPosition());
        switch(MineColonies.getConfig().getServer().turnOffExplosionsInColonies.get())
        {
            case DAMAGE_NOTHING:
                // if any entity is in colony -> remove from list
                getEntitiesInColony = entity -> colony.isCoordInColony(entity.getCommandSenderWorld(), entity.blockPosition());
                // intentional fall-through to next case.
            case DAMAGE_PLAYERS:
                // if non-mob or llama entity is in colony -> remove from list
                final List<Entity> entitiesToRemove = event.getAffectedEntities().stream()
                                                          .filter(getEntitiesInColony)
                                                          .filter(entity -> !(entity instanceof ServerPlayer))
                                                          .collect(Collectors.toList());
                event.getAffectedEntities().removeAll(entitiesToRemove);
                // intentional fall-through to next case.
            case DAMAGE_ENTITIES:
                // if block is in colony -> remove from list
                final List<BlockPos> blocksToRemove = event.getAffectedBlocks().stream()
                                                        .filter(getBlocksInColony)
                                                        .collect(Collectors.toList());
                event.getAffectedBlocks().removeAll(blocksToRemove);
                break;
            case DAMAGE_EVERYTHING:
            default:
                break;
        }
    }

    /**
     * ExplosionEvent.Start handler.
     *
     * @param event ExplosionEvent.Detonate
     */
    @SubscribeEvent
    public void on(final ExplosionEvent.Start event)
    {
        if (MineColonies.getConfig().getServer().enableColonyProtection.get()
              && MineColonies.getConfig().getServer().turnOffExplosionsInColonies.get() == Explosions.DAMAGE_NOTHING
              && colony.isCoordInColony(event.getLevel(), BlockPos.containing(event.getExplosion().getPosition())))
        {
            cancelEvent(event, null, colony, Action.EXPLODE, BlockPos.containing(event.getExplosion().getPosition()));
        }
    }

    /**
     * PlayerInteractEvent handler.
     * <p>
     * Check, if a player right clicked a block. Deny if: - If the block is in colony - block is AbstractBlockHut - player has not permission
     *
     * @param event PlayerInteractEvent
     */
    @SubscribeEvent
    public void on(final PlayerInteractEvent event)
    {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 748ms:
            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
            return false;
        }
    }

    public static void endWar(IColony colony) {
        // Get war data before removing it from active wars
        WarData warData = ACTIVE_WARS.get(colony.getID());

        // Remove resistance effects from guards in both colonies
        if (warData != null) {
            GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getColony());
            if (warData.getAttackerColony() != null) {
                GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getAttackerColony());
            }

            // Clean up militia system for both colonies
            cleanupWarMilitiaSystem(warData);
        }

        // Disable war actions for both sides
        setWarInteractionPermissions(colony, false);

        // Also disable for attacker colony if it exists
        if (warData != null && warData.getAttackerColony() != null) {
            setWarInteractionPermissions(warData.getAttackerColony(), false);
        }

        // Demote war participants out of the Hostile rank on both colonies.
        // Attackers were assigned hostile on the defender colony; defenders on the attacker colony.
        if (warData != null) {
            if (warData.getAttackerLives() != null) {
                demoteParticipantsFromHostile(colony, warData.getAttackerLives().keySet());
            }
            if (warData.getAttackerColony() != null && warData.getDefenderLives() != null) {
                demoteParticipantsFromHostile(warData.getAttackerColony(), warData.getDefenderLives().keySet());
            }
        }

        // Now remove from active wars
        warData = ACTIVE_WARS.remove(colony.getID());

        // Restore Hostile rank to pre-war state now that the war is no longer active
        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(warData.getAttackerColony());
        }

        // Remove War Exhaustion status and start recovery period
        net.machiavelli.minecolonytax.economy.WarExhaustionManager.removeWarStatus(colony.getID());
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.economy.WarExhaustionManager
                    .removeWarStatus(warData.getAttackerColony().getID());
        }

        // Clear war chest roles for both sides
        net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(colony.getID());
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(warData.getAttackerColony().getID());
        }

        if (warData != null) {
            if (warData.countdownTaskId >= 0) {
                TickScheduler.cancel(warData.countdownTaskId);
                warData.countdownTaskId = -1;
            }
            if (warData.warChestDrainTaskId >= 0) {
                TickScheduler.cancel(warData.warChestDrainTaskId);
                warData.warChestDrainTaskId = -1;
            }
            if (warData.bossEvent != null) {
                warData.bossEvent.removeAllPlayers();
                warData.bossEvent.setVisible(false);
            }
            if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                colony.getPermissions().getPlayers().forEach((uuid, pdata) -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null)
                        p.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                });
                if (colony.getCitizenManager() != null) { // Null check for citizen manager
                    colony.getCitizenManager().getCitizens().forEach(citizen -> {
                        citizen.getEntity().ifPresent(
                                entity -> entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING));
                    });
                }
                warData.getAttackerLives().keySet().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null)
                        resetWarGroup(p);
                });
                warData.getDefenderLives().keySet().forEach(uuid -> {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null)
                        resetWarGroup(p);
                });
                // Handle players in spectator mode (teleport to spawn, restore inventory, set
                // to survival)
                Set<UUID> allParticipants = new HashSet<>();
                if (warData.getAttackerLives() != null)
                    allParticipants.addAll(warData.getAttackerLives().keySet());
                if (warData.getDefenderLives() != null)
                    allParticipants.addAll(warData.getDefenderLives().keySet());

                for (UUID participantUUID : allParticipants) {
                    ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(participantUUID);
                    if (p != null && p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        WarInventoryHandler.restoreInventory(p);

                        BlockPos respawnPos = p.getRespawnPosition();
                        float respawnAngle = p.getRespawnAngle();
                        net.minecraft.server.level.ServerLevel respawnLevel = p.server
                                .getLevel(p.getRespawnDimension());

                        // Try to use personal respawn point first
                        boolean hasValidPersonalSpawn = respawnPos != null && respawnLevel != null
                                && p.isRespawnForced();

                        if (hasValidPersonalSpawn) {
                            p.teleportTo(respawnLevel, respawnPos.getX() + 0.5, respawnPos.getY() + 0.1,
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 770ms:
package net.machiavelli.minecolonytax.data;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.peace.PeaceProposal;
import net.minecraft.server.level.ServerBossEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.machiavelli.minecolonytax.util.TickScheduler;

public class WarData {
    private final UUID warID;
    private final UUID attacker;
    private final UUID defender;
    private final UUID attackerTeamID;
    private final UUID defenderTeamID;
    private final IColony colony;
    public long warStartTime;
    public long joinPhaseEndTime;
    private final Map<UUID, Integer> attackerLives = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> defenderLives = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<Integer> defenderGuardIDs = ConcurrentHashMap.newKeySet();
    private final Set<Integer> attackerGuardIDs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> attackerAllies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defenderAllies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> lastLifeInventoryPreservation = ConcurrentHashMap.newKeySet();

    public long countdownTaskId = -1;
    public long warChestDrainTaskId = -1;
    public ServerBossEvent bossEvent;
    public ServerBossEvent alliesBossEvent;
    private String penaltyReport = "";

    private final Set<UUID> acceptedAllies = new HashSet<>();
    private final Set<UUID> declinedAllies = new HashSet<>();

    public Map<Action, Boolean> originalHostilePerms;
    public Map<Action, Boolean> originalHostilePermsForAttacker;

    public int totalGuards;
    public int remainingGuards;
    private PeaceProposal activeProposal;
    private boolean stalemateTriggered;
    private boolean offlineOutpostWar = false;
    public enum WarStatus { JOINING, INWAR, ERROR }
    private WarStatus status;
    private boolean accepted = false;
    private final IColony attackerColony;
    public final int initialAttackerGuards;
    public int remainingAttackerGuards;
    public final int initialDefenderGuards;
    public int remainingDefenderGuards;
    public int initialAttackerTotalLives;
    public int initialDefenderTotalLives;

    public WarData(UUID attacker, UUID defender, UUID attackerTeamID, UUID defenderTeamID,
                   long joinPhaseStart, ServerBossEvent bossEvent, IColony colony, IColony attackerColony) {
        this.attacker = attacker;
        this.defender = defender;
        this.warID = UUID.randomUUID();
        this.attackerTeamID = attackerTeamID;
        this.defenderTeamID = defenderTeamID;
        this.colony = colony;
        this.attackerColony = attackerColony;
        this.warStartTime = joinPhaseStart;
        this.joinPhaseEndTime = joinPhaseStart + TimeUnit.MINUTES.toMillis(TaxConfig.JOIN_PHASE_DURATION_MINUTES.get());
        this.bossEvent = bossEvent;
        this.status = WarStatus.JOINING;

        this.initialDefenderGuards = colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .mapToInt(c -> 1)
                .sum();
        this.remainingDefenderGuards = initialDefenderGuards;
        this.initialAttackerGuards = attackerColony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .mapToInt(c -> 1)
                .sum();
        this.remainingAttackerGuards = initialAttackerGuards;

        // Track defender and attacker guards in separate sets to prevent ID collisions
        // (MineColonies assigns sequential per-colony citizen IDs, so colony A and B
        // can both have a guard with citizen ID 3)
        initializeGuards(colony, defenderGuardIDs);
        if (attackerColony != null) {
            initializeGuards(attackerColony, attackerGuardIDs);
        }
    }

    /**
     * Restoration constructor - used when loading saved wars from disk after a server restart.
     * Does NOT recalculate guards or generate a new warID; all values come from the save file.
     */
    public WarData(UUID warID, UUID attacker, UUID defender, UUID attackerTeamID, UUID defenderTeamID,
                   long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
                   IColony colony, IColony attackerColony, WarStatus status, boolean accepted,
                   int initialAttackerGuards, int remainingAttackerGuards,
                   int initialDefenderGuards, int remainingDefenderGuards,
                   int initialAttackerTotalLives, int initialDefenderTotalLives,
                   Map<UUID, Integer> attackerLivesData, Map<UUID, Integer> defenderLivesData,
                   Set<Integer> defenderGuardIDsData, Set<Integer> attackerGuardIDsData,
                   Set<UUID> attackerAlliesData, Set<UUID> defenderAlliesData,
                   Set<UUID> spectatorsData, Set<UUID> lastLifeData,
                   String penaltyReport, boolean stalemateTriggered) {
        this.warID = warID;
        this.attacker = attacker;
        this.defender = defender;
        this.attackerTeamID = attackerTeamID;
        this.defenderTeamID = defenderTeamID;
        this.colony = colony;
        this.attackerColony = attackerColony;
        this.warStartTime = warStartTime;
        this.joinPhaseEndTime = joinPhaseEndTime;
        this.bossEvent = bossEvent;
        this.status = status;
        this.accepted = accepted;
        this.initialAttackerGuards = initialAttackerGuards;
        this.remainingAttackerGuards = remainingAttackerGuards;
        this.initialDefenderGuards = initialDefenderGuards;
        this.remainingDefenderGuards = remainingDefenderGuards;
        this.initialAttackerTotalLives = initialAttackerTotalLives;
        this.initialDefenderTotalLives = initialDefenderTotalLives;
        this.penaltyReport = penaltyReport != null ? penaltyReport : "";
        this.stalemateTriggered = stalemateTriggered;
        if (attackerLivesData != null) this.attackerLives.putAll(attackerLivesData);
        if (defenderLivesData != null) this.defenderLives.putAll(defenderLivesData);
        if (defenderGuardIDsData != null) this.defenderGuardIDs.addAll(defenderGuardIDsData);
        if (attackerGuardIDsData != null) this.attackerGuardIDs.addAll(attackerGuardIDsData);
        if (attackerAlliesData != null) this.attackerAllies.addAll(attackerAlliesData);
        if (defenderAlliesData != null) this.defenderAllies.addAll(defenderAlliesData);
        if (spectatorsData != null) this.spectators.addAll(spectatorsData);
        if (lastLifeData != null) this.lastLifeInventoryPreservation.addAll(lastLifeData);
    }

    private void initializeGuards(IColony colony, Set<Integer> targetSet) {
        colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
                .forEach(citizen -> targetSet.add(citizen.getId()));
    }

    public void setStalemateTriggered(boolean stalemateTriggered) { this.stalemateTriggered = stalemateTriggered; }

    public boolean isOfflineOutpostWar() { return offlineOutpostWar; }
    public void setOfflineOutpostWar(boolean offlineOutpostWar) { this.offlineOutpostWar = offlineOutpostWar; }

    public void setActiveProposal(PeaceProposal proposal) {
        this.activeProposal = proposal;
    }
    public void setPenaltyReport(String report) {
        this.penaltyReport = report;
    }
    public String getPenaltyReport() {
        return penaltyReport;
    }
    public UUID getAttacker() { return attacker; }
    public UUID getDefender() { return defender; }
    public WarStatus getStatus() { return status; }
    public void setStatus(WarStatus status) { this.status = status; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public int getRemainingAttackerGuards() { return remainingAttackerGuards; }
    public int getRemainingDefenderGuards() { return remainingDefenderGuards; }
    public IColony getAttackerColony() { return attackerColony; }
    public UUID getWarID() { return warID; }
    public UUID getAttackerTeamID() { return attackerTeamID; }
    public UUID getDefenderTeamID() { return defenderTeamID; }
    public IColony getColony() { return colony; }
    public Map<UUID, Integer> getAttackerLives() { return attackerLives; }
    public Map<UUID, Integer> getDefenderLives() { return defenderLives; }
    public Set<UUID> getSpectators() { return spectators; }
    public Set<Integer> getDefenderGuardIDs() { return defenderGuardIDs; }
    public Set<Integer> getAttackerGuardIDs() { return attackerGuardIDs; }
    public boolean isJoinPhaseActive() { return System.currentTimeMillis() < joinPhaseEndTime; }
    public boolean isWarTimeExpired() { return System.currentTimeMillis() - warStartTime > TimeUnit.MINUTES.toMillis(TaxConfig.WAR_DURATION_MINUTES.get()); }
    public boolean isStalemateTriggered() { return stalemateTriggered; }
    public PeaceProposal getActiveProposal() { return activeProposal; }
    public Set<UUID> getAcceptedAllies() { return acceptedAllies; }
    public Set<UUID> getDeclinedAllies() { return declinedAllies; }
    public long getJoinPhaseEndTime() { return joinPhaseEndTime; }
    public void setJoinPhaseEndTime(long joinPhaseEndTime) { this.joinPhaseEndTime = joinPhaseEndTime; }
    public Set<UUID> getAttackerAllies() { return attackerAllies; }
    public Set<UUID> getDefenderAllies() { return defenderAllies; }
    public Set<UUID> getLastLifeInventoryPreservation() { return lastLifeInventoryPreservation; }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
**STATUS: changes needed before merge.**

- Zeroing defender lives is functional but not clean. `checkForVictory` prioritizes `defendersWin`, so if attackers are also already at `0` lives/guards, this can broadcast experimental victory but end as defender victory.
- Source extraction is wrong for common player-caused TNT/projectile cases: checking `getDirectSourceEntity()` first means a `PrimedTnt`/projectile direct source blocks fallback to the player owner. Prefer indirect `ServerPlayer`, then direct player.
- If a UUID is both attacker and defender, this code allows self-sabotage/self-victory and creates ambiguous broadcasts/stats. Treat defender membership as a hard reject or explicitly resolve side precedence.
- Thread safety is mostly OK if Forge detonate runs on the server thread, but `AttackerHitState` check/increment/update is not atomic despite the outer `ConcurrentHashMap`. Also `WAR_HITS` only clears on this victory path or `clearAll`, so wars ending normally can leak hit state.
- Cooldown gate is exploitable/noisy at edges: wall-clock time can jump, denied hits can spam messages, remaining seconds can display `0s`, and hits are not persisted across restart.

**Fixes**
1. Add an explicit `WarSystem` victory/end method with an outcome reason, or make `triggerVictory` guard against simultaneous defender victory before mutating lives.
2. Change source resolution to prefer `getIndirectSourceEntity()` when it is a `ServerPlayer`; only then accept direct `ServerPlayer`.
3. Reject attacker UUIDs present in defender lives and clear `WAR_HITS` from the normal `endWar` cleanup path.
tokens used
80.256
**STATUS: changes needed before merge.**

- Zeroing defender lives is functional but not clean. `checkForVictory` prioritizes `defendersWin`, so if attackers are also already at `0` lives/guards, this can broadcast experimental victory but end as defender victory.
- Source extraction is wrong for common player-caused TNT/projectile cases: checking `getDirectSourceEntity()` first means a `PrimedTnt`/projectile direct source blocks fallback to the player owner. Prefer indirect `ServerPlayer`, then direct player.
- If a UUID is both attacker and defender, this code allows self-sabotage/self-victory and creates ambiguous broadcasts/stats. Treat defender membership as a hard reject or explicitly resolve side precedence.
- Thread safety is mostly OK if Forge detonate runs on the server thread, but `AttackerHitState` check/increment/update is not atomic despite the outer `ConcurrentHashMap`. Also `WAR_HITS` only clears on this victory path or `clearAll`, so wars ending normally can leak hit state.
- Cooldown gate is exploitable/noisy at edges: wall-clock time can jump, denied hits can spam messages, remaining seconds can display `0s`, and hits are not persisted across restart.

**Fixes**
1. Add an explicit `WarSystem` victory/end method with an outcome reason, or make `triggerVictory` guard against simultaneous defender victory before mutating lives.
2. Change source resolution to prefer `getIndirectSourceEntity()` when it is a `ServerPlayer`; only then accept direct `ServerPlayer`.
3. Reject attacker UUIDs present in defender lives and clear `WAR_HITS` from the normal `endWar` cleanup path.
