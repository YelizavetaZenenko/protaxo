package com.example.printagent;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javax.print.PrintException;

/**
 * Entry point. Connects to the backend over WebSocket, prints whatever label job arrives, and
 * reconnects with a fixed delay whenever the connection drops (backend restart, network blip,
 * printer offline) — meant to run unattended as a background process on the shop's computer, so
 * it never gives up after a single failure. See docs/Print Agent.md for setup.
 */
public final class PrintAgentMain {

    private static final long RECONNECT_DELAY_MS = 5000;
    /** Shown on start and reported to the server — tells at a glance which build the shop actually runs. */
    static final String VERSION = "2026-09-30 ZPL/EPL";

    public static void main(String[] args) throws Exception {
        // Windows' console codepage is rarely UTF-8 by default — force it so Cyrillic log lines
        // aren't garbled regardless of the host's locale settings.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Path configPath = Path.of(args.length > 0 ? args[0] : "printagent.properties");
        PrintAgentConfig config = PrintAgentConfig.load(configPath);
        PrinterClient printerClient = new PrinterClient(
                config.printerName(), config.labelRotation(), config.printerLanguage(), config.labelInvert());

        String settings = "принтер=" + (config.printerName().isBlank() ? "(за замовчуванням)" : config.printerName())
                + ", мова=" + config.printerLanguage()
                + ", поворот наклейки=" + config.labelRotation() + "°"
                + (config.labelInvert() ? ", інверсія" : "");
        System.out.println("[print-agent] Стартую (версія " + VERSION + "), backend=" + config.backendWsUrl() + ", " + settings);

        while (true) {
            CountDownLatch closedLatch = new CountDownLatch(1);
            BackendWebSocketClient[] holder = new BackendWebSocketClient[1];
            BackendWebSocketClient client = new BackendWebSocketClient(
                    config.backendWsUrl(), config.token(), job -> handleJob(printerClient, holder[0], job), closedLatch);
            holder[0] = client;
            client.onConnected(() -> client.sendStatus("підключено, версія " + VERSION + ", " + settings));
            try {
                client.connect();
                closedLatch.await();
            } catch (Exception e) {
                System.err.println("[print-agent] Не вдалось підключитись: " + e.getMessage());
            }
            System.out.println("[print-agent] Повторне підключення через " + (RECONNECT_DELAY_MS / 1000) + "с...");
            Thread.sleep(RECONNECT_DELAY_MS);
        }
    }

    /** Every outcome also goes to the server log, so a print problem can be diagnosed without the shop's screen. */
    private static void handleJob(PrinterClient printerClient, BackendWebSocketClient client, byte[] job) {
        try {
            String sent = printerClient.print(job);
            System.out.println("[print-agent] Наклейку надіслано на друк: " + sent);
            client.sendStatus("надруковано: " + sent);
        } catch (PrintException | RuntimeException e) {
            System.err.println("[print-agent] Помилка друку: " + e.getMessage());
            client.sendStatus("помилка друку: " + e.getMessage());
        }
    }
}
