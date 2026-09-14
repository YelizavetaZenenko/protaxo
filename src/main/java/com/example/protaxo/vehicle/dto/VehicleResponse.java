package com.example.protaxo.vehicle.dto;

public record VehicleResponse(
        Long id,
        Long clientId,
        String vin,
        String registrationNumber,
        String chassisNumber,
        String make,
        String model,
        Integer year
) {
}
