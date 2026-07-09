package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.events.random.EventLogEntry;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.gui.data.ColonySummary;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.occupation.OccupationManager;
import net.machiavelli.minecolonytax.server.ColonyDataCollector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from client to server to request colony tax data for the GUI
 */
public class RequestColonyDataPacket {
    
    public RequestColonyDataPacket() {
        // Empty constructor for packet registration
    }
    
    public RequestColonyDataPacket(FriendlyByteBuf buf) {
        // No data to read for this packet
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        // No data to write for this packet
    }

    private static boolean isHistoryEntry(String eventId) {
        return eventId.startsWith("raid_") || eventId.startsWith("war_");
    }

    /** Returns true for live state entries (war/besiege/occupation) that are not dismissible. */
    static boolean isActiveStateEntry(String eventId) {
        return eventId.startsWith("active_");
    }

    private static String getPlayerDisplayName(UUID uuid, ServerPlayer requestingPlayer) {
        if (uuid == null || requestingPlayer.getServer() == null) return "unknown";
        ServerPlayer p = requestingPlayer.getServer().getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : "a player";
    }

    private static long extractTs(String eventId) {
        try {
            int u = eventId.indexOf('_');
            if (u >= 0) return Long.parseLong(eventId.substring(u + 1));
        } catch (NumberFormatException ignored) {}
        return 0L;
    }

    private static EventLogEntry raidToLogEntry(HistoryManager.RaidEntry raid) {
        String id = "raid_" + raid.getTimestamp();
        String name = "Raid by " + raid.getRaiderName();
        String desc;
        String stat;
        int color;
        if (raid.isSuccessful()) {
            desc = "Looted " + raid.getAmountStolen() + "$  (" + raid.getFormattedTimestamp() + ")";
            stat = "Looted " + raid.getAmountStolen() + "$";
            color = 0xFFA03030; // DANGER — bad for colony
        } else {
            String reason = raid.getFailureReason() != null ? raid.getFailureReason() : "unknown";
            desc = "Repelled: " + reason + "  (" + raid.getFormattedTimestamp() + ")";
            stat = "Repelled";
            color = 0xFF2A6E2A; // GREEN — colony defended
        }
        return new EventLogEntry(id, name, desc, stat, color, false, 0);
    }

    private static EventLogEntry warToLogEntry(HistoryManager.WarEntry war) {
        String id = "war_" + war.getTimestamp();
        String name;
        String desc;
        String stat;
        int color;
        String ts = "(" + war.getFormattedTimestamp() + ")";
        switch (war.getOutcome()) {
            case "VICTORY":
                name = "War Won vs " + war.getOpponentColonyName();
                desc = "Colony defended" + (war.getAmountTransferred() > 0 ? " +" + war.getAmountTransferred() + "$" : "") + "  " + ts;
                stat = war.getAmountTransferred() > 0 ? "+" + war.getAmountTransferred() + "$" : "Won";
                color = 0xFF2A6E2A; // GREEN
                break;
            case "DEFEAT":
                name = "War Lost to " + war.getOpponentColonyName();
                desc = "Colony defeated" + (war.getAmountTransferred() > 0 ? " -" + war.getAmountTransferred() + "$" : "") + "  " + ts;
                stat = war.getAmountTransferred() > 0 ? "-" + war.getAmountTransferred() + "$" : "Lost";
                color = 0xFFA03030; // DANGER
                break;
            case "STALEMATE":
                name = "Stalemate vs " + war.getOpponentColonyName();
                desc = "No decisive outcome  " + ts;
                stat = "Draw";
                color = 0xFFC8A430; // GOLD
                break;
            default:
                name = "War vs " + war.getOpponentColonyName();
                desc = "Partial outcome  " + ts;
                stat = "";
                color = 0xFF8B7355; // INK_FAINT
                break;
        }
        return new EventLogEntry(id, name, desc, stat, color, false, 0);
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // Collect colony data for this player
                List<ColonyTaxData> colonyData = ColonyDataCollector.collectColonyData(player);
                List<VassalIncomeData> vassalData = ColonyDataCollector.collectVassalIncomeData(player);

                // Build event log for each colony: random events + raid history + war history
                Map<Integer, List<EventLogEntry>> eventLogData = new HashMap<>();
                for (ColonyTaxData data : colonyData) {
                    int colonyId = data.getColonyId();
                    List<EventLogEntry> entries = new ArrayList<>();

                    // Active / recent random events first
                    entries.addAll(RandomEventManager.getEventLog(colonyId));

                    // Raid history
                    HistoryManager.ColonyHistory history = HistoryManager.getColonyHistory(colonyId);
                    for (HistoryManager.RaidEntry raid : history.getStructuredRaids()) {
                        entries.add(raidToLogEntry(raid));
                    }
                    // War history
                    for (HistoryManager.WarEntry war : history.getStructuredWars()) {
                        entries.add(warToLogEntry(war));
                    }

                    // Active state events: war, besiege, occupation (always shown at top, not dismissible)
                    WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
                    if (activeWar == null) {
                        for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
                            if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
                                activeWar = wd; break;
                            }
                        }
                    }
                    if (activeWar != null) {
                        boolean isDefender = WarSystem.ACTIVE_WARS.containsKey(colonyId);
                        String opponent = isDefender
                                ? (activeWar.getAttackerColony() != null ? activeWar.getAttackerColony().getName() : "unknown")
                                : (activeWar.getColony() != null ? activeWar.getColony().getName() : "unknown");
                        String role = isDefender ? "Defending vs " : "Attacking ";
                        entries.add(new EventLogEntry(
                                "active_war_" + colonyId,
                                role + opponent,
                                isDefender ? "Under attack — tax claiming disabled" : "War ongoing — treasury draining",
                                "Active",
                                0xFFA03030, true, -1));
                    }

