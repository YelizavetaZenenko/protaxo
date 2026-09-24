package com.example.protaxo.finance.repository;

import com.example.protaxo.finance.entity.FinanceAccount;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceAccountRepository extends JpaRepository<FinanceAccount, Long> {

    List<FinanceAccount> findAllByOrderByKindAscIdAsc();

    List<FinanceAccount> findByKindAndActiveTrueOrderByIdAsc(FinanceAccountKind kind);

    List<FinanceAccount> findByActiveTrueOrderByKindAscIdAsc();
}
