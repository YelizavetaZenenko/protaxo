package com.example.printagent;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * Thin wrapper around {@code java.net.http.WebSocket} (built into the JDK — no library needed)
 * that connects to the backend's /ws/print-agent endpoint and hands each complete binary message
 * (a PNG picture of the label, see LabelImageRenderer on the backend) to a job handler. Binary, not
 * text — image bytes wouldn't survive a text frame's UTF-8 encoding round-trip. Text frames go the
 * other way: short status lines the server writes to its log.
 */
public final class BackendWebSocketClient {

    /** Counted down once when the connection closes or fails — PrintAgentMain waits on this to reconnect. */
    private final CountDownLatch closedLatch;
    private final String wsUrl;
    private final String token;
    private final JobHandler jobHandler;
    private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
    private volatile WebSocket webSocket;
    private Runnable onConnected;

    public BackendWebSocketClient(String wsUrl, String token, JobHandler jobHandler, CountDownLatch closedLatch) {
        this.wsUrl = wsUrl;
        this.token = token;
        this.jobHandler = jobHandler;
        this.closedLatch = closedLatch;
    }

    /** Runs once the socket is open — used to report the agent's settings to the server. */
    public void onConnected(Runnable action) {
        this.onConnected = action;
    }

    public void connect() {
        String urlWithToken = wsUrl + (wsUrl.contains("?") ? "&" : "?") + "token=" + token;
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(urlWithToken), new Listener())
                .join();
    }

    /** Status line for the server log (PrintAgentWebSocketHandler logs every text message) — best effort. */
    public void sendStatus(String text) {
        WebSocket ws = webSocket;
        if (ws != null) {
            try {
                ws.sendText(text, true);
            } catch (RuntimeException e) {
                System.err.println("[print-agent] Не вдалось надіслати статус на сервер: " + e.getMessage());
            }
        }
    }

    public interface JobHandler {
        void onJob(byte[] tsplPayload);
    }

    private class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            BackendWebSocketClient.this.webSocket = webSocket;
            System.out.println("[print-agent] Підключено до " + wsUrl);
            if (onConnected != null) {
                onConnected.run();
            }
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            messageBuffer.writeBytes(chunk);
            webSocket.request(1);
            if (last) {
                byte[] message = messageBuffer.toByteArray();
                messageBuffer.reset();
                jobHandler.onJob(message);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[print-agent] З'єднання закрито: " + statusCode + " " + reason);
            closedLatch.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[print-agent] Помилка з'єднання: " + error.getMessage());
            closedLatch.countDown();
        }
    }
}
