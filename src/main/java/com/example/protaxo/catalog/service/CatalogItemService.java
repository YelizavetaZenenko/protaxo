package com.example.protaxo.catalog.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.dto.CatalogItemRequest;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.mapper.CatalogItemMapper;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
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
        return catalogItemRepository.findAll().stream()
                .map(catalogItemMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse findById(Long id) {
        return catalogItemMapper.toResponse(getOrThrow(id));
    }

    public CatalogItemResponse create(CatalogItemRequest request) {
        CatalogItem catalogItem = catalogItemMapper.toEntity(request);
        CatalogItem saved = catalogItemRepository.save(catalogItem);
        auditLogService.record(AuditAction.CREATE, "CatalogItem", saved.getId());
        return catalogItemMapper.toResponse(saved);
    }

    public CatalogItemResponse update(Long id, CatalogItemRequest request) {
        CatalogItem catalogItem = getOrThrow(id);
        catalogItemMapper.updateEntity(request, catalogItem);
        CatalogItem saved = catalogItemRepository.save(catalogItem);
        auditLogService.record(AuditAction.UPDATE, "CatalogItem", saved.getId());
        return catalogItemMapper.toResponse(saved);
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
