package com.example.protaxo.driver.repository;

import com.example.protaxo.driver.entity.Driver;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findByClientId(Long clientId);
}
