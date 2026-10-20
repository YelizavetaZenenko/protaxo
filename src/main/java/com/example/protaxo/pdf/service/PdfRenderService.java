package com.example.protaxo.pdf.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
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

    private static final double WATERMARK_OPACITY = 0.16;
    private static final double WATERMARK_ANGLE = Math.toRadians(-30);
    /** Logo width inside the watermark tile and empty space around it, in tile pixels. */
    private static final int WATERMARK_LOGO_WIDTH = 300;
    private static final int WATERMARK_GAP = 90;

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
     * openhtmltopdf is given no base URI ({@code withHtmlContent(html, null)}), so a template's
     * {@code <img src="...">} can't resolve a relative path or classpath URL on its own — the
     * simplest fix, given only one small logo image is embedded so far, is inlining it as a
     * base64 data URI the caller passes into the template context, no PdfRendererBuilder resource
     * resolver needed.
     */
    /**
     * Watermark tile for a full-page background: the classpath image turned diagonal (-30°) and
     * faded, as a transparent PNG — alpha comes from each pixel's darkness, so the logo's white
     * background vanishes and nothing on the page gets covered. Built once and cached.
     */
    public String watermarkTileDataUri(String resourcePath) {
        return watermarkCache.computeIfAbsent(resourcePath, this::buildWatermarkTile);
    }

    private String buildWatermarkTile(String resourcePath) {
        try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Image resource not found: " + resourcePath);
            }
            BufferedImage source = ImageIO.read(stream);
            int logoHeight = source.getHeight() * WATERMARK_LOGO_WIDTH / source.getWidth();

            BufferedImage faded = new BufferedImage(WATERMARK_LOGO_WIDTH, logoHeight, BufferedImage.TYPE_INT_ARGB);
            Image scaled = source.getScaledInstance(WATERMARK_LOGO_WIDTH, logoHeight, Image.SCALE_SMOOTH);
            BufferedImage scaledRgb = new BufferedImage(WATERMARK_LOGO_WIDTH, logoHeight, BufferedImage.TYPE_INT_RGB);
            scaledRgb.getGraphics().drawImage(scaled, 0, 0, null);
            for (int y = 0; y < logoHeight; y++) {
                for (int x = 0; x < WATERMARK_LOGO_WIDTH; x++) {
                    int rgb = scaledRgb.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    double darkness = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255;
                    int alpha = (int) Math.round(255 * WATERMARK_OPACITY * Math.min(1, darkness * 1.4));
                    faded.setRGB(x, y, (alpha << 24) | (rgb & 0xffffff));
                }
            }

            int tileSize = (int) Math.ceil(Math.hypot(WATERMARK_LOGO_WIDTH, logoHeight)) + WATERMARK_GAP;
            BufferedImage tile = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tile.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.rotate(WATERMARK_ANGLE, tileSize / 2.0, tileSize / 2.0);
            g.drawImage(faded, (tileSize - WATERMARK_LOGO_WIDTH) / 2, (tileSize - logoHeight) / 2, null);
            g.dispose();

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(tile, "png", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build watermark from " + resourcePath, e);
        }
    }

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
