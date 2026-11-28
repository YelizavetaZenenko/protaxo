package com.example.protaxo.client.entity;

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
 * Своя форма власності (V81), додана з модального вікна форми контрагента, коли потрібної немає
 * серед вбудованих {@link LegalForm}. Клієнт з нею має {@code legalForm = CUSTOM} і
 * {@code customLegalFormId}; назва складається так само, як у юрособи: «Скорочення «Назва»».
 */
@Entity
@Table(name = "custom_legal_forms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomLegalForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** «ТзОВ», «НВП» — у списку й на початку короткої назви. */
    @Column(name = "short_label", nullable = false, length = 20)
    private String shortLabel;

    /** «Науково-виробниче підприємство» — на початку повної назви; порожня — береться скорочення. */
    @Column(name = "full_label")
    private String fullLabel;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Для повної назви: повна форма, а якщо її не задали — скорочення. */
    public String fullOrShortLabel() {
        return fullLabel == null || fullLabel.isBlank() ? shortLabel : fullLabel;
    }
}
