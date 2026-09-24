package com.example.protaxo.finance.repository;

import com.example.protaxo.finance.entity.FinanceAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceAttachmentRepository extends JpaRepository<FinanceAttachment, Long> {

    /** Лише метадані [id, operationId, fileName] — без байтів файлу, для списків. */
    @Query("SELECT a.id, a.operationId, a.fileName FROM FinanceAttachment a WHERE a.operationId IN :operationIds")
    List<Object[]> findMetaByOperationIds(@Param("operationIds") Collection<Long> operationIds);
}
