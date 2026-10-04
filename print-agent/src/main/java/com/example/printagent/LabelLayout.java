package com.example.printagent;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Turns the label picture from the backend into exactly what lands on the roll: turned by the
 * configured rotation, centred on a canvas the physical size of one label (x across the head,
 * y along the feed, row 0 printed first) and nudged by the configured offset. Every print mode and
 * the settings window's preview use this one canvas, so the preview shows what gets printed.
 */
final class LabelLayout {

    static final double DOTS_PER_MM = 8.0;

    private LabelLayout() {
    }

    static int dots(double mm) {
        return (int) Math.round(mm * DOTS_PER_MM);
    }

    static BufferedImage compose(BufferedImage label, PrintAgentConfig config) {
        BufferedImage turned = scale(rotate(label, config.labelRotation()), config.scalePercent());
        int w = Math.max(1, dots(config.labelWidthMm()));
        int h = Math.max(1, dots(config.labelLengthMm()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            int x = (w - turned.getWidth()) / 2 + dots(config.offsetXmm());
            int y = (h - turned.getHeight()) / 2 + dots(config.offsetYmm());
            g.drawImage(turned, x, y, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Size in mm of the picture after rotation and scaling — compared with the label to warn about cropping. */
    static double[] turnedSizeMm(BufferedImage label, PrintAgentConfig config) {
        boolean quarter = config.labelRotation() == 90 || config.labelRotation() == 270;
        int w = quarter ? label.getHeight() : label.getWidth();
        int h = quarter ? label.getWidth() : label.getHeight();
        double factor = config.scalePercent() / 100.0;
        return new double[] {scaled(w, factor) / DOTS_PER_MM, scaled(h, factor) / DOTS_PER_MM};
    }

    /**
     * Shrinks (or enlarges) the picture for a label whose edges the printer crops. Smooth
     * resampling then back to pure black and white: nearest-neighbour would drop whole dot rows
     * and break the 1-2 dot strokes of the small text.
     */
    static BufferedImage scale(BufferedImage src, double percent) {
        if (Math.abs(percent - 100) < 0.01) {
            return src;
        }
        double factor = percent / 100.0;
        int w = scaled(src.getWidth(), factor);
        int h = scaled(src.getHeight(), factor);
        BufferedImage smooth = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = smooth.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, isBlack(smooth.getRGB(x, y)) ? 0x000000 : 0xFFFFFF);
            }
        }
        return out;
    }

    private static int scaled(int pixels, double factor) {
        return Math.max(1, (int) Math.round(pixels * factor));
    }

    /**
     * Clockwise turn in whole quarter-turns, pixel for pixel (no resampling) — for a roll where the
     * labels sit sideways relative to the feed.
     */
    static BufferedImage rotate(BufferedImage src, int degrees) {
        if (degrees == 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean quarter = degrees == 90 || degrees == 270;
        BufferedImage out = new BufferedImage(quarter ? h : w, quarter ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                switch (degrees) {
                    case 90 -> out.setRGB(h - 1 - y, x, rgb);
                    case 180 -> out.setRGB(w - 1 - x, h - 1 - y, rgb);
                    default -> out.setRGB(y, w - 1 - x, rgb);
                }
            }
        }
        return out;
    }

    static boolean isBlack(int rgb) {
        int luminance = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return luminance < 128;
    }

    /**
     * A test picture for lining the print up with the label: a frame 1mm inside the label edge,
     * centre cross and a "ВЕРХ" arrow. Drawn in the label's own (unrotated) orientation, the same
     * way the backend draws the real label, so the rotation setting applies to it identically.
     */
    static BufferedImage calibrationPattern(PrintAgentConfig config) {
        boolean quarter = config.labelRotation() == 90 || config.labelRotation() == 270;
        double wMm = quarter ? config.labelLengthMm() : config.labelWidthMm();
        double hMm = quarter ? config.labelWidthMm() : config.labelLengthMm();
        int w = Math.max(16, dots(wMm));
        int h = Math.max(16, dots(hMm));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(Color.BLACK);
            int inset = dots(1);
            g.setStroke(new BasicStroke(3));
            g.drawRect(inset, inset, w - 2 * inset - 1, h - 2 * inset - 1);
            g.setStroke(new BasicStroke(1));
            g.drawLine(w / 2, h / 2 - dots(5), w / 2, h / 2 + dots(5));
            g.drawLine(w / 2 - dots(5), h / 2, w / 2 + dots(5), h / 2);

            int arrowTop = inset + dots(3);
            int[] xs = {w / 2, w / 2 - dots(4), w / 2 + dots(4)};
            int[] ys = {arrowTop, arrowTop + dots(5), arrowTop + dots(5)};
            g.fillPolygon(xs, ys, 3);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            FontMetrics fm = g.getFontMetrics();
            String up = "ВЕРХ";
            g.drawString(up, (w - fm.stringWidth(up)) / 2, arrowTop + dots(5) + fm.getAscent() + 4);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            fm = g.getFontMetrics();
            String size = String.format("%.1f × %.1f мм", wMm, hMm);
            g.drawString(size, (w - fm.stringWidth(size)) / 2, h / 2 + dots(5) + fm.getAscent() + 6);
            String corner = "ліво-низ";
            g.drawString(corner, inset + dots(2), h - inset - dots(2));
        } finally {
            g.dispose();
        }
        return out;
    }
}
