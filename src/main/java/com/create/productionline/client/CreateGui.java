package com.create.productionline.client;

import java.util.List;

import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.minecraft.client.gui.Font;
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

    /**
     * Splits a line so it fits the given pixel width. Character-based on purpose:
     * a run without whitespace (e.g. a whole CJK sentence, or a long id) must be
     * broken mid-run — a word-aware splitter would emit it as one over-wide line
     * and the text would overflow the panel. Every returned line is therefore
     * guaranteed to be at most {@code maxWidth} pixels wide.
     */
    public static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            if (line.length() > 0 && font.width(line.toString() + ch) > maxWidth) {
                lines.add(line.toString().trim());
                line.setLength(0);
                if (Character.isWhitespace(cp)) {
                    continue; // drop the leading space after a break
                }
            }
            line.append(ch);
        }
        if (line.length() > 0) {
            lines.add(line.toString().trim());
        }
        return lines;
    }
}
