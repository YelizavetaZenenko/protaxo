package com.example.protaxo.client.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Додатковий поточний рахунок контрагента (V77); основний — у полях Client.iban/bankName/mfo. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientBankAccount {

    @Column(nullable = false, length = 34)
    private String iban;

    @Column(name = "bank_name")
    private String bankName;

    @Column(length = 6)
    private String mfo;
}
