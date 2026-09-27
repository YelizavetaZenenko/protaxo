package com.example.protaxo.invoice.dto;

import java.math.BigDecimal;

public record BillItemRow(
        Integer lineNumber,
        String itemName,
        String unit,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal discountPercent,
        BigDecimal amountWithoutVat,
        String vatRateLabel,
        BigDecimal vatAmount,
        BigDecimal amount
) {

    /** "1" instead of "1.000", "0.5" instead of "0.500" — matches how quantities are printed on the sample invoice. */
    public String quantityDisplay() {
        return quantity.stripTrailingZeros().toPlainString();
    }

    public String discountDisplay() {
        return discountPercent == null || discountPercent.signum() == 0 ? "" : discountPercent.stripTrailingZeros().toPlainString() + "%";
    }
}
