package com.example.protaxo.contract.dto;

import jakarta.validation.constraints.NotNull;

public record ContractRequest(
        @NotNull Long clientId
) {
}
