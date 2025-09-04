package net.machiavelli.minecolonytax.network;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class GlowClientHandler {
    // entityId -> endGameTime (client world ticks)
    private static final Map<Integer, Long> glowingEntities = new ConcurrentHashMap<>();

    public static void handleGlowPacket(int entityId, boolean shouldGlow, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        if (e == null) return;

        if (shouldGlow) {
            e.setGlowingTag(true);
            long end = level.getGameTime() + Math.max(0, durationTicks);
            glowingEntities.put(entityId, end);
        } else {
            e.setGlowingTag(false);
            glowingEntities.remove(entityId);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();

        Iterator<Map.Entry<Integer, Long>> it = glowingEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            int id = entry.getKey();
            long end = entry.getValue();
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive() || now >= end) {
                if (e != null) {
                    e.setGlowingTag(false);
                }
                it.remove();
            } else {
                // Re-assert glow each tick to override any server metadata updates
                e.setGlowingTag(true);
            }
        }
    }
}
