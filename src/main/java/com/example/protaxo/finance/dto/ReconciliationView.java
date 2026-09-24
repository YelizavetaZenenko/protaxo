package com.example.protaxo.finance.dto;

import com.example.protaxo.finance.entity.ReconciliationStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ReconciliationView(
        Long id,
        String accountName,
        Instant countedAt,
        BigDecimal expectedBalance,
        BigDecimal actualBalance,
        BigDecimal difference,
        ReconciliationStatus status,
        String comment,
        String createdByName,
        Instant resolvedAt,
        String resolvedByName,
        String resolutionComment
) {

    public String differenceLabel() {
        int sign = difference.signum();
        return sign == 0 ? "Збігається" : sign > 0 ? "Надлишок" : "Нестача";
    }
}
