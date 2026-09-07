package com.example.protaxo.invoice.dto;

import java.time.LocalDateTime;

public record VehiclePickerRow(
        Long id,
        String vin,
        String registrationNumber,
        String make,
        String model,
        Integer year,
        LocalDateTime lastVisitDate
) {
}
