package com.example.protaxo.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DriverRequest(
        @NotNull Long clientId,
        @NotBlank(message = "ПІБ обов'язкове") String fullName,
        @NotBlank(message = "Телефон обов'язковий")
        @Pattern(regexp = "^\\+380\\d{9}$", message = "Телефон має бути у форматі +380XXXXXXXXX")
        String phone
) {
}
