package com.example.protaxo.client.repository;

import com.example.protaxo.client.entity.Client;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT name FROM clients WHERE id = :id", nativeQuery = true)
    Optional<String> findNameByIdIncludingDeleted(@Param("id") Long id);
}
