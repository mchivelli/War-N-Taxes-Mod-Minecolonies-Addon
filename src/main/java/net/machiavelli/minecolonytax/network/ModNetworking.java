package net.machiavelli.minecolonytax.network;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.network.packets.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Modern NeoForge 1.21.1 networking using Payload system.
 * Replaces the old SimpleChannel approach.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MineColonyTax.MOD_ID)
            .versioned("1.0")
            .optional();

        // ---- Server-bound packets (client -> server) ----

        registrar.playToServer(
            ClaimTaxPayload.TYPE,
            ClaimTaxPayload.STREAM_CODEC,
            ClaimTaxPayload::handle
        );

        registrar.playToServer(
            RequestColonyDataPayload.TYPE,
            RequestColonyDataPayload.STREAM_CODEC,
            RequestColonyDataPayload::handle
        );

        registrar.playToServer(
            RequestOfficerDataPayload.TYPE,
            RequestOfficerDataPayload.STREAM_CODEC,
            RequestOfficerDataPayload::handle
        );

        registrar.playToServer(
            UpdateTaxPermissionPayload.TYPE,
            UpdateTaxPermissionPayload.STREAM_CODEC,
            UpdateTaxPermissionPayload::handle
        );

        registrar.playToServer(
            UpdatePlayerTaxPermissionPayload.TYPE,
            UpdatePlayerTaxPermissionPayload.STREAM_CODEC,
            UpdatePlayerTaxPermissionPayload::handle
        );

        registrar.playToServer(
            PayTaxDebtPayload.TYPE,
            PayTaxDebtPayload.STREAM_CODEC,
            PayTaxDebtPayload::handle
        );

        registrar.playToServer(
            PayDebtPayload.TYPE,
            PayDebtPayload.STREAM_CODEC,
            PayDebtPayload::handle
        );

        registrar.playToServer(
            ClaimVassalTributePayload.TYPE,
            ClaimVassalTributePayload.STREAM_CODEC,
            ClaimVassalTributePayload::handle
        );

        registrar.playToServer(
            EndVassalizationPayload.TYPE,
            EndVassalizationPayload.STREAM_CODEC,
            EndVassalizationPayload::handle
        );

        registrar.playToServer(
            SetTaxPolicyPayload.TYPE,
            SetTaxPolicyPayload.STREAM_CODEC,
            SetTaxPolicyPayload::handle
        );

        registrar.playToServer(
            RequestWarChestDataPayload.TYPE,
            RequestWarChestDataPayload.STREAM_CODEC,
            RequestWarChestDataPayload::handle
        );

        registrar.playToServer(
            WarChestActionPayload.TYPE,
            WarChestActionPayload.STREAM_CODEC,
            WarChestActionPayload::handle
        );

        registrar.playToServer(
            RequestSpyDataPayload.TYPE,
            RequestSpyDataPayload.STREAM_CODEC,
            RequestSpyDataPayload::handle
        );

        registrar.playToServer(
            DeploySpyPayload.TYPE,
            DeploySpyPayload.STREAM_CODEC,
            DeploySpyPayload::handle
        );

        registrar.playToServer(
            RecallSpyPayload.TYPE,
            RecallSpyPayload.STREAM_CODEC,
            RecallSpyPayload::handle
        );

        registrar.playToServer(
            DismissSpyMissionPayload.TYPE,
            DismissSpyMissionPayload.STREAM_CODEC,
            DismissSpyMissionPayload::handle
        );

        // ---- Client-bound packets (server -> client) ----

        registrar.playToClient(
            OpenTaxGUIPayload.TYPE,
            OpenTaxGUIPayload.STREAM_CODEC,
            OpenTaxGUIPayload::handle
        );

        registrar.playToClient(
            ColonyDataResponsePayload.TYPE,
            ColonyDataResponsePayload.STREAM_CODEC,
            ColonyDataResponsePayload::handle
        );

        registrar.playToClient(
            OfficerDataResponsePayload.TYPE,
            OfficerDataResponsePayload.STREAM_CODEC,
            OfficerDataResponsePayload::handle
        );

        registrar.playToClient(
            WarChestDataResponsePayload.TYPE,
            WarChestDataResponsePayload.STREAM_CODEC,
            WarChestDataResponsePayload::handle
        );

        registrar.playToClient(
            SpyDataResponsePayload.TYPE,
            SpyDataResponsePayload.STREAM_CODEC,
            SpyDataResponsePayload::handle
        );

        registrar.playToClient(
            EntityGlowPayload.TYPE,
            EntityGlowPayload.STREAM_CODEC,
            EntityGlowPayload::handle
        );

        MineColonyTax.LOGGER.info("Registered {} network packets", 22);
    }
}
