package com.example.protaxo.printagent;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Converts an arbitrary raster image into the raw 1-bit-per-pixel payload the TSPL {@code BITMAP}
 * command expects: rows of bytes, MSB-first, one bit per horizontal dot, packed left-to-right and
 * padded to a whole byte at the end of each row. A bit of {@code 1} means "print a black dot" —
 * the standard TSPL convention (TSC's own manual; unverified against the actual printer, like the
 * rest of the label layout, see [[Print Agent]]).
 */
public final class TsplBitmap {

    private TsplBitmap() {
    }

    public record Encoded(int widthBytes, int heightDots, byte[] data) {
    }

    /**
     * @param targetWidthDots must be a multiple of 8 — TSPL packs 8 dots per byte, and keeping the
     *                        width byte-aligned avoids partial trailing bits in the last byte of
     *                        each row.
     */
    public static Encoded fromImage(BufferedImage source, int targetWidthDots) {
        if (targetWidthDots % 8 != 0) {
            throw new IllegalArgumentException("targetWidthDots must be a multiple of 8, got " + targetWidthDots);
        }
        int targetHeightDots = Math.round(targetWidthDots * ((float) source.getHeight() / source.getWidth()));

        BufferedImage scaled = new BufferedImage(targetWidthDots, targetHeightDots, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, targetWidthDots, targetHeightDots, null);
        g.dispose();

        int widthBytes = targetWidthDots / 8;
        byte[] data = new byte[widthBytes * targetHeightDots];
        for (int y = 0; y < targetHeightDots; y++) {
            for (int x = 0; x < targetWidthDots; x++) {
                if (isDark(scaled.getRGB(x, y))) {
                    int byteIndex = y * widthBytes + (x / 8);
                    int bitIndex = 7 - (x % 8);
                    data[byteIndex] |= (byte) (1 << bitIndex);
                }
            }
        }
        return new Encoded(widthBytes, targetHeightDots, data);
    }

    /** Transparent/near-white pixels are background (not printed); everything else is a dot. */
    private static boolean isDark(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 128) {
            return false;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        return luminance < 150;
    }
}
