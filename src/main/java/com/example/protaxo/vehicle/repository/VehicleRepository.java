package com.example.protaxo.vehicle.repository;

import com.example.protaxo.vehicle.entity.Vehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByClientId(Long clientId);

    /**
     * Bypasses the {@code deleted_at IS NULL} @SQLRestriction (a plain column read, not an
     * entity load) so "Журнал дій" can still show a label for a vehicle that's since been
     * deleted. See {@link com.example.protaxo.audit.service.AuditEntityLabelResolver}.
     */
    @Query(value = "SELECT registration_number FROM vehicles WHERE id = :id", nativeQuery = true)
    Optional<String> findRegistrationNumberByIdIncludingDeleted(@Param("id") Long id);
}
