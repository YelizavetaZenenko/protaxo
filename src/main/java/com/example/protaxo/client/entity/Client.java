package com.example.protaxo.client.entity;

import com.example.protaxo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    @Column
    private String edrpou;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "contact_person_name")
    private String contactPersonName;

    @Column(name = "contact_person_phone")
    private String contactPersonPhone;

    @Column(length = 12)
    private String code;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_client_id")
    private Client employerClient;

    @Column(name = "job_title")
    private String position;

    private String phone;

    private String email;

    @Column(name = "vat_payer", nullable = false)
    private boolean vatPayer;

    // Реквізити для договору (V71, docs/Договори.md)
    @Column(name = "legal_address", length = 500)
    private String legalAddress;

    /** Поточний рахунок, IBAN (UA + 27 цифр). */
    @Column(length = 34)
    private String iban;

    @Column(name = "bank_name")
    private String bankName;

    @Column(length = 6)
    private String mfo;

    /** Посада того, хто підписує договір від замовника, напр. «Директор». */
    @Column(name = "signatory_position", length = 100)
    private String signatoryPosition;

    /** На підставі чого діє підписант: «Статуту», «довіреності № …». */
    @Column(name = "signatory_basis")
    private String signatoryBasis;

    // Юридична адреса частинами (V73): населений пункт з КАТОТТГ + вулиця, будинок, офіс, індекс.
    // legalAddress складається з них у ClientService; без населеного пункту — старий текст як є.
    @Column(name = "address_settlement_code", length = 19)
    private String addressSettlementCode;

    @Column(name = "address_street")
    private String addressStreet;

    @Column(name = "address_building", length = 30)
    private String addressBuilding;

    @Column(name = "address_office", length = 30)
    private String addressOffice;

    @Column(name = "address_postal_code", length = 5)
    private String addressPostalCode;
}
