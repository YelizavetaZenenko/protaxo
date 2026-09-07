package com.example.protaxo.calibration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record CalibrationProtocolRequest(
        @NotBlank String internalNumber,
        String stampNumber,
        @NotNull Long clientId,
        String vehicleName,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}[0-9]{12}$", message = "Формат: 3 великі латинські літери та 12 цифр")
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
