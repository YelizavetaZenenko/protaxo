package com.example.protaxo.bank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Банк із довідника для картки контрагента (V72, docs/Контрагенти.md). Стартовий список — діючі
 * банки НБУ ({@code source = NBU}); додані з форми — {@code USER}.
 */
@Entity
@Table(name = "banks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 6 цифр; збігається з цифрами 5–10 IBAN рахунку в цьому банку. */
    @Column(length = 6)
    private String mfo;

    @Column(length = 8)
    private String edrpou;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String source = "USER";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
