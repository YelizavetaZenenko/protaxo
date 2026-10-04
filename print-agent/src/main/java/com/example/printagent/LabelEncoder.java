package com.example.printagent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Turns the composed label canvas (see {@link LabelLayout#compose}) into a printer-language job
 * that carries the label size itself, so the Windows driver's page settings don't matter. Needed
 * because Java's print API only knows portrait pages and the Godex driver reports its own "2 x 4"
 * paper with 1-inch margins — a sideways label (81.5mm across the head, 47.5mm along the feed)
 * comes out cropped that way. The Godex G500 auto-detects its ZPL/EPL emulations, so both reach
 * it through the normal "Godex G500" queue as raw bytes. One canvas pixel is one 203dpi dot.
 */
final class LabelEncoder {

    private LabelEncoder() {
    }

    /**
     * Godex's native language. Needed because EZPL commands also start with {@code ^}, so a ZPL
     * job sent to a G500 without the emulation active is read as EZPL — {@code ^LL…} opens a
     * label ({@code ^L}) with nothing on it and a blank label comes out. Sizes in mm; the picture
     * goes as {@code Qx,y,bytesPerRow,rows} + binary rows (bit 1 = black, flip with label.invert
     * if it prints as a negative); darkness 1..15 maps onto {@code ^H} 0..19.
     */
    static byte[] ezpl(BufferedImage canvas, PrintAgentConfig config) {
        int h = canvas.getHeight();
        int bytesPerRow = (canvas.getWidth() + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "^Q" + mm(config.labelLengthMm()) + "," + mm(config.labelGapMm()) + "\r\n");
        writeAscii(out, "^W" + mm(config.labelWidthMm()) + "\r\n");
        if (config.darkness() > 0) {
            writeAscii(out, "^H" + Math.min(19, Math.round(config.darkness() * 19 / 15f)) + "\r\n");
        }
        writeAscii(out, "^P1\r\n");
        writeAscii(out, "^L\r\n");
        // Q's line ends with a bare CR: the data starts right after it, so an LF would be read as the first byte.
        writeAscii(out, "Q0,0," + bytesPerRow + "," + h + "\r");
        out.writeBytes(pack(canvas, config.invert(), true));
        writeAscii(out, "\r\nE\r\n");
        return out.toByteArray();
    }

    /** {@code ^GFA} hex graphic, bit 1 = black dot; darkness 1..15 maps onto ZPL's 0..30. */
    static byte[] zpl(BufferedImage canvas, PrintAgentConfig config) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        byte[] bits = pack(canvas, config.invert(), true);
        int bytesPerRow = (w + 7) / 8;
        StringBuilder hex = new StringBuilder(bits.length * 2);
        for (byte b : bits) {
            hex.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
            hex.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
        }
        StringBuilder job = new StringBuilder("^XA\n");
        if (config.darkness() > 0) {
            job.append(String.format("~SD%02d\n", Math.min(30, config.darkness() * 2)));
        }
        job.append("^MNY\n")
                .append("^PW").append(w).append('\n')
                .append("^LL").append(h).append('\n')
                .append("^LH0,0\n")
                .append("^FO0,0^GFA,").append(bits.length).append(',').append(bits.length).append(',')
                .append(bytesPerRow).append(',').append(hex).append("^FS\n")
                .append("^PQ1\n")
                .append("^XZ\n");
        return job.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** {@code GW} binary graphic, bit 0 = black dot. */
    static byte[] epl(BufferedImage canvas, PrintAgentConfig config) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        byte[] bits = pack(canvas, config.invert(), false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "\nN\n");
        if (config.darkness() > 0) {
            writeAscii(out, "D" + Math.min(15, config.darkness()) + "\n");
        }
        writeAscii(out, "q" + w + "\n");
        writeAscii(out, "Q" + h + "," + LabelLayout.dots(config.labelGapMm()) + "\n");
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
                    boolean black = x < w && LabelLayout.isBlack(image.getRGB(x, y)) != invert;
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

    /** Whole millimetres for ^Q/^W — the form the EZPL examples use. */
    private static String mm(double value) {
        return String.valueOf(Math.round(value));
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
