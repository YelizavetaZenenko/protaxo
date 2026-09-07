package com.example.protaxo.calibration.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.calibration.dto.CalibrationProtocolRequest;
import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.entity.CalibrationProtocol;
import com.example.protaxo.calibration.mapper.CalibrationProtocolMapper;
import com.example.protaxo.calibration.repository.CalibrationProtocolRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.invoice.repository.InvoiceRepository;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import com.example.protaxo.suggestion.service.FieldSuggestionService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CalibrationProtocolService {

    private final CalibrationProtocolRepository calibrationProtocolRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final CalibrationProtocolMapper calibrationProtocolMapper;
    private final FieldSuggestionService fieldSuggestionService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CalibrationProtocolResponse> findAll() {
        return calibrationProtocolRepository.findAll().stream()
                .map(calibrationProtocolMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CalibrationProtocolResponse findById(Long id) {
        return calibrationProtocolMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Optional<CalibrationProtocolResponse> findByInvoiceId(Long invoiceId) {
        return calibrationProtocolRepository.findByInvoiceId(invoiceId).map(calibrationProtocolMapper::toResponse);
    }

    public CalibrationProtocolResponse create(CalibrationProtocolRequest request) {
        CalibrationProtocol protocol = new CalibrationProtocol();
        protocol.setProtocolDate(LocalDateTime.now());
        Invoice invoice = null;
        if (request.invoiceId() != null) {
            if (calibrationProtocolRepository.findByInvoiceId(request.invoiceId()).isPresent()) {
                throw new BusinessRuleException("Для цього наряд-заказу протокол уже створено");
            }
            invoice = getInvoiceOrThrow(request.invoiceId());
            protocol.setInvoice(invoice);
        }
        applyFields(protocol, request, invoice);
        CalibrationProtocol saved = calibrationProtocolRepository.save(protocol);
        auditLogService.record(AuditAction.CREATE, "CalibrationProtocol", saved.getId());
        return calibrationProtocolMapper.toResponse(saved);
    }

    public CalibrationProtocolResponse update(Long id, CalibrationProtocolRequest request) {
        CalibrationProtocol protocol = getOrThrow(id);
        // client/vehicle/internal number are locked read-only in the UI once saved, and the
        // invoice link is permanent — `invoice` is deliberately never touched again here.
        applyFields(protocol, request, protocol.getInvoice());
        CalibrationProtocol saved = calibrationProtocolRepository.save(protocol);
        auditLogService.record(AuditAction.UPDATE, "CalibrationProtocol", saved.getId());
        return calibrationProtocolMapper.toResponse(saved);
    }

    public void softDelete(Long id) {
        CalibrationProtocol protocol = getOrThrow(id);
        protocol.setDeletedAt(Instant.now());
        calibrationProtocolRepository.save(protocol);
        auditLogService.record(AuditAction.DELETE, "CalibrationProtocol", id);
    }

    /**
     * When the protocol is linked to a Наряд-заказ, its internal number is always the invoice's
     * own number — one unified system, not two independently typed numbers that could drift
     * apart. Enforced here server-side (not just readonly in the UI) so it can't be bypassed.
     */
    private void applyFields(CalibrationProtocol protocol, CalibrationProtocolRequest request, Invoice linkedInvoice) {
        protocol.setInternalNumber(linkedInvoice != null ? linkedInvoice.getNumber() : request.internalNumber());
        protocol.setStampNumber(request.stampNumber());
        protocol.setClient(getClientOrThrow(request.clientId()));
        protocol.setVehicleName(request.vehicleName());
        protocol.setCardNumber(request.cardNumber());
        protocol.setRepresentativeName(request.representativeName());
        protocol.setTachographBrand(request.tachographBrand());
        protocol.setTachographModel(request.tachographModel());
        protocol.setTachographType(request.tachographType());
        protocol.setTachographManufacturer(request.tachographManufacturer());
        protocol.setPreviousInspectionDate(request.previousInspectionDate());

        fieldSuggestionService.remember(FieldSuggestionCategory.VEHICLE, request.vehicleName());
        fieldSuggestionService.remember(FieldSuggestionCategory.REPRESENTATIVE, request.representativeName());
    }

    private CalibrationProtocol getOrThrow(Long id) {
        return calibrationProtocolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CalibrationProtocol %d not found".formatted(id)));
    }

    private Client getClientOrThrow(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client %d not found".formatted(clientId)));
    }

    private Invoice getInvoiceOrThrow(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice %d not found".formatted(invoiceId)));
    }
}
