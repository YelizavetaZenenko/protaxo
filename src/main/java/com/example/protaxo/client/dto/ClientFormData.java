package com.example.protaxo.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientFormData {

    @NotBlank(message = "Назва обов'язкова")
    private String name;

    @NotBlank(message = "Код ЄДРПОУ обов'язковий")
    @Pattern(regexp = "^\\d{8}$", message = "Код ЄДРПОУ має складатись рівно з 8 цифр")
    private String edrpou;

    @NotBlank(message = "Повна назва обов'язкова")
    private String fullName;

    @NotBlank(message = "Телефон обов'язковий")
    @Pattern(regexp = "^\\+38\\(0\\d{2}\\)-\\d{3}-\\d{2}-\\d{2}$", message = "Телефон має бути у форматі +38(0XX)-XXX-XX-XX")
    private String contactPersonPhone;

    @NotBlank(message = "Код ІПН обов'язковий")
    @Pattern(regexp = "^\\d{12}$", message = "Код ІПН має складатись рівно з 12 цифр")
    private String code;

    @NotBlank(message = "Прізвище обов'язкове")
    private String lastName;

    @NotBlank(message = "Ім'я обов'язкове")
    private String firstName;

    @NotBlank(message = "По батькові обов'язкове")
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

    /** «UA 83 3348…» з пробілами й малими літерами → «UA833348…», щоб вставлене з виписки проходило. */
    public void setIban(String iban) {
        this.iban = iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase();
    }

    public void setMfo(String mfo) {
        this.mfo = mfo == null ? null : mfo.replaceAll("\\s+", "");
    }
}
