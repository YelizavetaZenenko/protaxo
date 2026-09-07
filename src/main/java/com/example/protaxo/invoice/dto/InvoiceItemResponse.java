package com.example.protaxo.invoice.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        Integer lineNumber,
        Long catalogItemId,
        CatalogItemType catalogItemType,
        String itemName,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount
) {
}
