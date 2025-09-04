package net.machiavelli.minecolonytax.network;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineColonyTax.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        int id = nextId();
        CHANNEL.registerMessage(
                id,
                EntityGlowPacket.class,
                EntityGlowPacket::encode,
                EntityGlowPacket::decode,
                EntityGlowPacket::handle
        );
        MineColonyTax.LOGGER.info("Network channel registered ({}:{} id={})", MineColonyTax.MOD_ID, "main", id);
    }
}
