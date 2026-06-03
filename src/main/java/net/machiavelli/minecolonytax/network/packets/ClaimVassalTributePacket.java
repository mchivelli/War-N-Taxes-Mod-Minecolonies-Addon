package net.machiavelli.minecolonytax.network.packets;

import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Packet for claiming vassal tribute from the GUI.
 *
 * AUDIT FIX (CRIT — defensive_04 C2 / Top-12 #3, HIGH — defensive_04 H1):
 *   VassalManager.claimVassalTribute already deducts from the vassal colony
 *   and credits the overlord's tax ledger. This handler previously ALSO paid
 *   the player directly (SDMShop deposit / item) — every claim was double-paid.
 *   The player will collect the tribute through the regular claim-tax flow on
 *   the overlord's colony.
 *
 *   Also: handler was fully replayable (no rate-limit / idempotency). Spamming
 *   the packet drained the vassal's stored tax cumulatively. We add a short
 *   per-colony anti-spam cooldown.
 */
public class ClaimVassalTributePacket {
    /** Per-vassal-colony anti-spam cooldown (last-claim timestamp). 500ms is enough to defeat packet spam without affecting gameplay. */
    private static final ConcurrentHashMap<Integer, Long> LAST_CLAIM_MS = new ConcurrentHashMap<>();
    private static final long CLAIM_COOLDOWN_MS = 500L;

    private final int vassalColonyId;

    public ClaimVassalTributePacket(int vassalColonyId) {
        this.vassalColonyId = vassalColonyId;
    }

    public ClaimVassalTributePacket(FriendlyByteBuf buf) {
        this.vassalColonyId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(vassalColonyId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                handlePacket(player, vassalColonyId);
            }
        });
        context.setPacketHandled(true);
        return true;
    }

    private void handlePacket(ServerPlayer player, int vassalColonyId) {
        try {
            // Anti-spam cooldown — purpose is to stop packet replay storms, not gameplay throttling.
            long now = System.currentTimeMillis();
            Long previous = LAST_CLAIM_MS.get(vassalColonyId);
            if (previous != null && (now - previous) < CLAIM_COOLDOWN_MS) {
                player.sendSystemMessage(Component.literal("§c[Vassal Tribute] Please wait a moment before claiming again."));
                return;
            }
            LAST_CLAIM_MS.put(vassalColonyId, now);

            int tributeAmount = VassalManager.claimVassalTribute(player.getUUID(), vassalColonyId);

            if (tributeAmount > 0) {
                // The tribute has already been deducted from the vassal AND credited to the
                // overlord's stored-tax ledger inside VassalManager.claimVassalTribute. Do NOT
                // also pay the player here — that produced a double-charge (defensive_04 C2).
                // The player collects the credited amount via the normal claim-tax path on
                // their overlord colony.
                player.sendSystemMessage(Component.literal("§a[Vassal Tribute] Transferred " + tributeAmount +
                        " from this vassal to your overlord colony's tax balance."));
            } else {
                player.sendSystemMessage(Component.literal("§c[Vassal Tribute] No tribute available to claim from this vassal."));
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c[Error] Failed to claim vassal tribute: " + e.getMessage()));
        }
    }
}
