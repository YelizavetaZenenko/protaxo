package com.example.protaxo.calibration.entity;

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
 * Registered workshop master cards, used to unlock/calibrate digital tachographs — backs the
 * "Номер картки" picker on [[Протокол калібрування (CalibrationProtocol)]] (a workshop may have
 * several cards, each assigned to a holder).
 */
@Entity
@Table(name = "master_cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, unique = true)
    private String cardNumber;

    @Column(name = "holder_name", nullable = false)
    private String holderName;
}
