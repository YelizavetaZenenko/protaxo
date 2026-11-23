package com.example.protaxo.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Запис клієнта в календарі (docs/Календар.md). */
@Entity
@Table(name = "appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Контрагент із довідника; null — людина ще не в довіднику (тоді лише clientName). */
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    private String phone;

    private String vehicle;

    private String note;
}
