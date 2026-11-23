package com.example.protaxo.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Записи календаря без бази: ім'я з довідника, обрізання пробілів, межі періоду, журнал дій. */
class AppointmentServiceTest {

    private final AppointmentRepository repository = mock(AppointmentRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final AppointmentService service = new AppointmentService(repository, clientRepository, audit);

    private static AppointmentRequest request(Long clientId, String name) {
        return new AppointmentRequest(LocalDateTime.of(2026, 11, 24, 9, 30, 15), 60, clientId, name,
                "  ", " AC1466HI ", null);
    }

    @Test
    void createTakesNameFromDirectoryAndTrims() {
        Client client = new Client();
        client.setName("Волинь Зерно Продукт");
        when(clientRepository.findById(7L)).thenReturn(Optional.of(client));
        when(repository.save(any())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        AppointmentResponse saved = service.create(request(7L, "волинь"));

        assertThat(saved.clientName()).isEqualTo("Волинь Зерно Продукт");
        assertThat(saved.clientId()).isEqualTo(7L);
        assertThat(saved.startsAt()).isEqualTo(LocalDateTime.of(2026, 11, 24, 9, 30));
        assertThat(saved.phone()).isNull();
        assertThat(saved.vehicle()).isEqualTo("AC1466HI");
        verify(audit).record(eq(AuditAction.CREATE), eq("Appointment"), eq(1L), anyMap());
    }

    @Test
    void createWithoutDirectoryKeepsTypedName() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(request(null, "  Петро, тел. сам  ")).clientName()).isEqualTo("Петро, тел. сам");
    }

    @Test
    void unknownClientIsRejected() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(99L, "x"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rangeIsValidated() {
        LocalDate d = LocalDate.of(2026, 11, 1);
        assertThatThrownBy(() -> service.findBetween(d, d)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.findBetween(d, d.plusDays(200))).isInstanceOf(BusinessRuleException.class);
        when(repository.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                d.atStartOfDay(), d.plusDays(42).atStartOfDay())).thenReturn(List.of());
        assertThat(service.findBetween(d, d.plusDays(42))).isEmpty();
    }
}
