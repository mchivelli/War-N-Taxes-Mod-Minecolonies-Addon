package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.events.random.EventLogEntry;
import net.machiavelli.minecolonytax.gui.data.ColonySummary;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet sent from server to client containing colony tax data for the GUI
 */
public class ColonyDataResponsePacket {
    private final List<ColonyTaxData> colonyData;
    private final List<VassalIncomeData> vassalData;
    private final Map<Integer, List<EventLogEntry>> eventLogData;
    private final List<ColonySummary> allColonySummaries;

    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData) {
        this.colonyData = colonyData;
        this.vassalData = new ArrayList<>();
        this.eventLogData = new HashMap<>();
        this.allColonySummaries = new ArrayList<>();
    }

    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData) {
        this.colonyData = colonyData;
        this.vassalData = vassalData;
        this.eventLogData = new HashMap<>();
        this.allColonySummaries = new ArrayList<>();
    }

    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
                                    Map<Integer, List<EventLogEntry>> eventLogData) {
        this.colonyData = colonyData;
        this.vassalData = vassalData;
        this.eventLogData = eventLogData;
        this.allColonySummaries = new ArrayList<>();
    }

    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
                                    Map<Integer, List<EventLogEntry>> eventLogData,
                                    List<ColonySummary> allColonySummaries) {
        this.colonyData = colonyData;
        this.vassalData = vassalData;
        this.eventLogData = eventLogData;
        this.allColonySummaries = allColonySummaries != null ? allColonySummaries : new ArrayList<>();
    }
    
    public ColonyDataResponsePacket(FriendlyByteBuf buf) {
        // Read colony data
        int size = buf.readInt();
        this.colonyData = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            int colonyId = buf.readInt();
            String colonyName = buf.readUtf();
            int taxBalance = buf.readInt();
            int maxTaxRevenue = buf.readInt();
            int buildingCount = buf.readInt();
            int guardCount = buf.readInt();
            int guardTowerCount = buf.readInt();
            boolean canClaimTax = buf.readBoolean();
            boolean isAtWar = buf.readBoolean();
            boolean isBeingRaided = buf.readBoolean();
            boolean isBesieged = buf.readBoolean();
            boolean isOccupied = buf.readBoolean();
            boolean isVassal = buf.readBoolean();
            int vassalTributeRate = buf.readInt();
            boolean hasVassals = buf.readBoolean();
            int vassalCount = buf.readInt();
            long lastTaxGeneration = buf.readLong();
            int debtAmount = buf.readInt();
            int approximateRevenuePerInterval = buf.readInt();
            boolean isOwner = buf.readBoolean();
            String taxPolicy = buf.readUtf();
            double colonyHappiness = buf.readDouble();
            double happinessMultiplier = buf.readDouble();

            this.colonyData.add(new ColonyTaxData(
                colonyId, colonyName, taxBalance, maxTaxRevenue,
                buildingCount, guardCount, guardTowerCount,
                canClaimTax, isAtWar, isBeingRaided,
                isVassal, vassalTributeRate, hasVassals, vassalCount,
                lastTaxGeneration, debtAmount, approximateRevenuePerInterval, isOwner, taxPolicy,
                colonyHappiness, happinessMultiplier,
                isBesieged, isOccupied
            ));
        }
        
        // Read vassal data
        int vassalSize = buf.readInt();
        this.vassalData = new ArrayList<>();

        for (int i = 0; i < vassalSize; i++) {
            int vassalColonyId = buf.readInt();
            String vassalColonyName = buf.readUtf();
            int tributeRate = buf.readInt();
            int tributeOwed = buf.readInt();
            int lastTribute = buf.readInt();
            long lastPayment = buf.readLong();
            boolean canClaim = buf.readBoolean();
            int kindOrdinal = buf.readInt();
            VassalIncomeData.VassalKind kind;
            try {
                kind = VassalIncomeData.VassalKind.values()[kindOrdinal];
            } catch (Exception e) {
                kind = VassalIncomeData.VassalKind.VASSAL;
            }

            this.vassalData.add(new VassalIncomeData(
                vassalColonyId, vassalColonyName, tributeRate,
                tributeOwed, lastTribute, lastPayment, canClaim, kind
            ));
        }

        // Read event log data
        this.eventLogData = new HashMap<>();
        int logMapSize = buf.readInt();
        for (int i = 0; i < logMapSize; i++) {
            int logColonyId = buf.readInt();
            int entryCount = buf.readInt();
            List<EventLogEntry> entries = new ArrayList<>();
            for (int j = 0; j < entryCount; j++) {
                String eventId       = buf.readUtf();
                String displayName   = buf.readUtf();
                String description   = buf.readUtf();
                String compactStat   = buf.readUtf();
                int colorCode        = buf.readInt();
                boolean isActive     = buf.readBoolean();
                int remainingCycles  = buf.readInt();
                entries.add(new EventLogEntry(eventId, displayName, description,
                        compactStat, colorCode, isActive, remainingCycles));
            }
            this.eventLogData.put(logColonyId, entries);
        }

        // Read all colony summaries (for spy target selection)
        this.allColonySummaries = new ArrayList<>();
        int summaryCount = buf.readInt();
        for (int i = 0; i < summaryCount; i++) {
            int id = buf.readInt();
            String name = buf.readUtf();
            this.allColonySummaries.add(new ColonySummary(id, name));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyData.size());
        
        for (ColonyTaxData data : colonyData) {
            buf.writeInt(data.getColonyId());
            buf.writeUtf(data.getColonyName());
            buf.writeInt(data.getTaxBalance());
            buf.writeInt(data.getMaxTaxRevenue());
            buf.writeInt(data.getBuildingCount());
            buf.writeInt(data.getGuardCount());
            buf.writeInt(data.getGuardTowerCount());
            buf.writeBoolean(data.canClaimTax());
            buf.writeBoolean(data.isAtWar());
            buf.writeBoolean(data.isBeingRaided());
            buf.writeBoolean(data.isBesieged());
            buf.writeBoolean(data.isOccupied());
            buf.writeBoolean(data.isVassal());
            buf.writeInt(data.getVassalTributeRate());
            buf.writeBoolean(data.hasVassals());
            buf.writeInt(data.getVassalCount());
            buf.writeLong(data.getLastTaxGeneration());
            buf.writeInt(data.getDebtAmount());
            buf.writeInt(data.getApproximateRevenuePerInterval());
            buf.writeBoolean(data.isOwner());
            buf.writeUtf(data.getTaxPolicy());
            buf.writeDouble(data.getColonyHappiness());
            buf.writeDouble(data.getHappinessMultiplier());
        }
        
        // Write vassal data
        buf.writeInt(vassalData.size());

        for (VassalIncomeData data : vassalData) {
            buf.writeInt(data.getVassalColonyId());
            buf.writeUtf(data.getVassalColonyName());
            buf.writeInt(data.getTributeRate());
            buf.writeInt(data.getTributeOwed());
            buf.writeInt(data.getLastTribute());
            buf.writeLong(data.getLastPayment());
            buf.writeBoolean(data.canClaim());
            buf.writeInt(data.getKind() != null ? data.getKind().ordinal() : 0);
        }

        // Write event log data
        buf.writeInt(eventLogData.size());
        for (Map.Entry<Integer, List<EventLogEntry>> logEntry : eventLogData.entrySet()) {
            buf.writeInt(logEntry.getKey());
            List<EventLogEntry> entries = logEntry.getValue();
            buf.writeInt(entries.size());
            for (EventLogEntry entry : entries) {
                buf.writeUtf(entry.getEventId());
                buf.writeUtf(entry.getDisplayName());
                buf.writeUtf(entry.getDescription());
                buf.writeUtf(entry.getCompactStat());
                buf.writeInt(entry.getColorCode());
                buf.writeBoolean(entry.isActive());
                buf.writeInt(entry.getRemainingCycles());
            }
        }

        // Write all colony summaries (for spy target selection)
        buf.writeInt(allColonySummaries.size());
        for (ColonySummary s : allColonySummaries) {
            buf.writeInt(s.getId());
            buf.writeUtf(s.getName());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // This runs on the client side — wrap to avoid class-loading client-only
            // classes (Minecraft, TaxManagementScreen) on a dedicated server.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen) {
                    net.machiavelli.minecolonytax.gui.TaxManagementScreen screen =
                            (net.machiavelli.minecolonytax.gui.TaxManagementScreen) mc.screen;
                    screen.updateColonyData(colonyData);
                    screen.updateVassalData(vassalData);
                    screen.updateEventData(eventLogData);
                    screen.updateAllColonySummaries(allColonySummaries);
                }
            });
        });
        return true;
    }
}
