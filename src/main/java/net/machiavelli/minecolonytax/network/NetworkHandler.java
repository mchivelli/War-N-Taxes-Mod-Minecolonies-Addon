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
import net.machiavelli.minecolonytax.network.packets.RequestTreasuryDataPacket;
import net.machiavelli.minecolonytax.network.packets.TreasuryDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.TreasuryActionPacket;
import net.machiavelli.minecolonytax.network.packets.SetTaxPolicyPacket;
import net.machiavelli.minecolonytax.network.packets.RequestSpyDataPacket;
import net.machiavelli.minecolonytax.network.packets.SpyDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.DeploySpyPacket;
import net.machiavelli.minecolonytax.network.packets.DismissEventPacket;
import net.machiavelli.minecolonytax.network.packets.DismissSpyMissionPacket;
import net.machiavelli.minecolonytax.network.packets.RecallSpyPacket;
import net.machiavelli.minecolonytax.network.packets.RequestInvestmentDataPacket;
import net.machiavelli.minecolonytax.network.packets.InvestmentDataResponsePacket;
import net.machiavelli.minecolonytax.network.packets.BuyInvestmentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
        public static final String PROTOCOL_VERSION = "3";
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
                // PLAY_TO_CLIENT: only the server can send EntityGlowPacket. Without the
                // direction constraint, Forge accepts the packet from either side, allowing
                // a malicious client to spam the netty decode path on the server.
                CHANNEL.messageBuilder(EntityGlowPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(EntityGlowPacket::decode)
                                .encoder(EntityGlowPacket::encode)
                                .consumerMainThread(EntityGlowPacket::handle)
                                .add();

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

                CHANNEL.messageBuilder(OpenTaxGUIPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(OpenTaxGUIPacket::new)
                                .encoder(OpenTaxGUIPacket::encode)
                                .consumerMainThread(OpenTaxGUIPacket::handle)
                                .add();

                CHANNEL.messageBuilder(RequestTreasuryDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestTreasuryDataPacket::new)
                                .encoder(RequestTreasuryDataPacket::toBytes)
                                .consumerMainThread(RequestTreasuryDataPacket::handle)
                                .add();

                CHANNEL.messageBuilder(TreasuryDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(TreasuryDataResponsePacket::new)
                                .encoder(TreasuryDataResponsePacket::toBytes)
                                .consumerMainThread(TreasuryDataResponsePacket::handle)
                                .add();

                CHANNEL.messageBuilder(TreasuryActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(TreasuryActionPacket::new)
                                .encoder(TreasuryActionPacket::toBytes)
                                .consumerMainThread(TreasuryActionPacket::handle)
                                .add();

                CHANNEL.messageBuilder(SetTaxPolicyPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(SetTaxPolicyPacket::new)
                                .encoder(SetTaxPolicyPacket::toBytes)
                                .consumerMainThread(SetTaxPolicyPacket::handle)
                                .add();

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

                CHANNEL.messageBuilder(DismissSpyMissionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(DismissSpyMissionPacket::new)
                                .encoder(DismissSpyMissionPacket::toBytes)
                                .consumerMainThread(DismissSpyMissionPacket::handle)
                                .add();

                CHANNEL.messageBuilder(DismissEventPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(DismissEventPacket::new)
                                .encoder(DismissEventPacket::toBytes)
                                .consumerMainThread(DismissEventPacket::handle)
                                .add();

                CHANNEL.messageBuilder(RequestInvestmentDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(RequestInvestmentDataPacket::new)
                                .encoder(RequestInvestmentDataPacket::toBytes)
                                .consumerMainThread(RequestInvestmentDataPacket::handle)
                                .add();

                CHANNEL.messageBuilder(InvestmentDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                                .decoder(InvestmentDataResponsePacket::new)
                                .encoder(InvestmentDataResponsePacket::toBytes)
                                .consumerMainThread(InvestmentDataResponsePacket::handle)
                                .add();

                CHANNEL.messageBuilder(BuyInvestmentPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                                .decoder(BuyInvestmentPacket::new)
                                .encoder(BuyInvestmentPacket::toBytes)
                                .consumerMainThread(BuyInvestmentPacket::handle)
                                .add();

                if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) MineColonyTax.LOGGER.info("Network channel registered with {} packets", packetId);
        }

        public static <MSG> void sendToServer(MSG message) {
                CHANNEL.sendToServer(message);
        }

        public static <MSG> void sendToPlayer(ServerPlayer player, MSG message) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
}
