package net.machiavelli.minecolonytax.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class InvestmentDataResponsePacket {

    private final int colonyId;
    private final Map<String, Integer> upgradeLevels;
    private final Map<String, Integer> upgradeCosts;
    private final int treasuryBalance;
    private final int maxCapacity;
    private final int maxLevel;

    public InvestmentDataResponsePacket(int colonyId, Map<String, Integer> upgradeLevels,
                                        Map<String, Integer> upgradeCosts,
                                        int treasuryBalance, int maxCapacity, int maxLevel) {
        this.colonyId = colonyId;
        this.upgradeLevels = new LinkedHashMap<>(upgradeLevels);
        this.upgradeCosts = new LinkedHashMap<>(upgradeCosts);
        this.treasuryBalance = treasuryBalance;
        this.maxCapacity = maxCapacity;
        this.maxLevel = maxLevel;
    }

    public InvestmentDataResponsePacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        int levelCount = buf.readInt();
        this.upgradeLevels = new LinkedHashMap<>();
        for (int i = 0; i < levelCount; i++) {
            upgradeLevels.put(buf.readUtf(), buf.readInt());
        }
        int costCount = buf.readInt();
        this.upgradeCosts = new LinkedHashMap<>();
        for (int i = 0; i < costCount; i++) {
            upgradeCosts.put(buf.readUtf(), buf.readInt());
        }
        this.treasuryBalance = buf.readInt();
        this.maxCapacity = buf.readInt();
        this.maxLevel = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(upgradeLevels.size());
        for (Map.Entry<String, Integer> e : upgradeLevels.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeInt(e.getValue());
        }
        buf.writeInt(upgradeCosts.size());
        for (Map.Entry<String, Integer> e : upgradeCosts.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeInt(e.getValue());
        }
        buf.writeInt(treasuryBalance);
        buf.writeInt(maxCapacity);
        buf.writeInt(maxLevel);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Wrap client-only access in DistExecutor so this class is safe to load on a
            // dedicated server. The double-lambda ensures Minecraft/Screen/TaxManagementScreen
            // references are only class-loaded when running on the physical client.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.gui.screens.Screen screen =
                        net.minecraft.client.Minecraft.getInstance().screen;
                if (screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen taxScreen) {
                    taxScreen.updateInvestmentData(colonyId, upgradeLevels, upgradeCosts,
                            treasuryBalance, maxCapacity, maxLevel);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public int getColonyId()                       { return colonyId; }
    public Map<String, Integer> getUpgradeLevels() { return upgradeLevels; }
    public Map<String, Integer> getUpgradeCosts()  { return upgradeCosts; }
    public int getTreasuryBalance()                { return treasuryBalance; }
    public int getMaxCapacity()                    { return maxCapacity; }
    public int getMaxLevel()                       { return maxLevel; }
}
