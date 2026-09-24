package com.example.protaxo.finance.dto;

import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.RefundKind;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Дані форм фінансового модуля. Перевірки — у FinanceService (одні правила для всіх входів),
 * тому тут без анотацій валідації. {@code requestKey} — одноразовий ключ, що генерується при
 * відкритті форми: повторна відправка тієї ж форми повертає вже збережену операцію, а не дубль.
 */
public final class FinanceForms {

    private FinanceForms() {
    }

    @Data
    public static class Payment {
        private Long invoiceId;
        private BigDecimal amount;
        private PaymentMethod method = PaymentMethod.CASH;
        private Long accountId;
        /** Лише для готівки: скільки клієнт дав купюрами (решта рахується автоматично). */
        private BigDecimal receivedAmount;
        private String comment;
        private String requestKey;
    }

    @Data
    public static class Refund {
        private BigDecimal amount;
        private RefundKind kind = RefundKind.ERRONEOUS_PAYMENT;
        private Long accountId;
        private String reason;
        private String requestKey;
    }

    @Data
    public static class Expense {
        private BigDecimal amount;
        private ExpenseCategory category;
        private String purpose;
        private String counterparty;
        private Long accountId;
        private String documentRef;
        /** Номер наряду, якщо витрата належить конкретній роботі. */
        private String invoiceNumber;
        private String requestKey;
    }

    @Data
    public static class Transfer {
        private Long fromAccountId;
        private Long toAccountId;
        private BigDecimal amount;
        private String comment;
        private String requestKey;
    }

    @Data
    public static class OwnerMovement {
        private FinanceOperationType type = FinanceOperationType.OWNER_WITHDRAWAL;
        private Long accountId;
        private BigDecimal amount;
        private String comment;
        private String requestKey;
    }

    @Data
    public static class Count {
        private Long accountId;
        private BigDecimal actualBalance;
        private String comment;
    }

    @Data
    public static class Resolve {
        private String resolutionComment;
        /** Провести коригувальну операцію на суму розбіжності (інакше — лише розглянути). */
        private boolean createAdjustment;
    }

    @Data
    public static class Account {
        private String name;
        private FinanceAccountKind kind = FinanceAccountKind.BANK;
        private BigDecimal openingBalance = BigDecimal.ZERO;
        private boolean active = true;
    }
}
