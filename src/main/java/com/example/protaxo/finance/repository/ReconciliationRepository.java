package com.example.protaxo.finance.repository;

import com.example.protaxo.finance.entity.Reconciliation;
import com.example.protaxo.finance.entity.ReconciliationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> {

    @Query("SELECT r FROM Reconciliation r JOIN FETCH r.account ORDER BY r.countedAt DESC, r.id DESC")
    List<Reconciliation> findAllDetailed();

    @Query("SELECT r FROM Reconciliation r JOIN FETCH r.account WHERE r.status = :status ORDER BY r.countedAt DESC")
    List<Reconciliation> findByStatusDetailed(@Param("status") ReconciliationStatus status);

    @Query("SELECT r FROM Reconciliation r JOIN FETCH r.account WHERE r.id = :id")
    Optional<Reconciliation> findDetailedById(@Param("id") Long id);
}
