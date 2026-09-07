package com.example.protaxo.vehicle.repository;

import com.example.protaxo.vehicle.entity.VehicleMake;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleMakeRepository extends JpaRepository<VehicleMake, Long> {

    List<VehicleMake> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
