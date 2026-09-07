package com.example.protaxo.worker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
