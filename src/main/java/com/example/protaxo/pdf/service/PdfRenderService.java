package com.example.protaxo.pdf.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class PdfRenderService {

    private static final String FONT_FAMILY = "DejaVu Sans";

    /**
     * "Times New Roman" is a Microsoft-licensed font that can't be freely bundled/redistributed
     * off Windows, and the target server OS isn't decided yet (see "Варіанти розгортання
     * (сервер)"). Liberation Serif is metric-compatible and visually near-identical (the same
     * substitute LibreOffice uses) and is SIL Open Font License — safe to ship — so it's
     * registered under the "Times New Roman" family name templates ask for in CSS.
     */
    private static final String TIMES_FONT_FAMILY = "Times New Roman";

    private static final double WATERMARK_OPACITY = 0.06;
    private static final double WATERMARK_ANGLE = Math.toRadians(-30);
    /** Watermark covers the whole A4 sheet, margins included. */
    private static final double WATERMARK_PAGE_WIDTH_CM = 21;
    private static final double WATERMARK_PAGE_HEIGHT_CM = 29.7;
    private static final double WATERMARK_LOGO_WIDTH_CM = 4;
    /** Logos per full row; shifted rows carry one less. */
    private static final int WATERMARK_COLUMNS = 4;
    private static final int WATERMARK_ROWS = 6;
    private static final int WATERMARK_DPI = 150;

    private final TemplateEngine pdfTemplateEngine;
    private final Map<String, String> watermarkCache = new ConcurrentHashMap<>();

    public byte[] render(String templateName, Context context) {
        String html = pdfTemplateEngine.process(templateName, context);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.useFont(() -> fontStream("DejaVuSans.ttf"), FONT_FAMILY, 400,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("DejaVuSans-Bold.ttf"), FONT_FAMILY, 700,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("LiberationSerif-Regular.ttf"), TIMES_FONT_FAMILY, 400,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("LiberationSerif-Bold.ttf"), TIMES_FONT_FAMILY, 700,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PDF for template " + templateName, e);
        }
        return out.toByteArray();
    }

    private InputStream fontStream(String fileName) {
        InputStream stream = getClass().getResourceAsStream("/fonts/" + fileName);
        if (stream == null) {
            throw new IllegalStateException("Font resource not found: " + fileName);
        }
        return stream;
    }

    /**
     * Full-page watermark for the protocol: the classpath image faded and turned diagonal (-30°),
     * laid out in a staggered (brick) pattern — rows of 3 and 2 logos alternate, each odd row
     * shifted by half a column, so there is no visible grid and no logo is cut at the page edge.
     * One transparent PNG the size of the page content area (alpha comes from each pixel's
     * darkness, so the logo's white background vanishes and nothing on the page gets covered).
     * Built once and cached.
     */
    public String watermarkPageDataUri(String resourcePath) {
        return watermarkCache.computeIfAbsent(resourcePath, this::buildWatermarkPage);
    }

    private String buildWatermarkPage(String resourcePath) {
        try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Image resource not found: " + resourcePath);
            }
            BufferedImage source = ImageIO.read(stream);
            int logoWidth = cmToPx(WATERMARK_LOGO_WIDTH_CM);
            int logoHeight = source.getHeight() * logoWidth / source.getWidth();
            BufferedImage faded = fadedCopy(source, logoWidth, logoHeight);

            BufferedImage page = new BufferedImage(cmToPx(WATERMARK_PAGE_WIDTH_CM), cmToPx(WATERMARK_PAGE_HEIGHT_CM),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = page.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double columnWidth = WATERMARK_PAGE_WIDTH_CM / WATERMARK_COLUMNS;
            double rowHeight = WATERMARK_PAGE_HEIGHT_CM / WATERMARK_ROWS;
            for (int row = 0; row < WATERMARK_ROWS; row++) {
                boolean shifted = row % 2 == 1;
                int logosInRow = shifted ? WATERMARK_COLUMNS - 1 : WATERMARK_COLUMNS;
                for (int col = 0; col < logosInRow; col++) {
                    double centerX = cmToPx(columnWidth * (col + (shifted ? 1 : 0.5)));
                    double centerY = cmToPx(rowHeight * (row + 0.5));
                    AffineTransform saved = g.getTransform();
                    g.rotate(WATERMARK_ANGLE, centerX, centerY);
                    g.drawImage(faded, (int) Math.round(centerX - logoWidth / 2.0), (int) Math.round(centerY - logoHeight / 2.0), null);
                    g.setTransform(saved);
                }
            }
            g.dispose();

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(page, "png", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build watermark from " + resourcePath, e);
        }
    }

    /** Scaled copy of {@code source} whose alpha is its darkness × {@link #WATERMARK_OPACITY}. */
    private static BufferedImage fadedCopy(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        scaled.getGraphics().drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        BufferedImage faded = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                double darkness = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255;
                int alpha = (int) Math.round(255 * WATERMARK_OPACITY * Math.min(1, darkness * 1.4));
                faded.setRGB(x, y, (alpha << 24) | (rgb & 0xffffff));
            }
        }
        return faded;
    }

    private static int cmToPx(double cm) {
        return (int) Math.round(cm * WATERMARK_DPI / 2.54);
    }

    /**
     * openhtmltopdf is given no base URI ({@code withHtmlContent(html, null)}), so a template's
     * {@code <img src="...">} can't resolve a relative path or classpath URL on its own — the
     * simplest fix, given only one small logo image is embedded so far, is inlining it as a
     * base64 data URI the caller passes into the template context, no PdfRendererBuilder resource
     * resolver needed.
     */
    public String classpathImageDataUri(String resourcePath, String mimeType) {
        try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Image resource not found: " + resourcePath);
            }
            byte[] bytes = stream.readAllBytes();
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read image resource " + resourcePath, e);
        }
    }
}