                    if (TaxConfig.isBesiegeSystemEnabled()
                            && BesiegeManager.isColonyBesieged(colonyId)) {
                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
                        String besiegerName = getPlayerDisplayName(brd != null ? brd.besiegingPlayerUUID : null, player);
                        entries.add(new EventLogEntry(
                                "active_besiege_" + colonyId,
                                "Under Siege by " + besiegerName,
                                "Colony besieged — tax claiming disabled",
                                "Besieged",
                                0xFFA03030, true, -1));
                    }

                    if (TaxConfig.isOccupationSystemEnabled()) {
                        OccupationManager.OccupationData occ = OccupationManager.getOccupation(colonyId);
                        if (occ != null && !occ.isExpired()) {
                            String occupierName = getPlayerDisplayName(occ.getOccupierUUID(), player);
                            entries.add(new EventLogEntry(
                                    "active_occupation_" + colonyId,
                                    "Occupied by " + occupierName,
                                    "Taxes collected by occupier — wage reclamation war to retake it",
                                    "Occupied",
                                    0xFFFF9800, true, -1));
                        }
                    }

                    // Sort: active state events first, then random events (active before inactive), then history newest-first
                    entries.sort((a, b) -> {
                        boolean aState = isActiveStateEntry(a.getEventId());
                        boolean bState = isActiveStateEntry(b.getEventId());
                        if (aState != bState) return aState ? -1 : 1;
                        boolean aHist = isHistoryEntry(a.getEventId());
                        boolean bHist = isHistoryEntry(b.getEventId());
                        if (aHist != bHist) return aHist ? 1 : -1;
                        if (aHist) {
                            return Long.compare(extractTs(b.getEventId()), extractTs(a.getEventId()));
                        }
                        if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
                        return 0;
                    });

                    eventLogData.put(colonyId, entries);
                }

                // Collect spy target colonies for the espionage tab: every colony this player does
                // NOT manage. Rival colonies appear as targets; the player's own/managed colonies
                // are excluded (you don't spy on yourself). Previously this listed ALL colonies,
                // so the player's own colonies also showed up as valid spy targets.
                List<ColonySummary> allColonySummaries = ColonyDataCollector.collectSpyTargetColonies(player);

                // Send response back to client
                NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new ColonyDataResponsePacket(colonyData, vassalData, eventLogData, allColonySummaries));
            }
        });
        return true;
    }
}
