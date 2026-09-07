package com.example.protaxo.vehicle.repository;

import com.example.protaxo.vehicle.entity.Vehicle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByClientId(Long clientId);
}
