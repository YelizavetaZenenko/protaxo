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

import com.example.protaxo.address.AddressDirectoryService;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.bank.Bank;
import com.example.protaxo.bank.BankService;
import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.CustomLegalForm;
import com.example.protaxo.client.entity.LegalForm;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.client.service.CustomLegalFormService;
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
    @MockitoBean BankService bankService;
    @MockitoBean AddressDirectoryService addressDirectoryService;
    @MockitoBean CustomLegalFormService customLegalFormService;
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
        when(bankService.findAll()).thenReturn(List.of(
                Bank.builder().id(1L).name("АТ «ПУМБ»").mfo("334851").build(),
                Bank.builder().id(2L).name("АТ КБ «ПриватБанк»").mfo("305299").build()));
        when(addressDirectoryService.regions()).thenReturn(List.of(
                new AddressDirectoryService.Region("UA07000000000024379", "Волинська", "O")));
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
                        containsString("type=\"text\" id=\"bankName\""),
                        containsString("\"mfo\":\"334851\""),
                        containsString("АТ КБ «ПриватБанк»"),
                        containsString("id=\"addBankBtn\""),
                        containsString("/js/autocomplete.js"),
                        containsString("Волинська обл."),
                        containsString("id=\"addressSettlement\""),
                        containsString("name=\"addressSettlementCode\""),
                        containsString("name=\"addressPostalCode\""),
                        containsString("id=\"bankModalOverlay\""),
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
                        .param("signatoryPosition", "Директор").param("signatoryBasis", "Довіреності № 15 від 03.11.2026")
                        .param("extraAccounts[0].iban", "ua 8933 4851 0000 0000 2600 1234 567")
                        .param("extraAccounts[0].bankName", "АТ «ПУМБ»").param("extraAccounts[0].mfo", "334851"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<com.example.protaxo.client.dto.ClientBankAccountData>> accounts =
                ArgumentCaptor.forClass(java.util.List.class);
        verify(clientService).create(request.capture(), accounts.capture());
        assertThat(accounts.getValue()).hasSize(1);
        assertThat(accounts.getValue().get(0).getIban()).isEqualTo("UA893348510000000026001234567");
        assertThat(accounts.getValue().get(0).getBankName()).isEqualTo("АТ «ПУМБ»");
        assertThat(request.getValue().email()).isNull();
        assertThat(request.getValue().iban()).isEqualTo("UA213223130000026007233566001");
        assertThat(request.getValue().mfo()).isEqualTo("305299");
        assertThat(request.getValue().signatoryBasis()).isEqualTo("Довіреності № 15 від 03.11.2026");
    }

    @Test
    void editFormCarriesStoredBasis() throws Exception {
        when(clientService.findById(5L)).thenReturn(new ClientResponse(5L, "К", "44556677", "ТОВ К", null,
                "+38(067)-123-45-67", "445566771234", "Іваненко", "Петро", "Васильович", null, null, null, null,
                null, null, false, null, null, null, null, "Директор", "Наказу № 7 від 01.02.2026",
                null, null, null, null, null, null, null));
        when(driverService.findByClientId(5L)).thenReturn(List.of());
        when(vehicleService.findByClientId(5L)).thenReturn(List.of());
        mvc.perform(get("/clients/5/edit").with(user("a@b.c").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Наказу № 7 від 01.02.2026\"")));
    }

    /** ФОП: назва — ПІБ без лапок, ІПН (РНОКПП) 10 цифр, код ЄДРПОУ береться той самий. */
    @Test
    void fopTakesEdrpouFromIpnAndComposesNames() throws Exception {
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("legalForm", "FOP").param("name", "Іваненко Петро Васильович")
                        .param("edrpou", "").param("code", "3809602220")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро").param("middleName", "Васильович"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).create(request.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(request.getValue().name()).isEqualTo("ФОП Іваненко Петро Васильович");
        assertThat(request.getValue().fullName()).isEqualTo("Фізична особа-підприємець Іваненко Петро Васильович");
        assertThat(request.getValue().edrpou()).isEqualTo("3809602220");
        assertThat(request.getValue().code()).isEqualTo("3809602220");
    }

    /** Коди й по батькові необов'язкові — і для юрособи, і для ФОП; у базу йде null, не порожній рядок. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"TOV", "FOP", ""})
    void savesWithoutCodesAndMiddleName(String legalForm) throws Exception {
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("legalForm", legalForm).param("name", "Іваненко Петро")
                        .param("edrpou", "").param("code", "")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро").param("middleName", ""))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).create(request.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(request.getValue().edrpou()).isNull();
        assertThat(request.getValue().code()).isNull();
        assertThat(request.getValue().middleName()).isNull();
    }

    @Test
    void fopRejectsTwelveDigitIpn() throws Exception {
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("legalForm", "FOP").param("name", "Іваненко Петро Васильович")
                        .param("code", "380960222012")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро").param("middleName", "Васильович"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("У ФОП код ІПН (РНОКПП) — рівно 10 цифр")));
    }

    /** ТОВ: лапки, які ввели самі, не подвоюються; повна назва — з повної форми власності. */
    @Test
    void tovComposesShortAndFullNames() throws Exception {
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("legalForm", "TOV").param("name", "\"Карпатський Перевізник\"")
                        .param("edrpou", "44556677").param("code", "445566771234")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро").param("middleName", "Васильович"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).create(request.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(request.getValue().name()).isEqualTo("ТОВ «Карпатський Перевізник»");
        assertThat(request.getValue().fullName()).isEqualTo("Товариство з обмеженою відповідальністю «Карпатський Перевізник»");
    }

    /** ТзОВ — та сама повна форма, що й ТОВ, лише інше скорочення. */
    @Test
    void tzovComposesShortAndFullNames() throws Exception {
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("legalForm", "TZOV").param("name", "Агро")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).create(request.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(request.getValue().name()).isEqualTo("ТзОВ «Агро»");
        assertThat(request.getValue().fullName()).isEqualTo("Товариство з обмеженою відповідальністю «Агро»");
        assertThat(request.getValue().legalForm()).isEqualTo(LegalForm.TZOV);
    }

    /** Своя форма з довідника (V81): значення «C<id>» → CUSTOM + id, назва складається з її скорочення й повної назви. */
    @Test
    void customLegalFormComposesNamesAndKeepsItsId() throws Exception {
        when(customLegalFormService.findById(7L)).thenReturn(java.util.Optional.of(
                CustomLegalForm.builder().id(7L).shortLabel("НВП").fullLabel("Науково-виробниче підприємство").build()));
        mvc.perform(post("/clients").with(user("a@b.c").roles("ADMIN")).with(csrf())
                        .param("legalForm", "C7").param("name", "Тахосервіс")
                        .param("contactPersonPhone", "+38(067)-123-45-67")
                        .param("lastName", "Іваненко").param("firstName", "Петро"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ClientRequest> request = ArgumentCaptor.forClass(ClientRequest.class);
        verify(clientService).create(request.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(request.getValue().name()).isEqualTo("НВП «Тахосервіс»");
        assertThat(request.getValue().fullName()).isEqualTo("Науково-виробниче підприємство «Тахосервіс»");
        assertThat(request.getValue().legalForm()).isEqualTo(LegalForm.CUSTOM);
        assertThat(request.getValue().customLegalFormId()).isEqualTo(7L);
    }
}
