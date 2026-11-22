package com.example.protaxo.bank;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.util.FieldDiff;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Довідник банків (docs/Контрагенти.md). */
@Service
@RequiredArgsConstructor
@Transactional
public class BankService {

    private final BankRepository repository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Bank> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    /** Новий банк із модального вікна форми контрагента; МФО й назва не можуть повторюватись. */
    public Bank create(BankRequest request) {
        String name = request.name().trim().replaceAll("\\s+", " ");
        String mfo = blankToNull(request.mfo());
        String edrpou = blankToNull(request.edrpou());
        if (mfo != null) {
            repository.findFirstByMfo(mfo).ifPresent(existing -> {
                throw new BusinessRuleException("Банк з МФО " + mfo + " уже є: " + existing.getName());
            });
        }
        repository.findFirstByNameIgnoreCase(name).ifPresent(existing -> {
            throw new BusinessRuleException("Банк «" + existing.getName() + "» уже є в списку");
        });
        Bank saved = repository.save(Bank.builder().name(name).mfo(mfo).edrpou(edrpou).source("USER").build());
        auditLogService.record(AuditAction.CREATE, "Bank", saved.getId(), FieldDiff.created(FieldDiff.snapshot()
                .add("Назва", name).add("МФО", mfo).add("Код ЄДРПОУ", edrpou)));
        return saved;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.replaceAll("\\s+", "");
    }
}
