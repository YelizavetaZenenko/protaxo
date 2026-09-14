package com.example.protaxo.contract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContractFormData {

    @NotNull(message = "Оберіть клієнта")
    private Long clientId;
}
