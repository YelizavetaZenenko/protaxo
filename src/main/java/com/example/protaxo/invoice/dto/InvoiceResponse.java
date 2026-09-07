package com.example.protaxo.invoice.dto;

import com.example.protaxo.invoice.entity.InvoicePaymentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String number,
        LocalDateTime documentDate,
        InvoicePaymentType paymentType,
        Long clientId,
        String vehicleName,
        String driverName,
        String repairResponsibleName,
        String repairSupervisorName,
        String buyerOrderLabel,
        List<InvoiceItemResponse> items,
        BigDecimal totalAmount
) {
}
