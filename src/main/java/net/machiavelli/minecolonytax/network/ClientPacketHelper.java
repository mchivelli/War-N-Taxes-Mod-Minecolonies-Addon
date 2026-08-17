package net.machiavelli.minecolonytax.network;

import net.machiavelli.minecolonytax.network.packets.*;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Helper class for sending packets from client to server.
 * Wraps the new NeoForge payload system for easier use from GUI code.
 */
public class ClientPacketHelper {

    public static void sendClaimTaxPacket(int colonyId, int amount) {
        PacketDistributor.sendToServer(new ClaimTaxPayload(colonyId, amount));
    }

    public static void sendRequestColonyDataPacket(int colonyId) {
        PacketDistributor.sendToServer(new RequestColonyDataPayload(colonyId));
    }

    public static void sendRequestOfficerDataPacket(int colonyId) {
        PacketDistributor.sendToServer(new RequestOfficerDataPayload(colonyId));
    }

    public static void sendUpdateTaxPermissionPacket(int colonyId,
            net.machiavelli.minecolonytax.permissions.ColonyPermission permission, boolean allowOfficers) {
        PacketDistributor.sendToServer(new UpdateTaxPermissionPayload(colonyId, permission, allowOfficers));
    }

    public static void sendUpdatePlayerTaxPermissionPacket(int colonyId, UUID playerId,
            net.machiavelli.minecolonytax.permissions.ColonyPermission permission, boolean allowed) {
        PacketDistributor.sendToServer(new UpdatePlayerTaxPermissionPayload(colonyId, playerId, permission, allowed));
    }

    public static void sendPayTaxDebtPacket(int colonyId) {
        PacketDistributor.sendToServer(new PayTaxDebtPayload(colonyId));
    }

    public static void sendPayDebtPacket(int colonyId, int amount) {
        PacketDistributor.sendToServer(new PayDebtPayload(colonyId, amount));
    }

    public static void sendClaimVassalTributePacket(int vassalColonyId) {
        PacketDistributor.sendToServer(new ClaimVassalTributePayload(vassalColonyId));
    }

    public static void sendEndVassalizationPacket(int colonyId) {
        PacketDistributor.sendToServer(new EndVassalizationPayload(colonyId));
    }

    public static void sendSetTaxPolicyPacket(int colonyId, String policyName) {
        PacketDistributor.sendToServer(new SetTaxPolicyPayload(colonyId, policyName));
    }

    public static void sendRequestWarChestDataPacket(int colonyId) {
        PacketDistributor.sendToServer(new RequestWarChestDataPayload(colonyId));
    }

    public static void sendWarChestActionPacket(int colonyId, WarChestActionPayload.ActionType action, int amount) {
        PacketDistributor.sendToServer(new WarChestActionPayload(colonyId, action, amount));
    }

    public static void sendRequestSpyDataPacket() {
        PacketDistributor.sendToServer(new RequestSpyDataPayload());
    }

    public static void sendDeploySpyPacket(int targetColonyId, String missionType) {
        PacketDistributor.sendToServer(new DeploySpyPayload(targetColonyId, missionType));
    }

    public static void sendRecallSpyPacket(String missionId) {
        PacketDistributor.sendToServer(new RecallSpyPayload(missionId));
    }

    public static void sendDismissSpyMissionPacket(String missionId) {
        PacketDistributor.sendToServer(new DismissSpyMissionPayload(missionId));
    }
}
