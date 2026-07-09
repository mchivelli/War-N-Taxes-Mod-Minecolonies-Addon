package net.machiavelli.minecolonytax.besiege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
// Intentionally do NOT import com.minecolonies.core.entity.mobs.EntityMercenary —
// it lives in the INTERNAL core package, not the api/* surface. We work through the
// API-typed EntityType<? extends PathfinderMob> ModEntities.MERCENARY and operate on
// the result as a Mob, so a future class move/rename cannot brick this manager.
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.machiavelli.minecolonytax.FirstColonyTracker;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.militia.MilitiaAttackGoal;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the besiege system — single-player raids on active non-primary colonies
 * that grant tax vassalage on victory, and the reclaim flow for the former owner.
 */
public class BesiegeManager {

    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";

    /**
     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
     * raid the same colony — look them up by colony with {@link #getRaidsForColony(int)}.
     *
     * Phase 2 follow-up: defender pool is still per-raid, not shared across
     * concurrent besiegers on the same colony. Each besieger currently spawns its
     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
     * are not yet implemented.
     */
    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();

    /**
     * Secondary index: colonyId → set of besiegerUUIDs currently raiding it.
     * Maintained in lock-step with ACTIVE_RAIDS so hot-path lookups
     * (isActiveRaidOnColony, getRaidsForColony, container deny checks)
     * are O(1) instead of O(activeRaids).
     */
    private static final Map<Integer, Set<UUID>> COLONY_RAID_INDEX = new ConcurrentHashMap<>();

    /** Persistent occupation records (colonyId -> occupation data). */
    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();

    /** Per-player cooldown map (playerUUID -> timestamp when cooldown expires). */
    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();

    /**
     * A besiege that has been DECLARED but not yet begun: the target has a short window
     * ({@code BesiegePrepMinutes}) to answer it — Defend it, or Buy it off — before the
     * siege launches automatically. Keyed by colonyId (one live offer per colony).
     */
    public static final class PendingBesiege {
        public final int colonyId;
        public final UUID besiegerUUID;
        public final long deadlineMs;
        PendingBesiege(int colonyId, UUID besiegerUUID, long deadlineMs) {
            this.colonyId = colonyId;
            this.besiegerUUID = besiegerUUID;
            this.deadlineMs = deadlineMs;
        }
    }

    /** Declared-but-not-started besieges awaiting the defender's answer (colonyId -> offer). */
    private static final Map<Integer, PendingBesiege> PENDING_BESIEGES = new ConcurrentHashMap<>();

    /**
     * Buy-off breather: after a target buys off a besieger, that besieger cannot besiege the
     * SAME colony again until this expires. Key = besiegerUUID + ":" + colonyId -> expiry ms.
     * In-memory like {@link #PLAYER_COOLDOWNS}; a restart clears it.
     */
    private static final Map<String, Long> BUYOFF_COOLDOWNS = new ConcurrentHashMap<>();

    private static String buyoffKey(UUID besieger, int colonyId) { return besieger + ":" + colonyId; }

