package com.example.protaxo.calendar;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Записи клієнтів у календарі (docs/Календар.md). Кожна зміна — у журналі дій. */
@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentService {

    /** Сітка місяця — 6 тижнів; із запасом, але не «вся база одним запитом». */
    static final int MAX_RANGE_DAYS = 100;
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;
    private final AuditLogService auditLogService;

    /** Записи з {@code from} включно до {@code to} не включно. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findBetween(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            throw new BusinessRuleException("Кінець періоду має бути пізніше за початок");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessRuleException("Завеликий період — не більше " + MAX_RANGE_DAYS + " днів");
        }
        return appointmentRepository
                .findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(from.atStartOfDay(), to.atStartOfDay())
                .stream().map(AppointmentResponse::of).toList();
    }

    public AppointmentResponse create(AppointmentRequest request) {
        Appointment appointment = new Appointment();
        apply(appointment, request);
        Appointment saved = appointmentRepository.save(appointment);
        auditLogService.record(AuditAction.CREATE, "Appointment", saved.getId(), FieldDiff.created(snapshot(saved)));
        return AppointmentResponse.of(saved);
    }

    public AppointmentResponse update(Long id, AppointmentRequest request) {
        Appointment appointment = get(id);
        FieldDiff.Snapshot before = snapshot(appointment);
        apply(appointment, request);
        Appointment saved = appointmentRepository.save(appointment);
        auditLogService.record(AuditAction.UPDATE, "Appointment", id, FieldDiff.between(before, snapshot(saved)));
        return AppointmentResponse.of(saved);
    }

    public void delete(Long id) {
        Appointment appointment = get(id);
        appointmentRepository.delete(appointment);
        auditLogService.record(AuditAction.DELETE, "Appointment", id, FieldDiff.deleted(snapshot(appointment)));
    }

    private Appointment get(Long id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Запис не знайдено: " + id));
    }

    /** Контрагент із довідника — ім'я беремо з довідника; інакше як ввели. */
    private void apply(Appointment a, AppointmentRequest r) {
        String name = r.clientName().trim();
        if (r.clientId() != null) {
            name = clientRepository.findById(r.clientId())
                    .orElseThrow(() -> new BusinessRuleException("Контрагента не знайдено — оберіть зі списку ще раз"))
                    .getName();
        }
        a.setStartsAt(r.startsAt().truncatedTo(ChronoUnit.MINUTES));
        a.setDurationMinutes(r.durationMinutes());
        a.setClientId(r.clientId());
        a.setClientName(name);
        a.setPhone(blankToNull(r.phone()));
        a.setVehicle(blankToNull(r.vehicle()));
        a.setNote(blankToNull(r.note()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static FieldDiff.Snapshot snapshot(Appointment a) {
        return FieldDiff.snapshot()
                .add("Початок", a.getStartsAt() == null ? null : a.getStartsAt().format(SHOWN))
                .add("Тривалість, хв", a.getDurationMinutes())
                .add("Клієнт", a.getClientName())
                .add("Телефон", a.getPhone())
                .add("Автомобіль", a.getVehicle())
                .add("Примітка", a.getNote());
    }
}
