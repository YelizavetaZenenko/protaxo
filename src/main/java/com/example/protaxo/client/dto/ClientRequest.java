package com.example.protaxo.client.dto;

import com.example.protaxo.client.entity.Gender;
import com.example.protaxo.client.entity.LegalForm;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ClientRequest(
        @NotBlank String name,
        @Pattern(regexp = "^(\\d{8}|\\d{10})?$", message = "Код ЄДРПОУ — 8 цифр (у ФОП — 10, як ІПН)")
        String edrpou,
        String fullName,
        String contactPersonName,
        @Pattern(regexp = "^(\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2})?$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
        String contactPersonPhone,
        @Pattern(regexp = "^(\\d{10}|\\d{12})?$", message = "Код ІПН — 12 цифр (у ФОП — 10)")
        String code,
        String lastName,
        String firstName,
        String middleName,
        LocalDate birthDate,
        Gender gender,
        Long employerClientId,
        String position,
        @Pattern(regexp = "^(\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2})?$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
        String phone,
        @Email(message = "Некоректний email") String email,
        boolean vatPayer,
        @Size(max = 500) String legalAddress,
        @Pattern(regexp = "^(UA\\d{27})?$", message = "IBAN має бути у форматі UA + 27 цифр")
        String iban,
        @Size(max = 255) String bankName,
        @Pattern(regexp = "^(\\d{6})?$", message = "МФО має складатись з 6 цифр")
        String mfo,
        @Size(max = 100) String signatoryPosition,
        @Size(max = 255) String signatoryBasis,
        @Size(max = 19) String addressSettlementCode,
        @Size(max = 255) String addressStreet,
        @Size(max = 30) String addressBuilding,
        @Size(max = 30) String addressOffice,
        @Pattern(regexp = "^(\\d{5})?$", message = "Індекс має складатись з 5 цифр")
        String addressPostalCode,
        LegalForm legalForm
) {
}
