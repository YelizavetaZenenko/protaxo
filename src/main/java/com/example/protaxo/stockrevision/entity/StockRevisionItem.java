package com.example.protaxo.stockrevision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Рядок ревізії. {@code catalogItemId} — просте поле, не зв'язок: позицію каталогу могли м'яко
 * видалити після створення чернетки, а рядок має лишитись читабельним.
 */
@Entity
@Table(name = "stock_revision_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "revision")
public class StockRevisionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    private StockRevision revision;

    @Column(name = "catalog_item_id", nullable = false)
    private Long catalogItemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    /** За обліком: у чернетці — на момент створення, у проведеній — на момент проведення. */
    @Column(name = "expected_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQuantity;

    /** Фактично перераховано; null — позицію не рахували, її залишок не змінюється. */
    @Column(name = "actual_quantity", precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    /** фактично − за обліком: > 0 надлишок, < 0 нестача. Заповнюється під час проведення. */
    @Column(precision = 12, scale = 3)
    private BigDecimal difference;

    /** Ціна з каталогу на момент проведення — для оцінки нестачі/надлишку в гривнях. */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private String comment;
}
