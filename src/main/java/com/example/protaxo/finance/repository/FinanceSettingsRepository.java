package com.example.protaxo.finance.repository;

import com.example.protaxo.finance.entity.FinanceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceSettingsRepository extends JpaRepository<FinanceSettings, Long> {
}
