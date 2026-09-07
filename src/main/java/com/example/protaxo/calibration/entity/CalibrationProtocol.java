package com.example.protaxo.calibration.entity;

import com.example.protaxo.client.entity.Client;
import com.example.protaxo.common.entity.BaseEntity;
import com.example.protaxo.invoice.entity.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "calibration_protocols")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CalibrationProtocol extends BaseEntity {

    @Column(name = "protocol_date", nullable = false)
    private LocalDateTime protocolDate;

    @Column(name = "internal_number", nullable = false)
    private String internalNumber;

    @Column(name = "stamp_number")
    private String stampNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "vehicle_name")
    private String vehicleName;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "representative_name")
    private String representativeName;

    @Column(name = "tachograph_brand")
    private String tachographBrand;

    @Column(name = "tachograph_model")
    private String tachographModel;

    @Column(name = "tachograph_type")
    private String tachographType;

    @Column(name = "tachograph_manufacturer")
    private String tachographManufacturer;

    @Column(name = "previous_inspection_date")
    private LocalDate previousInspectionDate;

    /**
     * The Наряд-заказ this protocol was created from, if any — set once at creation and never
     * changed afterward (see [[Наряд-заказ]] docs: one invoice has at most one linked protocol,
     * enforced by a unique constraint on this column).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
}
