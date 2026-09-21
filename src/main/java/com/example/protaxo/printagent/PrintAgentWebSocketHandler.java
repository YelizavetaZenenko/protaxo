package com.example.protaxo.printagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Server side of the [[Print Agent]] WebSocket channel (/ws/print-agent). Jobs only ever flow
 * server -> agent (see PrintAgentSessionRegistry#broadcast); messages the agent sends back are
 * just status lines for the server log ("printed"/"error: ..."), there's no job queue or
 * acknowledgement tracking yet — the shop only runs one agent, so "sent" is treated as "handled".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrintAgentWebSocketHandler extends TextWebSocketHandler {

    private final PrintAgentSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("Print Agent {} says: {}", session.getId(), message.getPayload());
        registry.recordPong(session);
    }

    /** Answers to {@link PrintAgentSessionRegistry#sweepStaleSessions()}'s periodic pings. */
    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        registry.recordPong(session);
    }
}
