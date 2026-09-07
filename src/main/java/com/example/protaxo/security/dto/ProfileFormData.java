package com.example.protaxo.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProfileFormData {

    @NotBlank(message = "Ім'я обов'язкове")
    private String fullName;

    private String currentPassword;

    private String newPassword;

    private String confirmNewPassword;
}
