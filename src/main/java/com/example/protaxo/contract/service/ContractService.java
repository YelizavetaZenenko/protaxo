package com.example.protaxo.contract.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.contract.dto.ContractRequest;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.contract.entity.Contract;
import com.example.protaxo.contract.mapper.ContractMapper;
import com.example.protaxo.contract.repository.ContractRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractService {

    private final ContractRepository contractRepository;
    private final ClientRepository clientRepository;
    private final ContractMapper contractMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ContractResponse> findAll() {
        return contractRepository.findAll().stream()
                .map(contractMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse findById(Long id) {
        return contractMapper.toResponse(getOrThrow(id));
    }

    public ContractResponse create(ContractRequest request) {
        Contract contract = contractMapper.toEntity(request);
        contract.setClient(getClientOrThrow(request.clientId()));
        Contract saved = contractRepository.save(contract);
        auditLogService.record(AuditAction.CREATE, "Contract", saved.getId());
        return contractMapper.toResponse(saved);
    }

    public ContractResponse update(Long id, ContractRequest request) {
        Contract contract = getOrThrow(id);
        contractMapper.updateEntity(request, contract);
        contract.setClient(getClientOrThrow(request.clientId()));
        Contract saved = contractRepository.save(contract);
        auditLogService.record(AuditAction.UPDATE, "Contract", saved.getId());
        return contractMapper.toResponse(saved);
    }

    public void softDelete(Long id) {
        Contract contract = getOrThrow(id);
        contract.setDeletedAt(Instant.now());
        contractRepository.save(contract);
        auditLogService.record(AuditAction.DELETE, "Contract", id);
    }

    private Contract getOrThrow(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Contract %d not found".formatted(id)));
    }

    private Client getClientOrThrow(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(clientId)));
    }
}
