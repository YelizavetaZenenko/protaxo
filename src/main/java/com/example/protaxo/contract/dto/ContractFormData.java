package com.example.protaxo.contract.dto;

import com.example.protaxo.contract.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContractFormData {

    @NotNull(message = "Оберіть клієнта")
    private Long clientId;

    @NotBlank(message = "Номер договору обов'язковий")
    private String contractNumber;

    @NotNull(message = "Оберіть статус")
    private ContractStatus status;
}
