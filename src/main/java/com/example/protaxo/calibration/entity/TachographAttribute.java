package com.example.protaxo.calibration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reference values for the "Дані тахографа" dropdowns (марка/модель/тип/виробник) on
 * [[Протокол калібрування (CalibrationProtocol)]] — one shared table with a category
 * discriminator, same idea as {@code field_suggestions}, but a "hard" managed list (real
 * dropdown + add-modal) rather than free-text autocomplete. Not wired to
 * {@code CalibrationProtocol.tachographBrand} etc. as a FK — those stay plain strings, this is
 * just the source of dropdown options and grows via "+ Додати".
 */
@Entity
@Table(name = "tachograph_attributes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TachographAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TachographAttributeCategory category;

    @Column(nullable = false)
    private String name;
}
