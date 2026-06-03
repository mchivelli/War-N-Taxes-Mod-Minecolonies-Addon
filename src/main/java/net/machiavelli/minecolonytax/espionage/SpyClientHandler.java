package net.machiavelli.minecolonytax.espionage;

import net.machiavelli.minecolonytax.gui.SpyDialogScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SpyClientHandler {
    private SpyClientHandler() {}

    public static void openSpyDialog() {
        Minecraft.getInstance().setScreen(new SpyDialogScreen());
    }
}
