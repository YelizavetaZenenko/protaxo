package com.example.protaxo.client.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Рядок «Інші рахунки» у формі контрагента (V77). Рядок без IBAN не зберігається. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientBankAccountData {

    @Pattern(regexp = "^(UA\\d{27})?$", message = "IBAN має бути у форматі UA + 27 цифр")
    private String iban;

    @Size(max = 255, message = "Назва банку — до 255 символів")
    private String bankName;

    @Pattern(regexp = "^(\\d{6})?$", message = "МФО має складатись з 6 цифр")
    private String mfo;

    /** «UA 83 3348…» з пробілами й малими літерами → «UA833348…». */
    public void setIban(String iban) {
        this.iban = iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase();
    }

    public void setMfo(String mfo) {
        this.mfo = mfo == null ? null : mfo.replaceAll("\\s+", "");
    }
}
