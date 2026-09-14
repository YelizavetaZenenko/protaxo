package com.example.protaxo.tachograph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TachographRequest(
        @NotNull Long vehicleId,
        @NotBlank String manufacturer,
        @NotBlank String model,
        @NotBlank String serialNumber,
        LocalDate productionDate
) {
}
