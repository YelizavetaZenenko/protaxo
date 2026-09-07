package com.example.protaxo.calibration.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CalibrationProtocolResponse(
        Long id,
        String protocolNumber,
        LocalDateTime protocolDate,
        String internalNumber,
        String stampNumber,
        Long clientId,
        String vehicleName,
        String orderLabel,
        String cardNumber,
        String representativeName,
        String tachographBrand,
        String tachographModel,
        String tachographType,
        String tachographManufacturer,
        LocalDate previousInspectionDate,
        Long invoiceId
) {
}
