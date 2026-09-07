package com.example.protaxo.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotNull Long clientId,
        @NotBlank String vin,
        @NotBlank String registrationNumber,
        @NotBlank String make,
        @NotBlank String model,
        @NotNull Integer year
) {
}
