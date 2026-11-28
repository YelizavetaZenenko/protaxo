package com.example.protaxo.client.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.entity.CustomLegalForm;
import com.example.protaxo.client.entity.LegalForm;
import com.example.protaxo.client.repository.CustomLegalFormRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.util.FieldDiff;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Свої форми власності контрагента (docs/Контрагенти.md): список і «+ Додати свою форму». */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomLegalFormService {

    private final CustomLegalFormRepository repository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CustomLegalForm> findAll() {
        return repository.findAllByOrderByShortLabelAsc();
    }

    @Transactional(readOnly = true)
    public Optional<CustomLegalForm> findById(Long id) {
        return id == null ? Optional.empty() : repository.findById(id);
    }

    /** Скорочення не може повторювати ні вбудовану форму, ні вже додану (без урахування регістру). */
    public CustomLegalForm create(String shortLabel, String fullLabel) {
        String shortTrimmed = normalize(shortLabel);
        String fullTrimmed = normalize(fullLabel);
        if (shortTrimmed == null) {
            throw new BusinessRuleException("Вкажіть скорочення форми власності");
        }
        if (shortTrimmed.length() > 20) {
            throw new BusinessRuleException("Скорочення — не довше 20 символів");
        }
        if (fullTrimmed != null && fullTrimmed.length() > 255) {
            throw new BusinessRuleException("Повна назва форми — не довше 255 символів");
        }
        boolean builtIn = Arrays.stream(LegalForm.values())
                .anyMatch(lf -> !lf.getShortLabel().isEmpty() && lf.getShortLabel().equalsIgnoreCase(shortTrimmed));
        if (builtIn || repository.findFirstByShortLabelIgnoreCase(shortTrimmed).isPresent()) {
            throw new BusinessRuleException("Форма «" + shortTrimmed + "» уже є в списку");
        }
        CustomLegalForm saved = repository.save(CustomLegalForm.builder()
                .shortLabel(shortTrimmed).fullLabel(fullTrimmed).build());
        auditLogService.record(AuditAction.CREATE, "CustomLegalForm", saved.getId(), FieldDiff.created(FieldDiff.snapshot()
                .add("Скорочення", saved.getShortLabel()).add("Повна назва форми", saved.getFullLabel())));
        return saved;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().replaceAll("\\s+", " ");
    }
}
