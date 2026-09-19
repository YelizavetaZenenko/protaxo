package com.example.protaxo.calibration.repository;

import com.example.protaxo.calibration.entity.CalibrationProtocol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalibrationProtocolRepository extends JpaRepository<CalibrationProtocol, Long> {

    Optional<CalibrationProtocol> findByInvoiceId(Long invoiceId);

    /** Backs the public, unauthenticated /verify/{qrHash}/pdf route — see [[Print Agent]]. */
    Optional<CalibrationProtocol> findByQrHash(String qrHash);

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT internal_number FROM calibration_protocols WHERE id = :id", nativeQuery = true)
    Optional<String> findInternalNumberByIdIncludingDeleted(@Param("id") Long id);
}
