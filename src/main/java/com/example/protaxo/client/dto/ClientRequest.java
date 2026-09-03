package com.example.protaxo.client.dto;

import com.example.protaxo.client.entity.PaymentType;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank String name,
        String edrpou,
        String contacts,
        PaymentType defaultPaymentType
) {
}
