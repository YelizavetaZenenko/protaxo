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
        String email,
        boolean vatPayer,
        String legalAddress,
        String iban,
        String bankName,
        String mfo,
        String signatoryPosition,
        String signatoryBasis,
        String addressSettlementCode,
        String addressStreet,
        String addressBuilding,
        String addressOffice,
        String addressPostalCode
) {

    /**
     * Хто підписує договір від замовника: «Ім'я контактної особи», якщо задане, інакше ПІБ
     * контактної особи з форми (прізвище, ім'я, по батькові).
     */
    public String signatoryName() {
        if (contactPersonName != null && !contactPersonName.isBlank()) {
            return contactPersonName.trim();
        }
        String full = String.join(" ", blankToEmpty(lastName), blankToEmpty(firstName), blankToEmpty(middleName))
                .trim().replaceAll("\\s+", " ");
        return full.isEmpty() ? null : full;
    }

    /** «Іваненко П.В.» — під підписом, як у Виконавця. */
    public String signatoryShortName() {
        if (lastName != null && !lastName.isBlank()) {
            return lastName.trim() + " " + initial(firstName) + initial(middleName);
        }
        return signatoryName();
    }

    /** Телефон для реквізитів: телефон контрагента, інакше телефон контактної особи. */
    public String requisitesPhone() {
        return phone != null && !phone.isBlank() ? phone : contactPersonPhone;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String initial(String value) {
        return value == null || value.isBlank() ? "" : value.trim().substring(0, 1).toUpperCase() + ".";
    }
}
