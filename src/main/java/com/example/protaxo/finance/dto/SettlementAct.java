package com.example.protaxo.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Акт звірки взаєморозрахунків з контрагентом за період — дані з боку ФОП. Дебет — що
 * контрагент винен (наряди/акти, повернення йому коштів), кредит — що він заплатив.
 * Сальдо додатне — борг контрагента, від'ємне — переплата (борг ФОП перед контрагентом).
 */
public record SettlementAct(
        Long clientId,
        String clientName,
        LocalDate from,
        LocalDate to,
        String contractNumber,
        LocalDate contractDate,
        BigDecimal openingBalance,
        List<Row> rows,
        BigDecimal debitTurnover,
        BigDecimal creditTurnover,
        BigDecimal closingBalance
) {

    public record Row(LocalDate date, String document, BigDecimal debit, BigDecimal credit) {
    }

    public BigDecimal openingDebit() {
        return openingBalance.signum() > 0 ? openingBalance : BigDecimal.ZERO;
    }

    public BigDecimal openingCredit() {
        return openingBalance.signum() < 0 ? openingBalance.negate() : BigDecimal.ZERO;
    }

    public BigDecimal closingDebit() {
        return closingBalance.signum() > 0 ? closingBalance : BigDecimal.ZERO;
    }

    public BigDecimal closingCredit() {
        return closingBalance.signum() < 0 ? closingBalance.negate() : BigDecimal.ZERO;
    }

    /** Модуль кінцевого сальдо — сума заборгованості для висновку акта. */
    public BigDecimal closingAmount() {
        return closingBalance.abs();
    }
}
