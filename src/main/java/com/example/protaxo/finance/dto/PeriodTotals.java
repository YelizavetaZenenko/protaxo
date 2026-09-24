package com.example.protaxo.finance.dto;

import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.PaymentMethod;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Обороти за період (на відміну від залишків — на момент). "Чистий рух коштів" — надходження
 * мінус виплати; це не прибуток (розд. 12 концепції).
 */
public record PeriodTotals(
        Map<PaymentMethod, BigDecimal> paymentsByMethod,
        Map<PaymentMethod, BigDecimal> refundsByMethod,
        BigDecimal payments,
        BigDecimal refunds,
        long paymentCount,
        Map<ExpenseCategory, BigDecimal> expensesByCategory,
        BigDecimal expenses,
        BigDecimal ownerDeposits,
        BigDecimal ownerWithdrawals
) {

    /** Для шаблонів: SpEL читає {@code map[m]} як ключ-рядок "m", тож доступ за способом — методом. */
    public BigDecimal paymentsBy(PaymentMethod method) {
        return paymentsByMethod.getOrDefault(method, BigDecimal.ZERO);
    }

    public BigDecimal netBy(PaymentMethod method) {
        return paymentsBy(method).subtract(refundsByMethod.getOrDefault(method, BigDecimal.ZERO));
    }

    public BigDecimal netRevenue() {
        return payments.subtract(refunds);
    }

    public BigDecimal netCashFlow() {
        return payments.add(ownerDeposits).subtract(refunds).subtract(expenses).subtract(ownerWithdrawals);
    }
}
