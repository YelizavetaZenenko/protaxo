package com.example.protaxo.vehicle.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.vehicle.dto.VehicleRequest;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.entity.Vehicle;
import com.example.protaxo.vehicle.mapper.VehicleMapper;
import com.example.protaxo.vehicle.repository.VehicleRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ClientRepository clientRepository;
    private final VehicleMapper vehicleMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<VehicleResponse> findAll() {
        return vehicleRepository.findAll().stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(Long id) {
        return vehicleMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> findByClientId(Long clientId) {
        return vehicleRepository.findByClientId(clientId).stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    public VehicleResponse create(VehicleRequest request) {
        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setClient(getClientOrThrow(request.clientId()));
        Vehicle saved = vehicleRepository.save(vehicle);
        auditLogService.record(AuditAction.CREATE, "Vehicle", saved.getId());
        return vehicleMapper.toResponse(saved);
    }

    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = getOrThrow(id);
        vehicleMapper.updateEntity(request, vehicle);
        vehicle.setClient(getClientOrThrow(request.clientId()));
        Vehicle saved = vehicleRepository.save(vehicle);
        auditLogService.record(AuditAction.UPDATE, "Vehicle", saved.getId());
        return vehicleMapper.toResponse(saved);
    }

    public void softDelete(Long id) {
        Vehicle vehicle = getOrThrow(id);
        vehicle.setDeletedAt(Instant.now());
        vehicleRepository.save(vehicle);
        auditLogService.record(AuditAction.DELETE, "Vehicle", id);
    }

    private Vehicle getOrThrow(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle %d not found".formatted(id)));
    }

    private Client getClientOrThrow(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(clientId)));
    }
}
