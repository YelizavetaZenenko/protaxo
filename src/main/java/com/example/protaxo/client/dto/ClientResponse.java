package com.example.protaxo.client.dto;

import com.example.protaxo.client.entity.Gender;
import java.time.LocalDate;

public record ClientResponse(
        Long id,
        String name,
        String edrpou,
        String fullName,
        String contactPersonName,
        String contactPersonPhone,
        String code,
        String lastName,
        String firstName,
        String middleName,
        LocalDate birthDate,
        Gender gender,
        Long employerClientId,
        String position,
        String phone,
        String email
) {
}
