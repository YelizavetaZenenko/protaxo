package com.example.protaxo.client.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.mapper.ClientMapper;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.exception.UniqueConstraints;
import com.example.protaxo.common.util.FieldDiff;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream()
                .map(clientMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(Long id) {
        return clientMapper.toResponse(getOrThrow(id));
    }

    public ClientResponse create(ClientRequest request) {
        Client client = clientMapper.toEntity(request);
        client.setEmployerClient(resolveEmployer(request.employerClientId(), null));
        Client saved = saveOrThrowFriendly(client);
        auditLogService.record(AuditAction.CREATE, "Client", saved.getId());
        return clientMapper.toResponse(saved);
    }

    public ClientResponse update(Long id, ClientRequest request) {
        Client client = getOrThrow(id);
        Map<String, String[]> changes = FieldDiff.builder()
                .add("Назва", client.getName(), request.name())
                .add("Повна назва", client.getFullName(), request.fullName())
                .add("Код ЄДРПОУ", client.getEdrpou(), request.edrpou())
                .add("Код", client.getCode(), request.code())
                .add("Прізвище", client.getLastName(), request.lastName())
                .add("Ім'я", client.getFirstName(), request.firstName())
                .add("По батькові", client.getMiddleName(), request.middleName())
                .add("Дата народження", client.getBirthDate(), request.birthDate())
                .add("Стать", client.getGender(), request.gender())
                .add("Посада", client.getPosition(), request.position())
                .add("Ім'я контактної особи", client.getContactPersonName(), request.contactPersonName())
                .add("Телефон контактної особи", client.getContactPersonPhone(), request.contactPersonPhone())
                .add("Телефон", client.getPhone(), request.phone())
                .add("Email", client.getEmail(), request.email())
                .add("Платник ПДВ", client.isVatPayer() ? "так" : "ні", request.vatPayer() ? "так" : "ні")
                .build();
        clientMapper.updateEntity(request, client);
        client.setEmployerClient(resolveEmployer(request.employerClientId(), id));
        Client saved = saveOrThrowFriendly(client);
        auditLogService.record(AuditAction.UPDATE, "Client", saved.getId(), changes);
        return clientMapper.toResponse(saved);
    }

    /**
     * {@code edrpou} has a DB-level unique constraint (@Column(unique = true)) with no pre-check
     * before this call — a duplicate previously surfaced as a raw DataIntegrityViolationException,
     * which GlobalExceptionHandler turns into an unhelpful JSON 409 response instead of the normal
     * form re-render with a field error. saveAndFlush forces the constraint to fire here (a plain
     * save() can defer the INSERT/UPDATE past this method, past this try/catch). Only a violation
     * of the edrpou index itself is reported as a duplicate — any other integrity error is
     * rethrown, never disguised as "already exists".
     */
    private Client saveOrThrowFriendly(Client client) {
        if (client.getEdrpou() != null && client.getEdrpou().isBlank()) {
            client.setEdrpou(null);
        }
        try {
            return clientRepository.saveAndFlush(client);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraints.isViolationOf(ex, UniqueConstraints.CLIENT_EDRPOU)) {
                throw new BusinessRuleException("Контрагент з таким кодом ЄДРПОУ вже існує");
            }
            throw ex;
        }
    }

    private Client resolveEmployer(Long employerClientId, Long ownId) {
        if (employerClientId == null) {
            return null;
        }
        if (employerClientId.equals(ownId)) {
            throw new BusinessRuleException("Контрагент не може бути власним місцем роботи");
        }
        return getOrThrow(employerClientId);
    }

    public void softDelete(Long id) {
        Client client = getOrThrow(id);
        client.setDeletedAt(Instant.now());
        clientRepository.save(client);
        auditLogService.record(AuditAction.DELETE, "Client", id);
    }

    private Client getOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(id)));
    }
}
