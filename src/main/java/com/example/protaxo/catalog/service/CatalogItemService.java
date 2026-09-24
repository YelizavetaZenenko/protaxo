package com.example.protaxo.catalog.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.dto.CatalogItemRequest;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.mapper.CatalogItemMapper;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.service.CurrentUserRoles;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogItemService {

    private final CatalogItemRepository catalogItemRepository;
    private final CatalogItemMapper catalogItemMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> findAll() {
        return catalogItemRepository.findAllByOrderByIdAsc().stream()
                .map(catalogItemMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse findById(Long id) {
        return catalogItemMapper.toResponse(getOrThrow(id));
    }

    public CatalogItemResponse create(CatalogItemRequest request) {
        if (CurrentUserRoles.has(Role.ACCOUNTANT)) {
            throw new BusinessRuleException("Бухгалтер може змінювати лише ціну, залишок і ПДВ наявних позицій");
        }
        CatalogItem catalogItem = catalogItemMapper.toEntity(request);
        clearStockForServices(catalogItem);
        CatalogItem saved = catalogItemRepository.save(catalogItem);
        auditLogService.record(AuditAction.CREATE, "CatalogItem", saved.getId());
        return catalogItemMapper.toResponse(saved);
    }

    public CatalogItemResponse update(Long id, CatalogItemRequest request) {
        CatalogItem catalogItem = getOrThrow(id);
        if (CurrentUserRoles.has(Role.ACCOUNTANT)) {
            // Бухгалтер змінює лише ціну, залишок і ПДВ — тип і назву бере з наявного запису,
            // навіть якщо форму підмінили.
            request = new CatalogItemRequest(catalogItem.getType(), catalogItem.getName(),
                    request.basePrice(), request.stockQuantity(), request.vatRate());
        }
        Map<String, String[]> changes = FieldDiff.builder()
                .add("Тип", catalogItem.getType(), request.type())
                .add("Назва", catalogItem.getName(), request.name())
                .add("Базова ціна", catalogItem.getBasePrice(), request.basePrice())
                .add("Залишок", catalogItem.getStockQuantity(), request.stockQuantity())
                .add("Ставка ПДВ", catalogItem.getVatRate() == null ? null : catalogItem.getVatRate().getLabel(),
                        request.vatRate() == null ? null : request.vatRate().getLabel())
                .build();
        catalogItemMapper.updateEntity(request, catalogItem);
        clearStockForServices(catalogItem);
        CatalogItem saved = catalogItemRepository.save(catalogItem);
        auditLogService.record(AuditAction.UPDATE, "CatalogItem", saved.getId(), changes);
        return catalogItemMapper.toResponse(saved);
    }

    /** Послуги не мають фізичного залишку — поле ігнорується на сервері, а не лише ховається в UI. */
    private void clearStockForServices(CatalogItem catalogItem) {
        if (catalogItem.getType() == CatalogItemType.SERVICE) {
            catalogItem.setStockQuantity(null);
        }
    }

    public void softDelete(Long id) {
        CatalogItem catalogItem = getOrThrow(id);
        catalogItem.setDeletedAt(Instant.now());
        catalogItemRepository.save(catalogItem);
        auditLogService.record(AuditAction.DELETE, "CatalogItem", id);
    }

    private CatalogItem getOrThrow(Long id) {
        return catalogItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CatalogItem %d not found".formatted(id)));
    }
}
