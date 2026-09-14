package com.example.protaxo.calibration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MasterCardRequest(
        @NotBlank(message = "Номер картки обов'язковий")
        @Pattern(regexp = "^[A-Z]{3}[0-9]{12}$", message = "Формат: 3 великі латинські літери та 12 цифр")
        String cardNumber,
        @NotBlank(message = "Власник картки обов'язковий") String holderName
) {
}
