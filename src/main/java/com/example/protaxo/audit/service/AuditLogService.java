package com.example.protaxo.audit.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.entity.AuditLog;
import com.example.protaxo.audit.repository.AuditLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private static final Logger FILE_LOGGER = LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository auditLogRepository;

    public void record(AuditAction action, String entityType, Long entityId) {
        String username = currentUsername();

        AuditLog log = AuditLog.builder()
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .occurredAt(Instant.now())
                .build();
        auditLogRepository.save(log);

        FILE_LOGGER.info("user={} action={} entityType={} entityId={}", username, action, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findRecent(Pageable pageable) {
        return auditLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
