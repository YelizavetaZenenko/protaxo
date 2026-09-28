package com.example.protaxo.worker.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
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
    private final AuditLogService auditLogService;

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
        auditLogService.record(AuditAction.CREATE, "RepairWorker", saved.getId(), FieldDiff.created(snapshot(saved)));
        return new RepairWorkerResponse(saved.getId(), saved.getFullName(), saved.getPosition());
    }

    public RepairWorkerResponse update(Long id, String fullName, String position) {
        RepairWorker worker = repairWorkerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Робітника не знайдено: " + id));
        FieldDiff.Snapshot before = snapshot(worker);
        worker.setFullName(fullName.trim());
        worker.setPosition(position.trim());
        RepairWorker saved = repairWorkerRepository.save(worker);
        auditLogService.record(AuditAction.UPDATE, "RepairWorker", id, FieldDiff.between(before, snapshot(saved)));
        return new RepairWorkerResponse(saved.getId(), saved.getFullName(), saved.getPosition());
    }

    /**
     * Hard delete — {@code RepairWorker} is a plain reference table (no {@code BaseEntity}/soft
     * delete, unlike most entities in this project). Nothing holds a foreign key to it: the fields
     * that fill from it ({@code repairResponsibleName}/{@code repairSupervisorName} on Invoice,
     * {@code executorName}/{@code executorPosition} on CalibrationProtocol) copy the worker's name
     * as a plain string at pick time, so removing a worker here cannot orphan or break any
     * already-saved document.
     */
    public void delete(Long id) {
        RepairWorker worker = repairWorkerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Робітника не знайдено: " + id));
        repairWorkerRepository.delete(worker);
        auditLogService.record(AuditAction.DELETE, "RepairWorker", id, FieldDiff.deleted(snapshot(worker)));
    }

    private static FieldDiff.Snapshot snapshot(RepairWorker worker) {
        return FieldDiff.snapshot()
                .add("ПІБ", worker.getFullName())
                .add("Посада", worker.getPosition());
    }
}
