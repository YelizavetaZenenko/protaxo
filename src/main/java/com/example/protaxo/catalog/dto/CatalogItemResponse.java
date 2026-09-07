package com.example.protaxo.catalog.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
import java.math.BigDecimal;

public record CatalogItemResponse(
        Long id,
        CatalogItemType type,
        String name,
        BigDecimal basePrice,
        BigDecimal stockQuantity
) {
}
