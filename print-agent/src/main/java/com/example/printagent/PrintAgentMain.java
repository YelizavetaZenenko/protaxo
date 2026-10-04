package com.example.printagent;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.print.PrintException;
import javax.swing.SwingUtilities;

/**
 * Entry point. Connects to the backend over WebSocket, prints whatever label job arrives, and
 * reconnects with a fixed delay whenever the connection drops (backend restart, network blip,
 * printer offline) — meant to run unattended on the shop's computer, so it never gives up after a
 * single failure. See docs/Print Agent.md for setup.
 *
 * <p>By default it also opens {@link SettingsWindow} (label settings with a live preview); pass
 * {@code --nogui} to run as before, console only. Settings saved in the window take effect for the
 * very next job — the agent reads the current config per job, not once at start-up.
 */
public final class PrintAgentMain {

    private static final long RECONNECT_DELAY_MS = 5000;
    private static final String LAST_LABEL_FILE = "last-label.png";

    private static volatile PrintAgentConfig config;
    private static volatile BackendWebSocketClient currentClient;
    private static volatile Consumer<Boolean> statusListener = connected -> { };
    private static volatile Runnable labelReceivedListener = () -> { };
    private static volatile Consumer<byte[]> confirmHandler;
    private static Path configPath;

    public static void main(String[] args) throws Exception {
        // Windows' console codepage is rarely UTF-8 by default — force it so Cyrillic log lines
        // aren't garbled regardless of the host's locale settings.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        boolean noGui = Arrays.asList(args).contains("--nogui") || GraphicsEnvironment.isHeadless();
        String pathArg = Arrays.stream(args).filter(a -> !a.startsWith("--")).findFirst().orElse("printagent.properties");
        configPath = Path.of(pathArg).toAbsolutePath();
        config = PrintAgentConfig.load(configPath);
        if (noGui) {
            config.requireToken();
        } else {
            SwingUtilities.invokeAndWait(() -> SettingsWindow.open(configPath));
        }

        System.out.println("[print-agent] Стартую, backend=" + config.backendWsUrl()
                + ", принтер=" + (config.printerName().isBlank() ? "(за замовчуванням)" : config.printerName())
                + ", спосіб=" + config.mode().key() + ", поворот=" + config.labelRotation() + "°");

        while (true) {
            PrintAgentConfig current = config;
            if (current.token().isBlank()) {
                System.out.println("[print-agent] Токен не задано — впишіть його в налаштуваннях і натисніть «Зберегти».");
            } else {
                CountDownLatch closedLatch = new CountDownLatch(1);
                BackendWebSocketClient client = new BackendWebSocketClient(
                        current.backendWsUrl(), current.token(), PrintAgentMain::handleJob,
                        connected -> statusListener.accept(connected), closedLatch);
                currentClient = client;
                try {
                    client.connect();
                    closedLatch.await();
                } catch (Exception e) {
                    statusListener.accept(false);
                    System.err.println("[print-agent] Не вдалось підключитись: " + e.getMessage());
                }
                currentClient = null;
                System.out.println("[print-agent] Повторне підключення через " + (RECONNECT_DELAY_MS / 1000) + "с...");
            }
            Thread.sleep(RECONNECT_DELAY_MS);
        }
    }

    static PrintAgentConfig config() {
        return config;
    }

    /** Saves and applies new settings; reconnects only if the server address or token changed. */
    static void applyConfig(PrintAgentConfig updated) throws IOException {
        updated.save(configPath);
        PrintAgentConfig previous = config;
        config = updated;
        if (!previous.backendWsUrl().equals(updated.backendWsUrl()) || !previous.token().equals(updated.token())) {
            BackendWebSocketClient client = currentClient;
            if (client != null) {
                client.close();
            }
        }
    }

    static Path lastLabelPath() {
        return configPath.resolveSibling(LAST_LABEL_FILE);
    }

    static void onStatus(Consumer<Boolean> listener) {
        statusListener = listener;
    }

    static void onLabelReceived(Runnable listener) {
        labelReceivedListener = listener;
    }

    /** The window's queue for labels that wait for «Друкувати» when print.confirm is on. */
    static void onConfirmNeeded(Consumer<byte[]> handler) {
        confirmHandler = handler;
    }

    private static void handleJob(byte[] payload) {
        if (PrinterClient.isPng(payload)) {
            // Kept so the settings window can preview — and re-print — the label that actually came in.
            try {
                Files.write(lastLabelPath(), payload);
                labelReceivedListener.run();
            } catch (IOException e) {
                System.err.println("[print-agent] Не вдалось зберегти " + LAST_LABEL_FILE + ": " + e.getMessage());
            }
            Consumer<byte[]> confirm = confirmHandler;
            if (config.confirmBeforePrint() && confirm != null) {
                System.out.println("[print-agent] Прийшла наклейка з сайту — чекає кнопки «Друкувати» у вікні");
                confirm.accept(payload);
                return;
            }
        }
        try {
            PrinterClient.print(payload, config);
            System.out.println("[print-agent] Наклейку надіслано на друк (" + payload.length + " байт)");
        } catch (PrintException e) {
            System.err.println("[print-agent] Помилка друку: " + e.getMessage());
        }
    }
}
