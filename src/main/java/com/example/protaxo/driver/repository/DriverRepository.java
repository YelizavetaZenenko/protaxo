package com.example.protaxo.driver.repository;

import com.example.protaxo.driver.entity.Driver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findByClientId(Long clientId);

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT full_name FROM drivers WHERE id = :id", nativeQuery = true)
    Optional<String> findFullNameByIdIncludingDeleted(@Param("id") Long id);
}
