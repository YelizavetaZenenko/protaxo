package com.example.protaxo.finance.entity;

import com.example.protaxo.common.entity.BaseEntity;
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
 * Звірка готівки — внутрішня перевірка, не Z-звіт (розд. 10 концепції). Внесення фактичної суми
 * НЕ змінює залишок: розбіжність лишається відкритою, доки бухгалтер її не розгляне; коригування,
 * якщо потрібне, — окрема операція ADJUSTMENT_* з посиланням на звірку.
 */
@Entity
@Table(name = "finance_reconciliations")
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = "account")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Reconciliation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private FinanceAccount account;

    @Column(name = "counted_at", nullable = false)
    private Instant countedAt;

    @Column(name = "expected_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedBalance;

    @Column(name = "actual_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualBalance;

    /** фактична готівка − залишок за програмою: > 0 надлишок, < 0 нестача. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationStatus status;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_comment", columnDefinition = "text")
    private String resolutionComment;
}
