package com.example.printagent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Loaded from printagent.properties (see printagent.properties.example) — token must match the
 * backend's {@code print-agent.token} (application.properties / PRINT_AGENT_TOKEN env var).
 * Immutable: the settings window builds a new one from its form and {@link #save}s it.
 *
 * <p>The label is described as it physically sits on the roll — {@code labelWidthMm} across the
 * print head, {@code labelLengthMm} along the feed — because that is what the printer needs, not
 * how the label's text happens to be oriented (the Godex roll has its labels sideways).
 */
public record PrintAgentConfig(
        String backendWsUrl,
        String token,
        String printerName,
        PrintMode mode,
        double labelWidthMm,
        double labelLengthMm,
        double labelGapMm,
        int labelRotation,
        double scalePercent,
        double offsetXmm,
        double offsetYmm,
        int darkness,
        boolean invert,
        boolean confirmBeforePrint) {

    public static final PrintAgentConfig DEFAULTS = new PrintAgentConfig(
            "ws://localhost:8080/ws/print-agent", "", "Godex G500", PrintMode.DRIVER,
            81.5, 47.5, 3.0, 90, 100, 0, 0, 0, false, false);

    /** How the finished label picture reaches the printer. */
    public enum PrintMode {
        EZPL("ezpl", "EZPL — рідна мова Godex, з розміром етикетки"),
        ZPL("zpl", "ZPL — напряму в принтер, з розміром етикетки"),
        EPL("epl", "EPL — напряму в принтер, з розміром етикетки"),
        DRIVER("driver", "Через драйвер Windows (може обрізати широку етикетку)");

        private final String key;
        private final String title;

        PrintMode(String key, String title) {
            this.key = key;
            this.title = title;
        }

        public String key() {
            return key;
        }

        @Override
        public String toString() {
            return title;
        }

        static PrintMode parse(String raw) {
            for (PrintMode mode : values()) {
                if (mode.key.equalsIgnoreCase(raw.trim())) {
                    return mode;
                }
            }
            throw new IllegalStateException("printagent.properties: 'printer.language' має бути ezpl, zpl, epl або driver, а не '" + raw + "'");
        }
    }

    /** Missing file → defaults (the settings window then lets the user fill in server and token). */
    public static PrintAgentConfig load(Path propertiesPath) throws IOException {
        if (!Files.exists(propertiesPath)) {
            return DEFAULTS;
        }
        Properties props = new Properties();
        // UTF-8, not Properties' ISO-8859-1 default: the file's comments (and possibly a printer
        // name) are Cyrillic.
        try (Reader in = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
            props.load(in);
        }
        PrintAgentConfig d = DEFAULTS;
        return new PrintAgentConfig(
                props.getProperty("backend.ws.url", d.backendWsUrl).trim(),
                props.getProperty("token", d.token).trim(),
                props.getProperty("printer.name", d.printerName).trim(),
                PrintMode.parse(props.getProperty("printer.language", d.mode.key)),
                number(props, "label.width.mm", d.labelWidthMm),
                number(props, "label.length.mm", d.labelLengthMm),
                number(props, "label.gap.mm", d.labelGapMm),
                parseRotation(props.getProperty("label.rotation", String.valueOf(d.labelRotation))),
                number(props, "label.scale.percent", d.scalePercent),
                number(props, "label.offset.x.mm", d.offsetXmm),
                number(props, "label.offset.y.mm", d.offsetYmm),
                (int) number(props, "label.darkness", d.darkness),
                Boolean.parseBoolean(props.getProperty("label.invert", String.valueOf(d.invert)).trim()),
                Boolean.parseBoolean(props.getProperty("print.confirm", String.valueOf(d.confirmBeforePrint)).trim()));
    }

    /** Rewrites the whole file with comments, so it stays readable when opened by hand. */
    public void save(Path propertiesPath) throws IOException {
        String text = """
                # Налаштування ProTaxo Print Agent. Зручніше міняти у вікні програми — воно саме
                # перезаписує цей файл кнопкою «Зберегти». Файл НЕ комітиться в git (токен — секрет).

                # Адреса сервера ProTaxo (працює лише з увімкненим Tailscale) і токен — має збігатись
                # з print-agent.token бекенду.
                backend.ws.url=%s
                token=%s

                # Назва принтера як у Windows («Принтери та сканери»). Порожньо — принтер за замовчуванням.
                printer.name=%s

                # Як наклейка йде на принтер: ezpl (рідна мова Godex) / zpl / epl — сирі команди з розміром
                # етикетки всередині; або driver (картинкою через драйвер Windows).
                printer.language=%s

                # Етикетка на рулоні: ширина ПОПЕРЕК стрічки, довжина ВЗДОВЖ стрічки, проміжок між
                # етикетками — в мм.
                label.width.mm=%s
                label.length.mm=%s
                label.gap.mm=%s

                # Поворот наклейки за годинниковою стрілкою: 0, 90, 180 або 270.
                label.rotation=%d

                # Масштаб картинки всередині етикетки, %% (100 — як прийшла з сервера; менше — якщо краї обрізаються).
                label.scale.percent=%s

                # Зсув вмісту всередині етикетки, мм (+X — до другого краю поперек стрічки, +Y — вздовж).
                label.offset.x.mm=%s
                label.offset.y.mm=%s

                # Темність друку 1..15; 0 — як налаштовано в самому принтері.
                label.darkness=%d

                # true — поміняти чорне й біле місцями (якщо принтер друкує «негативом»).
                label.invert=%s

                # true — наклейка з сайту не друкується одразу, а чекає у вікні кнопки «Друкувати».
                print.confirm=%s
                """.formatted(
                escape(backendWsUrl), escape(token), escape(printerName), mode.key,
                mm(labelWidthMm), mm(labelLengthMm), mm(labelGapMm), labelRotation,
                mm(scalePercent), mm(offsetXmm), mm(offsetYmm), darkness, invert, confirmBeforePrint);
        try (Writer out = Files.newBufferedWriter(propertiesPath, StandardCharsets.UTF_8)) {
            out.write(text);
        }
    }

    /** Only the headless mode insists — the window lets the user type the token in. */
    public void requireToken() {
        if (token.isBlank()) {
            throw new IllegalStateException(
                    "printagent.properties: 'token' не задано — має збігатись із print-agent.token бекенду");
        }
    }

    private static double number(Properties props, String key, double fallback) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("printagent.properties: '" + key + "' має бути числом, а не '" + raw + "'");
        }
    }

    private static int parseRotation(String raw) {
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("printagent.properties: 'label.rotation' має бути 0, 90, 180 або 270, а не '" + raw + "'");
        }
        if (value != 0 && value != 90 && value != 180 && value != 270) {
            throw new IllegalStateException("printagent.properties: 'label.rotation' має бути 0, 90, 180 або 270, а не '" + raw + "'");
        }
        return value;
    }

    private static String mm(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Properties syntax: backslashes are escapes, so a Windows path or token with one must double it. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\");
    }
}
