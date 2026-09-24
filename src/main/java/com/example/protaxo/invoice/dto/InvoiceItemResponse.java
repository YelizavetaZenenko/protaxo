package com.example.protaxo.invoice.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
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
        BigDecimal amount,
        VatRate vatRate,
        BigDecimal vatAmount,
        BigDecimal amountWithoutVat
) {
}
