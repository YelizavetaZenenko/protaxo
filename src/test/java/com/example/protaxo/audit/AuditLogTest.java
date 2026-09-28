package com.example.protaxo.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.entity.AuditLog;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.vehicle.dto.VehicleRequest;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Розширений журнал дій: повні знімки при створенні/видаленні, діф при редагуванні, фільтри сторінки. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogTest {

    @Autowired MockMvc mvc;
    @Autowired VehicleService vehicleService;
    @Autowired ClientRepository clientRepository;
    @Autowired AuditLogService auditLogService;

    MockHttpSession session;
    Client client;
    String plate;

    @BeforeEach
    void setUp() {
        SecurityContext context = new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        client = clientRepository.save(Client.builder().name("Журнал клієнт " + UUID.randomUUID()).build());
        plate = "AA" + (1000 + (int) (Math.random() * 8999)) + "KX";
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUpdateDeleteAreLoggedWithAllFields() {
        String vin = "VIN" + UUID.randomUUID().toString().substring(0, 14).toUpperCase();
        VehicleResponse vehicle = vehicleService.create(new VehicleRequest(client.getId(), vin, plate, null, "MAN", "TGX", 2019));
        vehicleService.update(vehicle.id(), new VehicleRequest(client.getId(), vin, plate, null, "MAN", "TGS", 2019));
        vehicleService.softDelete(vehicle.id());

        List<AuditLog> history = auditLogService.search(
                new AuditLogService.Filter(null, null, "Vehicle", vehicle.id(), null, null, null), PageRequest.of(0, 10)).getContent();
        assertThat(history).extracting(AuditLog::getAction)
                .containsExactly(AuditAction.DELETE, AuditAction.UPDATE, AuditAction.CREATE);

        Map<String, String[]> created = auditLogService.parseChanges(history.get(2).getChanges());
        assertThat(created).containsKeys("Контрагент", "VIN", "Держномер", "Марка", "Модель", "Рік випуску");
        assertThat(created.get("Модель")).containsExactly("", "TGX");

        Map<String, String[]> updated = auditLogService.parseChanges(history.get(1).getChanges());
        assertThat(updated).containsOnlyKeys("Модель");
        assertThat(updated.get("Модель")).containsExactly("TGX", "TGS");

        Map<String, String[]> deleted = auditLogService.parseChanges(history.get(0).getChanges());
        assertThat(deleted.get("Держномер")).containsExactly(plate, "");

        // Позначки «змінено» в таблиці автомобілів беруться лише з редагувань, не зі створення.
        assertThat(auditLogService.findLatestChanges("Vehicle", vehicle.id())).containsOnlyKeys("Модель");
    }

    @Test
    void sameValueInDifferentScaleIsNotAChange() {
        assertThat(FieldDiff.builder().add("Сума", new java.math.BigDecimal("100"), new java.math.BigDecimal("100.00")).build())
                .isEmpty();
    }

    @Test
    void pageFiltersByElementAndSearchText() throws Exception {
        VehicleResponse vehicle = vehicleService.create(new VehicleRequest(client.getId(),
                "VIN" + UUID.randomUUID().toString().substring(0, 14).toUpperCase(), plate, null, "DAF", "XF", 2020));

        String html = mvc.perform(get("/audit-log").session(session)
                        .param("entityType", "Vehicle").param("entityId", vehicle.id().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Історія змін", plate, "Створено", "DAF");

        String search = mvc.perform(get("/audit-log").session(session).param("q", plate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(search).contains(plate);

        mvc.perform(get("/audit-log").session(session).param("action", "LOGIN").param("from", "2026-01-01"))
                .andExpect(status().isOk());
    }
}
