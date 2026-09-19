package com.example.protaxo.tachograph.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.tachograph.dto.TachographRequest;
import com.example.protaxo.tachograph.dto.TachographResponse;
import com.example.protaxo.tachograph.entity.Tachograph;
import com.example.protaxo.tachograph.mapper.TachographMapper;
import com.example.protaxo.tachograph.repository.TachographRepository;
import com.example.protaxo.vehicle.entity.Vehicle;
import com.example.protaxo.vehicle.repository.VehicleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TachographService {

    private final TachographRepository tachographRepository;
    private final VehicleRepository vehicleRepository;
    private final TachographMapper tachographMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TachographResponse> findAll() {
        return tachographRepository.findAll().stream()
                .map(tachographMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TachographResponse findById(Long id) {
        return tachographMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TachographResponse> findByVehicleId(Long vehicleId) {
        return tachographRepository.findByVehicleId(vehicleId).stream()
                .map(tachographMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TachographResponse> findByClientId(Long clientId) {
        return tachographRepository.findByVehicle_ClientId(clientId).stream()
                .map(tachographMapper::toResponse)
                .toList();
    }

    public TachographResponse create(TachographRequest request) {
        Tachograph tachograph = tachographMapper.toEntity(request);
        tachograph.setVehicle(getVehicleOrThrow(request.vehicleId()));
        Tachograph saved = tachographRepository.save(tachograph);
        auditLogService.record(AuditAction.CREATE, "Tachograph", saved.getId());
        return tachographMapper.toResponse(saved);
    }

    public TachographResponse update(Long id, TachographRequest request) {
        Tachograph tachograph = getOrThrow(id);
        Map<String, String[]> changes = FieldDiff.builder()
                .add("Виробник", tachograph.getManufacturer(), request.manufacturer())
                .add("Модель", tachograph.getModel(), request.model())
                .add("Заводський номер", tachograph.getSerialNumber(), request.serialNumber())
                .add("Дата випуску", tachograph.getProductionDate(), request.productionDate())
                .build();
        tachographMapper.updateEntity(request, tachograph);
        tachograph.setVehicle(getVehicleOrThrow(request.vehicleId()));
        Tachograph saved = tachographRepository.save(tachograph);
        auditLogService.record(AuditAction.UPDATE, "Tachograph", saved.getId(), changes);
        return tachographMapper.toResponse(saved);
    }

    public void softDelete(Long id) {
        Tachograph tachograph = getOrThrow(id);
        tachograph.setDeletedAt(Instant.now());
        tachographRepository.save(tachograph);
        auditLogService.record(AuditAction.DELETE, "Tachograph", id);
    }

    private Tachograph getOrThrow(Long id) {
        return tachographRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tachograph %d not found".formatted(id)));
    }

    private Vehicle getVehicleOrThrow(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle %d not found".formatted(vehicleId)));
    }
}
