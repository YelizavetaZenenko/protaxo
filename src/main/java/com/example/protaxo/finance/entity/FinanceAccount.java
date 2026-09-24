package com.example.protaxo.finance.entity;

import com.example.protaxo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/** Каса або рахунок (docs/Фінансовий облік.md, розд. 5, 7). Кас майстрів немає — уся готівка в загальній касі. */
@Entity
@Table(name = "finance_accounts")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FinanceAccount extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinanceAccountKind kind;

    /** Залишок на момент початку обліку в програмі — не оплата від клієнта. */
    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private boolean active;
}
