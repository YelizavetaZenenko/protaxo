package com.example.protaxo.worker.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.worker.dto.RepairWorkerDetails;
import com.example.protaxo.worker.dto.RepairWorkerRequest;
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

    @Transactional(readOnly = true)
    public List<RepairWorkerDetails> findAllDetails() {
        return repairWorkerRepository.findAllByOrderByFullNameAsc().stream()
                .map(RepairWorkerService::toDetails)
                .toList();
    }

    public RepairWorkerResponse create(RepairWorkerRequest request) {
        RepairWorker worker = new RepairWorker();
        apply(worker, request);
        RepairWorker saved = repairWorkerRepository.save(worker);
        auditLogService.record(AuditAction.CREATE, "RepairWorker", saved.getId(), FieldDiff.created(snapshot(saved)));
        return new RepairWorkerResponse(saved.getId(), saved.getFullName(), saved.getPosition());
    }

    public RepairWorkerResponse update(Long id, RepairWorkerRequest request) {
        RepairWorker worker = repairWorkerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Робітника не знайдено: " + id));
        FieldDiff.Snapshot before = snapshot(worker);
        apply(worker, request);
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

    private static void apply(RepairWorker worker, RepairWorkerRequest request) {
        worker.setFullName(request.fullName().trim());
        worker.setPosition(request.position().trim());
        worker.setPhone(blankToNull(request.phone()));
        worker.setEmail(blankToNull(request.email()));
        worker.setAddress(blankToNull(request.address()));
        worker.setHireDate(request.hireDate());
        worker.setEmergencyContact(blankToNull(request.emergencyContact()));
        worker.setNotes(blankToNull(request.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static RepairWorkerDetails toDetails(RepairWorker w) {
        return new RepairWorkerDetails(w.getId(), w.getFullName(), w.getPosition(), w.getPhone(), w.getEmail(),
                w.getAddress(), w.getHireDate(), w.getEmergencyContact(), w.getNotes());
    }

    private static FieldDiff.Snapshot snapshot(RepairWorker worker) {
        return FieldDiff.snapshot()
                .add("ПІБ", worker.getFullName())
                .add("Посада", worker.getPosition())
                .add("Телефон", worker.getPhone())
                .add("E-mail", worker.getEmail())
                .add("Адреса", worker.getAddress())
                .add("Дата прийому на роботу", worker.getHireDate())
                .add("Екстрений контакт", worker.getEmergencyContact())
                .add("Примітки", worker.getNotes());
    }
}
