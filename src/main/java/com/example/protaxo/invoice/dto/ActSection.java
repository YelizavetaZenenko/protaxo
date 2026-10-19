package com.example.protaxo.invoice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One block of the act ("Надані матеріали" / "Надані послуги") with its own subtotals, as on the
 * user's sample act: Всього (без ПДВ, до знижки) → Знижка → ПДВ → Всього з ПДВ.
 */
public record ActSection(
        List<BillItemRow> rows,
        BigDecimal totalWithoutVat,
        BigDecimal discount,
        BigDecimal vat,
        BigDecimal totalWithVat
) {

    public static ActSection of(List<BillItemRow> rows) {
        BigDecimal gross = sum(rows.stream().map(BillItemRow::grossWithoutVat).toList());
        BigDecimal net = sum(rows.stream().map(BillItemRow::amountWithoutVat).toList());
        return new ActSection(rows, gross, gross.subtract(net),
                sum(rows.stream().map(BillItemRow::vatAmount).toList()),
                sum(rows.stream().map(BillItemRow::amount).toList()));
    }

    public boolean hasDiscount() {
        return discount.signum() != 0;
    }

    /** "ПДВ (20%)" when every row shares one rate, plain "ПДВ" for a mix. */
    public String vatLabel() {
        List<String> rates = rows.stream().map(BillItemRow::vatRateLabel).distinct().toList();
        return rates.size() == 1 && rates.get(0).endsWith("%") ? "ПДВ (" + rates.get(0) + ")" : "ПДВ";
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
