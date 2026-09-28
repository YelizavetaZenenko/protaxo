package com.example.protaxo.audit.repository;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.entity.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Optional<AuditLog> findFirstByEntityTypeAndEntityIdAndActionAndChangesIsNotNullOrderByOccurredAtDesc(
            String entityType, Long entityId, AuditAction action);

    @Query("select distinct a.username from AuditLog a order by a.username")
    List<String> findDistinctUsernames();

    @Query("select distinct a.entityType from AuditLog a")
    List<String> findDistinctEntityTypes();
}
