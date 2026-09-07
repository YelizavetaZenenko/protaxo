package com.example.protaxo.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ClientFormData {

    @NotBlank(message = "Назва обов'язкова")
    private String name;

    private String edrpou;

    private String fullName;

    private String contactPersonPhone;

    @Pattern(regexp = "^(\\d{9})?$", message = "Код має складатись рівно з 9 цифр")
    private String code;

    private String lastName;

    private String firstName;

    private String middleName;

    @Email(message = "Некоректний email")
    private String email;
}
