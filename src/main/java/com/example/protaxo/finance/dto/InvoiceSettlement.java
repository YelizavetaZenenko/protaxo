package com.example.protaxo.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Розрахунки за одним нарядом, обчислені з операцій (розд. 14: "сума оплат обчислюється з операцій").
 * {@code netPaid = paid − refunded}, {@code debt = total − netPaid}. Прострочення — окрема ознака,
 * не замінює стан оплати.
 */
public record InvoiceSettlement(
        Long invoiceId,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal refunded,
        BigDecimal netPaid,
        BigDecimal debt,
        SettlementStatus status,
        LocalDate dueDate,
        boolean overdue
) {

    public static InvoiceSettlement of(Long invoiceId, BigDecimal total, BigDecimal paid, BigDecimal refunded,
                                       LocalDate dueDate, LocalDate today) {
        BigDecimal netPaid = paid.subtract(refunded);
        BigDecimal debt = total.subtract(netPaid);
        SettlementStatus status;
        if (debt.signum() <= 0) {
            status = SettlementStatus.PAID;
        } else if (netPaid.signum() > 0) {
            status = SettlementStatus.PARTIALLY_PAID;
        } else {
            status = SettlementStatus.NOT_PAID;
        }
        boolean overdue = debt.signum() > 0 && dueDate != null && dueDate.isBefore(today);
        return new InvoiceSettlement(invoiceId, total, paid, refunded, netPaid, debt.max(BigDecimal.ZERO),
                status, dueDate, overdue);
    }
}
