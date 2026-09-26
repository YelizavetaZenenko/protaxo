package com.example.protaxo.finance.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.finance.dto.FinanceForms;
import com.example.protaxo.finance.entity.FinanceSettings;
import com.example.protaxo.finance.entity.TaxSystem;
import com.example.protaxo.finance.repository.FinanceSettingsRepository;
import com.example.protaxo.security.service.CurrentUserRoles;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Налаштування обліку для панелі бухгалтера (один рядок на систему). */
@Service
@RequiredArgsConstructor
@Transactional
public class FinanceSettingsService {

    private final FinanceSettingsRepository repository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public FinanceSettings get() {
        return repository.findById(FinanceSettings.SINGLETON_ID).orElseGet(() -> {
            FinanceSettings settings = new FinanceSettings();
            settings.setId(FinanceSettings.SINGLETON_ID);
            return settings;
        });
    }

    public void update(FinanceForms.Settings form) {
        FinanceSettings settings = repository.findById(FinanceSettings.SINGLETON_ID).orElseGet(() -> {
            FinanceSettings created = new FinanceSettings();
            created.setId(FinanceSettings.SINGLETON_ID);
            return created;
        });
        String businessName = form.getBusinessName() == null || form.getBusinessName().isBlank()
                ? null : form.getBusinessName().trim();
        var changes = FieldDiff.builder()
                .add("Назва", settings.getBusinessName(), businessName)
                .add("Система оподаткування", label(settings.getTaxSystem()), label(form.getTaxSystem()))
                .add("Платник ПДВ", settings.isVatPayer() ? "так" : "ні", form.isVatPayer() ? "так" : "ні")
                .build();
        settings.setBusinessName(businessName);
        settings.setTaxSystem(form.getTaxSystem());
        settings.setVatPayer(form.isVatPayer());
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(CurrentUserRoles.username());
        repository.save(settings);
        auditLogService.record(AuditAction.UPDATE, "FinanceSettings", settings.getId(), changes);
    }

    private static String label(TaxSystem taxSystem) {
        return taxSystem == null ? null : taxSystem.getLabel();
    }
}
