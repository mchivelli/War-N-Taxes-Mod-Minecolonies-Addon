package net.machiavelli.minecolonytax.occupation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Colony Occupation system.
 *
 * Constraints:
 * - The occupier may collect taxes but cannot interact with occupied colony
 *   buildings or items.
 * - The original owner has a configurable window (OccupationDurationDays) to
 *   wage a reclamation war.  If they do not, full ownership transfers permanently
 *   to the occupier when the occupation expires.
 */
public class OccupationManager {

    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/occupations.json";

    private static final Map<Integer, OccupationData> ACTIVE_OCCUPATIONS = new ConcurrentHashMap<>();

    private static MinecraftServer serverInstance;

    /**
     * How an occupation resolves when its timer expires.
     *
     * Primary colonies always run in TAX_ONLY (deed never moves), unless
     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
     */
    public enum OccupationMode {
        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
        TRANSFER_PENDING,
        /** Expiry auto-reclaims — taxes route back to original owner, deed never moves. Primary colonies. */
        TAX_ONLY
    }

    public static class OccupationData {
        public final int colonyId;
        public final String occupierUUID;
        public final String originalOwnerUUID;
        public final int occupierColonyId;
        public final long startTime;
        public final long expirationTime;
        public final String colonyName;
        public boolean reclamationAttempted;
        public long lastTaxCollectionTime;
        /** Null on save files written before the Siege SMP upgrade — see {@link #getMode()}. */
        public OccupationMode mode;

        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
                              int occupierColonyId, String colonyName,
                              long startTime, long expirationTime) {
            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
        }

        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
                              int occupierColonyId, String colonyName,
                              long startTime, long expirationTime, OccupationMode mode) {
            this.colonyId = colonyId;
            this.occupierUUID = occupierUUID.toString();
            this.originalOwnerUUID = originalOwnerUUID.toString();
            this.occupierColonyId = occupierColonyId;
            this.colonyName = colonyName;
            this.startTime = startTime;
            this.expirationTime = expirationTime;
            this.reclamationAttempted = false;
            this.lastTaxCollectionTime = 0;
            this.mode = mode;
        }

        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
        public OccupationMode getMode() {
            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public long getRemainingTimeMs() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }

        public int getRemainingDays() {
            long remainingMs = getRemainingTimeMs();
            return (int) (remainingMs / (24L * 60L * 60L * 1000L));
        }

        public int getRemainingHours() {
            long remainingMs = getRemainingTimeMs();
            return (int) (remainingMs / (60L * 60L * 1000L));
        }

        public UUID getOccupierUUID() {
            return UUID.fromString(occupierUUID);
        }

        public UUID getOriginalOwnerUUID() {
            return UUID.fromString(originalOwnerUUID);
        }
    }

    private static class OccupationSaveData {
        public List<OccupationData> occupations = new ArrayList<>();
    }

    public static void initialize(MinecraftServer server) {
        serverInstance = server;
        loadData();
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("OccupationManager initialized with {} active occupations", ACTIVE_OCCUPATIONS.size());
        }
    }

    public static void shutdown() {
        saveData();
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("OccupationManager shutdown complete");
        }
    }

    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
        if (colony == null || occupierUUID == null) {
            LOGGER.warn("startOccupation called with null colony or occupier");
            return;
        }

        int colonyId = colony.getID();

        // Don't double-occupy
        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
            return;
        }

        UUID originalOwner = colony.getPermissions().getOwner();
        // AUDIT FIX (defensive_04 / adversary_C / Top-12): abandoned colonies can have a null
        // owner. The OccupationData constructor does originalOwnerUUID.toString() and would
        // NPE, leaving the war stuck in ACTIVE_WARS forever draining treasuries. We refuse to
        // start occupation against a null-owner colony — the war-victory path falls through
        // and the colony stays abandoned for the normal claiming-raid flow to handle.
        if (originalOwner == null) {
            LOGGER.warn("startOccupation refused: colony {} (id {}) has a null owner (abandoned?). " +
                    "Occupation cannot be started; original-owner reclamation would be unreachable.",
                    colony.getName(), colony.getID());
            return;
        }
        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;

        // Decide mode by colony tier. Primary colonies are tax-only by default;
        // secondaries follow the legacy transfer-on-expiry flow.
        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
        // behavior for primaries too.
        //
        // Use the same canonical tier check as ColonyTierGuard: FCT reverse
        // lookup first, then permissions-owner fallback. Otherwise stale/null/
        // placeholder owners (abandoned colonies, system-owned, etc.) can leak
        // a primary into the TRANSFER_PENDING flow and accidentally transfer
        // the deed at expiry.
        boolean isPrimary;
        UUID trackedFirstOwner = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColonyOwner(colonyId);
        if (trackedFirstOwner != null) {
            isPrimary = true;
        } else {
            isPrimary = originalOwner != null
                    && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
        }
        OccupationMode mode;
        int durationDays;
        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
            mode = OccupationMode.TAX_ONLY;
            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
        } else {
            mode = OccupationMode.TRANSFER_PENDING;
            durationDays = TaxConfig.getOccupationDurationDays();
        }

        long now = System.currentTimeMillis();
        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);

        OccupationData data = new OccupationData(
                colonyId, occupierUUID, originalOwner,
                occupierColonyId, colony.getName(),
                now, expirationTime, mode
        );
        ACTIVE_OCCUPATIONS.put(colonyId, data);
        saveData();

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
                    colony.getName(), occupierUUID, durationDays, mode);
        }

        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
        final String expiryConsequence = isTaxOnly
                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
                : "If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!";
        final String ownerStakes = isTaxOnly
                ? "This is your Primary colony — the deed is safe. Reclaim within " + durationDays
                        + " days or the occupation simply ends."
                : "If you do not reclaim, ownership will permanently transfer!";

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Notify occupier
            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
            if (occupier != null) {
                // AUDIT FIX (defensive_04 C1): the previous "/wnt collectoccupation <id>" hint
                // pointed at a command that has never been registered. The automatic occupation
                // tax flow runs via TaxManager.processAutomaticOccupationTax and already routes
                // revenue to the occupier colony, so the manual path is unnecessary. Just tell
                // the player that revenue arrives automatically.
                Component occupierMsg = Component.literal(isTaxOnly ? "PRIMARY COLONY TAX-OCCUPIED" : "COLONY OCCUPIED")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("You now occupy " + colony.getName() + "!")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                        .append(Component.literal("Occupation tax is collected automatically and routed to your colony.")
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You cannot interact with colony buildings or items.")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n"))
                        .append(Component.literal(expiryConsequence)
                                .withStyle(ChatFormatting.AQUA));
                occupier.sendSystemMessage(occupierMsg);
            }

            // Notify original owner
            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
            if (owner != null) {
                Component ownerMsg = Component.literal(isTaxOnly
                                ? "YOUR PRIMARY COLONY HAS BEEN TAX-OCCUPIED"
                                : "YOUR COLONY HAS BEEN OCCUPIED")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("Your colony " + colony.getName() + " has been occupied!")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n"))
                        .append(Component.literal("The occupier will collect taxes from your colony.")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You have " + durationDays + " days to wage a reclamation war with /wnt wagewar " + colonyId)
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\n"))
                        .append(Component.literal(ownerStakes)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                owner.sendSystemMessage(ownerMsg);
            }
        }
    }

    public static boolean isOccupied(int colonyId) {
        return ACTIVE_OCCUPATIONS.containsKey(colonyId);
    }

    public static OccupationData getOccupation(int colonyId) {
        return ACTIVE_OCCUPATIONS.get(colonyId);
    }

    public static Map<Integer, OccupationData> getActiveOccupations() {
        return Collections.unmodifiableMap(ACTIVE_OCCUPATIONS);
    }

    public static boolean isOccupier(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        return data != null && data.occupierUUID.equals(playerUUID.toString());
    }

    public static List<OccupationData> getOccupiedByPlayer(UUID playerUUID) {
        List<OccupationData> result = new ArrayList<>();
        String uuid = playerUUID.toString();
        for (OccupationData data : ACTIVE_OCCUPATIONS.values()) {
            if (data.occupierUUID.equals(uuid)) {
                result.add(data);
            }
        }
        return result;
    }

    public static int collectOccupationTax(int colonyId, ServerPlayer occupier) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) {
            occupier.sendSystemMessage(Component.literal("This colony is not occupied by you.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!data.occupierUUID.equals(occupier.getUUID().toString())) {
            occupier.sendSystemMessage(Component.literal("You are not the occupier of this colony.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        IColony colony = findColonyById(colonyId);
        if (colony == null) {
            occupier.sendSystemMessage(Component.literal("Colony not found.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int storedTax = TaxManager.getStoredTaxForColony(colony);
        if (storedTax <= 0) {
            occupier.sendSystemMessage(Component.literal("No tax available to collect from " + colony.getName() + ".")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
        int taxToCollect = (int) (storedTax * occupationTaxRate);
        if (taxToCollect <= 0) {
            occupier.sendSystemMessage(Component.literal("Tax amount too small to collect.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        // MONEY CONSERVATION: resolve the RECIPIENT before debiting the occupied colony.
        // This used to deduct first and credit only "if (occupierColonyId > 0 && colony != null)",
        // so an occupier whose own colony had been deleted, abandoned or was never recorded
        // silently destroyed the tax — taken from the occupied colony, credited to nobody, while
        // the player was still told "Collected X occupation tax". Same guard VassalManager
        // already applies to tribute.
        IColony occupierColony = data.occupierColonyId > 0 ? findColonyById(data.occupierColonyId) : null;
        if (occupierColony == null) {
            occupier.sendSystemMessage(Component.literal(
                    "You have no colony to receive the occupation tax, so nothing was collected.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        TaxManager.adjustTax(colony, -taxToCollect);
        TaxManager.incrementTaxRevenue(occupierColony, taxToCollect);

        data.lastTaxCollectionTime = System.currentTimeMillis();
        saveData();

        occupier.sendSystemMessage(Component.literal("Collected " + taxToCollect + " occupation tax from " + colony.getName())
                .withStyle(ChatFormatting.GOLD));

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Occupier {} collected {} occupation tax from colony {}",
                    occupier.getName().getString(), taxToCollect, colony.getName());
        }

        return taxToCollect;
    }

    public static int processAutomaticOccupationTax(int colonyId, int generatedTax) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null || generatedTax <= 0) {
            return 0;
        }

        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
        int diverted = (int) (generatedTax * occupationTaxRate);
        if (diverted <= 0) return 0;

        // MONEY CONSERVATION (release fix C#2): the occupier must be credited EXACTLY the
        // amount the occupied colony is debited, and the occupied colony must never go
        // negative. Clamp the diversion to the occupied colony's available positive ledger
        // balance HERE, before crediting the occupier, then return the clamped value so the
        // caller debits precisely what was credited. Previously the full (unclamped) amount
        // was credited to the occupier while TaxManager later debited only the clamped
        // amount, creating money whenever available < diverted.
        int available = Math.max(0, TaxManager.getStoredTaxForColonyId(colonyId));
        diverted = Math.min(diverted, available);
        if (diverted <= 0) return 0;

        // The caller debits the occupied colony by exactly our return value, so returning a
        // non-zero amount without crediting anyone DESTROYS it. The clamp above conserved money
        // against over-crediting but left this hole open: a missing occupier colony skipped the
        // credit and still returned `diverted`. Resolve the recipient first and return 0 (no
        // debit at all) when there is none.
        IColony occupierColony = data.occupierColonyId > 0 ? findColonyById(data.occupierColonyId) : null;
        if (occupierColony == null) {
            return 0;
        }

        TaxManager.incrementTaxRevenue(occupierColony, diverted);
        if (TaxConfig.isDebugLogging()) {
            LOGGER.info("Auto-diverted {} occupation tax from colony {} to occupier colony {}",
                    diverted, colonyId, data.occupierColonyId);
        }

        return diverted;
    }

    /** Called when the original owner declares war against the occupier. */
    public static void markReclamationAttempted(int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data != null) {
            data.reclamationAttempted = true;
            saveData();
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Reclamation attempt recorded for occupied colony {}", colonyId);
            }
        }
    }

    public static void endOccupation(int colonyId, String reason) {
        OccupationData data = ACTIVE_OCCUPATIONS.remove(colonyId);
        if (data == null) return;

        saveData();
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Occupation ended for colony {} ({}): {}", data.colonyName, colonyId, reason);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Notify occupier
            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
            if (occupier != null) {
                occupier.sendSystemMessage(Component.literal("Your occupation of " + data.colonyName + " has ended: " + reason)
                        .withStyle(ChatFormatting.RED));
            }

            // Notify original owner
            ServerPlayer owner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("The occupation of " + data.colonyName + " has ended: " + reason)
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** Periodic check — transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
    public static void checkExpiredOccupations() {
        if (ACTIVE_OCCUPATIONS.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<Integer> toTransfer = new ArrayList<>();

        for (Map.Entry<Integer, OccupationData> entry : ACTIVE_OCCUPATIONS.entrySet()) {
            OccupationData data = entry.getValue();
            if (data.isExpired()) {
                // Transfer in both cases:
                // - reclamationAttempted=false: deadline passed with no attempt
                // - reclamationAttempted=true:  owner tried to reclaim but failed (lost the war),
                //   occupation expired while still in effect → occupier wins
                toTransfer.add(entry.getKey());
            }
        }

        for (int colonyId : toTransfer) {
            OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
            if (data == null) continue;

            IColony colony = findColonyById(colonyId);
            if (colony == null) {
                LOGGER.warn("Occupied colony {} no longer exists, removing occupation", colonyId);
                ACTIVE_OCCUPATIONS.remove(colonyId);
                continue;
            }

            UUID occupierUUID = data.getOccupierUUID();
            OccupationMode mode = data.getMode();

            if (mode == OccupationMode.TAX_ONLY) {
                // Primary colony auto-reclaim — deed never moves, taxes simply revert
                // to the original owner. Friendly notification on both sides.
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
                }

                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
                if (originalOwner != null) {
                    originalOwner.sendSystemMessage(
                            Component.literal("Your Primary colony " + colony.getName()
                                            + " has been auto-reclaimed — the besieger's hold has lapsed and your taxes now route to you again.")
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
                if (occupier != null) {
                    occupier.sendSystemMessage(
                            Component.literal("Your tax-occupation of " + colony.getName()
                                            + " has lapsed — the owner reclaims it automatically (Primary colonies cannot be permanently claimed).")
                                    .withStyle(ChatFormatting.GOLD));
                }
            } else {
                // Standard TRANSFER_PENDING flow for secondaries. Only broadcast the
                // permanent-claim message if the transfer actually succeeded —
                // ColonyTierGuard or other failures must not produce a misleading message.
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Occupation expired for colony {} - attempting full ownership transfer to {}",
                            colony.getName(), occupierUUID);
                }

                boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);
                if (transferred) {
                    Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(broadcastMsg);
                    }
                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
                    if (originalOwner != null) {
                        originalOwner.sendSystemMessage(
                                Component.literal("You failed to reclaim " + colony.getName()
                                                + " within the deadline. Ownership has been permanently transferred!")
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    }
                } else {
                    // Primary-colony protection (or colony transfer disabled): the deed cannot move.
                    // Keep the occupier in control via vassalization rather than a permanent seizure.
                    // forceVassalize() is idempotent (no-op if transferOwnership already vassalized on
                    // the protection path), so a double call here is safe.
                    LOGGER.info("Occupation of colony {} could not transfer the deed (primary protected) — vassalizing the occupier in instead.",
                            colony.getName());
                    try {
                        int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                        int durationHours = TaxConfig.getWarVassalizationDurationHours();
                        net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                                colony, occupierUUID, tributePercent, durationHours);
                    } catch (Throwable t) {
                        LOGGER.warn("Vassalize fallback on occupation of {} failed: {}", colony.getName(), t.toString());
                    }
                    Component broadcastMsg = Component.literal(
                            colony.getName() + " remains a protected Primary colony — it has been vassalized to its occupier rather than seized.")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(broadcastMsg);
                    }
                }
            }

            ACTIVE_OCCUPATIONS.remove(colonyId);
        }

        if (!toTransfer.isEmpty()) {
            saveData();
        }
    }

    /**
     * Manually end a tax-only occupation early — called when the owner successfully
     * mounts a counter-besiege. Restores everything to pre-occupation state.
     *
     * Strict guards:
     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
     *    occupations (secondary colonies) must follow the legacy reclaim flow
     *    or be ended by the standard expiry/cancel paths.
     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
     *    arbitrary players from cancelling another player's occupation.
     *  - Uses atomic remove so concurrent calls don't double-fire.
     *
     * @return true if an occupation was ended, false otherwise
     */
    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;
        if (data.getMode() != OccupationMode.TAX_ONLY) {
            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
                    colonyId, data.getMode());
            return false;
        }
        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
                    reclaimerUUID, data.originalOwnerUUID, colonyId);
            return false;
        }

        // Atomic remove — bail if a concurrent caller already cleared it.
        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
        saveData();

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
            if (originalOwner != null) {
                originalOwner.sendSystemMessage(
                        Component.literal("You have successfully reclaimed " + data.colonyName + "!")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            }
            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
            if (occupier != null) {
                occupier.sendSystemMessage(
                        Component.literal(data.colonyName + " has been reclaimed by its original owner — your hold has been broken.")
                                .withStyle(ChatFormatting.RED));
            }
        }
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Occupation reclaimed for colony {} ({}) by original owner", data.colonyName, colonyId);
        }
        return true;
    }

    /** Returns true if the occupier should be blocked from interacting with the occupied colony's buildings and items. */
    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;

        // Block the occupier from interacting with the occupied colony's items
        return data.occupierUUID.equals(playerUUID.toString());
    }

    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;
        return data.originalOwnerUUID.equals(playerUUID.toString());
    }

    public static void saveData() {
        // Snapshot on the calling (server) thread; the worker only touches
        // a fresh ArrayList so it can't ConcurrentModification on the live map.
        final OccupationSaveData saveData = new OccupationSaveData();
        saveData.occupations = new ArrayList<>(ACTIVE_OCCUPATIONS.values());

        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("occupations", () -> {
            try {
                Path dir = Paths.get("config/warntax");
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                // AUDIT FIX (Codex MED-11): atomic write — temp file + atomic-move. The old code
                // truncated occupations.json in place via FileWriter; a crash mid-write left a
                // truncated JSON which the loader treats as empty, silently dropping every
                // active occupation (and pending TRANSFER_PENDING deeds).
                Path target = Paths.get(STORAGE_FILE);
                Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
                try (Writer writer = new FileWriter(tmp.toFile())) {
                    GSON.toJson(saveData, writer);
                }
                try {
                    Files.move(tmp, target,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException atomicEx) {
                    // Windows can refuse ATOMIC_MOVE across some filesystems; fall back to
                    // REPLACE_EXISTING. Still safer than the original in-place truncate.
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save occupation data: {}", e.getMessage());
            }
        });
    }

    public static void loadData() {
        try {
            Path path = Paths.get(STORAGE_FILE);
            if (!Files.exists(path)) return;

            try (Reader reader = new FileReader(STORAGE_FILE)) {
                OccupationSaveData saveData = GSON.fromJson(reader, OccupationSaveData.class);
                if (saveData != null && saveData.occupations != null) {
                    ACTIVE_OCCUPATIONS.clear();
                    for (OccupationData data : saveData.occupations) {
                        ACTIVE_OCCUPATIONS.put(data.colonyId, data);
                    }
                    if (TaxConfig.isNormalLogging()) {
                        LOGGER.info("Loaded {} occupations from disk", ACTIVE_OCCUPATIONS.size());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load occupation data: {}", e.getMessage());
        }
    }

    private static IColony findColonyById(int colonyId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;

        for (Level level : server.getAllLevels()) {
            for (IColony c : IColonyManager.getInstance().getColonies(level)) {
                if (c.getID() == colonyId) return c;
            }
        }
        return null;
    }
}
