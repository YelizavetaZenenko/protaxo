package com.example.protaxo.printagent;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the raw TSPL2 command block for a calibration protocol seal/QR label. Label size
 * (50x30mm) and text positions are a starting point picked without the actual printer in hand —
 * see [[Print Agent]] for why these will very likely need adjusting once tested against real
 * hardware (label stock size, font, and especially Cyrillic codepage are printer/firmware-specific).
 *
 * <p>{@link #build} returns raw bytes, not a {@code String} — the label embeds the ProTaxo logo as
 * a TSPL {@code BITMAP} command, whose payload is arbitrary binary pixel data that cannot survive
 * a text-based transport (WebSocket TEXT frames are UTF-8; re-encoding arbitrary bytes through a
 * String would corrupt them). The Cyrillic {@code TEXT} portions are therefore also encoded to
 * Windows-1251 <em>here</em>, on the backend, and the whole thing travels to the Print Agent as one
 * binary WebSocket frame — see {@link PrintAgentSessionRegistry#broadcast(byte[])}. This moves the
 * charset decision that used to live in the agent's {@code PrinterClient} onto the backend instead.
 */
@Component
public class TsplLabelBuilder {

    private static final Charset LABEL_CHARSET = Charset.forName("windows-1251");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int LOGO_WIDTH_DOTS = 64;
    private static final String SHORT_COMPANY_ADDRESS = "м.Звягель, пров.Богуна 19-Б/2";

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final TsplBitmap.Encoded logo = loadLogo();

    /**
     * What the QR actually encodes — a link to {@link com.example.protaxo.calibration.web.PublicVerificationController},
     * not the bare hash, so a phone scan opens the protocol's PDF directly with no login required.
     * Exposed (not private) so the label preview page can encode/display the exact same value the
     * printer will — one source of truth, never two copies that could silently drift apart.
     */
    public String verifyUrl(CalibrationProtocolResponse protocol) {
        return appBaseUrl + "/verify/" + protocol.qrHash() + "/pdf";
    }

    public byte[] build(CalibrationProtocolResponse protocol, String clientName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLabel(out, header());
        writeLabel(out, "BITMAP 10,10," + logo.widthBytes() + "," + logo.heightDots() + ",0,");
        out.writeBytes(logo.data());
        writeLabel(out, "\r\n");
        writeLabel(out, textCommands(protocol, clientName));
        writeLabel(out, footer(protocol));
        return out.toByteArray();
    }

    /**
     * A human-readable stand-in for {@link #build} used only by the HTML label preview page —
     * the real payload is binary (embeds the raw logo bitmap) and can't be dumped as text, so the
     * bitmap line shows a placeholder byte count instead of the actual pixel bytes.
     */
    public String buildPreviewText(CalibrationProtocolResponse protocol, String clientName) {
        return header()
                + "BITMAP 10,10," + logo.widthBytes() + "," + logo.heightDots() + ",0,<логотип, " + logo.data().length + " байт бітмапи>\r\n"
                + textCommands(protocol, clientName)
                + footer(protocol);
    }

    private String header() {
        return "SIZE 50 mm, 30 mm\r\nGAP 2 mm, 0 mm\r\nDIRECTION 1\r\nCODEPAGE 1251\r\nCLS\r\n";
    }

    private String footer(CalibrationProtocolResponse protocol) {
        return "QRCODE 260,15,H,3,A,0,\"" + verifyUrl(protocol) + "\"\r\nPRINT 1,1\r\n";
    }

    private String textCommands(CalibrationProtocolResponse protocol, String clientName) {
        String date = protocol.protocolDate() == null ? null : DATE_FORMAT.format(protocol.protocolDate());
        StringBuilder sb = new StringBuilder();
        sb.append("TEXT 90,15,\"3\",0,1,1,\"").append(escape(protocol.protocolNumber())).append("\"\r\n");
        sb.append("TEXT 10,85,\"2\",0,1,1,\"").append(escape(truncate(clientName, 24))).append("\"\r\n");
        sb.append("TEXT 10,110,\"1\",0,1,1,\"").append(escape(field("Штамп", protocol.stampNumber())))
                .append("   ").append(escape(field("Дата", date))).append("\"\r\n");
        sb.append("TEXT 10,125,\"1\",0,1,1,\"").append(escape(field("VIN", truncate(protocol.vehicleVin(), 22)))).append("\"\r\n");
        sb.append("TEXT 10,140,\"1\",0,1,1,\"").append(escape(field("Шини", truncate(protocol.tireSize(), 16))))
                .append("  L=").append(escape(orDash(protocol.tireCircumferenceL()))).append("\"\r\n");
        sb.append("TEXT 10,155,\"1\",0,1,1,\"W=").append(escape(orDash(protocol.coefficientW())))
                .append("  K=").append(escape(orDash(protocol.constantK())))
                .append("  V=").append(escape(orDash(protocol.speedLimiterValue()))).append("\"\r\n");
        sb.append("TEXT 10,170,\"1\",0,1,1,\"").append(escape(field("Пломби", truncate(protocol.sealNumbers(), 22)))).append("\"\r\n");
        sb.append("TEXT 10,185,\"1\",0,1,1,\"").append(escape(SHORT_COMPANY_ADDRESS)).append("\"\r\n");
        return sb.toString();
    }

    private static void writeLabel(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(LABEL_CHARSET));
    }

    private static String field(String label, String value) {
        return label + ": " + orDash(value);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }

    private static TsplBitmap.Encoded loadLogo() {
        try (InputStream stream = TsplLabelBuilder.class.getResourceAsStream("/images/logo.png")) {
            if (stream == null) {
                throw new IllegalStateException("Logo resource not found: images/logo.png");
            }
            BufferedImage image = ImageIO.read(stream);
            return TsplBitmap.fromImage(image, LOGO_WIDTH_DOTS);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load label logo image", e);
        }
    }
}
