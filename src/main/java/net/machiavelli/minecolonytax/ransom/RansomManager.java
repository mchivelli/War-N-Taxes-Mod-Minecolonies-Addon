package net.machiavelli.minecolonytax.ransom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.FirstColonyTracker;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.util.AsyncSaveExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ransom System v2 — when the attacker in an active raid or besiege kills the defending
 * colony's owner or an officer, the victim automatically receives a ransom offer with
 * clickable ACCEPT/DENY buttons; the attacker holds a veto ([WITHDRAW]) until it is
 * answered. Accepting pays the attacker from the colony tax balance, ends the conflict
 * with no spoils, and grants the colony conflict immunity.
 *
 * <p>Threading: every entry point (kill hook, commands, {@link #tick()}) runs on the
 * main server thread — offers need no locking; the CAS in {@link RansomOffer} only
 * guards against double-fired chat clicks. Disk writes go through
 * {@link AsyncSaveExecutor} with the snapshot taken on the server thread.
 *
 * <p>Persistence: immunities and cooldowns survive restarts
 * ({@code config/warntax/ransom_data.json}); pending offers are deliberately transient
 * — the conflicts they belong to do not survive a restart either.
 */
public final class RansomManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/ransom_data.json";

    /** Single source of truth for the clickable chat commands (v1 hardcoded these per send site). */
    public static final String CMD_ACCEPT = "/wnt ransom accept";
    public static final String CMD_DENY = "/wnt ransom deny";
    public static final String CMD_CANCEL = "/wnt ransom cancel";
    public static final String CMD_STATUS = "/wnt ransom status";

    /** Non-victory reason string — {@code RaidManager.endActiveRaid} transfers no loot for it. */
    public static final String RAID_END_REASON = "Ransom paid";

    /** TRANSIENT — keyed by colonyId, enforcing at most one offer per colony. */
    private static final Map<Integer, RansomOffer> ACTIVE_OFFERS = new ConcurrentHashMap<>();
    /** PERSISTED — colonyId → immunity expiry (epoch ms). */
    private static final Map<Integer, Long> RANSOM_IMMUNITY = new ConcurrentHashMap<>();
    /** PERSISTED — "attackerUUID:victimUUID" → cooldown expiry (epoch ms). */
    private static final Map<String, Long> RANSOM_COOLDOWNS = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    private RansomManager() {
    }

    // ==================== Lifecycle ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData(server);
        LOGGER.info("RansomManager initialized — {} immunit{} and {} cooldown(s) restored",
                RANSOM_IMMUNITY.size(), RANSOM_IMMUNITY.size() == 1 ? "y" : "ies", RANSOM_COOLDOWNS.size());
    }

    public static void shutdown() {
        // Pending offers die with the server (their conflicts do too); persisted state is flushed.
        if (!ACTIVE_OFFERS.isEmpty()) {
            LOGGER.info("Server stopping with {} unanswered ransom offer(s) — they will not survive the restart",
                    ACTIVE_OFFERS.size());
        }
        ACTIVE_OFFERS.clear();
        saveData();
        SERVER = null;
    }

    /**
     * Driven ~1×/second from a {@code TickScheduler.scheduleRepeating} registration
     * (main server thread). Expires offers past their deadline, voids offers whose
     * conflict has ended by other means, and prunes lapsed immunities/cooldowns.
     */
    public static void tick() {
        if (SERVER == null) return;
        long now = System.currentTimeMillis();

        for (RansomOffer offer : new ArrayList<>(ACTIVE_OFFERS.values())) {
            if (!offer.isPending()) { // defensive — resolved offers are removed eagerly
                ACTIVE_OFFERS.remove(offer.colonyId, offer);
                continue;
            }
            if (!isConflictAlive(offer)) {
                if (offer.resolve(RansomOffer.State.EXPIRED)) {
                    ACTIVE_OFFERS.remove(offer.colonyId, offer);
                    startCooldown(offer);
                    sendTo(offer.victimUUID, Component.literal(
                            "The ransom offer is void — the conflict has ended.").withStyle(ChatFormatting.GRAY));
                    sendTo(offer.attackerUUID, Component.literal(
                            "Your ransom demand is void — the conflict has ended.").withStyle(ChatFormatting.GRAY));
                    saveData();
                }
                continue;
            }
            if (offer.isExpired(now)) {
                if (offer.resolve(RansomOffer.State.EXPIRED)) {
                    ACTIVE_OFFERS.remove(offer.colonyId, offer);
                    startCooldown(offer);
                    sendTo(offer.victimUUID, Component.literal(
                            "⏰ The ransom offer expired. The fight continues!").withStyle(ChatFormatting.YELLOW));
                    sendTo(offer.attackerUUID, Component.literal(
                            "⏰ Your ransom demand expired unanswered. Fight on!").withStyle(ChatFormatting.YELLOW));
                    saveData();
                }
            }
        }

        // Lazy pruning keeps the persisted maps bounded; no disk write here — expired
        // entries are harmless in the file (checked by timestamp) and pruned on load.
        RANSOM_IMMUNITY.values().removeIf(expiry -> expiry <= now);
        RANSOM_COOLDOWNS.values().removeIf(expiry -> expiry <= now);
    }

    // ==================== Offer creation (from the kill hook) ====================

    /**
     * Single trigger funnel (v1 had two divergent ones). Caller has already verified:
     * player-killer, victim is owner/officer of the defending colony, killer is the
     * raider / primary besieger of that colony.
     */
    public static void onDefenderKilled(ServerPlayer victim, ServerPlayer killer, int colonyId, ConflictType type) {
        if (SERVER == null || !TaxConfig.isRansomSystemEnabled()) return;
        if (type == ConflictType.BESIEGE && !TaxConfig.isRansomBesiegeEnabled()) return;

        if (ACTIVE_OFFERS.containsKey(colonyId)) {
            hint(killer, "A ransom offer for this colony is already pending.");
            return;
        }
        if (hasRansomImmunity(colonyId)) return; // conflict predates the immunity — no new offers inside it
        if (isOnCooldown(killer.getUUID(), victim.getUUID())) {
            hint(killer, "You demanded a ransom from this player too recently.");
            return;
        }
        if (type == ConflictType.BESIEGE && BesiegeManager.getDefenderLivesRemaining(colonyId) == 0) {
            return; // pool exhausted — victory resolution is imminent, don't race it
        }
        IColony colony = getColonyById(colonyId);
        if (colony == null) {
            LOGGER.debug("Ransom skipped — colony {} not found", colonyId);
            return;
        }

        double percent = type == ConflictType.RAID
                ? TaxConfig.getRansomDefaultPercent()
                : TaxConfig.getRansomBesiegePercent();
        int amount = RansomCalculator.computeRansomAmount(
                TaxManager.getStoredTaxForColony(colony), percent,
                TaxConfig.getRansomMinAmount(), TaxConfig.getRansomMaxAmount());
        if (amount <= 0) {
            hint(killer, colony.getName() + " is too poor for a ransom demand.");
            return;
        }

        long now = System.currentTimeMillis();
        long timeoutMs = TaxConfig.getRansomTimeoutSeconds() * 1000L;
        RansomOffer offer = new RansomOffer(killer.getUUID(), victim.getUUID(), colonyId, amount,
                now, now + timeoutMs, type);
        ACTIVE_OFFERS.put(colonyId, offer);

        sendOfferToVictim(victim, offer, colony);
        sendNoticeToAttacker(killer, offer, colony);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Ransom offer created: {} demands {} from {} (colony {}, {})",
                    killer.getName().getString(), amount, victim.getName().getString(), colonyId, type);
    }

    // ==================== Player actions (from WntCommands) ====================

    /**
     * Victim accepts: credit the attacker FIRST, then debit exactly what landed, end the
     * conflict, grant immunity. All on the server thread — a total payout failure leaves
     * the offer PENDING with nothing moved (credit-first means there is nothing to refund).
     */
    public static boolean acceptOffer(ServerPlayer caller) {
        RansomOffer offer = findOfferByVictim(caller.getUUID());
        if (offer == null || !offer.isPending()) {
            fail(caller, "You have no pending ransom offer.");
            return false;
        }
        if (!isConflictAlive(offer)) {
            return false; // next tick voids it with proper messages
        }
        IColony colony = getColonyById(offer.colonyId);
        if (colony == null) {
            fail(caller, "Your colony could not be resolved.");
            return false;
        }
        int pay = Math.min(offer.amount, TaxManager.getStoredTaxForColony(colony));
        if (pay <= 0) {
            fail(caller, "Your colony can no longer afford the ransom.");
            return false;
        }

        // --- Credit first, measure what actually landed (v1 could vanish money) ---
        int landed = creditAttacker(offer, pay);
        if (landed <= 0) {
            fail(caller, "The payment could not be delivered to the attacker — the offer stays open.");
            return false;
        }

        // --- Debit exactly the landed amount, persisted immediately ---
        TaxManager.adjustTaxAndSave(colony, -landed);

        boolean resolved = offer.resolve(RansomOffer.State.ACCEPTED);
        if (!resolved) {
            // Cannot happen on the single server thread (we checked isPending above);
            // kept as a loud invariant rather than a silent money bug.
            LOGGER.error("Ransom offer for colony {} resolved mid-accept — refunding {}", offer.colonyId, landed);
            TaxManager.adjustTaxAndSave(colony, landed);
            return false;
        }
        ACTIVE_OFFERS.remove(offer.colonyId, offer);

        endConflict(offer, landed);

        long now = System.currentTimeMillis();
        RANSOM_IMMUNITY.put(offer.colonyId, now + TaxConfig.getRansomImmunityAfterPaymentHours() * 3_600_000L);
        startCooldown(offer);
        saveData();

        caller.sendSystemMessage(Component.literal("✓ Ransom paid: " + landed + ". The conflict is over.")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        caller.sendSystemMessage(Component.literal("Your colony is protected from new conflicts for "
                + TaxConfig.getRansomImmunityAfterPaymentHours() + " hours.").withStyle(ChatFormatting.GREEN));
        if (offer.conflictType == ConflictType.RAID) {
            // Besiege messaging happens inside endBesiegeByRansom (covers co-besiegers).
            sendTo(offer.attackerUUID, Component.literal(
                    "✓ Ransom accepted! You received " + landed + ". The raid is over.")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Ransom accepted: colony {} paid {} to {} ({})",
                    offer.colonyId, landed, offer.attackerUUID, offer.conflictType);
        return true;
    }

    /** Victim rejects — conflict continues, cooldown starts (anti-spam). */
    public static boolean denyOffer(ServerPlayer caller) {
        RansomOffer offer = findOfferByVictim(caller.getUUID());
        if (offer == null || !offer.resolve(RansomOffer.State.DENIED)) {
            fail(caller, "You have no pending ransom offer.");
            return false;
        }
        ACTIVE_OFFERS.remove(offer.colonyId, offer);
        startCooldown(offer);
        saveData();
        caller.sendSystemMessage(Component.literal("✗ Ransom rejected. The fight continues!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        sendTo(offer.attackerUUID, Component.literal("✗ Your ransom demand was rejected. Fight on!")
                .withStyle(ChatFormatting.RED));
        return true;
    }

    /** Attacker veto — withdraw the automatic offer to keep fighting for full spoils. */
    public static boolean cancelOffer(ServerPlayer caller) {
        RansomOffer offer = findOfferByAttacker(caller.getUUID());
        if (offer == null || !offer.resolve(RansomOffer.State.CANCELLED)) {
            fail(caller, "You have no outgoing ransom demand to withdraw.");
            return false;
        }
        ACTIVE_OFFERS.remove(offer.colonyId, offer);
        startCooldown(offer); // cancelling also starts the cooldown — no offer-spam probing
        saveData();
        caller.sendSystemMessage(Component.literal("You withdrew your ransom demand — fight for the full spoils!")
                .withStyle(ChatFormatting.YELLOW));
        sendTo(offer.victimUUID, Component.literal("The attacker withdrew the ransom offer. Defend yourselves!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        return true;
    }

    public static void showStatus(ServerPlayer caller) {
        RansomOffer incoming = findOfferByVictim(caller.getUUID());
        if (incoming != null && incoming.isPending()) {
            long secondsLeft = Math.max(0, (incoming.expiresAtMs - System.currentTimeMillis()) / 1000);
            caller.sendSystemMessage(Component.literal(
                    "Pending ransom demand: " + incoming.amount + " — " + secondsLeft + "s left. Use "
                            + CMD_ACCEPT + " or " + CMD_DENY + ".").withStyle(ChatFormatting.GOLD));
            return;
        }
        RansomOffer outgoing = findOfferByAttacker(caller.getUUID());
        if (outgoing != null && outgoing.isPending()) {
            long secondsLeft = Math.max(0, (outgoing.expiresAtMs - System.currentTimeMillis()) / 1000);
            caller.sendSystemMessage(Component.literal(
                    "Your ransom demand of " + outgoing.amount + " is pending — " + secondsLeft
                            + "s left. Use " + CMD_CANCEL + " to withdraw it.").withStyle(ChatFormatting.GOLD));
            return;
        }
        IColony own = getPrimaryColonyOfPlayer(caller.getUUID());
        if (own != null && hasRansomImmunity(own.getID())) {
            long hours = Math.max(1, getImmunityRemainingMs(own.getID()) / 3_600_000L);
            caller.sendSystemMessage(Component.literal(
                    "✓ " + own.getName() + " has ransom immunity (~" + hours + "h remaining).")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        caller.sendSystemMessage(Component.literal("No pending ransom offers or active immunity.")
                .withStyle(ChatFormatting.GRAY));
    }

    // ==================== Immunity gate (checked by all conflict-start paths) ====================

    /** Lazy-expiring, no disk I/O on this hot path (expired file entries are pruned on load/tick). */
    public static boolean hasRansomImmunity(int colonyId) {
        Long expiry = RANSOM_IMMUNITY.get(colonyId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            RANSOM_IMMUNITY.remove(colonyId);
            return false;
        }
        return true;
    }

    public static long getImmunityRemainingMs(int colonyId) {
        Long expiry = RANSOM_IMMUNITY.get(colonyId);
        if (expiry == null) return 0;
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    // ==================== Queries ====================

    public static RansomOffer getOfferForColony(int colonyId) {
        return ACTIVE_OFFERS.get(colonyId);
    }

    public static RansomOffer findOfferByVictim(UUID victimUUID) {
        for (RansomOffer offer : ACTIVE_OFFERS.values()) {
            if (offer.victimUUID.equals(victimUUID)) return offer;
        }
        return null;
    }

    public static RansomOffer findOfferByAttacker(UUID attackerUUID) {
        for (RansomOffer offer : ACTIVE_OFFERS.values()) {
            if (offer.attackerUUID.equals(attackerUUID)) return offer;
        }
        return null;
    }

    // ==================== Internals ====================

    /** The conflict the offer belongs to must still be running with the same attacker. */
    private static boolean isConflictAlive(RansomOffer offer) {
        if (offer.conflictType == ConflictType.RAID) {
            ActiveRaidData raid = RaidManager.getActiveRaidForColony(offer.colonyId);
            return raid != null && raid.isActive() && offer.attackerUUID.equals(raid.getRaider());
        }
        return BesiegeManager.getBesiegedColonyIdFor(offer.attackerUUID) == offer.colonyId;
    }

    /**
     * Payout chain, credit-first: SDMShop wallet → attacker's primary colony tax →
     * (raids) raider colony war chest, cap-safe → 0 = undeliverable.
     */
    private static int creditAttacker(RansomOffer offer, int pay) {
        ServerPlayer attacker = SERVER.getPlayerList().getPlayer(offer.attackerUUID);

        if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable() && attacker != null) {
            if (SDMShopIntegration.addMoney(attacker, pay)) {
                LOGGER.info("Ransom payout: {} → SDMShop balance of {}", pay, offer.attackerUUID);
                return pay;
            }
            LOGGER.warn("SDMShop addMoney failed for {} — falling back to colony payout", offer.attackerUUID);
        }

        IColony attackerColony = getPrimaryColonyOfPlayer(offer.attackerUUID);
        if (attackerColony != null) {
            TaxManager.adjustTaxAndSave(attackerColony, pay);
            LOGGER.info("Ransom payout: {} → tax balance of colony {} (attacker {})",
                    pay, attackerColony.getID(), offer.attackerUUID);
            return pay;
        }

        if (offer.conflictType == ConflictType.RAID) {
            ActiveRaidData raid = RaidManager.getActiveRaidForColony(offer.colonyId);
            IColony raiderColony = raid != null ? raid.getRaiderColony() : null;
            if (raiderColony != null) {
                // Cap-safe: credit, measure what fit under the chest cap, only that much is "landed".
                int before = WarChestManager.getWarChestBalance(raiderColony.getID());
                int after = WarChestManager.addToWarChest(raiderColony.getID(), pay);
                int landed = after - before;
                if (landed > 0) {
                    LOGGER.info("Ransom payout: {} of {} → war chest of colony {} (attacker {}, chest cap)",
                            landed, pay, raiderColony.getID(), offer.attackerUUID);
                    return landed;
                }
            }
        }

        LOGGER.warn("Ransom payout undeliverable: attacker {} has no SDMShop, no colony, no war chest",
                offer.attackerUUID);
        return 0;
    }

    private static void endConflict(RansomOffer offer, int landed) {
        if (offer.conflictType == ConflictType.RAID) {
            ActiveRaidData raid = RaidManager.getActiveRaidForColony(offer.colonyId);
            if (raid != null && raid.isActive()) {
                RaidManager.endActiveRaid(raid, RAID_END_REASON); // non-victory reason ⇒ no loot transfer
            }
        } else {
            BesiegeManager.endBesiegeByRansom(offer.colonyId, offer.attackerUUID, landed);
        }
    }

    private static void startCooldown(RansomOffer offer) {
        RANSOM_COOLDOWNS.put(RansomCalculator.cooldownKey(offer.attackerUUID, offer.victimUUID),
                System.currentTimeMillis() + TaxConfig.getRansomCooldownMinutes() * 60_000L);
    }

    private static boolean isOnCooldown(UUID attacker, UUID victim) {
        Long expiry = RANSOM_COOLDOWNS.get(RansomCalculator.cooldownKey(attacker, victim));
        return expiry != null && expiry > System.currentTimeMillis();
    }

    // ==================== Chat ====================

    private static void sendOfferToVictim(ServerPlayer victim, RansomOffer offer, IColony colony) {
        MutableComponent acceptButton = Component.literal("[ACCEPT — Pay Ransom]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, CMD_ACCEPT))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
                                "Pay " + offer.amount + " from " + colony.getName()
                                        + ", end the conflict, gain "
                                        + TaxConfig.getRansomImmunityAfterPaymentHours() + "h immunity"))));
        MutableComponent denyButton = Component.literal("[DENY — Keep Fighting]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, CMD_DENY))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Reject the demand — the conflict continues"))));

        victim.sendSystemMessage(Component.literal("\n⚠ RANSOM DEMAND ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("\nYour attacker demands ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(offer.amount + "").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" to end the "
                        + (offer.conflictType == ConflictType.RAID ? "raid" : "siege") + " of "
                        + colony.getName() + ".\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("You have " + TaxConfig.getRansomTimeoutSeconds()
                        + " seconds to respond.\n").withStyle(ChatFormatting.GRAY))
                .append(acceptButton)
                .append(Component.literal(" "))
                .append(denyButton)
                .append(Component.literal("\n")));
    }

    private static void sendNoticeToAttacker(ServerPlayer attacker, RansomOffer offer, IColony colony) {
        MutableComponent withdrawButton = Component.literal("[WITHDRAW]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, CMD_CANCEL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
                                "Withdraw the demand and fight for the full spoils instead"))));

        attacker.sendSystemMessage(Component.literal("⚔ Ransom demand of " + offer.amount + " sent to "
                        + colony.getName() + "'s leadership. ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("If they accept, the "
                        + (offer.conflictType == ConflictType.RAID ? "raid" : "siege")
                        + " ends and you are paid. ").withStyle(ChatFormatting.GRAY))
                .append(withdrawButton));
    }

    private static void hint(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static void fail(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private static void sendTo(UUID playerUUID, Component message) {
        if (SERVER == null) return;
        ServerPlayer player = SERVER.getPlayerList().getPlayer(playerUUID);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    // ==================== Colony helpers ====================

    private static IColony getColonyById(int colonyId) {
        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
    }

    /** Same resolution order as BesiegeManager: FirstColonyTracker, then MC-owner fallback. */
    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
        if (firstColonyId != null) {
            IColony first = cm.getAllColonies().stream()
                    .filter(c -> c.getID() == firstColonyId)
                    .findFirst().orElse(null);
            if (first != null) return first;
        }
        for (IColony c : cm.getAllColonies()) {
            UUID owner = c.getPermissions().getOwner();
            if (owner != null && owner.equals(playerId)) return c;
        }
        return null;
    }

    /** Owner or officer of the given colony — the ranks whose death can trigger a ransom. */
    public static boolean isOwnerOrOfficer(IColony colony, UUID playerUUID) {
        var perms = colony.getPermissions();
        UUID owner = perms.getOwner();
        if (owner != null && owner.equals(playerUUID)) return true;
        Rank rank = perms.getRank(playerUUID);
        return rank != null && rank.equals(perms.getRankOfficer());
    }

    // ==================== Persistence ====================

    /** Gson DTO for {@code ransom_data.json}. */
    private static final class RansomSaveData {
        Map<Integer, Long> immunity = new HashMap<>();
        Map<String, Long> cooldowns = new HashMap<>();
    }

    private static void loadData(MinecraftServer server) {
        File f = server.getServerDirectory().resolve(STORAGE_FILE).toFile();
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            Type type = new TypeToken<RansomSaveData>() {}.getType();
            RansomSaveData data = GSON.fromJson(r, type);
            long now = System.currentTimeMillis();
            if (data != null) {
                if (data.immunity != null) {
                    data.immunity.forEach((colonyId, expiry) -> {
                        if (expiry != null && expiry > now) RANSOM_IMMUNITY.put(colonyId, expiry);
                    });
                }
                if (data.cooldowns != null) {
                    data.cooldowns.forEach((key, expiry) -> {
                        if (expiry != null && expiry > now) RANSOM_COOLDOWNS.put(key, expiry);
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load ransom data", e);
        }
    }

    private static void saveData() {
        if (SERVER == null) return;
        // Snapshot on the server thread; the write happens on the async saver.
        final RansomSaveData data = new RansomSaveData();
        data.immunity.putAll(RANSOM_IMMUNITY);
        data.cooldowns.putAll(RANSOM_COOLDOWNS);
        final File f = SERVER.getServerDirectory().resolve(STORAGE_FILE).toFile();

        AsyncSaveExecutor.submit("ransom", () -> {
            try {
                f.getParentFile().mkdirs();
                try (FileWriter w = new FileWriter(f)) {
                    GSON.toJson(data, w);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save ransom data", e);
            }
        });
    }
}
