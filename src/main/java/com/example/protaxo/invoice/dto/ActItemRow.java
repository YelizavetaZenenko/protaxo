package com.example.protaxo.invoice.dto;

import java.math.BigDecimal;

public record ActItemRow(
        Integer lineNumber,
        String itemName,
        String unit,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount
) {

    /** "1" instead of "1.000", "0.5" instead of "0.500" — same as BillItemRow#quantityDisplay. */
    public String quantityDisplay() {
        return quantity.stripTrailingZeros().toPlainString();
    }
}
