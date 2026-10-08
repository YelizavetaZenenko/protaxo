package com.example.protaxo.worker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RepairWorkerRequest(
        @NotBlank(message = "ПІБ обов'язкове") String fullName,
        @NotBlank(message = "Посада обов'язкова") String position,
        @Size(max = 50, message = "Телефон задовгий") String phone,
        @Email(message = "Некоректний e-mail") @Size(max = 255, message = "E-mail задовгий") String email,
        @Size(max = 500, message = "Адреса задовга") String address,
        LocalDate hireDate,
        @Size(max = 255, message = "Екстрений контакт задовгий") String emergencyContact,
        @Size(max = 2000, message = "Примітки задовгі") String notes
) {
}
