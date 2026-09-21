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
 * (50x80mm, portrait — confirmed with the user 2026-09-21) and text positions are a starting point
 * picked without the actual printer in hand — see [[Print Agent]] for why these will very likely
 * need adjusting once tested against real hardware (font and especially Cyrillic codepage are
 * printer/firmware-specific).
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
    /** 50x80mm portrait label, 8 dots/mm @ 203dpi (see class javadoc) — 400x640 dots. */
    private static final int LOGO_WIDTH_DOTS = 240;
    private static final int LOGO_X = 80;
    private static final String SHORT_COMPANY_ADDRESS = "м.Звягель, пров.Богуна 19-Б/2";
    private static final String SHORT_COMPANY_PHONE = "+38(067)-223-22-63";

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

    private final TsplBitmap.Encoded logo = loadLogo();

    /**
     * What the QR actually encodes — a link to {@link com.example.protaxo.calibration.web.PublicVerificationController},
     * not the bare hash, so a phone scan opens the protocol's PDF directly with no login required.
     * Exposed (not private) so the label preview page can encode/display the exact same value the
     * printer will — one source of truth, never two copies that could silently drift apart.
     */
    public String verifyUrl(CalibrationProtocolResponse protocol) {
        return publicVerifyBaseUrl + "/verify/" + protocol.qrHash() + "/pdf";
    }

    public byte[] build(CalibrationProtocolResponse protocol, String clientEdrpou) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLabel(out, header());
        writeLabel(out, "BITMAP " + LOGO_X + ",15," + logo.widthBytes() + "," + logo.heightDots() + ",0,");
        out.writeBytes(logo.data());
        writeLabel(out, "\r\n");
        writeLabel(out, textCommands(protocol, clientEdrpou));
        writeLabel(out, footer(protocol));
        return out.toByteArray();
    }

    /**
     * A human-readable stand-in for {@link #build} used only by the HTML label preview page —
     * the real payload is binary (embeds the raw logo bitmap) and can't be dumped as text, so the
     * bitmap line shows a placeholder byte count instead of the actual pixel bytes.
     */
    public String buildPreviewText(CalibrationProtocolResponse protocol, String clientEdrpou) {
        return header()
                + "BITMAP " + LOGO_X + ",15," + logo.widthBytes() + "," + logo.heightDots() + ",0,<логотип, " + logo.data().length + " байт бітмапи>\r\n"
                + textCommands(protocol, clientEdrpou)
                + footer(protocol);
    }

    /** 50x80mm, portrait (see LOGO_WIDTH_DOTS comment) — was 50x30mm landscape. */
    private String header() {
        return "SIZE 50 mm, 80 mm\r\nGAP 2 mm, 0 mm\r\nDIRECTION 1\r\nCODEPAGE 1251\r\nCLS\r\n";
    }

    private String footer(CalibrationProtocolResponse protocol) {
        return "QRCODE 100,510,H,3,A,0,\"" + verifyUrl(protocol) + "\"\r\nPRINT 1,1\r\n";
    }

    /**
     * Top to bottom: logo (BITMAP, in {@link #build}) → address → phone, both centered → stamp
     * number alone in a large font → date → VIN → S/N (the client's EDRPOU/RNOKPP code, not a
     * tachograph serial number — printed here so the physical seal can be traced back to the
     * carrier without a QR scan) → wheels → W/K/V-set, each on its own line. Client name and seal
     * numbers are intentionally not printed — S/N (EDRPOU) identifies the carrier instead, and the
     * seal numbers were dropped from the label by request (2026-09-21), even though the field still
     * exists on the protocol itself.
     *
     * <p>Address/phone centering uses TSPL2's optional 7th {@code TEXT} parameter (alignment: 2 =
     * center), with X set to the label's horizontal midpoint (400 dots / 2) rather than a left
     * margin — like the rest of this class, unverified against the real printer (see class
     * javadoc); some TSPL firmwares ignore this parameter or center relative to a different anchor.
     */
    private String textCommands(CalibrationProtocolResponse protocol, String clientEdrpou) {
        String date = protocol.protocolDate() == null ? null : DATE_FORMAT.format(protocol.protocolDate());
        StringBuilder sb = new StringBuilder();
        sb.append("TEXT 200,185,\"1\",0,1,1,2,\"").append(escape(SHORT_COMPANY_ADDRESS)).append("\"\r\n");
        sb.append("TEXT 200,210,\"1\",0,1,1,2,\"").append(escape(field("Tel", SHORT_COMPANY_PHONE))).append("\"\r\n");
        sb.append("TEXT 10,240,\"3\",0,1,1,\"").append(escape(orDash(protocol.stampNumber()))).append("\"\r\n");
        sb.append("TEXT 10,300,\"1\",0,1,1,\"").append(escape(field("Date", date))).append("\"\r\n");
        sb.append("TEXT 10,325,\"1\",0,1,1,\"").append(escape(field("VIN", truncate(protocol.vehicleVin(), 22)))).append("\"\r\n");
        sb.append("TEXT 10,350,\"1\",0,1,1,\"").append(escape(field("S/N", clientEdrpou))).append("\"\r\n");
        sb.append("TEXT 10,375,\"1\",0,1,1,\"").append(escape(field("Wheels", truncate(protocol.tireSize(), 16))))
                .append("  L=").append(escape(orDash(protocol.tireCircumferenceL()))).append("\"\r\n");
        sb.append("TEXT 10,400,\"1\",0,1,1,\"W=").append(escape(orDash(protocol.coefficientW()))).append("\"\r\n");
        sb.append("TEXT 10,425,\"1\",0,1,1,\"K=").append(escape(orDash(protocol.constantK()))).append("\"\r\n");
        sb.append("TEXT 10,450,\"1\",0,1,1,\"V-set=").append(escape(orDash(protocol.speedLimiterValue()))).append("\"\r\n");
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
