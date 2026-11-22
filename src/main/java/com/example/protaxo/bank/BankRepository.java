package com.example.protaxo.bank;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, Long> {

    List<Bank> findAllByOrderByNameAsc();

    Optional<Bank> findFirstByMfo(String mfo);

    Optional<Bank> findFirstByNameIgnoreCase(String name);
}
