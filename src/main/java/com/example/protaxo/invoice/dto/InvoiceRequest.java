package com.example.protaxo.invoice.dto;

import com.example.protaxo.invoice.entity.InvoicePaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
        @NotNull InvoicePaymentType paymentType,
        @NotNull Long clientId,
        String vehicleName,
        String driverName,
        String repairResponsibleName,
        String repairSupervisorName,
        @Valid List<InvoiceItemRequest> items,
        LocalDate paymentDueDate
) {
}
