package net.machiavelli.minecolonytax.gui;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

public class SpyDialogScreen extends Screen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(MineColonyTax.MOD_ID,
            "textures/gui/backgroundmenu.png");
    private static final String[] DIALOGUE = {
            "I'm just... looking around. Nice guard towers.",
            "Don't mind me, I'm a travelling merchant.",
            "How many guards did you say you have?",
            "Beautiful colony. Very... strategic layout.",
            "I was just admiring your storehouse. Lots of resources?"
    };

    private final String currentDialogue;
    private final int GUI_WIDTH = 250;
    private final int GUI_HEIGHT = 100;

    public SpyDialogScreen() {
        super(Component.literal("Suspicious Citizen"));
        this.currentDialogue = DIALOGUE[new Random().nextInt(DIALOGUE.length)];
    }

    @Override
    protected void init() {
        super.init();
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(guiLeft + (GUI_WIDTH - 60) / 2, guiTop + GUI_HEIGHT - 35, 60, 20)
                .build());
    }

    @Override
    public void render(@javax.annotation.Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // Draw background
        try {
            // Very simple stretch for the dialog
            guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
        } catch (Exception e) {
            guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF2D2D30);
            guiGraphics.fill(guiLeft + 2, guiTop + 2, guiLeft + GUI_WIDTH - 2, guiTop + GUI_HEIGHT - 2, 0xFF1E1E1E);
        }

        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 10, 0xFFC107);

        // Draw separator
        guiGraphics.fill(guiLeft + 10, guiTop + 25, guiLeft + GUI_WIDTH - 10, guiTop + 26, 0xFF555555);

        // Draw dialog text
        // Wrapped drawing if text is too long (using drawWordWrap)
        guiGraphics.drawWordWrap(this.font, Component.literal("\"" + currentDialogue + "\""), guiLeft + 20, guiTop + 40,
                GUI_WIDTH - 40, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
