package com.example.protaxo.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Один наряд, у який пішла позиція — деталізація звіту «Витрата товарів». */
public record CatalogUsageLine(
        Long invoiceId,
        String invoiceNumber,
        LocalDateTime documentDate,
        String clientName,
        String vehicleName,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount
) {
}
