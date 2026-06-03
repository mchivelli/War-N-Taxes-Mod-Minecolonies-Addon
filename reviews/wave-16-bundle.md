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
