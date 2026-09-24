package com.example.protaxo.stockrevision.dto;

import com.example.protaxo.stockrevision.entity.StockRevisionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StockRevisionView(
        Long id,
        StockRevisionStatus status,
        String comment,
        Instant createdAt,
        String createdByName,
        Instant completedAt,
        String completedByName,
        List<Line> lines
) {

    public record Line(
            Long id,
            Long catalogItemId,
            String itemName,
            BigDecimal expectedQuantity,
            BigDecimal actualQuantity,
            BigDecimal difference,
            BigDecimal unitPrice,
            String comment
    ) {

        public boolean counted() {
            return actualQuantity != null;
        }

        public boolean hasDifference() {
            return difference != null && difference.signum() != 0;
        }

        /** Різниця в гривнях за ціною каталогу (знак як у difference). */
        public BigDecimal differenceValue() {
            return difference == null || unitPrice == null ? BigDecimal.ZERO : difference.multiply(unitPrice);
        }
    }

    public boolean draft() {
        return status == StockRevisionStatus.DRAFT;
    }

    public long countedLines() {
        return lines.stream().filter(Line::counted).count();
    }

    public long discrepancyLines() {
        return lines.stream().filter(Line::hasDifference).count();
    }

    public BigDecimal shortageValue() {
        return lines.stream().map(Line::differenceValue).filter(v -> v.signum() < 0)
                .map(BigDecimal::negate).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal surplusValue() {
        return lines.stream().map(Line::differenceValue).filter(v -> v.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
