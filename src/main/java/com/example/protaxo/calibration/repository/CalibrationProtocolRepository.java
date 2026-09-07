package com.example.protaxo.calibration.repository;

import com.example.protaxo.calibration.entity.CalibrationProtocol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalibrationProtocolRepository extends JpaRepository<CalibrationProtocol, Long> {

    Optional<CalibrationProtocol> findByInvoiceId(Long invoiceId);
}
