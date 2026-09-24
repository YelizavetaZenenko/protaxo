package com.example.protaxo.invoice.repository;

import com.example.protaxo.invoice.entity.Invoice;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    long nextNumberValue();

    @Query("SELECT i.vehicleName, i.documentDate FROM Invoice i "
            + "WHERE i.client.id = :clientId AND i.vehicleName IS NOT NULL")
    List<Object[]> findVehicleVisitDatesByClientId(@Param("clientId") Long clientId);

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT number FROM invoices WHERE id = :id", nativeQuery = true)
    Optional<String> findNumberByIdIncludingDeleted(@Param("id") Long id);

    Optional<Invoice> findByNumber(String number);

    /** Блокує рядок наряду на час запису оплати — дві одночасні оплати не перевищать борг. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);
}
