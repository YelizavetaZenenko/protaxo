package com.example.protaxo.maintenance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Єдиний рядок (id = 1) — перемикач режиму технічних робіт. Див. V56__maintenance_mode.sql. */
@Entity
@Table(name = "maintenance_mode")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceMode {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean enabled;

    private LocalDateTime changedAt;

    private String changedBy;
}
