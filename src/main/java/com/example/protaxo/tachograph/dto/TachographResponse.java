package com.example.protaxo.tachograph.dto;

import java.time.LocalDate;

public record TachographResponse(
        Long id,
        Long vehicleId,
        String manufacturer,
        String model,
        String firmwareVersion,
        String serialNumber,
        LocalDate productionDate
) {
}
