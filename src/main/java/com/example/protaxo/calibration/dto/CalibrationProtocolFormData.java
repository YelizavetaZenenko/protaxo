package com.example.protaxo.calibration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class CalibrationProtocolFormData {

    @NotBlank(message = "Внутрішній номер обов'язковий")
    private String internalNumber;

    private String stampNumber;

    @NotNull(message = "Оберіть контрагента")
    private Long clientId;

    private String vehicleName;

    @NotBlank(message = "Номер картки обов'язковий")
    @Pattern(regexp = "^[A-Z]{3}[0-9]{12}$", message = "Формат: 3 великі латинські літери та 12 цифр")
    private String cardNumber;

    private String representativeName;

    private Long tachographId;

    private String tachographBrand;

    private String tachographModel;

    private String tachographType;

    private String tachographManufacturer;

    // Without ISO formatting the value is rendered locale-style (3/9/85) and <input type="date"> drops it.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate previousInspectionDate;

    private String vehicleVrn;

    private String vehicleVin;

    private String tachographSerialNumber;

    private String tachographManufactureYear;

    private String inspectionReason;

    private String checkMethod;

    private String mileageBefore;

    private String mileageAfter;

    private String tireSize;

    private String tirePressure;

    private String tireCircumferenceL;

    private String coefficientW;

    private String constantK;

    private String pathDeviationAfterInstall;

    private String pathDeviationInService;

    private String speedDeviationAfterInstall;

    private String speedDeviationInService;

    private String timeDeviationAfterInstall;

    private String timeDeviationInService;

    private String speedLimiterValue;

    private String coverOpeningRegistered;

    private String powerCutoffRegistered;

    private String pulseSensorInterruptionRegistered;

    private String executorPosition;

    private String executorName;

    private Long invoiceId;

    private String sealNumbers;
}
