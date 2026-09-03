package com.example.protaxo.client.dto;

import com.example.protaxo.client.entity.PaymentType;

public record ClientResponse(
        Long id,
        String name,
        String edrpou,
        String contacts,
        PaymentType defaultPaymentType
) {
}
