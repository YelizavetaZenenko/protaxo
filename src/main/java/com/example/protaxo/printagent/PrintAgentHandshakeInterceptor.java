package com.example.protaxo.printagent;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AuditLogService auditLogService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || !constantTimeEquals(token, expectedToken)) {
            log.warn("Rejected Print Agent handshake from {} — missing or wrong token", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            try {
                auditLogService.recordAs("print-agent", AuditAction.ACCESS_DENIED, "PrintAgent", null,
                        Map.of("Причина", new String[]{"", token == null ? "Підключення без токена" : "Неправильний токен"}));
            } catch (RuntimeException e) {
                log.warn("Audit of rejected handshake failed: {}", e.getMessage());
            }
            return false;
        }
        return true;
    }

    /**
     * {@code String.equals} short-circuits on the first mismatched byte, which leaks (via
     * response timing) how many leading characters of a guess were correct — a classic timing
     * side-channel for a secret token. {@link MessageDigest#isEqual} always compares the full
     * length of both arrays regardless of where they first differ.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
