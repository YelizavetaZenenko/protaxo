package com.example.protaxo.contract.dto;

import com.example.protaxo.contract.entity.ContractStatus;

public record ContractResponse(
        Long id,
        Long clientId,
        String contractNumber,
        ContractStatus status
) {
}
