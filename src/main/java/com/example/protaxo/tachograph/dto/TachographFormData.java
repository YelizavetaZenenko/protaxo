package com.example.protaxo.tachograph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class TachographFormData {

    // UI-only: не частина TachographRequest/сутності Tachograph (той самий зв'язок з
    // автомобілем лишається єдиним джерелом істини) — служить лише для звуження списку
    // автомобілів у формі до конкретного контрагента, див. TachographPageController.
    private Long clientId;

    @NotNull(message = "Оберіть автомобіль")
    private Long vehicleId;

    @NotBlank(message = "Виробник обов'язковий")
    private String manufacturer;

    @NotBlank(message = "Модель обов'язкова")
    private String model;

    @NotBlank(message = "Заводський номер обов'язковий")
    private String serialNumber;

    private LocalDate productionDate;
}
