package com.example.protaxo.calibration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MasterCardRequest(
        @NotBlank(message = "Номер картки обов'язковий")
        @Pattern(regexp = "^[A-Z]{2,3}[A-Z0-9]{10,14}$", message = "Формат: код країни (2–3 великі латинські літери) і ще 10–14 великих латинських літер або цифр, напр. UAM000000EOS3000")
        String cardNumber,
        @NotBlank(message = "Власник картки обов'язковий") String holderName
) {
}
