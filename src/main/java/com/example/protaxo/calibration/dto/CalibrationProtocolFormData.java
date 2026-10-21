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

    // Поле вводиться текстом як дд.мм.рррр; ISO (рррр-мм-дд) приймається теж — так збережені
    // старі чернетки, коли тут був <input type="date">.
    @DateTimeFormat(pattern = "dd.MM.yyyy", fallbackPatterns = "yyyy-MM-dd")
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

    // Типові значення — стоять у новому протоколі одразу, їх можна змінити
    private String speedDeviationAfterInstall = "0.1";

    private String speedDeviationInService;

    private String timeDeviationAfterInstall = "0.1 хвилин за добу";

    private String timeDeviationInService = "0.7 хвилин за 7 діб";

    private String speedLimiterValue;

    private String coverOpeningRegistered;

    private String powerCutoffRegistered;

    private String pulseSensorInterruptionRegistered;

    private boolean verificationPlatePresent;

    private boolean markPresent;

    private boolean noManipulationDevices;

    private boolean sealsIntact;

    private String executorPosition;

    private String executorName;

    private Long invoiceId;

    private String sealNumbers;

    private String loadType;

    private String extGnss;

    private String gnssSerialNumber;

    private String dsrcSerialNumber;
}
