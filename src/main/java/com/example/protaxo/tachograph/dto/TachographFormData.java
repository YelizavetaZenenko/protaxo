package com.example.protaxo.tachograph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class TachographFormData {

    @NotNull(message = "Оберіть автомобіль")
    private Long vehicleId;

    @NotBlank(message = "Виробник обов'язковий")
    private String manufacturer;

    @NotBlank(message = "Модель обов'язкова")
    private String model;

    private String firmwareVersion;

    @NotBlank(message = "Серійний номер обов'язковий")
    private String serialNumber;

    private LocalDate productionDate;
}
