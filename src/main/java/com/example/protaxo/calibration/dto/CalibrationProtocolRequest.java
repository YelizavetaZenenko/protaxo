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
        Long tachographId,
        String tachographBrand,
        String tachographModel,
        String tachographType,
        String tachographManufacturer,
        LocalDate previousInspectionDate,
        String vehicleVrn,
        String vehicleVin,
        String tachographSerialNumber,
        String tachographManufactureYear,
        String inspectionReason,
        String checkMethod,
        String mileageBefore,
        String mileageAfter,
        String tireSize,
        String tirePressure,
        String tireCircumferenceL,
        String coefficientW,
        String constantK,
        String pathDeviationAfterInstall,
        String pathDeviationInService,
        String speedDeviationAfterInstall,
        String speedDeviationInService,
        String timeDeviationAfterInstall,
        String timeDeviationInService,
        String speedLimiterValue,
        String coverOpeningRegistered,
        String powerCutoffRegistered,
        String pulseSensorInterruptionRegistered,
        String executorPosition,
        String executorName,
        Long invoiceId,
        String sealNumbers
) {
}
