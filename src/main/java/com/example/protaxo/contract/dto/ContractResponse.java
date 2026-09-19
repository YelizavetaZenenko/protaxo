package com.example.protaxo.contract.dto;

import java.time.LocalDate;

public record ContractResponse(
        Long id,
        Long clientId,
        String contractNumber,
        LocalDate contractDate
) {
}
