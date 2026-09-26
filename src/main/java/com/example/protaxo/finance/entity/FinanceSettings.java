package com.example.protaxo.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Налаштування обліку — єдиний рядок {@code id = 1} (створюється міграцією V47). */
@Entity
@Table(name = "finance_settings")
@Data
@NoArgsConstructor
public class FinanceSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Як підписувати панель, напр. «ФОП Вишивата Діана Олександрівна». */
    @Column(name = "business_name")
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_system")
    private TaxSystem taxSystem;

    @Column(name = "vat_payer", nullable = false)
    private boolean vatPayer;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_statement_source", nullable = false)
    private BankStatementSource bankStatementSource = BankStatementSource.MANUAL;

    @Version
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    /** «Єдиний податок, 3 група · платник ПДВ» або null, якщо систему не вказано. */
    public String taxSummary() {
        if (taxSystem == null) {
            return null;
        }
        return taxSystem.getLabel() + (vatPayer ? " · платник ПДВ" : " · без ПДВ");
    }
}
