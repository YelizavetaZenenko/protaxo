package com.example.protaxo.printagent;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Автооновлення програми друку наклейок (docs/Print Agent.md). Програма на комп'ютері СТО раз на
 * годину й при запуску питає {@code /agent/update/version}; якщо версія новіша за її власну —
 * завантажує {@code /agent/update/jar} і перезапускається з новим файлом.
 *
 * <p>Без входу на сайт (це фоновий процес): доступ — за тим самим токеном, що й для друку
 * ({@code print-agent.token}), у заголовку {@code X-Agent-Token} (не в адресі — щоб токен не
 * потрапляв у журнали проксі). SecurityConfig пропускає {@code /agent/update/**} без сесії.
 */
@RestController
@RequestMapping("/agent/update")
@RequiredArgsConstructor
public class AgentUpdateController {

    static final String TOKEN_HEADER = "X-Agent-Token";

    private final PrintAgentDistribution distribution;
    private final AuditLogService auditLogService;

    @Value("${print-agent.token}")
    private String expectedToken;

    @GetMapping(value = "/version", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> version(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        if (!tokenMatches(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return distribution.version()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jar")
    public ResponseEntity<byte[]> jar(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                      @RequestHeader(value = "X-Agent-Version", required = false) String fromVersion)
            throws IOException {
        if (!tokenMatches(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!distribution.available()) {
            return ResponseEntity.notFound().build();
        }
        String toVersion = distribution.version().orElse("?");
        try {
            auditLogService.recordAs("print-agent", AuditAction.FILE_DOWNLOAD, "PrintAgent", null,
                    Map.of("Автооновлення", new String[]{fromVersion == null ? "" : fromVersion, toVersion}));
        } catch (RuntimeException e) {
            // журнал не повинен заважати оновленню
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(distribution.jarBytes());
    }

    /** Порівняння за сталий час — як у PrintAgentHandshakeInterceptor. */
    boolean tokenMatches(String token) {
        return token != null && expectedToken != null && !expectedToken.isBlank()
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}
