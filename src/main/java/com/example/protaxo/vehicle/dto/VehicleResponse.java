package com.example.protaxo.vehicle.dto;

public record VehicleResponse(
        Long id,
        Long clientId,
        String vin,
        String registrationNumber,
        String make,
        String model,
        Integer year
) {
}
