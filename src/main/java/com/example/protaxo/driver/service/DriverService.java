package com.example.protaxo.driver.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.driver.dto.DriverRequest;
import com.example.protaxo.driver.dto.DriverResponse;
import com.example.protaxo.driver.entity.Driver;
import com.example.protaxo.driver.mapper.DriverMapper;
import com.example.protaxo.driver.repository.DriverRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DriverService {

    private final DriverRepository driverRepository;
    private final ClientRepository clientRepository;
    private final DriverMapper driverMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<DriverResponse> findAll() {
        return driverRepository.findAll().stream()
                .map(driverMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(Long id) {
        return driverMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> findByClientId(Long clientId) {
        return driverRepository.findByClientId(clientId).stream()
                .map(driverMapper::toResponse)
                .toList();
    }

    public DriverResponse create(DriverRequest request) {
        Driver driver = driverMapper.toEntity(request);
        driver.setClient(getClientOrThrow(request.clientId()));
        Driver saved = driverRepository.save(driver);
        auditLogService.record(AuditAction.CREATE, "Driver", saved.getId(), FieldDiff.created(snapshot(saved)));
        return driverMapper.toResponse(saved);
    }

    public DriverResponse update(Long id, DriverRequest request) {
        Driver driver = getOrThrow(id);
        FieldDiff.Snapshot before = snapshot(driver);
        driverMapper.updateEntity(request, driver);
        driver.setClient(getClientOrThrow(request.clientId()));
        Driver saved = driverRepository.save(driver);
        auditLogService.record(AuditAction.UPDATE, "Driver", saved.getId(), FieldDiff.between(before, snapshot(saved)));
        return driverMapper.toResponse(saved);
    }

    public void softDelete(Long id) {
        Driver driver = getOrThrow(id);
        driver.setDeletedAt(Instant.now());
        driverRepository.save(driver);
        auditLogService.record(AuditAction.DELETE, "Driver", id, FieldDiff.deleted(snapshot(driver)));
    }

    private static FieldDiff.Snapshot snapshot(Driver d) {
        return FieldDiff.snapshot()
                .add("Контрагент", d.getClient() == null ? null : d.getClient().getName())
                .add("ПІБ", d.getFullName())
                .add("Телефон", d.getPhone())
                .add("Посада", d.getPosition());
    }

    private Driver getOrThrow(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Driver %d not found".formatted(id)));
    }

    private Client getClientOrThrow(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(clientId)));
    }
}
