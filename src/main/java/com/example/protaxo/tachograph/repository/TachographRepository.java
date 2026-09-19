package com.example.protaxo.tachograph.repository;

import com.example.protaxo.tachograph.entity.Tachograph;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TachographRepository extends JpaRepository<Tachograph, Long> {

    List<Tachograph> findByVehicleId(Long vehicleId);

    /** For the "Тахограф" picker on [[Протокол калібрування (CalibrationProtocol)]] - every
        tachograph belonging to any vehicle owned by this client. */
    List<Tachograph> findByVehicle_ClientId(Long clientId);

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT serial_number FROM tachographs WHERE id = :id", nativeQuery = true)
    Optional<String> findSerialNumberByIdIncludingDeleted(@Param("id") Long id);
}
