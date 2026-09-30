package com.example.printagent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Turns the (already rotated) 1-bit label picture into a printer-language job that carries the
 * label size itself, so the Windows driver's page settings no longer matter: Java's print API
 * normalises every page to portrait (width &le; height), which a roll with sideways labels
 * (81.5mm across the head, 47.5mm along the feed) can't be printed through — found on the real
 * Godex G500, 2026-09-30. The G500 auto-detects its emulations, so both of these reach it through
 * the normal "Godex G500" queue as raw bytes:
 *
 * <ul>
 *   <li><b>ZPL</b> (default) — {@code ^GFA} hex graphic, bit 1 = black dot.</li>
 *   <li><b>EPL</b> (fallback) — {@code GW} binary graphic, bit 0 = black dot.</li>
 * </ul>
 *
 * Image width becomes the label width across the head, image height the label length along the
 * feed; one pixel is one 203dpi dot.
 */
final class LabelEncoder {

    /** Gap between labels on the roll, only EPL's {@code Q} command needs it (3mm @ 8 dots/mm). */
    private static final int EPL_GAP_DOTS = 24;

    private LabelEncoder() {
    }

    static byte[] zpl(BufferedImage image, boolean invert) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] bits = pack(image, invert, true);
        int bytesPerRow = (w + 7) / 8;
        StringBuilder hex = new StringBuilder(bits.length * 2);
        for (byte b : bits) {
            hex.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
            hex.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
        }
        String job = "^XA\n"
                + "^PW" + w + "\n"
                + "^LL" + h + "\n"
                + "^LH0,0\n"
                + "^FO0,0^GFA," + bits.length + "," + bits.length + "," + bytesPerRow + "," + hex + "^FS\n"
                + "^PQ1\n"
                + "^XZ\n";
        return job.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] epl(BufferedImage image, boolean invert) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] bits = pack(image, invert, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "\nN\n");
        writeAscii(out, "q" + w + "\n");
        writeAscii(out, "Q" + h + "," + EPL_GAP_DOTS + "\n");
        writeAscii(out, "GW0,0," + ((w + 7) / 8) + "," + h + ",");
        out.writeBytes(bits);
        writeAscii(out, "\nP1\n");
        return out.toByteArray();
    }

    /** Row-major, MSB first, rows padded to whole bytes; {@code blackIsOne} picks the language's polarity. */
    private static byte[] pack(BufferedImage image, boolean invert, boolean blackIsOne) {
        int w = image.getWidth();
        int h = image.getHeight();
        int bytesPerRow = (w + 7) / 8;
        byte[] out = new byte[bytesPerRow * h];
        for (int y = 0; y < h; y++) {
            for (int bx = 0; bx < bytesPerRow; bx++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = bx * 8 + bit;
                    boolean black = x < w && isBlack(image.getRGB(x, y)) != invert;
                    // Padding past the right edge is always "no dot", whatever the polarity.
                    boolean one = x < w ? black == blackIsOne : !blackIsOne;
                    if (one) {
                        value |= 0x80 >> bit;
                    }
                }
                out[y * bytesPerRow + bx] = (byte) value;
            }
        }
        return out;
    }

    private static boolean isBlack(int rgb) {
        int luminance = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return luminance < 128;
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
