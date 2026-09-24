package com.example.protaxo.stockrevision.repository;

import com.example.protaxo.stockrevision.entity.StockRevision;
import com.example.protaxo.stockrevision.entity.StockRevisionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRevisionRepository extends JpaRepository<StockRevision, Long> {

    List<StockRevision> findAllByOrderByCreatedAtDesc();

    Optional<StockRevision> findFirstByStatus(StockRevisionStatus status);
}
