package com.example.protaxo.invoice.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.invoice.entity.InvoiceItem;
import com.example.protaxo.invoice.mapper.InvoiceMapper;
import com.example.protaxo.invoice.repository.InvoiceRepository;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import com.example.protaxo.suggestion.service.FieldSuggestionService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final InvoiceMapper invoiceMapper;
    private final FieldSuggestionService fieldSuggestionService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        return invoiceRepository.findAll().stream()
                .map(invoiceMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(Long id) {
        return invoiceMapper.toResponse(getOrThrow(id));
    }

    /**
     * Last visit date per free-text vehicle label (see vehicleName), used by the "Автомобіль"
     * picker on the invoice form. Matching is a best-effort exact string match against the same
     * label format the form itself generates — there is no FK between InvoiceItem and Vehicle.
     */
    @Transactional(readOnly = true)
    public Map<String, LocalDateTime> findLastVisitDatesByClientId(Long clientId) {
        return invoiceRepository.findVehicleVisitDatesByClientId(clientId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (LocalDateTime) row[1],
                        (a, b) -> a.isAfter(b) ? a : b));
    }

    public InvoiceResponse create(InvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setNumber("%06d".formatted(invoiceRepository.nextNumberValue()));
        invoice.setPaymentType(request.paymentType());
        invoice.setDocumentDate(LocalDateTime.now());
        invoice.setClient(getClientOrThrow(request.clientId()));
        applyNaryadFields(invoice, request);
        applyItems(invoice, request.items());
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record(AuditAction.CREATE, "Invoice", saved.getId());
        return invoiceMapper.toResponse(saved);
    }

    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = getOrThrow(id);
        Map<String, String[]> changes = FieldDiff.builder()
                .add("Тип оплати", invoice.getPaymentType(), request.paymentType())
                .add("Автомобіль", invoice.getVehicleName(), request.vehicleName())
                .add("Водій", invoice.getDriverName(), request.driverName())
                .add("Відповідальний за ремонт", invoice.getRepairResponsibleName(), request.repairResponsibleName())
                .add("Керівник ремонту", invoice.getRepairSupervisorName(), request.repairSupervisorName())
                .build();
        invoice.setPaymentType(request.paymentType());
        invoice.setClient(getClientOrThrow(request.clientId()));
        applyNaryadFields(invoice, request);
        invoice.getItems().clear();
        applyItems(invoice, request.items());
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record(AuditAction.UPDATE, "Invoice", saved.getId(), changes);
        return invoiceMapper.toResponse(saved);
    }

    private void applyNaryadFields(Invoice invoice, InvoiceRequest request) {
        invoice.setVehicleName(request.vehicleName());
        invoice.setDriverName(request.driverName());
        invoice.setRepairResponsibleName(request.repairResponsibleName());
        invoice.setRepairSupervisorName(request.repairSupervisorName());

        fieldSuggestionService.remember(FieldSuggestionCategory.VEHICLE, request.vehicleName());
    }

    public void softDelete(Long id) {
        Invoice invoice = getOrThrow(id);
        invoice.setDeletedAt(Instant.now());
        invoiceRepository.save(invoice);
        auditLogService.record(AuditAction.DELETE, "Invoice", id);
    }

    private void applyItems(Invoice invoice, List<InvoiceItemRequest> itemRequests) {
        if (itemRequests == null) {
            return;
        }
        int lineNumber = 1;
        for (InvoiceItemRequest itemRequest : itemRequests) {
            if (itemRequest.catalogItemId() == null) {
                continue;
            }
            CatalogItem catalogItem = catalogItemRepository.findById(itemRequest.catalogItemId())
                    .orElseThrow(() -> new NotFoundException("CatalogItem %d not found".formatted(itemRequest.catalogItemId())));

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setCatalogItem(catalogItem);
            item.setLineNumber(lineNumber++);
            item.setItemName(catalogItem.getName());
            item.setQuantity(itemRequest.quantity());
            item.setPrice(itemRequest.price());
            item.setAmount(itemRequest.quantity().multiply(itemRequest.price()));
            invoice.getItems().add(item);
        }
    }

    private Invoice getOrThrow(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice %d not found".formatted(id)));
    }

    private Client getClientOrThrow(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(clientId)));
    }
}
