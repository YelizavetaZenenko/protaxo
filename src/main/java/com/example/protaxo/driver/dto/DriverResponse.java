package com.example.protaxo.driver.dto;

public record DriverResponse(
        Long id,
        Long clientId,
        String fullName,
        String phone,
        String position
) {
}
