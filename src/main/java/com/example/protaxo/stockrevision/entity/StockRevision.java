package com.example.protaxo.stockrevision.entity;

import com.example.protaxo.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/** Ревізія складу (docs/Ревізія складу.md). Проведена ревізія більше не редагується. */
@Entity
@Table(name = "stock_revisions")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = "items")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StockRevision extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockRevisionStatus status;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private String completedBy;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemName ASC")
    @Builder.Default
    private List<StockRevisionItem> items = new ArrayList<>();
}
