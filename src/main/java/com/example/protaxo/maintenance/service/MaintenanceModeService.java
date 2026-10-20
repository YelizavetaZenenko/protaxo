package com.example.protaxo.maintenance.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.maintenance.entity.MaintenanceMode;
import com.example.protaxo.maintenance.repository.MaintenanceModeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Режим технічних робіт (docs/Технічні роботи.md). Перевіряється на кожному запиті
 * ({@code MaintenanceModeInterceptor}), тому стан тримається в пам'яті й читається з БД лише
 * один раз після старту — застосунок працює в одному екземплярі, а змінюється стан тільки через
 * {@link #setEnabled}.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceModeService {

    private final MaintenanceModeRepository repository;
    private final AuditLogService auditLogService;

    private volatile Boolean enabled;

    public boolean isEnabled() {
        Boolean cached = enabled;
        if (cached == null) {
            cached = load().isEnabled();
            enabled = cached;
        }
        return cached;
    }

    @Transactional
    public void setEnabled(boolean value, String changedBy) {
        MaintenanceMode mode = load();
        if (mode.isEnabled() == value) {
            enabled = value;
            return;
        }
        mode.setEnabled(value);
        mode.setChangedAt(LocalDateTime.now());
        mode.setChangedBy(changedBy);
        repository.save(mode);
        enabled = value;
        auditLogService.record(AuditAction.UPDATE, "MaintenanceMode", (long) MaintenanceMode.SINGLETON_ID,
                FieldDiff.builder().add("Технічні роботи", label(!value), label(value)).build());
    }

    private MaintenanceMode load() {
        return repository.findById(MaintenanceMode.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("maintenance_mode row is missing (V56 migration)"));
    }

    private static String label(boolean value) {
        return value ? "увімкнено" : "вимкнено";
    }
}
