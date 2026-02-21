package net.machiavelli.minecolonytax.network;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
import net.machiavelli.minecolonytax.network.packets.ColonyDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.PayTaxDebtPacket;
import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
import net.machiavelli.minecolonytax.network.packets.UpdateTaxPermissionPacket;
import net.machiavelli.minecolonytax.network.packets.UpdatePlayerTaxPermissionPacket;
import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPacket;
import net.machiavelli.minecolonytax.network.packets.OfficerDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.RequestColonyDataPacket;
import net.machiavelli.minecolonytax.network.packets.OpenTaxGUIPacket;
import net.machiavelli.minecolonytax.network.packets.RequestWarChestDataPacket;
import net.machiavelli.minecolonytax.network.packets.WarChestDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.WarChestActionPacket;
import net.machiavelli.minecolonytax.network.packets.SetTaxPolicyPacket;
import net.machiavelli.minecolonytax.network.packets.RequestSpyDataPacket;
import net.machiavelli.minecolonytax.network.packets.SpyDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.DeploySpyPacket;
import net.machiavelli.minecolonytax.network.packets.RecallSpyPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
        public static final String PROTOCOL_VERSION = "1";
        public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(MineColonyTax.MOD_ID, "main"),
                        () -> PROTOCOL_VERSION,
                        PROTOCOL_VERSION::equals,
                        PROTOCOL_VERSION::equals);

        private static int packetId = 0;

        private static int nextId() {
                return packetId++;
        }

        public static void register() {
                // Register existing EntityGlowPacket
                CHANNEL.registerMessage(
                                nextId(),
                                EntityGlowPacket.class,
                                EntityGlowPacket::encode,
                                EntityGlowPacket::decode,
                                EntityGlowPacket::handle);

                // Register new GUI packets
                CHANNEL.messageBuilder(RequestColonyDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestColonyDataPacket::new)
                                .encoder(RequestColonyDataPacket::toBytes)
                                .consumerMainThread(RequestColonyDataPacket::handle)
                                .add();

                CHANNEL.messageBuilder(ColonyDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(ColonyDataResponsePacket::new)
                                .encoder(ColonyDataResponsePacket::toBytes)
                                .consumerMainThread(ColonyDataResponsePacket::handle)
                                .add();

                CHANNEL.messageBuilder(ClaimTaxPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(ClaimTaxPacket::new)
                                .encoder(ClaimTaxPacket::toBytes)
                                .consumerMainThread(ClaimTaxPacket::handle)
                                .add();

                CHANNEL.messageBuilder(PayTaxDebtPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(PayTaxDebtPacket::new)
                                .encoder(PayTaxDebtPacket::toBytes)
                                .consumerMainThread(PayTaxDebtPacket::handle)
                                .add();

                CHANNEL.messageBuilder(EndVassalizationPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(EndVassalizationPacket::new)
                                .encoder(EndVassalizationPacket::toBytes)
                                .consumerMainThread(EndVassalizationPacket::handle)
                                .add();

                CHANNEL.messageBuilder(UpdateTaxPermissionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(UpdateTaxPermissionPacket::new)
                                .encoder(UpdateTaxPermissionPacket::toBytes)
                                .consumerMainThread(UpdateTaxPermissionPacket::handle)
                                .add();

                CHANNEL.messageBuilder(UpdatePlayerTaxPermissionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(UpdatePlayerTaxPermissionPacket::new)
                                .encoder(UpdatePlayerTaxPermissionPacket::encode)
                                .consumerMainThread(UpdatePlayerTaxPermissionPacket::handle)
                                .add();

                CHANNEL.messageBuilder(RequestOfficerDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestOfficerDataPacket::new)
                                .encoder(RequestOfficerDataPacket::toBytes)
                                .consumerMainThread(RequestOfficerDataPacket::handle)
                                .add();

                CHANNEL.messageBuilder(OfficerDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(OfficerDataResponsePacket::new)
                                .encoder(OfficerDataResponsePacket::toBytes)
                                .consumerMainThread(OfficerDataResponsePacket::handle)
                                .add();

                // OpenTaxGUIPacket - server to client, tells client to open Tax GUI
                CHANNEL.messageBuilder(OpenTaxGUIPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(OpenTaxGUIPacket::new)
                                .encoder(OpenTaxGUIPacket::encode)
                                .consumerMainThread(OpenTaxGUIPacket::handle)
                                .add();

                // War Chest packets
                CHANNEL.messageBuilder(RequestWarChestDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestWarChestDataPacket::new)
                                .encoder(RequestWarChestDataPacket::toBytes)
                                .consumerMainThread(RequestWarChestDataPacket::handle)
                                .add();

                CHANNEL.messageBuilder(WarChestDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(WarChestDataResponsePacket::new)
                                .encoder(WarChestDataResponsePacket::toBytes)
                                .consumerMainThread(WarChestDataResponsePacket::handle)
                                .add();

                CHANNEL.messageBuilder(WarChestActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(WarChestActionPacket::new)
                                .encoder(WarChestActionPacket::toBytes)
                                .consumerMainThread(WarChestActionPacket::handle)
                                .add();

                // Tax Policy packet
                CHANNEL.messageBuilder(SetTaxPolicyPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(SetTaxPolicyPacket::new)
                                .encoder(SetTaxPolicyPacket::toBytes)
                                .consumerMainThread(SetTaxPolicyPacket::handle)
                                .add();

                // Espionage packets
                CHANNEL.messageBuilder(RequestSpyDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestSpyDataPacket::new)
                                .encoder(RequestSpyDataPacket::toBytes)
                                .consumerMainThread(RequestSpyDataPacket::handle)
                                .add();

                CHANNEL.messageBuilder(SpyDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(SpyDataResponsePacket::new)
                                .encoder(SpyDataResponsePacket::toBytes)
                                .consumerMainThread(SpyDataResponsePacket::handle)
                                .add();

                CHANNEL.messageBuilder(DeploySpyPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(DeploySpyPacket::new)
                                .encoder(DeploySpyPacket::toBytes)
                                .consumerMainThread(DeploySpyPacket::handle)
                                .add();

                CHANNEL.messageBuilder(RecallSpyPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RecallSpyPacket::new)
                                .encoder(RecallSpyPacket::toBytes)
                                .consumerMainThread(RecallSpyPacket::handle)
                                .add();

                MineColonyTax.LOGGER.info("Network channel registered with {} packets", packetId);
        }

        public static <MSG> void sendToServer(MSG message) {
                CHANNEL.sendToServer(message);
        }

        public static <MSG> void sendToPlayer(ServerPlayer player, MSG message) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
}
