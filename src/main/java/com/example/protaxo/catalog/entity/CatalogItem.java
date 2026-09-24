package com.example.protaxo.catalog.entity;

import com.example.protaxo.common.entity.BaseEntity;
import com.example.protaxo.common.vat.VatRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "catalog_items")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CatalogItem extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatalogItemType type;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "stock_quantity", precision = 12, scale = 3)
    private BigDecimal stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_rate", nullable = false)
    @Builder.Default
    private VatRate vatRate = VatRate.VAT_20;
}
