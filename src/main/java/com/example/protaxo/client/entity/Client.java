package com.example.protaxo.client.entity;

import com.example.protaxo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "clients")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Client extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String edrpou;

    private String contacts;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_payment_type")
    private PaymentType defaultPaymentType;
}
