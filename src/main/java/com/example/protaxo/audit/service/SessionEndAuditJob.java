package com.example.protaxo.audit.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.web.ServerActivityAuditFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Записує в журнал, що сесія згасла сама, і лише тоді її видаляє (docs/Журнал дій (Audit Log).md).
 *
 * <p>Раніше прострочені сесії мовчки видаляло завдання Spring Session (cleanup-cron), тож у журналі
 * були лише повторні «Вхід» без жодного «Вихід» і не було видно, чому людину викинуло. Тепер
 * стандартне прибирання вимкнене ({@code spring.session.jdbc.cleanup-cron=-}), а це завдання раз на
 * хвилину робить те саме, але спершу пише «Сесію завершено» з причиною:
 * <ul>
 *   <li>таймаут ≤ 1 хв — це скорочення від {@code /session/closing}: закрито всі вкладки сайту;</li>
 *   <li>інакше — 30 хв без жодного запиту: сон чи блокування ПК, заморожена вкладка, обрив мережі.</li>
 * </ul>
 * Сесію видаляє {@code DELETE … WHERE EXPIRY_TIME < now}: якщо між вибіркою й видаленням людина
 * встигла зробити запит (сесія ожила), рядок не видалиться і запису не буде.
 * Анонімні сесії (без входу — сторінка логіну, QR-перевірка) видаляються без запису.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionEndAuditJob {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final DeserializingConverter deserializer = new DeserializingConverter();

    @Value("${app.session.closing-grace:10s}")
    private Duration closingGrace;

    record Expired(String id, String principal, long created, long lastAccess, int maxInactive, long expiry) {
    }

    @Scheduled(cron = "${app.session.cleanup-cron:0 * * * * *}")
    public void endExpiredSessions() {
        long now = System.currentTimeMillis();
        List<Expired> expired = jdbcTemplate.query(
                "SELECT PRIMARY_ID, PRINCIPAL_NAME, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME "
                        + "FROM SPRING_SESSION WHERE EXPIRY_TIME < ? ORDER BY EXPIRY_TIME LIMIT 1000",
                (rs, i) -> new Expired(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), rs.getLong(6)),
                now);
        for (Expired session : expired) {
            try {
                end(session, now);
            } catch (RuntimeException e) {
                log.warn("Failed to end session {}: {}", session.id(), e.getMessage());
            }
        }
    }

    private void end(Expired session, long now) {
        String lastPage = null;
        String lastIp = null;
        if (session.principal() != null) {
            lastPage = attribute(session.id(), ServerActivityAuditFilter.LAST_PAGE_ATTR);
            lastIp = attribute(session.id(), ServerActivityAuditFilter.LAST_IP_ATTR);
        }
        int deleted = jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRIMARY_ID = ? AND EXPIRY_TIME < ?",
                session.id(), now);
        if (deleted == 0 || session.principal() == null) {
            return;
        }
        auditLogService.recordAs(session.principal(), AuditAction.SESSION_ENDED, "Session", null,
                describe(session, lastPage), lastIp);
    }

    Map<String, String[]> describe(Expired session, String lastPage) {
        ZoneId zone = ZoneId.systemDefault();
        boolean tabsClosed = session.maxInactive() <= Math.max(60, closingGrace.toSeconds());
        Map<String, String[]> changes = new LinkedHashMap<>();
        changes.put("Причина", new String[]{"", tabsClosed
                ? "Закрито всі вкладки сайту"
                : "Неактивність " + Duration.ofSeconds(session.maxInactive()).toMinutes()
                        + " хв: сторінка не надсилала сигналів (сон чи блокування ПК, заморожена вкладка, обрив мережі, закритий браузер)"});
        changes.put("Остання активність", new String[]{"", TIME.format(Instant.ofEpochMilli(session.lastAccess()).atZone(zone))});
        changes.put("Сесія згасла о", new String[]{"", TIME.format(Instant.ofEpochMilli(session.expiry()).atZone(zone))});
        changes.put("Тривалість сесії", new String[]{"", humanDuration(Duration.ofMillis(session.lastAccess() - session.created()))});
        if (lastPage != null) {
            changes.put("Остання сторінка", new String[]{"", lastPage});
        }
        return changes;
    }

    private String attribute(String sessionId, String name) {
        List<byte[]> rows = jdbcTemplate.query(
                "SELECT ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ? AND ATTRIBUTE_NAME = ?",
                (rs, i) -> rs.getBytes(1), sessionId, name);
        if (rows.isEmpty()) {
            return null;
        }
        try {
            return deserializer.convert(rows.getFirst()) instanceof String s ? s : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String humanDuration(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 1) {
            return d.toSeconds() + " с";
        }
        return minutes < 60 ? minutes + " хв" : minutes / 60 + " год " + minutes % 60 + " хв";
    }
}
