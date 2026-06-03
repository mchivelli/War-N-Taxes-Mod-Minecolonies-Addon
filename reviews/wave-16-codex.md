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
session id: 019e5fd0-729b-7001-b6f7-8bff066f56a3
--------
user
Reviewing Step 11 — Plant-the-Banner objective. Custom Block + BlockItem registered via DeferredRegister; PlantTheBannerObjective handles place validation, war-scoped boss bar, hold-timer, defender break, 1 re-plant, and victory trigger. WarSystem hands out the banner at INWAR and clears state at endWar. Review for: (1) is setPlacedBy → static handler hand-off safe (server-side only)?, (2) BlockEvent.BreakEvent handler — does it correctly identify our banner block via instanceof?, (3) replant counter semantics — is it correctly incremented on break only (not on initial place)?, (4) boss bar lifecycle — does removeAllPlayers + setVisible(false) cleanly drop client-side state?, (5) does the item-handout in finalizeWarStart correctly skip if player is offline AND retry when they connect?, (6) is destroyBannerSilently safe to call before placeBlock fully settled?, (7) any race between the 1Hz scheduled task and onWarEnded clearing state? STATUS APPROVE/REWORK, up to 6 bullets, up to 3 fix suggestions. Max 400 words.

<stdin>
## WAVE 16 — Step 11 Plant-the-Banner objective

Files created:
  - siege/SiegeBannerBlock.java — placeable block (hardness 0.5, blast resistance 1.2M, soft glow). setPlacedBy hands off to PlantTheBannerObjective.
  - siege/ModSiegeBlocks.java — DeferredRegister<Block> + DeferredRegister<Item> registering 'minecolonytax:siege_banner'.
  - siege/PlantTheBannerObjective.java — full lifecycle: validate placer is attacker / placement is inside Town Hall via isInBuilding / start war-scoped boss bar / 10-min hold timer / defender can break to cancel / 1 re-plant allowed / on expiry trigger victory via WarSystem.checkForVictory.
  - resources: blockstates/siege_banner.json + models/block/siege_banner.json + models/item/siege_banner.json + loot_tables/blocks/siege_banner.json (uses vanilla red_concrete texture as placeholder).
  - lang/en_us.json: 3 new entries.

TaxConfig: BannerCaptureMinutes config (default 10).

WarSystem hooks: at INWAR transition (if experimental flag on), give each attacker a Siege Banner. At endWar, call PlantTheBannerObjective.onWarEnded to clear per-war state.

Behind EnableExperimentalSiegeObjectives — banner block is always registered, but PlantTheBannerObjective.onBannerPlaced early-returns if the flag is off (banner just behaves as a vanilla solid block in that case).

### NEW FILE: siege/SiegeBannerBlock.java
```java
package net.machiavelli.minecolonytax.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The Siege Banner — placed by an attacker inside the defender's Town Hall to
 * trigger the capture-and-hold victory objective. The block itself is a simple
 * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
 *
 * Indestructible to vanilla explosions to prevent it being blown up by stray TNT,
 * but explicitly breakable by defenders via right-click melee (handled via the
 * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
 * left-click destroys it for defenders who get past attackers.
 */
public class SiegeBannerBlock extends Block {

    public SiegeBannerBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(0.5f, 1200000.0f) // hardness 0.5 (quick to break by hand)
                                            // blast resistance very high (siege banners don't fall to TNT)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)); // soft glow so it's visible at night
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
    }
}
```

### NEW FILE: siege/ModSiegeBlocks.java
```java
package net.machiavelli.minecolonytax.siege;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * DeferredRegister wiring for siege-system blocks and their BlockItems.
 * Currently only registers the Siege Banner used by the experimental
 * Plant-the-Banner victory objective (step 11).
 *
 * Registration is opt-in via {@link MineColonyTax}'s mod-bus subscription —
 * the registries are always created, but the Plant-the-Banner objective is
 * gated behind {@code EnableExperimentalSiegeObjectives} at runtime.
 */
public final class ModSiegeBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MineColonyTax.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MineColonyTax.MOD_ID);

    public static final RegistryObject<Block> SIEGE_BANNER = BLOCKS.register("siege_banner",
            SiegeBannerBlock::new);

    public static final RegistryObject<Item> SIEGE_BANNER_ITEM = ITEMS.register("siege_banner",
            () -> new BlockItem(SIEGE_BANNER.get(),
                    new Item.Properties().stacksTo(1).fireResistant()));

    private ModSiegeBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
```

### NEW FILE: siege/PlantTheBannerObjective.java
```java
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
     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
     * Validates: feature flag on, placer is attacker in an active war, position is
     * inside the defender's Town Hall building. If valid → start capture.
     */
    public static void onBannerPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (level == null || level.isClientSide()) return;
        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
        if (!(placer instanceof ServerPlayer attacker)) return;

        // Find war where this player is attacker.
        WarData war = findWarForAttacker(attacker.getUUID());
        if (war == null) {
            attacker.sendSystemMessage(Component.literal(
                    "You're not an attacker in any active war — the Siege Banner does nothing here.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        // Hard reject if also a defender — same guard pattern as TownHallDemolitionObjective.
        if (war.getDefenderLives().containsKey(attacker.getUUID())) {
            attacker.sendSystemMessage(Component.literal(
                    "You're listed on both sides of this war — banner placement refused.")
                    .withStyle(ChatFormatting.RED));
            destroyBannerSilently(level, pos);
            return;
        }

        IColony defenderColony = war.getColony();
        if (defenderColony == null) return;

        ITownHall townHall = findTownHall(defenderColony);
        if (townHall == null) {
            attacker.sendSystemMessage(Component.literal(
                    "No Town Hall found in the defender colony — banner cannot be planted.")
                    .withStyle(ChatFormatting.RED));
            destroyBannerSilently(level, pos);
            return;
        }

        if (!((IBuilding) townHall).isInBuilding(pos)) {
            attacker.sendSystemMessage(Component.literal(
                    "The Siege Banner must be planted INSIDE the Town Hall building.")
                    .withStyle(ChatFormatting.RED));
            destroyBannerSilently(level, pos);
            return;
        }

        // Replant limit check (codex spec: 1 replant max per war).
        int replantsUsed = REPLANT_COUNT.getOrDefault(war.getWarID(), 0);
        int maxReplants = 1; // could be a config; matches the design doc
        if (replantsUsed > maxReplants) {
            attacker.sendSystemMessage(Component.literal(
                    "The capture path is locked — you have exhausted your re-plants for this war.")
                    .withStyle(ChatFormatting.RED));
            destroyBannerSilently(level, pos);
            return;
        }

        // Already-active capture for this war? Refuse stacking.
        if (ACTIVE_CAPTURES.containsKey(war.getWarID())) {
            attacker.sendSystemMessage(Component.literal(
                    "A Siege Banner is already active for this war.")
                    .withStyle(ChatFormatting.YELLOW));
            destroyBannerSilently(level, pos);
            return;
        }

        startCapture(war, pos, attacker);
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

    /** Defender broke the banner — cancel capture, allow one re-plant if config-allowed. */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getState().getBlock() instanceof SiegeBannerBlock)) return;
        BlockPos pos = event.getPos();

        // Find any active capture at this position.
        for (Map.Entry<UUID, BannerCaptureState> e : ACTIVE_CAPTURES.entrySet()) {
            BannerCaptureState st = e.getValue();
            if (!st.bannerPos.equals(pos)) continue;
            UUID warId = e.getKey();
            WarData war = warById(warId);
            if (war == null) {
                clearCapture(warId);
                return;
            }
            // Cancel capture, bump replant counter so the attacker has limited re-plants.
            int replants = REPLANT_COUNT.getOrDefault(warId, 0) + 1;
            REPLANT_COUNT.put(warId, replants);
            clearCapture(warId);

            Component msg = Component.literal("The Siege Banner has been broken by ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(event.getPlayer().getName().getString())
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(" — capture cancelled.").withStyle(ChatFormatting.GREEN));
            if (event.getPlayer() instanceof ServerPlayer sp && sp.getServer() != null) {
                broadcastToWarParticipants(war, sp.getServer(), msg);
            }
            LOGGER.info("Siege Banner at {} broken for war {} (replants used: {})", pos, warId, replants);
            return;
        }
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
```

