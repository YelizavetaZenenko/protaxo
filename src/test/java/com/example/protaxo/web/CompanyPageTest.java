package com.example.protaxo.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.calibration.dto.MasterCardResponse;
import com.example.protaxo.calibration.service.MasterCardService;
import com.example.protaxo.maintenance.service.MaintenanceModeService;
import com.example.protaxo.security.config.SecurityConfig;
import com.example.protaxo.security.listener.AuthAuditRecorder;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.DeveloperAccounts;
import com.example.protaxo.security.service.LoginAttemptService;
import com.example.protaxo.security.service.PermissionService;
import com.example.protaxo.security.service.UserManagementService;
import com.example.protaxo.security.entity.PermissionKey;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.dto.UserSummary;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import com.example.protaxo.worker.dto.RepairWorkerDetails;
import com.example.protaxo.worker.service.RepairWorkerService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** «Основна інформація»: робітники й картки майстерні; кнопки змін — лише адміністратору. */
@WebMvcTest(CompanyPageController.class)
@Import(SecurityConfig.class)
class CompanyPageTest {

    @Autowired MockMvc mvc;

    @MockitoBean RepairWorkerService repairWorkerService;
    @MockitoBean MasterCardService masterCardService;
    @MockitoBean UserManagementService userManagementService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean AuthAuditRecorder authAuditRecorder;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MaintenanceModeService maintenanceModeService;
    @MockitoBean DeveloperAccounts developerAccounts;
    /** @EnableJpaAuditing на ProtaxoApplication вимагає JPA-контексту, якого в зрізі WebMvc немає. */
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

    private void data() {
        when(repairWorkerService.findAllDetails()).thenReturn(List.of(
                new RepairWorkerDetails(1L, "Петренко Іван", "Майстер", null, null, null, null, null, null)));
        when(masterCardService.findAll()).thenReturn(List.of(new MasterCardResponse(7L, "UAW123456789012", "Вишивата Д.О.")));
    }

    @Test
    void adminSeesWorkersAndCardsWithEditButtons() throws Exception {
        data();
        mvc.perform(get("/company").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Основна інформація"),
                        containsString("Петренко Іван"),
                        containsString("UAW123456789012"),
                        containsString("Вишивата Д.О."),
                        containsString("edit-card-btn"),
                        containsString("delete-card-btn"),
                        containsString("addWorkerBtn"),
                        not(containsString("ProTaxo ERP")))));
    }

    @Test
    void usersAreShownOnlyWithManageUsersPermission() throws Exception {
        data();
        when(permissionService.hasPermission(any(), eq(PermissionKey.CAN_MANAGE_USERS))).thenReturn(true);
        when(userManagementService.findAll()).thenReturn(List.of(userSummary()));
        mvc.perform(get("/company").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"users\""),
                        containsString("Запросити користувача"),
                        containsString("lena@example.com"))));
    }

    private static UserSummary userSummary() {
        return new UserSummary(3L, "Олена Коваль", "lena@example.com", Role.MASTER, true, null);
    }

    @Test
    void masterSeesButCannotEdit() throws Exception {
        data();
        mvc.perform(get("/company").with(user("m@b.c").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("UAW123456789012"),
                        not(containsString("id=\"users\"")),
                        not(containsString("class=\"link-btn edit-card-btn\"")),
                        not(containsString("id=\"addCardBtn\"")))));
    }

    @Test
    void accountantHasNoAccess() throws Exception {
        mvc.perform(get("/company").with(user("acc@b.c").roles("ACCOUNTANT")))
                .andExpect(status().isForbidden());
    }
}
