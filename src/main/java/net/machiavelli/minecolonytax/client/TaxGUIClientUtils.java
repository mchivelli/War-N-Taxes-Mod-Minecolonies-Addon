package net.machiavelli.minecolonytax.client;

import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side utilities for Tax GUI functionality
 * This class is only loaded on the client side to prevent server crashes
 */
@OnlyIn(Dist.CLIENT)
public class TaxGUIClientUtils {
    
    /**
     * Client-side method to open the GUI (called from keybind or client command)
     */
    public static void openTaxGUI() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new TaxManagementScreen());
        }
    }
}






