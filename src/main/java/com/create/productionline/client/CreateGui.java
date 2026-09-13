package com.create.productionline.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Thin helpers that skin our container screens with Create's own GUI controls
 * (referenced at runtime from the Create jar — nothing is copied into this mod).
 * Sizes are stretched to the caller's layout; UV coordinates come from
 * {@link AllGuiTextures} itself, so they always match Create's texture sheets.
 */
public final class CreateGui {

    private CreateGui() {
    }

    /** Draws a Create-styled item slot frame filling the given 18x18-ish area. */
    public static void slot(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        blit(guiGraphics, AllGuiTextures.FROGPORT_SLOT, x, y, width, height);
    }

    /** Stretch-blits one of Create's GUI sprites using its own UV coordinates. */
    public static void blit(GuiGraphics guiGraphics, AllGuiTextures texture, int x, int y, int width, int height) {
        if (texture == null || guiGraphics == null) {
            return;
        }
        guiGraphics.blit(texture.getLocation(), x, y, width, height,
                texture.getStartX(), texture.getStartY(),
                texture.getWidth(), texture.getHeight(),
                256, 256);
    }
}
