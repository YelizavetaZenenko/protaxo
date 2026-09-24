package com.example.protaxo.finance.entity;

import com.example.protaxo.common.entity.BaseEntity;
import com.example.protaxo.invoice.entity.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Один рух коштів. Не видаляється: скасування (сторно) заповнює {@code cancelled*} з причиною,
 * після чого операція не впливає на залишки й борги, але лишається в історії (розд. 14 концепції).
 */
@Entity
@Table(name = "finance_operations")
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"account", "targetAccount", "invoice", "originalOperation", "reconciliation"})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FinanceOperation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinanceOperationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private FinanceAccount account;

    /** Лише для TRANSFER — куди переміщено кошти. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private FinanceAccount targetAccount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    /** Для PAYMENT/REFUND; для EXPENSE — якщо витрата належить конкретній роботі. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /** Для REFUND — оплата, яку повертають. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_operation_id")
    private FinanceOperation originalOperation;

    /** Для ADJUSTMENT_* — звірка, за якою зроблено коригування. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id")
    private Reconciliation reconciliation;

    /** Готівкова оплата: скільки клієнт дав купюрами. Виручка — {@code amount}, не це поле. */
    @Column(name = "received_amount", precision = 14, scale = 2)
    private BigDecimal receivedAmount;

    @Column(name = "change_amount", precision = 14, scale = 2)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_kind")
    private RefundKind refundKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category")
    private ExpenseCategory expenseCategory;

    private String counterparty;

    @Column(name = "document_ref")
    private String documentRef;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /** Ключ з форми: повторна відправка тієї самої форми не створює дубль (критерій 8). */
    @Column(name = "request_key", nullable = false, unique = true, length = 64)
    private String requestKey;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** Знакова зміна залишку рахунку {@code accountId} (0, якщо операція його не стосується або скасована). */
    public BigDecimal effectOn(Long accountId) {
        if (isCancelled()) {
            return BigDecimal.ZERO;
        }
        BigDecimal result = BigDecimal.ZERO;
        if (account.getId().equals(accountId)) {
            result = result.add(type.getSign() > 0 ? amount : amount.negate());
        }
        if (targetAccount != null && targetAccount.getId().equals(accountId)) {
            result = result.add(amount);
        }
        return result;
    }
}
