package com.example.protaxo.printagent;

import com.example.protaxo.calibration.dto.CalibrationLabelType;
import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Draws the calibration protocol seal/QR label as a finished 1-bit PNG — the Print Agent prints it
 * through the printer's own Windows driver like any picture, so the label no longer depends on a
 * printer command language. Replaced the TSPL builder (2026-09-30): the real printer turned out to
 * be a Godex G500 (EZPL), not a TSC, and drawing it ourselves also sidesteps whether the printer's
 * built-in fonts have Cyrillic at all — the text is rendered here with the bundled DejaVu fonts.
 *
 * <p>Canvas is 47.5x81.5mm at 8 dots/mm (the G500's 203dpi) = 380x652 px, so every pixel maps to
 * exactly one printer dot when the agent prints it at physical size — no resampling blur.
 */
@Component
public class LabelImageRenderer {

    private static final int DOTS_PER_MM = 8;
    public static final double WIDTH_MM = 47.5;
    public static final double HEIGHT_MM = 81.5;
    private static final int WIDTH = (int) Math.round(WIDTH_MM * DOTS_PER_MM);
    private static final int HEIGHT = (int) Math.round(HEIGHT_MM * DOTS_PER_MM);
    private static final int MARGIN = 12;
    private static final int LOGO_WIDTH = 200;
    private static final int MAX_QR_MODULE = 4;
    private static final int SMART_LOGO_WIDTH = 150;
    private static final int SMART_VALUE_X = MARGIN + 150;
    private static final int SMART_MAX_ROW_HEIGHT = 30;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String FOP_NAME = "ФОП Вишивата Діана Олександрівна";
    private static final String COMPANY_ADDRESS_LINE_1 = "45604, Волинська обл., Луцький р-н,";
    private static final String COMPANY_ADDRESS_LINE_2 = "с. Крупа, вул. Дубнівська, 10";
    private static final String COMPANY_PHONE = "+38(067)-223-22-63";

    private final Font regular = loadFont("/fonts/DejaVuSans.ttf");
    private final Font bold = loadFont("/fonts/DejaVuSans-Bold.ttf");
    private final BufferedImage logo = loadLogo();

    /**
     * Deliberately NOT {@code app.base-url} — a real outside scanner (an inspector, a carrier) has
     * no Tailscale membership and can't resolve the Tailscale-only hostname {@code app.base-url}
     * points at. This property points at the Tailscale Funnel address instead — a different port,
     * exposed to the public internet by {@code tailscale funnel}, forwarding only {@code /verify/*}
     * to this app (see docs/Print Agent.md "Публічний доступ через Tailscale Funnel" and the
     * Caddyfile). Falls back to app.base-url when unset (local dev, where the distinction doesn't
     * matter).
     */
    @Value("${app.public-verify-base-url}")
    private String publicVerifyBaseUrl;

    /**
     * What the QR actually encodes — a link to {@link com.example.protaxo.calibration.web.PublicVerificationController},
     * not the bare hash, so a phone scan opens the protocol's PDF directly with no login required.
     */
    public String verifyUrl(CalibrationProtocolResponse protocol) {
        return publicVerifyBaseUrl + "/verify/" + protocol.qrHash() + "/pdf";
    }

    /** The exact bytes sent to the Print Agent. */
    public byte[] renderPng(CalibrationProtocolResponse protocol, String clientEdrpou) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(render(protocol, clientEdrpou), "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалось зберегти наклейку як PNG", e);
        }
        return out.toByteArray();
    }

    /** Same PNG the printer gets, for the preview page — one source of truth, no separate mock. */
    public String renderPngDataUri(CalibrationProtocolResponse protocol, String clientEdrpou) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(renderPng(protocol, clientEdrpou));
    }

    /**
     * The service on the linked наряд-заказ picks the layout: Smart 1/Smart 2 get the
     * smart-tachograph sticker (no QR), everything else the original one with the QR code.
     */
    BufferedImage render(CalibrationProtocolResponse protocol, String clientEdrpou) {
        return protocol.labelType() != null && protocol.labelType().isSmart()
                ? renderSmart(protocol)
                : renderStandard(protocol, clientEdrpou);
    }

    /**
     * Top to bottom: logo → company name, address (two lines — ~64 chars won't fit one), phone, all
     * centered → stamp number, large → Date, VIN, S/N (the client's EDRPOU/RNOKPP, so the seal traces
     * back to the carrier without a scan), Wheels, L, W, k in bold → QR filling the space left.
     */
    private BufferedImage renderStandard(CalibrationProtocolResponse protocol, String clientEdrpou) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.BLACK);
            // No antialiasing on text: a thermal head prints dots, not greys — crisp 1-bit glyphs
            // read better than antialiased ones that get thresholded afterwards.
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            int logoHeight = logo.getHeight() * LOGO_WIDTH / logo.getWidth();
            int y = 8;
            g.drawImage(logo, (WIDTH - LOGO_WIDTH) / 2, y, LOGO_WIDTH, logoHeight, null);
            y += logoHeight + 6;

            y = centered(g, regular.deriveFont(17f), FOP_NAME, y);
            y = centered(g, regular.deriveFont(17f), COMPANY_ADDRESS_LINE_1, y);
            y = centered(g, regular.deriveFont(17f), COMPANY_ADDRESS_LINE_2, y);
            y = centered(g, regular.deriveFont(17f), "Tel: " + COMPANY_PHONE, y);
            y += 4;
            y = centered(g, bold.deriveFont(38f), orDash(protocol.stampNumber()), y);
            y += 4;

            Font details = bold.deriveFont(21f);
            String date = protocol.protocolDate() == null ? null : DATE_FORMAT.format(protocol.protocolDate());
            y = left(g, details, "Date: " + orDash(date), y);
            y = left(g, details, "VIN: " + orDash(protocol.vehicleVin()), y);
            y = left(g, details, "S/N: " + orDash(clientEdrpou), y);
            y = left(g, details, "Wheels: " + orDash(protocol.tireSize()), y);
            y = left(g, details, "L=" + orDash(protocol.tireCircumferenceL()), y);
            y = left(g, details, "W=" + orDash(protocol.coefficientW()), y);
            y = left(g, details, "k=" + orDash(protocol.constantK()), y);

            drawQr(g, verifyUrl(protocol), y + 8);
        } finally {
            g.dispose();
        }
        return toMonochrome(canvas);
    }

    /**
     * Smart-tachograph sticker after the workshop's sample (photos 2026-10-06): framed, smaller logo,
     * company block and stamp number, then label / value / unit rows. Smart 2 differs only by the
     * "Load type" row. No QR — thirteen rows leave no room for it. Empty values stay blank, as on
     * the sample, rather than "-".
     */
    private BufferedImage renderSmart(CalibrationProtocolResponse protocol) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.BLACK);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(5, 5, WIDTH - 11, HEIGHT - 11, 18, 18);

            int logoHeight = logo.getHeight() * SMART_LOGO_WIDTH / logo.getWidth();
            int y = 12;
            g.drawImage(logo, (WIDTH - SMART_LOGO_WIDTH) / 2, y, SMART_LOGO_WIDTH, logoHeight, null);
            y += logoHeight + 2;

            Font company = regular.deriveFont(16f);
            y = centered(g, company, FOP_NAME, y);
            y = centered(g, company, COMPANY_ADDRESS_LINE_1, y);
            y = centered(g, company, COMPANY_ADDRESS_LINE_2, y);
            y = centered(g, company, "Tel: " + COMPANY_PHONE, y);
            y = centered(g, bold.deriveFont(34f), orDash(protocol.stampNumber()), y);
            y += 4;

            List<String[]> rows = smartRows(protocol);
            int rowHeight = Math.min(SMART_MAX_ROW_HEIGHT, (HEIGHT - 16 - y) / rows.size());
            Font label = regular.deriveFont(19f);
            Font value = bold.deriveFont(19f);
            Font unit = regular.deriveFont(16f);
            for (String[] row : rows) {
                FontMetrics fm = g.getFontMetrics(label);
                int baseline = y + (rowHeight + fm.getAscent() - fm.getDescent()) / 2;
                g.setFont(shrinkToFit(g, label, row[0], SMART_VALUE_X - MARGIN - 4));
                g.drawString(row[0], MARGIN, baseline);
                int unitWidth = row[2].isEmpty() ? 0 : g.getFontMetrics(unit).stringWidth(row[2]) + 6;
                g.setFont(shrinkToFit(g, value, row[1], WIDTH - MARGIN - SMART_VALUE_X - unitWidth));
                g.drawString(row[1], SMART_VALUE_X, baseline);
                if (!row[2].isEmpty()) {
                    g.setFont(unit);
                    g.drawString(row[2], WIDTH - MARGIN - g.getFontMetrics(unit).stringWidth(row[2]), baseline);
                }
                y += rowHeight;
            }
        } finally {
            g.dispose();
        }
        return toMonochrome(canvas);
    }

    /** {label, value, unit} — order and wording as on the sample sticker. */
    private static List<String[]> smartRows(CalibrationProtocolResponse protocol) {
        String date = protocol.protocolDate() == null ? null : DATE_FORMAT.format(protocol.protocolDate());
        List<String[]> rows = new ArrayList<>();
        rows.add(row("Date of calibration:", date, ""));
        rows.add(row("VIN:", protocol.vehicleVin(), ""));
        rows.add(row("VU Serial No:", protocol.tachographSerialNumber(), ""));
        if (protocol.labelType() == CalibrationLabelType.SMART_2) {
            rows.add(row("Load type:", protocol.loadType(), ""));
        }
        rows.add(row("Tyre size:", protocol.tireSize(), ""));
        rows.add(row("L=", protocol.tireCircumferenceL(), "mm"));
        rows.add(row("k=", protocol.constantK(), "imp/km"));
        rows.add(row("W=", protocol.coefficientW(), "imp/km"));
        rows.add(row("V(max):", protocol.speedLimiterValue(), "km/h"));
        rows.add(row("Ext. GNSS:", protocol.extGnss(), ""));
        rows.add(row("GNSS S/N=", protocol.gnssSerialNumber(), ""));
        rows.add(row("DSRC S/N=", protocol.dsrcSerialNumber(), ""));
        rows.add(row("Seal S/Ns=", protocol.sealNumbers(), ""));
        return rows;
    }

    private static String[] row(String label, String value, String unit) {
        return new String[] {label, value == null ? "" : value.strip(), unit};
    }

    private int centered(Graphics2D g, Font font, String text, int top) {
        Font fitted = shrinkToFit(g, font, text, WIDTH - 2 * MARGIN);
        FontMetrics fm = g.getFontMetrics(fitted);
        g.setFont(fitted);
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, top + fm.getAscent());
        return top + fm.getHeight();
    }

    private int left(Graphics2D g, Font font, String text, int top) {
        Font fitted = shrinkToFit(g, font, text, WIDTH - 2 * MARGIN);
        FontMetrics fm = g.getFontMetrics(fitted);
        g.setFont(fitted);
        g.drawString(text, MARGIN, top + fm.getAscent());
        return top + fm.getHeight();
    }

    /** A long VIN or tyre size shrinks its own line instead of running off the label edge. */
    private static Font shrinkToFit(Graphics2D g, Font font, String text, int maxWidth) {
        Font f = font;
        while (g.getFontMetrics(f).stringWidth(text) > maxWidth && f.getSize2D() > 8f) {
            f = f.deriveFont(f.getSize2D() - 1f);
        }
        return f;
    }

    /** Whole printer dots per QR module (no fractional scaling), as large as the space below allows. */
    private void drawQr(Graphics2D g, String content, int top) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H, EncodeHintType.MARGIN, 0));
        } catch (WriterException e) {
            throw new IllegalStateException("Не вдалось згенерувати QR-код наклейки", e);
        }
        int modules = matrix.getWidth();
        int available = Math.min(HEIGHT - top - MARGIN, WIDTH - 2 * MARGIN);
        int module = Math.max(1, Math.min(MAX_QR_MODULE, available / modules));
        int size = modules * module;
        int x0 = (WIDTH - size) / 2;
        int y0 = top + Math.max(0, (HEIGHT - MARGIN - top - size) / 2);
        for (int x = 0; x < modules; x++) {
            for (int y = 0; y < modules; y++) {
                if (matrix.get(x, y)) {
                    g.fillRect(x0 + x * module, y0 + y * module, module, module);
                }
            }
        }
    }

    /** Hard threshold to pure black/white — the logo's greys would otherwise be left to the driver's dithering. */
    private static BufferedImage toMonochrome(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);
                int luminance = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                out.setRGB(x, y, luminance < 160 ? 0x000000 : 0xFFFFFF);
            }
        }
        return out;
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static Font loadFont(String resource) {
        try (InputStream stream = LabelImageRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Font resource not found: " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Failed to load label font " + resource, e);
        }
    }

    /** White-backed: a transparent PNG logo would otherwise come out black on the RGB canvas. */
    private static BufferedImage loadLogo() {
        try (InputStream stream = LabelImageRenderer.class.getResourceAsStream("/images/logo.png")) {
            if (stream == null) {
                throw new IllegalStateException("Logo resource not found: images/logo.png");
            }
            BufferedImage source = ImageIO.read(stream);
            BufferedImage flattened = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = flattened.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
            g.dispose();
            return flattened;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load label logo image", e);
        }
    }
}
