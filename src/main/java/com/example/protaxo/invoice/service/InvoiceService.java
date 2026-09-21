package com.example.protaxo.invoice.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
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
import java.math.BigDecimal;
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
        restoreStock(invoice.getItems());
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
        restoreStock(invoice.getItems());
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

            deductStock(catalogItem, itemRequest.quantity());
        }
    }

    /** Selling a line item takes its quantity off stockQuantity - only for catalog items that
        actually track stock (MATERIAL; stockQuantity is null for SERVICE and is left alone).
        Rejects the sale outright if it would take stock below zero - by this point in update()
        the old quantities are already restored, so this only ever sees the real, current stock,
        not stock still "reserved" by the document being edited.

        <p>BigDecimal arithmetic throughout (not .intValue()) - quantity has scale 3 (fractional
        units like 2.5 liters are legitimate), and truncating to an int on every sale silently
        drifted stockQuantity upward relative to reality with every fractional-quantity sale. */
    private void deductStock(CatalogItem catalogItem, BigDecimal quantity) {
        BigDecimal available = catalogItem.getStockQuantity();
        if (available == null) {
            return;
        }
        if (quantity.compareTo(available) > 0) {
            throw new BusinessRuleException("Недостатньо на складі: \"%s\" — залишок %s, потрібно %s"
                    .formatted(catalogItem.getName(), display(available), display(quantity)));
        }
        catalogItem.setStockQuantity(available.subtract(quantity));
        catalogItemRepository.save(catalogItem);
    }

    private String display(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Undoes deductStock for items still attached to the invoice - called before re-applying
        items on update, and before soft-deleting the whole document, so stock reflects reality
        instead of drifting down every time the same наряд-заказ is edited.

        <p>Goes straight to a native, id-based update ({@link CatalogItemRepository#restoreStockQuantity})
        instead of loading the CatalogItem entity and re-saving it - the catalog item on an OLD line
        item can have been soft-deleted since the sale (someone tidied up the catalog), and loading
        it through the normal entity path runs into {@code @SQLRestriction("deleted_at IS NULL")},
        which would either silently skip the restore or throw depending on how the association
        happens to be fetched. The native update bypasses that restriction and hits the real row
        directly, so a soft-deleted catalog item's stock still gets restored correctly. Reading
        {@code item.getCatalogItem().getId()} is safe even when the row is gone - a Hibernate proxy's
        identifier is known without initializing/fetching it. */
    private void restoreStock(List<InvoiceItem> items) {
        for (InvoiceItem item : items) {
            CatalogItem catalogItem = item.getCatalogItem();
            if (catalogItem == null) {
                continue;
            }
            catalogItemRepository.restoreStockQuantity(catalogItem.getId(), item.getQuantity());
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
