package com.example.protaxo.vehicle.repository;

import com.example.protaxo.vehicle.entity.VehicleModel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {

    List<VehicleModel> findByMakeIdOrderByNameAsc(Long makeId);

    boolean existsByMakeIdAndNameIgnoreCase(Long makeId, String name);
}
