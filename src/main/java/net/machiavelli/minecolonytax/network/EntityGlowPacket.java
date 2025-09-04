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

    public static EntityGlowPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean glow = buf.readBoolean();
        int duration = buf.readVarInt();
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
