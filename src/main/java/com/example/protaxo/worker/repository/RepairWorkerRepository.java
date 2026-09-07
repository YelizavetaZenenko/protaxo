package com.example.protaxo.worker.repository;

import com.example.protaxo.worker.entity.RepairWorker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairWorkerRepository extends JpaRepository<RepairWorker, Long> {

    List<RepairWorker> findAllByOrderByFullNameAsc();
}
