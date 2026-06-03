package net.machiavelli.minecolonytax.network;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Handles entity glow effects on the client side.
 */
@EventBusSubscriber(modid = MineColonyTax.MOD_ID, value = Dist.CLIENT)
public class GlowClientHandler {
    
    private static final Map<Integer, Integer> glowingEntities = new HashMap<>();
    
    /**
     * Add an entity to the glow list
     */
    public static void handleGlowPacket(int entityId, boolean shouldGlow, int durationTicks) {
        if (shouldGlow) {
            glowingEntities.put(entityId, durationTicks);
            MineColonyTax.LOGGER.debug("Added entity {} to glow list for {} ticks", entityId, durationTicks);
        } else {
            glowingEntities.remove(entityId);
            MineColonyTax.LOGGER.debug("Removed entity {} from glow list", entityId);
        }
    }
    
    /**
     * Update glowing entities every tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        
        if (level == null) {
            return;
        }
        
        Iterator<Map.Entry<Integer, Integer>> iterator = glowingEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            int entityId = entry.getKey();
            int remaining = entry.getValue();
            
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                entity.setGlowingTag(true);
                
                // Decrease duration
                remaining--;
                if (remaining <= 0) {
                    entity.setGlowingTag(false);
                    iterator.remove();
                    MineColonyTax.LOGGER.debug("Glow expired for entity {}", entityId);
                } else {
                    entry.setValue(remaining);
                }
            } else {
                // Entity no longer exists
                iterator.remove();
            }
        }
    }
}
