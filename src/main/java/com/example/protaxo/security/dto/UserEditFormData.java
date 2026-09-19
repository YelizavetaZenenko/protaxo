package com.example.protaxo.security.dto;

import com.example.protaxo.security.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserEditFormData {

    @NotBlank(message = "Ім'я обов'язкове")
    private String fullName;

    @NotBlank(message = "Email обов'язковий")
    @Email(message = "Некоректний email")
    private String email;

    @NotNull(message = "Оберіть роль")
    private Role role;
}
