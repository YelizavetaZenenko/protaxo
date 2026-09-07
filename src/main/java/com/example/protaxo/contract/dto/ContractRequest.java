package com.example.protaxo.contract.dto;

import com.example.protaxo.contract.entity.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContractRequest(
        @NotNull Long clientId,
        @NotBlank String contractNumber,
        @NotNull ContractStatus status
) {
}
