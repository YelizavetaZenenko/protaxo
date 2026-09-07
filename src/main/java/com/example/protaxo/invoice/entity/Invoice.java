package com.example.protaxo.invoice.entity;

import com.example.protaxo.client.entity.Client;
import com.example.protaxo.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "invoices")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Invoice extends BaseEntity {

    @Column(nullable = false)
    private String number;

    @Column(name = "document_date", nullable = false)
    private LocalDateTime documentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private InvoicePaymentType paymentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "vehicle_name")
    private String vehicleName;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "repair_responsible_name")
    private String repairResponsibleName;

    @Column(name = "repair_supervisor_name")
    private String repairSupervisorName;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();
}
