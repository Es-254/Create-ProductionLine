package com.create.productionline.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Width-aware, hard-breaking text wrapper for item tooltips.
 *
 * <p>Minecraft's own tooltip wrapping only breaks at spaces, so a long unbroken run
 * (a CJK sentence, a {@code modid:very_long_id}, or {@code #c:storage_blocks/steel})
 * is emitted as one over-wide line and overflows the screen. This wraps by estimated
 * pixel width instead: ASCII ≈ 6 px, CJK / full-width ≈ 9 px, breaking anywhere when
 * a single run is too long.
 */
public final class TextWrap {

    /** Vanilla-ish tooltip content width budget in pixels. */
    public static final int DEFAULT_MAX_WIDTH = 200;

    private TextWrap() {
    }

    public static List<String> wrap(String text) {
        return wrap(text, DEFAULT_MAX_WIDTH);
    }

    /** Like {@link #wrap(String)} but indents every continuation line. */
    public static List<String> wrapIndented(String text, String indent) {
        List<String> out = new ArrayList<>();
        List<String> parts = wrap(text);
        for (int i = 0; i < parts.size(); i++) {
            out.add(i == 0 ? parts.get(i) : indent + parts.get(i));
        }
        return out;
    }

    public static List<String> wrap(String text, int maxWidthPx) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int cw = charWidth(cp);
            if (width + cw > maxWidthPx && line.length() > 0) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
                width = 0;
                if (Character.isWhitespace(cp)) {
                    continue; // never start a continuation line with a space
                }
            }
            line.appendCodePoint(cp);
            width += cw;
        }
        if (line.length() > 0) {
            lines.add(line.toString().stripTrailing());
        }
        return lines;
    }

    /** Approximate rendered width of one code point (default font metrics). */
    public static int charWidth(int cp) {
        return isWide(cp) ? 9 : 6;
    }

    /** True for CJK / full-width ranges that occupy a full 9 px cell. */
    public static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)      // Hangul Jamo
                || (cp >= 0x2E80 && cp <= 0xA4CF)  // CJK radicals … Yi
                || (cp >= 0xAC00 && cp <= 0xD7A3)  // Hangul syllables
                || (cp >= 0xF900 && cp <= 0xFAFF)  // CJK compatibility ideographs
                || (cp >= 0xFE30 && cp <= 0xFE6F)  // CJK compatibility forms
                || (cp >= 0xFF00 && cp <= 0xFF60)  // full-width forms
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x20000 && cp <= 0x3FFFD); // CJK extensions
    }
}
