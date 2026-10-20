package com.example.protaxo.maintenance.repository;

import com.example.protaxo.maintenance.entity.MaintenanceMode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceModeRepository extends JpaRepository<MaintenanceMode, Short> {
}
