package com.example.protaxo.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DriverRequest(
        @NotNull Long clientId,
        @NotBlank(message = "ПІБ обов'язкове") String fullName,
        @NotBlank(message = "Телефон обов'язковий")
        @Pattern(regexp = "^\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2}$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
        String phone
) {
}
