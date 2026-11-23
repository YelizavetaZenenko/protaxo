package com.example.protaxo.catalog.dto;

import com.example.protaxo.catalog.entity.CatalogUnit;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.common.vat.VatRate;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record CatalogItemResponse(
        Long id,
        CatalogItemType type,
        String name,
        BigDecimal basePrice,
        BigDecimal stockQuantity,
        VatRate vatRate,
        BigDecimal purchasePrice,
        CatalogUnit unit,
        Long categoryId
) {

    /** "шт" / "м"; у послуг одиниці немає. */
    public String unitLabel() {
        return type == CatalogItemType.SERVICE || unit == null ? "" : unit.getLabel();
    }

    /** Ставка для колонки «Ціна з ПДВ 20%» у каталозі (продаж без ПДВ × 1,2). */
    private static final BigDecimal VAT_20 = new BigDecimal("1.20");

    /** Продаж задано (не порожній і не 0) — без нього різниця й ціна з ПДВ не мають сенсу. */
    private boolean hasSalePrice() {
        return basePrice != null && basePrice.signum() > 0;
    }

    /** Різниця між продажем і закупівлею (заробіток з одиниці); null, якщо однієї з цін немає. */
    public BigDecimal margin() {
        return hasSalePrice() && purchasePrice != null ? basePrice.subtract(purchasePrice) : null;
    }

    /** Націнка у відсотках від закупівлі: (продаж − закупівля) / закупівля × 100, ціле число. */
    public BigDecimal marginPercent() {
        BigDecimal margin = margin();
        if (margin == null || purchasePrice.signum() <= 0) {
            return null;
        }
        return margin.multiply(BigDecimal.valueOf(100)).divide(purchasePrice, 0, RoundingMode.HALF_UP);
    }

    /** Ціна продажу з ПДВ 20%: продаж × 1,2, до копійок. */
    public BigDecimal priceWithVat20() {
        return hasSalePrice() ? basePrice.multiply(VAT_20).setScale(2, RoundingMode.HALF_UP) : null;
    }

    /** "5" instead of "5.000", "2.5" instead of "2.500" — same pattern as BillItemRow#quantityDisplay. */
    public String stockQuantityDisplay() {
        return stockQuantity == null ? null : stockQuantity.stripTrailingZeros().toPlainString();
    }
}
