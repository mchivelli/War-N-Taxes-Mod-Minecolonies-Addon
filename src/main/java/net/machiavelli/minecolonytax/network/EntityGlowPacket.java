package net.machiavelli.minecolonytax.network;

import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class EntityGlowPacket {
    private final int entityId;
    private final boolean shouldGlow;
    private final int durationTicks;

    public EntityGlowPacket(int entityId, boolean shouldGlow, int durationTicks) {
        this.entityId = entityId;
        this.shouldGlow = shouldGlow;
        this.durationTicks = durationTicks;
    }

    public static void encode(EntityGlowPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.shouldGlow);
        buf.writeVarInt(msg.durationTicks);
    }

    // Maximum duration: 1 hour at 20 TPS. Bounds the VarInt to prevent malformed/malicious
    // payloads from forcing the server (or trusting client) to schedule absurd glow durations.
    private static final int MAX_DURATION_TICKS = 20 * 60 * 60;
    // Sane upper bound on entity IDs (Minecraft uses sequential ints; 1 << 24 is well above
    // any realistic count). Negative IDs are never valid.
    private static final int MAX_ENTITY_ID = 1 << 24;

    public static EntityGlowPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean glow = buf.readBoolean();
        int duration = buf.readVarInt();
        if (id < 0 || id > MAX_ENTITY_ID) {
            throw new IllegalArgumentException("EntityGlowPacket: entity id out of bounds: " + id);
        }
        if (duration < 0 || duration > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("EntityGlowPacket: duration out of bounds: " + duration);
        }
        return new EntityGlowPacket(id, glow, duration);
    }

    public static void handle(EntityGlowPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx.getDirection().getReceptionSide().isClient()) {
            ctx.enqueueWork(() -> GlowClientHandler.handleGlowPacket(msg.entityId, msg.shouldGlow, msg.durationTicks));
        }
        ctx.setPacketHandled(true);
    }
}
