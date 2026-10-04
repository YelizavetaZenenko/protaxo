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
 * (the label PNG, see LabelImageRenderer on the backend) to a job handler. Binary, not text —
 * arbitrary image bytes wouldn't survive a text frame's UTF-8 encoding round-trip.
 */
public final class BackendWebSocketClient {

    /** Counted down once when the connection closes or fails — PrintAgentMain waits on this to reconnect. */
    private final CountDownLatch closedLatch;
    private final String wsUrl;
    private final String token;
    private final JobHandler jobHandler;
    private final StatusListener statusListener;
    private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
    private WebSocket webSocket;

    public BackendWebSocketClient(String wsUrl, String token, JobHandler jobHandler,
                                  StatusListener statusListener, CountDownLatch closedLatch) {
        this.wsUrl = wsUrl;
        this.token = token;
        this.jobHandler = jobHandler;
        this.statusListener = statusListener;
        this.closedLatch = closedLatch;
    }

    public void connect() {
        String urlWithToken = wsUrl + (wsUrl.contains("?") ? "&" : "?") + "token=" + token;
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(urlWithToken), new Listener())
                .join();
    }

    /** Drops the connection (e.g. after the server address or token changed); the main loop reconnects. */
    public void close() {
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.abort();
        }
        closedLatch.countDown();
    }

    public interface JobHandler {
        void onJob(byte[] payload);
    }

    public interface StatusListener {
        void onStatus(boolean connected);
    }

    private class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            BackendWebSocketClient.this.webSocket = webSocket;
            System.out.println("[print-agent] Підключено до " + wsUrl);
            statusListener.onStatus(true);
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
            statusListener.onStatus(false);
            closedLatch.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[print-agent] Помилка з'єднання: " + error.getMessage());
            statusListener.onStatus(false);
            closedLatch.countDown();
        }
    }
}
