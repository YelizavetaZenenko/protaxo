package com.example.protaxo.printagent;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Tracks Print Agent WebSocket connections and broadcasts print jobs to all of them. One shop is
 * expected to run a single agent process, but broadcasting to every currently-open session (rather
 * than picking "the one" connection) keeps this simple and tolerant of reconnects — an agent that
 * dropped and reconnected just has a stale session that's already been removed by
 * {@code afterConnectionClosed}, never a phantom duplicate print.
 *
 * <p>{@code WebSocketSession.isOpen()} alone is not enough to know an agent is actually reachable —
 * an unclean disconnect (sleep, cable pull, crash) can leave a session reporting "open" for a long
 * time before the OS notices, during which a print job would be reported as "sent" and then never
 * arrive. A periodic ping/pong sweep detects and drops those stale sessions instead.
 */
@Component
@Slf4j
public class PrintAgentSessionRegistry {

    private static final int STALE_AFTER_SECONDS = 90;

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    void register(WebSocketSession session) {
        sessions.add(session);
        lastSeen.put(session.getId(), Instant.now());
        log.info("Print Agent connected: {}", session.getId());
    }

    void unregister(WebSocketSession session) {
        sessions.remove(session);
        lastSeen.remove(session.getId());
        log.info("Print Agent disconnected: {}", session.getId());
    }

    /** Called by {@link PrintAgentWebSocketHandler} whenever a pong frame comes back. */
    void recordPong(WebSocketSession session) {
        lastSeen.put(session.getId(), Instant.now());
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
                // A failed send means this session is dead — drop it now rather than leaving it
                // registered (which would keep isAnyAgentConnected() reporting true, and keep
                // retrying a send that will never succeed, for every future job).
                unregister(session);
            }
        }
        return sentToAny;
    }

    /**
     * Every 30s: ping sessions that answered recently, and drop ones that haven't answered (a ping
     * or any inbound message) in over {@value STALE_AFTER_SECONDS}s — that's the actual liveness
     * check {@code isOpen()} alone can't provide. Runs even with zero sessions; the loop is a no-op.
     */
    @Scheduled(fixedRate = 30_000)
    void sweepStaleSessions() {
        Instant staleThreshold = Instant.now().minusSeconds(STALE_AFTER_SECONDS);
        for (WebSocketSession session : sessions) {
            Instant seenAt = lastSeen.getOrDefault(session.getId(), Instant.now());
            if (seenAt.isBefore(staleThreshold)) {
                log.warn("Print Agent session {} unresponsive for over {}s, dropping", session.getId(), STALE_AFTER_SECONDS);
                closeQuietly(session);
                unregister(session);
                continue;
            }
            try {
                if (session.isOpen()) {
                    session.sendMessage(new PingMessage());
                }
            } catch (IOException e) {
                log.warn("Ping failed for Print Agent session {}: {}", session.getId(), e.getMessage());
                unregister(session);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.GOING_AWAY);
        } catch (IOException ignored) {
            // Already gone — that's exactly why we're dropping it.
        }
    }
}
