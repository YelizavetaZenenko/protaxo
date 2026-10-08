package com.example.protaxo.worker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared staff directory for the "Відповідальний за ремонт" and "Керівник ремонту" fields on
 * [[Наряд-заказ]] — replaces the earlier free-text FieldSuggestion pool for those two fields.
 * Only ADMIN may add new workers (enforced in SecurityConfig, not here).
 */
@Entity
@Table(name = "repair_workers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairWorker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String position;

    private String phone;

    private String email;

    private String address;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    /**
     * Personal workshop card for digital tachographs. Not used yet — removed from the page on
     * request (2026-10-08); the columns stay so it can come back without a migration.
     */
    @Column(name = "workshop_card_number")
    private String workshopCardNumber;

    @Column(name = "workshop_card_valid_until")
    private LocalDate workshopCardValidUntil;

    /** Who to call if something happens to the worker — name and phone in one line. */
    @Column(name = "emergency_contact")
    private String emergencyContact;

    private String notes;
}
