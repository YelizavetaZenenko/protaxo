package com.example.protaxo.calendar;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.maintenance.service.MaintenanceModeService;
import com.example.protaxo.security.config.SecurityConfig;
import com.example.protaxo.security.listener.AuthAuditRecorder;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.DeveloperAccounts;
import com.example.protaxo.security.service.LoginAttemptService;
import com.example.protaxo.security.service.PermissionService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** «Календар»: сторінка й API записів — адміністратор і майстер; бухгалтеру закрито. */
@WebMvcTest({CalendarPageController.class, AppointmentController.class})
@Import(SecurityConfig.class)
class CalendarPageTest {

    @Autowired MockMvc mvc;

    @MockitoBean ClientService clientService;
    @MockitoBean VehicleService vehicleService;
    @MockitoBean AppointmentService appointmentService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean AuthAuditRecorder authAuditRecorder;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MaintenanceModeService maintenanceModeService;
    @MockitoBean DeveloperAccounts developerAccounts;
    /** @EnableJpaAuditing на ProtaxoApplication вимагає JPA-контексту, якого в зрізі WebMvc немає. */
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

    private static ClientResponse client() {
        return new ClientResponse(5L, "Волинь Зерно Продукт", "31496816", null, null, null, null, null, null, null,
                null, null, null, null, "+38(095)-995-44-16", null, false, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void masterSeesCalendarWithClientDirectory() throws Exception {
        when(clientService.findAll()).thenReturn(List.of(client()));
        when(vehicleService.findAll()).thenReturn(List.of(
                new VehicleResponse(1L, 5L, "YV2RT40A0KB123456", "AC1466HI", null, "Volvo", "FH", 2019)));
        mvc.perform(get("/calendar").with(user("m@b.c").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"calBody\""),
                        containsString("id=\"calViewWeek\""),
                        containsString("id=\"calDayToggles\""),
                        containsString("id=\"apptDurH\""),
                        containsString("href=\"/calendar\""),
                        containsString("Волинь Зерно Продукт"),
                        containsString("AC1466HI (Volvo FH)"))));
    }

    @Test
    void appointmentsApi() throws Exception {
        when(appointmentService.findBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 7))).thenReturn(List.of(
                new AppointmentResponse(3L, LocalDateTime.of(2026, 11, 24, 9, 0), 60, 5L, "Волинь Зерно Продукт",
                        null, "AC1466HI", null)));
        mvc.perform(get("/api/appointments?from=2026-10-26&to=2026-12-07").with(user("m@b.c").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientName").value("Волинь Зерно Продукт"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-24T09:00:00"));

        mvc.perform(delete("/api/appointments/3").with(user("m@b.c").roles("MASTER")))
                .andExpect(status().isNoContent());
        verify(appointmentService).delete(3L);
    }

    @Test
    void accountantHasNoAccess() throws Exception {
        mvc.perform(get("/calendar").with(user("acc@b.c").roles("ACCOUNTANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments?from=2026-11-01&to=2026-12-01").with(user("acc@b.c").roles("ACCOUNTANT")))
                .andExpect(status().isForbidden());
    }
}
