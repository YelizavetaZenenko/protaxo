package com.example.protaxo.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptInviteFormData {

    @NotBlank(message = "Пароль обов'язковий")
    @Size(min = 8, message = "Пароль має містити щонайменше 8 символів")
    private String password;

    @NotBlank(message = "Підтвердіть пароль")
    private String confirmPassword;
}
