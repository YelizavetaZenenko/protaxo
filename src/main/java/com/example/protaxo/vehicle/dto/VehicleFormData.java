package com.example.protaxo.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleFormData {

    @NotNull(message = "Оберіть клієнта")
    private Long clientId;

    @NotBlank(message = "VIN обов'язковий")
    private String vin;

    @NotBlank(message = "Держномер обов'язковий")
    private String registrationNumber;

    @NotBlank(message = "Марка обов'язкова")
    private String make;

    @NotBlank(message = "Модель обов'язкова")
    private String model;

    @NotNull(message = "Рік випуску обов'язковий")
    private Integer year;

    /** Set only when arriving from a client's "+ Додати автомобіль" — sends the user back there after save. */
    private Long returnToClientId;
}
