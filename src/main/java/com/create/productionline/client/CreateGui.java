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

    /** Splits a line so it fits the given pixel width (word- and char-aware). */
    public static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split("(?<=\\s)|(?=\\s)");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String probe = line.length() == 0 ? word : line + word;
            if (font.width(probe) <= maxWidth || line.length() == 0) {
                line.append(word);
            } else {
                lines.add(line.toString().trim());
                line.setLength(0);
                if (font.width(word) > maxWidth) {
                    String rest = word;
                    while (font.width(rest) > maxWidth) {
                        int cut = 1;
                        while (cut < rest.length() && font.width(rest.substring(0, cut + 1)) <= maxWidth) {
                            cut++;
                        }
                        lines.add(rest.substring(0, cut));
                        rest = rest.substring(cut);
                    }
                    line.append(rest);
                } else {
                    line.append(word);
                }
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString().trim());
        }
        return lines;
    }
}
