package net.machiavelli.minecolonytax.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.client.TaxGUIClientUtils;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyBindings {
    
    public static final String KEY_CATEGORY_MINECOLONYTAX = "key.category.minecolonytax";
    public static final String KEY_OPEN_TAX_GUI = "key.minecolonytax.open_tax_gui";
    
    /**
     * Default key for the tax/war codex.
     *
     * <p>MUST NOT be T. T is vanilla Minecraft's "open chat" key, so defaulting to it put this mod
     * in a conflict every player hits on their first session: pressing T either opened the codex
     * instead of chat or triggered both, depending on which binding won. G is unbound in vanilla.
     */
    public static final KeyMapping OPEN_TAX_GUI_KEY = new KeyMapping(
            KEY_OPEN_TAX_GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY_MINECOLONYTAX
    );
    
    @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventBusClientEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN_TAX_GUI_KEY);
        }
    }
    
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (OPEN_TAX_GUI_KEY.consumeClick()) {
            TaxGUIClientUtils.openTaxGUI();
        }
    }
}