    private static MinecraftServer SERVER;

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData(server);
        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
    }

    public static void shutdown() {
        saveData();
        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
            cleanupRaid(raid, false);
        }
        ACTIVE_RAIDS.clear();
        COLONY_RAID_INDEX.clear();
        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
    }

    public static void tick() {
        // Declared-but-not-started besieges resolve on their own timer, independent of active raids.
        processPendingBesieges();
        if (ACTIVE_RAIDS.isEmpty()) return;

        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
            BesiegeRaidData raid = entry.getValue();

            try {
                IColony colony = getColonyById(raid.colonyId);
                if (colony == null) {
                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
                    cleanupRaid(raid, false);
                    it.remove();
                    continue;
                }

                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);

                // --- Online requirement: a siege cannot be conducted from the lobby ---
                // When BesiegeRequireOnline is set, a besieger who logs off for longer than
                // BesiegeOfflineGraceMinutes has their participation cancelled. With the
                // multi-besieger shared pool, cancelling one besieger does not despawn the
                // defenders while other besiegers remain (cleanupRaid gates on the index).
                if (TaxConfig.isBesiegeRequireOnline()) {
                    if (besieger == null) {
                        if (raid.offlineSinceMs == 0) raid.offlineSinceMs = System.currentTimeMillis();
                        long graceMs = TaxConfig.getBesiegeOfflineGraceMinutes() * 60_000L;
                        if (System.currentTimeMillis() - raid.offlineSinceMs >= graceMs) {
                            if (TaxConfig.isNormalLogging())
                                LOGGER.info("Besieger {} offline past grace — cancelling their besiege of colony {}",
                                        raid.besiegingPlayerUUID, raid.colonyId);
                            cleanupRaid(raid, false);
                            applyCooldown(raid.besiegingPlayerUUID);
                            it.remove();
                            continue;
                        }
                    } else if (raid.offlineSinceMs != 0) {
                        raid.offlineSinceMs = 0; // back online — reset the grace timer
                    }
                }

                // --- Timer expired: defenders win ---
                if (System.currentTimeMillis() >= raid.endTime) {
                    if (TaxConfig.isNormalLogging())
                        LOGGER.info("Besiege raid on colony {} timed out — defenders win", colony.getName());
                    sendToPlayer(raid.besiegingPlayerUUID,
                            Component.literal("The besiege of " + colony.getName() + " has failed — the defenders held out!")
                                    .withStyle(ChatFormatting.RED));
                    broadcastToNearbyPlayers(colony,
                            Component.literal(colony.getName() + " successfully repelled the besiege!")
                                    .withStyle(ChatFormatting.GREEN), 200);
                    // Route through completeBesiege so siege spoils + cooldown + cleanup all fire
                    // via a single path. Previously the timeout cleaned up directly, skipping
                    // defender-victory siege spoils entirely.
                    completeBesiege(raid, false, colony,
                            java.util.Collections.singletonList(raid.besiegingPlayerUUID));
                    it.remove();
                    continue;
                }

                // --- Besieger left the area ---
                if (besieger != null) {
                    BlockPos center = colony.getCenter();
                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
                    if (dist > (double) maxRadius * maxRadius) {
                        besieger.sendSystemMessage(Component.literal(
                                "You left the besiege area — the raid has been cancelled!")
                                .withStyle(ChatFormatting.RED));
                        cleanupRaid(raid, false);
                        applyCooldown(raid.besiegingPlayerUUID);
                        it.remove();
                        continue;
                    }

                    // Track allies: anyone who recently damaged a defender
                    // (ally tracking is done in the kill/hurt event — see RaidKillTracker integration)
                }

                // --- Victory: all defenders dead ---
                if (allDefendersDead(raid, colony)) {
                    if (TaxConfig.isNormalLogging())
                        LOGGER.info("Besiege raid on colony {} resolved this tick — defenders are down", colony.getName());

                    // Gather EVERY besieger currently participating on this colony. The one
                    // this tick happened to resolve first is the "lead" (receives the
                    // vassalization tribute); the others are co-victors who share the
                    // treasury spoils (BesiegeShareSpoils). participants drives both the
                    // 'not solo' minimum-attacker gate and the spoils split.
                    java.util.List<BesiegeRaidData> coBesiegers = new java.util.ArrayList<>();
                    java.util.List<UUID> participants = new java.util.ArrayList<>();
                    participants.add(raid.besiegingPlayerUUID);
                    for (UUID otherUUID : new java.util.HashSet<>(
                            COLONY_RAID_INDEX.getOrDefault(raid.colonyId, java.util.Collections.emptySet()))) {
                        if (otherUUID.equals(raid.besiegingPlayerUUID)) continue;
                        BesiegeRaidData other = ACTIVE_RAIDS.get(otherUUID);
                        if (other != null) { coBesiegers.add(other); participants.add(otherUUID); }
                    }

                    // Drop ALL concurrent raids on this colony from the index first, so the
                    // winner's cleanup path is deterministic (defenders are already dead, so
                    // there is nothing live left to despawn either way).
                    Set<UUID> indexEntry = COLONY_RAID_INDEX.remove(raid.colonyId);
                    if (indexEntry != null) indexEntry.clear();

                    // The colony's defenders are down and every raid on it is being torn
                    // down this tick (capture below, or min-attacker collapse). Because the
                    // index entry was just cleared, cleanupRaid's "last raid" check can no
                    // longer fire its despawn — so despawn the shared militia-upgrade
                    // reinforcements here. Guards/mercenaries are already dead (per
                    // allDefendersDead); militia are NOT victory-counted and can still be
                    // alive. despawnAll is idempotent and clears the (possibly shared-by-
                    // reference) set, so the co-besieger calls are safe no-ops.
                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(raid.militiaSupport);
                    for (BesiegeRaidData co : coBesiegers) {
                        net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(co.militiaSupport);
                    }

                    // 'Not solo' rule: the attacking force must meet BesiegeMinAttackers to
                    // actually claim the colony. If too few took part, the defenders fell but
                    // the colony is NOT captured — raids end with cooldowns and no spoils.
                    int minAttackers = TaxConfig.getBesiegeMinAttackers();
                    if (participants.size() < minAttackers) {
                        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                                "The defenders of " + colony.getName() + " fell, but your force was too small to hold it (needs "
                                        + minAttackers + " besiegers). Rally allies and try again.")
                                .withStyle(ChatFormatting.YELLOW));
                        cleanupRaid(raid, true);
                        applyCooldown(raid.besiegingPlayerUUID);
                        it.remove();
                        for (BesiegeRaidData co : coBesiegers) {
                            try {
                                sendToPlayer(co.besiegingPlayerUUID, Component.literal(
                                        "The besiege of " + colony.getName() + " collapsed — too few attackers to claim it.")
                                        .withStyle(ChatFormatting.YELLOW));
                                cleanupRaid(co, true);
                                applyCooldown(co.besiegingPlayerUUID);
                            } catch (Exception ex) {
                                LOGGER.warn("Failed to end co-besieger raid for {}: {}",
                                        co.besiegingPlayerUUID, ex.getMessage());
                            }
                        }
                        continue;
                    }

                    // Enough attackers — capture. Lead besieger gets vassalization; spoils are
                    // split across all participants' primary colonies inside completeBesiege.
                    completeBesiege(raid, true, colony, participants);
                    it.remove();

                    for (BesiegeRaidData co : coBesiegers) {
                        try {
                            sendToPlayer(co.besiegingPlayerUUID, Component.literal(
                                    "Victory! You shared in the successful siege of " + colony.getName() + ".")
                                    .withStyle(ChatFormatting.GREEN));
                            cleanupRaid(co, true);
                            applyCooldown(co.besiegingPlayerUUID);
                            if (TaxConfig.isNormalLogging())
                                LOGGER.info("Co-besieger {} shared in victory on colony {}",
                                        co.besiegingPlayerUUID, colony.getName());
                        } catch (Exception ex) {
                            LOGGER.warn("Failed to finalize co-besieger raid for {}: {}",
                                    co.besiegingPlayerUUID, ex.getMessage());
                        }
                    }
                    continue;
                }

                // --- Update boss bar ---
                updateBossBar(raid, colony);

            } catch (Exception e) {
                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
            }
        }
    }


    /**
     * Start a besiege raid. Validates all preconditions.
     * Returns true if the raid started successfully.
     */
    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
        if (!TaxConfig.isBesiegeSystemEnabled()) {
            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID besiegerUUID = besieger.getUUID();
        int colonyId = colony.getID();

        // 1. Must own at least one colony themselves
        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
        if (besiegerColony == null) {
            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 2. Cannot besiege own colony
        if (colony.getPermissions().getOwner() != null
                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
            besieger.sendSystemMessage(Component.literal("You cannot besiege your own colony.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 2b. The target's owner must be ONLINE. A besiege is meant to be a fight the defender can
        // answer (see the call-to-arms below), not a smash-and-grab on a sleeping player. If the owner
        // is offline, the besiege is simply unavailable.
        UUID targetOwner = colony.getPermissions().getOwner();
        if (targetOwner == null || SERVER == null || SERVER.getPlayerList().getPlayer(targetOwner) == null) {
            besieger.sendSystemMessage(Component.literal(
                    "You can only besiege a colony whose owner is online — give them a chance to defend.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 3. Primary colonies CAN now be besieged. Outcome routes through
        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.

        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
        // Multiple besiegers attacking the SAME colony concurrently is allowed.
        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
            besieger.sendSystemMessage(Component.literal(
                    "You already have an active besiege. Only one besiege at a time per player.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 5. Cooldown check
        Long cooldownExpiry = PLAYER_COOLDOWNS.get(besiegerUUID);
        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
            besieger.sendSystemMessage(Component.literal(
                    "You must wait " + remaining + " more minute(s) before besieging again.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 6. Min colony size
        int citizenCount = colony.getCitizenManager().getCitizens().size();
        if (citizenCount < TaxConfig.getBesiegeMinColonySize()) {
            besieger.sendSystemMessage(Component.literal(
                    "Target colony is too small to besiege (needs at least "
                            + TaxConfig.getBesiegeMinColonySize() + " citizens).")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 7. Cannot besiege a colony that is currently in an active war
        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
            besieger.sendSystemMessage(Component.literal(
                    "This colony is already engaged in an active war. Besiege is not available during wartime.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 8. Cannot besiege a colony that is already a vassal of this player
        if (VassalManager.isColonyVassal(colonyId)
                && VassalManager.getVassalOverlordUUID(colonyId) != null
                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
            besieger.sendSystemMessage(Component.literal("This colony is already your vassal.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 9. Buy-off breather: this colony recently bought THIS besieger off — they must wait
        // before shaking it down again (gives the target time to find allies and fight back).
        Long buyoffExpiry = BUYOFF_COOLDOWNS.get(buyoffKey(besiegerUUID, colonyId));
        if (buyoffExpiry != null && System.currentTimeMillis() < buyoffExpiry) {
            long remaining = (buyoffExpiry - System.currentTimeMillis()) / 60000;
            besieger.sendSystemMessage(Component.literal(
                    colony.getName() + " bought you off recently — you must wait " + remaining
                            + " more minute(s) before besieging them again.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // If a siege is ALREADY raging on this colony, join it directly (shared defender pool);
        // the prep / buy-off phase only applies to the first, not-yet-started declaration.
        if (!getRaidsForColony(colonyId).isEmpty()) {
            return launchRaid(colony, besieger, false);
        }

        // Otherwise DECLARE the besiege and give the target a window to Defend it or Buy it off.
        if (PENDING_BESIEGES.containsKey(colonyId)) {
            besieger.sendSystemMessage(Component.literal(
                    colony.getName() + " already has a besiege declared against it right now.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return declareBesiege(colony, besieger);
    }

    /**
     * Declare a besiege WITHOUT starting the fight. Registers a pending offer and gives the target
     * colony a window ({@code BesiegePrepMinutes}) to answer — Defend it (via {@code /wnt defend}) or
     * Buy it off (via {@code /wnt buyoff}). If the window elapses with no answer, {@link #tick()}
     * launches the siege automatically. This is what gives defenders time to rally allies.
     */
    private static boolean declareBesiege(IColony colony, ServerPlayer besieger) {
        int colonyId = colony.getID();
        int prepMin = TaxConfig.getBesiegePrepMinutes();
        int buyoffPct = TaxConfig.getBesiegeBuyoffPercent();
        long deadline = System.currentTimeMillis() + Math.max(1, prepMin) * 60_000L;
        PENDING_BESIEGES.put(colonyId, new PendingBesiege(colonyId, besieger.getUUID(), deadline));

        besieger.sendSystemMessage(Component.literal(
                "You have declared a besiege on " + colony.getName() + ". They have " + prepMin
                        + " minute(s) to defend or buy you off, then the siege begins.")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Call-to-arms with BOTH answers. Buttons RUN player-safe commands (no op needed); the colony
        // name is quoted so names with spaces still parse.
        Component msg = Component.literal("WARNING: ")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(besieger.getName().getString())
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" has declared a besiege on ")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal(colony.getName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("! You have " + prepMin + " min. ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[Defend it]")
                        .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/wnt defend \"" + colony.getName() + "\""))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Stand and fight — teleport in and rally your defenders")))))
                .append(Component.literal(" "))
                .append(Component.literal("[Buy it off — " + buyoffPct + "%]")
                        .withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/wnt buyoff \"" + colony.getName() + "\""))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Pay " + buyoffPct
                                                + "% of your treasury to call off the siege (owner only)")))));
        notifyColonyDefenders(colony, msg);
        return true;
    }

    /** True while a besiege has been declared on this colony but has not yet started. */
    public static boolean hasPendingBesiege(int colonyId) {
        return PENDING_BESIEGES.containsKey(colonyId);
    }

    /**
     * A defender chose to FIGHT: if a besiege is pending on this colony, launch the real siege now
     * against the declared besieger. Returns true when a siege is active afterwards (just launched
     * or already ongoing). Safe to call with nothing pending.
     */
    public static boolean commitDefend(IColony colony) {
        PendingBesiege pending = PENDING_BESIEGES.remove(colony.getID());
        if (pending == null) {
            return !getRaidsForColony(colony.getID()).isEmpty();   // already active, or nothing to commit
        }
        ServerPlayer besieger = SERVER != null ? SERVER.getPlayerList().getPlayer(pending.besiegerUUID) : null;
        if (besieger == null) {
            notifyColonyDefenders(colony, Component.literal(
                    "The besieger has left; the assault on " + colony.getName() + " is called off.")
                    .withStyle(ChatFormatting.GREEN));
            return false;
        }
        return launchRaid(colony, besieger, false);
    }

    /**
     * The target's OWNER buys off a declared besiege: pay {@code BesiegeBuyoffPercent} of the colony's
     * treasury to the besieger's primary colony, cancel the pending siege, and put that besieger on a
     * per-colony cooldown so they cannot immediately shake the colony down again. No-ops (with a chat
     * note) when nothing is pending or the caller is not the owner.
     */
    public static boolean buyOff(IColony colony, ServerPlayer caller) {
        int colonyId = colony.getID();
        PendingBesiege pending = PENDING_BESIEGES.get(colonyId);
        if (pending == null) {
            caller.sendSystemMessage(Component.literal(
                    "There is no declared besiege to buy off on " + colony.getName() + ".")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }
        UUID owner = colony.getPermissions().getOwner();
        if (owner == null || !owner.equals(caller.getUUID())) {
            caller.sendSystemMessage(Component.literal(
                    "Only the owner of " + colony.getName() + " can buy off its besiege.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        int percent = TaxConfig.getBesiegeBuyoffPercent();
        IColony besiegerColony = getPrimaryColonyOfPlayer(pending.besiegerUUID);
        int paid = 0;
        if (percent > 0 && besiegerColony != null) {
            int chest = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);
            int requested = (int) Math.floor(chest * (percent / 100.0));
            if (requested > 0) {
                // Cap-safe (same pattern as siege spoils): credit the besieger first, then deduct
                // exactly what actually landed so coins are never created or lost.
                int before = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(besiegerColony.getID());
                int after = net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(besiegerColony.getID(), requested);
                paid = after - before;
                if (paid > 0) net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(colonyId, paid);
            }
        }

        // Cancel the offer and start the shakedown breather for this besieger-colony pair.
        PENDING_BESIEGES.remove(colonyId);
        long cd = Math.max(0L, (long) TaxConfig.getBesiegeBuyoffCooldownHours()) * 3_600_000L;
        if (cd > 0) BUYOFF_COOLDOWNS.put(buyoffKey(pending.besiegerUUID, colonyId), System.currentTimeMillis() + cd);

        final int paidFinal = paid;
        caller.sendSystemMessage(Component.literal(
                "You bought off " + getPlayerName(pending.besiegerUUID) + "'s besiege of " + colony.getName()
                        + " for " + paidFinal + " coins. They cannot besiege you again for a while.")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        sendToPlayer(pending.besiegerUUID, Component.literal(
                colony.getName() + " bought off your besiege — you extorted " + paidFinal + " coins and withdraw. "
                        + "You must wait before you can march on them again.")
                .withStyle(ChatFormatting.GOLD));
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Besiege of colony {} bought off by owner for {} coins (besieger {})",
                    colony.getName(), paidFinal, pending.besiegerUUID);
        return true;
    }

    /** Launch declared besieges whose prep window has elapsed with no answer (or drop the offer if the
     *  besieger has since gone offline). Driven from {@link #tick()}. */
    private static void processPendingBesieges() {
        if (PENDING_BESIEGES.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, PendingBesiege>> it = PENDING_BESIEGES.entrySet().iterator(); it.hasNext(); ) {
            PendingBesiege p = it.next().getValue();
            if (now < p.deadlineMs) continue;
            it.remove();
            IColony colony = getColonyById(p.colonyId);
            if (colony == null) continue;
            ServerPlayer besieger = SERVER != null ? SERVER.getPlayerList().getPlayer(p.besiegerUUID) : null;
            if (besieger == null) {
                notifyColonyDefenders(colony, Component.literal(
                        "The besieger never pressed the attack; " + colony.getName() + " is left in peace.")
                        .withStyle(ChatFormatting.GREEN));
                continue;
            }
            notifyColonyDefenders(colony, Component.literal(
                    "Time is up — the siege of " + colony.getName() + " begins!")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            launchRaid(colony, besieger, false);
        }
    }

    /**
     * Start a reclaim raid — former owner/officer taking back their besieged colony.
     */
    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
        if (!TaxConfig.isBesiegeSystemEnabled()) {
            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        int colonyId = colony.getID();
        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
        if (occ == null) {
            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID playerUUID = reclaimingPlayer.getUUID();
        if (!occ.formerOwnerUUID.equals(playerUUID)) {
            reclaimingPlayer.sendSystemMessage(Component.literal(
                    "Only the former owner can reclaim this colony via besiege.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Solo rule: this player may not already have an active raid.
        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
            reclaimingPlayer.sendSystemMessage(Component.literal(
                    "You already have an active besiege/reclaim raid.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Cooldown check
        Long cooldownExpiry = PLAYER_COOLDOWNS.get(playerUUID);
        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
            reclaimingPlayer.sendSystemMessage(Component.literal(
                    "You must wait " + remaining + " more minute(s) before attempting a reclaim.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        reclaimingPlayer.sendSystemMessage(Component.literal(
                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
                .withStyle(ChatFormatting.GOLD));
        return launchRaid(colony, reclaimingPlayer, true);
    }


    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
        int colonyId = colony.getID();
        UUID besiegerUUID = besieger.getUUID();

        try {
            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
            try { raid.dimension = colony.getDimension(); } catch (Exception ignored) { /* fallback handled in mixin */ }

            // Step 3 Phase 2 — multi-besieger shared defender pool.
            // Find any already-active raid on this colony BEFORE inserting our own.
            // If one exists, this besieger is a "secondary raider": share the
            // primary's defender pool by reference so the colony's defenders aren't
            // doubled, and skip the spawn calls that would otherwise create extra
            // mercenaries/militia.
            //
            // PUBLICATION ORDER (codex finding wave-15): assign the shared
            // references on `raid` BEFORE publishing to ACTIVE_RAIDS / COLONY_RAID_INDEX
            // so that a concurrent tick/reader can never see the new raid with
            // empty private sets and act on it as if it had no defenders.
            List<BesiegeRaidData> existingForColony = getRaidsForColony(colonyId);
            BesiegeRaidData primary = existingForColony.isEmpty() ? null : existingForColony.get(0);

            if (primary != null) {
                raid.isSecondaryRaider = true;
                raid.hostileCitizenIds = primary.hostileCitizenIds;
                raid.spawnedMercenaries = primary.spawnedMercenaries;
                raid.militiaSupport = primary.militiaSupport;
            }

            ACTIVE_RAIDS.put(besiegerUUID, raid);
            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);

            // Grant the besieger hostile rank + combat permissions on the colony
            // so MineColonies allows the player to attack citizens.
            grantBesiegeCombatPermissions(colony, besiegerUUID);

            int guardCount;
            int militiaCount;
            int mercCount;

            if (primary != null) {
                // Secondary raider — share already established above.
                // Defender counts come from the shared pool.
                guardCount = (int) primary.hostileCitizenIds.stream()
                        .filter(id -> {
                            try {
                                ICitizenData c = colony.getCitizenManager().getCivilian(id);
                                return c != null && c.getJob() != null && c.getJob().isGuard();
                            } catch (Exception e) { return false; }
                        }).count();
                militiaCount = primary.hostileCitizenIds.size() - guardCount;
                mercCount = primary.spawnedMercenaries.size();
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Secondary besieger {} joining existing raid on colony {} — sharing defender pool ({}g {}m {}e)",
                            besiegerUUID, colony.getName(), guardCount, militiaCount, mercCount);
                }
            } else {
                // Primary raider — spawn the defender pool normally.
                guardCount = makeGuardsHostile(colony, besieger, raid);
                militiaCount = convertCitizensToMilitia(colony, besieger, raid);
                mercCount = spawnMercenaries(colony, besieger, raid);
                spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);
                applyFortificationBonus(colony, raid);
            }

            int totalDefenders = guardCount + militiaCount + mercCount;

            // Create boss bar (per-besieger; each gets their own)
            createBossBar(raid, besieger, colony, totalDefenders);

            String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
            besieger.sendSystemMessage(Component.literal(
                    verb + " STARTED: " + colony.getName()
                            + " | Defenders: " + totalDefenders
                            + " | Time: " + TaxConfig.getBesiegeDurationMinutes() + "m")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

            broadcastToNearbyPlayers(colony,
                    Component.literal("Nearby colony " + colony.getName()
                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
                            + " by " + besieger.getName().getString() + "!")
                            .withStyle(ChatFormatting.YELLOW), 200);

            // Notify owner + officers + friends — the defender's call-to-arms.
            // Friends are included per the Siege SMP defender-ally rule: defenders may
            // mobilize allies even when the attacker must stand alone.
            // The "[Defend it]" button RUNS a player-safe command (/wnt defend) that teleports the
            // defender to their colony. It used to SUGGEST an op-only "/tp ...", which normal players
            // could neither run nor even fire on click — the reason defenders couldn't answer the call.
            // The colony name is quoted so names with spaces still parse.
            net.minecraft.network.chat.Component callToArms = Component.literal("WARNING: ")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal(besieger.getName().getString())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" is besieging your colony ")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                    .append(Component.literal(colony.getName())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("! ")
                            .withStyle(ChatFormatting.DARK_RED))
                    .append(Component.literal("[Defend it]")
                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                            "/wnt defend \"" + colony.getName() + "\""))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Teleport to your colony and join the defense")))));
            notifyColonyDefenders(colony, callToArms);

            if (TaxConfig.isNormalLogging())
                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);

            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
            ACTIVE_RAIDS.remove(besiegerUUID);
            removeFromColonyIndex(colonyId, besiegerUUID);
            return false;
        }
    }

    /** Remove a besieger from the colony→raid index; drop the colony entry when empty. */
    private static void removeFromColonyIndex(int colonyId, UUID besiegerUUID) {
        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
        if (set == null) return;
        set.remove(besiegerUUID);
        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
    }


    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
        int count = 0;
        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
            if (entityOpt.isEmpty()) continue;
            AbstractEntityCitizen guard = entityOpt.get();

            try {
                applyDefenderAI(guard, besieger, raid);
                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
                raid.hostileCitizenIds.add(citizenData.getId());
                count++;
            } catch (Exception e) {
                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
            }
        }
        return count;
    }

    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
        List<ICitizenData> eligible = new ArrayList<>();
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen.isChild()) continue;
            if (citizen.getEntity().isEmpty()) continue;
            if (citizen.getJob() != null && citizen.getJob().isGuard()) continue;
            if (citizen.getJob() != null) {
                String jobPath = citizen.getJob().getJobRegistryEntry().getKey().getPath();
                if (jobPath.equals("deliveryman")) continue;
            }
            eligible.add(citizen);
        }

        Collections.shuffle(eligible);
        int target = (int) Math.ceil(eligible.size() * TaxConfig.getBesiegeMilitiaPercent());
        int count = 0;

        for (ICitizenData citizen : eligible) {
            if (count >= target) break;
            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
            if (entityOpt.isEmpty()) continue;
            AbstractEntityCitizen entity = entityOpt.get();

            try {
                // Equip wooden sword
                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
                applyDefenderAI(entity, besieger, raid);
                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
                raid.hostileCitizenIds.add(citizen.getId());
                count++;
            } catch (Exception e) {
                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
            }
        }
        return count;
    }

    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
        entity.goalSelector.removeAllGoals(g -> true);
        entity.targetSelector.removeAllGoals(g -> true);

        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal — non-guard citizens
        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
        // crash with IllegalArgumentException.
        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));

        // Retaliate against anyone who hits them (covers allies)
        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));

        // Proactively hunt the besieger (and any allies)
        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
                20, true, false, (target) -> {
                    if (!(target instanceof ServerPlayer sp)) return false;
                    return sp.getUUID().equals(besieger.getUUID())
                            || raid.alliedPlayers.contains(sp.getUUID());
                }));
    }

    private static void applyDefenderEffects(AbstractEntityCitizen entity, int durationMinutes) {
        int ticks = durationMinutes * 60 * 20;
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
    }

    /**
     * Applies bonus DAMAGE_RESISTANCE to all besiege defenders based on the
     * colony's FORTIFICATION investment level. Called once after all defenders
     * are spawned in launchRaid().
     * Each 20% damage reduction from the investment adds +1 resistance amplifier.
     */
    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
        if (!TaxConfig.isUpgradesEnabled()) return;
        double dmgReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                .getFortificationDamageReduction(colony.getID());
        if (dmgReduction <= 0) return;
        int extraAmplifier = (int) (dmgReduction / 0.20);
        if (extraAmplifier <= 0) return;

        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
        // Re-apply DAMAGE_RESISTANCE to citizens with the boosted amplifier
        for (int citizenId : raid.hostileCitizenIds) {
            colony.getCitizenManager().getCitizens().stream()
                    .filter(c -> c.getId() == citizenId)
                    .findFirst()
                    .flatMap(ICitizenData::getEntity)
                    .ifPresent(entity -> entity.addEffect(
                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
        }
        // Also boost mercenaries
        for (Entity merc : raid.spawnedMercenaries) {
            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
            }
        }
        if (TaxConfig.isDebugLogging())
            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
                    colony.getName(), extraAmplifier);
    }

    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
        Level world = colony.getWorld();
        if (!(world instanceof ServerLevel)) return 0;

        int buildingCount = 0;
        try {
            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
                    .getBuildings(colony).size();
        } catch (Exception e) {
            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
        }

        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
        if (count <= 0) return 0;

        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            try {
                // Use the API-typed superclass — never reference the internal
                // EntityMercenary class. See import block for rationale.
                PathfinderMob merc = com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                if (merc == null) continue;

                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
                merc.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                ((Mob) merc).setTarget(besieger);

                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));

                world.addFreshEntity(merc);
                raid.spawnedMercenaries.add(merc);
                spawned++;
            } catch (Throwable e) {
                // Catch Throwable so a NoClassDefFoundError on the internal mercenary
                // class (referenced transitively through the EntityType create() return)
                // cannot propagate up the besiege tick path.
                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
            }
        }
        return spawned;
    }


    /**
     * Spawns militia-upgrade bonus defenders for a besiege.
     * Delegates to the shared {@link net.machiavelli.minecolonytax.militia.MilitiaSpawner}
     * so besiege and full-war militia spawning share one implementation.
     */
    private static int spawnMilitiaUpgradeReinforcements(IColony colony, ServerPlayer besieger,
            BesiegeRaidData raid, int guardCount) {
        return net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
                colony, guardCount, besieger, raid.militiaSupport,
                TaxConfig.getBesiegeDurationMinutes());
    }

    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony,
            java.util.List<UUID> participantBesiegers) {
        cleanupRaid(raid, true);
        applyCooldown(raid.besiegingPlayerUUID);

        if (attackerWon) {
            applySiegeSpoils(raid, colony, true, participantBesiegers);
            if (raid.isReclaim) {
                completeReclaim(raid, colony);
            } else {
                completeBesiegeVictory(raid, colony);
            }
        } else {
            applySiegeSpoils(raid, colony, false, participantBesiegers);
            sendToPlayer(raid.besiegingPlayerUUID,
                    Component.literal("The besiege of " + colony.getName() + " failed.")
                            .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
     *
     * Honours both sides' treasury caps: the actual credited amount is the lesser
     * of (computed spoil) and (winner's remaining headroom). The deduction matches
     * that credited amount, so coins are never lost to the cap.
     */
    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon,
            java.util.List<UUID> participantBesiegers) {
        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
        if (percent <= 0) return;

        if (attackerWon) {
            applySharedAttackerSpoils(defenderColony, raid, participantBesiegers, percent);
        } else {
            // Defender win — the single besieger's primary colony pays the defender colony.
            IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
            if (besiegerColony == null) return;
            int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(besiegerColony.getID());
            if (loserBalance <= 0) return;
            int requestedSpoil = (int) Math.floor(loserBalance * (percent / 100.0));
            if (requestedSpoil <= 0) return;
            int winnerBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(defenderColony.getID());
            int winnerCap = net.machiavelli.minecolonytax.economy.TreasuryManager.getEffectiveMaxCapacity(defenderColony.getID());
            int headroom = Math.max(0, winnerCap - winnerBalance);
            int actualSpoil = Math.min(requestedSpoil, headroom);
            if (actualSpoil <= 0) return;
            net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(besiegerColony.getID(), actualSpoil);
            net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(defenderColony.getID(), actualSpoil);
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Siege spoils ({}%, defender win): {} → {} = {}",
                        percent, besiegerColony.getName(), defenderColony.getName(), actualSpoil);
            }
            UUID winnerOwner = defenderColony.getPermissions().getOwner();
            UUID loserOwner = besiegerColony.getPermissions().getOwner();
            if (winnerOwner != null) sendToPlayer(winnerOwner, Component.literal("Siege spoils: " + actualSpoil
                    + " coins seized from the repelled besieger.").withStyle(ChatFormatting.GOLD));
            if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, Component.literal(
                    "Siege fine: " + actualSpoil + " coins paid to " + defenderColony.getName() + ".")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Splits the besiege treasury spoils across every participating besieger's primary
     * colony. When BesiegeShareSpoils is disabled, only the lead (resolving) besieger's
     * colony is paid. The loser colony is debited exactly the sum actually credited
     * (after each winner's cap headroom), so coins are never created or lost.
     */
    private static void applySharedAttackerSpoils(IColony defenderColony, BesiegeRaidData leadRaid,
            java.util.List<UUID> participantBesiegers, int percent) {
        // Distinct winner colonies, insertion-ordered (lead first).
        java.util.LinkedHashMap<Integer, IColony> winnerColonies = new java.util.LinkedHashMap<>();
        boolean share = TaxConfig.isBesiegeShareSpoilsEnabled() && participantBesiegers != null
                && !participantBesiegers.isEmpty();
        if (share) {
            for (UUID uuid : participantBesiegers) {
                IColony c = getPrimaryColonyOfPlayer(uuid);
                if (c != null) winnerColonies.putIfAbsent(c.getID(), c);
            }
        } else {
            IColony c = getPrimaryColonyOfPlayer(leadRaid.besiegingPlayerUUID);
            if (c != null) winnerColonies.put(c.getID(), c);
        }
        if (winnerColonies.isEmpty()) return;

        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(defenderColony.getID());
        if (loserBalance <= 0) return;
        int totalSpoil = (int) Math.floor(loserBalance * (percent / 100.0));
        if (totalSpoil <= 0) return;

        int n = winnerColonies.size();
        int base = totalSpoil / n;
        int remainder = totalSpoil % n;
        int totalCredited = 0;
        int idx = 0;
        for (IColony winner : winnerColonies.values()) {
            int portion = base + (idx < remainder ? 1 : 0);
            idx++;
            if (portion <= 0) continue;
            int winnerBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(winner.getID());
            int winnerCap = net.machiavelli.minecolonytax.economy.TreasuryManager.getEffectiveMaxCapacity(winner.getID());
            int headroom = Math.max(0, winnerCap - winnerBalance);
            int actual = Math.min(portion, headroom);
            if (actual <= 0) continue;
            net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), actual);
            totalCredited += actual;
            UUID winnerOwner = winner.getPermissions().getOwner();
            if (winnerOwner != null) sendToPlayer(winnerOwner, Component.literal("Siege spoils: " + actual
                    + " coins from " + defenderColony.getName() + (n > 1 ? " (shared)" : "") + ".")
                    .withStyle(ChatFormatting.GOLD));
        }

        if (totalCredited > 0) {
            net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(defenderColony.getID(), totalCredited);
            UUID loserOwner = defenderColony.getPermissions().getOwner();
            if (loserOwner != null && !winnerColonies.containsKey(defenderColony.getID()))
                sendToPlayer(loserOwner, Component.literal("Siege fine: " + totalCredited
                        + " coins lost from " + defenderColony.getName() + "'s treasury to the besiegers.")
                        .withStyle(ChatFormatting.RED));
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Siege spoils ({}%): {} → {} winner colony(ies), total {}",
                        percent, defenderColony.getName(), n, totalCredited);
        }
    }

    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
        // Counter-besiege reclaim handoff: if the besieger is the original owner
        // of a TAX_ONLY war-occupation on this colony, clear that occupation
        // and SHORT-CIRCUIT — the colony is already theirs, vassalizing it to
        // themselves would create a self-vassal nonsense state.
        try {
            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
            if (reclaimed) {
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Besiege victory was a counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
                            raid.besiegingPlayerUUID, colony.getName());
                }
                sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                        "You have successfully reclaimed " + colony.getName() + "!")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                broadcastToNearbyPlayers(colony,
                        Component.literal(colony.getName() + " has been reclaimed by its original owner!")
                                .withStyle(ChatFormatting.GREEN), 300);
                return; // No vassalization — the owner is whole again.
            }
        } catch (Exception e) {
            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
        }

        int tributePct = TaxConfig.getBesiegeTributePercent();
        int durationHours = TaxConfig.getBesiegeTributeDurationHours();

        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);

        if (vassalized) {
            // Store occupation record
            UUID ownerUUID = colony.getPermissions().getOwner();
            BesiegeOccupationData occ = new BesiegeOccupationData(
                    colony.getID(), colony.getName(),
                    raid.besiegingPlayerUUID, ownerUUID,
                    System.currentTimeMillis(), tributePct);
            OCCUPATIONS.put(colony.getID(), occ);
            saveData();

            // Notify former owner
            if (ownerUUID != null) {
                String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
                sendToPlayer(ownerUUID, Component.literal(
                        "Your colony " + colony.getName() + " has been besieged by " + besiegerName
                                + "! Tax tribute (" + tributePct + "%) now flows to them. "
                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
                        .withStyle(ChatFormatting.RED));
            }

            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
                    .withStyle(ChatFormatting.GREEN));

            broadcastToNearbyPlayers(colony,
                    Component.literal(colony.getName() + " has fallen under besiege occupation!")
                            .withStyle(ChatFormatting.DARK_RED), 300);

            if (TaxConfig.isNormalLogging())
                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
        } else {
            // Colony was already a vassal (edge case) — just notify
            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
        int colonyId = colony.getID();

        // Remove vassalization
        VassalManager.removeVassalRelation(colonyId);

        // Remove occupation record
        OCCUPATIONS.remove(colonyId);
        saveData();

        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                "Reclaim successful! " + colony.getName() + " is free from occupation.")
                .withStyle(ChatFormatting.GREEN));

        broadcastToNearbyPlayers(colony,
                Component.literal(colony.getName() + " has been reclaimed by its owner!")
                        .withStyle(ChatFormatting.GOLD), 300);

        if (TaxConfig.isNormalLogging())
            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
                    getPlayerName(raid.besiegingPlayerUUID));
    }


    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
        IColony colony = getColonyById(raid.colonyId);
        if (colony != null) {
            // Revoke combat permissions from the besieger (and any allies)
            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
            for (UUID ally : raid.alliedPlayers) {
                revokeBesiegeCombatPermissions(colony, ally);
            }

            // Step 3 Phase 2: only despawn shared defender entities + restore
            // citizen AI if this is the LAST raid on the colony. Other concurrent
            // raiders still reference the same hostileCitizenIds / spawnedMercenaries
            // / militiaSupport sets and rely on those entities to continue existing
            // until they too end.
            //
            // Count siblings BEFORE removing this raid from the index. We're "the
            // last" if every entry in the index points back to this raid's besieger.
            Set<UUID> siblingsOnColony = COLONY_RAID_INDEX.getOrDefault(raid.colonyId,
                    java.util.Collections.emptySet());
            boolean isLastRaidOnColony = siblingsOnColony.size() <= 1
                    && siblingsOnColony.contains(raid.besiegingPlayerUUID);

            if (isLastRaidOnColony) {
                // Restore citizen AI
                for (int citizenId : raid.hostileCitizenIds) {
                    try {
                        ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
                        if (citizen != null && citizen.getEntity().isPresent()) {
                            AbstractEntityCitizen entity = citizen.getEntity().get();
                            entity.goalSelector.removeAllGoals(g -> true);
                            entity.targetSelector.removeAllGoals(g -> true);
                            // Remove militia sword if present
                            if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
                                entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                            }
                            // Remove combat effects
                            entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                            entity.removeEffect(MobEffects.MOVEMENT_SPEED);
                            entity.removeEffect(MobEffects.DAMAGE_BOOST);
                            // Restore job AI
                            if (citizen.getJob() != null) {
                                entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
                    }
                }

                // Despawn militia-upgrade reinforcements (NOT victory-counted)
                net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(raid.militiaSupport);

                // Despawn mercenaries
                for (Entity merc : raid.spawnedMercenaries) {
                    try {
                        if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to despawn mercenary after besiege", e);
                    }
                }
            } else if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Besiege cleanup for {} on colony {} — other raids still active, skipping defender despawn",
                        raid.besiegingPlayerUUID, colony.getName());
            }
        }

        // Remove boss bar
        if (raid.bossEvent != null) {
            try {
                raid.bossEvent.removeAllPlayers();
            } catch (Exception e) {
                LOGGER.warn("Failed to remove besiege boss bar", e);
            }
            raid.bossEvent = null;
        }

        // Always clear the colony index — it's a secondary lookup, not the source
        // of truth. ACTIVE_RAIDS removal is gated so tick callers that own the
        // iterator can do it.remove() themselves; the index gets cleaned either
        // way to avoid stale colony→besieger entries blocking subsequent raids.
        removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
        if (removeFromMap) {
            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
        }
    }


    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
        try {
            int minutes = TaxConfig.getBesiegeDurationMinutes();
            Component text = Component.literal(
                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
                            colony.getName(), totalDefenders, minutes, 0))
                    .withStyle(ChatFormatting.YELLOW);

            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            raid.bossEvent.setProgress(1.0f);
            raid.bossEvent.addPlayer(besieger);

            // Add nearby players
            Level world = colony.getWorld();
            if (world instanceof ServerLevel serverLevel) {
                BlockPos center = colony.getCenter();
                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                    if (!player.equals(besieger) && player.level() == world) {
                        double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
                        if (dist <= 200.0 * 200.0) {
                            try { raid.bossEvent.addPlayer(player); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create boss bar for besiege on colony {}", colony.getName(), e);
        }
    }

    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
        if (raid.bossEvent == null) return;

        try {
            long remaining = Math.max(0, raid.endTime - System.currentTimeMillis());
            float progress = (float) remaining / (float) (TaxConfig.getBesiegeDurationMinutes() * 60_000L);
            progress = Math.max(0f, Math.min(1f, progress));

            long seconds = remaining / 1000;
            long mm = seconds / 60;
            long ss = seconds % 60;

            int aliveDefenders = countAliveDefenders(raid, colony);

            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
                    : BossEvent.BossBarColor.RED;

            raid.bossEvent.setColor(color);
            raid.bossEvent.setProgress(progress);
            raid.bossEvent.setName(Component.literal(
                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
                            colony.getName(), aliveDefenders, mm, ss)));
        } catch (Exception e) {
            LOGGER.warn("Failed to update besiege boss bar", e);
        }
    }


    public static boolean isColonyBesieged(int colonyId) {
        return OCCUPATIONS.containsKey(colonyId);
    }

    public static boolean isActiveRaidOnColony(int colonyId) {
        // Hot path — O(1) via COLONY_RAID_INDEX.
        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
        return set != null && !set.isEmpty();
    }

    /** All currently active besiege raids targeting this colony. O(matches). */
    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
        Set<UUID> besiegerUUIDs = COLONY_RAID_INDEX.get(colonyId);
        if (besiegerUUIDs == null || besiegerUUIDs.isEmpty()) return java.util.Collections.emptyList();
        List<BesiegeRaidData> matches = new ArrayList<>(besiegerUUIDs.size());
        for (UUID uuid : besiegerUUIDs) {
            BesiegeRaidData raid = ACTIVE_RAIDS.get(uuid);
            if (raid != null) matches.add(raid);
        }
        return matches;
    }

    /**
     * Returns true if the player is locked out of the colony due to besiege occupation.
     * The former owner is locked out; the besieging player is the new effective controller.
     */
    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
        if (occ == null) return false;
        // Former owner is locked out
        return occ.formerOwnerUUID != null && occ.formerOwnerUUID.equals(playerUUID);
    }

    public static boolean isBesiegingPlayer(UUID playerUUID, int colonyId) {
        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
        return occ != null && occ.besiegingPlayerUUID.equals(playerUUID);
    }

    /**
     * True if the player is a legitimate besiege combatant on this colony — the besieging
     * player or a registered ally of any ACTIVE besiege raid targeting it.
     *
     * Used by PermissionSnapshot / PermissionsHealthCheck so that a live besiege's Hostile-rank
     * grant is not mistaken for stale leftover state and cleared (which would silently neuter an
     * in-progress siege when a co-located war/raid ends or an admin runs /wnt permcheck).
     */
    public static boolean isBesiegeCombatant(UUID playerUUID, int colonyId) {
        if (playerUUID == null) return false;
        for (BesiegeRaidData raid : getRaidsForColony(colonyId)) {
            if (playerUUID.equals(raid.besiegingPlayerUUID)) return true;
            if (raid.alliedPlayers.contains(playerUUID)) return true;
        }
        return false;
    }

    /**
     * Called from RaidKillTracker to register an allied player to ALL raids
     * targeting this colony. With multi-besieger, several besiegers may target
     * the same colony — registering the ally on each gives them combat rights
     * regardless of which besieger they're supporting.
     *
     * Note: the new Siege SMP solo-besiege rule (step 5) blocks attacker-side
     * allies via a damage shield. This method remains for any defender-ally
     * tracking and for legacy callers; the besiege-allies config still gates it.
     */
    public static void registerAlly(int colonyId, UUID allyUUID) {
        if (!TaxConfig.isBesiegeAlliesEnabled()) return;
        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
        if (raids.isEmpty()) return;
        IColony colony = getColonyById(colonyId);
        for (BesiegeRaidData raid : raids) {
            if (raid.alliedPlayers.add(allyUUID) && colony != null) {
                grantBesiegeCombatPermissions(colony, allyUUID);
            }
        }
    }

    /** Check whether a player is on cooldown. */
    public static boolean isOnCooldown(UUID playerUUID) {
        Long expiry = PLAYER_COOLDOWNS.get(playerUUID);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public static BesiegeOccupationData getOccupation(int colonyId) {
        return OCCUPATIONS.get(colonyId);
    }

    /**
     * Backward-compatible view of active raids keyed by colonyId.
     *
     * Since multi-besieger support landed, the internal storage is keyed by
     * besieger UUID. This view returns at most ONE raid per colony (the first
     * one encountered). Callers that need ALL raids for a colony must use
     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
     * raid should use {@link #getRaidForBesieger(UUID)}.
     */
    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
        Map<Integer, BesiegeRaidData> view = new HashMap<>();
        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
            view.putIfAbsent(raid.colonyId, raid);
        }
        return Collections.unmodifiableMap(view);
    }

    /**
     * O(1), allocation-free "is any besiege active?" check. Use this on hot guard
     * paths (e.g. the block-interaction filter) instead of {@code getActiveRaids().isEmpty()},
     * which builds and discards a HashMap on every call (audit H6).
     */
    public static boolean hasActiveRaids() {
        return !ACTIVE_RAIDS.isEmpty();
    }

    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
        return ACTIVE_RAIDS.get(besiegerUUID);
    }

    /** Read-only view of all active raids keyed by besieger UUID. */
    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
        return Collections.unmodifiableMap(ACTIVE_RAIDS);
    }

    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
        return Collections.unmodifiableMap(OCCUPATIONS);
    }


    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
        return countAliveDefenders(raid, colony) == 0;
    }

    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
        int alive = 0;
        // Citizens
        for (int citizenId : raid.hostileCitizenIds) {
            try {
                ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
                if (citizen != null && citizen.getEntity().isPresent()
                        && citizen.getEntity().get().isAlive()) {
                    alive++;
                }
            } catch (Exception ignored) {}
        }
        // Mercenaries
        for (Entity merc : raid.spawnedMercenaries) {
            if (merc.isAlive()) alive++;
        }
        return alive;
    }

    private static void applyCooldown(UUID playerUUID) {
        long cooldownMs = TaxConfig.getBesiegeCooldownHours() * 3600_000L;
        PLAYER_COOLDOWNS.put(playerUUID, System.currentTimeMillis() + cooldownMs);
    }

    private static BlockPos findSpawnPos(BlockPos center, Level world) {
        Random rng = new Random();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getX() + rng.nextInt(20) - 10;
            int z = center.getZ() + rng.nextInt(20) - 10;
            BlockPos surface = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            if (world.getBlockState(surface).isAir() && !world.getBlockState(surface.below()).isAir()) {
                return surface;
            }
        }
        return center;
    }

    private static void broadcastToNearbyPlayers(IColony colony, Component message, int radius) {
        Level world = colony.getWorld();
        if (!(world instanceof ServerLevel serverLevel)) return;
        BlockPos center = colony.getCenter();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == world) {
                double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
                if (dist <= (double) radius * radius) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private static void sendToPlayer(UUID uuid, Component message) {
        if (SERVER == null) return;
        ServerPlayer player = SERVER.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(message);
        }
        // Offline messages are not queued for the besiege system (unlike VassalManager)
        // because besiege outcomes are time-sensitive and stale messages would confuse players.
    }

    /**
     * Sends a message to the colony owner and all online officers, regardless of distance.
     * Used to ensure colonists responsible for defense are always informed of a besiege.
     */
    private static void notifyColonyOwnersAndOfficers(IColony colony, Component message) {
        if (SERVER == null) return;
        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
        // Owner
        sendToPlayer(perms.getOwner(), message);
        // Officers
        try {
            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
                    : perms.getPlayersByRank(perms.getRankOfficer())) {
                sendToPlayer(cp.getID(), message);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Defender call-to-arms: notifies owner, officers, AND friends.
     * Per the Siege SMP rule, defender allies may answer the besiege; attacker
     * allies are blocked by the solo-besiege damage shield (step 5).
     */
    private static void notifyColonyDefenders(IColony colony, Component message) {
        if (SERVER == null) return;
        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
        java.util.Set<UUID> notified = new java.util.HashSet<>();
        UUID owner = perms.getOwner();
        if (owner != null && notified.add(owner)) {
            sendToPlayer(owner, message);
        }
        try {
            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
                    : perms.getPlayersByRank(perms.getRankOfficer())) {
                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
                    sendToPlayer(cp.getID(), message);
                }
            }
        } catch (Exception ignored) {}
        try {
            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
                    : perms.getPlayersByRank(perms.getRankFriend())) {
                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
                    sendToPlayer(cp.getID(), message);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getPlayerName(UUID uuid) {
        if (SERVER == null) return uuid.toString();
        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : uuid.toString();
    }

    private static IColony getColonyById(int colonyId) {
        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
    }

    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
        // Prefer FCT: it tracks the true first colony regardless of permissions state
        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
        if (firstColonyId != null) {
            IColony first = cm.getAllColonies().stream()
                    .filter(c -> c.getID() == firstColonyId)
                    .findFirst().orElse(null);
            if (first != null) return first;
        }
        // Fallback: any colony where the player is listed as MC owner
        for (IColony c : cm.getAllColonies()) {
            UUID owner = c.getPermissions().getOwner();
            if (owner != null && owner.equals(playerId)) return c;
        }
        return null;
    }


    /**
     * Grants the besieging player hostile rank and combat permissions on the target colony.
     * Without this, MineColonies blocks all player attacks on citizens with
     * "You do not have permission to do this in this colony!".
     */
    private static void grantBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        try {
            IPermissions perms = colony.getPermissions();
            // Snapshot before modifying (for restore on cleanup)
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);

            // Assign the player to Hostile rank so guards treat them as enemy
            Rank hostile = perms.getRankHostile();
            perms.setPlayerRank(playerUUID, hostile, colony.getWorld());

            // Enable combat actions on the hostile rank
            for (Action a : TaxConfig.getWarActions()) {
                perms.setPermission(hostile, a, true);
            }

            if (TaxConfig.isDebugLogging())
                LOGGER.debug("Granted besiege combat permissions to {} on colony {}",
                        playerUUID, colony.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to grant besiege combat permissions for {} on colony {}",
                    playerUUID, colony.getName(), e);
        }
    }

    /**
     * Revokes combat permissions and demotes the player from hostile back to neutral.
     * Called during raid cleanup.
     */
    private static void revokeBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        try {
            IPermissions perms = colony.getPermissions();

            // Disable combat actions on hostile rank
            Rank hostile = perms.getRankHostile();
            for (Action a : TaxConfig.getWarActions()) {
                perms.setPermission(hostile, a, false);
            }

            // Demote player back to neutral (skip if they are the colony owner)
            UUID owner = perms.getOwner();
            if (!playerUUID.equals(owner)) {
                Rank neutral = perms.getRankNeutral();
                perms.setPlayerRank(playerUUID, neutral, colony.getWorld());
            }

            if (TaxConfig.isDebugLogging())
                LOGGER.debug("Revoked besiege combat permissions from {} on colony {}",
                        playerUUID, colony.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to revoke besiege combat permissions for {} on colony {}",
                    playerUUID, colony.getName(), e);
        }
    }

    private static void loadData(MinecraftServer server) {
        File f = new File(server.getServerDirectory(), STORAGE_FILE);
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
            if (list != null) {
                for (BesiegeOccupationData occ : list) {
                    OCCUPATIONS.put(occ.colonyId, occ);
                }
            }
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load besiege occupation data", e);
        }
    }

    private static void saveData() {
        if (SERVER == null) return;
        // Snapshot on the calling (server) thread.
        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);

        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
            // Atomic write: serialize to a sibling .tmp file, then rename over the final
            // path so a crash mid-write cannot leave a truncated besiege occupation file.
            File tmpFile = new File(f.getParentFile(), f.getName() + ".tmp");
            try {
                f.getParentFile().mkdirs();
                try (FileWriter w = new FileWriter(tmpFile)) {
                    GSON.toJson(list, w);
                }
                try {
                    Files.move(tmpFile.toPath(), f.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException atomicEx) {
                    // Some filesystems (notably across drive letters on Windows) cannot
                    // ATOMIC_MOVE — fall back to a regular replace.
                    Files.move(tmpFile.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save besiege occupation data", e);
                // Best-effort cleanup of the temp file so it doesn't accumulate.
                if (tmpFile.exists()) {
                    try { tmpFile.delete(); } catch (Exception ignored) { /* nothing else to do */ }
                }
            }
        });
    }


    /** Transient raid state — not persisted. */
    public static class BesiegeRaidData {
        public final int colonyId;
        public final UUID besiegingPlayerUUID;
        public final long startTime;
        public final long endTime;
        /**
         * Defender pool sets. NOT {@code final} — when a secondary besieger joins
         * an already-besieged colony, these three references are reassigned to
         * point at the primary raid's sets, so all concurrent raids see the SAME
         * defender pool (no doubling). The primary raid keeps its original sets
         * even after it ends, so secondary raids' references remain valid.
         */
        public Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
        public Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
        /**
         * Militia upgrade reinforcements. Tracked separately from spawnedMercenaries
         * because by design they extend combat without counting toward victory.
         * allDefendersDead and countAliveDefenders intentionally ignore this set.
         * Also shared by reference for secondary raiders.
         */
        public Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
        public final BlockPos colonyCenter;
        public ServerBossEvent bossEvent;
        public final boolean isReclaim;
        /**
         * True when this raid is a "secondary" — joined after a primary was
         * already underway on the same colony. Secondary raids share the defender
         * pool by reference and skip merc/militia spawning. Used to gate cleanup
         * so secondary cleanup doesn't despawn shared entities.
         */
        public boolean isSecondaryRaider = false;

        /**
         * Wall-clock millis when the besieging player was first seen offline, or 0 when
         * they are online. Used by the BesiegeRequireOnline rule: a besieger offline
         * longer than BesiegeOfflineGraceMinutes has their participation cancelled.
         * Reset to 0 the moment they come back online.
         */
        public long offlineSinceMs = 0;

        /**
         * Short-TTL cache of "is this player a colony-mate of the besieger?" keyed
         * by the damage source's UUID. Value = {@code long[]{result(0/1), expiryMs}}.
         * LivingHurtEvent fires constantly, so without this the damage shield scanned
         * ALL colonies on every hit; the TTL collapses per-hit bursts to one scan per
         * few seconds per attacker while still catching mid-besiege rank changes
         * (audit C3 + codex follow-up).
         */
        public final Map<UUID, long[]> colonyMateCache = new ConcurrentHashMap<>();

        /**
         * Dimension the besieged colony lives in, cached at launch so the Explosion't
         * war-aware mixin can match the ticking level in O(1) instead of scanning all
         * colonies every level tick (audit H12). May be null (e.g. a restored raid).
         */
        public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;

        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
            this.colonyId = colonyId;
            this.besiegingPlayerUUID = besiegingPlayerUUID;
            this.colonyCenter = colonyCenter;
            this.isReclaim = isReclaim;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
        }
    }

    /** Persisted occupation record — survives server restart. */
    public static class BesiegeOccupationData {
        public int colonyId;
        public String colonyName;
        public UUID besiegingPlayerUUID;
        public UUID formerOwnerUUID;
        public long besiegeTime;
        public int tributePercent;

        // For Gson deserialization
        public BesiegeOccupationData() {}

        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
            this.colonyId = colonyId;
            this.colonyName = colonyName;
            this.besiegingPlayerUUID = besiegingPlayerUUID;
            this.formerOwnerUUID = formerOwnerUUID;
            this.besiegeTime = besiegeTime;
            this.tributePercent = tributePercent;
        }
    }
}
