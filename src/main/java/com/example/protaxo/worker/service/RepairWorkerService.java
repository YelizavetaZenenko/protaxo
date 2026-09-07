package com.example.protaxo.worker.service;

import com.example.protaxo.worker.dto.RepairWorkerResponse;
import com.example.protaxo.worker.entity.RepairWorker;
import com.example.protaxo.worker.repository.RepairWorkerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RepairWorkerService {

    private final RepairWorkerRepository repairWorkerRepository;

    @Transactional(readOnly = true)
    public List<RepairWorkerResponse> findAll() {
        return repairWorkerRepository.findAllByOrderByFullNameAsc().stream()
                .map(w -> new RepairWorkerResponse(w.getId(), w.getFullName(), w.getPosition()))
                .toList();
    }

    public RepairWorkerResponse create(String fullName, String position) {
        RepairWorker saved = repairWorkerRepository.save(RepairWorker.builder()
                .fullName(fullName.trim())
                .position(position.trim())
                .build());
        return new RepairWorkerResponse(saved.getId(), saved.getFullName(), saved.getPosition());
    }
}
