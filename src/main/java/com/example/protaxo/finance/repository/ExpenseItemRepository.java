package com.example.protaxo.finance.repository;

import com.example.protaxo.finance.entity.ExpenseItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByOperationIdOrderByLineNumber(Long operationId);

    List<ExpenseItem> findByOperationIdInOrderByLineNumber(Collection<Long> operationIds);
}
