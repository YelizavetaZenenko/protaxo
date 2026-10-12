package com.example.protaxo.finance.dto;

import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.RefundKind;
import com.example.protaxo.finance.entity.TaxSystem;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Дані форм фінансового модуля. Перевірки — у FinanceService (одні правила для всіх входів),
 * тому тут без анотацій валідації. {@code requestKey} — одноразовий ключ, що генерується при
 * відкритті форми: повторна відправка тієї ж форми повертає вже збережену операцію, а не дубль.
 * {@code Serializable}: при помилці форма повертається через flash-атрибут, тобто кладеться в сесію,
 * а сесії зберігаються в Postgres (Spring Session JDBC) — серіалізуються.
 */
public final class FinanceForms {

    private FinanceForms() {
    }

    @Data
    public static class Payment implements Serializable {
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
    public static class Refund implements Serializable {
        private BigDecimal amount;
        private RefundKind kind = RefundKind.ERRONEOUS_PAYMENT;
        private Long accountId;
        private String reason;
        private String requestKey;
    }

    @Data
    public static class Expense implements Serializable {
        private BigDecimal amount;
        private ExpenseCategory category;
        private String purpose;
        private String counterparty;
        private Long accountId;
        private String documentRef;
        /** Номер наряду, якщо витрата належить конкретній роботі. */
        private String invoiceNumber;
        private String requestKey;
        /** Лише для «Запчастини та матеріали»: закуплені товари — оприбутковуються на склад. */
        private List<ExpenseLine> items = new ArrayList<>();
    }

    /**
     * Рядок закупівлі: або наявна позиція каталогу ({@code catalogItemId}), або нова
     * ({@code newItemName} + {@code newItemSalePrice}) — її буде створено в каталозі.
     */
    @Data
    public static class ExpenseLine implements Serializable {
        private Long catalogItemId;
        private String newItemName;
        private BigDecimal newItemSalePrice;
        private BigDecimal quantity;
        private BigDecimal purchasePrice;

        public boolean isBlank() {
            return catalogItemId == null && (newItemName == null || newItemName.isBlank())
                    && quantity == null && purchasePrice == null;
        }
    }

    @Data
    public static class Transfer implements Serializable {
        private Long fromAccountId;
        private Long toAccountId;
        private BigDecimal amount;
        private String comment;
        private String requestKey;
    }

    @Data
    public static class OwnerMovement implements Serializable {
        private FinanceOperationType type = FinanceOperationType.OWNER_WITHDRAWAL;
        private Long accountId;
        private BigDecimal amount;
        private String comment;
        private String requestKey;
    }

    @Data
    public static class Count implements Serializable {
        private Long accountId;
        private BigDecimal actualBalance;
        private String comment;
    }

    @Data
    public static class Resolve implements Serializable {
        private String resolutionComment;
        /** Провести коригувальну операцію на суму розбіжності (інакше — лише розглянути). */
        private boolean createAdjustment;
    }

    @Data
    public static class Account implements Serializable {
        private String name;
        private FinanceAccountKind kind = FinanceAccountKind.BANK;
        private BigDecimal openingBalance = BigDecimal.ZERO;
        private boolean active = true;
    }

    @Data
    public static class Settings implements Serializable {
        private String businessName;
        private TaxSystem taxSystem;
        private boolean vatPayer;
    }
}
