package com.example.protaxo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.client.web.ClientPageController;
import com.example.protaxo.driver.service.DriverService;
import com.example.protaxo.maintenance.service.MaintenanceModeService;
import com.example.protaxo.security.config.SecurityConfig;
import com.example.protaxo.security.listener.AuthAuditRecorder;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.DeveloperAccounts;
import com.example.protaxo.security.service.LoginAttemptService;
import com.example.protaxo.security.service.PermissionService;
import com.example.protaxo.vehicle.service.VehicleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Форма контрагента: реквізити для договору, список «Діє на підставі», email необов'язковий. */
@WebMvcTest(ClientPageController.class)
@Import(SecurityConfig.class)
class ClientRequisitesPagesTest {

    @Autowired MockMvc mvc;

    @MockitoBean ClientService clientService;
    @MockitoBean DriverService driverService;
    @MockitoBean VehicleService vehicleService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean AuthAuditRecorder authAuditRecorder;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MaintenanceModeService maintenanceModeService;
    @MockitoBean DeveloperAccounts developerAccounts;
    /** @EnableJpaAuditing на ProtaxoApplication вимагає JPA-контексту, якого в зрізі WebMvc немає. */
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void newClientFormHasRequisitesAndBasisDropdownAndOptionalEmail() throws Exception {
        mvc.perform(get("/clients/new").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Реквізити для договору"),
                        containsString("name=\"legalAddress\""),
                        containsString("name=\"iban\""),
                        containsString("name=\"mfo\""),
                        containsString("id=\"basisKind\""),
                        containsString("Модельного статуту"),
                        containsString("Довіреності"),
                        containsString("type=\"hidden\" id=\"signatoryBasis\""),
                        not(containsString("type=\"email\" id=\"email\" name=\"email\" value=\"\" required")))));
    }

    @Test
    void savesWithoutEmailAndWithNormalizedIban() throws Exception {
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("name", "Карпатський Перевізник").param("fullName", "ТОВ \"Карпатський Перевізник\"")
                        .param("edrpou", "44556677").param("code", "445566771234")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро").param("middleName", "Васильович")
                        .param("email", "")
                        .param("iban", "ua21 3223 1300 0002 6007 2335 6600 1").param("mfo", "305 299")
                        .param("signatoryPosition", "Директор").param("signatoryBasis", "Довіреності № 15 від 03.11.2026"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).create(request.capture());
        assertThat(request.getValue().email()).isNull();
        assertThat(request.getValue().iban()).isEqualTo("UA213223130000026007233566001");
        assertThat(request.getValue().mfo()).isEqualTo("305299");
        assertThat(request.getValue().signatoryBasis()).isEqualTo("Довіреності № 15 від 03.11.2026");
    }

    @Test
    void editFormCarriesStoredBasis() throws Exception {
        when(clientService.findById(5L)).thenReturn(new ClientResponse(5L, "К", "44556677", "ТОВ К", null,
                "+38(067)-123-45-67", "445566771234", "Іваненко", "Петро", "Васильович", null, null, null, null,
                null, null, false, null, null, null, null, "Директор", "Наказу № 7 від 01.02.2026"));
        when(driverService.findByClientId(5L)).thenReturn(List.of());
        when(vehicleService.findByClientId(5L)).thenReturn(List.of());
        mvc.perform(get("/clients/5/edit").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Наказу № 7 від 01.02.2026\"")));
    }
}
