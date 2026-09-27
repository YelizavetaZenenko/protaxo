package com.example.protaxo.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** {@code discountPercent} — знижка на рядок, %; null означає без знижки. */
public record InvoiceItemRequest(
        @NotNull Long catalogItemId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent
) {

    public InvoiceItemRequest(Long catalogItemId, BigDecimal quantity, BigDecimal price) {
        this(catalogItemId, quantity, price, null);
    }
}
