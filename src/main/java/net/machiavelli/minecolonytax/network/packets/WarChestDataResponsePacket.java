package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-Client packet with War Chest data response.
 */
public class WarChestDataResponsePacket {

    private final int colonyId;
    private final int balance;
    private final int maxCapacity;
    private final int drainPerMinute;
    private final int taxBalance;
    private final boolean autoSurrender;
    private final double minPercentForWar;

    public WarChestDataResponsePacket(int colonyId, int balance, int maxCapacity,
            int drainPerMinute, int taxBalance, boolean autoSurrender, double minPercentForWar) {
        this.colonyId = colonyId;
        this.balance = balance;
        this.maxCapacity = maxCapacity;
        this.drainPerMinute = drainPerMinute;
        this.taxBalance = taxBalance;
        this.autoSurrender = autoSurrender;
        this.minPercentForWar = minPercentForWar;
    }

    public WarChestDataResponsePacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.balance = buf.readInt();
        this.maxCapacity = buf.readInt();
        this.drainPerMinute = buf.readInt();
        this.taxBalance = buf.readInt();
        this.autoSurrender = buf.readBoolean();
        this.minPercentForWar = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(balance);
        buf.writeInt(maxCapacity);
        buf.writeInt(drainPerMinute);
        buf.writeInt(taxBalance);
        buf.writeBoolean(autoSurrender);
        buf.writeDouble(minPercentForWar);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            handleOnClient();
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleOnClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TaxManagementScreen screen) {
            screen.updateWarChestData(colonyId, balance, maxCapacity, drainPerMinute,
                    taxBalance, autoSurrender, minPercentForWar);
        }
    }

    // Getters for client-side use
    public int getColonyId() {
        return colonyId;
    }

    public int getBalance() {
        return balance;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getDrainPerMinute() {
        return drainPerMinute;
    }

    public int getTaxBalance() {
        return taxBalance;
    }

    public boolean isAutoSurrender() {
        return autoSurrender;
    }

    public double getMinPercentForWar() {
        return minPercentForWar;
    }
}
