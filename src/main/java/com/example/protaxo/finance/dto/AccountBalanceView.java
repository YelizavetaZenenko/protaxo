package com.example.protaxo.finance.dto;

import com.example.protaxo.finance.entity.FinanceAccountKind;
import java.math.BigDecimal;

public record AccountBalanceView(
        Long id,
        String name,
        FinanceAccountKind kind,
        boolean active,
        BigDecimal openingBalance,
        BigDecimal balance
) {
}
