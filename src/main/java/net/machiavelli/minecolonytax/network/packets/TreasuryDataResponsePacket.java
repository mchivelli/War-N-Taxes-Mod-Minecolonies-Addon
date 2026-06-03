package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-Client packet carrying treasury data for a colony.
 */
public class TreasuryDataResponsePacket {

    private final int colonyId;
    private final int balance;
    private final int maxCapacity;
    private final int drainPerMinute;
    private final int taxBalance;
    private final boolean autoSurrender;
    private final double minPercentForWar;
    /**
     * Player's economy wallet balance. -1 means wallet (SDMShop/SDMEconomy) is not available.
     */
    private final long walletBalance;

    public TreasuryDataResponsePacket(int colonyId, int balance, int maxCapacity,
            int drainPerMinute, int taxBalance, boolean autoSurrender, double minPercentForWar,
            long walletBalance) {
        this.colonyId = colonyId;
        this.balance = balance;
        this.maxCapacity = maxCapacity;
        this.drainPerMinute = drainPerMinute;
        this.taxBalance = taxBalance;
        this.autoSurrender = autoSurrender;
        this.minPercentForWar = minPercentForWar;
        this.walletBalance = walletBalance;
    }

    public TreasuryDataResponsePacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.balance = buf.readInt();
        this.maxCapacity = buf.readInt();
        this.drainPerMinute = buf.readInt();
        this.taxBalance = buf.readInt();
        this.autoSurrender = buf.readBoolean();
        this.minPercentForWar = buf.readDouble();
        this.walletBalance = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(balance);
        buf.writeInt(maxCapacity);
        buf.writeInt(drainPerMinute);
        buf.writeInt(taxBalance);
        buf.writeBoolean(autoSurrender);
        buf.writeDouble(minPercentForWar);
        buf.writeLong(walletBalance);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Wrap client-only access in DistExecutor so this class is safe to load on a
            // dedicated server. The double-lambda ensures the Minecraft/TaxManagementScreen
            // references are only class-loaded when running on the physical client.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen screen) {
                    screen.updateTreasuryData(colonyId, balance, maxCapacity, drainPerMinute,
                            taxBalance, autoSurrender, minPercentForWar, walletBalance);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public int getColonyId()          { return colonyId; }
    public int getBalance()           { return balance; }
    public int getMaxCapacity()       { return maxCapacity; }
    public int getDrainPerMinute()    { return drainPerMinute; }
    public int getTaxBalance()        { return taxBalance; }
    public boolean isAutoSurrender()  { return autoSurrender; }
    public double getMinPercentForWar() { return minPercentForWar; }
    public long getWalletBalance()    { return walletBalance; }
}
