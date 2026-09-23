package com.example.protaxo.client.dto;

import com.example.protaxo.client.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record ClientRequest(
        @NotBlank String name,
        @Pattern(regexp = "^(\\d{8})?$", message = "Код ЄДРПОУ має складатись рівно з 8 цифр")
        String edrpou,
        String fullName,
        String contactPersonName,
        @Pattern(regexp = "^(\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2})?$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
        String contactPersonPhone,
        @Pattern(regexp = "^(\\d{12})?$", message = "Код ІПН має складатись рівно з 12 цифр")
        String code,
        String lastName,
        String firstName,
        String middleName,
        LocalDate birthDate,
        Gender gender,
        Long employerClientId,
        String position,
        @Pattern(regexp = "^(\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2})?$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
        String phone,
        @Email(message = "Некоректний email") String email
) {
}
