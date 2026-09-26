package com.example.protaxo.catalog.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
import java.math.BigDecimal;

/**
 * Рядок звіту «Витрата товарів» (docs/Витрата товарів.md): скільки позиції пішло в наряди за
 * період, коригування ревізіями за той самий період і поточний залишок.
 */
public record CatalogUsageRow(
        Long catalogItemId,
        String name,
        CatalogItemType type,
        BigDecimal usedQuantity,
        BigDecimal usedAmount,
        long invoiceCount,
        BigDecimal revisionAdjustment,
        BigDecimal currentStock,
        boolean deleted
) {

    public static String qty(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}
