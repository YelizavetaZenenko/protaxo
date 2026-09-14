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
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.invoice.repository.InvoiceRepository;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import com.example.protaxo.suggestion.service.FieldSuggestionService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CalibrationProtocolService {

    /**
     * "7. Перевірка та адаптація тахографа до транспортного засобу здійснюються за допомогою:" —
     * a fixed line on the official form (see samples/ПРОТОКОЛ.pdf), same equipment for every
     * protocol at this workshop. Not a form field — {@code request.checkMethod()} is ignored,
     * this constant always wins (same "server always wins" pattern as internalNumber when a
     * protocol is linked to an invoice).
     */
    private static final String CHECK_METHOD =
            "пристрою аналогічного відтворення параметрів руху транспортного засобу із застосуванням устаткування";

    /**
     * "Номер штампу" is fixed for this workshop — not a form field, {@code request.stampNumber()}
     * is ignored, same "server always wins" treatment as {@link #CHECK_METHOD}.
     */
    private static final String STAMP_NUMBER = "UA-999";

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
        Map<String, String[]> changes = buildChanges(protocol, request);
        applyFields(protocol, request, protocol.getInvoice());
        CalibrationProtocol saved = calibrationProtocolRepository.save(protocol);
        auditLogService.record(AuditAction.UPDATE, "CalibrationProtocol", saved.getId(), changes);
        return calibrationProtocolMapper.toResponse(saved);
    }

    private Map<String, String[]> buildChanges(CalibrationProtocol p, CalibrationProtocolRequest r) {
        return FieldDiff.builder()
                .add("Автомобіль", p.getVehicleName(), r.vehicleName())
                .add("Номер картки", p.getCardNumber(), r.cardNumber())
                .add("Представник", p.getRepresentativeName(), r.representativeName())
                .add("Марка тахографа", p.getTachographBrand(), r.tachographBrand())
                .add("Модель тахографа", p.getTachographModel(), r.tachographModel())
                .add("Тип тахографа", p.getTachographType(), r.tachographType())
                .add("Виробник тахографа", p.getTachographManufacturer(), r.tachographManufacturer())
                .add("Дата попередньої перевірки", p.getPreviousInspectionDate(), r.previousInspectionDate())
                .add("Держномер ТЗ", p.getVehicleVrn(), r.vehicleVrn())
                .add("VIN ТЗ", p.getVehicleVin(), r.vehicleVin())
                .add("Заводський номер тахографа", p.getTachographSerialNumber(), r.tachographSerialNumber())
                .add("Рік випуску тахографа", p.getTachographManufactureYear(), r.tachographManufactureYear())
                .add("Причина перевірки", p.getInspectionReason(), r.inspectionReason())
                .add("Пробіг до", p.getMileageBefore(), r.mileageBefore())
                .add("Пробіг після", p.getMileageAfter(), r.mileageAfter())
                .add("Розмір шини", p.getTireSize(), r.tireSize())
                .add("Тиск у шинах", p.getTirePressure(), r.tirePressure())
                .add("Довжина кола шини L", p.getTireCircumferenceL(), r.tireCircumferenceL())
                .add("Коефіцієнт W", p.getCoefficientW(), r.coefficientW())
                .add("Константа K", p.getConstantK(), r.constantK())
                .add("Відхилення шляху після встановлення", p.getPathDeviationAfterInstall(), r.pathDeviationAfterInstall())
                .add("Відхилення шляху в експлуатації", p.getPathDeviationInService(), r.pathDeviationInService())
                .add("Відхилення швидкості після встановлення", p.getSpeedDeviationAfterInstall(), r.speedDeviationAfterInstall())
                .add("Відхилення швидкості в експлуатації", p.getSpeedDeviationInService(), r.speedDeviationInService())
                .add("Відхилення часу після встановлення", p.getTimeDeviationAfterInstall(), r.timeDeviationAfterInstall())
                .add("Відхилення часу в експлуатації", p.getTimeDeviationInService(), r.timeDeviationInService())
                .add("Значення обмежувача швидкості", p.getSpeedLimiterValue(), r.speedLimiterValue())
                .add("Зафіксовано відкриття кришки", p.getCoverOpeningRegistered(), r.coverOpeningRegistered())
                .add("Зафіксовано відключення живлення", p.getPowerCutoffRegistered(), r.powerCutoffRegistered())
                .add("Зафіксовано переривання імпульсного датчика", p.getPulseSensorInterruptionRegistered(), r.pulseSensorInterruptionRegistered())
                .add("Посада виконавця", p.getExecutorPosition(), r.executorPosition())
                .add("ПІБ виконавця", p.getExecutorName(), r.executorName())
                .build();
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
        protocol.setStampNumber(STAMP_NUMBER);
        protocol.setClient(getClientOrThrow(request.clientId()));
        protocol.setVehicleName(request.vehicleName());
        protocol.setCardNumber(request.cardNumber());
        protocol.setRepresentativeName(request.representativeName());
        protocol.setTachographBrand(request.tachographBrand());
        protocol.setTachographModel(request.tachographModel());
        protocol.setTachographType(request.tachographType());
        protocol.setTachographManufacturer(request.tachographManufacturer());
        protocol.setPreviousInspectionDate(request.previousInspectionDate());
        protocol.setVehicleVrn(request.vehicleVrn());
        protocol.setVehicleVin(request.vehicleVin());
        protocol.setTachographSerialNumber(request.tachographSerialNumber());
        protocol.setTachographManufactureYear(request.tachographManufactureYear());
        protocol.setInspectionReason(request.inspectionReason());
        protocol.setCheckMethod(CHECK_METHOD);
        protocol.setMileageBefore(request.mileageBefore());
        protocol.setMileageAfter(request.mileageAfter());
        protocol.setTireSize(request.tireSize());
        protocol.setTirePressure(request.tirePressure());
        protocol.setTireCircumferenceL(request.tireCircumferenceL());
        protocol.setCoefficientW(request.coefficientW());
        protocol.setConstantK(request.constantK());
        protocol.setPathDeviationAfterInstall(request.pathDeviationAfterInstall());
        protocol.setPathDeviationInService(request.pathDeviationInService());
        protocol.setSpeedDeviationAfterInstall(request.speedDeviationAfterInstall());
        protocol.setSpeedDeviationInService(request.speedDeviationInService());
        protocol.setTimeDeviationAfterInstall(request.timeDeviationAfterInstall());
        protocol.setTimeDeviationInService(request.timeDeviationInService());
        protocol.setSpeedLimiterValue(request.speedLimiterValue());
        protocol.setCoverOpeningRegistered(request.coverOpeningRegistered());
        protocol.setPowerCutoffRegistered(request.powerCutoffRegistered());
        protocol.setPulseSensorInterruptionRegistered(request.pulseSensorInterruptionRegistered());
        protocol.setExecutorPosition(request.executorPosition());
        protocol.setExecutorName(request.executorName());

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
