package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record RequestColonyDataPayload(int colonyId) implements CustomPacketPayload {
    
    public static final Type<RequestColonyDataPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "request_colony_data"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestColonyDataPayload> STREAM_CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.INT, RequestColonyDataPayload::colonyId,
            RequestColonyDataPayload::new
        );
    
    @Override
    public Type<RequestColonyDataPayload> type() {
        return TYPE;
    }
    
    public static void handle(RequestColonyDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<net.machiavelli.minecolonytax.gui.data.ColonyTaxData> colonyData =
                net.machiavelli.minecolonytax.server.ColonyDataCollector.collectColonyData(player);
            List<net.machiavelli.minecolonytax.gui.data.VassalIncomeData> vassalData =
                net.machiavelli.minecolonytax.server.ColonyDataCollector.collectVassalIncomeData(player);

            java.util.Map<Integer, List<net.machiavelli.minecolonytax.events.random.EventLogEntry>> eventLogData =
                new java.util.HashMap<>();
            for (net.machiavelli.minecolonytax.gui.data.ColonyTaxData data : colonyData) {
                eventLogData.put(data.getColonyId(), buildEventLog(data.getColonyId(), player));
            }

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ColonyDataResponsePayload(colonyData, vassalData, eventLogData));
        });
    }

    /**
     * Build the ordered per-colony event log for the GUI Events view: dismissible random-event
     * history + raid history + active war/besiege/occupation state rows, sorted (active state
     * first, then random events, then raid history newest-first). Mirrors Forge's assembly minus
     * structured-war history (Neo's HistoryManager has no structured-war records).
     */
    public static List<net.machiavelli.minecolonytax.events.random.EventLogEntry> buildEventLog(
            int colonyId, ServerPlayer player) {
        java.util.List<net.machiavelli.minecolonytax.events.random.EventLogEntry> entries =
            new java.util.ArrayList<>(
                net.machiavelli.minecolonytax.events.random.RandomEventManager.getEventLog(colonyId));

        // Raid history
        net.machiavelli.minecolonytax.data.HistoryManager.ColonyHistory history =
            net.machiavelli.minecolonytax.data.HistoryManager.getColonyHistory(colonyId);
        if (history != null) {
            for (net.machiavelli.minecolonytax.data.HistoryManager.RaidEntry raid : history.getStructuredRaids()) {
                entries.add(raidToLogEntry(raid));
            }
            for (net.machiavelli.minecolonytax.data.HistoryManager.WarEntry war : history.getStructuredWars()) {
                entries.add(warToLogEntry(war));
            }
        }

        // Active war (this colony as defender, or as attacker)
        net.machiavelli.minecolonytax.data.WarData activeWar =
            net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colonyId);
        boolean isDefender = activeWar != null;
        if (activeWar == null) {
            for (net.machiavelli.minecolonytax.data.WarData wd
                    : net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.values()) {
                if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
                    activeWar = wd; break;
                }
            }
        }
        if (activeWar != null) {
            String opponent = isDefender
                    ? (activeWar.getAttackerColony() != null ? activeWar.getAttackerColony().getName() : "unknown")
                    : (activeWar.getColony() != null ? activeWar.getColony().getName() : "unknown");
            entries.add(new net.machiavelli.minecolonytax.events.random.EventLogEntry(
                    "active_war_" + colonyId,
                    (isDefender ? "Defending vs " : "Attacking ") + opponent,
                    isDefender ? "Under attack — tax claiming disabled" : "War ongoing — treasury draining",
                    "Active", 0xFFA03030, true, -1));
        }

        // Active besiege
        if (net.machiavelli.minecolonytax.TaxConfig.isBesiegeSystemEnabled()
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId)) {
            net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData brd =
                net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().get(colonyId);
            String besiegerName = getPlayerDisplayName(brd != null ? brd.besiegingPlayerUUID : null, player);
            entries.add(new net.machiavelli.minecolonytax.events.random.EventLogEntry(
                    "active_besiege_" + colonyId,
                    "Under Siege by " + besiegerName,
                    "Colony besieged — tax claiming disabled",
                    "Besieged", 0xFFA03030, true, -1));
        }

        // Active occupation
        if (net.machiavelli.minecolonytax.TaxConfig.isOccupationSystemEnabled()) {
            net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData occ =
                net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(colonyId);
            if (occ != null && !occ.isExpired()) {
                String occupierName = getPlayerDisplayName(occ.getOccupierUUID(), player);
                entries.add(new net.machiavelli.minecolonytax.events.random.EventLogEntry(
                        "active_occupation_" + colonyId,
                        "Occupied by " + occupierName,
                        "Taxes collected by occupier — wage reclamation war to retake it",
                        "Occupied", 0xFFFF9800, true, -1));
            }
        }

        // Sort: active state first, then random events (active before inactive), then raid history newest-first
        entries.sort((a, b) -> {
            boolean aState = a.getEventId().startsWith("active_");
            boolean bState = b.getEventId().startsWith("active_");
            if (aState != bState) return aState ? -1 : 1;
            boolean aHist = isHistoryId(a.getEventId());
            boolean bHist = isHistoryId(b.getEventId());
            if (aHist != bHist) return aHist ? 1 : -1;
            if (aHist) return Long.compare(extractTs(b.getEventId()), extractTs(a.getEventId()));
            if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
            return 0;
        });

        return entries;
    }

    private static boolean isHistoryId(String eventId) {
        return eventId.startsWith("raid_") || eventId.startsWith("war_");
    }

    private static long extractTs(String eventId) {
        try {
            int u = eventId.indexOf('_');
            if (u >= 0) return Long.parseLong(eventId.substring(u + 1));
        } catch (NumberFormatException ignored) {}
        return 0L;
    }

    private static String getPlayerDisplayName(java.util.UUID uuid, ServerPlayer requestingPlayer) {
        if (uuid == null || requestingPlayer.getServer() == null) return "unknown";
        ServerPlayer p = requestingPlayer.getServer().getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : "a player";
    }

    private static net.machiavelli.minecolonytax.events.random.EventLogEntry warToLogEntry(
            net.machiavelli.minecolonytax.data.HistoryManager.WarEntry war) {
        String id = "war_" + war.getTimestamp();
        String outcome = war.getOutcome();
        int color = "VICTORY".equals(outcome) ? 0xFF2A6E2A
                  : "DEFEAT".equals(outcome) ? 0xFFA03030 : 0xFF777777;
        String name = ("VICTORY".equals(outcome) ? "Won war vs " : "DEFEAT".equals(outcome) ? "Lost war vs " : "War vs ")
                + war.getOpponentColonyName();
        String desc = outcome + " vs " + war.getOpponentColonyName() + "  (" + war.getFormattedTimestamp() + ")";
        String stat = outcome.charAt(0) + outcome.substring(1).toLowerCase();
        return new net.machiavelli.minecolonytax.events.random.EventLogEntry(id, name, desc, stat, color, false, 0);
    }

    private static net.machiavelli.minecolonytax.events.random.EventLogEntry raidToLogEntry(
            net.machiavelli.minecolonytax.data.HistoryManager.RaidEntry raid) {
        String id = "raid_" + raid.getTimestamp();
        String name = "Raid by " + raid.getRaiderName();
        String desc; String stat; int color;
        if (raid.isSuccessful()) {
            desc = "Looted " + raid.getAmountStolen() + "$  (" + raid.getFormattedTimestamp() + ")";
            stat = "Looted " + raid.getAmountStolen() + "$";
            color = 0xFFA03030;
        } else {
            String reason = raid.getFailureReason() != null ? raid.getFailureReason() : "unknown";
            desc = "Repelled: " + reason + "  (" + raid.getFormattedTimestamp() + ")";
            stat = "Repelled";
            color = 0xFF2A6E2A;
        }
        return new net.machiavelli.minecolonytax.events.random.EventLogEntry(id, name, desc, stat, color, false, 0);
    }
}
