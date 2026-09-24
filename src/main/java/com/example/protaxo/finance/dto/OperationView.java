package com.example.protaxo.finance.dto;

import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.RefundKind;
import java.math.BigDecimal;
import java.time.Instant;

/** Операція для відображення (open-in-view вимкнено — шаблони не чіпають лінивих зв'язків). */
public record OperationView(
        Long id,
        FinanceOperationType type,
        Long accountId,
        String accountName,
        String targetAccountName,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Long invoiceId,
        String invoiceNumber,
        Long originalOperationId,
        BigDecimal receivedAmount,
        BigDecimal changeAmount,
        RefundKind refundKind,
        ExpenseCategory expenseCategory,
        String counterparty,
        String documentRef,
        String comment,
        Instant occurredAt,
        String createdByName,
        Instant cancelledAt,
        String cancelledByName,
        String cancelReason,
        Long attachmentId,
        String attachmentName,
        BigDecimal refundedAmount
) {

    public boolean cancelled() {
        return cancelledAt != null;
    }

    /** Скільки ще можна повернути з цієї оплати. */
    public BigDecimal refundableAmount() {
        return refundedAmount == null ? amount : amount.subtract(refundedAmount);
    }

    public boolean inflow() {
        return type.getSign() > 0;
    }
}
