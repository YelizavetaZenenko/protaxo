package com.example.protaxo.finance.repository;

import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.FinanceOperationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceOperationRepository extends JpaRepository<FinanceOperation, Long> {

    Optional<FinanceOperation> findByRequestKey(String requestKey);

    @Query("SELECT o FROM FinanceOperation o JOIN FETCH o.account LEFT JOIN FETCH o.targetAccount "
            + "LEFT JOIN FETCH o.invoice LEFT JOIN FETCH o.originalOperation WHERE o.id = :id")
    Optional<FinanceOperation> findDetailedById(@Param("id") Long id);

    @Query("SELECT o FROM FinanceOperation o JOIN FETCH o.account LEFT JOIN FETCH o.originalOperation "
            + "WHERE o.invoice.id = :invoiceId ORDER BY o.occurredAt ASC, o.id ASC")
    List<FinanceOperation> findByInvoiceId(@Param("invoiceId") Long invoiceId);

    /**
     * Журнал операцій за період з необов'язковими фільтрами. {@code types} ніколи не порожній —
     * контролер передає всі типи, коли фільтр не вибрано (порожній IN-список Hibernate не любить).
     */
    @Query("SELECT o FROM FinanceOperation o JOIN FETCH o.account LEFT JOIN FETCH o.targetAccount "
            + "LEFT JOIN FETCH o.invoice "
            + "WHERE o.occurredAt >= :from AND o.occurredAt < :to AND o.type IN :types "
            + "AND (:accountId IS NULL OR o.account.id = :accountId OR o.targetAccount.id = :accountId) "
            + "AND (:includeCancelled = TRUE OR o.cancelledAt IS NULL) "
            + "ORDER BY o.occurredAt DESC, o.id DESC")
    List<FinanceOperation> search(@Param("from") Instant from, @Param("to") Instant to,
                                  @Param("types") Collection<FinanceOperationType> types,
                                  @Param("accountId") Long accountId,
                                  @Param("includeCancelled") boolean includeCancelled);

    /** Суми нескасованих операцій по рахунку-джерелу: [accountId, type, sum]. */
    @Query("SELECT o.account.id, o.type, SUM(o.amount) FROM FinanceOperation o "
            + "WHERE o.cancelledAt IS NULL AND o.occurredAt <= :at GROUP BY o.account.id, o.type")
    List<Object[]> sumBySourceAccountAndType(@Param("at") Instant at);

    /** Надходження за переміщеннями на рахунок-одержувач: [targetAccountId, sum]. */
    @Query("SELECT o.targetAccount.id, SUM(o.amount) FROM FinanceOperation o "
            + "WHERE o.cancelledAt IS NULL AND o.occurredAt <= :at AND o.targetAccount IS NOT NULL "
            + "GROUP BY o.targetAccount.id")
    List<Object[]> sumByTargetAccount(@Param("at") Instant at);

    /** Оплати й повернення по нарядах: [invoiceId, type, sum]. */
    @Query("SELECT o.invoice.id, o.type, SUM(o.amount) FROM FinanceOperation o "
            + "WHERE o.cancelledAt IS NULL AND o.invoice IS NOT NULL "
            + "AND o.type IN (com.example.protaxo.finance.entity.FinanceOperationType.PAYMENT, "
            + "com.example.protaxo.finance.entity.FinanceOperationType.REFUND) "
            + "GROUP BY o.invoice.id, o.type")
    List<Object[]> sumSettlementsByInvoice();

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM FinanceOperation o "
            + "WHERE o.cancelledAt IS NULL AND o.invoice.id = :invoiceId AND o.type = :type")
    BigDecimal sumForInvoice(@Param("invoiceId") Long invoiceId, @Param("type") FinanceOperationType type);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM FinanceOperation o "
            + "WHERE o.cancelledAt IS NULL AND o.originalOperation.id = :operationId "
            + "AND o.type = com.example.protaxo.finance.entity.FinanceOperationType.REFUND")
    BigDecimal sumRefundsOf(@Param("operationId") Long operationId);

    @Query("SELECT COUNT(o) > 0 FROM FinanceOperation o WHERE o.cancelledAt IS NULL "
            + "AND (o.account.id = :accountId OR o.targetAccount.id = :accountId)")
    boolean existsActiveForAccount(@Param("accountId") Long accountId);

    @Query("SELECT COUNT(o) > 0 FROM FinanceOperation o WHERE o.cancelledAt IS NULL "
            + "AND o.originalOperation.id = :operationId")
    boolean hasActiveDependents(@Param("operationId") Long operationId);
}
