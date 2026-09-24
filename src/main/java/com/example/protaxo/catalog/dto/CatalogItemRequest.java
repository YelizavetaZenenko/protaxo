package com.example.protaxo.catalog.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.common.vat.VatRate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CatalogItemRequest(
        @NotNull CatalogItemType type,
        @NotBlank String name,
        @NotNull @DecimalMin("0.00") BigDecimal basePrice,
        @DecimalMin("0.000") BigDecimal stockQuantity,
        @NotNull VatRate vatRate
) {
}
