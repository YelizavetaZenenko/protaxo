package com.example.protaxo.client.service;

import com.example.protaxo.address.AddressDirectoryService;
import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.dto.ClientBankAccountData;
import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.entity.ClientBankAccount;
import com.example.protaxo.client.mapper.ClientMapper;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.exception.UniqueConstraints;
import com.example.protaxo.common.util.FieldDiff;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final AddressDirectoryService addressDirectoryService;

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

    /** Додаткові рахунки контрагента (V77), у порядку з форми. */
    @Transactional(readOnly = true)
    public List<ClientBankAccountData> findExtraAccounts(Long id) {
        return getOrThrow(id).getExtraAccounts().stream()
                .map(a -> new ClientBankAccountData(a.getIban(), a.getBankName(), a.getMfo()))
                .toList();
    }

    /** id контрагента → IBAN-и його додаткових рахунків через пробіл (для пошуку в списку). */
    @Transactional(readOnly = true)
    public Map<Long, String> extraIbansByClient() {
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : clientRepository.findAllExtraAccountIbans()) {
            result.merge(((Number) row[0]).longValue(), (String) row[1], (a, b) -> a + " " + b);
        }
        return result;
    }

    public ClientResponse create(ClientRequest request) {
        return create(request, null);
    }

    public ClientResponse update(Long id, ClientRequest request) {
        return update(id, request, null);
    }

    /** {@code extraAccounts} null — додаткові рахунки не чіпаються (REST API їх не передає). */
    public ClientResponse create(ClientRequest request, List<ClientBankAccountData> extraAccounts) {
        Client client = clientMapper.toEntity(request);
        applyExtraAccounts(client, extraAccounts);
        composeLegalAddress(client);
        client.setEmployerClient(resolveEmployer(request.employerClientId(), null));
        Client saved = saveOrThrowFriendly(client);
        auditLogService.record(AuditAction.CREATE, "Client", saved.getId(), FieldDiff.created(snapshot(saved)));
        return clientMapper.toResponse(saved);
    }

    public ClientResponse update(Long id, ClientRequest request, List<ClientBankAccountData> extraAccounts) {
        Client client = getOrThrow(id);
        FieldDiff.Snapshot before = snapshot(client);
        clientMapper.updateEntity(request, client);
        applyExtraAccounts(client, extraAccounts);
        composeLegalAddress(client);
        client.setEmployerClient(resolveEmployer(request.employerClientId(), id));
        Client saved = saveOrThrowFriendly(client);
        auditLogService.record(AuditAction.UPDATE, "Client", saved.getId(), FieldDiff.between(before, snapshot(saved)));
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

    /** Порожні рядки (без IBAN) і повтори основного рахунку відкидаються. */
    private static void applyExtraAccounts(Client client, List<ClientBankAccountData> extraAccounts) {
        if (extraAccounts == null) {
            return;
        }
        List<ClientBankAccount> accounts = extraAccounts.stream()
                .filter(a -> a != null && a.getIban() != null && !a.getIban().isBlank())
                .filter(a -> !a.getIban().equals(client.getIban()))
                .map(a -> new ClientBankAccount(a.getIban(), blankToNull(a.getBankName()), blankToNull(a.getMfo())))
                .collect(Collectors.collectingAndThen(Collectors.toMap(ClientBankAccount::getIban, a -> a,
                        (a, b) -> a, LinkedHashMap::new), m -> List.copyOf(m.values())));
        client.getExtraAccounts().clear();
        client.getExtraAccounts().addAll(accounts);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        auditLogService.record(AuditAction.DELETE, "Client", id, FieldDiff.deleted(snapshot(client)));
    }

    /**
     * Обрано населений пункт із КАТОТТГ — юридична адреса для договору складається з частин
     * («45604, Волинська обл., Луцький р-н, с. Крупа, вул. Дубнівська, 10»). Не обрано — лишається
     * текст, як його ввели раніше (старі контрагенти до V73).
     */
    private void composeLegalAddress(Client client) {
        if (client.getAddressSettlementCode() == null) {
            return;
        }
        AddressDirectoryService.Settlement settlement = addressDirectoryService.find(client.getAddressSettlementCode())
                .orElseThrow(() -> new BusinessRuleException("Населений пункт не знайдено в довіднику — оберіть його зі списку"));
        client.setLegalAddress(addressDirectoryService.format(client.getAddressPostalCode(), settlement,
                client.getAddressStreet(), client.getAddressBuilding(), client.getAddressOffice()));
        // Наступного разу для цього пункту індекс підставиться сам (V74).
        addressDirectoryService.rememberPostalCode(client.getAddressSettlementCode(), client.getAddressPostalCode());
    }

    private static FieldDiff.Snapshot snapshot(Client c) {
        return FieldDiff.snapshot()
                .add("Форма власності", c.getLegalForm() == null ? null : c.getLegalForm().getShortLabel())
                .add("Назва", c.getName())
                .add("Повна назва", c.getFullName())
                .add("Код ЄДРПОУ", c.getEdrpou())
                .add("Код", c.getCode())
                .add("Прізвище", c.getLastName())
                .add("Ім'я", c.getFirstName())
                .add("По батькові", c.getMiddleName())
                .add("Дата народження", c.getBirthDate())
                .add("Стать", c.getGender() == null ? null : c.getGender().getLabel())
                .add("Посада", c.getPosition())
                .add("Місце роботи", c.getEmployerClient() == null ? null : c.getEmployerClient().getName())
                .add("Ім'я контактної особи", c.getContactPersonName())
                .add("Телефон контактної особи", c.getContactPersonPhone())
                .add("Телефон", c.getPhone())
                .add("Email", c.getEmail())
                .add("Платник ПДВ", c.isVatPayer())
                .add("Юридична адреса", c.getLegalAddress())
                .add("IBAN", c.getIban())
                .add("Банк", c.getBankName())
                .add("МФО", c.getMfo())
                .add("Інші рахунки", c.getExtraAccounts().isEmpty() ? null : c.getExtraAccounts().stream()
                        .map(a -> a.getIban() + (a.getBankName() == null ? "" : " (" + a.getBankName() + ")"))
                        .collect(Collectors.joining("; ")))
                .add("Посада підписанта", c.getSignatoryPosition())
                .add("Діє на підставі", c.getSignatoryBasis());
    }

    private Client getOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(id)));
    }
}
