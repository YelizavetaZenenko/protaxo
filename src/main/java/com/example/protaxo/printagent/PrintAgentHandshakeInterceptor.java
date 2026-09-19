package com.example.protaxo.printagent;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The /ws/print-agent endpoint is deliberately outside the normal login session (it's a background
 * process on the shop's computer, not a person at a browser — see SecurityConfig, which permits
 * this path without authentication) — this is the only gate. A connection without a matching
 * {@code ?token=} query param never completes the WebSocket handshake.
 */
@Component
@Slf4j
public class PrintAgentHandshakeInterceptor implements HandshakeInterceptor {

    @Value("${print-agent.token}")
    private String expectedToken;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || !token.equals(expectedToken)) {
            log.warn("Rejected Print Agent handshake from {} — missing or wrong token", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
