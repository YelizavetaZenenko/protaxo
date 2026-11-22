package com.example.protaxo.bank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BankRequest(
        @NotBlank(message = "Вкажіть назву банку") @Size(max = 255, message = "Назва — до 255 символів") String name,
        @Pattern(regexp = "^(\\d{6})?$", message = "МФО має складатись з 6 цифр") String mfo,
        @Pattern(regexp = "^(\\d{8})?$", message = "Код ЄДРПОУ має складатись з 8 цифр") String edrpou
) {
}
