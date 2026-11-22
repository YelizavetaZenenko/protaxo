package com.example.protaxo.audit.service;

import com.example.protaxo.audit.entity.AuditAction;
import java.lang.management.ManagementFactory;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Запуск і зупинка сервера в журналі дій: видно деплої й перезапуски поруч із входами й
 * завершеннями сесій (чи не збігся «викинуло» з перезапуском).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServerLifecycleAuditor {

    private final AuditLogService auditLogService;

    @EventListener(ApplicationReadyEvent.class)
    public void started() {
        long seconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        safely(AuditAction.SERVER_START, Map.of("Запуск тривав", new String[]{"", seconds + " с"}));
    }

    @EventListener(ContextClosedEvent.class)
    public void stopping() {
        safely(AuditAction.SERVER_STOP, null);
    }

    private void safely(AuditAction action, Map<String, String[]> changes) {
        try {
            auditLogService.recordAs("system", action, "Server", null, changes, null);
        } catch (RuntimeException e) {
            log.warn("Audit of {} failed: {}", action, e.getMessage());
        }
    }
}
