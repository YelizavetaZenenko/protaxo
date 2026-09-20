package com.example.protaxo.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordFormData {

    @NotBlank(message = "Email обов'язковий")
    @Email(message = "Некоректний email")
    private String email;
}
