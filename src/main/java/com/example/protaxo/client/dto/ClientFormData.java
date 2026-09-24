package com.example.protaxo.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ClientFormData {

    @NotBlank(message = "Назва обов'язкова")
    private String name;

    @NotBlank(message = "Код ЄДРПОУ обов'язковий")
    @Pattern(regexp = "^\\d{8}$", message = "Код ЄДРПОУ має складатись рівно з 8 цифр")
    private String edrpou;

    @NotBlank(message = "Повна назва обов'язкова")
    private String fullName;

    @NotBlank(message = "Телефон обов'язковий")
    @Pattern(regexp = "^\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2}$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
    private String contactPersonPhone;

    @NotBlank(message = "Код ІПН обов'язковий")
    @Pattern(regexp = "^\\d{12}$", message = "Код ІПН має складатись рівно з 12 цифр")
    private String code;

    @NotBlank(message = "Прізвище обов'язкове")
    private String lastName;

    @NotBlank(message = "Ім'я обов'язкове")
    private String firstName;

    @NotBlank(message = "По батькові обов'язкове")
    private String middleName;

    @NotBlank(message = "Email обов'язковий")
    @Email(message = "Некоректний email")
    private String email;

    /** Платник ПДВ (docs/Фінансовий облік.md, розд. 5). */
    private boolean vatPayer;
}
