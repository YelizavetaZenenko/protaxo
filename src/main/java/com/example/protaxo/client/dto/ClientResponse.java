package com.example.protaxo.client.dto;

import com.example.protaxo.client.entity.Gender;
import com.example.protaxo.client.entity.LegalForm;
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
        String addressPostalCode,
        LegalForm legalForm,
        Long customLegalFormId
) {

    /** ФОП — фізособа: у документах «РНОКПП», а не «Код ЄДРПОУ». */
    public boolean individual() {
        return legalForm != null && legalForm.isIndividual();
    }

    /** Підпис коду в документах: «Код ЄДРПОУ» для юрособи, «РНОКПП» для ФОП; без форми власності — за наявністю ЄДРПОУ. */
    public String taxIdLabel() {
        boolean legalEntity = legalForm != null && legalForm != LegalForm.OTHER
                ? !individual()
                : edrpou != null && !edrpou.isBlank();
        return legalEntity ? "Код ЄДРПОУ" : "РНОКПП";
    }

    /** Сам код до taxIdLabel(): ЄДРПОУ юрособи або РНОКПП (ІПН) ФОП. */
    public String taxId() {
        return "Код ЄДРПОУ".equals(taxIdLabel()) ? edrpou : (code != null && !code.isBlank() ? code : edrpou);
    }

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
