package com.example.protaxo.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotNull Long clientId,
        @NotBlank @jakarta.validation.constraints.Size(max = 32, message = "VIN — до 32 символів") String vin,
        @NotBlank @jakarta.validation.constraints.Size(max = 20, message = "Держномер — до 20 символів") String registrationNumber,
        String chassisNumber,
        @NotBlank String make,
        @NotBlank String model,
        @NotNull Integer year
) {
}
