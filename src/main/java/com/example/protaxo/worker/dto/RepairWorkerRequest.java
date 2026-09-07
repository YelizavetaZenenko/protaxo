package com.example.protaxo.worker.dto;

import jakarta.validation.constraints.NotBlank;

public record RepairWorkerRequest(
        @NotBlank(message = "ПІБ обов'язкове") String fullName,
        @NotBlank(message = "Посада обов'язкова") String position
) {
}
