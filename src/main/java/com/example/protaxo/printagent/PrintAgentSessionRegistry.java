package com.example.protaxo.printagent;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Tracks Print Agent WebSocket connections and broadcasts print jobs to all of them. One shop is
 * expected to run a single agent process, but broadcasting to every currently-open session (rather
 * than picking "the one" connection) keeps this simple and tolerant of reconnects — an agent that
 * dropped and reconnected just has a stale session that's already been removed by
 * {@code afterConnectionClosed}, never a phantom duplicate print.
 */
@Component
@Slf4j
public class PrintAgentSessionRegistry {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    void register(WebSocketSession session) {
        sessions.add(session);
        log.info("Print Agent connected: {}", session.getId());
    }

    void unregister(WebSocketSession session) {
        sessions.remove(session);
        log.info("Print Agent disconnected: {}", session.getId());
    }

    public boolean isAnyAgentConnected() {
        return !sessions.isEmpty();
    }

    /**
     * @return true if the job was handed to at least one connected agent — the caller uses this to
     * tell the user whether printing was actually queued or nothing is listening right now.
     *
     * <p>Sent as a binary frame, not text — the payload embeds a raw TSPL {@code BITMAP} (the
     * label logo), arbitrary pixel bytes that a text frame's UTF-8 encoding would corrupt. See
     * {@link TsplLabelBuilder}.
     */
    public boolean broadcast(byte[] payload) {
        boolean sentToAny = false;
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new BinaryMessage(payload));
                    sentToAny = true;
                }
            } catch (IOException e) {
                log.warn("Failed to send print job to agent session {}: {}", session.getId(), e.getMessage());
            }
        }
        return sentToAny;
    }
}
