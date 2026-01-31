package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet sent from server to client containing colony tax data for the GUI
 */
public class ColonyDataResponsePacket {
    private final List<ColonyTaxData> colonyData;
    private final List<VassalIncomeData> vassalData;
    
    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData) {
        this.colonyData = colonyData;
        this.vassalData = new ArrayList<>();
    }
    
    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData) {
        this.colonyData = colonyData;
        this.vassalData = vassalData;
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
            boolean isVassal = buf.readBoolean();
            int vassalTributeRate = buf.readInt();
            boolean hasVassals = buf.readBoolean();
            int vassalCount = buf.readInt();
            long lastTaxGeneration = buf.readLong();
            int debtAmount = buf.readInt();
            int approximateRevenuePerInterval = buf.readInt();
            boolean isOwner = buf.readBoolean();
            String taxPolicy = buf.readUtf();

            this.colonyData.add(new ColonyTaxData(
                colonyId, colonyName, taxBalance, maxTaxRevenue,
                buildingCount, guardCount, guardTowerCount,
                canClaimTax, isAtWar, isBeingRaided,
                isVassal, vassalTributeRate, hasVassals, vassalCount,
                lastTaxGeneration, debtAmount, approximateRevenuePerInterval, isOwner, taxPolicy
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
            
            this.vassalData.add(new VassalIncomeData(
                vassalColonyId, vassalColonyName, tributeRate,
                tributeOwed, lastTribute, lastPayment, canClaim
            ));
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
            buf.writeBoolean(data.isVassal());
            buf.writeInt(data.getVassalTributeRate());
            buf.writeBoolean(data.hasVassals());
            buf.writeInt(data.getVassalCount());
            buf.writeLong(data.getLastTaxGeneration());
            buf.writeInt(data.getDebtAmount());
            buf.writeInt(data.getApproximateRevenuePerInterval());
            buf.writeBoolean(data.isOwner());
            buf.writeUtf(data.getTaxPolicy());
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
        }
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // This runs on the client side
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TaxManagementScreen) {
                TaxManagementScreen screen = (TaxManagementScreen) mc.screen;
                screen.updateColonyData(colonyData);
                screen.updateVassalData(vassalData);
            }
        });
        return true;
    }
}