### Resource files
```json
// blockstates/siege_banner.json
{
  "variants": {
    "": { "model": "minecolonytax:block/siege_banner" }
  }
}

// models/block/siege_banner.json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "minecraft:block/red_concrete"
  }
}

// models/item/siege_banner.json
{
  "parent": "minecolonytax:block/siege_banner"
}

// loot_tables/blocks/siege_banner.json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecolonytax:siege_banner"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
```
</stdin>
codex
I’ll review this against the existing code paths rather than only the snippet, especially `WarSystem` lifecycle and event wiring. I’ll use the project index if exposed in this session, then fall back to read-only repo inspection.
codex
The GitNexus callable tools are not exposed in this session, so I can’t run `gitnexus_query/context/impact` directly. I’m continuing with read-only inspection and will keep the review scoped to behavior, not edits.
mcp: codex/list_mcp_resources started
mcp: codex/list_mcp_resources (completed)
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"class TickScheduler|scheduleRepeating|cancel\\(|runScheduled|ServerTick|TickEvent\" -S src ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "finalizeWarStart|INWAR|endWar|checkForVictory|PlayerLoggedIn|LoggedIn|SiegeBanner|PlantTheBanner|ModSiegeBlocks|TickScheduler|EnableExperimentalSiegeObjectives|BannerCaptureMinutes" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"BlockEvent\\.BreakEvent|setPlacedBy\\(|removeAllPlayers|ServerBossEvent|BossEvent|isInBuilding|requiresCorrectToolForDrops\" -S src ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 2374ms:
src\main\java\net\machiavelli\minecolonytax\data\WarData.java:7:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\data\WarData.java:44:    public ServerBossEvent bossEvent;
src\main\java\net\machiavelli\minecolonytax\data\WarData.java:45:    public ServerBossEvent alliesBossEvent;
src\main\java\net\machiavelli\minecolonytax\data\WarData.java:71:                   long joinPhaseStart, ServerBossEvent bossEvent, IColony colony, IColony attackerColony) {
src\main\java\net\machiavelli\minecolonytax\data\WarData.java:118:                   long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:23:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:25:import net.minecraft.world.BossEvent;
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:115:        ServerBossEvent bossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:117:                BossEvent.BossBarColor.RED,
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:118:                BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:518:            if (war.alliesBossEvent != null) {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:519:                war.alliesBossEvent.setName(Component.literal(joinText));
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:520:                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:535:            if (war.alliesBossEvent != null) {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:536:                war.alliesBossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:537:                war.alliesBossEvent.setVisible(false);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:568:        war.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:584:        if (war.alliesBossEvent != null) {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:585:            war.alliesBossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:586:            war.alliesBossEvent.setVisible(false);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1390:                warData.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2331:        war.alliesBossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2333:                BossEvent.BossBarColor.YELLOW,
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2334:                BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2335:        war.alliesBossEvent.setProgress(1.0f);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2336:        war.alliesBossEvent.setVisible(true);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3071:        warData.bossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3073:                BossEvent.BossBarColor.RED,
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3074:                BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3597:            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3598:                war.alliesBossEvent.addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3612:            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3613:                war.alliesBossEvent.addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3656:            if (war.alliesBossEvent != null)
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3657:                war.alliesBossEvent.removePlayer(player);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4315:        ServerBossEvent bossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4317:                BossEvent.BossBarColor.RED,
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4318:                BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4445:            warData.alliesBossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4447:                    BossEvent.BossBarColor.YELLOW,
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4448:                    BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4449:            warData.alliesBossEvent.setProgress(1.0f);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4450:            warData.alliesBossEvent.setVisible(true);
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:29:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:32:import net.minecraft.world.BossEvent;
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:907:                raid.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:933:            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:969:            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:970:                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:971:                    : BossEvent.BossBarColor.RED;
src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1354:        public ServerBossEvent bossEvent;
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:30:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:32:import net.minecraft.world.BossEvent;
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:68:        public ServerBossEvent bossEvent;
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:370:                    raidData.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:385:            raidData.bossEvent = new ServerBossEvent(bossBarText, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:566:            raidData.bossEvent.setColor(BossEvent.BossBarColor.RED);
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:568:            raidData.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:570:            raidData.bossEvent.setColor(BossEvent.BossBarColor.GREEN);
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:691:                raidData.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:769:                raidData.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1121:                raidData.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1416:                            raidData.bossEvent.removeAllPlayers();
.\audit\defensive_02_war.md:32:- `alliesBossEvent` â€” reâ€‘created on restore only for `JOINING` status (`WarSystem.java:4187-4192`); a war saved during JOINING with no alliesBossEvent is fine, but an active INWAR war that midâ€‘game had one is silently lost (low impact, cosmetic)
src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:383:                    if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:384:                        war.alliesBossEvent.addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:398:                    if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:399:                        war.alliesBossEvent.addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:33: * the building's footprint, per IBuilding.isInBuilding).
src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:107:            if (((IBuilding) townHall).isInBuilding(pos)) {
src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:31:                .requiresCorrectToolForDrops()
src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:36:    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:37:        super.setPlacedBy(level, pos, state, placer, stack);
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:273:                        if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:274:                            war.alliesBossEvent.addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:287:                if (raidData != null && raidData.getBossEvent() != null) {
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:288:                    raidData.getBossEvent().addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:297:            if (raidData.isActive() && raidData.getBossEvent() != null && raidData.getColony() != null) {
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:299:                    raidData.getBossEvent().addPlayer(player);
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:15:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:17:import net.minecraft.world.BossEvent;
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:67:        final ServerBossEvent bossEvent;
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:71:                           ServerBossEvent bossEvent, long taskId) {
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:123:        if (!((IBuilding) townHall).isInBuilding(pos)) {
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:159:        ServerBossEvent bossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:162:                BossEvent.BossBarColor.YELLOW,
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:163:                BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:201:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:288:                st.bossEvent.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:323:    private static void addWarParticipantsToBossBar(WarData war, MinecraftServer server, ServerBossEvent bossEvent) {
.\wiki\War_Persistence.md:101:- Creates a fresh `ServerBossEvent` with the colony name
.\wiki\War_Persistence.md:221:- **`ServerBossEvent`** â€” Minecraft boss bar instance (recreated with colony name)
.\wiki\War_Persistence.md:223:- **`alliesBossEvent`** â€” Secondary boss bar for join phase (recreated if in JOINING status)
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:199:        if (raidData.getBossEvent() != null) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:204:            raidData.getBossEvent().setName(net.minecraft.network.chat.Component.literal(progressText));
src\main\java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:35:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
src\main\java\net\machiavelli\minecolonytax\event\AbandonedColonyProtectionHandler.java:26:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:430:        if (raidData.getBossEvent() != null) {
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:431:            raidData.getBossEvent().removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:432:            raidData.getBossEvent().setVisible(false);
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1141:            raidData.getBossEvent().setName(name);
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1142:            raidData.getBossEvent().setProgress(progress);
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1146:                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.RED);
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1148:                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.YELLOW);
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1150:                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.WHITE);
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:17:import net.minecraft.world.BossEvent;
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:18:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:708:        private ServerBossEvent bossBar;
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:802:            bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:837:                bossBar.setColor(BossEvent.BossBarColor.YELLOW);
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:843:                bossBar.setColor(BossEvent.BossBarColor.RED);
src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:1047:                bossBar.removeAllPlayers();
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:7:import net.minecraft.server.level.ServerBossEvent;
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:9:import net.minecraft.world.BossEvent;
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:22:    final ServerBossEvent bossEvent;
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:41:    public ActiveRaidData(UUID raider, IColony colony, ServerBossEvent bossEvent) {
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:53:        this.bossEvent = new ServerBossEvent(
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:55:                BossEvent.BossBarColor.RED,
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:56:                BossEvent.BossBarOverlay.PROGRESS);
src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:117:    public ServerBossEvent getBossEvent() {
.\reviews\step-05-codex.md:1295:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-679-                raid.bossEvent.removeAllPlayers();
.\reviews\step-05-codex.md:1495:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1105-        public ServerBossEvent bossEvent;
.\reviews\step-05-codex.md:1809:1105-        public ServerBossEvent bossEvent;
.\reviews\rework-v2-codex.md:1548:import net.minecraft.server.level.ServerBossEvent;
.\reviews\rework-v2-codex.md:1551:import net.minecraft.world.BossEvent;
.\reviews\step-03-codex.md:486:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-661-                raid.bossEvent.removeAllPlayers();
.\reviews\step-03-codex.md:1294:1048-        public ServerBossEvent bossEvent;
.\reviews\step-03-codex.md:1739:661-                raid.bossEvent.removeAllPlayers();
.\reviews\step-03-codex.md:1760:682-            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\reviews\step-03-codex.md:1796:718-            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
.\reviews\step-03-codex.md:1797:719-                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
.\reviews\step-03-codex.md:1798:720-                    : BossEvent.BossBarColor.RED;
.\reviews\step-03-codex.md:1931:1048-        public ServerBossEvent bossEvent;
.\reviews\rework-codex.md:333:         public ServerBossEvent bossEvent;
.\reviews\rework-codex.md:592:         public ServerBossEvent bossEvent;
.\reviews\rework-codex.md:1274:                 warData.bossEvent.removeAllPlayers();
.\reviews\rework-codex.md:2317:+        warData.bossEvent = new ServerBossEvent(
.\reviews\rework-codex.md:2319:+                BossEvent.BossBarColor.RED,
.\reviews\rework-codex.md:2320:+                BossEvent.BossBarOverlay.PROGRESS);
.\reviews\rework-codex.md:2689:+        ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\rework-codex.md:2691:+                BossEvent.BossBarColor.RED,
.\reviews\rework-codex.md:2692:+                BossEvent.BossBarOverlay.PROGRESS);
.\reviews\rework-codex.md:2762:+            warData.alliesBossEvent = new ServerBossEvent(
.\reviews\rework-codex.md:2764:+                    BossEvent.BossBarColor.YELLOW,
.\reviews\rework-codex.md:2765:+                    BossEvent.BossBarOverlay.PROGRESS);
.\reviews\rework-codex.md:2766:+            warData.alliesBossEvent.setProgress(1.0f);
.\reviews\rework-codex.md:2767:+            warData.alliesBossEvent.setVisible(true);
.\reviews\rework-codex.md:2850:+import net.minecraft.server.level.ServerBossEvent;
.\reviews\rework-codex.md:2853:+import net.minecraft.world.BossEvent;
.\reviews\rework-codex.md:3505:+                raid.bossEvent.removeAllPlayers();
.\reviews\rework-codex.md:3526:+            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\reviews\rework-codex.md:3562:+            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
.\reviews\rework-codex.md:3563:+                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
.\reviews\rework-codex.md:3564:+                    : BossEvent.BossBarColor.RED;
.\reviews\rework-codex.md:3931:+        public ServerBossEvent bossEvent;
.\reviews\rework-codex.md:5186: * the building's footprint, per IBuilding.isInBuilding).
.\reviews\rework-codex.md:5260:            if (((IBuilding) townHall).isInBuilding(pos)) {
.\reviews\rework-codex.md:5550:                raid.bossEvent.removeAllPlayers();
.\reviews\rework-codex.md:6303:import net.minecraft.server.level.ServerBossEvent;
.\reviews\rework-codex.md:6306:import net.minecraft.world.BossEvent;
.\reviews\rework-codex.md:6562:        public ServerBossEvent bossEvent;
.\reviews\rework-bundle.md:304:         public ServerBossEvent bossEvent;
.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:54: * the building's footprint, per IBuilding.isInBuilding).
.\reviews\step-11-codex.md:117:            if (((IBuilding) townHall).isInBuilding(pos)) {
.\reviews\step-11-codex.md:480:.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:574:.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:598:.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:622:.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:1009:                warData.bossEvent.removeAllPlayers();
.\reviews\step-11-codex.md:1074:import net.minecraft.server.level.ServerBossEvent;
.\reviews\step-11-codex.md:1101:    public ServerBossEvent bossEvent;
.\reviews\step-11-codex.md:1102:    public ServerBossEvent alliesBossEvent;
.\reviews\step-11-codex.md:1128:                   long joinPhaseStart, ServerBossEvent bossEvent, IColony colony, IColony attackerColony) {
.\reviews\step-11-codex.md:1166:                   long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-bundle.md:38: * the building's footprint, per IBuilding.isInBuilding).
.\reviews\step-11-bundle.md:101:            if (((IBuilding) townHall).isInBuilding(pos)) {
.\reviews\step-08-codex.md:172:         public ServerBossEvent bossEvent;
.\reviews\step-08-bundle.md:156:         public ServerBossEvent bossEvent;
.\reviews\step-07-codex.md:1336:24-import net.minecraft.server.level.ServerBossEvent;
.\reviews\step-07-codex.md:1339:27-import net.minecraft.world.BossEvent;
.\reviews\step-07-codex.md:1990:721-                raid.bossEvent.removeAllPlayers();
.\reviews\step-07-codex.md:2011:742-            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\reviews\step-07-codex.md:2047:778-            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
.\reviews\step-07-codex.md:2048:779-                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
.\reviews\step-07-codex.md:2049:780-                    : BossEvent.BossBarColor.RED;
.\reviews\step-07-codex.md:2233:1147-        public ServerBossEvent bossEvent;
.\reviews\wave-15-bundle.md:490:         public ServerBossEvent bossEvent;
.\reviews\wave-12-v2-codex.md:527:.\audit\defensive_02_war.md:32:- `alliesBossEvent` Ã¢â‚¬â€ reÃ¢â‚¬â€˜created on restore only for `JOINING` status (`WarSystem.java:4187-4192`); a war saved during JOINING with no alliesBossEvent is fine, but an active INWAR war that midÃ¢â‚¬â€˜game had one is silently lost (low impact, cosmetic)
.\reviews\wave-12-v2-codex.md:2370:.\reviews\step-05-codex.md:1295:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-679-                raid.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:2566:.\reviews\step-05-codex.md:1495:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1105-        public ServerBossEvent bossEvent;
.\reviews\wave-12-v2-codex.md:3242:.\reviews\step-03-codex.md:486:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-661-                raid.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:3561:.\reviews\step-03-codex.md:1739:661-                raid.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:3566:.\reviews\step-03-codex.md:1760:682-            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\reviews\wave-12-v2-codex.md:4929:.\reviews\rework-codex.md:1274:                 warData.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:5173:.\reviews\rework-codex.md:2317:+        warData.bossEvent = new ServerBossEvent(
.\reviews\wave-12-v2-codex.md:5261:.\reviews\rework-codex.md:2762:+            warData.alliesBossEvent = new ServerBossEvent(
.\reviews\wave-12-v2-codex.md:5262:.\reviews\rework-codex.md:2766:+            warData.alliesBossEvent.setProgress(1.0f);
.\reviews\wave-12-v2-codex.md:5263:.\reviews\rework-codex.md:2767:+            warData.alliesBossEvent.setVisible(true);
.\reviews\wave-12-v2-codex.md:5415:.\reviews\rework-codex.md:3505:+                raid.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:5420:.\reviews\rework-codex.md:3526:+            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\reviews\wave-12-v2-codex.md:5699:.\reviews\rework-codex.md:5550:                raid.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:6667:.\reviews\step-07-codex.md:1990:721-                raid.bossEvent.removeAllPlayers();
.\reviews\wave-12-v2-codex.md:6672:.\reviews\step-07-codex.md:2011:742-            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\reviews\wave-12-v2-codex.md:6829:.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\wave-16-bundle.md:6:  - siege/PlantTheBannerObjective.java â€” full lifecycle: validate placer is attacker / placement is inside Town Hall via isInBuilding / start war-scoped boss bar / 10-min hold timer / defender can break to cancel / 1 re-plant allowed / on expiry trigger victory via WarSystem.checkForVictory.
.\reviews\wave-16-bundle.md:48:                .requiresCorrectToolForDrops()
.\reviews\wave-16-bundle.md:53:    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
.\reviews\wave-16-bundle.md:54:        super.setPlacedBy(level, pos, state, placer, stack);
.\reviews\wave-16-bundle.md:122:import net.minecraft.server.level.ServerBossEvent;
.\reviews\wave-16-bundle.md:124:import net.minecraft.world.BossEvent;
.\reviews\wave-16-bundle.md:174:        final ServerBossEvent bossEvent;
.\reviews\wave-16-bundle.md:178:                           ServerBossEvent bossEvent, long taskId) {
.\reviews\wave-16-bundle.md:230:        if (!((IBuilding) townHall).isInBuilding(pos)) {
.\reviews\wave-16-bundle.md:266:        ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\wave-16-bundle.md:269:                BossEvent.BossBarColor.YELLOW,
.\reviews\wave-16-bundle.md:270:                BossEvent.BossBarOverlay.PROGRESS);
.\reviews\wave-16-bundle.md:308:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
.\reviews\wave-16-bundle.md:395:                st.bossEvent.removeAllPlayers();
.\reviews\wave-16-bundle.md:430:    private static void addWarParticipantsToBossBar(WarData war, MinecraftServer server, ServerBossEvent bossEvent) {
.\reviews\wave-15-codex.md:506:         public ServerBossEvent bossEvent;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:23:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:25:import net.minecraft.world.BossEvent;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:115:        ServerBossEvent bossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:117:                BossEvent.BossBarColor.RED,
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:118:                BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:518:            if (war.alliesBossEvent != null) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:519:                war.alliesBossEvent.setName(Component.literal(joinText));
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:520:                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:535:            if (war.alliesBossEvent != null) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:536:                war.alliesBossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:537:                war.alliesBossEvent.setVisible(false);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:568:        war.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:584:        if (war.alliesBossEvent != null) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:585:            war.alliesBossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:586:            war.alliesBossEvent.setVisible(false);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1390:                warData.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2331:        war.alliesBossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2333:                BossEvent.BossBarColor.YELLOW,
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2334:                BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2335:        war.alliesBossEvent.setProgress(1.0f);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2336:        war.alliesBossEvent.setVisible(true);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3071:        warData.bossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3073:                BossEvent.BossBarColor.RED,
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3074:                BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3597:            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3598:                war.alliesBossEvent.addPlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3612:            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3613:                war.alliesBossEvent.addPlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3656:            if (war.alliesBossEvent != null)
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3657:                war.alliesBossEvent.removePlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4315:        ServerBossEvent bossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4317:                BossEvent.BossBarColor.RED,
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4318:                BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4445:            warData.alliesBossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4447:                    BossEvent.BossBarColor.YELLOW,
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4448:                    BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4449:            warData.alliesBossEvent.setProgress(1.0f);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4450:            warData.alliesBossEvent.setVisible(true);
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:33: * the building's footprint, per IBuilding.isInBuilding).
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:107:            if (((IBuilding) townHall).isInBuilding(pos)) {
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:31:                .requiresCorrectToolForDrops()
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:36:    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:37:        super.setPlacedBy(level, pos, state, placer, stack);
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:15:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:17:import net.minecraft.world.BossEvent;
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:67:        final ServerBossEvent bossEvent;
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:71:                           ServerBossEvent bossEvent, long taskId) {
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:123:        if (!((IBuilding) townHall).isInBuilding(pos)) {
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:159:        ServerBossEvent bossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:162:                BossEvent.BossBarColor.YELLOW,
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:163:                BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:201:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:288:                st.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:323:    private static void addWarParticipantsToBossBar(WarData war, MinecraftServer server, ServerBossEvent bossEvent) {
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:430:        if (raidData.getBossEvent() != null) {
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:431:            raidData.getBossEvent().removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:432:            raidData.getBossEvent().setVisible(false);
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1141:            raidData.getBossEvent().setName(name);
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1142:            raidData.getBossEvent().setProgress(progress);
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1146:                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.RED);
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1148:                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.YELLOW);
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1150:                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.WHITE);
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:17:import net.minecraft.world.BossEvent;
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:18:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:708:        private ServerBossEvent bossBar;
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:802:            bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:837:                bossBar.setColor(BossEvent.BossBarColor.YELLOW);
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:843:                bossBar.setColor(BossEvent.BossBarColor.RED);
.\src\main\java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:1047:                bossBar.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:7:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:9:import net.minecraft.world.BossEvent;
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:22:    final ServerBossEvent bossEvent;
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:41:    public ActiveRaidData(UUID raider, IColony colony, ServerBossEvent bossEvent) {
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:53:        this.bossEvent = new ServerBossEvent(
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:55:                BossEvent.BossBarColor.RED,
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:56:                BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\raid\ActiveRaidData.java:117:    public ServerBossEvent getBossEvent() {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:29:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:32:import net.minecraft.world.BossEvent;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:907:                raid.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:933:            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:969:            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:970:                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:971:                    : BossEvent.BossBarColor.RED;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1354:        public ServerBossEvent bossEvent;
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:7:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:44:    public ServerBossEvent bossEvent;
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:45:    public ServerBossEvent alliesBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:71:                   long joinPhaseStart, ServerBossEvent bossEvent, IColony colony, IColony attackerColony) {
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:118:                   long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:30:import net.minecraft.server.level.ServerBossEvent;
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:32:import net.minecraft.world.BossEvent;
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:68:        public ServerBossEvent bossEvent;
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:370:                    raidData.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:385:            raidData.bossEvent = new ServerBossEvent(bossBarText, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:566:            raidData.bossEvent.setColor(BossEvent.BossBarColor.RED);
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:568:            raidData.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:570:            raidData.bossEvent.setColor(BossEvent.BossBarColor.GREEN);
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:691:                raidData.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:769:                raidData.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1121:                raidData.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1416:                            raidData.bossEvent.removeAllPlayers();
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:383:                    if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:384:                        war.alliesBossEvent.addPlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:398:                    if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:399:                        war.alliesBossEvent.addPlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:199:        if (raidData.getBossEvent() != null) {
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:204:            raidData.getBossEvent().setName(net.minecraft.network.chat.Component.literal(progressText));
.\src\main\java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:35:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\AbandonedColonyProtectionHandler.java:26:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:273:                        if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:274:                            war.alliesBossEvent.addPlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:287:                if (raidData != null && raidData.getBossEvent() != null) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:288:                    raidData.getBossEvent().addPlayer(player);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:297:            if (raidData.isActive() && raidData.getBossEvent() != null && raidData.getColony() != null) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:299:                    raidData.getBossEvent().addPlayer(player);
.\reviews\waves-13-14-codex.md:216:         ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:826:         ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:828:                 BossEvent.BossBarColor.RED,
.\reviews\waves-13-14-codex.md:955:                    long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
.\reviews\waves-13-14-codex.md:1386:         ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:1996:         ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:1998:                 BossEvent.BossBarColor.RED,
.\reviews\waves-13-14-codex.md:2121:                    long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
.\reviews\waves-13-14-codex.md:2406:518-            if (war.alliesBossEvent != null) {
.\reviews\waves-13-14-codex.md:2407:519-                war.alliesBossEvent.setName(Component.literal(joinText));
.\reviews\waves-13-14-codex.md:2408:520-                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
.\reviews\waves-13-14-codex.md:2423:535-            if (war.alliesBossEvent != null) {
.\reviews\waves-13-14-codex.md:2424:536-                war.alliesBossEvent.removeAllPlayers();
.\reviews\waves-13-14-codex.md:2425:537-                war.alliesBossEvent.setVisible(false);
.\reviews\waves-13-14-codex.md:2456:568-        war.bossEvent.removeAllPlayers();
.\reviews\waves-13-14-codex.md:2556:4419-            warData.alliesBossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:2558:4421-                    BossEvent.BossBarColor.YELLOW,
.\reviews\waves-13-14-codex.md:2559:4422-                    BossEvent.BossBarOverlay.PROGRESS);
.\reviews\waves-13-14-codex.md:2560:4423-            warData.alliesBossEvent.setProgress(1.0f);
.\reviews\waves-13-14-codex.md:2561:4424-            warData.alliesBossEvent.setVisible(true);
.\reviews\waves-13-14-codex.md:3021:568-        war.bossEvent.removeAllPlayers();
.\reviews\waves-13-14-codex.md:3037:584-        if (war.alliesBossEvent != null) {
.\reviews\waves-13-14-codex.md:3038:585-            war.alliesBossEvent.removeAllPlayers();
.\reviews\waves-13-14-codex.md:3039:586-            war.alliesBossEvent.setVisible(false);
.\reviews\waves-13-14-codex.md:3365:3045-        warData.bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:3367:3047-                BossEvent.BossBarColor.RED,
.\reviews\waves-13-14-codex.md:3368:3048-                BossEvent.BossBarOverlay.PROGRESS);
.\reviews\waves-13-14-codex.md:3450:4419-            warData.alliesBossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:3452:4421-                    BossEvent.BossBarColor.YELLOW,
.\reviews\waves-13-14-codex.md:3453:4422-                    BossEvent.BossBarOverlay.PROGRESS);
.\reviews\waves-13-14-codex.md:3454:4423-            warData.alliesBossEvent.setProgress(1.0f);
.\reviews\waves-13-14-codex.md:3455:4424-            warData.alliesBossEvent.setVisible(true);
.\reviews\waves-13-14-codex.md:5647:4419-            warData.alliesBossEvent = new ServerBossEvent(
.\reviews\waves-13-14-codex.md:5649:4421-                    BossEvent.BossBarColor.YELLOW,
.\reviews\waves-13-14-codex.md:5650:4422-                    BossEvent.BossBarOverlay.PROGRESS);
.\reviews\waves-13-14-codex.md:5651:4423-            warData.alliesBossEvent.setProgress(1.0f);
.\reviews\waves-13-14-codex.md:5652:4424-            warData.alliesBossEvent.setVisible(true);
.\reviews\waves-13-14-codex.md:5755:src/main/java/net/machiavelli/minecolonytax\WarSystem.java-568-        war.bossEvent.removeAllPlayers();
.\reviews\waves-13-14-bundle.md:200:         ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-bundle.md:810:         ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\waves-13-14-bundle.md:812:                 BossEvent.BossBarColor.RED,
.\reviews\waves-13-14-bundle.md:939:                    long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
.\reviews\wave-8-11-v2-codex.md:318:         public ServerBossEvent bossEvent;
.\reviews\wave-8-11-v2-codex.md:656:         public ServerBossEvent bossEvent;
.\reviews\wave-8-11-v2-codex.md:712:24-import net.minecraft.server.level.ServerBossEvent;
.\reviews\wave-8-11-v2-codex.md:715:27-import net.minecraft.world.BossEvent;
.\reviews\wave-8-11-v2-codex.md:1338:1273-        public ServerBossEvent bossEvent;
.\reviews\wave-8-11-v2-bundle.md:302:         public ServerBossEvent bossEvent;
.\reviews\wave-8-11-codex.md:771:         public ServerBossEvent bossEvent;
.\reviews\wave-8-11-codex.md:1205:827-                raid.bossEvent.removeAllPlayers();
.\reviews\wave-8-11-codex.md:1415:827-                raid.bossEvent.removeAllPlayers();
.\reviews\wave-8-11-codex.md:1444:886-                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
.\reviews\wave-8-11-codex.md:1445:887-                    : BossEvent.BossBarColor.RED;
.\reviews\wave-8-11-bundle.md:755:         public ServerBossEvent bossEvent;
.\reviews\wave-16-codex.md:14:Reviewing Step 11 â€” Plant-the-Banner objective. Custom Block + BlockItem registered via DeferredRegister; PlantTheBannerObjective handles place validation, war-scoped boss bar, hold-timer, defender break, 1 re-plant, and victory trigger. WarSystem hands out the banner at INWAR and clears state at endWar. Review for: (1) is setPlacedBy â†’ static handler hand-off safe (server-side only)?, (2) BlockEvent.BreakEvent handler â€” does it correctly identify our banner block via instanceof?, (3) replant counter semantics â€” is it correctly incremented on break only (not on initial place)?, (4) boss bar lifecycle â€” does removeAllPlayers + setVisible(false) cleanly drop client-side state?, (5) does the item-handout in finalizeWarStart correctly skip if player is offline AND retry when they connect?, (6) is destroyBannerSilently safe to call before placeBlock fully settled?, (7) any race between the 1Hz scheduled task and onWarEnded clearing state? STATUS APPROVE/REWORK, up to 6 bullets, up to 3 fix suggestions. Max 400 words.
.\reviews\wave-16-codex.md:22:  - siege/PlantTheBannerObjective.java â€” full lifecycle: validate placer is attacker / placement is inside Town Hall via isInBuilding / start war-scoped boss bar / 10-min hold timer / defender can break to cancel / 1 re-plant allowed / on expiry trigger victory via WarSystem.checkForVictory.
.\reviews\wave-16-codex.md:64:                .requiresCorrectToolForDrops()
.\reviews\wave-16-codex.md:69:    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
.\reviews\wave-16-codex.md:70:        super.setPlacedBy(level, pos, state, placer, stack);
.\reviews\wave-16-codex.md:138:import net.minecraft.server.level.ServerBossEvent;
.\reviews\wave-16-codex.md:140:import net.minecraft.world.BossEvent;
.\reviews\wave-16-codex.md:190:        final ServerBossEvent bossEvent;
.\reviews\wave-16-codex.md:194:                           ServerBossEvent bossEvent, long taskId) {
.\reviews\wave-16-codex.md:246:        if (!((IBuilding) townHall).isInBuilding(pos)) {
.\reviews\wave-16-codex.md:282:        ServerBossEvent bossEvent = new ServerBossEvent(
.\reviews\wave-16-codex.md:285:                BossEvent.BossBarColor.YELLOW,
.\reviews\wave-16-codex.md:286:                BossEvent.BossBarOverlay.PROGRESS);
.\reviews\wave-16-codex.md:324:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
.\reviews\wave-16-codex.md:411:                st.bossEvent.removeAllPlayers();
.\reviews\wave-16-codex.md:446:    private static void addWarParticipantsToBossBar(WarData war, MinecraftServer server, ServerBossEvent bossEvent) {
.\reviews\wave-16-codex.md:527:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"BlockEvent\\.BreakEvent|setPlacedBy\\(|removeAllPlayers|ServerBossEvent|BossEvent|isInBuilding|requiresCorrectToolForDrops\" -S src ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\JigsawPlacementHandler.java:56:                    blockState.getBlock().setPlacedBy(world, pos, blockState, null, BlockUtils.getItemStackFromBlockState(blockState));
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\HutPlacementHandler.java:100:                        blockState.getBlock().setPlacedBy(world, pos, blockState, null, BlockUtils.getItemStackFromBlockState(blockState));
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\GeneralBlockPlacementHandler.java:84:                placementState.getBlock().setPlacedBy(world, pos, placementState, null, BlockUtils.getItemStackFromBlockState(placementState));
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\FieldPlacementHandler.java:58:                blockState.getBlock().setPlacedBy(world, pos, blockState, null, BlockUtils.getItemStackFromBlockState(blockState));
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\DoBlockPlacementHandler.java:90:                    placementState.getBlock().setPlacedBy(world, pos, placementState, null, placementState.getBlock().getCloneItemStack(placementState,
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\DoBlockPlacementHandler.java:111:                blockState.getBlock().setPlacedBy(world, pos, placementState, null, placementState.getBlock().getCloneItemStack(placementState,
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\BuilderIgnorePlacementHandler.java:52:                    blockState.getBlock().setPlacedBy(world, pos, blockState, null, BlockUtils.getItemStackFromBlockState(blockState));
.\minecolonies\src\main\java\com\minecolonies\core\placementhandlers\BarracksTowerHandler.java:69:                blockState.getBlock().setPlacedBy(world, pos, blockState, null, BlockUtils.getItemStackFromBlockState(blockState));
.\minecolonies\src\main\java\com\minecolonies\core\network\messages\server\DirectPlaceMessage.java:140:                    state.getBlock().setPlacedBy(world, pos, state, player, stack);
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:209:     * BlockEvent.BreakEvent handler.
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:211:     * @param event BlockEvent.BreakEvent
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:214:    public void on(final BlockEvent.BreakEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\jobs\JobSwineHerder.java:63:          .isInBuilding(getCitizen().getEntity().get().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\colony\jobs\JobShepherd.java:58:          .isInBuilding(getCitizen().getEntity().get().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\colony\jobs\JobRabbitHerder.java:57:          .isInBuilding(getCitizen().getEntity().get().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\colony\jobs\JobCowboy.java:57:          .isInBuilding(getCitizen().getEntity().get().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\colony\jobs\JobChickenHerder.java:71:          .isInBuilding(getCitizen().getEntity().get().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\HordeRaidEvent.java:26:import net.minecraft.server.level.ServerBossEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\HordeRaidEvent.java:29:import net.minecraft.world.BossEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\HordeRaidEvent.java:81:    protected final ServerBossEvent raidBar = new ServerBossEvent(Component.literal("Colony Raid"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\HordeRaidEvent.java:290:        raidBar.removeAllPlayers();
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\AbstractShipRaidEvent.java:29:import net.minecraft.server.level.ServerBossEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\AbstractShipRaidEvent.java:31:import net.minecraft.world.BossEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\AbstractShipRaidEvent.java:96:    protected final ServerBossEvent raidBar = new ServerBossEvent(Component.literal("Colony Raid"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
.\minecolonies\src\main\java\com\minecolonies\core\colony\events\raid\AbstractShipRaidEvent.java:327:        raidBar.removeAllPlayers();
.\minecolonies\src\main\java\com\minecolonies\core\event\QuestObjectiveEventHandler.java:57:     * BlockEvent.BreakEvent handler.
.\minecolonies\src\main\java\com\minecolonies\core\event\QuestObjectiveEventHandler.java:59:     * @param event BlockEvent.BreakEvent
.\minecolonies\src\main\java\com\minecolonies\core\event\QuestObjectiveEventHandler.java:62:    public static void on(final BlockEvent.BreakEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\QuestObjectiveEventHandler.java:117:     * @param event BlockEvent.BreakEvent
.\minecolonies\src\main\java\com\minecolonies\core\event\EventHandler.java:453:            if (building != null && building.getBuildingLevel() >= 1 && building.isInBuilding(pos))
.\minecolonies\src\main\java\com\minecolonies\core\event\EventHandler.java:551:    public static void onBlockBreak(@NotNull final BlockEvent.BreakEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\buildings\AbstractSchematicProvider.java:484:    public boolean isInBuilding(@NotNull final BlockPos positionVec)
.\minecolonies\src\main\java\com\minecolonies\core\colony\buildings\AbstractBuilding.java:299:        if (isInBuilding(player.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\util\Tree.java:711:                if (building.isInBuilding(log))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\util\Tree.java:1062:            if (building.isInBuilding(pos))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\service\EntityAIWorkUndertaker.java:148:            if (building.isInBuilding(worker.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\service\EntityAIWorkCook.java:172:        if (!building.isInBuilding(citizenToServe.peek().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\service\EntityAIWorkCook.java:261:        if (!building.isInBuilding(playerToServe.peek().blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\minimal\EntityAISleep.java:125:        else if (homeBuilding.isInBuilding(citizen.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\crafting\EntityAIWorkAlchemist.java:324:                if (building.isInBuilding(worker.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\minimal\EntityAIEatTask.java:360:        if (!restaurant.isInBuilding(citizen.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\minimal\EntityAIEatTask.java:450:                if (building.isInBuilding(citizen.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\ai\workers\crafting\AbstractEntityAICrafting.java:187:        if (!building.isInBuilding(worker.blockPosition()))
.\minecolonies\src\main\java\com\minecolonies\core\entity\other\cavalry\CavalryHorseEntity.java:897:        if (stable.isInBuilding(this.getOnPos())) {
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockScarecrow.java:172:    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockScarecrow.java:174:        super.setPlacedBy(worldIn, pos, state, placer, stack);
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockPlantationField.java:172:    public void setPlacedBy(@NotNull final Level worldIn, @NotNull final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockPlantationField.java:174:        super.setPlacedBy(worldIn, pos, state, placer, stack);
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockMinecoloniesNamedGrave.java:63:    public void setPlacedBy(final Level worldIn, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockMinecoloniesGrave.java:172:    public void setPlacedBy(final Level worldIn, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockDecorationController.java:180:    public void setPlacedBy(@NotNull final Level worldIn, @NotNull final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockDecorationController.java:182:        super.setPlacedBy(worldIn, pos, state, placer, stack);
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockColonySign.java:91:    public void setPlacedBy(@NotNull final Level worldIn, @NotNull final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockColonySign.java:95:            super.setPlacedBy(worldIn, pos, state, placer, stack);
.\minecolonies\src\main\java\com\minecolonies\core\blocks\BlockColonySign.java:108:        super.setPlacedBy(worldIn, pos, state, placer, stack);
.\minecolonies\src\main\java\com\minecolonies\api\colony\buildings\IBuilding.java:401:    boolean isInBuilding(@NotNull final BlockPos pos);
.\minecolonies\src\main\java\com\minecolonies\api\blocks\decorative\AbstractColonyFlagBanner.java:54:    public void setPlacedBy(final Level worldIn, final @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\api\blocks\decorative\AbstractBlockGate.java:223:    public void setPlacedBy(@NotNull final Level worldIn, @NotNull final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\api\blocks\AbstractColonyBlock.java:249:    public void setPlacedBy(@NotNull final Level worldIn, @NotNull final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack)
.\minecolonies\src\main\java\com\minecolonies\api\blocks\AbstractColonyBlock.java:251:        super.setPlacedBy(worldIn, pos, state, placer, stack);
.\minecolonies\src\main\java\com\minecolonies\api\blocks\AbstractBlockHut.java:120:        setPlacedBy(worldIn, pos, state, placer, stack);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 2388ms:
.\CLAUDE.md:49:TickScheduler.scheduleRepeating(runnable, initialDelayMs, intervalMs);
.\CLAUDE.md:50:TickScheduler.cancel(taskId);
.\audit\adversary_B_concurrency.md:8:- The mod has a real central scheduler â€” `TickScheduler` â€” that uses Forge's `ServerTickEvent` and a `ConcurrentHashMap` for tasks. Documentation in `CLAUDE.md` and `MEMORY.md` is honored across the war/raid/spy systems.
.\audit\adversary_B_concurrency.md:46:- **Threads involved:** server tick (`PvPEventHandler.onServerTick` lines 94, 97â€“137, 139â€“161), packet handlers (Forge's `enqueueWork` is server thread â†’ OK), and `BATTLE_END_SCHEDULER` callbacks. The latter currently hop via `server.execute(...)` but the design does not require it.
.\audit\adversary_B_concurrency.md:47:- **Failure mode:** `onServerTick` (line 94) calls `pendingRequests.entrySet().removeIf(...)` while the `forEach` on `pendingTeamBattles.values()` (line 97) may mutate the map via `battlesToStart.forEach(battle -> battleManager.startTeamBattle(battle))` (line 134), and `startTeamBattle` does `pvpManager.pendingTeamBattles.remove(...)` (lines 118, 125, 137, 144, 158). That mutation happens during the outer iteration. With a plain `HashMap`, this is a textbook **`ConcurrentModificationException`** â€” even on a single thread, because the structural modification happens between iterator creation and the next `next()`.
.\audit\adversary_B_concurrency.md:162:- Only touched in `onServerTick`. Single-threaded.
.\audit\adversary_B_concurrency.md:221:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` â€” OK; central scheduler, ConcurrentHashMap, atomic counters, properly hooks ServerTickEvent.END
.\audit\adversary_B_concurrency.md:269:- `src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java` â€” ServerTickEvent iterates plain HashMaps from PvPManager [CONC-2]
.\audit\adversary_D_edge_cases.md:135:**Scenario:** Player has command rebound to mouse button; misclicks rapidly. Two server command threads (or one ServerTickEvent thread + one command thread) both enter `claimTax` for the same colony.
.\audit\AUDIT_SUMMARY.md:49:- **Good:** TaxManager uses `ServerTickEvent`, war drain uses TickScheduler, persistence is JSON as expected
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:325:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1382:                TickScheduler.cancel(warData.countdownTaskId);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1386:                TickScheduler.cancel(warData.warChestDrainTaskId);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2395:            TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2560:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2567:                TickScheduler.cancel(warData.countdownTaskId);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2574:                TickScheduler.cancel(warData.countdownTaskId);
src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2606:                TickScheduler.cancel(warData.countdownTaskId);
src\main\java\net\machiavelli\minecolonytax\commands\TradeRouteCommand.java:70:    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:3:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:15: * All scheduled tasks run on the main server thread via Forge's ServerTickEvent,
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:26:public class TickScheduler {
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:63:    public static void cancel(long taskId) {
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:84:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:85:        if (event.phase != TickEvent.Phase.END) return;
src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:18:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:69:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:70:        if (event.phase != TickEvent.Phase.END) return;
.\audit\defensive_01_taxation.md:4:Several real defects exist: an exploitable upgrade dupe via concurrent BuyInvestmentPacket replays (purchase is not atomic), a coin-burning bug where FactionCommand withdraws faction funds before depositing into a possibly-full/disabled treasury, a misleading reuse of `TreasuryManager.shutdown()` as a periodic save during war drain, an off-server-thread mutation of the non-concurrent `colonyTaxMap`, and a money-loss path in legacy `ClaimTaxCommand` where tax is debited before payout is verified. Multiple NPE risks exist on `getRank(...).isColonyManager()` chains. Persistence is JSON-based with sane shutdown saves; tax timer correctly uses ServerTickEvent (no `Timer`); upgrade cost overflow becomes possible at higher levels via `int` casts of `Math.pow`.
.\audit\defensive_03_espionage.md:202:`ConcurrentHashMap.values()` is weakly consistent â€” iteration during a concurrent put/remove is safe but may include the modified entry or not. Combined with `tick()` running on main thread and packets being `consumerMainThread`, this is currently safe. Just calling it out because a `ConcurrentHashMap` here is theatre â€” every access happens on the main server thread (`enqueueWork`, `TaxManager.TickEventHandler`), so a plain `HashMap` would do.
src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:10:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:59:    private static void warntax$pauseDuringActiveConflict(TickEvent.LevelTickEvent event, CallbackInfo ci) {
src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:73:                if (defender != null && defender.getWorld() == eventLevel) { ci.cancel(); return; }
src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:75:                if (attacker != null && attacker.getWorld() == eventLevel) { ci.cancel(); return; }
src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:82:                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:92:                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:275:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:287:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:300:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:17:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:62:    private static TickEventHandler tickEventHandler = null;
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:80:        tickEventHandler = new TickEventHandler();
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:97:    public static class TickEventHandler {
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:104:        public void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:105:            if (event.phase == TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:169:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:190:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:170:        final long taskId = TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:285:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:928:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:932:                TickScheduler.cancel(raidData.getCountdownTaskId());
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:940:                TickScheduler.cancel(raidData.getCountdownTaskId());
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:951:                TickScheduler.cancel(raidData.getCountdownTaskId());
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1006:                TickScheduler.cancel(raidData.getCountdownTaskId());
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1019:                TickScheduler.cancel(raidData.getCountdownTaskId());
src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1190:            TickScheduler.cancel(raidData.getCountdownTaskId());
src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:16:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:105:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:106:        if (event.phase == TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:257:    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:258:        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:10:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:52:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:53:        if (event.phase != TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\network\GlowClientHandler.java:8:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\network\GlowClientHandler.java:39:    public static void onClientTick(TickEvent.ClientTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\network\GlowClientHandler.java:40:        if (event.phase != TickEvent.Phase.END) return;
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:27:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:405:     * Runs on both client and server (TickEvent fires on both).
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:409:    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:410:        if (event.phase != TickEvent.Phase.END) return;
src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:13:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:156:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:157:        if (event.phase != TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:292:    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:293:        if (event.phase != TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyPermissionMonitor.java:7:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\event\ColonyPermissionMonitor.java:29:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyPermissionMonitor.java:30:        if (event.phase != TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:17:import net.minecraftforge.event.TickEvent;
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:39:    public static void onServerTick(TickEvent.ServerTickEvent event) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:40:        if (event.phase != TickEvent.Phase.END) {
src\main\java\net\machiavelli\minecolonytax\espionage\SpyManager.java:806:     * Called once per second from TaxManager.TickEventHandler.
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:325:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1382:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1386:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2395:            TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2560:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2567:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2574:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2606:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:3:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:15: * All scheduled tasks run on the main server thread via Forge's ServerTickEvent,
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:26:public class TickScheduler {
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:63:    public static void cancel(long taskId) {
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:84:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:85:        if (event.phase != TickEvent.Phase.END) return;
.\SDM-Economy\common\src\main\java\net\sixik\sdmeconomy\SDMEconomyEvents.java:5:import dev.architectury.event.events.common.TickEvent;
.\SDM-Economy\common\src\main\java\net\sixik\sdmeconomy\SDMEconomyEvents.java:20:        TickEvent.SERVER_POST.register((server) -> {
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:17:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:62:    private static TickEventHandler tickEventHandler = null;
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:80:        tickEventHandler = new TickEventHandler();
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:97:    public static class TickEventHandler {
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:104:        public void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:105:            if (event.phase == TickEvent.Phase.END) {
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:169:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:190:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:170:        final long taskId = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:285:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:928:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:932:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:940:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:951:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1006:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1019:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1190:            TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:16:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:105:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:106:        if (event.phase == TickEvent.Phase.END) {
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:257:    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:258:        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
.\src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:10:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:52:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:53:        if (event.phase != TickEvent.Phase.END) {
.\reviews\step-05-codex.md:2746:minecolonies/src/main/java\com\minecolonies\core\event\FMLEventHandler.java:25:        IColonyManager.getInstance().onServerTick(event);
.\reviews\step-03-codex.md:2365:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-409-    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
.\reviews\step-03-codex.md:2366:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-410-        if (event.phase != TickEvent.Phase.END) return;
.\reviews\step-11-codex.md:1001:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\step-11-codex.md:1005:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\step-10-codex.md:158:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\step-10-codex.md:177:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\step-10-bundle.md:142:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\step-10-bundle.md:161:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\rework-codex.md:879:+        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:1263:-                warData.timerTask.cancel();
.\reviews\rework-codex.md:1266:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:1270:+                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\rework-codex.md:1676:-                        this.cancel();
.\reviews\rework-codex.md:1691:-                        this.cancel();
.\reviews\rework-codex.md:1713:-                        this.cancel();
.\reviews\rework-codex.md:1772:-                            this.cancel();
.\reviews\rework-codex.md:1776:+            TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:1831:-                            this.cancel();
.\reviews\rework-codex.md:1836:-                        this.cancel();
.\reviews\rework-codex.md:1851:-                    this.cancel();
.\reviews\rework-codex.md:1892:-                    this.cancel();
.\reviews\rework-codex.md:1895:+        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:1902:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:1909:-                    this.cancel();
.\reviews\rework-codex.md:1914:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:1932:-                    this.cancel();
.\reviews\rework-codex.md:1947:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:5088:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:5109:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\rework-codex.md:6188:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"class TickScheduler|scheduleRepeating|cancel\\(\" src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\rework-codex.md:6196:src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:26:public class TickScheduler {
.\reviews\rework-codex.md:6197:src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
.\reviews\rework-codex.md:6198:src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:63:    public static void cancel(long taskId) {
.\reviews\rework-codex.md:6199:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:331:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6200:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1315:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6201:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1319:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\rework-codex.md:6202:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2325:            TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6203:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2490:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6204:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2497:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6205:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2504:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6206:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2522:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6207:src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:261:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6208:src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:273:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6209:src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:286:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6210:src/main/java/net/machiavelli/minecolonytax\commands\TradeRouteCommand.java:70:    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
.\reviews\rework-codex.md:6211:src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:26:public class TickScheduler {
.\reviews\rework-codex.md:6212:src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
.\reviews\rework-codex.md:6213:src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:63:    public static void cancel(long taskId) {
.\reviews\rework-codex.md:6214:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:914:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6215:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:918:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6216:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:926:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6217:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:937:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6218:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:992:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6219:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1005:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6220:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1313:            TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6221:src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:165:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6222:src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:186:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\rework-codex.md:6780:import net.minecraftforge.event.TickEvent;
.\reviews\rework-codex.md:6792: * All scheduled tasks run on the main server thread via Forge's ServerTickEvent,
.\reviews\rework-codex.md:6803:public class TickScheduler {
.\reviews\rework-codex.md:6832:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
.\reviews\rework-codex.md:6840:    public static void cancel(long taskId) {
.\reviews\rework-codex.md:6861:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\reviews\rework-codex.md:6862:        if (event.phase != TickEvent.Phase.END) return;
.\reviews\wave-12-bundle.md:73:import net.minecraftforge.event.TickEvent;
.\reviews\wave-12-bundle.md:112:    private static void warntax$pauseDuringActiveWar(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\reviews\wave-12-bundle.md:122:                ci.cancel();
.\reviews\wave-12-bundle.md:127:                ci.cancel();
.\reviews\wave-12-codex.md:89:import net.minecraftforge.event.TickEvent;
.\reviews\wave-12-codex.md:128:    private static void warntax$pauseDuringActiveWar(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\reviews\wave-12-codex.md:138:                ci.cancel();
.\reviews\wave-12-codex.md:143:                ci.cancel();
.\reviews\wave-12-codex.md:11605:import net.minecraftforge.event.TickEvent;
.\reviews\wave-12-codex.md:11644:    private static void warntax$pauseDuringActiveWar(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\reviews\wave-12-codex.md:11654:                ci.cancel();
.\reviews\wave-12-codex.md:11659:                ci.cancel();
.\reviews\wave-12-codex.md:11816:2490-        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-codex.md:11917:46-    private static void warntax$pauseDuringActiveWar(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\reviews\wave-12-codex.md:11927:56-                ci.cancel();
.\reviews\wave-12-codex.md:11932:61-                ci.cancel();
.\reviews\wave-12-codex.md: WARNING: stopped searching binary file after match (found "\0" byte around offset 1185159)
.\reviews\wave-16-bundle.md:277:        final long taskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-16-bundle.md:392:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
.\reviews\wave-12-v2-codex.md:340:.\audit\adversary_B_concurrency.md:8:- The mod has a real central scheduler Ã¢â‚¬â€ `TickScheduler` Ã¢â‚¬â€ that uses Forge's `ServerTickEvent` and a `ConcurrentHashMap` for tasks. Documentation in `CLAUDE.md` and `MEMORY.md` is honored across the war/raid/spy systems.
.\reviews\wave-12-v2-codex.md:429:.\audit\AUDIT_SUMMARY.md:49:- **Good:** TaxManager uses `ServerTickEvent`, war drain uses TickScheduler, persistence is JSON as expected
.\reviews\wave-12-v2-codex.md:506:.\audit\defensive_01_taxation.md:4:Several real defects exist: an exploitable upgrade dupe via concurrent BuyInvestmentPacket replays (purchase is not atomic), a coin-burning bug where FactionCommand withdraws faction funds before depositing into a possibly-full/disabled treasury, a misleading reuse of `TreasuryManager.shutdown()` as a periodic save during war drain, an off-server-thread mutation of the non-concurrent `colonyTaxMap`, and a money-loss path in legacy `ClaimTaxCommand` where tax is debited before payout is verified. Multiple NPE risks exist on `getRank(...).isColonyManager()` chains. Persistence is JSON-based with sane shutdown saves; tax timer correctly uses ServerTickEvent (no `Timer`); upgrade cost overflow becomes possible at higher levels via `int` casts of `Math.pow`.
.\reviews\wave-12-v2-codex.md:4844:.\reviews\rework-codex.md:879:+        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:4920:.\reviews\rework-codex.md:1263:-                warData.timerTask.cancel();
.\reviews\wave-12-v2-codex.md:4923:.\reviews\rework-codex.md:1266:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:4926:.\reviews\rework-codex.md:1270:+                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\wave-12-v2-codex.md:5104:.\reviews\rework-codex.md:1895:+        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5110:.\reviews\rework-codex.md:1902:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5116:.\reviews\rework-codex.md:1914:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5136:.\reviews\rework-codex.md:1947:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5817:.\reviews\rework-codex.md:6199:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:331:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5818:.\reviews\rework-codex.md:6200:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1315:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5819:.\reviews\rework-codex.md:6201:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1319:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\wave-12-v2-codex.md:5820:.\reviews\rework-codex.md:6203:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2490:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5821:.\reviews\rework-codex.md:6204:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2497:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5822:.\reviews\rework-codex.md:6205:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2504:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5823:.\reviews\rework-codex.md:6206:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2522:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5824:.\reviews\rework-codex.md:6214:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:914:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5825:.\reviews\rework-codex.md:6215:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:918:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5826:.\reviews\rework-codex.md:6216:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:926:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5827:.\reviews\rework-codex.md:6217:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:937:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5828:.\reviews\rework-codex.md:6218:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:992:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5829:.\reviews\rework-codex.md:6219:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1005:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5830:.\reviews\rework-codex.md:6220:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1313:            TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:7341:src/main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:59:    private static void warntax$pauseDuringActiveConflict(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\reviews\wave-12-v2-codex.md:7614:import net.minecraftforge.event.TickEvent;
.\reviews\wave-12-v2-codex.md:7663:    private static void warntax$pauseDuringActiveConflict(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\reviews\wave-12-v2-codex.md:7677:                if (defender != null && defender.getWorld() == eventLevel) { ci.cancel(); return; }
.\reviews\wave-12-v2-codex.md:7679:                if (attacker != null && attacker.getWorld() == eventLevel) { ci.cancel(); return; }
.\reviews\wave-12-v2-codex.md:7686:                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
.\reviews\wave-12-v2-codex.md:7696:                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
.\src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:10:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:59:    private static void warntax$pauseDuringActiveConflict(TickEvent.LevelTickEvent event, CallbackInfo ci) {
.\src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:73:                if (defender != null && defender.getWorld() == eventLevel) { ci.cancel(); return; }
.\src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:75:                if (attacker != null && attacker.getWorld() == eventLevel) { ci.cancel(); return; }
.\src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:82:                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
.\src\main\java\net\machiavelli\minecolonytax\mixin\WorldTickHandlerMixin.java:92:                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:275:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:287:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:300:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:18:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:69:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:70:        if (event.phase != TickEvent.Phase.END) return;
.\src\main\java\net\machiavelli\minecolonytax\commands\TradeRouteCommand.java:70:    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:27:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:405:     * Runs on both client and server (TickEvent fires on both).
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:409:    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:410:        if (event.phase != TickEvent.Phase.END) return;
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:13:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:156:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:157:        if (event.phase != TickEvent.Phase.END) {
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:292:    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:293:        if (event.phase != TickEvent.Phase.END) {
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyPermissionMonitor.java:7:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyPermissionMonitor.java:29:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyPermissionMonitor.java:30:        if (event.phase != TickEvent.Phase.END) {
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:17:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:39:    public static void onServerTick(TickEvent.ServerTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:40:        if (event.phase != TickEvent.Phase.END) {
.\src\main\java\net\machiavelli\minecolonytax\espionage\SpyManager.java:806:     * Called once per second from TaxManager.TickEventHandler.
.\src\main\java\net\machiavelli\minecolonytax\network\GlowClientHandler.java:8:import net.minecraftforge.event.TickEvent;
.\src\main\java\net\machiavelli\minecolonytax\network\GlowClientHandler.java:39:    public static void onClientTick(TickEvent.ClientTickEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\network\GlowClientHandler.java:40:        if (event.phase != TickEvent.Phase.END) return;
.\SDMShop\common\src\main\java\net\sixik\sdmshop\client\SDMShopClient.java:7:import dev.architectury.event.events.client.ClientTickEvent;
.\SDMShop\common\src\main\java\net\sixik\sdmshop\client\SDMShopClient.java:70:        ClientTickEvent.CLIENT_PRE.register(SDMShopClient::keyInput);
.\reviews\waves-13-14-codex.md:3229:2369-            TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-16-codex.md:293:        final long taskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-16-codex.md:408:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
.\reviews\wave-16-codex.md:523:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"class TickScheduler|scheduleRepeating|cancel\\(|runScheduled|ServerTick|TickEvent\" -S src ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\minecolonies\src\main\java\com\minecolonies\core\compatibility\journeymap\EventListener.java:29:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\core\compatibility\journeymap\EventListener.java:172:    public void onClientTick(@NotNull final TickEvent.ClientTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\compatibility\journeymap\EventListener.java:174:        if (event.phase != TickEvent.Phase.END) return;
.\minecolonies\src\main\java\com\minecolonies\core\entity\pathfinding\pathresults\PathResult.java:343:    public void cancel()
.\minecolonies\src\main\java\com\minecolonies\core\entity\pathfinding\pathresults\PathResult.java:347:            pathCalculation.cancel(true);
.\minecolonies\src\main\java\com\minecolonies\core\entity\pathfinding\navigation\MinecoloniesAdvancedPathNavigate.java:275:            pathResult.cancel();
.\minecolonies\src\main\java\com\minecolonies\core\entity\pathfinding\navigation\MinecoloniesAdvancedPathNavigate.java:1078:            pathResult.cancel();
.\minecolonies\src\main\java\com\minecolonies\core\entity\pathfinding\navigation\MinecoloniesAdvancedPathNavigate.java:1103:            pathResult.cancel();
.\minecolonies\src\main\java\com\minecolonies\core\event\FMLEventHandler.java:9:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\core\event\FMLEventHandler.java:23:    public static void onServerTick(final TickEvent.ServerTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\FMLEventHandler.java:25:        IColonyManager.getInstance().onServerTick(event);
.\minecolonies\src\main\java\com\minecolonies\core\event\FMLEventHandler.java:29:    public static void onClientTick(final TickEvent.ClientTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\FMLEventHandler.java:60:    public static void onWorldTick(final TickEvent.LevelTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\EventHandler.java:350:    public static void onEnteringChunk(final TickEvent.PlayerTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\EventHandler.java:352:        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide() || event.player.level.getGameTime() % 100 != 0)
.\minecolonies\src\main\java\com\minecolonies\core\event\EventHandler.java:865:    public static void onServerTick(TickEvent.ServerTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\ClientEventHandler.java:58:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\core\event\ClientEventHandler.java:95:    public static void onwWorldTick(@NotNull final TickEvent.LevelTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\ClientEventHandler.java:97:        if (event.level.isClientSide && event.phase == TickEvent.Phase.END && ColonyConstants.rand.nextInt(20) == 0)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyView.java:64:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyView.java:1118:    public void onServerTick(@NotNull final TickEvent.ServerTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyView.java:1131:    public void onWorldTick(@NotNull final TickEvent.LevelTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:41:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:574:    public void onServerTick(@NotNull final TickEvent.ServerTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:576:        if (event.phase == TickEvent.Phase.END)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:580:                c.onServerTick(event);
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:613:    public void onClientTick(@NotNull final TickEvent.ClientTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:615:        if (event.phase == TickEvent.Phase.END)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:633:    public void onWorldTick(final TickEvent.@NotNull LevelTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\ColonyManager.java:635:        if (event.phase == TickEvent.Phase.END)
.\minecolonies\src\main\java\com\minecolonies\core\colony\Colony.java:66:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\Colony.java:1078:    public void onServerTick(@NotNull final TickEvent.ServerTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\colony\Colony.java:1181:     * Any per-world-tick logic should be performed here. NOTE: If the Colony's world isn't loaded, it won't have a world tick. Use onServerTick for logic that should _always_
.\minecolonies\src\main\java\com\minecolonies\core\colony\Colony.java:1184:     * @param event {@link TickEvent.LevelTickEvent}
.\minecolonies\src\main\java\com\minecolonies\core\colony\Colony.java:1187:    public void onWorldTick(@NotNull final TickEvent.LevelTickEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\WindowRequestDetail.java:204:            requestTreeWindowModule.cancel(request);
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\WindowBuildingBrowser.java:111:            futureBuildings.cancel(false);
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\townhall\WindowTownHallDeleteAbandonColony.java:104:    private void cancel()
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\modules\RequestTreeWindowModule.java:313:    private void cancel(@NotNull final Button button)
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\modules\RequestTreeWindowModule.java:316:        cancel(getCachedOpenRequests().get(row).request());
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\modules\RequestTreeWindowModule.java:324:    public final void cancel(@NotNull final IRequest<?> request)
.\minecolonies\src\main\java\com\minecolonies\core\client\gui\modules\building\WindowSelectRequest.java:120:    private void cancel()
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColonyManager.java:14:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColonyManager.java:261:     * @param event {@link net.minecraftforge.event.TickEvent.ServerTickEvent}
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColonyManager.java:263:    void onServerTick(@NotNull TickEvent.ServerTickEvent event);
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColonyManager.java:282:     * @param event {@link TickEvent.ClientTickEvent}.
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColonyManager.java:284:    void onClientTick(@NotNull TickEvent.ClientTickEvent event);
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColonyManager.java:289:    void onWorldTick(@NotNull TickEvent.LevelTickEvent event);
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColony.java:26:import net.minecraftforge.event.TickEvent;
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColony.java:43:    void onServerTick(@NotNull TickEvent.ServerTickEvent event);
.\minecolonies\src\main\java\com\minecolonies\api\colony\IColony.java:48:    void onWorldTick(@NotNull TickEvent.LevelTickEvent event);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 2419ms:
.\CLAUDE.md:37:| Task Scheduling | `TickScheduler.java` | Use this instead of `java.util.Timer`. Runs on main server thread. |
.\CLAUDE.md:46:Use `TickScheduler`:
.\CLAUDE.md:48:TickScheduler.scheduleDelayed(runnable, delayMs);
.\CLAUDE.md:49:TickScheduler.scheduleRepeating(runnable, initialDelayMs, intervalMs);
.\CLAUDE.md:50:TickScheduler.cancel(taskId);
.\CHANGELOG.md:537:- Integrated with `WarSystem.checkForVictory()` to trigger vassalization on attacker victory
.\audit\adversary_B_concurrency.md:8:- The mod has a real central scheduler â€” `TickScheduler` â€” that uses Forge's `ServerTickEvent` and a `ConcurrentHashMap` for tasks. Documentation in `CLAUDE.md` and `MEMORY.md` is honored across the war/raid/spy systems.
.\audit\adversary_B_concurrency.md:25:- **Violates CLAUDE.md rule:** "Never use `java.util.Timer` or `new Thread(...)` for deferred tasks. Use TickScheduler."
.\audit\adversary_B_concurrency.md:31:- **Fix:** delete the executor; route both schedule sites through `TickScheduler.scheduleDelayed(...)` (already used everywhere else).
.\audit\adversary_B_concurrency.md:60:- **Threads involved:** server thread (TickScheduler raid lifecycle), `PlayerLoggedInEvent` (`RaidLoginNotifier.onPlayerLogin` line 52 reads `RaidManager.getActiveRaids().values()` â€” login is server-thread under Forge but the read returns the live map). Also accessed via commands.
.\audit\adversary_B_concurrency.md:93:- **Mitigation:** either keep this and accept that all serialized objects must be effectively immutable while a save is pending, or move all save() calls to `TickScheduler` and serialize inline (simpler).
.\audit\adversary_B_concurrency.md:171:- `ServerStoppingEvent` runs on the server thread before `ServerStoppedEvent`. By that point Forge has paused most tick activity but the `TickScheduler` is still alive (shutdown happens at `MineColonyTax.java:397`, after `WarSystem.saveActiveWars()`). If a scheduled war-drain task fires on the last few ticks before TickScheduler.shutdown(), it can mutate `ACTIVE_WARS` during the save iteration.
.\audit\adversary_B_concurrency.md:175:### [CONC-16] Persistence ordering: TickScheduler shutdown happens AFTER all manager shutdowns that may have queued AsyncSaveExecutor jobs
.\audit\adversary_B_concurrency.md:181:  4. TickScheduler.shutdown (cancels all pending TickScheduler tasks â€” INCLUDING the periodic snapshot scheduled at line 286)
.\audit\adversary_B_concurrency.md:183:- This order is correct: step 5 properly drains every queued write. Step 4 happens after step 3, so any TickScheduler task firing between step 1 and step 4 may queue an async save â€” drained at step 5. **OK.**
.\audit\adversary_B_concurrency.md:193:   - Add a CheckStyle / SpotBugs / ArchUnit rule (or a CI grep) to fail the build if any new `Thread`/`Executors`/`Timer` reference appears outside the allowlist. **`PvPManager.BATTLE_END_SCHEDULER` must be deleted and migrated to TickScheduler before this rule lands.**
.\audit\adversary_B_concurrency.md:208:   - Move all saves to `TickScheduler` and write inline (slower, but no race).
.\audit\adversary_B_concurrency.md:210:4. **`WarSystem.saveActiveWars()` should snapshot `ACTIVE_WARS` before iterating** to avoid an iterator-time race with late-firing TickScheduler war tasks during `ServerStoppingEvent`.
.\audit\adversary_B_concurrency.md:221:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` â€” OK; central scheduler, ConcurrentHashMap, atomic counters, properly hooks ServerTickEvent.END
.\audit\adversary_B_concurrency.md:300:- `src/main/java/net/machiavelli/minecolonytax/capability/PlayerWarDataCapability.java` â€” PlayerLoggedIn/Out + PlayerEvent.SaveToFile; all on server thread
.\audit\adversary_C_crashes.md:48:  - Trigger: `WarSystem.checkForVictory` â†’ attackers win â†’ `OccupationManager.startOccupation(colony, attacker, attackerColony)`. Inside, line 163 calls `colony.getPermissions().getOwner()` â€” this returns null for an abandoned colony (ColonyAbandonmentManager explicitly leaves owner=null in some legacy paths and FCT explicitly checks `if (ownerUUID != null)`). Line 197-201 passes this null straight to the `OccupationData` constructor, which on line 88 calls `originalOwnerUUID.toString()` â†’ NPE.
.\audit\adversary_C_crashes.md:50:  - Why it kills the server: thrown from the war-victory tick path. Wrapped only in the outer `try` in `checkForVictory`? No â€” `checkForVictory` has NO try/catch. The NPE propagates up the TickScheduler â†’ caught by TickScheduler at line 103-105 (logs, does not re-throw), so the *server does not crash* but the war never ends cleanly: ACTIVE_WARS still contains the war, treasury drain keeps running, players stay in spectator mode forever. Recovery requires server restart.
.\audit\adversary_C_crashes.md:64:  - Scenario: any spy mission against a colony whose owner was abandoned at the moment of detection/escape â€” fires from `onSpyDetected`, `onSpyEscaped`, `onMissionSuccess`. Server-side, propagates through TickScheduler â€” caught (logged) but mission state is left dangling.
.\audit\adversary_C_crashes.md:125:- **[CRASH-20]** TickScheduler swallows `Exception` but not `Error` â€”
.\audit\adversary_C_crashes.md:126:  `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:103`
.\audit\adversary_C_crashes.md:160:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java`
.\audit\CODEX_INDEPENDENT.md:33:- [HIGH-9] PvP battle scheduler uses a non-daemon executor that is never shut down â€” `src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java:59`, `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:72`, and `src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java:723` â€” Root cause: `Executors.newScheduledThreadPool(1)` creates non-daemon threads and no shutdown call exists for `BATTLE_END_SCHEDULER`; the project already has `TickScheduler` for server-thread scheduling. Impact: PvP tasks can keep the JVM alive after server stop or execute stale callbacks against a later server lifecycle. Repro: start a PvP battle or defeat a player, then stop the server while delayed restoration/end tasks are pending.
.\audit\CODEX_INDEPENDENT.md:91:- Replace remaining ad hoc executors/schedulers with `TickScheduler` or lifecycle-managed daemon services. If a static executor is necessary, initialize it per server start and shut it down per server stop.
.\audit\CODEX_INDEPENDENT.md:159:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java`
.\audit\AUDIT_SUMMARY.md:49:- **Good:** TaxManager uses `ServerTickEvent`, war drain uses TickScheduler, persistence is JSON as expected
.\audit\AUDIT_SUMMARY.md:57:- **HIGH** `acceptPeace`/`declinePeace` clear `activeProposal` *after* the work block â€” simultaneous accepts can double-apply reparations + double-run `endWar`
.\audit\AUDIT_SUMMARY.md:59:- **HIGH** `endWar` is not idempotent; triple-reads `ACTIVE_WARS`
.\audit\AUDIT_SUMMARY.md:151:- **HIGH** `WarSystem.saveActiveWars` iterates live `ACTIVE_WARS` during `ServerStoppingEvent` before `TickScheduler.shutdown()` â€” late-firing war-drain task can see partially-serialized state. Other managers snapshot first; this one doesn't.
.\audit\AUDIT_SUMMARY.md:152:- **HIGH** `AsyncSaveExecutor` callers like `SpyManager.saveData()` snapshot the top-level map but share `SpyMission` object references with the worker â€” Gson can serialize while server thread mutates fields. Latent torn-write race. Deep-copy or move to TickScheduler.
.\audit\AUDIT_SUMMARY.md:188:- `TickScheduler` itself is well-designed (task add/cancel during iteration safe, shutdown drains correctly).
.\audit\AUDIT_SUMMARY.md:216:Full Codex report at `audit/CODEX_INDEPENDENT.md`. Codex's cross-cutting recommendations align with the Claude swarm's: validate every C2S packet's input range + auth, centralize role checks into null-safe helpers, transactional persistence across tax/treasury/vassal ledgers, atomic file writes, persist or reconcile live operations on restart, replace ad hoc executors with `TickScheduler`, declare optional integrations in `mods.toml`.
.\audit\defensive_01_taxation.md:110:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java`
.\audit\adversary_D_edge_cases.md:7:The single largest class of bugs found is **non-atomic state mutation**: nearly every "give X to player" / "claim Y" / "purchase Z" code path mutates the authoritative server-side map first, then attempts the secondary side-effect. If the secondary effect fails (player offline, integration mod missing, JVM dies, exception), the resource is permanently lost or duplicated. A second large class is **state that lives only in-memory** (TickScheduler tasks, PvP battles, vassal proposals, raid grace periods, sabotage effects in pending costs) â€” a crash or even a clean restart silently destroys it. A third class is **trust placed in `getOrDefault â†’ put` without compare-and-set**, allowing two server-thread paths plus async-save snapshots to interleave.
.\audit\adversary_D_edge_cases.md:115:### [EDGE-8] All TickScheduler tasks are lost on server restart
.\audit\adversary_D_edge_cases.md:116:**File:** `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:26-121`
.\audit\adversary_D_edge_cases.md:118:**Scenario:** Hundreds of TickScheduler tasks exist at any given moment: war countdown warnings, treasury drain loops (one per active war), occupation expiry checker, BesiegeManager tick, DB snapshot loop, abandonment check loops, war timer warnings, PvP countdowns, etc. `TASKS` is a ConcurrentHashMap with no persistence.
.\audit\adversary_D_edge_cases.md:124:- A war in JOINING phase at 4106-4112 is force-transitioned to INWAR with `e.warStartTime = now`, EVEN IF the join phase ended hours ago during downtime. Defender suddenly faces a "fresh" war they thought ended.
.\audit\adversary_D_edge_cases.md:128:**Detection hint:** Add an "end of war during downtime" handler in resume path that calls the normal `endWar(colony)` logic when `now > warStartTime + warDuration`.
.\audit\adversary_D_edge_cases.md:277:The drain loop calls `TreasuryManager.shutdown()` every 5 minutes as a "periodic save". `shutdown()` calls `saveData()` â€” fine in normal operation. But `MineColonyTax.onServerStopping` also calls `TreasuryManager.shutdown()` at line 319 explicitly. If a drain tick fires during shutdown (between TickScheduler.shutdown not yet called and TreasuryManager.shutdown being called), the save runs twice in rapid succession. The second `FileWriter` may truncate the file the first is reading (or vice versa) â€” race on stream.
.\audit\adversary_D_edge_cases.md:317:`status = WarData.WarStatus.INWAR; e.warStartTime = now;` â€” But `joinPhaseEndTime` is not reset, so `scheduleTimerWarnings(warData, remaining)` may compute a `remaining` based on `warStartTime + warDurationMs - now` = full duration. Player who never had a chance to react gets a fresh full-duration war.
.\audit\adversary_D_edge_cases.md:324:### [EDGE-28] FirstColonyTracker bootstrap on TickScheduler 6000-tick delay can race with active war restore
.\audit\adversary_D_edge_cases.md:337:Loop only adds online players to boss bar. If a player joins AFTER war is restored, there's no `PlayerLoggedIn` event handler that adds them to the boss bar. So they see no war indicator despite being a participant. (Maybe one exists elsewhere â€” `WarEventHandler` â€” but I didn't see it in this file. If absent, real bug.)
.\audit\adversary_D_edge_cases.md:349:7. **TickScheduler tasks for end-of-war / end-of-occupation effects must be idempotent and replayable on restart.** Right now they vanish silently.
.\audit\adversary_D_edge_cases.md:374:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` (121 lines)
.\audit\defensive_08_compat_threading.md:3:Scope: `compat/`, `util/TickScheduler`, `util/ColonyActivityTracker`, `util/TranslationUtil`, `CrashLogger`, `raid/ReflectionCache`, `integration/SDMShopIntegration`, `TaxConfig`, `datagen/`, `recipe/`, `event/Datapack/Recipe*`, `event/PatchouliBookHandler`.
.\audit\defensive_08_compat_threading.md:11:The compat & threading layer is mostly disciplined: `ColonyBuildingUtil` is the single point of MineColonies building API access (no direct call sites outside it), `TickScheduler` is correctly registered + drained, `JmImpl` is the only file importing JourneyMap and is guarded by `ModList.isLoaded("journeymap")` in `SpyJourneyMapPlugin`. The big problems are elsewhere:
.\audit\defensive_08_compat_threading.md:14:2. **Non-TickScheduler scheduling** â€” `PvPManager.BATTLE_END_SCHEDULER` (ScheduledExecutorService) is never shut down, and `WarStatsDB.WRITER` + `AsyncSaveExecutor.EXEC` raw threads exist (the latter two are at least intentional async I/O with controlled shutdown).
.\audit\defensive_08_compat_threading.md:56:- CLAUDE.md mandates `TickScheduler` for deferred tasks; `BATTLE_END_SCHEDULER` violates that rule wholesale. Replace `BATTLE_END_SCHEDULER.schedule(r, n, TimeUnit.SECONDS)` with `TickScheduler.scheduleDelayed(r, n * 1000L)`.
.\audit\defensive_08_compat_threading.md:61:- Justified for blocking JDBC I/O (correct decision â€” JDBC must not run on the tick thread). Shutdown is at line 85-91 with 10s grace. The daemon flag is set. This is acceptable, but it's a deliberate exception to the "use TickScheduler" rule and should be called out in CLAUDE.md so future contributors don't replicate the pattern for non-I/O work.
.\audit\defensive_08_compat_threading.md:66:- Same shape as H2 â€” intentional async file I/O, daemon, has `shutdownAndFlush()` at line 88 called from `MineColonyTax:406`. Acceptable, but again is a documented carve-out from the "TickScheduler only" rule.
.\audit\defensive_08_compat_threading.md:199:- `util/TickScheduler.java:79` (wrapped â€” OK)
.\audit\defensive_08_compat_threading.md:207:### L5. `TickScheduler` interval task uses int subtraction without epoch handling
.\audit\defensive_08_compat_threading.md:209:`util/TickScheduler.java:98` â€” `task.ticksRemaining--;` â€” `long` arithmetic, safe. No issue.
.\audit\defensive_08_compat_threading.md:211:### L6. `TickScheduler.cancel` on a non-existent id is silently OK
.\audit\defensive_08_compat_threading.md:213:`util/TickScheduler.java:63-68` â€” `TASKS.remove(taskId)` returns null if not present; method short-circuits. Correct.
.\audit\defensive_08_compat_threading.md:215:### L7. `TickScheduler` cancel-during-iteration is safe
.\audit\defensive_08_compat_threading.md:217:`util/TickScheduler.java:88-115` â€” uses an `Iterator` and calls `it.remove()` for terminal/cancelled tasks; new tasks added during `task.action.run()` go into the `ConcurrentHashMap` and will be picked up next tick (not this tick), which is the desired semantic.
.\audit\defensive_08_compat_threading.md:256:- `compat/EasyFactionsPermissionSync.java` â€” clean, uses TickScheduler-style tick counter
.\audit\defensive_08_compat_threading.md:258:- `util/TickScheduler.java` â€” correct
.\audit\defensive_08_compat_threading.md:290:- `MineColonyTax.java` â€” registers TickScheduler correctly at line 61
.\audit\defensive_02_war.md:32:- `alliesBossEvent` â€” reâ€‘created on restore only for `JOINING` status (`WarSystem.java:4187-4192`); a war saved during JOINING with no alliesBossEvent is fine, but an active INWAR war that midâ€‘game had one is silently lost (low impact, cosmetic)
.\audit\defensive_02_war.md:50:`PeaceProposalManager.acceptPeace` (`PeaceProposalManager.java:122-150`) and `declinePeace` (152-186) read `war.getActiveProposal()` and then perform multiâ€‘step state mutation (`finalizePeaceProposal` â†’ `WarSystem.endWar`) with no synchronization. Although TickScheduler is singleâ€‘threaded, command execution is driven by the Forge command dispatcher on the server thread *and* the network packet handler â€” `accept` is reachable directly from a `RUN_COMMAND` click event in chat. Two players clicking accept (or accept vs decline) in the same tick:
.\audit\defensive_02_war.md:53:- Both will call `finalizePeaceProposal(war, true, player)` and both will call `WarSystem.endWar(war.getColony())` â€” leading to double rank demotion, double history records, and (in REPARATIONS) double money transfer if `payReparationsProportionally` doesnâ€™t earlyâ€‘exit.
.\audit\defensive_02_war.md:63:`updateBossBar` (`WarSystem.java:529`), `startWarCountdown` (`WarSystem.java:2509`), and `handleTimeExpiry` test all rely on `System.currentTimeMillis() - war.warStartTime`. If the server clock is set backwards (NTP adjustment, manual change, container time shift), all live wars instantly appear "expired" or "not yet started" depending on direction. Long restoration of `INWAR` wars also computes `now - warStartTime` against persisted `warStartTime`; if the persisted value is in the future (e.g. someone timeâ€‘traveled the server), `remaining` is huge and the war runs indefinitely. No monotonic time fallback; no sanity check against `warDurationMs * 2`.
.\audit\defensive_02_war.md:66:In the constructor at `WarData.java:69`: `this.warStartTime = joinPhaseStart`. The same field is then reassigned to the real war start at `finalizeWarStart` (`WarSystem.java:610`) and at `startJoinPhase`â€™s scheduled callback (`WarSystem.java:2371`). Until `INWAR`, every check that uses `warStartTime` as "time since war began" (e.g. `isWarTimeExpired()` at `WarData.java:178`) is comparing against joinâ€‘phase start. `isWarTimeExpired()` is currently only called transitively from a tick handler that bails on `JOINING`, but adding any other caller would silently break. The field needs splitting (`joinStartTime` + `warStartTime`) or `isWarTimeExpired` needs to assert `status == INWAR`.
.\audit\defensive_02_war.md:75:### M2. `WarSystem.endWar` tripleâ€‘reads `ACTIVE_WARS.get/remove`
.\audit\defensive_02_war.md:76:At `:1243`, `:1276`, `:1292`, `:1296`, etc. the method calls `ACTIVE_WARS.get(colony.getID())` at line 1243, then mutates state, then does `ACTIVE_WARS.remove(colony.getID())` at line 1292. Other callers of `endWar` on the same colony in the same tick (it happens â€” see H2; also `checkForVictory` â†’ `endWar` and `handleTimeExpiry` â†’ `endWar` can both fire from the same tickâ€™s death event) will read a stale, partiallyâ€‘tornâ€‘down WarData and double the cleanup work. Pattern should be: `WarData warData = ACTIVE_WARS.remove(colony.getID()); if (warData == null) return;` as the very first operation, making the function idempotent.
.\audit\defensive_02_war.md:96:- `player.getPersistentData().ForgeData.<modid>_war_data` â€” written on every increment via `markDirty` (`PlayerWarDataManager.java:80-87`), and reâ€‘read on multiple events (`AttachCapabilities`, `LoadFromFile`, `PlayerLoggedIn` â€” `:48-141`)
.\audit\defensive_02_war.md:98:Three different load paths (`onPlayerLoad`, `loadDataFromPersistent`, `onPlayerLoggedIn`) all deserialize from the same NBT into the same capability. Lastâ€‘writerâ€‘wins between AttachCapabilities and PlayerLoggedIn is undefined and depends on Forge event ordering. Should consolidate to capabilityâ€‘only and only sync NBT once on player save.
.\audit\defensive_02_war.md:103:### M7. `checkForVictory` does not lock `WarData` lives maps
.\audit\defensive_02_war.md:104:`WarSystem.java:668-696` reads attackerLives and defenderLives, then calls `endWar`, but lives maps are `ConcurrentHashMap`s being mutated by handlers like `handleGuardKilled` (`:1781-1794`) on the same tick. The reads at 669-670 use `.stream().allMatch(...)` which is *weakly consistent* â€” two nearâ€‘simultaneous deaths can make `allDefendersDead == true` for both calls of `checkForVictory`, leading to two simultaneous `applyWarEconomyTransfers` (`:735`) doubling the economy hit. Pure CPUâ€‘level race even within the single tick thread is unlikely, but the same call from two separate tick handlers (death + countdown) is. Mark `checkForVictory` idempotent the same way as `endWar` (M2).
.\audit\defensive_02_war.md:144:This happens **before** `OccupationManager.initialize` at line 251. If a war ended in `ENABLE_COLONY_TRANSFER` mode and started an occupation just before crash, the occupation is loaded *after* the war is restored. `OccupationManager.startOccupation` is called from `endWar`/`checkForVictory` only at runtime, not on load â€” but `loadAndResumeActiveWars` itself never reâ€‘applies occupation, so an occupation that was triggered exactly as the server crashed (after `endWar` removed the war but before OccupationManager.saveData fired) is lost. Low because the window is small, but the lifecycle order is unsafe.
.\audit\defensive_02_war.md:166:- `src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java` (for concurrency model)
.\reviews\REWORK_RESULTS.md:16:| 8 | WarSystem.endWar wires WarBlockLedger.restoreWarDamage | VERIFIED | âœ… Fixed |
.\reviews\REWORK_RESULTS.md:27:| 9-redux | defendersWouldWin guard now mirrors checkForVictory exactly | âœ… Fixed |
.\reviews\REWORK_RESULTS.md:93:| 13.3 | `WarSystem.finalizeWarStart` spawns militia for both defender AND attacker colonies, race-guarded with `ACTIVE_WARS.get(colonyId) == war` | VERIFIED | âœ… |
.\reviews\REWORK_RESULTS.md:95:| 13.5 | `WarSystem.endWar` despawns war militia via `MilitiaSpawner.despawnAll` | n/a | âœ… |
.\wiki\War_Persistence.md:49:**When called:** During `ServerStoppingEvent`, before `TickScheduler.shutdown()`.
.\wiki\War_Persistence.md:97:- **INWAR status:** Calculates `warStartTime + warDurationMs`. If the current time exceeds this, the war expired during downtime and is **skipped** (not restored).
.\wiki\War_Persistence.md:98:- **JOINING status:** If the join phase end time has passed, the war is **promoted to INWAR** immediately with `warStartTime` set to now. The join phase is not re-run.
.\wiki\War_Persistence.md:109:**Step 7 â€” State Restoration (INWAR wars):**
.\wiki\War_Persistence.md:120:- Schedules a `TickScheduler.scheduleDelayed()` callback for when the join phase expires, which transitions the war to INWAR and calls `finalizeWarStart()`
.\wiki\War_Persistence.md:141:**`onServerStopping`** â€” Added **before** `TickScheduler.shutdown()`:
.\wiki\War_Persistence.md:145:Placed before TickScheduler shutdown because the war state must be captured while timers are still valid. Also wrapped in try/catch.
.\wiki\War_Persistence.md:166:      "status": "INWAR",
.\wiki\War_Persistence.md:201:| Join phase expired during downtime | War is **promoted to INWAR** immediately on restore |
.\wiki\War_Persistence.md:224:- **Active `TickScheduler` timers** â€” Rescheduled based on remaining time calculations
.\reviews\rework-v2-codex.md:14:Second rework pass â€” focused fix for the two findings from the previous rework review. Verify: (A) the new defendersWouldWin condition in triggerVictory matches WarSystem.checkForVictory's defender-win path exactly, (B) startOccupation's FCT-first classification matches ColonyTierGuard's logic. Respond: STATUS APPROVE/REWORK, VERIFIED/STILL-BROKEN for each, up to 2 new findings if any. Max 250 words.
.\reviews\rework-v2-codex.md:21:  #9 (now full) â€” triggerVictory's defender-would-win guard now mirrors WarSystem.checkForVictory's exact logic ((hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)) instead of requiring both.
.\reviews\rework-v2-codex.md:105:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "checkForVictory|triggerVictory|defendersWouldWin|startOccupation|class ColonyTierGuard|isFirstColony|getFirstColonyOwner" src/main/java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\rework-v2-codex.md:111:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:668:    public static void checkForVictory(WarData war) {
.\reviews\rework-v2-codex.md:113:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1793:        checkForVictory(war);
.\reviews\rework-v2-codex.md:130:src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:183:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\rework-v2-codex.md:131:src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:187:     * checkForVictory would resolve as DEFENDER victory the moment we zero
.\reviews\rework-v2-codex.md:133:src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:196:        // Mirror WarSystem.checkForVictory's defender-win condition exactly. If we
.\reviews\rework-v2-codex.md:136:src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:228:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\rework-v2-codex.md:137:src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:235:            WarSystem.checkForVictory(war);
.\reviews\rework-v2-codex.md:138:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:186:            WarSystem.checkForVictory(war);
.\reviews\rework-v2-codex.md:139:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:200:            WarSystem.checkForVictory(war);
.\reviews\rework-v2-codex.md:140:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:356:            WarSystem.checkForVictory(war);
.\reviews\rework-v2-codex.md:988:A  src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java
.\reviews\rework-v2-codex.md:1066:I found the relevant methods in the working tree. Next Iâ€™m reading the exact branches around `checkForVictory`, `triggerVictory`, `startOccupation`, and `ColonyTierGuard` to compare behavior line by line.
.\reviews\rework-v2-codex.md:1077:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\rework-v2-codex.md:1078:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\rework-v2-codex.md:1082:     * checkForVictory would resolve as DEFENDER victory the moment we zero
.\reviews\rework-v2-codex.md:1091:        // Mirror WarSystem.checkForVictory's defender-win condition exactly. If we
.\reviews\rework-v2-codex.md:1123:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\rework-v2-codex.md:1130:            WarSystem.checkForVictory(war);
.\reviews\rework-v2-codex.md:1140:     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
.\reviews\rework-v2-codex.md:1173:    public static void checkForVictory(WarData war) {
.\reviews\rework-v2-codex.md:2673:A VERIFIED â€” `triggerVictory` now matches `WarSystem.checkForVictory` defender-win semantics: `(hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)`.
.\reviews\rework-v2-codex.md:2685:A VERIFIED â€” `triggerVictory` now matches `WarSystem.checkForVictory` defender-win semantics: `(hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)`.
.\reviews\rework-v2-bundle.md:5:  #9 (now full) â€” triggerVictory's defender-would-win guard now mirrors WarSystem.checkForVictory's exact logic ((hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)) instead of requiring both.
.\reviews\rework-codex.md:23:#8 WarSystem.endWar now calls WarBlockLedger.restoreWarDamage
.\reviews\rework-codex.md:42:  #8 â€” WarSystem.endWar now calls WarBlockLedger.restoreWarDamage(warId, world)
.\reviews\rework-codex.md:50:  - WAR_HITS cleared from WarSystem.endWar via TownHallDemolitionObjective.onWarEnded
.\reviews\rework-codex.md:344:### DIFF: WarSystem.java (endWar wiring only)
.\reviews\rework-codex.md:731:+import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\rework-codex.md:879:+        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:896:+                    endWar(colony);
.\reviews\rework-codex.md:1046:+            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\rework-codex.md:1266:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:1270:+                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\rework-codex.md:1352:             endWar(war.getColony());
.\reviews\rework-codex.md:1364:             endWar(war.getColony());
.\reviews\rework-codex.md:1679:+            TickScheduler.scheduleDelayed(() -> {
.\reviews\rework-codex.md:1690:-                    if (war.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\rework-codex.md:1695:+                if (war.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\rework-codex.md:1776:+            TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:1854:-                war.setStatus(WarData.WarStatus.INWAR);
.\reviews\rework-codex.md:1856:-                finalizeWarStart(war);
.\reviews\rework-codex.md:1863:+        TickScheduler.scheduleDelayed(() -> {
.\reviews\rework-codex.md:1867:+            war.setStatus(WarData.WarStatus.INWAR);
.\reviews\rework-codex.md:1869:+            finalizeWarStart(war);
.\reviews\rework-codex.md:1895:+        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:1902:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:1908:-                if (warData.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\rework-codex.md:1913:+            if (warData.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\rework-codex.md:1914:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:1947:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:2203:+        TickScheduler.scheduleDelayed(() -> {
.\reviews\rework-codex.md:2326:+        warData.setStatus(WarData.WarStatus.INWAR);
.\reviews\rework-codex.md:2675:+        if (status == WarData.WarStatus.INWAR) {
.\reviews\rework-codex.md:2683:+                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
.\reviews\rework-codex.md:2684:+                status = WarData.WarStatus.INWAR;
.\reviews\rework-codex.md:2737:+        if (warData.getStatus() == WarData.WarStatus.INWAR) {
.\reviews\rework-codex.md:2759:+            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
.\reviews\rework-codex.md:2772:+                TickScheduler.scheduleDelayed(() -> {
.\reviews\rework-codex.md:2778:+                    w.setStatus(WarData.WarStatus.INWAR);
.\reviews\rework-codex.md:2780:+                    finalizeWarStart(w);
.\reviews\rework-codex.md:4375:+    /** Periodic check Ã¢â‚¬â€ transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
.\reviews\rework-codex.md:4931:import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\rework-codex.md:4956: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\rework-codex.md:5068:     * Called from WarSystem.endWar() Æ’?" safe to call when no ledger exists.
.\reviews\rework-codex.md:5088:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:5109:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\rework-codex.md:5194: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\rework-codex.md:5335:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\rework-codex.md:5336:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\rework-codex.md:5340:     * checkForVictory would resolve as DEFENDER victory the moment we zero
.\reviews\rework-codex.md:5374:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\rework-codex.md:5381:            WarSystem.checkForVictory(war);
.\reviews\rework-codex.md:5391:     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
.\reviews\rework-codex.md:6059:    public static void endWar(IColony colony) {
.\reviews\rework-codex.md:6188:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"class TickScheduler|scheduleRepeating|cancel\\(\" src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\rework-codex.md:6196:src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:26:public class TickScheduler {
.\reviews\rework-codex.md:6197:src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
.\reviews\rework-codex.md:6198:src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:63:    public static void cancel(long taskId) {
.\reviews\rework-codex.md:6199:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:331:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6200:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1315:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6201:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1319:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\rework-codex.md:6202:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2325:            TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6203:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2490:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6204:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2497:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6205:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2504:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6206:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2522:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\rework-codex.md:6207:src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:261:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6208:src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:273:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6209:src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:286:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6211:src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:26:public class TickScheduler {
.\reviews\rework-codex.md:6212:src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
.\reviews\rework-codex.md:6213:src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:63:    public static void cancel(long taskId) {
.\reviews\rework-codex.md:6214:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:914:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6215:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:918:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6216:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:926:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6217:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:937:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6218:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:992:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6219:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1005:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6220:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1313:            TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\rework-codex.md:6221:src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:165:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\rework-codex.md:6222:src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:186:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\rework-codex.md:6770:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\rework-codex.md:6803:public class TickScheduler {
.\reviews\rework-codex.md:6856:            LOGGER.info("TickScheduler shutdown - cleared {} pending tasks", count);
.\reviews\rework-codex.md:6881:                    LOGGER.error("TickScheduler task {} threw an exception", entry.getKey(), e);
.\reviews\rework-codex.md:7011:        endWar(war.getColony());
.\reviews\rework-codex.md:7579:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "recordWarLoss|applyWarEconomyTransfers|handleVictoryRewards|checkForVictory|attackersWin|defendersWin" src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\rework-codex.md:7584:668:    public static void checkForVictory(WarData war) {
.\reviews\rework-codex.md:7599:1793:        checkForVictory(war);
.\reviews\rework-codex.md:7716:            endWar(war.getColony());
.\reviews\rework-codex.md:7741:            endWar(war.getColony());
.\reviews\rework-codex.md:7892:    public static void checkForVictory(WarData war) {
.\reviews\rework-codex.md:8791:8. VERIFIED â€” `WarSystem.endWar` calls `WarBlockLedger.restoreWarDamage(warId, world)` before active-war removal.
.\reviews\rework-codex.md:8793:9. PARTIAL â€” `triggerVictory` checks attackers exhausted before zeroing defenders, but its condition requires attacker lives AND guards at zero; `WarSystem.checkForVictory` grants defender victory when attacker lives alone are zero, so the race still exists.
.\reviews\rework-codex.md:8798:- HIGH: `TownHallDemolitionObjective.triggerVictory` must mirror `WarSystem.checkForVictory` defender-win logic before mutation; current guard misses â€œall attacker lives dead, guards remainâ€.
.\reviews\rework-codex.md:8802:Close: Most rework deltas are valid and directly address the reviewed bugs, but #9 remains incomplete against the actual victory resolver semantics. I would not approve this bundle until the Town Hall guard checks the same defender-win condition that `checkForVictory` will evaluate after defender lives are zeroed.
.\reviews\rework-codex.md:8821:8. VERIFIED â€” `WarSystem.endWar` calls `WarBlockLedger.restoreWarDamage(warId, world)` before active-war removal.
.\reviews\rework-codex.md:8823:9. PARTIAL â€” `triggerVictory` checks attackers exhausted before zeroing defenders, but its condition requires attacker lives AND guards at zero; `WarSystem.checkForVictory` grants defender victory when attacker lives alone are zero, so the race still exists.
.\reviews\rework-codex.md:8828:- HIGH: `TownHallDemolitionObjective.triggerVictory` must mirror `WarSystem.checkForVictory` defender-win logic before mutation; current guard misses â€œall attacker lives dead, guards remainâ€.
.\reviews\rework-codex.md:8832:Close: Most rework deltas are valid and directly address the reviewed bugs, but #9 remains incomplete against the actual victory resolver semantics. I would not approve this bundle until the Town Hall guard checks the same defender-win condition that `checkForVictory` will evaluate after defender lives are zeroed.
.\reviews\rework-bundle.md:13:  #8 â€” WarSystem.endWar now calls WarBlockLedger.restoreWarDamage(warId, world)
.\reviews\rework-bundle.md:21:  - WAR_HITS cleared from WarSystem.endWar via TownHallDemolitionObjective.onWarEnded
.\reviews\rework-bundle.md:315:### DIFF: WarSystem.java (endWar wiring only)
.\reviews\step-05-codex.md:983:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-03-codex.md:693:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-03-codex.md:2245:44-    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\reviews\step-03-codex.md:2401:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-01-codex.md:532:src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:349:    /** Periodic check Ã¢â‚¬â€ transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
.\reviews\step-01-codex.md:581:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\step-01-codex.md:818:    public static void endWar(IColony colony) {
.\reviews\step-01-codex.md:914:        endWar(war.getColony());
.\reviews\step-01-codex.md:1284:    /** Periodic check Æ’?" transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
.\reviews\SUMMARY.md:18:| 10 â€” Block ledger | NEEDS FIXES | âœ“ | 1 high | Wrong BlockEntity NBT API for 1.20.1; not wired into endWar |
.\reviews\SUMMARY.md:51:   `restoreWarDamage(warId, level)` is not called from `WarSystem.endWar`. Ledger just accumulates indefinitely. Add the call.
.\reviews\SUMMARY.md:54:   Zeroing defender lives can broadcast "experimental victory" but resolve as defender victory if attackers also at 0. Add an explicit `WarSystem.endWarWithReason()` or guard against simultaneous defender win before mutating lives.
.\reviews\SUMMARY.md:86:- **Step 11 â€” `WAR_HITS` only clears on victory or shutdown.** Add cleanup in normal `endWar` path so peace-ended or stalemate wars don't leak hit state.
.\reviews\step-11-codex.md:14:Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:62: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-codex.md:192:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-codex.md:193:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-codex.md:214:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-codex.md:221:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:301:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "checkForVictory|endWar|ACTIVE_WARS|class WarData|defenderLives|attackerLives" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-11-codex.md:303:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ExplosionEvent\\.Detonate|getDirectSourceEntity|getIndirectSourceEntity|TownHallDemolitionObjective|EnableExperimentalSiegeObjectives|TownHallHitCooldown\" -S ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-11-codex.md:305:.\CHANGELOG.md:537:- Integrated with `WarSystem.checkForVictory()` to trigger vassalization on attacker victory
.\reviews\step-11-codex.md:315:.\reviews\step-01-codex.md:581:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â bare return would leak the war in ACTIVE_WARS
.\reviews\step-11-codex.md:381:.\reviews\step-01-codex.md:818:    public static void endWar(IColony colony) {
.\reviews\step-11-codex.md:383:.\reviews\step-01-codex.md:914:        endWar(war.getColony());
.\reviews\step-11-codex.md:393:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:348:                    endWar(colony);
.\reviews\step-11-codex.md:398:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:558:            endWar(war.getColony());
.\reviews\step-11-codex.md:399:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\step-11-codex.md:400:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:668:    public static void checkForVictory(WarData war) {
.\reviews\step-11-codex.md:401:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:791:        endWar(war.getColony());
.\reviews\step-11-codex.md:404:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1241:    public static void endWar(IColony colony) {
.\reviews\step-11-codex.md:407:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1586:            endWar(war.getColony());
.\reviews\step-11-codex.md:408:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1611:            endWar(war.getColony());
.\reviews\step-11-codex.md:409:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1647:            endWar(war.getColony());
.\reviews\step-11-codex.md:410:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1757:        endWar(war.getColony());
.\reviews\step-11-codex.md:411:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1777:        checkForVictory(war);
.\reviews\step-11-codex.md:445:.\reviews\step-05-codex.md:983:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-11-codex.md:449:.\reviews\step-03-codex.md:693:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-11-codex.md:453:.\reviews\step-03-codex.md:2401:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-11-codex.md:463:.\reviews\step-10-bundle.md:3:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet Ã¢â‚¬â€ caller integration is a follow-up. NOT persisted across server restarts yet Ã¢â‚¬â€ ledger lost on crash.
.\reviews\step-11-codex.md:464:.\reviews\step-10-bundle.md:38: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-11-codex.md:466:.\reviews\step-10-bundle.md:124:     * Called from WarSystem.endWar() Ã¢â‚¬â€ safe to call when no ledger exists.
.\reviews\step-11-codex.md:469:.\reviews\step-10-codex.md:14:Reviewing step 10 of 11. New WarBlockLedger captures explosion-damaged blocks per active war. Architecture mirrors harmonised/explosiont's BlockInfo pattern. Review for: (1) interaction with the explosion event - we snapshot but DON'T clear the affected blocks ourselves (explosion proceeds normally and clears them), so restoration just rewrites them back; is this correct? (2) BlockEntity NBT serialize/deserialize - is serializeNBT()/deserializeNBT() correct API for MC 1.20.1?; (3) thread safety of ledger access between event thread and TickScheduler; (4) memory bound if many explosions, (5) wiring gap (not called from endWar yet). Respond: STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-11-codex.md:470:.\reviews\step-10-codex.md:19:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet Ã¢â‚¬â€ caller integration is a follow-up. NOT persisted across server restarts yet Ã¢â‚¬â€ ledger lost on crash.
.\reviews\step-11-codex.md:471:.\reviews\step-10-codex.md:54: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-11-codex.md:473:.\reviews\step-10-codex.md:140:     * Called from WarSystem.endWar() Ã¢â‚¬â€ safe to call when no ledger exists.
.\reviews\step-11-codex.md:476:.\reviews\step-10-codex.md:221:- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.
.\reviews\step-11-codex.md:477:.\reviews\step-10-codex.md:226:3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
.\reviews\step-11-codex.md:478:.\reviews\step-10-codex.md:235:- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.
.\reviews\step-11-codex.md:479:.\reviews\step-10-codex.md:240:3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
.\reviews\step-11-codex.md:480:.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:482:.\reviews\step-11-bundle.md:176:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-codex.md:483:.\reviews\step-11-bundle.md:177:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-codex.md:484:.\reviews\step-11-bundle.md:198:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-codex.md:485:.\reviews\step-11-bundle.md:205:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:489:.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:33: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-11-codex.md:491:.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:119:     * Called from WarSystem.endWar() Ã¢â‚¬â€ safe to call when no ledger exists.
.\reviews\step-11-codex.md:495:.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:171:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-codex.md:496:.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:172:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-codex.md:497:.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:193:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-codex.md:498:.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:200:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:504:.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:186:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:505:.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:200:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:508:.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:356:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:511:.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:445:    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\step-11-codex.md:527:.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:248:                // Set penalty report before endWar so war history logging captures peace outcome
.\reviews\step-11-codex.md:528:.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:250:                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
.\reviews\step-11-codex.md:529:.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:301:                // Set penalty report before endWar so war history logging captures peace outcome
.\reviews\step-11-codex.md:530:.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:304:                WarSystem.endWar(war.getColony());
.\reviews\step-11-codex.md:531:.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:332:                // Set penalty report before endWar
.\reviews\step-11-codex.md:532:.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:335:                WarSystem.endWar(war.getColony());
.\reviews\step-11-codex.md:536:.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:336:     * @param warData        The WarData captured at endWar() time (still valid after remove).
.\reviews\step-11-codex.md:538:.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:573:    /** Determine the DB outcome string from a WarData at the moment endWar() is called. */
.\reviews\step-11-codex.md:558:.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1764:                WarSystem.endWar(colony);
.\reviews\step-11-codex.md:560:.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1783:                        WarSystem.endWar(colony);
.\reviews\step-11-codex.md:565:.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:324:            WarSystem.endWar(colony);
.\reviews\step-11-codex.md:567:.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:347:        WarSystem.endWar(colony);
.\reviews\step-11-codex.md:573:.\reviews\step-11-codex.md:14:Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-11-codex.md:574:.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:576:.\reviews\step-11-codex.md:192:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-codex.md:577:.\reviews\step-11-codex.md:193:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-codex.md:578:.\reviews\step-11-codex.md:214:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-codex.md:579:.\reviews\step-11-codex.md:221:            WarSystem.checkForVictory(war);
.\reviews\step-11-codex.md:581:.\reviews\step-11-codex.md:301:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "checkForVictory|endWar|ACTIVE_WARS|class WarData|defenderLives|attackerLives" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-11-codex.md:592:.\reviews\step-10-bundle.md:3:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet Ã¢â‚¬â€ caller integration is a follow-up. NOT persisted across server restarts yet Ã¢â‚¬â€ ledger lost on crash.
.\reviews\step-11-codex.md:594:.\reviews\step-10-codex.md:19:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet Ã¢â‚¬â€ caller integration is a follow-up. NOT persisted across server restarts yet Ã¢â‚¬â€ ledger lost on crash.
.\reviews\step-11-codex.md:598:.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:599:.\reviews\step-11-bundle.md:46: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-codex.md:609:.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:666:                                .define("EnableExperimentalSiegeObjectives", false);
.\reviews\step-11-codex.md:613:.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:41: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-codex.md:621:.\reviews\step-11-codex.md:14:Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-11-codex.md:622:.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-codex.md:623:.\reviews\step-11-codex.md:62: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-codex.md:633:.\reviews\step-11-codex.md:303:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ExplosionEvent\\.Detonate|getDirectSourceEntity|getIndirectSourceEntity|TownHallDemolitionObjective|EnableExperimentalSiegeObjectives|TownHallHitCooldown\" -S ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-11-codex.md:648:The existing `checkForVictory` path is the key part here: whether this is just a state nudge or a semantic shortcut depends on how it derives winner/loser from lives and guards.
.\reviews\step-11-codex.md:689:    public static void checkForVictory(WarData war) {
.\reviews\step-11-codex.md:812:        endWar(war.getColony());
.\reviews\step-11-codex.md:943:    public static void endWar(IColony colony) {
.\reviews\step-11-codex.md:1001:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\step-11-codex.md:1005:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\step-11-codex.md:1079:import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\step-11-codex.md:1116:    public enum WarStatus { JOINING, INWAR, ERROR }
.\reviews\step-11-codex.md:1268:- Zeroing defender lives is functional but not clean. `checkForVictory` prioritizes `defendersWin`, so if attackers are also already at `0` lives/guards, this can broadcast experimental victory but end as defender victory.
.\reviews\step-11-codex.md:1277:3. Reject attacker UUIDs present in defender lives and clear `WAR_HITS` from the normal `endWar` cleanup path.
.\reviews\step-11-codex.md:1282:- Zeroing defender lives is functional but not clean. `checkForVictory` prioritizes `defendersWin`, so if attackers are also already at `0` lives/guards, this can broadcast experimental victory but end as defender victory.
.\reviews\step-11-codex.md:1291:3. Reject attacker UUIDs present in defender lives and clear `WAR_HITS` from the normal `endWar` cleanup path.
.\reviews\step-11-bundle.md:3:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed â†’ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\step-11-bundle.md:46: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\reviews\step-11-bundle.md:176:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\reviews\step-11-bundle.md:177:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\reviews\step-11-bundle.md:198:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\reviews\step-11-bundle.md:205:            WarSystem.checkForVictory(war);
.\reviews\step-10-codex.md:14:Reviewing step 10 of 11. New WarBlockLedger captures explosion-damaged blocks per active war. Architecture mirrors harmonised/explosiont's BlockInfo pattern. Review for: (1) interaction with the explosion event - we snapshot but DON'T clear the affected blocks ourselves (explosion proceeds normally and clears them), so restoration just rewrites them back; is this correct? (2) BlockEntity NBT serialize/deserialize - is serializeNBT()/deserializeNBT() correct API for MC 1.20.1?; (3) thread safety of ledger access between event thread and TickScheduler; (4) memory bound if many explosions, (5) wiring gap (not called from endWar yet). Respond: STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\step-10-codex.md:19:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet â€” caller integration is a follow-up. NOT persisted across server restarts yet â€” ledger lost on crash.
.\reviews\step-10-codex.md:29:import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\step-10-codex.md:54: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-10-codex.md:140:     * Called from WarSystem.endWar() â€” safe to call when no ledger exists.
.\reviews\step-10-codex.md:147:        // Restore at ~50 blocks/tick using TickScheduler.
.\reviews\step-10-codex.md:158:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\step-10-codex.md:177:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\step-10-codex.md:219:- `ConcurrentHashMap` only protects the map. The `ArrayList` values are not thread-safe, so capture and restore can race if TickScheduler and events interleave, or if async scheduling exists. Use synchronized lists, queues, or main-thread-only enforcement.
.\reviews\step-10-codex.md:221:- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.
.\reviews\step-10-codex.md:226:3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
.\reviews\step-10-codex.md:233:- `ConcurrentHashMap` only protects the map. The `ArrayList` values are not thread-safe, so capture and restore can race if TickScheduler and events interleave, or if async scheduling exists. Use synchronized lists, queues, or main-thread-only enforcement.
.\reviews\step-10-codex.md:235:- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.
.\reviews\step-10-codex.md:240:3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
.\reviews\step-10-bundle.md:3:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet â€” caller integration is a follow-up. NOT persisted across server restarts yet â€” ledger lost on crash.
.\reviews\step-10-bundle.md:13:import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\step-10-bundle.md:38: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\step-10-bundle.md:124:     * Called from WarSystem.endWar() â€” safe to call when no ledger exists.
.\reviews\step-10-bundle.md:131:        // Restore at ~50 blocks/tick using TickScheduler.
.\reviews\step-10-bundle.md:142:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\reviews\step-10-bundle.md:161:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\reviews\wave-12-v2-codex.md:340:.\audit\adversary_B_concurrency.md:8:- The mod has a real central scheduler Ã¢â‚¬â€ `TickScheduler` Ã¢â‚¬â€ that uses Forge's `ServerTickEvent` and a `ConcurrentHashMap` for tasks. Documentation in `CLAUDE.md` and `MEMORY.md` is honored across the war/raid/spy systems.
.\reviews\wave-12-v2-codex.md:347:.\audit\adversary_B_concurrency.md:60:- **Threads involved:** server thread (TickScheduler raid lifecycle), `PlayerLoggedInEvent` (`RaidLoginNotifier.onPlayerLogin` line 52 reads `RaidManager.getActiveRaids().values()` Ã¢â‚¬â€ login is server-thread under Forge but the read returns the live map). Also accessed via commands.
.\reviews\wave-12-v2-codex.md:356:.\audit\adversary_B_concurrency.md:171:- `ServerStoppingEvent` runs on the server thread before `ServerStoppedEvent`. By that point Forge has paused most tick activity but the `TickScheduler` is still alive (shutdown happens at `MineColonyTax.java:397`, after `WarSystem.saveActiveWars()`). If a scheduled war-drain task fires on the last few ticks before TickScheduler.shutdown(), it can mutate `ACTIVE_WARS` during the save iteration.
.\reviews\wave-12-v2-codex.md:358:.\audit\adversary_B_concurrency.md:210:4. **`WarSystem.saveActiveWars()` should snapshot `ACTIVE_WARS` before iterating** to avoid an iterator-time race with late-firing TickScheduler war tasks during `ServerStoppingEvent`.
.\reviews\wave-12-v2-codex.md:375:.\audit\adversary_C_crashes.md:50:  - Why it kills the server: thrown from the war-victory tick path. Wrapped only in the outer `try` in `checkForVictory`? No Ã¢â‚¬â€ `checkForVictory` has NO try/catch. The NPE propagates up the TickScheduler Ã¢â€ â€™ caught by TickScheduler at line 103-105 (logs, does not re-throw), so the *server does not crash* but the war never ends cleanly: ACTIVE_WARS still contains the war, treasury drain keeps running, players stay in spectator mode forever. Recovery requires server restart.
.\reviews\wave-12-v2-codex.md:384:.\audit\adversary_D_edge_cases.md:7:The single largest class of bugs found is **non-atomic state mutation**: nearly every "give X to player" / "claim Y" / "purchase Z" code path mutates the authoritative server-side map first, then attempts the secondary side-effect. If the secondary effect fails (player offline, integration mod missing, JVM dies, exception), the resource is permanently lost or duplicated. A second large class is **state that lives only in-memory** (TickScheduler tasks, PvP battles, vassal proposals, raid grace periods, sabotage effects in pending costs) Ã¢â‚¬â€ a crash or even a clean restart silently destroys it. A third class is **trust placed in `getOrDefault Ã¢â€ â€™ put` without compare-and-set**, allowing two server-thread paths plus async-save snapshots to interleave.
.\reviews\wave-12-v2-codex.md:394:.\audit\adversary_D_edge_cases.md:118:**Scenario:** Hundreds of TickScheduler tasks exist at any given moment: war countdown warnings, treasury drain loops (one per active war), occupation expiry checker, BesiegeManager tick, DB snapshot loop, abandonment check loops, war timer warnings, PvP countdowns, etc. `TASKS` is a ConcurrentHashMap with no persistence.
.\reviews\wave-12-v2-codex.md:397:.\audit\adversary_D_edge_cases.md:124:- A war in JOINING phase at 4106-4112 is force-transitioned to INWAR with `e.warStartTime = now`, EVEN IF the join phase ended hours ago during downtime. Defender suddenly faces a "fresh" war they thought ended.
.\reviews\wave-12-v2-codex.md:399:.\audit\adversary_D_edge_cases.md:128:**Detection hint:** Add an "end of war during downtime" handler in resume path that calls the normal `endWar(colony)` logic when `now > warStartTime + warDuration`.
.\reviews\wave-12-v2-codex.md:414:.\audit\adversary_D_edge_cases.md:317:`status = WarData.WarStatus.INWAR; e.warStartTime = now;` Ã¢â‚¬â€ But `joinPhaseEndTime` is not reset, so `scheduleTimerWarnings(warData, remaining)` may compute a `remaining` based on `warStartTime + warDurationMs - now` = full duration. Player who never had a chance to react gets a fresh full-duration war.
.\reviews\wave-12-v2-codex.md:416:.\audit\adversary_D_edge_cases.md:324:### [EDGE-28] FirstColonyTracker bootstrap on TickScheduler 6000-tick delay can race with active war restore
.\reviews\wave-12-v2-codex.md:419:.\audit\adversary_D_edge_cases.md:337:Loop only adds online players to boss bar. If a player joins AFTER war is restored, there's no `PlayerLoggedIn` event handler that adds them to the boss bar. So they see no war indicator despite being a participant. (Maybe one exists elsewhere Ã¢â‚¬â€ `WarEventHandler` Ã¢â‚¬â€ but I didn't see it in this file. If absent, real bug.)
.\reviews\wave-12-v2-codex.md:420:.\audit\adversary_D_edge_cases.md:349:7. **TickScheduler tasks for end-of-war / end-of-occupation effects must be idempotent and replayable on restart.** Right now they vanish silently.
.\reviews\wave-12-v2-codex.md:429:.\audit\AUDIT_SUMMARY.md:49:- **Good:** TaxManager uses `ServerTickEvent`, war drain uses TickScheduler, persistence is JSON as expected
.\reviews\wave-12-v2-codex.md:434:.\audit\AUDIT_SUMMARY.md:57:- **HIGH** `acceptPeace`/`declinePeace` clear `activeProposal` *after* the work block Ã¢â‚¬â€ simultaneous accepts can double-apply reparations + double-run `endWar`
.\reviews\wave-12-v2-codex.md:445:.\audit\AUDIT_SUMMARY.md:151:- **HIGH** `WarSystem.saveActiveWars` iterates live `ACTIVE_WARS` during `ServerStoppingEvent` before `TickScheduler.shutdown()` Ã¢â‚¬â€ late-firing war-drain task can see partially-serialized state. Other managers snapshot first; this one doesn't.
.\reviews\wave-12-v2-codex.md:457:.\audit\AUDIT_SUMMARY.md:216:Full Codex report at `audit/CODEX_INDEPENDENT.md`. Codex's cross-cutting recommendations align with the Claude swarm's: validate every C2S packet's input range + auth, centralize role checks into null-safe helpers, transactional persistence across tax/treasury/vassal ledgers, atomic file writes, persist or reconcile live operations on restart, replace ad hoc executors with `TickScheduler`, declare optional integrations in `mods.toml`.
.\reviews\wave-12-v2-codex.md:527:.\audit\defensive_02_war.md:32:- `alliesBossEvent` Ã¢â‚¬â€ reÃ¢â‚¬â€˜created on restore only for `JOINING` status (`WarSystem.java:4187-4192`); a war saved during JOINING with no alliesBossEvent is fine, but an active INWAR war that midÃ¢â‚¬â€˜game had one is silently lost (low impact, cosmetic)
.\reviews\wave-12-v2-codex.md:534:.\audit\defensive_02_war.md:50:`PeaceProposalManager.acceptPeace` (`PeaceProposalManager.java:122-150`) and `declinePeace` (152-186) read `war.getActiveProposal()` and then perform multiÃ¢â‚¬â€˜step state mutation (`finalizePeaceProposal` Ã¢â€ â€™ `WarSystem.endWar`) with no synchronization. Although TickScheduler is singleÃ¢â‚¬â€˜threaded, command execution is driven by the Forge command dispatcher on the server thread *and* the network packet handler Ã¢â‚¬â€ `accept` is reachable directly from a `RUN_COMMAND` click event in chat. Two players clicking accept (or accept vs decline) in the same tick:
.\reviews\wave-12-v2-codex.md:535:.\audit\defensive_02_war.md:53:- Both will call `finalizePeaceProposal(war, true, player)` and both will call `WarSystem.endWar(war.getColony())` Ã¢â‚¬â€ leading to double rank demotion, double history records, and (in REPARATIONS) double money transfer if `payReparationsProportionally` doesnÃ¢â‚¬â„¢t earlyÃ¢â‚¬â€˜exit.
.\reviews\wave-12-v2-codex.md:539:.\audit\defensive_02_war.md:63:`updateBossBar` (`WarSystem.java:529`), `startWarCountdown` (`WarSystem.java:2509`), and `handleTimeExpiry` test all rely on `System.currentTimeMillis() - war.warStartTime`. If the server clock is set backwards (NTP adjustment, manual change, container time shift), all live wars instantly appear "expired" or "not yet started" depending on direction. Long restoration of `INWAR` wars also computes `now - warStartTime` against persisted `warStartTime`; if the persisted value is in the future (e.g. someone timeÃ¢â‚¬â€˜traveled the server), `remaining` is huge and the war runs indefinitely. No monotonic time fallback; no sanity check against `warDurationMs * 2`.
.\reviews\wave-12-v2-codex.md:541:.\audit\defensive_02_war.md:66:In the constructor at `WarData.java:69`: `this.warStartTime = joinPhaseStart`. The same field is then reassigned to the real war start at `finalizeWarStart` (`WarSystem.java:610`) and at `startJoinPhase`Ã¢â‚¬â„¢s scheduled callback (`WarSystem.java:2371`). Until `INWAR`, every check that uses `warStartTime` as "time since war began" (e.g. `isWarTimeExpired()` at `WarData.java:178`) is comparing against joinÃ¢â‚¬â€˜phase start. `isWarTimeExpired()` is currently only called transitively from a tick handler that bails on `JOINING`, but adding any other caller would silently break. The field needs splitting (`joinStartTime` + `warStartTime`) or `isWarTimeExpired` needs to assert `status == INWAR`.
.\reviews\wave-12-v2-codex.md:543:.\audit\defensive_02_war.md:76:At `:1243`, `:1276`, `:1292`, `:1296`, etc. the method calls `ACTIVE_WARS.get(colony.getID())` at line 1243, then mutates state, then does `ACTIVE_WARS.remove(colony.getID())` at line 1292. Other callers of `endWar` on the same colony in the same tick (it happens Ã¢â‚¬â€ see H2; also `checkForVictory` Ã¢â€ â€™ `endWar` and `handleTimeExpiry` Ã¢â€ â€™ `endWar` can both fire from the same tickÃ¢â‚¬â„¢s death event) will read a stale, partiallyÃ¢â‚¬â€˜tornÃ¢â‚¬â€˜down WarData and double the cleanup work. Pattern should be: `WarData warData = ACTIVE_WARS.remove(colony.getID()); if (warData == null) return;` as the very first operation, making the function idempotent.
.\reviews\wave-12-v2-codex.md:547:.\audit\defensive_02_war.md:96:- `player.getPersistentData().ForgeData.<modid>_war_data` Ã¢â‚¬â€ written on every increment via `markDirty` (`PlayerWarDataManager.java:80-87`), and reÃ¢â‚¬â€˜read on multiple events (`AttachCapabilities`, `LoadFromFile`, `PlayerLoggedIn` Ã¢â‚¬â€ `:48-141`)
.\reviews\wave-12-v2-codex.md:555:.\audit\defensive_02_war.md:144:This happens **before** `OccupationManager.initialize` at line 251. If a war ended in `ENABLE_COLONY_TRANSFER` mode and started an occupation just before crash, the occupation is loaded *after* the war is restored. `OccupationManager.startOccupation` is called from `endWar`/`checkForVictory` only at runtime, not on load Ã¢â‚¬â€ but `loadAndResumeActiveWars` itself never reÃ¢â‚¬â€˜applies occupation, so an occupation that was triggered exactly as the server crashed (after `endWar` removed the war but before OccupationManager.saveData fired) is lost. Low because the window is small, but the lifecycle order is unsafe.
.\reviews\wave-12-v2-codex.md:569:.\audit\defensive_08_compat_threading.md:3:Scope: `compat/`, `util/TickScheduler`, `util/ColonyActivityTracker`, `util/TranslationUtil`, `CrashLogger`, `raid/ReflectionCache`, `integration/SDMShopIntegration`, `TaxConfig`, `datagen/`, `recipe/`, `event/Datapack/Recipe*`, `event/PatchouliBookHandler`.
.\reviews\wave-12-v2-codex.md:843:.\wiki\War_Persistence.md:97:- **INWAR status:** Calculates `warStartTime + warDurationMs`. If the current time exceeds this, the war expired during downtime and is **skipped** (not restored).
.\reviews\wave-12-v2-codex.md:844:.\wiki\War_Persistence.md:98:- **JOINING status:** If the join phase end time has passed, the war is **promoted to INWAR** immediately with `warStartTime` set to now. The join phase is not re-run.
.\reviews\wave-12-v2-codex.md:845:.\wiki\War_Persistence.md:109:**Step 7 Ã¢â‚¬â€ State Restoration (INWAR wars):**
.\reviews\wave-12-v2-codex.md:849:.\wiki\War_Persistence.md:120:- Schedules a `TickScheduler.scheduleDelayed()` callback for when the join phase expires, which transitions the war to INWAR and calls `finalizeWarStart()`
.\reviews\wave-12-v2-codex.md:851:.\wiki\War_Persistence.md:145:Placed before TickScheduler shutdown because the war state must be captured while timers are still valid. Also wrapped in try/catch.
.\reviews\wave-12-v2-codex.md:2068:.\reviews\step-05-codex.md:983:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\wave-12-v2-codex.md:3360:.\reviews\step-03-codex.md:693:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\wave-12-v2-codex.md:3713:.\reviews\step-03-codex.md:2401:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\wave-12-v2-codex.md:4103:.\reviews\step-01-codex.md:581:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-v2-codex.md:4199:.\reviews\step-01-codex.md:914:        endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:4408:.\reviews\rework-v2-codex.md:111:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:668:    public static void checkForVictory(WarData war) {
.\reviews\wave-12-v2-codex.md:4409:.\reviews\rework-v2-codex.md:113:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1793:        checkForVictory(war);
.\reviews\wave-12-v2-codex.md:4413:.\reviews\rework-v2-codex.md:137:src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:235:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:4414:.\reviews\rework-v2-codex.md:138:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:186:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:4415:.\reviews\rework-v2-codex.md:139:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:200:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:4416:.\reviews\rework-v2-codex.md:140:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:356:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:4494:.\reviews\rework-v2-codex.md:1130:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:4498:.\reviews\rework-v2-codex.md:1140:     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
.\reviews\wave-12-v2-codex.md:4503:.\reviews\rework-v2-codex.md:1173:    public static void checkForVictory(WarData war) {
.\reviews\wave-12-v2-codex.md:4735:.\reviews\rework-codex.md:42:  #8 Ã¢â‚¬â€ WarSystem.endWar now calls WarBlockLedger.restoreWarDamage(warId, world)
.\reviews\wave-12-v2-codex.md:4844:.\reviews\rework-codex.md:879:+        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:4863:.\reviews\rework-codex.md:1046:+            endWar(war.getColony()); // clean up ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-v2-codex.md:4923:.\reviews\rework-codex.md:1266:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:4926:.\reviews\rework-codex.md:1270:+                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\wave-12-v2-codex.md:4953:.\reviews\rework-codex.md:1352:             endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:4957:.\reviews\rework-codex.md:1364:             endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:5037:.\reviews\rework-codex.md:1690:-                    if (war.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\wave-12-v2-codex.md:5039:.\reviews\rework-codex.md:1695:+                if (war.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\wave-12-v2-codex.md:5079:.\reviews\rework-codex.md:1854:-                war.setStatus(WarData.WarStatus.INWAR);
.\reviews\wave-12-v2-codex.md:5081:.\reviews\rework-codex.md:1856:-                finalizeWarStart(war);
.\reviews\wave-12-v2-codex.md:5088:.\reviews\rework-codex.md:1867:+            war.setStatus(WarData.WarStatus.INWAR);
.\reviews\wave-12-v2-codex.md:5090:.\reviews\rework-codex.md:1869:+            finalizeWarStart(war);
.\reviews\wave-12-v2-codex.md:5104:.\reviews\rework-codex.md:1895:+        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5110:.\reviews\rework-codex.md:1902:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5113:.\reviews\rework-codex.md:1908:-                if (warData.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\wave-12-v2-codex.md:5115:.\reviews\rework-codex.md:1913:+            if (warData.getStatus() != WarData.WarStatus.INWAR) {
.\reviews\wave-12-v2-codex.md:5116:.\reviews\rework-codex.md:1914:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5136:.\reviews\rework-codex.md:1947:+                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5178:.\reviews\rework-codex.md:2326:+        warData.setStatus(WarData.WarStatus.INWAR);
.\reviews\wave-12-v2-codex.md:5241:.\reviews\rework-codex.md:2683:+                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
.\reviews\wave-12-v2-codex.md:5250:.\reviews\rework-codex.md:2737:+        if (warData.getStatus() == WarData.WarStatus.INWAR) {
.\reviews\wave-12-v2-codex.md:5258:.\reviews\rework-codex.md:2759:+            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
.\reviews\wave-12-v2-codex.md:5596:.\reviews\rework-codex.md:4956: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\wave-12-v2-codex.md:5661:.\reviews\rework-codex.md:5381:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:5665:.\reviews\rework-codex.md:5391:     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
.\reviews\wave-12-v2-codex.md:5817:.\reviews\rework-codex.md:6199:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:331:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5818:.\reviews\rework-codex.md:6200:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1315:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5819:.\reviews\rework-codex.md:6201:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1319:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\reviews\wave-12-v2-codex.md:5820:.\reviews\rework-codex.md:6203:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2490:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5821:.\reviews\rework-codex.md:6204:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2497:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5822:.\reviews\rework-codex.md:6205:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2504:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5823:.\reviews\rework-codex.md:6206:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2522:                TickScheduler.cancel(warData.countdownTaskId);
.\reviews\wave-12-v2-codex.md:5824:.\reviews\rework-codex.md:6214:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:914:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-v2-codex.md:5825:.\reviews\rework-codex.md:6215:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:918:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5826:.\reviews\rework-codex.md:6216:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:926:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5827:.\reviews\rework-codex.md:6217:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:937:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5828:.\reviews\rework-codex.md:6218:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:992:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5829:.\reviews\rework-codex.md:6219:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1005:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5830:.\reviews\rework-codex.md:6220:src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1313:            TickScheduler.cancel(raidData.getCountdownTaskId());
.\reviews\wave-12-v2-codex.md:5949:.\reviews\rework-codex.md:7011:        endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:5993:.\reviews\rework-codex.md:7579:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "recordWarLoss|applyWarEconomyTransfers|handleVictoryRewards|checkForVictory|attackersWin|defendersWin" src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\wave-12-v2-codex.md:5994:.\reviews\rework-codex.md:7584:668:    public static void checkForVictory(WarData war) {
.\reviews\wave-12-v2-codex.md:6002:.\reviews\rework-codex.md:7599:1793:        checkForVictory(war);
.\reviews\wave-12-v2-codex.md:6022:.\reviews\rework-codex.md:7716:            endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6035:.\reviews\rework-codex.md:7741:            endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6063:.\reviews\rework-codex.md:7892:    public static void checkForVictory(WarData war) {
.\reviews\wave-12-v2-codex.md:6163:.\reviews\rework-codex.md:8791:8. VERIFIED Ã¢â‚¬â€ `WarSystem.endWar` calls `WarBlockLedger.restoreWarDamage(warId, world)` before active-war removal.
.\reviews\wave-12-v2-codex.md:6167:.\reviews\rework-codex.md:8821:8. VERIFIED Ã¢â‚¬â€ `WarSystem.endWar` calls `WarBlockLedger.restoreWarDamage(warId, world)` before active-war removal.
.\reviews\wave-12-v2-codex.md:6172:.\reviews\rework-bundle.md:13:  #8 Ã¢â‚¬â€ WarSystem.endWar now calls WarBlockLedger.restoreWarDamage(warId, world)
.\reviews\wave-12-v2-codex.md:6224:.\reviews\step-10-bundle.md:3:New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet Ã¢â‚¬â€ caller integration is a follow-up. NOT persisted across server restarts yet Ã¢â‚¬â€ ledger lost on crash.
.\reviews\wave-12-v2-codex.md:6227:.\reviews\step-10-bundle.md:38: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\reviews\wave-12-v2-codex.md:6815:.\reviews\SUMMARY.md:51:   `restoreWarDamage(warId, level)` is not called from `WarSystem.endWar`. Ledger just accumulates indefinitely. Add the call.
.\reviews\wave-12-v2-codex.md:6822:.\reviews\SUMMARY.md:86:- **Step 11 Ã¢â‚¬â€ `WAR_HITS` only clears on victory or shutdown.** Add cleanup in normal `endWar` path so peace-ended or stalemate wars don't leak hit state.
.\reviews\wave-12-v2-codex.md:6828:.\reviews\step-11-codex.md:14:Reviewing step 11 of 11. Experimental Town Hall demolition objective. Listens to ExplosionEvent.Detonate. Source must be ServerPlayer. Per-attacker per-war hit counter with 5-min cooldown gating. Distance check. Glow + coord broadcast. Victory via zeroing defender lives then calling checkForVictory. Banner objective NOT implemented. Review for: (1) is forcing victory by zeroing defender lives a clean way to trigger endWar? (2) ExplosionEvent.Detonate source extraction correctness - direct vs indirect source, (3) what if attacker is also somehow a defender? (4) thread safety, (5) edge cases of the cooldown gate. STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.
.\reviews\wave-12-v2-codex.md:6829:.\reviews\step-11-codex.md:19:New file TownHallDemolitionObjective.java. ExplosionEvent.Detonate listener. When EnableExperimentalSiegeObjectives is on AND explosion source is a player who is an attacker in an active war AND any affected block falls inside the defender's Town Hall building (via IBuilding.isInBuilding) AND attacker is within MaxSiegeRadius AND per-attacker cooldown elapsed Ã¢â€ â€™ count as Town Hall hit, apply GLOWING, broadcast coords to war participants. N hits = trigger victory via WarSystem.checkForVictory by zeroing defender lives. Banner-plant objective NOT implemented (requires custom item registration).
.\reviews\wave-12-v2-codex.md:6860:.\reviews\step-11-codex.md:221:            WarSystem.checkForVictory(war);
.\reviews\wave-12-v2-codex.md:6868:.\reviews\step-11-codex.md:303:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ExplosionEvent\\.Detonate|getDirectSourceEntity|getIndirectSourceEntity|TownHallDemolitionObjective|EnableExperimentalSiegeObjectives|TownHallHitCooldown\" -S ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\wave-12-v2-codex.md:6873:.\reviews\step-11-codex.md:315:.\reviews\step-01-codex.md:581:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-v2-codex.md:6919:.\reviews\step-11-codex.md:383:.\reviews\step-01-codex.md:914:        endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6924:.\reviews\step-11-codex.md:398:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:558:            endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6925:.\reviews\step-11-codex.md:399:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-v2-codex.md:6926:.\reviews\step-11-codex.md:400:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:668:    public static void checkForVictory(WarData war) {
.\reviews\wave-12-v2-codex.md:6927:.\reviews\step-11-codex.md:401:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:791:        endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6932:.\reviews\step-11-codex.md:407:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1586:            endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6933:.\reviews\step-11-codex.md:408:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1611:            endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6934:.\reviews\step-11-codex.md:409:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1647:            endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6935:.\reviews\step-11-codex.md:410:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1757:        endWar(war.getColony());
.\reviews\wave-12-v2-codex.md:6936:.\reviews\step-11-codex.md:411:.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1777:        checkForVictory(war);
.\reviews\wave-12-v2-codex.md:6949:.\reviews\step-11-codex.md:445:.\reviews\step-05-codex.md:983:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\reviews\wave-12-v2-codex.md:7186:src/main\java\net\machiavelli\minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-codex.md:490:src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java
.\reviews\wave-12-codex.md:11271:src\main\java\net\machiavelli\minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-codex.md:11329:src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:10:import java.util.concurrent.ConcurrentHashMap;
.\reviews\wave-12-codex.md:11330:src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:32:    private static final Map<Long, ScheduledTask> TASKS = new ConcurrentHashMap<>();
.\reviews\wave-12-codex.md:11755:570:            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\wave-12-codex.md:11777:1241-    public static void endWar(IColony colony) {
.\reviews\wave-12-codex.md:11800:2143-            TickScheduler.scheduleDelayed(() -> {
.\reviews\wave-12-codex.md:11816:2490-        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-12-codex.md:11880:4197-                TickScheduler.scheduleDelayed(() -> {
.\reviews\wave-12-codex.md:11886:4203-                    w.setStatus(WarData.WarStatus.INWAR);
.\reviews\wave-12-codex.md: WARNING: stopped searching binary file after match (found "\0" byte around offset 1185159)
.\reviews\wave-16-bundle.md:4:  - siege/SiegeBannerBlock.java â€” placeable block (hardness 0.5, blast resistance 1.2M, soft glow). setPlacedBy hands off to PlantTheBannerObjective.
.\reviews\wave-16-bundle.md:5:  - siege/ModSiegeBlocks.java â€” DeferredRegister<Block> + DeferredRegister<Item> registering 'minecolonytax:siege_banner'.
.\reviews\wave-16-bundle.md:6:  - siege/PlantTheBannerObjective.java â€” full lifecycle: validate placer is attacker / placement is inside Town Hall via isInBuilding / start war-scoped boss bar / 10-min hold timer / defender can break to cancel / 1 re-plant allowed / on expiry trigger victory via WarSystem.checkForVictory.
.\reviews\wave-16-bundle.md:10:TaxConfig: BannerCaptureMinutes config (default 10).
.\reviews\wave-16-bundle.md:12:WarSystem hooks: at INWAR transition (if experimental flag on), give each attacker a Siege Banner. At endWar, call PlantTheBannerObjective.onWarEnded to clear per-war state.
.\reviews\wave-16-bundle.md:14:Behind EnableExperimentalSiegeObjectives â€” banner block is always registered, but PlantTheBannerObjective.onBannerPlaced early-returns if the flag is off (banner just behaves as a vanilla solid block in that case).
.\reviews\wave-16-bundle.md:16:### NEW FILE: siege/SiegeBannerBlock.java
.\reviews\wave-16-bundle.md:32: * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
.\reviews\wave-16-bundle.md:36: * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
.\reviews\wave-16-bundle.md:39:public class SiegeBannerBlock extends Block {
.\reviews\wave-16-bundle.md:41:    public SiegeBannerBlock() {
.\reviews\wave-16-bundle.md:55:        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
.\reviews\wave-16-bundle.md:60:### NEW FILE: siege/ModSiegeBlocks.java
.\reviews\wave-16-bundle.md:80: * gated behind {@code EnableExperimentalSiegeObjectives} at runtime.
.\reviews\wave-16-bundle.md:82:public final class ModSiegeBlocks {
.\reviews\wave-16-bundle.md:91:            SiegeBannerBlock::new);
.\reviews\wave-16-bundle.md:97:    private ModSiegeBlocks() {}
.\reviews\wave-16-bundle.md:106:### NEW FILE: siege/PlantTheBannerObjective.java
.\reviews\wave-16-bundle.md:117:import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\wave-16-bundle.md:148: *     remaining hold time (default 10 min via {@code BannerCaptureMinutes}).
.\reviews\wave-16-bundle.md:153: *     WarSystem.checkForVictory path used by Town Hall demolition.
.\reviews\wave-16-bundle.md:155: * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
.\reviews\wave-16-bundle.md:160:public final class PlantTheBannerObjective {
.\reviews\wave-16-bundle.md:162:    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);
.\reviews\wave-16-bundle.md:188:    private PlantTheBannerObjective() {}
.\reviews\wave-16-bundle.md:191:     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
.\reviews\wave-16-bundle.md:262:        int holdMinutes = TaxConfig.getBannerCaptureMinutesOrDefault();
.\reviews\wave-16-bundle.md:277:        final long taskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-16-bundle.md:310:        if (!(event.getState().getBlock() instanceof SiegeBannerBlock)) return;
.\reviews\wave-16-bundle.md:371:        // Zero defender lives so checkForVictory resolves as attacker win.
.\reviews\wave-16-bundle.md:377:            WarSystem.checkForVictory(war);
.\reviews\wave-16-bundle.md:383:    /** Cleanup hook from WarSystem.endWar â€” drop per-war state. */
.\reviews\wave-16-bundle.md:392:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:33:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:325:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:342:                    endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:542:    public static void finalizeWarStart(WarData war) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:552:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:564:            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:605:        war.setStatus(WarData.WarStatus.INWAR);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:615:        // a Siege Banner at INWAR transition. They can plant it inside the
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:622:                    var bannerItem = net.machiavelli.minecolonytax.siege.ModSiegeBlocks.SIEGE_BANNER_ITEM.get();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:713:    public static void checkForVictory(WarData war) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:836:        endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1286:    public static void endWar(IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1288:        // Finding 10: make endWar idempotent. Atomically remove the WarData from
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1289:        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1328:                net.machiavelli.minecolonytax.siege.PlantTheBannerObjective.onWarEnded(warData.getWarID());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1382:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1386:                TickScheduler.cancel(warData.warChestDrainTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1669:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1694:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1730:            endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1840:        endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1860:        checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2213:            TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2224:                if (war.getStatus() != WarData.WarStatus.INWAR) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2395:            TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2436:        TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2440:            war.setStatus(WarData.WarStatus.INWAR);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2442:            finalizeWarStart(war);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2560:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2567:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2573:            if (warData.getStatus() != WarData.WarStatus.INWAR) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2574:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2606:                TickScheduler.cancel(warData.countdownTaskId);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2926:        TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:3080:        warData.setStatus(WarData.WarStatus.INWAR);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4287:        if (status == WarData.WarStatus.INWAR) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4295:                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4296:                status = WarData.WarStatus.INWAR;
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4377:        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4381:        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4383:        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4388:                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4389:                try { endWar(defenderColony); } catch (Throwable t2) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4390:                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4420:        if (warData.getStatus() == WarData.WarStatus.INWAR) {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4442:            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4455:                TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4461:                    w.setStatus(WarData.WarStatus.INWAR);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4463:                    finalizeWarStart(w);
.\src\main\java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:574:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:26:public class TickScheduler {
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:79:            LOGGER.info("TickScheduler shutdown - cleared {} pending tasks", count);
.\src\main\java\net\machiavelli\minecolonytax\util\TickScheduler.java:104:                    LOGGER.error("TickScheduler task {} threw an exception", entry.getKey(), e);
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:32:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1179:        // Schedule unfreeze on the main server thread via TickScheduler
.\src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1180:        TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:670:                                .define("EnableExperimentalSiegeObjectives", false);
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:699:                                                "attacker may re-plant once. Behind EnableExperimentalSiegeObjectives.")
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:700:                                .defineInRange("BannerCaptureMinutes", 10, 1, 120);
.\src\main\java\net\machiavelli\minecolonytax\TaxConfig.java:2735:        public static int getBannerCaptureMinutesOrDefault() {
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:8:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:33: * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:149:     * Called from WarSystem.endWar() â€” safe to call when no ledger exists.
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:169:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\siege\WarBlockLedger.java:190:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:41: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:182:     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:183:     * defender lives to zero so the existing checkForVictory + endWar pipeline
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:187:     * checkForVictory would resolve as DEFENDER victory the moment we zero
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:196:        // Mirror WarSystem.checkForVictory's defender-win condition exactly. If we
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:228:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:235:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:245:     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:15: * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:19: * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:22:public class SiegeBannerBlock extends Block {
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:24:    public SiegeBannerBlock() {
.\src\main\java\net\machiavelli\minecolonytax\siege\SiegeBannerBlock.java:38:        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:10:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:41: *     remaining hold time (default 10 min via {@code BannerCaptureMinutes}).
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:46: *     WarSystem.checkForVictory path used by Town Hall demolition.
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:48: * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:53:public final class PlantTheBannerObjective {
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:55:    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:81:    private PlantTheBannerObjective() {}
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:84:     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:155:        int holdMinutes = TaxConfig.getBannerCaptureMinutesOrDefault();
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:170:        final long taskId = TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:203:        if (!(event.getState().getBlock() instanceof SiegeBannerBlock)) return;
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:264:        // Zero defender lives so checkForVictory resolves as attacker win.
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:270:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:276:    /** Cleanup hook from WarSystem.endWar â€” drop per-war state. */
.\src\main\java\net\machiavelli\minecolonytax\siege\PlantTheBannerObjective.java:285:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
.\src\main\java\net\machiavelli\minecolonytax\siege\ModSiegeBlocks.java:19: * gated behind {@code EnableExperimentalSiegeObjectives} at runtime.
.\src\main\java\net\machiavelli\minecolonytax\siege\ModSiegeBlocks.java:21:public final class ModSiegeBlocks {
.\src\main\java\net\machiavelli\minecolonytax\siege\ModSiegeBlocks.java:30:            SiegeBannerBlock::new);
.\src\main\java\net\machiavelli\minecolonytax\siege\ModSiegeBlocks.java:36:    private ModSiegeBlocks() {}
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:38:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:928:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:932:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:940:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:951:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1006:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1019:                TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1190:            TickScheduler.cancel(raidData.getCountdownTaskId());
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPManager.java:58:    // All deferred scheduling now uses TickScheduler.scheduleDelayed(...) which runs
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:217:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:25:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:70:        // Schedule restoration after 5 seconds via TickScheduler (main server thread).
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:74:        TickScheduler.scheduleDelayed(() -> restoreDefeatedPlayer(capturedId, battle), 5_000L);
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:90:            // PlayerLoggedIn handler can restore them on next login (fix #4b).
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:727:        // Use TickScheduler (main server thread) instead of forbidden ScheduledExecutorService.
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:728:        TickScheduler.scheduleDelayed(() -> endBattle(battleId), countdownSeconds * 1000L);
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:751:            // TickScheduler already runs us on the main server thread; no nested server.execute.
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:761:            // If player is offline, leave entries in place so PlayerLoggedInEvent
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:823:            TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\pvp\PvPBattleManager.java:835:        TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:186:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:200:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:223:        if (war.getStatus() == WarData.WarStatus.INWAR) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:258:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:356:            WarSystem.checkForVictory(war);
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:445:    private static void sendWarParticipantsMessage(WarData war, Component message) {
.\src\main\java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:44:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:54:        // Block peace proposals during join phase - only allow during INWAR status
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:55:        if (war.getStatus() != WarData.WarStatus.INWAR) {
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:161:        // double reparations transfers and double-fires of endWar().
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:286:                // Set penalty report before endWar so war history logging captures peace outcome
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:288:                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:339:                // Set penalty report before endWar so war history logging captures peace outcome
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:342:                WarSystem.endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:370:                // Set penalty report before endWar
.\src\main\java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:373:                WarSystem.endWar(war.getColony());
.\src\main\java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:431:    /** Periodic check â€” transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
.\src\main\java\net\machiavelli\minecolonytax\event\PatchouliBookHandler.java:29:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:54:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(
.\src\main\java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:113:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\event\EntityRaidBossbarAttachHandler.java:26:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:57:        // via EnableExperimentalSiegeObjectives.
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:58:        net.machiavelli.minecolonytax.siege.ModSiegeBlocks.register(
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:67:        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.util.TickScheduler.class);
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:137:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:162:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:171:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:228:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:275:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:287:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:300:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:381:        // Save active wars before TickScheduler shutdown â€” task IDs are still needed for cleanup
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:411:            net.machiavelli.minecolonytax.util.TickScheduler.shutdown();
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:412:            if (TaxConfig.isNormalLogging()) LOGGER.info("TickScheduler shutdown complete");
.\src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:414:            LOGGER.warn("Error during TickScheduler shutdown: {}", t.toString());
.\src\main\java\net\machiavelli\minecolonytax\militia\MilitiaSpawner.java:99:     * endWar / cleanupRaid. Clears the set after iteration.
.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:336:     * @param warData        The WarData captured at endWar() time (still valid after remove).
.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:415:     * Called periodically from the TickScheduler task set up in MineColonyTax.
.\src\main\java\net\machiavelli\minecolonytax\db\WarStatsDB.java:573:    /** Determine the DB outcome string from a WarData at the moment endWar() is called. */
.\src\main\java\net\machiavelli\minecolonytax\espionage\SpyManager.java:612:     * Safe to call repeatedly â€” drains the per-player queue. Call from PlayerLoggedInEvent or GUI-open hook.
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:12:import net.machiavelli.minecolonytax.util.TickScheduler;
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:18:     * Militia-upgrade reinforcements spawned at INWAR transition. Transient â€”
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:21:     * they can be cleanly despawned in {@code WarSystem.endWar}. By design
.\src\main\java\net\machiavelli\minecolonytax\data\WarData.java:59:    public enum WarStatus { JOINING, INWAR, ERROR }
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:94:    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\capability\PlayerWarDataCapability.java:19:import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
.\src\main\java\net\machiavelli\minecolonytax\capability\PlayerWarDataCapability.java:125:    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1764:                WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1783:                        WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:324:            WarSystem.endWar(colony);
.\src\main\java\net\machiavelli\minecolonytax\commands\WarCommands.java:347:        WarSystem.endWar(colony);
.\reviews\waves-13-14-codex.md:14:Reviewing the partial-work completion pass: Step 8 (war-side militia wiring) and Step 9 (Vassals tab UI/packet wiring) finished. Five files changed plus one new MilitiaSpawner helper. Review for: (1) is the militia spawn in WarSystem.finalizeWarStart in a re-entrant safe location â€” could finalizeWarStart be called twice for the same war? (2) packet encode/decode symmetry â€” does the kind ordinal round-trip correctly and handle out-of-range values from old clients? (3) ColonyDataCollector occupation rows â€” does it correctly skip expired occupations, dedup against existing vassal rows for the same colony? (4) VassalsPage kind dispatch â€” fallback for null kind correct? Respond: STATUS APPROVE/REWORK, up to 5 bullets, up to 3 fix suggestions. Max 300 words.
.\reviews\waves-13-14-codex.md:19:Wave 13 (Step 8 finish): extracted militia spawning to shared net.machiavelli.minecolonytax.militia.MilitiaSpawner. BesiegeManager now delegates. WarSystem.finalizeWarStart calls MilitiaSpawner for BOTH defender and attacker colonies (their guard count Ã— upgrade multiplier). Tracked in new WarData.militiaSupport set. Despawned in endWar.
.\reviews\waves-13-14-codex.md:125:     * endWar / cleanupRaid. Clears the set after iteration.
.\reviews\waves-13-14-codex.md:156:### DIFF: WarSystem.java (finalizeWarStart + endWar militia despawn)
.\reviews\waves-13-14-codex.md:281:     public static void endWar(IColony colony) {
.\reviews\waves-13-14-codex.md:285:+        // Finding 10: make endWar idempotent. Atomically remove the WarData from
.\reviews\waves-13-14-codex.md:286:+        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
.\reviews\waves-13-14-codex.md:798:         if (status == WarData.WarStatus.INWAR) {
.\reviews\waves-13-14-codex.md:889:+        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
.\reviews\waves-13-14-codex.md:893:+        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
.\reviews\waves-13-14-codex.md:895:+        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
.\reviews\waves-13-14-codex.md:900:+                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
.\reviews\waves-13-14-codex.md:901:+                try { endWar(defenderColony); } catch (Throwable t2) {
.\reviews\waves-13-14-codex.md:902:+                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
.\reviews\waves-13-14-codex.md:924: import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\waves-13-14-codex.md:930:+     * Militia-upgrade reinforcements spawned at INWAR transition. Transient â€”
.\reviews\waves-13-14-codex.md:933:+     * they can be cleanly despawned in {@code WarSystem.endWar}. By design
.\reviews\waves-13-14-codex.md:1207:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"finalizeWarStart|new VassalIncomeData|VassalKind|OccupationData|isExpired|getActiveOccupations|class ColonyDataResponsePacket|encode\\(|decode\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\waves-13-14-codex.md:1225:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:542:    public static void finalizeWarStart(WarData war) {
.\reviews\waves-13-14-codex.md:1226:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2416:            finalizeWarStart(war);
.\reviews\waves-13-14-codex.md:1231:src/main/java\net\machiavelli\minecolonytax\WarSystem.java:4437:                    finalizeWarStart(w);
.\reviews\waves-13-14-codex.md:1451:     public static void endWar(IColony colony) {
.\reviews\waves-13-14-codex.md:1455:+        // Finding 10: make endWar idempotent. Atomically remove the WarData from
.\reviews\waves-13-14-codex.md:1456:+        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
.\reviews\waves-13-14-codex.md:1968:         if (status == WarData.WarStatus.INWAR) {
.\reviews\waves-13-14-codex.md:2059:+        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
.\reviews\waves-13-14-codex.md:2063:+        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
.\reviews\waves-13-14-codex.md:2065:+        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
.\reviews\waves-13-14-codex.md:2070:+                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
.\reviews\waves-13-14-codex.md:2071:+                try { endWar(defenderColony); } catch (Throwable t2) {
.\reviews\waves-13-14-codex.md:2072:+                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
.\reviews\waves-13-14-codex.md:2090: import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\waves-13-14-codex.md:2096:+     * Militia-upgrade reinforcements spawned at INWAR transition. Transient Ñ‚ÐÐ¤
.\reviews\waves-13-14-codex.md:2099:+     * they can be cleanly despawned in {@code WarSystem.endWar}. By design
.\reviews\waves-13-14-codex.md:2387:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 35 \"public static void finalizeWarStart|finalizeWarStart\\(war\\)|finalizeWarStart\\(w\\)\" src/main/java/net/machiavelli/minecolonytax/WarSystem.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\waves-13-14-codex.md:2430:542:    public static void finalizeWarStart(WarData war) {
.\reviews\waves-13-14-codex.md:2440:552-            endWar(war.getColony());
.\reviews\waves-13-14-codex.md:2452:564-            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\waves-13-14-codex.md:2496:2410-        TickScheduler.scheduleDelayed(() -> {
.\reviews\waves-13-14-codex.md:2500:2414-            war.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:2502:2416:            finalizeWarStart(war);
.\reviews\waves-13-14-codex.md:2553:4416-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
.\reviews\waves-13-14-codex.md:2566:4429-                TickScheduler.scheduleDelayed(() -> {
.\reviews\waves-13-14-codex.md:2572:4435-                    w.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:2574:4437:                    finalizeWarStart(w);
.\reviews\waves-13-14-codex.md:2809:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 45 \"militiaSupport|setStatus\\(WarData\\.WarStatus\\.INWAR\\)|startWarCountdown\\(war\\)\" src/main/java/net/machiavelli/minecolonytax/WarSystem.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\waves-13-14-codex.md:3017:564-            endWar(war.getColony()); // clean up Ã¢â‚¬â€ bare return would leak the war in ACTIVE_WARS
.\reviews\waves-13-14-codex.md:3058:605:        war.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:3140:1265-    public static void endWar(IColony colony) {
.\reviews\waves-13-14-codex.md:3142:1267-        // Finding 10: make endWar idempotent. Atomically remove the WarData from
.\reviews\waves-13-14-codex.md:3143:1268-        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
.\reviews\waves-13-14-codex.md:3229:2369-            TickScheduler.scheduleRepeating(() -> {
.\reviews\waves-13-14-codex.md:3270:2410-        TickScheduler.scheduleDelayed(() -> {
.\reviews\waves-13-14-codex.md:3274:2414:            war.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:3276:2416-            finalizeWarStart(war);
.\reviews\waves-13-14-codex.md:3374:3054:        warData.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:3425:4394-        if (warData.getStatus() == WarData.WarStatus.INWAR) {
.\reviews\waves-13-14-codex.md:3447:4416-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
.\reviews\waves-13-14-codex.md:3460:4429-                TickScheduler.scheduleDelayed(() -> {
.\reviews\waves-13-14-codex.md:3466:4435:                    w.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:3468:4437-                    finalizeWarStart(w);
.\reviews\waves-13-14-codex.md:5266:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 6 \"endWar\\(\" src/main/java/net/machiavelli/minecolonytax | rg -v \"public static void endWar\"" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\waves-13-14-codex.md:5589:4261-        if (status == WarData.WarStatus.INWAR) {
.\reviews\waves-13-14-codex.md:5597:4269-                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
.\reviews\waves-13-14-codex.md:5598:4270-                status = WarData.WarStatus.INWAR;
.\reviews\waves-13-14-codex.md:5644:4416-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
.\reviews\waves-13-14-codex.md:5656:4429-                TickScheduler.scheduleDelayed(() -> {
.\reviews\waves-13-14-codex.md:5662:4435-                    w.setStatus(WarData.WarStatus.INWAR);
.\reviews\waves-13-14-codex.md:5664:4437-                    finalizeWarStart(w);
.\reviews\waves-13-14-codex.md:5697:src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java:336:     * @param warData        The WarData captured at endWar() time (still valid after remove).
.\reviews\waves-13-14-codex.md:5711:src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java:573:    /** Determine the DB outcome string from a WarData at the moment endWar() is called. */
.\reviews\waves-13-14-codex.md:5725:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:342:                    endWar(colony);
.\reviews\waves-13-14-codex.md:5739:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:552:            endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5751:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:564:            endWar(war.getColony()); // clean up ??? bare return would leak the war in ACTIVE_WARS
.\reviews\waves-13-14-codex.md:5765:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:815:        endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5780:src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1267-        // Finding 10: make endWar idempotent. Atomically remove the WarData from
.\reviews\waves-13-14-codex.md:5781:src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1268-        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
.\reviews\waves-13-14-codex.md:5792:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1643:            endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5806:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1668:            endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5820:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1704:            endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5834:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1814:        endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5842:src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4357-        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
.\reviews\waves-13-14-codex.md:5847:src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4362-                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
.\reviews\waves-13-14-codex.md:5848:src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4363:                try { endWar(defenderColony); } catch (Throwable t2) {
.\reviews\waves-13-14-codex.md:5849:src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4364-                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
.\reviews\waves-13-14-codex.md:5862:src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:149:     * Called from WarSystem.endWar() ??? safe to call when no ledger exists.
.\reviews\waves-13-14-codex.md:5876:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:324:            WarSystem.endWar(colony);
.\reviews\waves-13-14-codex.md:5890:src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:347:        WarSystem.endWar(colony);
.\reviews\waves-13-14-codex.md:5904:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:161:        // double reparations transfers and double-fires of endWar().
.\reviews\waves-13-14-codex.md:5916:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-286-                // Set penalty report before endWar so war history logging captures peace outcome
.\reviews\waves-13-14-codex.md:5918:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:288:                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
.\reviews\waves-13-14-codex.md:5929:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-339-                // Set penalty report before endWar so war history logging captures peace outcome
.\reviews\waves-13-14-codex.md:5932:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:342:                WarSystem.endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5943:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-370-                // Set penalty report before endWar
.\reviews\waves-13-14-codex.md:5946:src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:373:                WarSystem.endWar(war.getColony());
.\reviews\waves-13-14-codex.md:5960:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1764:                WarSystem.endWar(colony);
.\reviews\waves-13-14-codex.md:5974:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1783:                        WarSystem.endWar(colony);
.\reviews\waves-13-14-codex.md:5993:- `finalizeWarStart` is not fully re-entrant safe. `militiaSupport.isEmpty()` prevents duplicate militia only while the same active `WarData` still holds spawned entities, but the live delayed join-phase callback does not check `ACTIVE_WARS` or prior status before transitioning. A late callback after `endWar` clears `militiaSupport` could re-spawn militia on stale war data.
.\reviews\waves-13-14-codex.md:5999:1. Add an active-war/status guard before `finalizeWarStart`: `ACTIVE_WARS.get(colonyId) == war && war.getStatus() == JOINING`, then transition once.
.\reviews\waves-13-14-codex.md:6006:- `finalizeWarStart` is not fully re-entrant safe. `militiaSupport.isEmpty()` prevents duplicate militia only while the same active `WarData` still holds spawned entities, but the live delayed join-phase callback does not check `ACTIVE_WARS` or prior status before transitioning. A late callback after `endWar` clears `militiaSupport` could re-spawn militia on stale war data.
.\reviews\waves-13-14-codex.md:6012:1. Add an active-war/status guard before `finalizeWarStart`: `ACTIVE_WARS.get(colonyId) == war && war.getStatus() == JOINING`, then transition once.
.\reviews\waves-13-14-bundle.md:3:Wave 13 (Step 8 finish): extracted militia spawning to shared net.machiavelli.minecolonytax.militia.MilitiaSpawner. BesiegeManager now delegates. WarSystem.finalizeWarStart calls MilitiaSpawner for BOTH defender and attacker colonies (their guard count Ã— upgrade multiplier). Tracked in new WarData.militiaSupport set. Despawned in endWar.
.\reviews\waves-13-14-bundle.md:109:     * endWar / cleanupRaid. Clears the set after iteration.
.\reviews\waves-13-14-bundle.md:140:### DIFF: WarSystem.java (finalizeWarStart + endWar militia despawn)
.\reviews\waves-13-14-bundle.md:265:     public static void endWar(IColony colony) {
.\reviews\waves-13-14-bundle.md:269:+        // Finding 10: make endWar idempotent. Atomically remove the WarData from
.\reviews\waves-13-14-bundle.md:270:+        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
.\reviews\waves-13-14-bundle.md:782:         if (status == WarData.WarStatus.INWAR) {
.\reviews\waves-13-14-bundle.md:873:+        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
.\reviews\waves-13-14-bundle.md:877:+        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
.\reviews\waves-13-14-bundle.md:879:+        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
.\reviews\waves-13-14-bundle.md:884:+                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
.\reviews\waves-13-14-bundle.md:885:+                try { endWar(defenderColony); } catch (Throwable t2) {
.\reviews\waves-13-14-bundle.md:886:+                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
.\reviews\waves-13-14-bundle.md:908: import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\waves-13-14-bundle.md:914:+     * Militia-upgrade reinforcements spawned at INWAR transition. Transient â€”
.\reviews\waves-13-14-bundle.md:917:+     * they can be cleanly despawned in {@code WarSystem.endWar}. By design
.\reviews\wave-8-11-v2-codex.md:2192:A  src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java
.\reviews\wave-8-11-codex.md:76:+                                .define("EnableExperimentalSiegeObjectives", false);
.\reviews\wave-8-11-codex.md:2899:src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-415-    /** Periodic check Ã¢â‚¬â€ transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
.\reviews\wave-8-11-bundle.md:60:+                                .define("EnableExperimentalSiegeObjectives", false);
.\reviews\wave-16-codex.md:14:Reviewing Step 11 â€” Plant-the-Banner objective. Custom Block + BlockItem registered via DeferredRegister; PlantTheBannerObjective handles place validation, war-scoped boss bar, hold-timer, defender break, 1 re-plant, and victory trigger. WarSystem hands out the banner at INWAR and clears state at endWar. Review for: (1) is setPlacedBy â†’ static handler hand-off safe (server-side only)?, (2) BlockEvent.BreakEvent handler â€” does it correctly identify our banner block via instanceof?, (3) replant counter semantics â€” is it correctly incremented on break only (not on initial place)?, (4) boss bar lifecycle â€” does removeAllPlayers + setVisible(false) cleanly drop client-side state?, (5) does the item-handout in finalizeWarStart correctly skip if player is offline AND retry when they connect?, (6) is destroyBannerSilently safe to call before placeBlock fully settled?, (7) any race between the 1Hz scheduled task and onWarEnded clearing state? STATUS APPROVE/REWORK, up to 6 bullets, up to 3 fix suggestions. Max 400 words.
.\reviews\wave-16-codex.md:20:  - siege/SiegeBannerBlock.java â€” placeable block (hardness 0.5, blast resistance 1.2M, soft glow). setPlacedBy hands off to PlantTheBannerObjective.
.\reviews\wave-16-codex.md:21:  - siege/ModSiegeBlocks.java â€” DeferredRegister<Block> + DeferredRegister<Item> registering 'minecolonytax:siege_banner'.
.\reviews\wave-16-codex.md:22:  - siege/PlantTheBannerObjective.java â€” full lifecycle: validate placer is attacker / placement is inside Town Hall via isInBuilding / start war-scoped boss bar / 10-min hold timer / defender can break to cancel / 1 re-plant allowed / on expiry trigger victory via WarSystem.checkForVictory.
.\reviews\wave-16-codex.md:26:TaxConfig: BannerCaptureMinutes config (default 10).
.\reviews\wave-16-codex.md:28:WarSystem hooks: at INWAR transition (if experimental flag on), give each attacker a Siege Banner. At endWar, call PlantTheBannerObjective.onWarEnded to clear per-war state.
.\reviews\wave-16-codex.md:30:Behind EnableExperimentalSiegeObjectives â€” banner block is always registered, but PlantTheBannerObjective.onBannerPlaced early-returns if the flag is off (banner just behaves as a vanilla solid block in that case).
.\reviews\wave-16-codex.md:32:### NEW FILE: siege/SiegeBannerBlock.java
.\reviews\wave-16-codex.md:48: * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
.\reviews\wave-16-codex.md:52: * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
.\reviews\wave-16-codex.md:55:public class SiegeBannerBlock extends Block {
.\reviews\wave-16-codex.md:57:    public SiegeBannerBlock() {
.\reviews\wave-16-codex.md:71:        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
.\reviews\wave-16-codex.md:76:### NEW FILE: siege/ModSiegeBlocks.java
.\reviews\wave-16-codex.md:96: * gated behind {@code EnableExperimentalSiegeObjectives} at runtime.
.\reviews\wave-16-codex.md:98:public final class ModSiegeBlocks {
.\reviews\wave-16-codex.md:107:            SiegeBannerBlock::new);
.\reviews\wave-16-codex.md:113:    private ModSiegeBlocks() {}
.\reviews\wave-16-codex.md:122:### NEW FILE: siege/PlantTheBannerObjective.java
.\reviews\wave-16-codex.md:133:import net.machiavelli.minecolonytax.util.TickScheduler;
.\reviews\wave-16-codex.md:164: *     remaining hold time (default 10 min via {@code BannerCaptureMinutes}).
.\reviews\wave-16-codex.md:169: *     WarSystem.checkForVictory path used by Town Hall demolition.
.\reviews\wave-16-codex.md:171: * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
.\reviews\wave-16-codex.md:176:public final class PlantTheBannerObjective {
.\reviews\wave-16-codex.md:178:    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);
.\reviews\wave-16-codex.md:204:    private PlantTheBannerObjective() {}
.\reviews\wave-16-codex.md:207:     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
.\reviews\wave-16-codex.md:278:        int holdMinutes = TaxConfig.getBannerCaptureMinutesOrDefault();
.\reviews\wave-16-codex.md:293:        final long taskId = TickScheduler.scheduleRepeating(() -> {
.\reviews\wave-16-codex.md:326:        if (!(event.getState().getBlock() instanceof SiegeBannerBlock)) return;
.\reviews\wave-16-codex.md:387:        // Zero defender lives so checkForVictory resolves as attacker win.
.\reviews\wave-16-codex.md:393:            WarSystem.checkForVictory(war);
.\reviews\wave-16-codex.md:399:    /** Cleanup hook from WarSystem.endWar â€” drop per-war state. */
.\reviews\wave-16-codex.md:408:        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
.\reviews\wave-16-codex.md:523:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"class TickScheduler|scheduleRepeating|cancel\\(|runScheduled|ServerTick|TickEvent\" -S src ." in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\wave-16-codex.md:525:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "finalizeWarStart|INWAR|endWar|checkForVictory|PlayerLoggedIn|LoggedIn|SiegeBanner|PlantTheBanner|ModSiegeBlocks|TickScheduler|EnableExperimentalSiegeObjectives|BannerCaptureMinutes" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\minecolonies\src\main\java\com\minecolonies\core\event\FMLEventHandler.java:35:    public static void onPlayerLogin(@NotNull final PlayerEvent.PlayerLoggedInEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\EventHandler.java:467:    public static void onPlayerEnterWorld(final PlayerEvent.PlayerLoggedInEvent event)
.\minecolonies\src\main\java\com\minecolonies\core\event\DataPackSyncEventHandler.java:35: *  -- OnDatapackSyncEvent [server], PlayerLoggedInEvent [server], RecipesUpdatedEvent [client], TagsUpdatedEvent [client]
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
I found the Step 11 files in the workspace and am narrowing on the exact lifecycle points: banner placement, break handling, `finalizeWarStart`, login boss-bar behavior, `endWar`, and `TickScheduler` cancellation semantics.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java'; Get-Content "'$p | Select-Object -Skip 1 -First 380' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java'; Get-Content "'$p | Select-Object -Skip 235 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 520 -First 140' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 1260 -First 155' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java'; Get-Content "'$p | Select-Object -Skip 1 -First 135' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java'; Get-Content "'$p | Select-Object -Skip 1 -First 380'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java'; Get-Content "'$p | Select-Object -Skip 1 -First 135'` rejected: blocked by policy
2026-05-25T15:46:48.729129Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java'; Get-Content "'$p | Select-Object -Skip 1 -First 380'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 520 -First 140'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 1260 -First 155'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java'; Get-Content "'$p | Select-Object -Skip 235 -First 90'` rejected: blocked by policy
2026-05-25T15:46:48.760783Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 1260 -First 155'` rejected: blocked by policy
2026-05-25T15:46:48.761156Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java'; Get-Content "'$p | Select-Object -Skip 1 -First 135'` rejected: blocked by policy
2026-05-25T15:46:48.765597Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java'; Get-Content "'$p | Select-Object -Skip 235 -First 90'` rejected: blocked by policy
2026-05-25T15:46:48.778554Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; Get-Content "'$p | Select-Object -Skip 520 -First 140'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 85 "public static void endWar" src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 90 "public static void onBannerPlaced|private static void startCapture|public static void onBlockBreak|private static void clearCapture|destroyBannerSilently" src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 60 "onPlayerLogin|PlayerLoggedInEvent|bossEvent.addPlayer|alliesBossEvent.addPlayer" src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 75 \"class TickScheduler|scheduleRepeating|cancel\\(|onServerTick\" src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 75 "public static void finalizeWarStart" src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 471ms:
1-package net.machiavelli.minecolonytax.util;
2-
3-import net.minecraftforge.event.TickEvent;
4-import net.minecraftforge.eventbus.api.SubscribeEvent;
5-import org.apache.logging.log4j.LogManager;
6-import org.apache.logging.log4j.Logger;
7-
8-import java.util.Iterator;
9-import java.util.Map;
10-import java.util.concurrent.ConcurrentHashMap;
11-import java.util.concurrent.atomic.AtomicLong;
12-
13-/**
14- * Central tick-based scheduler that replaces all java.util.Timer/TimerTask usage.
15- * All scheduled tasks run on the main server thread via Forge's ServerTickEvent,
16- * eliminating cross-thread state mutation of war/raid data.
17- *
18- * <p>Key guarantees:
19- * <ul>
20- *   <li>All callbacks execute on the main server thread</li>
21- *   <li>Deterministic tick-based timing (20 ticks = 1 second)</li>
22- *   <li>Safe cancellation by task ID</li>
23- *   <li>Automatic cleanup on server shutdown</li>
24- * </ul>
25- */
26:public class TickScheduler {
27-
28-    private static final Logger LOGGER = LogManager.getLogger();
29-    private static final int TICKS_PER_SECOND = 20;
30-
31-    private static final AtomicLong NEXT_TASK_ID = new AtomicLong(1);
32-    private static final Map<Long, ScheduledTask> TASKS = new ConcurrentHashMap<>();
33-
34-    private static class ScheduledTask {
35-        final Runnable action;
36-        long ticksRemaining;       // ticks until first/next execution
37-        final long intervalTicks;  // 0 = one-shot, >0 = repeating
38-        volatile boolean cancelled;
39-
40-        ScheduledTask(Runnable action, long delayTicks, long intervalTicks) {
41-            this.action = action;
42-            this.ticksRemaining = delayTicks;
43-            this.intervalTicks = intervalTicks;
44-            this.cancelled = false;
45-        }
46-    }
47-
48-    public static long scheduleDelayed(Runnable action, long delayMs) {
49-        long ticks = msToTicks(delayMs);
50-        long id = NEXT_TASK_ID.getAndIncrement();
51-        TASKS.put(id, new ScheduledTask(action, ticks, 0));
52-        return id;
53-    }
54-
55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
56-        long delayTicks = msToTicks(delayMs);
57-        long intervalTicks = Math.max(1, msToTicks(intervalMs));
58-        long id = NEXT_TASK_ID.getAndIncrement();
59-        TASKS.put(id, new ScheduledTask(action, delayTicks, intervalTicks));
60-        return id;
61-    }
62-
63:    public static void cancel(long taskId) {
64-        ScheduledTask task = TASKS.remove(taskId);
65-        if (task != null) {
66-            task.cancelled = true;
67-        }
68-    }
69-
70-    public static boolean isActive(long taskId) {
71-        return TASKS.containsKey(taskId);
72-    }
73-
74-    public static void shutdown() {
75-        int count = TASKS.size();
76-        TASKS.values().forEach(t -> t.cancelled = true);
77-        TASKS.clear();
78-        if (count > 0 && net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
79-            LOGGER.info("TickScheduler shutdown - cleared {} pending tasks", count);
80-        }
81-    }
82-
83-    @SubscribeEvent
84:    public static void onServerTick(TickEvent.ServerTickEvent event) {
85-        if (event.phase != TickEvent.Phase.END) return;
86-        if (TASKS.isEmpty()) return;
87-
88-        Iterator<Map.Entry<Long, ScheduledTask>> it = TASKS.entrySet().iterator();
89-        while (it.hasNext()) {
90-            Map.Entry<Long, ScheduledTask> entry = it.next();
91-            ScheduledTask task = entry.getValue();
92-
93-            if (task.cancelled) {
94-                it.remove();
95-                continue;
96-            }
97-
98-            task.ticksRemaining--;
99-
100-            if (task.ticksRemaining <= 0) {
101-                try {
102-                    task.action.run();
103-                } catch (Exception e) {
104-                    LOGGER.error("TickScheduler task {} threw an exception", entry.getKey(), e);
105-                }
106-
107-                if (task.cancelled) {
108-                    it.remove();
109-                } else if (task.intervalTicks > 0) {
110-                    task.ticksRemaining = task.intervalTicks;
111-                } else {
112-                    it.remove();
113-                }
114-            }
115-        }
116-    }
117-
118-    private static long msToTicks(long ms) {
119-        return Math.max(1, ms / 50);
120-    }
121-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 487ms:
1201-        }
1202-
1203-        // Fallback: Colony owner even if they didn't participate
1204-        return owner;
1205-    }
1206-
1207-    /**
1208-     * Sends a message to all war participants only.
1209-     * 
1210-     * @param war     The war data
1211-     * @param message The message to send
1212-     */
1213-    private static void sendMessageToWarParticipants(WarData war, Component message) {
1214-        Set<UUID> allParticipants = new HashSet<>();
1215-        allParticipants.addAll(war.getAttackerLives().keySet());
1216-        allParticipants.addAll(war.getDefenderLives().keySet());
1217-
1218-        for (UUID participantUUID : allParticipants) {
1219-            ServerPlayer participant = war.getColony().getWorld().getServer().getPlayerList()
1220-                    .getPlayer(participantUUID);
1221-            if (participant != null) {
1222-                participant.sendSystemMessage(message);
1223-            }
1224-        }
1225-    }
1226-
1227-    /**
1228-     * Transfers a colony's deed to a new owner, OR routes the action through
1229-     * the appropriate fallback per the Siege SMP ruleset.
1230-     *
1231-     * @return true if the deed actually moved; false if the transfer was blocked
1232-     *         (e.g. primary colony protection), vassalized as fallback, or failed
1233-     *         for any other reason. Callers MUST inspect this so they don't
1234-     *         broadcast "permanently claimed" when the deed never moved.
1235-     */
1236-    public static boolean transferOwnership(IColony colony, UUID newOwnerUUID) {
1237-        if (colony == null) {
1238-            return false;
1239-        }
1240-        if (colony.getWorld() == null || colony.getWorld().getServer() == null) {
1241-            return false;
1242-        }
1243-
1244-        // Siege SMP ruleset: primary colonies are protected from ownership transfer
1245-        // by default. Fall back to vassalization if enabled, so the war still has
1246-        // a meaningful consequence for the loser.
1247-        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
1248-            WARSYSTEM_LOGGER.info("Ownership transfer denied for colony {}: {}",
1249-                    colony.getID(),
1250-                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
1251-            if (TaxConfig.isWarVassalizationEnabled()) {
1252-                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
1253-                int durationHours = TaxConfig.getWarVassalizationDurationHours();
1254-                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
1255-                        colony, newOwnerUUID, tributePercent, durationHours);
1256-                if (vassalized) {
1257-                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
1258-                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
1259-                    WarData war = ACTIVE_WARS.get(colony.getID());
1260-                    Component msg = Component.literal(colony.getName()
1261-                            + " is a Primary colony â€” vassalized instead of conquered.")
1262-                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
1263-                    sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
1264-                }
1265-            }
1266-            return false;
1267-        }
1268-
1269-        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
1270-        if (newOwner == null) {
1271-            return false;
1272-        }
1273-        if (colony.getPermissions().setOwner(newOwner)) {
1274-            colony.markDirty();
1275-            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
1276-                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));
1277-            WarData war = ACTIVE_WARS.get(colony.getID());
1278-            sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
1279-            return true;
1280-        } else {
1281-            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
1282-            return false;
1283-        }
1284-    }
1285-
1286:    public static void endWar(IColony colony) {
1287-        if (colony == null) return;
1288-        // Finding 10: make endWar idempotent. Atomically remove the WarData from
1289-        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
1290-        // different code path), bail out â€” re-running the rest of this method
1291-        // would double-fire demotions, history records, treasury cleanup, etc.
1292-        WarData warData = ACTIVE_WARS.remove(colony.getID());
1293-        if (warData == null) {
1294-            // Already ended â€” nothing to do. (Previously this code re-ran all
1295-            // cleanup with warData == null, producing best-effort no-ops scattered
1296-            // with NPE risk.)
1297-            return;
1298-        }
1299-
1300-        // Remove resistance effects from guards in both colonies
1301-        if (warData != null) {
1302-            GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getColony());
1303-            if (warData.getAttackerColony() != null) {
1304-                GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getAttackerColony());
1305-            }
1306-
1307-            // Clean up militia system for both colonies
1308-            cleanupWarMilitiaSystem(warData);
1309-
1310-            // Restore all explosion-damaged blocks ledgered for this war.
1311-            // Bug #8 fix: previously the ledger only accumulated and never restored.
1312-            try {
1313-                if (warData.getColony() != null && warData.getColony().getWorld() != null) {
1314-                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
1315-                            warData.getWarID(), warData.getColony().getWorld());
1316-                }
1317-            } catch (Exception e) {
1318-                WARSYSTEM_LOGGER.error("Failed to restore war block ledger for war {}", warData.getWarID(), e);
1319-            }
1320-
1321-            // Drop any Town Hall demolition hit state so it doesn't leak between wars.
1322-            try {
1323-                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
1324-            } catch (Exception ignored) {}
1325-
1326-            // Drop any Plant-the-Banner capture state for the same reason.
1327-            try {
1328-                net.machiavelli.minecolonytax.siege.PlantTheBannerObjective.onWarEnded(warData.getWarID());
1329-            } catch (Exception ignored) {}
1330-
1331-            // Despawn militia-upgrade reinforcements (NOT victory-counted, just combat extenders).
1332-            try {
1333-                net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(warData.militiaSupport);
1334-            } catch (Exception e) {
1335-                WARSYSTEM_LOGGER.warn("Failed to despawn war militia: {}", e.getMessage());
1336-            }
1337-        }
1338-
1339-        // Disable war actions for both sides
1340-        setWarInteractionPermissions(colony, false);
1341-
1342-        // Also disable for attacker colony if it exists
1343-        if (warData != null && warData.getAttackerColony() != null) {
1344-            setWarInteractionPermissions(warData.getAttackerColony(), false);
1345-        }
1346-
1347-        // Demote war participants out of the Hostile rank on both colonies.
1348-        // Attackers were assigned hostile on the defender colony; defenders on the attacker colony.
1349-        if (warData != null) {
1350-            if (warData.getAttackerLives() != null) {
1351-                demoteParticipantsFromHostile(colony, warData.getAttackerLives().keySet());
1352-            }
1353-            if (warData.getAttackerColony() != null && warData.getDefenderLives() != null) {
1354-                demoteParticipantsFromHostile(warData.getAttackerColony(), warData.getDefenderLives().keySet());
1355-            }
1356-        }
1357-
1358-        // (Removed from ACTIVE_WARS at the top of this method as part of the
1359-        // Finding 10 idempotency fix â€” no further read/remove needed.)
1360-
1361-        // Restore Hostile rank to pre-war state now that the war is no longer active
1362-        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
1363-        if (warData != null && warData.getAttackerColony() != null) {
1364-            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(warData.getAttackerColony());
1365-        }
1366-
1367-        // Remove War Exhaustion status and start recovery period
1368-        net.machiavelli.minecolonytax.economy.WarExhaustionManager.removeWarStatus(colony.getID());
1369-        if (warData != null && warData.getAttackerColony() != null) {
1370-            net.machiavelli.minecolonytax.economy.WarExhaustionManager
1371-                    .removeWarStatus(warData.getAttackerColony().getID());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 465ms:
467-            // Clean up Neutral rank attack permissions that claiming raids grant
468-            Rank neutral = perms.getRankNeutral();
469-            perms.setPermission(neutral, Action.HURT_CITIZEN, false);
470-            perms.setPermission(neutral, Action.ATTACK_CITIZEN, false);
471-            perms.setPermission(neutral, Action.HURT_VISITOR, false);
472-            perms.setPermission(neutral, Action.ATTACK_ENTITY, false);
473-            perms.setPermission(neutral, Action.SHOOT_ARROW, false);
474-            perms.setPermission(neutral, Action.THROW_POTION, false);
475-            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, false);
476-            perms.setPermission(neutral, Action.FILL_BUCKET, false);
477-        } catch (Exception e) {
478-            WARSYSTEM_LOGGER.debug("Error restoring claiming permissions for colony {}", colony.getID(), e);
479-        }
480-    }
481-
482-    /**
483-     * Demote a set of players from the Hostile rank to Neutral on the given colony.
484-     * Called at war end to clean up rank assignments made by assignWarParticipantRanks().
485-     * Skips the colony owner and players who are not currently in the Hostile rank.
486-     */
487-    public static void demoteParticipantsFromHostile(IColony colony, java.util.Set<UUID> participants) {
488-        if (colony == null || colony.getWorld() == null || participants == null) return;
489-        IPermissions perms = colony.getPermissions();
490-        Rank neutral = perms.getRankNeutral();
491-        UUID colonyOwner = perms.getOwner();
492-
493-        for (UUID uuid : participants) {
494-            if (uuid.equals(colonyOwner)) continue; // Never demote the colony owner
495-            try {
496-                Rank current = perms.getRank(uuid);
497-                if (current != null && current.isHostile()) {
498-                    perms.setPlayerRank(uuid, neutral, colony.getWorld());
499-                    if (TaxConfig.isDebugLogging())
500-                        WARSYSTEM_LOGGER.debug("Demoted {} from Hostile to Neutral on colony {} after war end.", uuid, colony.getName());
501-                }
502-            } catch (Exception e) {
503-                WARSYSTEM_LOGGER.warn("Failed to demote player {} from hostile rank on colony {} at war end", uuid, colony.getName(), e);
504-            }
505-        }
506-    }
507-
508-    public static void updateBossBar(WarData war) {
509-        long now = System.currentTimeMillis();
510-        if (now < war.getJoinPhaseEndTime()) {
511-            long remainingMillis = war.getJoinPhaseEndTime() - now;
512-            String timeStr = String.format("%02d:%02d", remainingMillis / 60000, (remainingMillis / 1000) % 60);
513-            String joinText = Component.translatable("war.siege.status", war.getColony().getName(), timeStr)
514-                    .getString();
515-            war.bossEvent.setName(Component.literal(joinText));
516-            long joinDuration = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
517-            war.bossEvent.setProgress((float) remainingMillis / joinDuration);
518-            if (war.alliesBossEvent != null) {
519-                war.alliesBossEvent.setName(Component.literal(joinText));
520-                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
521-            }
522-        } else {
523-            long elapsedSeconds = (now - war.warStartTime) / 1000;
524-            long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
525-            long remainingSeconds = Math.max(0, warDurationSeconds - elapsedSeconds);
526-            int attackerLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
527-            int defenderLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
528-            String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
529-            String warText = "Â§6Â§lWar for " + war.getColony().getName() +
530-                    " - Time Remaining: " + timeStr +
531-                    " | Attackers: " + attackerLives +
532-                    " | Defenders: " + defenderLives;
533-            war.bossEvent.setName(Component.literal(warText));
534-            war.bossEvent.setProgress((float) remainingSeconds / warDurationSeconds);
535-            if (war.alliesBossEvent != null) {
536-                war.alliesBossEvent.removeAllPlayers();
537-                war.alliesBossEvent.setVisible(false);
538-            }
539-        }
540-    }
541-
542:    public static void finalizeWarStart(WarData war) {
543-        int attackerPlayerCount = war.getAttackerLives().size();
544-        int defenderPlayerCount = war.getDefenderLives().size();
545-
546-        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
547-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
548-                Component cancelMsg = Component.literal("War cancelled due to lack of participants.")
549-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
550-                broadcastToServer(cancelMsg);
551-            }
552-            endWar(war.getColony());
553-            return;
554-        }
555-
556-        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
557-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
558-                Component ratioMsg = Component.literal(
559-                        "Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
560-                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
561-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
562-                broadcastToServer(ratioMsg);
563-            }
564-            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
565-            return;
566-        }
567-
568-        war.bossEvent.removeAllPlayers();
569-        war.getAttackerLives().keySet().forEach(uuid -> {
570-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
571-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
572-                if (p != null)
573-                    war.bossEvent.addPlayer(p);
574-            }
575-        });
576-        war.getDefenderLives().keySet().forEach(uuid -> {
577-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
578-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
579-                if (p != null)
580-                    war.bossEvent.addPlayer(p);
581-            }
582-        });
583-
584-        if (war.alliesBossEvent != null) {
585-            war.alliesBossEvent.removeAllPlayers();
586-            war.alliesBossEvent.setVisible(false);
587-        }
588-
589-        war.getAttackerLives().keySet().forEach(uuid -> {
590-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
591-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
592-                if (p != null)
593-                    assignWarGroup(p);
594-            }
595-        });
596-        war.getDefenderLives().keySet().forEach(uuid -> {
597-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
598-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
599-                if (p != null)
600-                    assignWarGroup(p);
601-            }
602-        });
603-
604-        war.warStartTime = System.currentTimeMillis();
605-        war.setStatus(WarData.WarStatus.INWAR);
606-        updateBossBar(war);
607-        // Apply glow to both defender and attacker guards for clear visibility
608-        applyGuardGlow(war.getColony());
609-        if (war.getAttackerColony() != null) {
610-            applyGuardGlow(war.getAttackerColony());
611-        }
612-        applyWarGlowToParticipants(war);
613-
614-        // Step 11 â€” when experimental objectives are enabled, give each attacker
615-        // a Siege Banner at INWAR transition. They can plant it inside the
616-        // defender's Town Hall to start the capture timer.
617-        if (TaxConfig.isExperimentalSiegeObjectivesEnabled()) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 495ms:
1-package net.machiavelli.minecolonytax.siege;
2-
3-import com.minecolonies.api.colony.IColony;
4-import com.minecolonies.api.colony.buildings.IBuilding;
5-import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
6-import net.machiavelli.minecolonytax.MineColonyTax;
7-import net.machiavelli.minecolonytax.TaxConfig;
8-import net.machiavelli.minecolonytax.WarSystem;
9-import net.machiavelli.minecolonytax.data.WarData;
10-import net.machiavelli.minecolonytax.util.TickScheduler;
11-import net.minecraft.ChatFormatting;
12-import net.minecraft.core.BlockPos;
13-import net.minecraft.network.chat.Component;
14-import net.minecraft.server.MinecraftServer;
15-import net.minecraft.server.level.ServerBossEvent;
16-import net.minecraft.server.level.ServerPlayer;
17-import net.minecraft.world.BossEvent;
18-import net.minecraft.world.entity.LivingEntity;
19-import net.minecraft.world.level.Level;
20-import net.minecraftforge.event.level.BlockEvent;
21-import net.minecraftforge.eventbus.api.EventPriority;
22-import net.minecraftforge.eventbus.api.SubscribeEvent;
23-import net.minecraftforge.fml.common.Mod;
24-import org.apache.logging.log4j.LogManager;
25-import org.apache.logging.log4j.Logger;
26-
27-import java.util.HashMap;
28-import java.util.Map;
29-import java.util.UUID;
30-import java.util.concurrent.ConcurrentHashMap;
31-
32-/**
33- * Experimental "Plant the Banner" capture-and-hold victory objective for full wars.
34- *
35- * Player flow:
36- *  1. Attacker carries a Siege Banner item ({@code minecolonytax:siege_banner})
37- *     into the defender colony.
38- *  2. Right-click to place. Place is validated to be inside the Town Hall building.
39- *     Outside тЖТ cancelled with a chat message.
40- *  3. Successful placement тЖТ a war-scoped boss bar appears for both sides showing
41- *     remaining hold time (default 10 min via {@code BannerCaptureMinutes}).
42- *  4. Defenders can break the banner to cancel the capture. The attacker can
43- *     re-plant ONCE ({@code BannerMaxReplants = 1}); after that the capture path
44- *     is locked for this war.
45- *  5. If the timer reaches zero, attacker victory тАФ triggers the same
46- *     WarSystem.checkForVictory path used by Town Hall demolition.
47- *
48- * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
49- * banner item is still registered but the place handler treats placement as
50- * a vanilla block place (no boss bar, no win check).
51- */
52-@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
53-public final class PlantTheBannerObjective {
54-
55-    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);
56-
57-    /** Active banner captures, keyed by warId. */
58-    private static final Map<UUID, BannerCaptureState> ACTIVE_CAPTURES = new ConcurrentHashMap<>();
59-    /** Per-war replant counter so the limit can't be bypassed by serial places. */
60-    private static final Map<UUID, Integer> REPLANT_COUNT = new ConcurrentHashMap<>();
61-
62-    private static final class BannerCaptureState {
63-        final UUID warId;
64-        final BlockPos bannerPos;
65-        final UUID attackerUUID;
66-        final long expiresAtMs;
67-        final ServerBossEvent bossEvent;
68-        final long taskId;
69-
70-        BannerCaptureState(UUID warId, BlockPos pos, UUID attackerUUID, long expiresAtMs,
71-                           ServerBossEvent bossEvent, long taskId) {
72-            this.warId = warId;
73-            this.bannerPos = pos;
74-            this.attackerUUID = attackerUUID;
75-            this.expiresAtMs = expiresAtMs;
76-            this.bossEvent = bossEvent;
77-            this.taskId = taskId;
78-        }
79-    }
80-
81-    private PlantTheBannerObjective() {}
82-
83-    /**
84-     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
85-     * Validates: feature flag on, placer is attacker in an active war, position is
86-     * inside the defender's Town Hall building. If valid тЖТ start capture.
87-     */
88:    public static void onBannerPlaced(Level level, BlockPos pos, LivingEntity placer) {
89-        if (level == null || level.isClientSide()) return;
90-        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
91-        if (!(placer instanceof ServerPlayer attacker)) return;
92-
93-        // Find war where this player is attacker.
94-        WarData war = findWarForAttacker(attacker.getUUID());
95-        if (war == null) {
96-            attacker.sendSystemMessage(Component.literal(
97-                    "You're not an attacker in any active war тАФ the Siege Banner does nothing here.")
98-                    .withStyle(ChatFormatting.YELLOW));
99-            return;
100-        }
101-
102-        // Hard reject if also a defender тАФ same guard pattern as TownHallDemolitionObjective.
103-        if (war.getDefenderLives().containsKey(attacker.getUUID())) {
104-            attacker.sendSystemMessage(Component.literal(
105-                    "You're listed on both sides of this war тАФ banner placement refused.")
106-                    .withStyle(ChatFormatting.RED));
107:            destroyBannerSilently(level, pos);
108-            return;
109-        }
110-
111-        IColony defenderColony = war.getColony();
112-        if (defenderColony == null) return;
113-
114-        ITownHall townHall = findTownHall(defenderColony);
115-        if (townHall == null) {
116-            attacker.sendSystemMessage(Component.literal(
117-                    "No Town Hall found in the defender colony тАФ banner cannot be planted.")
118-                    .withStyle(ChatFormatting.RED));
119:            destroyBannerSilently(level, pos);
120-            return;
121-        }
122-
123-        if (!((IBuilding) townHall).isInBuilding(pos)) {
124-            attacker.sendSystemMessage(Component.literal(
125-                    "The Siege Banner must be planted INSIDE the Town Hall building.")
126-                    .withStyle(ChatFormatting.RED));
127:            destroyBannerSilently(level, pos);
128-            return;
129-        }
130-
131-        // Replant limit check (codex spec: 1 replant max per war).
132-        int replantsUsed = REPLANT_COUNT.getOrDefault(war.getWarID(), 0);
133-        int maxReplants = 1; // could be a config; matches the design doc
134-        if (replantsUsed > maxReplants) {
135-            attacker.sendSystemMessage(Component.literal(
136-                    "The capture path is locked тАФ you have exhausted your re-plants for this war.")
137-                    .withStyle(ChatFormatting.RED));
138:            destroyBannerSilently(level, pos);
139-            return;
140-        }
141-
142-        // Already-active capture for this war? Refuse stacking.
143-        if (ACTIVE_CAPTURES.containsKey(war.getWarID())) {
144-            attacker.sendSystemMessage(Component.literal(
145-                    "A Siege Banner is already active for this war.")
146-                    .withStyle(ChatFormatting.YELLOW));
147:            destroyBannerSilently(level, pos);
148-            return;
149-        }
150-
151-        startCapture(war, pos, attacker);
152-    }
153-
154:    private static void startCapture(WarData war, BlockPos pos, ServerPlayer attacker) {
155-        int holdMinutes = TaxConfig.getBannerCaptureMinutesOrDefault();
156-        long now = System.currentTimeMillis();
157-        long expiresAt = now + holdMinutes * 60_000L;
158-
159-        ServerBossEvent bossEvent = new ServerBossEvent(
160-                Component.literal("SIEGE BANNER PLANTED тАФ " + holdMinutes + ":00 remaining")
161-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
162-                BossEvent.BossBarColor.YELLOW,
163-                BossEvent.BossBarOverlay.PROGRESS);
164-        bossEvent.setProgress(1.0f);
165-
166-        // Visible only to war participants (attackers + defenders).
167-        addWarParticipantsToBossBar(war, attacker.getServer(), bossEvent);
168-
169-        // Tick-driven progress update + expiry check.
170-        final long taskId = TickScheduler.scheduleRepeating(() -> {
171-            BannerCaptureState st = ACTIVE_CAPTURES.get(war.getWarID());
172-            if (st == null) return; // already cleaned up
173-            long left = st.expiresAtMs - System.currentTimeMillis();
174-            if (left <= 0) {
175-                onCaptureExpired(war, st);
176-                return;
177-            }
178-            float progress = Math.max(0f, Math.min(1f, (float) left / (float) (holdMinutes * 60_000L)));
179-            long secs = left / 1000;
180-            String label = String.format("SIEGE BANNER тАФ %d:%02d remaining", secs / 60, secs % 60);
181-            st.bossEvent.setProgress(progress);
182-            st.bossEvent.setName(Component.literal(label).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
183-        }, 1000, 1000);
184-
185-        BannerCaptureState state = new BannerCaptureState(
186-                war.getWarID(), pos, attacker.getUUID(), expiresAt, bossEvent, taskId);
187-        ACTIVE_CAPTURES.put(war.getWarID(), state);
188-
189-        // Broadcast event-flavour message to participants.
190-        Component banner = Component.literal("Siege Banner planted by ")
191-                .withStyle(ChatFormatting.GOLD)
192-                .append(Component.literal(attacker.getName().getString()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
193-                .append(Component.literal(" тАФ defend or break it before " + holdMinutes + " minutes!")
194-                        .withStyle(ChatFormatting.GOLD));
195-        broadcastToWarParticipants(war, attacker.getServer(), banner);
196-        LOGGER.info("Siege Banner planted at {} by {} for war {}", pos, attacker.getUUID(), war.getWarID());
197-    }
198-
199-    /** Defender broke the banner тАФ cancel capture, allow one re-plant if config-allowed. */
200-    @SubscribeEvent(priority = EventPriority.NORMAL)
201:    public static void onBlockBreak(BlockEvent.BreakEvent event) {
202-        if (event.getLevel().isClientSide()) return;
203-        if (!(event.getState().getBlock() instanceof SiegeBannerBlock)) return;
204-        BlockPos pos = event.getPos();
205-
206-        // Find any active capture at this position.
207-        for (Map.Entry<UUID, BannerCaptureState> e : ACTIVE_CAPTURES.entrySet()) {
208-            BannerCaptureState st = e.getValue();
209-            if (!st.bannerPos.equals(pos)) continue;
210-            UUID warId = e.getKey();
211-            WarData war = warById(warId);
212-            if (war == null) {
213-                clearCapture(warId);
214-                return;
215-            }
216-            // Cancel capture, bump replant counter so the attacker has limited re-plants.
217-            int replants = REPLANT_COUNT.getOrDefault(warId, 0) + 1;
218-            REPLANT_COUNT.put(warId, replants);
219-            clearCapture(warId);
220-
221-            Component msg = Component.literal("The Siege Banner has been broken by ")
222-                    .withStyle(ChatFormatting.GREEN)
223-                    .append(Component.literal(event.getPlayer().getName().getString())
224-                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
225-                    .append(Component.literal(" тАФ capture cancelled.").withStyle(ChatFormatting.GREEN));
226-            if (event.getPlayer() instanceof ServerPlayer sp && sp.getServer() != null) {
227-                broadcastToWarParticipants(war, sp.getServer(), msg);
228-            }
229-            LOGGER.info("Siege Banner at {} broken for war {} (replants used: {})", pos, warId, replants);
230-            return;
231-        }
232-    }
233-
234-    /** Capture timer reached zero тАФ attacker wins via the same path as Town Hall demolition. */
235-    private static void onCaptureExpired(WarData war, BannerCaptureState st) {
236-        clearCapture(war.getWarID());
237-
238-        // Guard symmetric to TownHallDemolitionObjective: refuse if defender-win
239-        // condition is already true (legacy resolver would flip the result).
240-        boolean hasAttackers = !war.getAttackerLives().isEmpty();
241-        boolean allAttackersDead = hasAttackers
242-                && war.getAttackerLives().values().stream().allMatch(v -> v <= 0);
243-        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;
244-        boolean defendersWouldWin =
245-                (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);
246-        if (defendersWouldWin) {
247-            LOGGER.info("Banner capture refused at expiry: defender-win condition is already true for war {}",
248-                    war.getWarID());
249-            return;
250-        }
251-
252-        // Broadcast + trigger attacker victory.
253-        MinecraftServer server = null;
254-        if (war.getColony() != null && war.getColony().getWorld() != null) {
255-            server = war.getColony().getWorld().getServer();
256-        }
257-        if (server != null) {
258-            Component victoryMsg = Component.literal(
259-                    "EXPERIMENTAL VICTORY тАФ Siege Banner held to completion!")
260-                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
261-            broadcastToWarParticipants(war, server, victoryMsg);
262-        }
263-
264-        // Zero defender lives so checkForVictory resolves as attacker win.
265-        for (Map.Entry<UUID, Integer> e : new HashMap<>(war.getDefenderLives()).entrySet()) {
266-            war.getDefenderLives().put(e.getKey(), 0);
267-        }
268-        war.remainingDefenderGuards = 0;
269-        try {
270-            WarSystem.checkForVictory(war);
271-        } catch (Exception ex) {
272-            LOGGER.error("Failed to trigger banner-capture victory for war {}", war.getWarID(), ex);
273-        }
274-    }
275-
276-    /** Cleanup hook from WarSystem.endWar тАФ drop per-war state. */
277-    public static void onWarEnded(UUID warId) {
278-        clearCapture(warId);
279-        REPLANT_COUNT.remove(warId);
280-    }
281-
282:    private static void clearCapture(UUID warId) {
283-        BannerCaptureState st = ACTIVE_CAPTURES.remove(warId);
284-        if (st == null) return;
285-        try { TickScheduler.cancel(st.taskId); } catch (Exception ignored) {}
286-        try {
287-            if (st.bossEvent != null) {
288-                st.bossEvent.removeAllPlayers();
289-                st.bossEvent.setVisible(false);
290-            }
291-        } catch (Exception ignored) {}
292-    }
293-
294:    private static void destroyBannerSilently(Level level, BlockPos pos) {
295-        try {
296-            level.removeBlock(pos, false);
297-        } catch (Exception ignored) {}
298-    }
299-
300-    private static WarData findWarForAttacker(UUID attackerUUID) {
301-        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
302-            if (war.getAttackerLives().containsKey(attackerUUID)) return war;
303-        }
304-        return null;
305-    }
306-
307-    private static WarData warById(UUID warId) {
308-        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
309-            if (warId.equals(war.getWarID())) return war;
310-        }
311-        return null;
312-    }
313-
314-    private static ITownHall findTownHall(IColony colony) {
315-        try {
316-            for (IBuilding b : net.machiavelli.minecolonytax.compat.ColonyBuildingUtil.getBuildings(colony)) {
317-                if (b instanceof ITownHall th) return th;
318-            }
319-        } catch (Exception ignored) {}
320-        return null;
321-    }
322-
323-    private static void addWarParticipantsToBossBar(WarData war, MinecraftServer server, ServerBossEvent bossEvent) {
324-        if (server == null) return;
325-        for (UUID uuid : war.getAttackerLives().keySet()) {
326-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
327-            if (p != null) bossEvent.addPlayer(p);
328-        }
329-        for (UUID uuid : war.getDefenderLives().keySet()) {
330-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
331-            if (p != null) bossEvent.addPlayer(p);
332-        }
333-    }
334-
335-    private static void broadcastToWarParticipants(WarData war, MinecraftServer server, Component msg) {
336-        if (server == null) return;
337-        for (UUID uuid : war.getAttackerLives().keySet()) {
338-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
339-            if (p != null) p.sendSystemMessage(msg);
340-        }
341-        for (UUID uuid : war.getDefenderLives().keySet()) {
342-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
343-            if (p != null) p.sendSystemMessage(msg);
344-        }
345-    }
346-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fБr 
Kerntypen unterstБtzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 500ms:
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-198-                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-199-            
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-200-            WarSystem.checkForVictory(war);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-201-            WarSystem.updateBossBar(war);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-202-        } else if (currentLives == 1) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-203-            player.sendSystemMessage(Component.literal("WARNING: This is your LAST LIFE!")
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-204-                    .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-205-            
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-206-            // Send the info message about being on last life
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-207-            String keepInventoryMessage = TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get() 
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-208-                ? "Â§eÂ§lWarning: Â§rÂ§eYou are on your last life! If you die, you will keep your inventory and become a spectator."
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-209-                : "Â§eÂ§lWarning: Â§rÂ§eYou are on your last life! If you die, you will lose your inventory and become a spectator.";
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-210-            
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-211-            player.sendSystemMessage(Component.literal(keepInventoryMessage));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-212-            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 0.5F);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-213-            if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-214-                player.sendSystemMessage(Component.literal("If you die, you will keep your inventory and can continue fighting as a spectator.")
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-215-                        .withStyle(style -> style.withColor(ChatFormatting.GOLD)));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-216-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-217-            
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-218-            WarSystem.updateBossBar(war);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-219-        } else {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-220-            WarSystem.updateBossBar(war);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-221-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-222-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-223-        if (war.getStatus() == WarData.WarStatus.INWAR) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-224-            player.addEffect(new MobEffectInstance(
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-225-                    net.minecraft.world.effect.MobEffects.GLOWING,
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-226-                    999999, 0,
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-227-                    false, false
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-228-            ));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-229-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-230-        player.getTags().remove("war_keep_inventory");
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-231-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-232-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-233-    @SubscribeEvent
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-234-    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-235-        if (!(event.getEntity() instanceof ServerPlayer player)) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-236-        
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-237-        UUID playerUUID = player.getUUID();
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-238-        
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-239-        WarData war = WarSystem.getActiveWarForPlayer(player);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-240-        if (war != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-241-            if (war.getAttackerLives().containsKey(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-242-                disconnectedWarParticipants.put(playerUUID, 1);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-243-            } else if (war.getDefenderLives().containsKey(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-244-                disconnectedWarParticipants.put(playerUUID, 2);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-245-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-246-            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} disconnected during active war", player.getName().getString());
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-247-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-248-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-249-        ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(playerUUID);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-250-        if (raidData != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-251-            disconnectedWarParticipants.put(playerUUID, 3);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-252-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-253-            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} disconnected during active raid", player.getName().getString());
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-254-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-255-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-256-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-257-    @SubscribeEvent
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:258:    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-259-        if (!(event.getEntity() instanceof ServerPlayer player)) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-260-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-261-        net.machiavelli.minecolonytax.db.WarStatsDB.upsertPlayerLogin(
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-262-                player.getUUID(), player.getName().getString());
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-263-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-264-        UUID playerUUID = player.getUUID();
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-265-        
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-266-        if (disconnectedWarParticipants.containsKey(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-267-            int warStatus = disconnectedWarParticipants.get(playerUUID);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-268-            if (warStatus == 1 || warStatus == 2) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-269-                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-270-                    boolean isAttacker = war.getAttackerLives().containsKey(playerUUID);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-271-                    boolean isDefender = war.getDefenderLives().containsKey(playerUUID);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-272-                    if (isAttacker || isDefender) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-273-                        if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:274:                            war.alliesBossEvent.addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-275-                        } else if (war.bossEvent != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:276:                            war.bossEvent.addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-277-                        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-278-                        player.sendSystemMessage(Component.literal("You have reconnected to an active war.")
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-279-                                .withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-280-                        if (war.getSpectators().contains(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-281-                            player.setGameMode(GameType.SPECTATOR);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-282-                        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-283-                    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-284-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-285-            } else if (warStatus == 3) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-286-                ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(playerUUID);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-287-                if (raidData != null && raidData.getBossEvent() != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-288-                    raidData.getBossEvent().addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-289-                    player.sendSystemMessage(Component.literal("You have reconnected to an active raid.")
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-290-                            .withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-291-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-292-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-293-            disconnectedWarParticipants.remove(playerUUID);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-294-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-295-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-296-        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-297-            if (raidData.isActive() && raidData.getBossEvent() != null && raidData.getColony() != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-298-                if (raidData.getColony().getPermissions().getPlayers().containsKey(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-299-                    raidData.getBossEvent().addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-300-                    player.sendSystemMessage(Component.literal("Your colony is currently being raided!")
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-301-                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-302-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-303-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-304-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-305-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-306-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-307-    @SubscribeEvent
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-308-    public static void onCitizenDeath(LivingDeathEvent event) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-309-        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-310-        var data = citizen.getCitizenData();
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-311-        if (data == null) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-312-        IColony citizenColony = data.getColony();
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-313-        if (citizenColony == null) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-314-        WarData war = WarSystem.ACTIVE_WARS.get(citizenColony.getID());
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-315-        if (war == null) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-316-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-317-            // Separate sets per side prevent collisions when both colonies share the same sequential citizen ID.
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-318-        boolean isDefenderGuard = citizenColony.equals(war.getColony())
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-319-                && war.getDefenderGuardIDs().remove(data.getId());
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-320-        boolean isAttackerGuard = !isDefenderGuard
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-321-                && citizenColony.equals(war.getAttackerColony())
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-322-                && war.getAttackerGuardIDs().remove(data.getId());
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-323-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-324-        if (isDefenderGuard || isAttackerGuard) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-325-            if (isDefenderGuard) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-326-                war.remainingDefenderGuards = Math.max(0, war.remainingDefenderGuards - 1);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-327-                for (UUID uuid : war.getDefenderLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-328-                    if (war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-329-                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-330-                        if (p != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-331-                            p.sendSystemMessage(Component.literal("Your guard has been killed!")
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-332-                                    .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-333-                        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-334-                    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-335-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-336-            } else {
--
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-513-            String joinText = Component.translatable("war.siege.status", war.getColony().getName(), timeStr)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-514-                    .getString();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-515-            war.bossEvent.setName(Component.literal(joinText));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-516-            long joinDuration = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-517-            war.bossEvent.setProgress((float) remainingMillis / joinDuration);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-518-            if (war.alliesBossEvent != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-519-                war.alliesBossEvent.setName(Component.literal(joinText));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-520-                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-521-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-522-        } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-523-            long elapsedSeconds = (now - war.warStartTime) / 1000;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-524-            long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-525-            long remainingSeconds = Math.max(0, warDurationSeconds - elapsedSeconds);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-526-            int attackerLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-527-            int defenderLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-528-            String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-529-            String warText = "Â§6Â§lWar for " + war.getColony().getName() +
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-530-                    " - Time Remaining: " + timeStr +
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-531-                    " | Attackers: " + attackerLives +
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-532-                    " | Defenders: " + defenderLives;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-533-            war.bossEvent.setName(Component.literal(warText));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-534-            war.bossEvent.setProgress((float) remainingSeconds / warDurationSeconds);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-535-            if (war.alliesBossEvent != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-536-                war.alliesBossEvent.removeAllPlayers();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-537-                war.alliesBossEvent.setVisible(false);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-538-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-539-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-540-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-541-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-542-    public static void finalizeWarStart(WarData war) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-543-        int attackerPlayerCount = war.getAttackerLives().size();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-544-        int defenderPlayerCount = war.getDefenderLives().size();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-545-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-546-        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-547-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-548-                Component cancelMsg = Component.literal("War cancelled due to lack of participants.")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-549-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-550-                broadcastToServer(cancelMsg);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-551-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-552-            endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-553-            return;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-554-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-555-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-556-        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-557-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-558-                Component ratioMsg = Component.literal(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-559-                        "Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-560-                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-561-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-562-                broadcastToServer(ratioMsg);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-563-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-564-            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-565-            return;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-566-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-567-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-568-        war.bossEvent.removeAllPlayers();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-569-        war.getAttackerLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-570-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-571-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-572-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:573:                    war.bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-574-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-575-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-576-        war.getDefenderLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-577-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-578-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-579-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:580:                    war.bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-581-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-582-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-583-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-584-        if (war.alliesBossEvent != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-585-            war.alliesBossEvent.removeAllPlayers();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-586-            war.alliesBossEvent.setVisible(false);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-587-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-588-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-589-        war.getAttackerLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-590-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-591-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-592-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-593-                    assignWarGroup(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-594-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-595-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-596-        war.getDefenderLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-597-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-598-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-599-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-600-                    assignWarGroup(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-601-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-602-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-603-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-604-        war.warStartTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-605-        war.setStatus(WarData.WarStatus.INWAR);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-606-        updateBossBar(war);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-607-        // Apply glow to both defender and attacker guards for clear visibility
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-608-        applyGuardGlow(war.getColony());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-609-        if (war.getAttackerColony() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-610-            applyGuardGlow(war.getAttackerColony());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-611-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-612-        applyWarGlowToParticipants(war);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-613-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-614-        // Step 11 â€” when experimental objectives are enabled, give each attacker
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-615-        // a Siege Banner at INWAR transition. They can plant it inside the
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-616-        // defender's Town Hall to start the capture timer.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-617-        if (TaxConfig.isExperimentalSiegeObjectivesEnabled()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-618-            try {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-619-                if (war.getColony() != null && war.getColony().getWorld() != null
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-620-                        && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-621-                    var server = war.getColony().getWorld().getServer();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-622-                    var bannerItem = net.machiavelli.minecolonytax.siege.ModSiegeBlocks.SIEGE_BANNER_ITEM.get();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-623-                    for (UUID attackerUUID : war.getAttackerLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-624-                        ServerPlayer p = server.getPlayerList().getPlayer(attackerUUID);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-625-                        if (p != null && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(bannerItem))) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-626-                            p.getInventory().add(new net.minecraft.world.item.ItemStack(bannerItem, 1));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-627-                        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-628-                    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-629-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-630-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-631-                WARSYSTEM_LOGGER.warn("Failed to hand Siege Banners to attackers: {}", e.getMessage());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-632-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-633-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-634-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-635-        // Militia upgrade reinforcements â€” spawn on BOTH sides if either colony
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-636-        // has the upgrade. Defender side primarily (per design), attacker side
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-637-        // optionally so an upgraded attacker colony also gets the boost.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-638-        // Idempotent â€” re-entry checks the existing set is empty first.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-639-        if (war.militiaSupport.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-640-            try {
--
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2282-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2283-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2284-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2285-    public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2286-        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2287-                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2288-                : null;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2289-        FtbTeamsCompat.TeamHandle defenderTeam = FTB_TEAMS_INSTALLED
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2290-                ? FtbTeamsCompat.getTeamForPlayer(owner.getUUID()).orElse(null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2291-                : null;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2292-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2293-        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2294-                .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2295-                .findFirst().orElse(null);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2296-        if (attackerColony == null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2297-            attacker.sendSystemMessage(Component.literal("You must own a colony to declare war.")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2298-                    .withStyle(style -> style.withColor(ChatFormatting.RED)));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2299-            return;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2300-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2301-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2302-        initiateWar(attacker, owner.getUUID(), attackerTeam, defenderTeam, colony, attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2303-        WarData war = getActiveWarForPlayer(owner);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2304-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2305-        int configuredMinutes = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2306-        if (TaxConfig.isDebugLogging()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2307-            WARSYSTEM_LOGGER.info("[DEBUG] JOIN_PHASE_DURATION_MINUTES config value: {} minutes", configuredMinutes);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2308-            WARSYSTEM_LOGGER.info("[DEBUG] Config spec: {}", TaxConfig.CONFIG.getClass().getName());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2309-            WARSYSTEM_LOGGER.info("[DEBUG] Config default value: {}",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2310-                    TaxConfig.JOIN_PHASE_DURATION_MINUTES.getDefault());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2311-            WARSYSTEM_LOGGER.info("[DEBUG] Config is loaded: {}", TaxConfig.CONFIG.isLoaded());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2312-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2313-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2314-        if (ServerLifecycleHooks.getCurrentServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2315-            // Get the time remaining in a readable format
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2316-            String timeRemaining = configuredMinutes + " minutes";
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2317-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2318-            // Send join phase announcement only to war participants
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2319-            Component joinPhaseMsg = Component.translatable("war.join.phase.declared", colony.getName(), timeRemaining);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2320-            sendNotificationToWarParticipants(colony, attackerColony, joinPhaseMsg);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2321-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2322-        WARSYSTEM_LOGGER.info("Join phase started for colony {}. Waiting for participants for {} seconds.",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2323-                colony.getName(), configuredMinutes * 60);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2324-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2325-        if (war == null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2326-            return;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2327-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2328-        long joinDurationMillis = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2329-        war.setJoinPhaseEndTime(System.currentTimeMillis() + joinDurationMillis);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2330-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2331-        war.alliesBossEvent = new ServerBossEvent(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2332-                Component.literal("Joining War - " + colony.getName()),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2333-                BossEvent.BossBarColor.YELLOW,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2334-                BossEvent.BossBarOverlay.PROGRESS);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2335-        war.alliesBossEvent.setProgress(1.0f);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2336-        war.alliesBossEvent.setVisible(true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2337-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2338-        if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2339-            ServerPlayer ownerPlayer = colony.getWorld().getServer().getPlayerList()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2340-                    .getPlayer(war.getColony().getPermissions().getOwner());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2341-            if (ownerPlayer != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2342:                war.bossEvent.addPlayer(ownerPlayer);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2343-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2344-            for (UUID uuid : war.getAttackerLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2345-                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2346-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2347:                    war.bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2348-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2349-            for (UUID uuid : war.getDefenderLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2350-                ServerPlayer p = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2351-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2352:                    war.bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2353-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2354-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2355-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2356-        Component joinAnnouncement = Component.empty()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2357-                .append(JOIN_MSG)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2358-                .append(Component.literal(" "))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2359-                .append(LEAVE_MSG);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2360-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2361-        if (FTB_TEAMS_INSTALLED) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2362-            if (attackerTeam != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2363-                sendNotificationToColonyParticipants(attackerColony, joinAnnouncement);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2364-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2365-            if (defenderTeam != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2366-                sendNotificationToColonyParticipants(colony, joinAnnouncement);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2367-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2368-        } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2369-            sendNotificationToColonyParticipants(attackerColony, joinAnnouncement);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2370-            sendNotificationToColonyParticipants(colony, joinAnnouncement);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2371-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2372-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2373-        // Calculate actual remaining time instead of total duration
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2374-        long remainingMillis = war.getJoinPhaseEndTime() - System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2375-        remainingMillis = Math.max(0, remainingMillis); // Ensure non-negative
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2376-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2377-        Component joinPhaseInfo = Component.translatable("war.siege.status", colony.getName(),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2378-                String.format("%02d:%02d", remainingMillis / (60 * 1000), (remainingMillis / 1000) % 60))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2379-                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2380-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2381-        if (FTB_TEAMS_INSTALLED) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2382-            if (attackerTeam != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2383-                sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2384-            if (defenderTeam != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2385-                sendNotificationToColonyParticipants(colony, joinPhaseInfo);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2386-        } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2387-            sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2388-            sendNotificationToColonyParticipants(colony, joinPhaseInfo);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2389-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2390-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2391-        // Add countdown sound timer for the last 6 seconds of join phase, but only if
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2392-        // join phase is at least 6 seconds long
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2393-        if (joinDurationMillis >= 6000) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2394-            final int[] secondsLeft = { 6 };
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2395-            TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2396-                try {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2397-                    if (war == null || war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2398-                        return; // Task will be cleaned up when war starts or ends
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2399-                    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2400-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2401-                    // Play countdown sound to all war participants
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2402-                    Set<UUID> allParticipants = new HashSet<>();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2403-                    allParticipants.addAll(war.getAttackerLives().keySet());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2404-                    allParticipants.addAll(war.getDefenderLives().keySet());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2405-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2406-                    // Only play sound if there are participants
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2407-                    if (!allParticipants.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2408-                        // Play countdown sound using Minecraft's bell sound
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2409-                        for (UUID uuid : allParticipants) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2410-                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2411-                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-2412-                                    : null;
--
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3017-        initiateOfflineOutpostWar(attacker, targetColony, attackerColony, attackerTeam);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3018-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3019-        return 1;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3020-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3021-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3022-    /**
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3023-     * Initiates a war against an outpost when the owner is offline.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3024-     * Special handling: No defender players, only guards defend.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3025-     */
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3026-    private static void initiateOfflineOutpostWar(ServerPlayer attacker, IColony targetColony,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3027-            IColony attackerColony, FtbTeamsCompat.TeamHandle attackerTeam) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3028-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3029-        int attackerGuards = countGuards(attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3030-        int defenderGuards = countGuards(targetColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3031-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3032-        WarData warData = new WarData(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3033-                attacker.getUUID(),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3034-                targetColony.getPermissions().getOwner(), // Defender owner UUID (offline)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3035-                attackerTeam != null ? FtbTeamsCompat.getTeamId(attackerTeam) : null,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3036-                null, // No defender team
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3037-                System.currentTimeMillis(),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3038-                null, // No boss event yet - created below
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3039-                targetColony,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3040-                attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3041-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3042-        // Mark this as an offline outpost war
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3043-        warData.setOfflineOutpostWar(true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3044-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3045-        // IMPORTANT: Set attacker guards to 0 for offline outpost wars.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3046-        // The attacker's guards are at their HOME colony, not on the battlefield.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3047-        // Without this, the victory check (attackerLives==0 && attackerGuards==0)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3048-        // would never trigger - making the attacker invincible!
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3049-        warData.remainingAttackerGuards = 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3050-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3051-        ACTIVE_WARS.put(targetColony.getID(), warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3052-        int _defStartBal = TaxManager.getStoredTaxForColonyId(targetColony.getID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3053-        HistoryManager.logWithBalance(targetColony.getID(), "WAR",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3054-                "War started â€” attacked by " + attacker.getName().getString(),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3055-                _defStartBal, _defStartBal);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3056-        if (attackerColony != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3057-            int _atkStartBal = TaxManager.getStoredTaxForColonyId(attackerColony.getID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3058-            HistoryManager.logWithBalance(attackerColony.getID(), "WAR",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3059-                    "War started â€” attacking " + targetColony.getName(),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3060-                    _atkStartBal, _atkStartBal);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3061-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3062-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3063-        // Mark defender for home-field drain advantage + schedule drain
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3064-        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(targetColony.getID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3065-        scheduleTreasuryDrain(warData, targetColony, attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3066-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3067-        // Add attacker to the war
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3068-        warData.getAttackerLives().put(attacker.getUUID(), TaxConfig.PLAYER_LIVES_IN_WAR.get());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3069-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3070-        // Create boss bar for the attacker
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3071-        warData.bossEvent = new ServerBossEvent(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3072-                Component.literal("Outpost Assault - " + targetColony.getName()),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3073-                BossEvent.BossBarColor.RED,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3074-                BossEvent.BossBarOverlay.PROGRESS);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3075-        warData.bossEvent.setProgress(1.0f);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3076-        warData.bossEvent.setVisible(true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3077:        warData.bossEvent.addPlayer(attacker);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3078-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3079-        // Set war status to IN WAR immediately (no join phase for offline attacks)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3080-        warData.setStatus(WarData.WarStatus.INWAR);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3081-        warData.warStartTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3082-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3083-        // Enable war interactions
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3084-        setWarInteractionPermissions(targetColony, true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3085-        setWarInteractionPermissions(attackerColony, true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3086-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3087-        // Assign hostile rank to attacker in target colony
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3088-        Rank hostileRank = targetColony.getPermissions().getRankHostile();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3089-        targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3090-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3091-        // Apply glow effects and resistance buffs
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3092-        // TODO: Implement applyGlowEffect and applyGuardResistance for offline outpost
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3093-        // wars
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3094-        // applyGlowEffect(attacker, targetColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3095-        // applyGuardResistance(targetColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3096-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3097-        // Start the war countdown
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3098-        startWarCountdown(warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3099-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3100-        // Notify the attacker
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3101-        attacker.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3102-                Component.literal("âš” ASSAULT BEGUN! Defeat the colony guards or hold the outpost until time expires!")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3103-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3104-        attacker.sendSystemMessage(Component
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3105-                .literal("âš  You have " + TaxConfig.PLAYER_LIVES_IN_WAR.get()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3106-                        + " lives. Lose them all and the outpost's owner wins!")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3107-                .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3108-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3109-        WARSYSTEM_LOGGER.info("Offline outpost war started: {} vs {} (defender offline)",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3110-                attackerColony.getName(), targetColony.getName());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3111-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3112-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3113-    public static int processWageWarRequestWithExtortion(ServerPlayer attacker, IColony targetColony,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3114-            CommandSourceStack source, int extortionPercent) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3115-        Level level = source.getLevel();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3116-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3117-        int targetGuards = countGuards(targetColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3118-        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3119-            source.sendFailure(Component.literal("Target colony must have at least "
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3120-                    + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3121-            return 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3122-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3123-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3124-        // Find a valid attacker colony using the new helper
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3125-        IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3126-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3127-        if (attackerColony == null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3128-            // Retained specific error messaging logic for better user feedback
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3129-            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3130-                    .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3131-                    .findFirst().orElse(null);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3132-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3133-            if (anyColony == null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3134-                source.sendFailure(Component.literal("You must own a colony to declare war."));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3135-            } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3136-                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3137-                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
--
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3538-                        playerRank.equals(attackerPerms.getRankFriend()))) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3539-                    canJoinAttackers = true;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3540-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3541-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3542-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3543-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3544-        // Check if player is in defender colony with appropriate rank
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3545-        if (defenderColony != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3546-            IPermissions defenderPerms = defenderColony.getPermissions();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3547-            if (defenderPerms.getPlayers().containsKey(player.getUUID())) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3548-                Rank playerRank = defenderPerms.getRank(player.getUUID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3549-                if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3550-                        playerRank.equals(defenderPerms.getRankOfficer()) ||
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3551-                        playerRank.equals(defenderPerms.getRankFriend()))) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3552-                    canJoinDefenders = true;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3553-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3554-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3555-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3556-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3557-        // Handle the case where player can join both sides
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3558-        if (canJoinAttackers && canJoinDefenders) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3559-            MutableComponent message = Component
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3560-                    .literal("You are eligible to join both sides. Please choose which side to join:\n")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3561-                    .withStyle(ChatFormatting.GOLD);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3562-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3563-            Component joinAttackers = Component.literal("[Join Attackers]")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3564-                    .withStyle(style -> style.withColor(ChatFormatting.RED)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3565-                            .withBold(true)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3566-                            .withClickEvent(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3567-                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt choosewarside attacker"))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3568-                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3569-                                    Component.literal("Click to join the attacking side")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3570-                                            .withStyle(ChatFormatting.GOLD))));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3571-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3572-            Component joinDefenders = Component.literal("[Join Defenders]")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3573-                    .withStyle(style -> style.withColor(ChatFormatting.BLUE)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3574-                            .withBold(true)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3575-                            .withClickEvent(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3576-                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt choosewarside defender"))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3577-                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3578-                                    Component.literal("Click to join the defending side")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3579-                                            .withStyle(ChatFormatting.GOLD))));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3580-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3581-            player.sendSystemMessage(message.append(" ")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3582-                    .append(joinAttackers).append(" ")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3583-                    .append(joinDefenders));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3584-            return 1;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3585-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3586-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3587-        // Join the appropriate side
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3588-        if (canJoinAttackers) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3589-            war.getAttackerLives().put(player.getUUID(), playerLives);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3590-            war.getAttackerAllies().add(player.getUUID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3591-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3592-            // Assign hostile rank to this attacker on defender's colony
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3593-            assignWarParticipantRanks(player.getUUID(), war.getColony(), war.getAttackerColony(), true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3594-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3595-            player.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3596-                    Component.literal("You have joined the attacking side!").withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3597-            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3598:                war.alliesBossEvent.addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3599-            } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3600:                war.bossEvent.addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3601-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3602-            return 1;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3603-        } else if (canJoinDefenders) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3604-            war.getDefenderLives().put(player.getUUID(), playerLives);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3605-            war.getDefenderAllies().add(player.getUUID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3606-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3607-            // Assign hostile rank to this defender on attacker's colony
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3608-            assignWarParticipantRanks(player.getUUID(), war.getColony(), war.getAttackerColony(), false);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3609-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3610-            player.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3611-                    Component.literal("You have joined the defending side!").withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3612-            if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3613:                war.alliesBossEvent.addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3614-            } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:3615:                war.bossEvent.addPlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3616-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3617-            return 1;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3618-        } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3619-            source.sendFailure(Component.literal(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3620-                    "You are not eligible to join this war. Only colony owners, officers, and friends can participate."));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3621-            return 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3622-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3623-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3624-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3625-    // --- Logic moved from WarCommands.leaveWar ---
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3626-    public static int processLeaveWar(ServerPlayer player, CommandSourceStack source) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3627-        WarData war = getActiveWarForPlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3628-        if (war == null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3629-            source.sendFailure(Component.literal("No active war to leave."));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3630-            return 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3631-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3632-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3633-        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3634-                player.getUUID().equals(war.getAttacker()) ||
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3635-                (war.getAttackerColony() != null
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3636-                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3637-            source.sendFailure(Component.literal("Primary war participants cannot leave the war."));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3638-            return 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3639-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3640-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3641-        if (System.currentTimeMillis() >= war.getJoinPhaseEndTime()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3642-            source.sendFailure(Component.literal("Join phase is over; you cannot leave now."));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3643-            return 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3644-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3645-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3646-        boolean removedFromAttackers = war.getAttackerLives().remove(player.getUUID()) != null;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3647-        if (removedFromAttackers)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3648-            war.getAttackerAllies().remove(player.getUUID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3649-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3650-        boolean removedFromDefenders = war.getDefenderLives().remove(player.getUUID()) != null;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3651-        if (removedFromDefenders)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3652-            war.getDefenderAllies().remove(player.getUUID());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3653-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3654-        if (removedFromAttackers || removedFromDefenders) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3655-            source.sendSuccess(() -> Component.literal("You have left the war."), false);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3656-            if (war.alliesBossEvent != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3657-                war.alliesBossEvent.removePlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3658-            if (war.bossEvent != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3659-                war.bossEvent.removePlayer(player);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3660-            return 1;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3661-        } else {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3662-            source.sendFailure(Component.literal("You were not registered in the war to leave."));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3663-            return 0;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3664-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3665-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3666-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3667-    public static IColony findColonyByName(String name, Level level) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3668-        return IColonyManager.getInstance().getColonies(level).stream()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3669-                .filter(c -> c.getName().equalsIgnoreCase(name))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3670-                .findFirst().orElse(null);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3671-    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3672-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3673-    /**
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3674-     * Counts the number of guard citizens in a colony.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-3675-     * 
--
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4345-                        net.machiavelli.minecolonytax.peace.PeaceProposal.Type.valueOf(e.activeProposal.type),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4346-                        e.activeProposal.amount,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4347-                        UUID.fromString(e.activeProposal.proposer));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4348-                // PeaceProposal.createdTime defaults to "now" on construction â€” close
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4349-                // enough for restored proposals; the timeout check is a relative delta.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4350-            } catch (IllegalArgumentException ignored) {}
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4351-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4352-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4353-        WarData warData = new WarData(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4354-                UUID.fromString(e.warID),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4355-                UUID.fromString(e.attacker),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4356-                UUID.fromString(e.defender),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4357-                atkTid,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4358-                defTid,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4359-                e.warStartTime, e.joinPhaseEndTime,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4360-                bossEvent, defenderColony, attackerColony,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4361-                status, e.accepted,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4362-                e.initialAttackerGuards, e.remainingAttackerGuards,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4363-                e.initialDefenderGuards, e.remainingDefenderGuards,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4364-                e.initialAttackerTotalLives, e.initialDefenderTotalLives,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4365-                attackerLives, defenderLives,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4366-                defenderGuardIDSet, attackerGuardIDSet, attackerAlliesSet, defenderAlliesSet,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4367-                spectatorsSet, lastLifeSet,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4368-                e.penaltyReport, e.stalemateTriggered,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4369-                restoredHostilePerms, restoredHostilePermsAtk,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4370-                acceptedAlliesSet, declinedAlliesSet,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4371-                e.offlineOutpostWar,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4372-                restoredProposal);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4373-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4374-        ACTIVE_WARS.put(e.defenderColonyId, warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4375-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4376-        // Finding 5 cont'd: war ran past its clock while we were down. Register
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4377-        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4378-        // then immediately resolve. handleTimeExpiry takes care of victor logic,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4379-        // reparations, ranks, etc. If the canonical end-of-time path lives at
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4380-        // a different entrypoint in this codebase, this still has the WarData
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4381-        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4382-        // TODO: if handleTimeExpiry isn't safe at boot (e.g. needs world ticks),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4383-        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4384-        if (expiredDuringDowntime) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4385-            try {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4386-                handleTimeExpiry(warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4387-            } catch (Throwable t) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4388-                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4389-                try { endWar(defenderColony); } catch (Throwable t2) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4390-                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4391-                    ACTIVE_WARS.remove(e.defenderColonyId);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4392-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4393-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4394-            // True for the loader: we processed this war successfully.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4395-            return true;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4396-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4397-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4398-        // Restore defender tracking + drain scheduling
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4399-        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(e.defenderColonyId);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4400-        scheduleTreasuryDrain(warData, defenderColony, attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4401-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4402-        for (UUID uuid : warData.getAttackerLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4403-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4404-            if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4405:                bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4406-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4407-        for (UUID uuid : warData.getDefenderLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4408-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4409-            if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:4410:                bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4411-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4412-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4413-        setWarInteractionPermissions(defenderColony, true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4414-        if (attackerColony != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4415-            setWarInteractionPermissions(attackerColony, true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4416-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4417-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4418-        final IColony finalDefenderColony = defenderColony;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4419-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4420-        if (warData.getStatus() == WarData.WarStatus.INWAR) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4421-            applyWarGlowToParticipants(warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4422-            applyGuardGlow(defenderColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4423-            if (attackerColony != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4424-                applyGuardGlow(attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4425-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4426-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4427-            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4428-            if (attackerColony != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4429-                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4430-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4431-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4432-            startWarCountdown(warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4433-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4434-            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4435-            long elapsed = now - warData.warStartTime;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4436-            long remaining = warDurationMs - elapsed;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4437-            if (remaining > 0) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4438-                scheduleTimerWarnings(warData, remaining);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4439-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4440-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4441-            updateBossBar(warData);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4442-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4443-                    e.warID, defenderColony.getName(), remaining);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4444-        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4445-            warData.alliesBossEvent = new ServerBossEvent(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4446-                    Component.literal("Joining War - " + defenderColony.getName()),
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4447-                    BossEvent.BossBarColor.YELLOW,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4448-                    BossEvent.BossBarOverlay.PROGRESS);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4449-            warData.alliesBossEvent.setProgress(1.0f);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4450-            warData.alliesBossEvent.setVisible(true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4451-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4452-            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4453-            if (remainingJoinMs > 0) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4454-                final int colonyId = e.defenderColonyId;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4455-                TickScheduler.scheduleDelayed(() -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4456-                    if (!ACTIVE_WARS.containsKey(colonyId))
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4457-                        return;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4458-                    WarData w = ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4459-                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4460-                        return;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4461-                    w.setStatus(WarData.WarStatus.INWAR);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4462-                    w.warStartTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4463-                    finalizeWarStart(w);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4464-                    setWarInteractionPermissions(w.getColony(), true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4465-                    if (w.getAttackerColony() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4466-                        setWarInteractionPermissions(w.getAttackerColony(), true);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4467-                    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4468-                    startWarCountdown(w);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4469-                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-4470-                    scheduleTimerWarnings(w, warDurationMillis);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 35 "Step 11|SIEGE_BANNER_ITEM|Siege Banner" src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "PlantTheBannerObjective|SIEGE_BANNER_ITEM|BannerCaptureMinutes|EnableExperimentalSiegeObjectives" src/main/java/net/machiavelli/minecolonytax -S' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 40 "onBannerPlaced|setPlacedBy" src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 307ms:
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-1-package net.machiavelli.minecolonytax.siege;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-2-
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-3-import net.minecraft.core.BlockPos;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-4-import net.minecraft.world.entity.LivingEntity;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-5-import net.minecraft.world.item.ItemStack;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-6-import net.minecraft.world.level.Level;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-7-import net.minecraft.world.level.block.Block;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-8-import net.minecraft.world.level.block.SoundType;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-9-import net.minecraft.world.level.block.state.BlockState;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-10-import net.minecraft.world.level.material.MapColor;
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-11-
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-12-/**
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-13- * The Siege Banner â€” placed by an attacker inside the defender's Town Hall to
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-14- * trigger the capture-and-hold victory objective. The block itself is a simple
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-15- * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-16- *
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-17- * Indestructible to vanilla explosions to prevent it being blown up by stray TNT,
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-18- * but explicitly breakable by defenders via right-click melee (handled via the
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-19- * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-20- * left-click destroys it for defenders who get past attackers.
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-21- */
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-22-public class SiegeBannerBlock extends Block {
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-23-
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-24-    public SiegeBannerBlock() {
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-25-        super(Properties.of()
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-26-                .mapColor(MapColor.COLOR_RED)
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-27-                .strength(0.5f, 1200000.0f) // hardness 0.5 (quick to break by hand)
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-28-                                            // blast resistance very high (siege banners don't fall to TNT)
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-29-                .sound(SoundType.WOOL)
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-30-                .noOcclusion()
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-31-                .requiresCorrectToolForDrops()
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-32-                .lightLevel(state -> 7)); // soft glow so it's visible at night
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-33-    }
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-34-
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-35-    @Override
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java:36:    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java:37:        super.setPlacedBy(level, pos, state, placer, stack);
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java:38:        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-39-    }
src/main/java/net/machiavelli/minecolonytax/siege/SiegeBannerBlock.java-40-}
--
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-44- *     is locked for this war.
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-45- *  5. If the timer reaches zero, attacker victory â€” triggers the same
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-46- *     WarSystem.checkForVictory path used by Town Hall demolition.
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-47- *
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-48- * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-49- * banner item is still registered but the place handler treats placement as
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-50- * a vanilla block place (no boss bar, no win check).
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-51- */
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-52-@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-53-public final class PlantTheBannerObjective {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-54-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-55-    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-56-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-57-    /** Active banner captures, keyed by warId. */
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-58-    private static final Map<UUID, BannerCaptureState> ACTIVE_CAPTURES = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-59-    /** Per-war replant counter so the limit can't be bypassed by serial places. */
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-60-    private static final Map<UUID, Integer> REPLANT_COUNT = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-61-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-62-    private static final class BannerCaptureState {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-63-        final UUID warId;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-64-        final BlockPos bannerPos;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-65-        final UUID attackerUUID;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-66-        final long expiresAtMs;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-67-        final ServerBossEvent bossEvent;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-68-        final long taskId;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-69-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-70-        BannerCaptureState(UUID warId, BlockPos pos, UUID attackerUUID, long expiresAtMs,
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-71-                           ServerBossEvent bossEvent, long taskId) {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-72-            this.warId = warId;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-73-            this.bannerPos = pos;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-74-            this.attackerUUID = attackerUUID;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-75-            this.expiresAtMs = expiresAtMs;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-76-            this.bossEvent = bossEvent;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-77-            this.taskId = taskId;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-78-        }
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-79-    }
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-80-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-81-    private PlantTheBannerObjective() {}
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-82-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-83-    /**
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java:84:     * Called from {@link SiegeBannerBlock#setPlacedBy} when the banner block is placed.
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-85-     * Validates: feature flag on, placer is attacker in an active war, position is
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-86-     * inside the defender's Town Hall building. If valid â†’ start capture.
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-87-     */
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java:88:    public static void onBannerPlaced(Level level, BlockPos pos, LivingEntity placer) {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-89-        if (level == null || level.isClientSide()) return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-90-        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-91-        if (!(placer instanceof ServerPlayer attacker)) return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-92-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-93-        // Find war where this player is attacker.
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-94-        WarData war = findWarForAttacker(attacker.getUUID());
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-95-        if (war == null) {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-96-            attacker.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-97-                    "You're not an attacker in any active war â€” the Siege Banner does nothing here.")
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-98-                    .withStyle(ChatFormatting.YELLOW));
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-99-            return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-100-        }
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-101-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-102-        // Hard reject if also a defender â€” same guard pattern as TownHallDemolitionObjective.
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-103-        if (war.getDefenderLives().containsKey(attacker.getUUID())) {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-104-            attacker.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-105-                    "You're listed on both sides of this war â€” banner placement refused.")
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-106-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-107-            destroyBannerSilently(level, pos);
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-108-            return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-109-        }
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-110-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-111-        IColony defenderColony = war.getColony();
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-112-        if (defenderColony == null) return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-113-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-114-        ITownHall townHall = findTownHall(defenderColony);
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-115-        if (townHall == null) {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-116-            attacker.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-117-                    "No Town Hall found in the defender colony â€” banner cannot be planted.")
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-118-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-119-            destroyBannerSilently(level, pos);
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-120-            return;
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-121-        }
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-122-
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-123-        if (!((IBuilding) townHall).isInBuilding(pos)) {
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-124-            attacker.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-125-                    "The Siege Banner must be planted INSIDE the Town Hall building.")
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-126-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-127-            destroyBannerSilently(level, pos);
src/main/java/net/machiavelli/minecolonytax/siege/PlantTheBannerObjective.java-128-            return;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 332ms:
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-579-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-580-                    war.bossEvent.addPlayer(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-581-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-582-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-583-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-584-        if (war.alliesBossEvent != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-585-            war.alliesBossEvent.removeAllPlayers();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-586-            war.alliesBossEvent.setVisible(false);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-587-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-588-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-589-        war.getAttackerLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-590-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-591-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-592-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-593-                    assignWarGroup(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-594-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-595-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-596-        war.getDefenderLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-597-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-598-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-599-                if (p != null)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-600-                    assignWarGroup(p);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-601-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-602-        });
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-603-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-604-        war.warStartTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-605-        war.setStatus(WarData.WarStatus.INWAR);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-606-        updateBossBar(war);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-607-        // Apply glow to both defender and attacker guards for clear visibility
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-608-        applyGuardGlow(war.getColony());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-609-        if (war.getAttackerColony() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-610-            applyGuardGlow(war.getAttackerColony());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-611-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-612-        applyWarGlowToParticipants(war);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-613-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:614:        // Step 11 â€” when experimental objectives are enabled, give each attacker
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:615:        // a Siege Banner at INWAR transition. They can plant it inside the
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-616-        // defender's Town Hall to start the capture timer.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-617-        if (TaxConfig.isExperimentalSiegeObjectivesEnabled()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-618-            try {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-619-                if (war.getColony() != null && war.getColony().getWorld() != null
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-620-                        && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-621-                    var server = war.getColony().getWorld().getServer();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:622:                    var bannerItem = net.machiavelli.minecolonytax.siege.ModSiegeBlocks.SIEGE_BANNER_ITEM.get();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-623-                    for (UUID attackerUUID : war.getAttackerLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-624-                        ServerPlayer p = server.getPlayerList().getPlayer(attackerUUID);
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-625-                        if (p != null && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(bannerItem))) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-626-                            p.getInventory().add(new net.minecraft.world.item.ItemStack(bannerItem, 1));
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-627-                        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-628-                    }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-629-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-630-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:631:                WARSYSTEM_LOGGER.warn("Failed to hand Siege Banners to attackers: {}", e.getMessage());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-632-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-633-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-634-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-635-        // Militia upgrade reinforcements â€” spawn on BOTH sides if either colony
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-636-        // has the upgrade. Defender side primarily (per design), attacker side
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-637-        // optionally so an upgraded attacker colony also gets the boost.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-638-        // Idempotent â€” re-entry checks the existing set is empty first.
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-639-        if (war.militiaSupport.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-640-            try {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-641-                // Defender militia
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-642-                if (war.getColony() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-643-                    int defenderGuardCount = (int) war.getColony().getCitizenManager().getCitizens().stream()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-644-                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-645-                            .count();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-646-                    // No specific attacker-target â€” let the militia find via vanilla aggro
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-647-                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-648-                            war.getColony(), defenderGuardCount, null,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-649-                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-650-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-651-                // Attacker militia (their colony also benefits from the upgrade)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-652-                if (war.getAttackerColony() != null) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-653-                    int attackerGuardCount = (int) war.getAttackerColony().getCitizenManager().getCitizens().stream()
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-654-                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-655-                            .count();
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-656-                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-657-                            war.getAttackerColony(), attackerGuardCount, null,
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-658-                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-659-                }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-660-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-661-                WARSYSTEM_LOGGER.warn("Militia spawn during war start failed: {}", e.getMessage());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-662-            }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-663-        }
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-664-
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-665-        // Apply resistance effects to defending guards during war
src/main/java/net/machiavelli/minecolonytax/WarSystem.java-666-        GuardResistanceHandler.applyResistanceToGuardsForWar(war.getColony());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 364ms:
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:622:                    var bannerItem = net.machiavelli.minecolonytax.siege.ModSiegeBlocks.SIEGE_BANNER_ITEM.get();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1328:                net.machiavelli.minecolonytax.siege.PlantTheBannerObjective.onWarEnded(warData.getWarID());
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:670:                                .define("EnableExperimentalSiegeObjectives", false);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:699:                                                "attacker may re-plant once. Behind EnableExperimentalSiegeObjectives.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:700:                                .defineInRange("BannerCaptureMinutes", 10, 1, 120);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2735:        public static int getBannerCaptureMinutesOrDefault() {
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:57:        // via EnableExperimentalSiegeObjectives.
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:41: * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
src/main/java/net/machiavelli/minecolonytax\siege\SiegeBannerBlock.java:15: * solid block; all win-condition logic lives in {@link PlantTheBannerObjective}.
src/main/java/net/machiavelli/minecolonytax\siege\SiegeBannerBlock.java:19: * BlockBreak event by {@code PlantTheBannerObjective}). Hardness 0.5 so a quick
src/main/java/net/machiavelli/minecolonytax\siege\SiegeBannerBlock.java:38:        PlantTheBannerObjective.onBannerPlaced(level, pos, placer);
src/main/java/net/machiavelli/minecolonytax\siege\PlantTheBannerObjective.java:41: *     remaining hold time (default 10 min via {@code BannerCaptureMinutes}).
src/main/java/net/machiavelli/minecolonytax\siege\PlantTheBannerObjective.java:48: * Behind {@code EnableExperimentalSiegeObjectives}. When the flag is off the
src/main/java/net/machiavelli/minecolonytax\siege\PlantTheBannerObjective.java:53:public final class PlantTheBannerObjective {
src/main/java/net/machiavelli/minecolonytax\siege\PlantTheBannerObjective.java:55:    private static final Logger LOGGER = LogManager.getLogger(PlantTheBannerObjective.class);
src/main/java/net/machiavelli/minecolonytax\siege\PlantTheBannerObjective.java:81:    private PlantTheBannerObjective() {}
src/main/java/net/machiavelli/minecolonytax\siege\PlantTheBannerObjective.java:155:        int holdMinutes = TaxConfig.getBannerCaptureMinutesOrDefault();
src/main/java/net/machiavelli/minecolonytax\siege\ModSiegeBlocks.java:19: * gated behind {@code EnableExperimentalSiegeObjectives} at runtime.
src/main/java/net/machiavelli/minecolonytax\siege\ModSiegeBlocks.java:32:    public static final RegistryObject<Item> SIEGE_BANNER_ITEM = ITEMS.register("siege_banner",
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 25 \"ModSiegeBlocks|register\\(modEventBus|TickScheduler\" src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 50 "handleTimeExpiry|startWarCountdown|checkForVictory" src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 336ms:
663-        }
664-
665-        // Apply resistance effects to defending guards during war
666-        GuardResistanceHandler.applyResistanceToGuardsForWar(war.getColony());
667-        if (war.getAttackerColony() != null) {
668-            GuardResistanceHandler.applyResistanceToGuardsForWar(war.getAttackerColony());
669-        }
670-
671-        // Initialize militia system for guard tracking and citizen conversion in BOTH
672-        // colonies
673-        initializeWarMilitiaSystem(war);
674-        activateWarMilitia(war);
675-        if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
676-            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
677-                    : "Attacking Forces";
678-            String defenderColonyName = war.getColony().getName();
679-
680-            Component warBeginMsg = Component.empty()
681-                    .append(Component.translatable("war.begin.title").withStyle(ChatFormatting.GOLD,
682-                            ChatFormatting.BOLD))
683-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
684-                    .append(Component.literal("\n"))
685-                    .append(Component.translatable("war.begin.body", attackerColonyName, defenderColonyName)
686-                            .withStyle(ChatFormatting.YELLOW))
687-                    .append(Component.literal("\n"))
688-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
689-            broadcastToServer(warBeginMsg);
690-        }
691-        long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
692-        scheduleTimerWarnings(war, warDurationMillis);
693-    }
694-
695-    private static void assignWarGroup(ServerPlayer player) {
696-        if (player == null || player.getServer() == null)
697-            return;
698-        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
699-            String command = "lp user " + player.getName().getString() + " parent set war";
700-            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
701-        }
702-    }
703-
704-    public static void resetWarGroup(ServerPlayer player) {
705-        if (player == null || player.getServer() == null)
706-            return;
707-        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
708-            String command = "lp user " + player.getName().getString() + " parent set default";
709-            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
710-        }
711-    }
712-
713:    public static void checkForVictory(WarData war) {
714-        boolean allAttackersDead = war.getAttackerLives().values().stream().allMatch(lives -> lives <= 0);
715-        boolean allDefendersDead = war.getDefenderLives().values().stream().allMatch(lives -> lives <= 0);
716-        boolean allDefenderGuardsDead = war.getRemainingDefenderGuards() <= 0;
717-        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;
718-
719-        // Check if we have any participants at all
720-        boolean hasAttackers = !war.getAttackerLives().isEmpty();
721-        boolean hasDefenders = !war.getDefenderLives().isEmpty();
722-
723-        // If no participants, don't end the war
724-        if (!hasAttackers && !hasDefenders) {
725-            return;
726-        }
727-
728-        // Victory conditions:
729-        // - Attackers win if all defenders are dead (0 lives) OR if all defender guards
730-        // are dead
731-        // - Defenders win if all attackers are dead (0 lives) OR if all attacker guards
732-        // are dead
733-        // - Priority: Player deaths take precedence over guard deaths for ending wars
734-        boolean attackersWin = (hasDefenders && allDefendersDead) || (!hasDefenders && allDefenderGuardsDead);
735-        boolean defendersWin = (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);
736-
737-        // Only proceed if there's a clear victory condition
738-        if (!attackersWin && !defendersWin) {
739-            return;
740-        }
741-
742-        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
743-            return;
744-
745-        if (TaxConfig.isDebugLogging()) {
746-            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
747-                    "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: "
748-                            + defendersWin);
749-            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
750-                    "[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
751-            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: "
752-                    + war.getRemainingDefenderGuards());
753-        }
754-
755-        if (defendersWin) {
756-            String defenderColonyName = war.getColony().getName();
757-            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
758-                    : "The Attackers";
759-            Component victoryMsg = Component.empty()
760-                    .append(Component.translatable("war.defenders.win.title").withStyle(ChatFormatting.GOLD,
761-                            ChatFormatting.BOLD))
762-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
763-                    .append(Component.literal("\n"))
--
1560-                    .addWarEntry(attackerName, defenderOutcome, amountTransferred, _defBefore, _defAfter);
1561-            if (warData.getAttackerColony() != null) {
1562-                HistoryManager.getColonyHistory(warData.getAttackerColony().getID())
1563-                        .addWarEntry(colony.getName(), attackerOutcome, amountTransferred, _atkBefore, _atkAfter);
1564-            }
1565-            HistoryManager.saveHistory();
1566-            WARSYSTEM_LOGGER.info("War ended for colony {}", colony.getName());
1567-        }
1568-    }
1569-
1570-    public static class WarInventoryHandler {
1571-        private static final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
1572-        private static final Map<UUID, ItemStack[]> savedArmors = new ConcurrentHashMap<>();
1573-
1574-        public static boolean hasSavedInventory(ServerPlayer player) {
1575-            return savedInventories.containsKey(player.getUUID());
1576-        }
1577-
1578-        public static void saveAndClearInventory(ServerPlayer player) {
1579-            ItemStack[] main = new ItemStack[player.getInventory().getContainerSize()];
1580-            for (int i = 0; i < main.length; i++) {
1581-                main[i] = player.getInventory().getItem(i).copy();
1582-            }
1583-            savedInventories.put(player.getUUID(), main);
1584-            ItemStack[] armor = new ItemStack[4];
1585-            for (int i = 0; i < 4; i++) {
1586-                armor[i] = player.getInventory().armor.get(i).copy();
1587-            }
1588-            savedArmors.put(player.getUUID(), armor);
1589-            player.getInventory().clearContent();
1590-        }
1591-
1592-        public static void restoreInventory(ServerPlayer player) {
1593-            UUID uuid = player.getUUID();
1594-            ItemStack[] main = savedInventories.remove(uuid);
1595-            if (main != null) {
1596-                for (int i = 0; i < main.length; i++) {
1597-                    player.getInventory().setItem(i, main[i]);
1598-                }
1599-            }
1600-            ItemStack[] armor = savedArmors.remove(uuid);
1601-            if (armor != null) {
1602-                for (int i = 0; i < 4; i++) {
1603-                    player.getInventory().armor.set(i, armor[i]);
1604-                }
1605-            }
1606-            player.containerMenu.broadcastChanges();
1607-        }
1608-    }
1609-
1610:    public static void handleTimeExpiry(WarData war) {
1611-        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
1612-            return;
1613-
1614-        // Handle disconnected players - set their lives to zero
1615-        Map<UUID, Integer> disconnectedPlayers = WarEventHandler.getDisconnectedWarParticipants();
1616-
1617-        // Process disconnected attackers
1618-        for (UUID uuid : new ArrayList<>(war.getAttackerLives().keySet())) {
1619-            if (disconnectedPlayers.containsKey(uuid) && disconnectedPlayers.get(uuid) == 1) { // 1 = attacker
1620-                // Player is disconnected and part of this war, set lives to zero
1621-                war.getAttackerLives().put(uuid, 0);
1622-                WARSYSTEM_LOGGER.info("[MineColonyTax] Setting disconnected attacker {} to 0 lives on war expiry",
1623-                        uuid);
1624-            }
1625-        }
1626-
1627-        // Process disconnected defenders
1628-        for (UUID uuid : new ArrayList<>(war.getDefenderLives().keySet())) {
1629-            if (disconnectedPlayers.containsKey(uuid) && disconnectedPlayers.get(uuid) == 2) { // 2 = defender
1630-                // Player is disconnected and part of this war, set lives to zero
1631-                war.getDefenderLives().put(uuid, 0);
1632-                WARSYSTEM_LOGGER.info("[MineColonyTax] Setting disconnected defender {} to 0 lives on war expiry",
1633-                        uuid);
1634-            }
1635-        }
1636-
1637-        int attackerTotalLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
1638-        int defenderTotalLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
1639-        String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
1640-                : "The Attackers";
1641-        String defenderColonyName = war.getColony().getName();
1642-
1643-        MutableComponent timeExpiredMsgBase = Component.translatable("war.time.expired.title")
1644-                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
1645-                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
1646-
1647-        if (attackerTotalLives == 0 && war.getRemainingAttackerGuards() == 0) {
1648-            MutableComponent defenderVictoryMsg = Component.translatable("war.time.expired.title")
1649-                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
1650-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
1651-                    .append(Component.literal("\n"))
1652-                    .append(Component.translatable("war.time.expired.defenders.part1").withStyle(ChatFormatting.YELLOW))
1653-                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
1654-                    .append(Component.translatable("war.time.expired.defenders.part2").withStyle(ChatFormatting.YELLOW))
1655-                    .append(Component.literal("\n"))
1656-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
1657-            broadcastComponent(war, defenderVictoryMsg);
1658-            for (UUID defUUID : war.getDefenderLives().keySet()) {
1659-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
1660-                if (p != null)
--
1810-                if (p != null)
1811-                    PlayerWarDataManager.incrementWarsLost(p);
1812-            }
1813-        } else { // Proportional losses are too close - stalemate
1814-            reportOutcome = "Stalemate (Timeout - Proportional Losses): Both sides fought hard but neither gained a clear advantage. Penalties apply.";
1815-            strategicMsg = Component.translatable("war.time.expired.title")
1816-                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
1817-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
1818-                    .append(Component.literal("\n"))
1819-                    .append(Component.translatable("war.stalemate.proportional.part1").withStyle(ChatFormatting.YELLOW))
1820-                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
1821-                            ChatFormatting.BOLD))
1822-                    .append(Component.translatable("war.stalemate.proportional.part2").withStyle(ChatFormatting.YELLOW))
1823-                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
1824-                    .append(Component.translatable("war.stalemate.proportional.part3").withStyle(ChatFormatting.YELLOW))
1825-                    .append(Component.literal("\n"))
1826-                    .append(Component.translatable("war.stalemate.proportional.penalties")
1827-                            .withStyle(ChatFormatting.AQUA))
1828-                    .append(Component.literal("\n"))
1829-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
1830-            war.getAttackerLives().keySet().forEach(
1831-                    uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, TaxConfig.getWarStalematePercentage()));
1832-            war.getDefenderLives().keySet().forEach(
1833-                    uuid -> WarEconomyHandler.deductTeamBalanceWithReport(uuid, TaxConfig.getWarStalematePercentage()));
1834-            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage());
1835-            if (war.getAttackerColony() != null)
1836-                TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage());
1837-            broadcastComponent(war, strategicMsg);
1838-        }
1839-        war.setPenaltyReport(reportOutcome);
1840-        endWar(war.getColony());
1841-    }
1842-
1843-    // Helper to broadcast war results to entire server
1844-    private static void broadcastComponent(WarData war, Component message) {
1845-        broadcastToServer(message);
1846-    }
1847-
1848-    public static void handleGuardKilled(WarData war, boolean isDefenderGuard) {
1849-        if (isDefenderGuard) {
1850-            war.remainingDefenderGuards--;
1851-            Component message = Component.translatable("war.guard.killed.defender", war.getRemainingDefenderGuards())
1852-                    .withStyle(style -> style.withColor(ChatFormatting.RED));
1853-            notifyWarParticipants(war, message);
1854-        } else {
1855-            war.remainingAttackerGuards--;
1856-            Component message = Component.translatable("war.guard.killed.attacker", war.getRemainingAttackerGuards())
1857-                    .withStyle(style -> style.withColor(ChatFormatting.BLUE));
1858-            notifyWarParticipants(war, message);
1859-        }
1860:        checkForVictory(war);
1861-    }
1862-
1863-    private static void notifyWarParticipants(WarData war, Component notification) {
1864-        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
1865-            return;
1866-        war.getAttackerLives().keySet().forEach(uuid -> {
1867-            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
1868-            if (player != null)
1869-                player.sendSystemMessage(notification);
1870-        });
1871-        war.getDefenderLives().keySet().forEach(uuid -> {
1872-            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
1873-            if (player != null)
1874-                player.sendSystemMessage(notification);
1875-        });
1876-    }
1877-
1878-    // Keep the original method for backward compatibility
1879-    private static void notifyWarParticipants(WarData war, String message, ChatFormatting color) {
1880-        Component notification = Component.literal(message).withStyle(style -> style.withColor(color));
1881-        notifyWarParticipants(war, notification);
1882-    }
1883-
1884-    private static void handleVictoryRewards(WarData war, boolean defendersWon) {
1885-        Map<UUID, Integer> winnerLives = defendersWon ? war.getDefenderLives() : war.getAttackerLives();
1886-        IColony loserColony = defendersWon ? war.getAttackerColony() : war.getColony();
1887-
1888-        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // ATTACKER WINS - Target colony enters occupied
1889-                                                                       // state
1890-            if (TaxConfig.isOccupationSystemEnabled()) {
1891-                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
1892-                        war.getColony(), war.getAttacker(), war.getAttackerColony());
1893-                war.setPenaltyReport("TOTAL VICTORY - Colony is now OCCUPIED! Original owner has " +
1894-                        TaxConfig.getOccupationDurationDays() + " days to reclaim.");
1895-            } else {
1896-                transferOwnership(war.getColony(), war.getAttacker());
1897-                war.setPenaltyReport("TOTAL VICTORY - Colony transferred to attackers!");
1898-            }
1899-        } else if (TaxConfig.isColonyWagerEnabled() && defendersWon && war.getAttackerColony() != null) {
1900-            // DEFENDER WINS with COLONY WAGER enabled - Attacker's wagered colony enters
1901-            // occupied state!
1902-            if (TaxConfig.isOccupationSystemEnabled()) {
1903-                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
1904-                        war.getAttackerColony(), war.getDefender(), war.getColony());
1905-                war.setPenaltyReport("âš” COUNTER-CONQUEST! The attacker's colony is now OCCUPIED by the defenders! " +
1906-                        "Attacker has " + TaxConfig.getOccupationDurationDays() + " days to reclaim.");
1907-
1908-                // Notify both sides about the wager outcome
1909-                if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
1910-                    Component wagerLostMsg = Component.empty()
--
2191-            // Check if player is in defender colony (owner, officer, or friend)
2192-            if (defenderColony != null) {
2193-                IPermissions defenderPerms = defenderColony.getPermissions();
2194-                if (defenderPerms.getPlayers().containsKey(player.getUUID())) {
2195-                    Rank playerRank = defenderPerms.getRank(player.getUUID());
2196-                    if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
2197-                            playerRank.equals(defenderPerms.getRankOfficer()) ||
2198-                            playerRank.equals(defenderPerms.getRankFriend()))) {
2199-                        return war;
2200-                    }
2201-                }
2202-            }
2203-        }
2204-        return null;
2205-    }
2206-
2207-    public static void scheduleTimerWarnings(WarData war, long warDurationMillis) {
2208-        long quarter = warDurationMillis / 4;
2209-        for (int i = 1; i <= 3; i++) {
2210-            long delay = quarter * i;
2211-            if (delay <= 0)
2212-                continue;
2213-            TickScheduler.scheduleDelayed(() -> {
2214-                // Check if war still exists in active wars or if the colony world/server is
2215-                // null
2216-                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
2217-                        war.getColony().getWorld() == null ||
2218-                        war.getColony().getWorld().getServer() == null ||
2219-                        war.bossEvent == null) {
2220-                    return;
2221-                }
2222-
2223-                // Check war status - don't process for ended wars
2224-                if (war.getStatus() != WarData.WarStatus.INWAR) {
2225-                    return;
2226-                }
2227-
2228-                long elapsedSeconds = (System.currentTimeMillis() - war.warStartTime) / 1000;
2229-                long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
2230-                long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
2231-                String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
2232-                        war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
2233-                        war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
2234-                        remaining / 60, remaining % 60);
2235-                Component newName = Component.literal(bossText);
2236-                float newProgress = (float) remaining / warDurationSeconds;
2237-                war.bossEvent.setName(newName);
2238-                war.bossEvent.setProgress(newProgress);
2239-                war.bossEvent.setVisible(true);
2240-                if (remaining <= 0) {
2241:                    handleTimeExpiry(war);
2242-                }
2243-            }, delay);
2244-        }
2245-    }
2246-
2247-    private static void applyWarGlowToParticipants(WarData war) {
2248-        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
2249-            return;
2250-        war.getAttackerLives().keySet().forEach(uuid -> {
2251-            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
2252-            if (p != null)
2253-                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
2254-                        net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
2255-        });
2256-        war.getDefenderLives().keySet().forEach(uuid -> {
2257-            ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
2258-            if (p != null)
2259-                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
2260-                        net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
2261-        });
2262-    }
2263-
2264-    public static void applyGuardGlow(IColony colony) {
2265-        if (colony.getCitizenManager() == null)
2266-            return;
2267-        colony.getCitizenManager().getCitizens().stream()
2268-                .filter(citizen -> citizen.getJob() != null && citizen.getJob().isGuard())
2269-                .forEach(citizen -> citizen.getEntity().ifPresent(entity -> {
2270-                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
2271-                            net.minecraft.world.effect.MobEffects.GLOWING, 999999, 0, false, false));
2272-                }));
2273-    }
2274-
2275-    public static void onPlayerKilledInWar(ServerPlayer killer, ServerPlayer killed, WarData war) {
2276-        if (killer != null && killed != null && war != null) {
2277-            PlayerWarDataManager.incrementPlayersKilledInWar(killer);
2278-            PlayerWarDataManager.incrementTimesKilledInWar(killed);
2279-            net.machiavelli.minecolonytax.db.WarStatsDB.recordWarKill(
2280-                    killer.getUUID(), killer.getName().getString(),
2281-                    killed.getUUID(), killed.getName().getString());
2282-        }
2283-    }
2284-
2285-    public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
2286-        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
2287-                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
2288-                : null;
2289-        FtbTeamsCompat.TeamHandle defenderTeam = FTB_TEAMS_INSTALLED
2290-                ? FtbTeamsCompat.getTeamForPlayer(owner.getUUID()).orElse(null)
2291-                : null;
--
2398-                        return; // Task will be cleaned up when war starts or ends
2399-                    }
2400-
2401-                    // Play countdown sound to all war participants
2402-                    Set<UUID> allParticipants = new HashSet<>();
2403-                    allParticipants.addAll(war.getAttackerLives().keySet());
2404-                    allParticipants.addAll(war.getDefenderLives().keySet());
2405-
2406-                    // Only play sound if there are participants
2407-                    if (!allParticipants.isEmpty()) {
2408-                        // Play countdown sound using Minecraft's bell sound
2409-                        for (UUID uuid : allParticipants) {
2410-                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
2411-                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
2412-                                    : null;
2413-                            if (player != null) {
2414-                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
2415-                                        1.0F);
2416-                            }
2417-                        }
2418-                    }
2419-
2420-                    // Notify remaining seconds
2421-                    notifyWarParticipants(war,
2422-                            Component
2423-                                    .literal("â± " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds")
2424-                                            + " until war starts!")
2425-                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
2426-
2427-                    secondsLeft[0]--;
2428-                } catch (Exception ex) {
2429-                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
2430-                }
2431-            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1
2432-                                                              // second
2433-        }
2434-
2435-        // Main timer to start the war when join phase ends
2436-        TickScheduler.scheduleDelayed(() -> {
2437-            if (war == null || war.getColony() == null) {
2438-                return;
2439-            }
2440-            war.setStatus(WarData.WarStatus.INWAR);
2441-            war.warStartTime = System.currentTimeMillis();
2442-            finalizeWarStart(war);
2443-            // Enable war actions for both sides
2444-            setWarInteractionPermissions(war.getColony(), true);
2445-            if (war.getAttackerColony() != null) {
2446-                setWarInteractionPermissions(war.getAttackerColony(), true);
2447-            }
2448:            startWarCountdown(war);
2449-        }, joinDurationMillis);
2450-        war.setAccepted(true);
2451-    }
2452-
2453-    private static void sendNotificationToColonyParticipants(IColony colony, Component message) {
2454-        if (colony == null || colony.getWorld() == null || colony.getWorld().getServer() == null) {
2455-            return;
2456-        }
2457-        colony.getPermissions().getPlayers().keySet().stream()
2458-                .filter(uuid -> isOfficerOrFriendly(colony, uuid))
2459-                .forEach(uuid -> {
2460-                    ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
2461-                    if (player != null) {
2462-                        player.sendSystemMessage(message);
2463-                    }
2464-                });
2465-    }
2466-
2467-    /**
2468-     * Sends a notification to all war participants from both colonies (attacker and
2469-     * defender),
2470-     * including officers, friends, and FTB team members if applicable.
2471-     */
2472-    // Helper to broadcast to entire server
2473-    private static void broadcastToServer(Component message) {
2474-        if (ServerLifecycleHooks.getCurrentServer() == null)
2475-            return;
2476-        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);
2477-    }
2478-
2479-    private static void sendNotificationToWarParticipants(IColony defenderColony, IColony attackerColony,
2480-            Component message) {
2481-        if (defenderColony == null || defenderColony.getWorld() == null
2482-                || defenderColony.getWorld().getServer() == null) {
2483-            return;
2484-        }
2485-
2486-        Set<UUID> notifiedPlayers = new HashSet<>();
2487-        MinecraftServer server = defenderColony.getWorld().getServer();
2488-
2489-        // Notify defender colony officers and friends
2490-        defenderColony.getPermissions().getPlayers().keySet().stream()
2491-                .filter(uuid -> isOfficerOrFriendly(defenderColony, uuid))
2492-                .forEach(uuid -> {
2493-                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
2494-                    if (player != null) {
2495-                        player.sendSystemMessage(message);
2496-                        notifiedPlayers.add(uuid);
2497-                    }
2498-                });
--
2503-                    .filter(uuid -> isOfficerOrFriendly(attackerColony, uuid))
2504-                    .forEach(uuid -> {
2505-                        if (!notifiedPlayers.contains(uuid)) {
2506-                            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
2507-                            if (player != null) {
2508-                                player.sendSystemMessage(message);
2509-                                notifiedPlayers.add(uuid);
2510-                            }
2511-                        }
2512-                    });
2513-        }
2514-
2515-        // If FTB Teams is installed, also notify team members
2516-        if (FTB_TEAMS_INSTALLED) {
2517-            WarData war = ACTIVE_WARS.get(defenderColony.getID());
2518-            if (war != null) {
2519-                // Notify attacker team members
2520-                if (war.getAttackerTeamID() != null) {
2521-                    FtbTeamsCompat.TeamHandle attackerTeam = FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
2522-                    if (attackerTeam != null && FtbTeamsCompat.isPartyTeam(attackerTeam)) {
2523-                        FtbTeamsCompat.getPartyMembers(attackerTeam).forEach(uuid -> {
2524-                            if (!notifiedPlayers.contains(uuid)) {
2525-                                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
2526-                                if (player != null) {
2527-                                    player.sendSystemMessage(message);
2528-                                    notifiedPlayers.add(uuid);
2529-                                }
2530-                            }
2531-                        });
2532-                    }
2533-                }
2534-
2535-                // Notify defender team members
2536-                if (war.getDefenderTeamID() != null) {
2537-                    FtbTeamsCompat.TeamHandle defenderTeam = FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
2538-                    if (defenderTeam != null && FtbTeamsCompat.isPartyTeam(defenderTeam)) {
2539-                        FtbTeamsCompat.getPartyMembers(defenderTeam).forEach(uuid -> {
2540-                            if (!notifiedPlayers.contains(uuid)) {
2541-                                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
2542-                                if (player != null) {
2543-                                    player.sendSystemMessage(message);
2544-                                }
2545-                            }
2546-                        });
2547-                    }
2548-                }
2549-            }
2550-        }
2551-    }
2552-
2553:    private static void startWarCountdown(WarData warData) {
2554-        if (warData.getColony().getWorld() == null) {
2555-            WARSYSTEM_LOGGER.error("Cannot start war countdown, world is null for colony {}",
2556-                    warData.getColony().getID());
2557-            return;
2558-        }
2559-        final long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
2560-        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
2561-            // Check if war still exists in active wars or if the colony world/server is
2562-            // null
2563-            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
2564-                    warData.getColony().getWorld() == null ||
2565-                    warData.getColony().getWorld().getServer() == null ||
2566-                    warData.bossEvent == null) {
2567-                TickScheduler.cancel(warData.countdownTaskId);
2568-                warData.countdownTaskId = -1;
2569-                return;
2570-            }
2571-
2572-            // Check war status - don't process for ended wars
2573-            if (warData.getStatus() != WarData.WarStatus.INWAR) {
2574-                TickScheduler.cancel(warData.countdownTaskId);
2575-                warData.countdownTaskId = -1;
2576-                return;
2577-            }
2578-
2579-            // Finding 9: defensive guard against wall-clock skew (NTP, manual
2580-            // clock change, container restart). If now < warStartTime the war
2581-            // was "born in the future" â€” almost certainly a backwards clock
2582-            // adjustment. Reset warStartTime to the current wall clock so the
2583-            // war doesn't appear to never expire (or instantly expire). This is
2584-            // a soft repair, not a monotonic rewrite â€” sufficient to avoid
2585-            // every-war-killed-on-NTP-skew bugs.
2586-            long nowMs = System.currentTimeMillis();
2587-            if (nowMs < warData.warStartTime) {
2588-                WARSYSTEM_LOGGER.warn("War {}: wall clock went backwards (now={} < warStartTime={}). "
2589-                        + "Resetting warStartTime to now; war will continue from the new clock value.",
2590-                        warData.getWarID(), nowMs, warData.warStartTime);
2591-                warData.warStartTime = nowMs;
2592-            }
2593-            long elapsedSeconds = (nowMs - warData.warStartTime) / 1000;
2594-            long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
2595-            String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
2596-                    warData.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
2597-                    warData.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
2598-                    remaining / 60, remaining % 60);
2599-            Component newName = Component.literal(bossText);
2600-            float newProgress = (float) remaining / warDurationSeconds;
2601-            warData.bossEvent.setName(newName);
2602-            warData.bossEvent.setProgress(newProgress);
2603-            warData.bossEvent.setVisible(true);
2604-            if (remaining <= 0) {
2605:                handleTimeExpiry(warData);
2606-                TickScheduler.cancel(warData.countdownTaskId);
2607-                warData.countdownTaskId = -1;
2608-            }
2609-        }, 1000, 1000);
2610-    }
2611-
2612-    public static void sendColonyMessage(IColony colony, Component message) {
2613-        if (colony == null || colony.getWorld() == null)
2614-            return;
2615-        IPermissions perms = colony.getPermissions();
2616-        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
2617-            // Only send to colony allies: Owner, Officers, and Friends
2618-            // Excludes: Hostile and Neutral players
2619-            Rank rank = perms.getRank(uuid);
2620-            if (rank != null && (rank.equals(perms.getRankOwner()) ||
2621-                    rank.equals(perms.getRankOfficer()) ||
2622-                    rank.equals(perms.getRankFriend()))) {
2623-                ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
2624-                if (p != null)
2625-                    p.sendSystemMessage(message);
2626-            }
2627-        });
2628-    }
2629-
2630-    public static void sendMessageToTeam(FtbTeamsCompat.TeamHandle team, Component msg) {
2631-        if (team == null || ServerLifecycleHooks.getCurrentServer() == null)
2632-            return;
2633-        for (UUID member : FtbTeamsCompat.getTeamMembers(team)) {
2634-            ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
2635-            if (sp != null)
2636-                sp.sendSystemMessage(msg);
2637-        }
2638-    }
2639-
2640-    /**
2641-     * Finds a valid colony owned by the player that meets the requirements to
2642-     * declare war on the target.
2643-     * Checks building requirements, guard counts, and treasury status.
2644-     * 
2645-     * SPECIAL CASE: Occupied colonies can be used for RECLAMATION wars against the
2646-     * occupier's colony.
2647-     * This allows players whose only colony is occupied to still fight back!
2648-     * 
2649-     * @param player        The player attempting to declare war
2650-     * @param targetColony  The target colony
2651-     * @param checkTreasury Whether to check treasury requirements (usually true)
2652-     * @return The first valid IColony found, or null if none meet requirements
2653-     */
2654-    public static IColony findValidAttackerColony(ServerPlayer player, IColony targetColony, boolean checkTreasury) {
2655-        if (player == null || targetColony == null)
--
3048-        // would never trigger - making the attacker invincible!
3049-        warData.remainingAttackerGuards = 0;
3050-
3051-        ACTIVE_WARS.put(targetColony.getID(), warData);
3052-        int _defStartBal = TaxManager.getStoredTaxForColonyId(targetColony.getID());
3053-        HistoryManager.logWithBalance(targetColony.getID(), "WAR",
3054-                "War started â€” attacked by " + attacker.getName().getString(),
3055-                _defStartBal, _defStartBal);
3056-        if (attackerColony != null) {
3057-            int _atkStartBal = TaxManager.getStoredTaxForColonyId(attackerColony.getID());
3058-            HistoryManager.logWithBalance(attackerColony.getID(), "WAR",
3059-                    "War started â€” attacking " + targetColony.getName(),
3060-                    _atkStartBal, _atkStartBal);
3061-        }
3062-
3063-        // Mark defender for home-field drain advantage + schedule drain
3064-        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(targetColony.getID());
3065-        scheduleTreasuryDrain(warData, targetColony, attackerColony);
3066-
3067-        // Add attacker to the war
3068-        warData.getAttackerLives().put(attacker.getUUID(), TaxConfig.PLAYER_LIVES_IN_WAR.get());
3069-
3070-        // Create boss bar for the attacker
3071-        warData.bossEvent = new ServerBossEvent(
3072-                Component.literal("Outpost Assault - " + targetColony.getName()),
3073-                BossEvent.BossBarColor.RED,
3074-                BossEvent.BossBarOverlay.PROGRESS);
3075-        warData.bossEvent.setProgress(1.0f);
3076-        warData.bossEvent.setVisible(true);
3077-        warData.bossEvent.addPlayer(attacker);
3078-
3079-        // Set war status to IN WAR immediately (no join phase for offline attacks)
3080-        warData.setStatus(WarData.WarStatus.INWAR);
3081-        warData.warStartTime = System.currentTimeMillis();
3082-
3083-        // Enable war interactions
3084-        setWarInteractionPermissions(targetColony, true);
3085-        setWarInteractionPermissions(attackerColony, true);
3086-
3087-        // Assign hostile rank to attacker in target colony
3088-        Rank hostileRank = targetColony.getPermissions().getRankHostile();
3089-        targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
3090-
3091-        // Apply glow effects and resistance buffs
3092-        // TODO: Implement applyGlowEffect and applyGuardResistance for offline outpost
3093-        // wars
3094-        // applyGlowEffect(attacker, targetColony);
3095-        // applyGuardResistance(targetColony);
3096-
3097-        // Start the war countdown
3098:        startWarCountdown(warData);
3099-
3100-        // Notify the attacker
3101-        attacker.sendSystemMessage(
3102-                Component.literal("âš” ASSAULT BEGUN! Defeat the colony guards or hold the outpost until time expires!")
3103-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
3104-        attacker.sendSystemMessage(Component
3105-                .literal("âš  You have " + TaxConfig.PLAYER_LIVES_IN_WAR.get()
3106-                        + " lives. Lose them all and the outpost's owner wins!")
3107-                .withStyle(ChatFormatting.RED));
3108-
3109-        WARSYSTEM_LOGGER.info("Offline outpost war started: {} vs {} (defender offline)",
3110-                attackerColony.getName(), targetColony.getName());
3111-    }
3112-
3113-    public static int processWageWarRequestWithExtortion(ServerPlayer attacker, IColony targetColony,
3114-            CommandSourceStack source, int extortionPercent) {
3115-        Level level = source.getLevel();
3116-
3117-        int targetGuards = countGuards(targetColony);
3118-        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) {
3119-            source.sendFailure(Component.literal("Target colony must have at least "
3120-                    + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
3121-            return 0;
3122-        }
3123-
3124-        // Find a valid attacker colony using the new helper
3125-        IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);
3126-
3127-        if (attackerColony == null) {
3128-            // Retained specific error messaging logic for better user feedback
3129-            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
3130-                    .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
3131-                    .findFirst().orElse(null);
3132-
3133-            if (anyColony == null) {
3134-                source.sendFailure(Component.literal("You must own a colony to declare war."));
3135-            } else {
3136-                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
3137-                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
3138-                            .checkWarRequirements(anyColony);
3139-                    source.sendFailure(Component.literal("None of your colonies meet the war requirements. Example ("
3140-                            + anyColony.getName() + "): " + reqs.message));
3141-                } else {
3142-                    source.sendFailure(
3143-                            Component.literal("None of your colonies have enough guards or resources to declare war."));
3144-                }
3145-            }
3146-            return 0;
3147-        }
3148-
--
4234-                        .filter(c -> c.getID() == e.defenderColonyId).findFirst().orElse(null);
4235-            }
4236-            if (e.attackerColonyId > 0 && attackerColony == null) {
4237-                attackerColony = IColonyManager.getInstance().getColonies(level).stream()
4238-                        .filter(c -> c.getID() == e.attackerColonyId).findFirst().orElse(null);
4239-            }
4240-            if (defenderColony != null && (e.attackerColonyId <= 0 || attackerColony != null))
4241-                break;
4242-        }
4243-
4244-        if (defenderColony == null) {
4245-            WARSYSTEM_LOGGER.warn("Cannot restore war {}: defender colony {} no longer exists", e.warID,
4246-                    e.defenderColonyId);
4247-            return false;
4248-        }
4249-        if (e.attackerColonyId > 0 && attackerColony == null) {
4250-            WARSYSTEM_LOGGER.warn("Cannot restore war {}: attacker colony {} no longer exists", e.warID,
4251-                    e.attackerColonyId);
4252-            return false;
4253-        }
4254-
4255-        WarData.WarStatus status;
4256-        try {
4257-            status = WarData.WarStatus.valueOf(e.status);
4258-        } catch (IllegalArgumentException ex) {
4259-            WARSYSTEM_LOGGER.warn("Cannot restore war {}: invalid status '{}'", e.warID, e.status);
4260-            return false;
4261-        }
4262-
4263-        Map<UUID, Integer> attackerLives = new HashMap<>();
4264-        if (e.attackerLives != null) {
4265-            e.attackerLives.forEach((k, v) -> attackerLives.put(UUID.fromString(k), v));
4266-        }
4267-        Map<UUID, Integer> defenderLives = new HashMap<>();
4268-        if (e.defenderLives != null) {
4269-            e.defenderLives.forEach((k, v) -> defenderLives.put(UUID.fromString(k), v));
4270-        }
4271-        Set<Integer> defenderGuardIDSet = e.defenderGuardIDs != null ? new HashSet<>(e.defenderGuardIDs) : new HashSet<>();
4272-        Set<Integer> attackerGuardIDSet = e.attackerGuardIDs != null ? new HashSet<>(e.attackerGuardIDs) : new HashSet<>();
4273-        Set<UUID> attackerAlliesSet = parseUUIDList(e.attackerAllies);
4274-        Set<UUID> defenderAlliesSet = parseUUIDList(e.defenderAllies);
4275-        Set<UUID> spectatorsSet = parseUUIDList(e.spectators);
4276-        Set<UUID> lastLifeSet = parseUUIDList(e.lastLifeInventoryPreservation);
4277-
4278-        long now = System.currentTimeMillis();
4279-        // Finding 5: wars that ran out their clock while the server was down
4280-        // previously logged "expired during server downtime, skipping" and gave
4281-        // the victor zero rewards / no occupation / no rank cleanup. Instead,
4282-        // construct the WarData, register it in ACTIVE_WARS, then immediately
4283-        // run the normal end-of-war handler so reparations, ranks, occupation
4284:        // hooks, and history all fire. handleTimeExpiry() is the canonical
4285-        // end-of-time-elapsed entry point used by the countdown tick.
4286-        boolean expiredDuringDowntime = false;
4287-        if (status == WarData.WarStatus.INWAR) {
4288-            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4289-            if (now >= e.warStartTime + warDurationMs) {
4290:                WARSYSTEM_LOGGER.info("War {} expired during server downtime â€” resolving via handleTimeExpiry", e.warID);
4291-                expiredDuringDowntime = true;
4292-            }
4293-        } else if (status == WarData.WarStatus.JOINING) {
4294-            if (now >= e.joinPhaseEndTime) {
4295-                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
4296-                status = WarData.WarStatus.INWAR;
4297-                e.warStartTime = now;
4298-            }
4299-        }
4300-
4301-        // Convert sentinel team IDs (written by saveActiveWars for wars whose
4302-        // team IDs were null at save time) back into null so callers see the
4303-        // same invariant they had before the save.
4304-        UUID atkTid;
4305-        UUID defTid;
4306-        try {
4307-            atkTid = UUID.fromString(e.attackerTeamID);
4308-            if (NULL_TEAM_ID_SENTINEL.equals(atkTid)) atkTid = null;
4309-        } catch (IllegalArgumentException iae) { atkTid = null; }
4310-        try {
4311-            defTid = UUID.fromString(e.defenderTeamID);
4312-            if (NULL_TEAM_ID_SENTINEL.equals(defTid)) defTid = null;
4313-        } catch (IllegalArgumentException iae) { defTid = null; }
4314-
4315-        ServerBossEvent bossEvent = new ServerBossEvent(
4316-                Component.literal("War for " + defenderColony.getName()),
4317-                BossEvent.BossBarColor.RED,
4318-                BossEvent.BossBarOverlay.PROGRESS);
4319-        bossEvent.setProgress(1.0f);
4320-        bossEvent.setVisible(true);
4321-
4322-        // Reconstruct restored fields (formerly silently dropped â€” see WarData.java).
4323-        Map<Action, Boolean> restoredHostilePerms = null;
4324-        if (e.originalHostilePerms != null) {
4325-            restoredHostilePerms = new HashMap<>();
4326-            for (Map.Entry<String, Boolean> en : e.originalHostilePerms.entrySet()) {
4327-                try { restoredHostilePerms.put(Action.valueOf(en.getKey()), en.getValue()); }
4328-                catch (IllegalArgumentException ignored) {} // forward-compat: skip unknown Action names
4329-            }
4330-        }
4331-        Map<Action, Boolean> restoredHostilePermsAtk = null;
4332-        if (e.originalHostilePermsForAttacker != null) {
4333-            restoredHostilePermsAtk = new HashMap<>();
4334-            for (Map.Entry<String, Boolean> en : e.originalHostilePermsForAttacker.entrySet()) {
4335-                try { restoredHostilePermsAtk.put(Action.valueOf(en.getKey()), en.getValue()); }
4336-                catch (IllegalArgumentException ignored) {}
4337-            }
4338-        }
4339-        Set<UUID> acceptedAlliesSet = parseUUIDList(e.acceptedAllies);
4340-        Set<UUID> declinedAlliesSet = parseUUIDList(e.declinedAllies);
4341-        net.machiavelli.minecolonytax.peace.PeaceProposal restoredProposal = null;
4342-        if (e.activeProposal != null && e.activeProposal.type != null && e.activeProposal.proposer != null) {
4343-            try {
4344-                restoredProposal = new net.machiavelli.minecolonytax.peace.PeaceProposal(
4345-                        net.machiavelli.minecolonytax.peace.PeaceProposal.Type.valueOf(e.activeProposal.type),
4346-                        e.activeProposal.amount,
4347-                        UUID.fromString(e.activeProposal.proposer));
4348-                // PeaceProposal.createdTime defaults to "now" on construction â€” close
4349-                // enough for restored proposals; the timeout check is a relative delta.
4350-            } catch (IllegalArgumentException ignored) {}
4351-        }
4352-
4353-        WarData warData = new WarData(
4354-                UUID.fromString(e.warID),
4355-                UUID.fromString(e.attacker),
4356-                UUID.fromString(e.defender),
4357-                atkTid,
4358-                defTid,
4359-                e.warStartTime, e.joinPhaseEndTime,
4360-                bossEvent, defenderColony, attackerColony,
4361-                status, e.accepted,
4362-                e.initialAttackerGuards, e.remainingAttackerGuards,
4363-                e.initialDefenderGuards, e.remainingDefenderGuards,
4364-                e.initialAttackerTotalLives, e.initialDefenderTotalLives,
4365-                attackerLives, defenderLives,
4366-                defenderGuardIDSet, attackerGuardIDSet, attackerAlliesSet, defenderAlliesSet,
4367-                spectatorsSet, lastLifeSet,
4368-                e.penaltyReport, e.stalemateTriggered,
4369-                restoredHostilePerms, restoredHostilePermsAtk,
4370-                acceptedAlliesSet, declinedAlliesSet,
4371-                e.offlineOutpostWar,
4372-                restoredProposal);
4373-
4374-        ACTIVE_WARS.put(e.defenderColonyId, warData);
4375-
4376-        // Finding 5 cont'd: war ran past its clock while we were down. Register
4377:        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
4378:        // then immediately resolve. handleTimeExpiry takes care of victor logic,
4379-        // reparations, ranks, etc. If the canonical end-of-time path lives at
4380-        // a different entrypoint in this codebase, this still has the WarData
4381:        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
4382:        // TODO: if handleTimeExpiry isn't safe at boot (e.g. needs world ticks),
4383-        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
4384-        if (expiredDuringDowntime) {
4385-            try {
4386:                handleTimeExpiry(warData);
4387-            } catch (Throwable t) {
4388:                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
4389-                try { endWar(defenderColony); } catch (Throwable t2) {
4390-                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
4391-                    ACTIVE_WARS.remove(e.defenderColonyId);
4392-                }
4393-            }
4394-            // True for the loader: we processed this war successfully.
4395-            return true;
4396-        }
4397-
4398-        // Restore defender tracking + drain scheduling
4399-        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(e.defenderColonyId);
4400-        scheduleTreasuryDrain(warData, defenderColony, attackerColony);
4401-
4402-        for (UUID uuid : warData.getAttackerLives().keySet()) {
4403-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
4404-            if (p != null)
4405-                bossEvent.addPlayer(p);
4406-        }
4407-        for (UUID uuid : warData.getDefenderLives().keySet()) {
4408-            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
4409-            if (p != null)
4410-                bossEvent.addPlayer(p);
4411-        }
4412-
4413-        setWarInteractionPermissions(defenderColony, true);
4414-        if (attackerColony != null) {
4415-            setWarInteractionPermissions(attackerColony, true);
4416-        }
4417-
4418-        final IColony finalDefenderColony = defenderColony;
4419-
4420-        if (warData.getStatus() == WarData.WarStatus.INWAR) {
4421-            applyWarGlowToParticipants(warData);
4422-            applyGuardGlow(defenderColony);
4423-            if (attackerColony != null) {
4424-                applyGuardGlow(attackerColony);
4425-            }
4426-
4427-            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
4428-            if (attackerColony != null) {
4429-                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
4430-            }
4431-
4432:            startWarCountdown(warData);
4433-
4434-            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4435-            long elapsed = now - warData.warStartTime;
4436-            long remaining = warDurationMs - elapsed;
4437-            if (remaining > 0) {
4438-                scheduleTimerWarnings(warData, remaining);
4439-            }
4440-
4441-            updateBossBar(warData);
4442-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
4443-                    e.warID, defenderColony.getName(), remaining);
4444-        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
4445-            warData.alliesBossEvent = new ServerBossEvent(
4446-                    Component.literal("Joining War - " + defenderColony.getName()),
4447-                    BossEvent.BossBarColor.YELLOW,
4448-                    BossEvent.BossBarOverlay.PROGRESS);
4449-            warData.alliesBossEvent.setProgress(1.0f);
4450-            warData.alliesBossEvent.setVisible(true);
4451-
4452-            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
4453-            if (remainingJoinMs > 0) {
4454-                final int colonyId = e.defenderColonyId;
4455-                TickScheduler.scheduleDelayed(() -> {
4456-                    if (!ACTIVE_WARS.containsKey(colonyId))
4457-                        return;
4458-                    WarData w = ACTIVE_WARS.get(colonyId);
4459-                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
4460-                        return;
4461-                    w.setStatus(WarData.WarStatus.INWAR);
4462-                    w.warStartTime = System.currentTimeMillis();
4463-                    finalizeWarStart(w);
4464-                    setWarInteractionPermissions(w.getColony(), true);
4465-                    if (w.getAttackerColony() != null) {
4466-                        setWarInteractionPermissions(w.getAttackerColony(), true);
4467-                    }
4468:                    startWarCountdown(w);
4469-                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4470-                    scheduleTimerWarnings(w, warDurationMillis);
4471-                }, remainingJoinMs);
4472-            }
4473-
4474-            updateBossBar(warData);
4475-            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",
4476-                    e.warID, defenderColony.getName(), remainingJoinMs);
4477-        }
4478-
4479-        Component restoreMsg = Component.literal("âš” War Restored: ")
4480-                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
4481-                .append(Component
4482-                        .literal("The war for " + finalDefenderColony.getName()
4483-                                + " has been resumed after server restart.")
4484-                        .withStyle(ChatFormatting.YELLOW));
4485-        broadcastToServer(restoreMsg);
4486-
4487-        return true;
4488-    }
4489-
4490-    private static Set<UUID> parseUUIDList(List<String> list) {
4491-        Set<UUID> result = new HashSet<>();
4492-        if (list != null) {
4493-            for (String s : list) {
4494-                try {
4495-                    result.add(UUID.fromString(s));
4496-                } catch (IllegalArgumentException ignored) {
4497-                }
4498-            }
4499-        }
4500-        return result;
4501-    }
4502-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 354ms:
33-@Mod(MineColonyTax.MOD_ID)
34-public class MineColonyTax {
35-    public static final String MOD_ID = "minecolonytax";
36-    public static final Logger LOGGER = LogManager.getLogger();
37-
38-    /**
39-     * Guards against registering FCT event handlers more than once.
40-     * DefaultEventBus is a JVM-lifetime singleton â€” in single-player, onServerStarting
41-     * fires on every world load without clearing the handler list. Without this flag,
42-     * each world load would add another duplicate handler.
43-     */
44-    private static boolean fctEventBusSubscribed = false;
45-
46-    public MineColonyTax() {
47-        // COMMON config type writes to /config/warntax/ only, not world/serverconfig/
48-        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TaxConfig.CONFIG, "warntax/minecolonytax.toml");
49-
50-        ModRecipeSerializers.RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());
51-
52-        net.machiavelli.minecolonytax.espionage.ModEntities.ENTITIES
53-                .register(FMLJavaModLoadingContext.get().getModEventBus());
54-
55-        // Step 11 â€” siege banner block + item registration. Block is always
56-        // registered; the Plant-the-Banner objective gates behaviour at runtime
57-        // via EnableExperimentalSiegeObjectives.
58:        net.machiavelli.minecolonytax.siege.ModSiegeBlocks.register(
59-                FMLJavaModLoadingContext.get().getModEventBus());
60-
61-        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
62-        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
63-
64-        MinecraftForge.EVENT_BUS.register(this);
65-
66-        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.event.RaidKillTracker.class);
67:        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.util.TickScheduler.class);
68-    }
69-
70-    private void setup(final FMLCommonSetupEvent event) {
71-        event.enqueueWork(() -> {
72-            NetworkHandler.register();
73-            LOGGER.info("MineColonyTax setup complete");
74-        });
75-    }
76-
77-    private void clientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
78-        // Set Patchouli config flag if admin pages are enabled
79-        if (ModList.get().isLoaded("patchouli") && TaxConfig.SHOW_ADMIN_PAGES_IN_BOOK.get()) {
80-            try {
81-                Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
82-                Object instance = apiClass.getMethod("get").invoke(null);
83-                instance.getClass().getMethod("setConfigFlag", String.class, boolean.class)
84-                        .invoke(instance, "minecolonytax:show_admin", true);
85-                if (TaxConfig.isNormalLogging()) LOGGER.info("Registered Patchouli flag 'minecolonytax:show_admin'");
86-            } catch (Exception e) {
87-                LOGGER.warn("Failed to set Patchouli config flag: {}", e.getMessage());
88-            }
89-        }
90-    }
91-
92-    @SubscribeEvent
--
112-        // Subscribe to colony lifecycle events so FirstColonyTracker stays accurate.
113-        // Guard with a static flag: DefaultEventBus is a JVM-lifetime singleton and
114-        // does not clear its handler list between server starts in single-player mode.
115-        if (!fctEventBusSubscribed) {
116-            fctEventBusSubscribed = true;
117-            com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus()
118-                    .subscribe(com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent.class, e -> {
119-                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
120-                        if (ownerUUID != null) {
121-                            FirstColonyTracker.addColony(ownerUUID, e.getColony().getID());
122-                        }
123-                    });
124-            com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus()
125-                    .subscribe(com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent.class, e -> {
126-                        // Event fires before cap.deleteColony() so permissions are still readable here
127-                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
128-                        if (ownerUUID != null) {
129-                            FirstColonyTracker.removeColony(ownerUUID, e.getColony().getID());
130-                        }
131-                    });
132-        }
133-
134-        // Deferred bootstrap: seed FirstColonyTracker for colonies that existed before this
135-        // mod was installed. Runs after MineColonies finishes loading all colonies.
136-        // Orders colonies by ID ascending so the lowest-ID (oldest) colony becomes primary.
137:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
138-            try {
139-                com.minecolonies.api.colony.IColonyManager cm =
140-                        com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager();
141-                FirstColonyTracker.bootstrapFromExistingColonies(cm);
142-            } catch (Exception e) {
143-                LOGGER.error("FirstColonyTracker bootstrap failed", e);
144-            }
145-        }, 6000);
146-
147-        net.machiavelli.minecolonytax.economy.WarExhaustionManager.initialize(event.getServer());
148-
149-        // Load persisted permission snapshots BEFORE clearing bits so existing snapshots
150-        // survive the defaults restore and are available when wars re-enable permissions.
151-        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.loadFromFile();
152-
153-        WarSystem.restoreAllColonyPermissionsToDefaults();
154-
155-        try {
156-            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
157-        } catch (Exception e) {
158-            LOGGER.error("Immediate null owner fix failed", e);
159-        }
160-
161-        // Deferred safety passes let MineColonies finish loading colonies before the fix runs
162:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
163-            try {
164-                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
165-                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
166-            } catch (Exception e) {
167-                LOGGER.error("Delayed null owner fix failed", e);
168-            }
169-        }, 1000);
170-
171:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
172-            try {
173-                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
174-            } catch (Exception e) {
175-                LOGGER.error("Final null owner fix failed", e);
176-            }
177-        }, 3000);
178-
179-        VassalManager.initialize(event.getServer());
180-
181-        TreasuryManager.initialize(event.getServer());
182-        if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager initialized");
183-
184-        FactionManager.init();
185-        if (TaxConfig.isNormalLogging()) LOGGER.info("FactionManager initialized");
186-
187-        TaxPolicyManager.initialize(event.getServer());
188-        if (TaxConfig.isNormalLogging()) LOGGER.info("TaxPolicyManager initialized");
189-
190-        net.machiavelli.minecolonytax.events.random.RandomEventManager.initialize(event.getServer());
191-        if (TaxConfig.isNormalLogging()) LOGGER.info("RandomEventManager initialized");
192-
193-        HistoryManager.loadHistory();
194-        if (TaxConfig.isNormalLogging()) LOGGER.info("HistoryManager loaded");
195-
196-        if (TaxConfig.isSpySystemEnabled()) {
--
203-            if (TaxConfig.isNormalLogging()) LOGGER.info("ColonyUpgradeManager initialized");
204-        }
205-
206-        GuardResistanceHandler.emergencyCleanup();
207-
208-        // War persistence must be restored after the colony manager is ready
209-        try {
210-            WarSystem.loadAndResumeActiveWars();
211-            if (TaxConfig.isNormalLogging()) LOGGER.info("War persistence restoration complete");
212-        } catch (Throwable t) {
213-            LOGGER.error("Failed to restore active wars: {}", t.toString());
214-        }
215-
216-        // Restore Hostile rank to pre-conflict state for any colonies whose conflict
217-        // ended while the server was down (snapshot exists but no active war/raid).
218-        try {
219-            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreAllStale(event.getServer());
220-        } catch (Exception e) {
221-            LOGGER.error("Failed to run stale permission snapshot restore", e);
222-        }
223-
224-        // Permissions health check: runs after wars are restored so that legitimately
225-        // hostile-ranked players in active wars are not incorrectly demoted.
226-        // Deferred ~5 s to let MineColonies finish loading all colony data.
227-        // This catches stale Hostile rank assignments left by crashes or older mod versions.
228:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
229-            try {
230-                net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.Result result =
231-                        net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.run(event.getServer());
232-                if (result.hasIssues()) {
233-                    LOGGER.warn("[PermissionsHealthCheck] Corrected stale permission state on startup: {}", result.summary());
234-                } else if (TaxConfig.isNormalLogging()) {
235-                    LOGGER.info("[PermissionsHealthCheck] {}", result.summary());
236-                }
237-            } catch (Exception e) {
238-                LOGGER.error("[PermissionsHealthCheck] Startup health check failed", e);
239-            }
240-        }, 5000);
241-
242-        // Warn if MineColonies pvp_mode is enabled â€” it bypasses ALL colony permission
243-        // checks for players whose guards enter enemy territory, overriding our per-action
244-        // permission system. Wars and raids should use WNT's own permission management.
245-        // NOTE: com.minecolonies.core.MineColonies is an INTERNAL class (not the api package);
246-        // any refactor on the MC side can throw NoClassDefFoundError (an Error, not Exception),
247-        // so the catch must trap Throwable. The whole block is also gated on ModList.isLoaded.
248-        if (ModList.get().isLoaded("minecolonies")) {
249-            try {
250-                if (com.minecolonies.core.MineColonies.getConfig().getServer().pvp_mode.get()) {
251-                    LOGGER.warn("[WNT] MineColonies pvp_mode is ENABLED. This bypasses colony permission "
252-                            + "checks for players who rally guards into enemy colonies, overriding War 'N Taxes "
253-                            + "permission management. Set pvp_mode = false in minecolonies-server.toml for "
254-                            + "correct war/raid permission behavior.");
255-                }
256-            } catch (Throwable t) {
257-                // Config not available yet, or MineColonies internal class moved â€” ignore.
258-                if (TaxConfig.isDebugLogging()) {
259-                    LOGGER.debug("[WNT] Could not read MineColonies pvp_mode (likely internal API change): {}", t.toString());
260-                }
261-            }
262-        }
263-
264-        try {
265-            net.machiavelli.minecolonytax.occupation.OccupationManager.initialize(event.getServer());
266-            if (TaxConfig.isNormalLogging()) LOGGER.info("OccupationManager initialized");
267-        } catch (Throwable t) {
268-            LOGGER.error("Failed to initialize OccupationManager: {}", t.toString());
269-        }
270-
271-        if (TaxConfig.isBesiegeSystemEnabled()) {
272-            try {
273-                net.machiavelli.minecolonytax.besiege.BesiegeManager.initialize(event.getServer());
274-                if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
275:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
276-                    try {
277-                        net.machiavelli.minecolonytax.besiege.BesiegeManager.tick();
278-                    } catch (Throwable t) {
279-                        LOGGER.error("Error in BesiegeManager tick: {}", t.toString());
280-                    }
281-                }, 1000, 1000);
282-            } catch (Throwable t) {
283-                LOGGER.error("Failed to initialize BesiegeManager: {}", t.toString());
284-            }
285-        }
286-
287:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
288-            try {
289-                net.machiavelli.minecolonytax.occupation.OccupationManager.checkExpiredOccupations();
290-            } catch (Throwable t) {
291-                LOGGER.error("Error checking expired occupations: {}", t.toString());
292-            }
293-        }, 300_000, 300_000);
294-
295-        if (TaxConfig.isDatabaseEnabled()) {
296-            try {
297-                WarStatsDB.initialize();
298-                if (TaxConfig.isNormalLogging()) LOGGER.info("WarStatsDB initialized");
299-                long snapshotMs = TaxConfig.getDatabaseSnapshotIntervalSeconds() * 1000L;
300:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
301-                    try {
302-                        WarStatsDB.snapshotAllColonies(event.getServer());
303-                        WarStatsDB.updateServerState(event.getServer());
304-                    } catch (Throwable t) {
305-                        LOGGER.error("Error during colony DB snapshot: {}", t.toString());
306-                    }
307-                }, snapshotMs, snapshotMs);
308-            } catch (Exception e) {
309-                LOGGER.error("Failed to initialize WarStatsDB: {}", e.getMessage());
310-            }
311-        } else {
312-            if (TaxConfig.isDebugLogging()) LOGGER.debug("WarStats database is disabled in configuration");
313-        }
314-    }
315-
316-    @SubscribeEvent
317-    public void onServerStopping(ServerStoppingEvent event) {
318-        try {
319-            WarStatsDB.shutdown();
320-            if (TaxConfig.isNormalLogging()) LOGGER.info("WarStatsDB shutdown complete");
321-        } catch (Throwable t) {
322-            LOGGER.warn("Error during WarStatsDB shutdown: {}", t.toString());
323-        }
324-
325-        try {
--
356-        } catch (Throwable t) {
357-            LOGGER.warn("Error during TaxPolicyManager shutdown: {}", t.toString());
358-        }
359-
360-        try {
361-            net.machiavelli.minecolonytax.events.random.RandomEventManager.shutdown();
362-            if (TaxConfig.isNormalLogging()) LOGGER.info("RandomEventManager shutdown complete");
363-        } catch (Throwable t) {
364-            LOGGER.warn("Error during RandomEventManager shutdown: {}", t.toString());
365-        }
366-
367-        try {
368-            HistoryManager.saveHistory();
369-            if (TaxConfig.isNormalLogging()) LOGGER.info("HistoryManager saved");
370-        } catch (Throwable t) {
371-            LOGGER.warn("Error saving HistoryManager: {}", t.toString());
372-        }
373-
374-        try {
375-            net.machiavelli.minecolonytax.espionage.SpyManager.shutdown();
376-            if (TaxConfig.isNormalLogging()) LOGGER.info("SpyManager shutdown complete");
377-        } catch (Throwable t) {
378-            LOGGER.warn("Error during SpyManager shutdown: {}", t.toString());
379-        }
380-
381:        // Save active wars before TickScheduler shutdown â€” task IDs are still needed for cleanup
382-        try {
383-            WarSystem.saveActiveWars();
384-            if (TaxConfig.isNormalLogging()) LOGGER.info("Active wars saved to disk");
385-        } catch (Throwable t) {
386-            LOGGER.warn("Error saving active wars: {}", t.toString());
387-        }
388-
389-        try {
390-            net.machiavelli.minecolonytax.occupation.OccupationManager.shutdown();
391-            if (TaxConfig.isNormalLogging()) LOGGER.info("OccupationManager shutdown complete");
392-        } catch (Throwable t) {
393-            LOGGER.warn("Error during OccupationManager shutdown: {}", t.toString());
394-        }
395-
396-        try {
397-            net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.shutdown();
398-            if (TaxConfig.isNormalLogging()) LOGGER.info("ColonyUpgradeManager shutdown complete");
399-        } catch (Throwable t) {
400-            LOGGER.warn("Error during ColonyUpgradeManager shutdown: {}", t.toString());
401-        }
402-
403-        try {
404-            net.machiavelli.minecolonytax.besiege.BesiegeManager.shutdown();
405-            if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
406-        } catch (Throwable t) {
407-            LOGGER.warn("Error during BesiegeManager shutdown: {}", t.toString());
408-        }
409-
410-        try {
411:            net.machiavelli.minecolonytax.util.TickScheduler.shutdown();
412:            if (TaxConfig.isNormalLogging()) LOGGER.info("TickScheduler shutdown complete");
413-        } catch (Throwable t) {
414:            LOGGER.warn("Error during TickScheduler shutdown: {}", t.toString());
415-        }
416-
417-        // Must run AFTER all manager shutdowns above so any final saveData()
418-        // they queued is flushed to disk before the JVM exits.
419-        try {
420-            net.machiavelli.minecolonytax.util.AsyncSaveExecutor.shutdownAndFlush();
421-            if (TaxConfig.isNormalLogging()) LOGGER.info("AsyncSaveExecutor flushed");
422-        } catch (Throwable t) {
423-            LOGGER.warn("Error flushing AsyncSaveExecutor: {}", t.toString());
424-        }
425-    }
426-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS: REWORK

- `setPlacedBy -> onBannerPlaced` is server-safe: `level.isClientSide()` and `ServerPlayer` guards prevent client/static misuse. Null/non-player placers also no-op safely.
- Banner block detection in `BreakEvent` works with `instanceof SiegeBannerBlock`, but `== ModSiegeBlocks.SIEGE_BANNER.get()` would be stricter. Bigger issue: the handler does not verify the breaker is a defender, so attackers/other permitted players can cancel the capture and consume the replant budget.
- Replant semantics are correct for “1 replant”: initial place does not increment; each break increments; `replantsUsed > 1` allows the first replant and rejects after the second break.
- Boss bar cleanup is fine: `removeAllPlayers()` sends client removal, and `setVisible(false)` is consistent with existing project cleanup patterns.
- Banner handout does skip offline players, but I do not see a retry path on login. `WarEventHandler.onPlayerLogin` re-adds war boss bars only for tracked disconnected players and never gives the banner.
- `destroyBannerSilently` is probably state-safe server-side, but it runs after placement has already succeeded, so invalid placement can still consume the item and cause transient place/remove updates. The 1Hz task vs `onWarEnded` is okay under `TickScheduler` server-thread semantics: state removal + cancel make later ticks no-op.

Fix suggestions:
1. In `onBlockBreak`, require `war.getDefenderLives().containsKey(event.getPlayer().getUUID())`; cancel or ignore non-defender breaks.
2. Extract `giveSiegeBannerIfNeeded(ServerPlayer, WarData)` and call it from `finalizeWarStart` and login/reconnect handling.
3. Prefer cancelling invalid placement via `BlockEvent.EntityPlaceEvent` so the item is not consumed and the block never transiently exists.
tokens used
125.587
STATUS: REWORK

- `setPlacedBy -> onBannerPlaced` is server-safe: `level.isClientSide()` and `ServerPlayer` guards prevent client/static misuse. Null/non-player placers also no-op safely.
- Banner block detection in `BreakEvent` works with `instanceof SiegeBannerBlock`, but `== ModSiegeBlocks.SIEGE_BANNER.get()` would be stricter. Bigger issue: the handler does not verify the breaker is a defender, so attackers/other permitted players can cancel the capture and consume the replant budget.
- Replant semantics are correct for “1 replant”: initial place does not increment; each break increments; `replantsUsed > 1` allows the first replant and rejects after the second break.
- Boss bar cleanup is fine: `removeAllPlayers()` sends client removal, and `setVisible(false)` is consistent with existing project cleanup patterns.
- Banner handout does skip offline players, but I do not see a retry path on login. `WarEventHandler.onPlayerLogin` re-adds war boss bars only for tracked disconnected players and never gives the banner.
- `destroyBannerSilently` is probably state-safe server-side, but it runs after placement has already succeeded, so invalid placement can still consume the item and cause transient place/remove updates. The 1Hz task vs `onWarEnded` is okay under `TickScheduler` server-thread semantics: state removal + cancel make later ticks no-op.

Fix suggestions:
1. In `onBlockBreak`, require `war.getDefenderLives().containsKey(event.getPlayer().getUUID())`; cancel or ignore non-defender breaks.
2. Extract `giveSiegeBannerIfNeeded(ServerPlayer, WarData)` and call it from `finalizeWarStart` and login/reconnect handling.
3. Prefer cancelling invalid placement via `BlockEvent.EntityPlaceEvent` so the item is not consumed and the block never transiently exists.
