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
        BigDecimal purchaseAmount,
        boolean purchaseComplete,
        BigDecimal purchasePrice,
        long invoiceCount,
        BigDecimal revisionAdjustment,
        BigDecimal currentStock,
        boolean deleted
) {

    /** Середня ціна продажу одиниці за період (з урахуванням знижок). */
    public BigDecimal averageSalePrice() {
        return usedQuantity == null || usedQuantity.signum() == 0 ? null
                : usedAmount.divide(usedQuantity, 2, java.math.RoundingMode.HALF_UP);
    }

    /** Різниця між продажем і закупівлею; null, якщо закупівельна ціна відома не для всіх продажів. */
    public BigDecimal margin() {
        return purchaseComplete && purchaseAmount != null ? usedAmount.subtract(purchaseAmount) : null;
    }

    public static String qty(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}
