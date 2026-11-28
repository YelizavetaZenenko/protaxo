package com.example.protaxo.client.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ClientFormData {

    /**
     * Форма власності зі списку ліворуч від назви (V78): ім'я вбудованої {@code LegalForm} («TOV», «FOP»)
     * або «C&lt;id&gt;» для своєї форми з довідника (V81); порожньо — назва як є.
     */
    private String legalForm;

    /** Назва без форми власності й лапок («Агро»; для ФОП — ПІБ). Коротку й повну складає контролер. */
    @NotBlank(message = "Назва обов'язкова")
    private String name;

    /** ФОП: те саме, що ІПН (10 цифр); необов'язковий, довжину перевіряє ClientPageController.checkCodes. */
    private String edrpou;

    /** Порожньо — складеться з форми власності й назви. */
    private String fullName;

    @NotBlank(message = "Телефон обов'язковий")
    @Pattern(regexp = "^\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2}$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
    private String contactPersonPhone;

    /** Необов'язковий. 12 цифр; у ФОП — 10 (РНОКПП). Довжину перевіряє ClientPageController за формою власності. */
    private String code;

    @NotBlank(message = "Прізвище обов'язкове")
    private String lastName;

    @NotBlank(message = "Ім'я обов'язкове")
    private String firstName;

    private String middleName;

    /** Необов'язковий (2026-11-22). */
    @Email(message = "Некоректний email")
    private String email;

    /** Платник ПДВ (docs/Фінансовий облік.md, розд. 5). */
    private boolean vatPayer;

    // Реквізити для договору — усі необов'язкові
    @Size(max = 500, message = "Адреса — до 500 символів")
    private String legalAddress;

    /** Пробіли й регістр прибирає ClientPageController перед перевіркою. */
    @Pattern(regexp = "^(UA\\d{27})?$", message = "IBAN має бути у форматі UA + 27 цифр")
    private String iban;

    @Size(max = 255, message = "Назва банку — до 255 символів")
    private String bankName;

    @Pattern(regexp = "^(\\d{6})?$", message = "МФО має складатись з 6 цифр")
    private String mfo;

    @Size(max = 100, message = "Посада — до 100 символів")
    private String signatoryPosition;

    @Size(max = 255, message = "До 255 символів")
    private String signatoryBasis;

    // Юридична адреса частинами (V73)
    private String addressSettlementCode;

    @Size(max = 255, message = "Вулиця — до 255 символів")
    private String addressStreet;

    @Size(max = 30, message = "Будинок — до 30 символів")
    private String addressBuilding;

    @Size(max = 30, message = "Офіс / квартира — до 30 символів")
    private String addressOffice;

    @Pattern(regexp = "^(\\d{5})?$", message = "Індекс має складатись з 5 цифр")
    private String addressPostalCode;

    /** Інші поточні рахунки, крім основного (V77). */
    @Valid
    private List<ClientBankAccountData> extraAccounts = new ArrayList<>();

    /** «UA 83 3348…» з пробілами й малими літерами → «UA833348…», щоб вставлене з виписки проходило. */
    public void setIban(String iban) {
        this.iban = iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase();
    }

    public void setMfo(String mfo) {
        this.mfo = mfo == null ? null : mfo.replaceAll("\\s+", "");
    }
}
