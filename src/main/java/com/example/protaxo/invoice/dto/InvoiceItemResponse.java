package com.example.protaxo.invoice.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.entity.CatalogUnit;
import com.example.protaxo.common.vat.VatRate;
import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        Integer lineNumber,
        Long catalogItemId,
        CatalogItemType catalogItemType,
        String itemName,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal discountPercent,
        BigDecimal amount,
        VatRate vatRate,
        BigDecimal vatAmount,
        BigDecimal amountWithoutVat,
        CatalogUnit catalogItemUnit
) {

    /** "шт" / "м" для товару; у послуги — порожньо. */
    public String unitLabel() {
        return catalogItemType == CatalogItemType.SERVICE ? "" : catalogItemUnit == null ? "шт" : catalogItemUnit.getLabel();
    }
}
