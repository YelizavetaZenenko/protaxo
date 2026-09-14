package com.example.protaxo.contract.repository;

import com.example.protaxo.contract.entity.Contract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query(value = "SELECT nextval('contract_number_seq')", nativeQuery = true)
    long nextNumberValue();

    Optional<Contract> findFirstByClient_IdOrderByIdDesc(Long clientId);

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT contract_number FROM contracts WHERE id = :id", nativeQuery = true)
    Optional<String> findContractNumberByIdIncludingDeleted(@Param("id") Long id);
}
