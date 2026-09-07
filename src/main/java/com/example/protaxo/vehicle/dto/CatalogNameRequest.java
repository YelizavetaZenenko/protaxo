package com.example.protaxo.vehicle.dto;

import jakarta.validation.constraints.NotBlank;

public record CatalogNameRequest(@NotBlank(message = "Назва обов'язкова") String name) {
}
