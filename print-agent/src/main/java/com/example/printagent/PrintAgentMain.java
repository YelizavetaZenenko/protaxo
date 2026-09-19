package com.example.printagent;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javax.print.PrintException;

/**
 * Entry point. Connects to the backend over WebSocket, prints whatever TSPL job arrives, and
 * reconnects with a fixed delay whenever the connection drops (backend restart, network blip,
 * printer offline) — meant to run unattended as a background process on the shop's computer, so
 * it never gives up after a single failure. See docs/Print Agent.md for setup.
 */
public final class PrintAgentMain {

    private static final long RECONNECT_DELAY_MS = 5000;

    public static void main(String[] args) throws Exception {
        // Windows' console codepage is rarely UTF-8 by default — force it so Cyrillic log lines
        // aren't garbled regardless of the host's locale settings.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Path configPath = Path.of(args.length > 0 ? args[0] : "printagent.properties");
        PrintAgentConfig config = PrintAgentConfig.load(configPath);
        PrinterClient printerClient = new PrinterClient(config.printerName());

        System.out.println("[print-agent] Стартую, backend=" + config.backendWsUrl()
                + ", принтер=" + (config.printerName().isBlank() ? "(за замовчуванням)" : config.printerName()));

        while (true) {
            CountDownLatch closedLatch = new CountDownLatch(1);
            BackendWebSocketClient client = new BackendWebSocketClient(
                    config.backendWsUrl(), config.token(), tspl -> handleJob(printerClient, tspl), closedLatch);
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

    private static void handleJob(PrinterClient printerClient, byte[] tspl) {
        try {
            printerClient.print(tspl);
            System.out.println("[print-agent] Наклейку надіслано на друк (" + tspl.length + " байт TSPL)");
        } catch (PrintException e) {
            System.err.println("[print-agent] Помилка друку: " + e.getMessage());
        }
    }
}
