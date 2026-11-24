package com.example.protaxo.client.repository;

import com.example.protaxo.client.entity.Client;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT name FROM clients WHERE id = :id", nativeQuery = true)
    Optional<String> findNameByIdIncludingDeleted(@Param("id") Long id);

    /** [client_id, iban] усіх додаткових рахунків — для пошуку в списку контрагентів одним запитом. */
    @Query(value = "SELECT client_id, iban FROM client_bank_accounts", nativeQuery = true)
    List<Object[]> findAllExtraAccountIbans();
}
