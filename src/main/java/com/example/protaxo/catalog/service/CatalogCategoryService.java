package com.example.protaxo.catalog.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.entity.CatalogCategory;
import com.example.protaxo.catalog.repository.CatalogCategoryRepository;
import com.example.protaxo.common.util.FieldDiff;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Довідник підкатегорій товарів (docs/Каталог.md). */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogCategoryService {

    private final CatalogCategoryRepository repository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CatalogCategory> findAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc();
    }

    /** id → назва, у порядку довідника. */
    @Transactional(readOnly = true)
    public Map<Long, String> names() {
        Map<Long, String> names = new LinkedHashMap<>();
        findAll().forEach(c -> names.put(c.getId(), c.getName()));
        return names;
    }

    @Transactional(readOnly = true)
    public String nameOf(Long id) {
        return id == null ? null : repository.findById(id).map(CatalogCategory::getName).orElse(null);
    }

    /** Нова категорія з форми товару; якщо така назва вже є (без урахування регістру) — наявна. */
    public Long findOrCreate(String rawName) {
        String cleaned = rawName.trim().replaceAll("\\s+", " ");
        String name = cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
        return repository.findFirstByNameIgnoreCase(name)
                .map(CatalogCategory::getId)
                .orElseGet(() -> create(name));
    }

    private Long create(String name) {
        CatalogCategory saved = repository.save(CatalogCategory.builder()
                .name(name)
                .sortOrder(repository.maxSortOrder() + 10)
                .build());
        auditLogService.record(AuditAction.CREATE, "CatalogCategory", saved.getId(),
                FieldDiff.created(FieldDiff.snapshot().add("Назва", name)));
        return saved.getId();
    }
}
