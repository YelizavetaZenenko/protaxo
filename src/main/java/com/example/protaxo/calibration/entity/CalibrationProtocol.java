package com.example.protaxo.calibration.entity;

import com.example.protaxo.client.entity.Client;
import com.example.protaxo.common.entity.BaseEntity;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.tachograph.entity.Tachograph;
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

    @Column(name = "vehicle_vrn")
    private String vehicleVrn;

    @Column(name = "vehicle_vin")
    private String vehicleVin;

    @Column(name = "tachograph_serial_number")
    private String tachographSerialNumber;

    @Column(name = "tachograph_manufacture_year")
    private String tachographManufactureYear;

    @Column(name = "inspection_reason")
    private String inspectionReason;

    @Column(name = "check_method")
    private String checkMethod;

    @Column(name = "mileage_before")
    private String mileageBefore;

    @Column(name = "mileage_after")
    private String mileageAfter;

    @Column(name = "tire_size")
    private String tireSize;

    @Column(name = "tire_pressure")
    private String tirePressure;

    @Column(name = "tire_circumference_l")
    private String tireCircumferenceL;

    @Column(name = "coefficient_w")
    private String coefficientW;

    @Column(name = "constant_k")
    private String constantK;

    @Column(name = "path_deviation_after_install")
    private String pathDeviationAfterInstall;

    @Column(name = "path_deviation_in_service")
    private String pathDeviationInService;

    @Column(name = "speed_deviation_after_install")
    private String speedDeviationAfterInstall;

    @Column(name = "speed_deviation_in_service")
    private String speedDeviationInService;

    @Column(name = "time_deviation_after_install")
    private String timeDeviationAfterInstall;

    @Column(name = "time_deviation_in_service")
    private String timeDeviationInService;

    @Column(name = "speed_limiter_value")
    private String speedLimiterValue;

    @Column(name = "cover_opening_registered")
    private String coverOpeningRegistered;

    @Column(name = "power_cutoff_registered")
    private String powerCutoffRegistered;

    @Column(name = "pulse_sensor_interruption_registered")
    private String pulseSensorInterruptionRegistered;

    @Column(name = "executor_position")
    private String executorPosition;

    @Column(name = "executor_name")
    private String executorName;

    /**
     * The Наряд-заказ this protocol was created from, if any — set once at creation and never
     * changed afterward (see [[Наряд-заказ]] docs: one invoice has at most one linked protocol,
     * enforced by a unique constraint on this column).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /**
     * The real, system-tracked device this protocol is about — nullable, since older/free-text
     * protocols never picked one. When set, {@code tachographModel}/{@code tachographManufacturer}/
     * {@code tachographSerialNumber}/{@code tachographManufactureYear} are unconditionally derived
     * from this record server-side (see CalibrationProtocolService.applyFields) rather than trusted
     * from the request, the same "server always wins" treatment as internalNumber/stampNumber.
     * {@code tachographBrand}/{@code tachographType} stay independently editable dictionary
     * selects — Tachograph itself has no "brand" or "type" field to derive them from.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tachograph_id")
    private Tachograph tachograph;

    /**
     * Номери пломб, встановлених при калібруванні — вільний текст (фізичні пломби можуть мати
     * будь-яке маркування виробника), використовується лише для наклейки [[Print Agent]], у самому
     * PDF-бланку протоколу такого поля немає (у зразку його не було).
     */
    @Column(name = "seal_numbers")
    private String sealNumbers;

    /**
     * Згенерований раз при створенні протоколу (див. CalibrationProtocolService.create), ніколи не
     * редагується вручну — саме цей рядок кодується в QR-коді на наклейці [[Print Agent]].
     */
    @Column(name = "qr_hash")
    private String qrHash;
}
