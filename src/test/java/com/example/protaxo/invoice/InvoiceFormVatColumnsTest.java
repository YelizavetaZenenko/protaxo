package com.example.protaxo.invoice;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.calibration.service.CalibrationProtocolService;
import com.example.protaxo.catalog.service.CatalogItemService;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.contract.service.ContractService;
import com.example.protaxo.finance.entity.FinanceSettings;
import com.example.protaxo.finance.service.FinanceQueryService;
import com.example.protaxo.finance.service.FinanceSettingsService;
import com.example.protaxo.invoice.service.InvoiceService;
import com.example.protaxo.invoice.web.InvoicePageController;
import com.example.protaxo.maintenance.service.MaintenanceModeService;
import com.example.protaxo.pdf.service.PdfRenderService;
import com.example.protaxo.security.config.SecurityConfig;
import com.example.protaxo.security.listener.AuthAuditRecorder;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.DeveloperAccounts;
import com.example.protaxo.security.service.LoginAttemptService;
import com.example.protaxo.security.service.PermissionService;
import com.example.protaxo.vehicle.service.VehicleService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.entity.CatalogUnit;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.finance.dto.InvoiceSettlement;
import com.example.protaxo.invoice.dto.InvoiceItemResponse;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.ActStatus;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import java.math.BigDecimal;
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

/**
 * Новий наряд-заказ: у неплатника ПДВ стовпці «Ціна» і сірий довідковий «Ціна з ПДВ» замість
 * «Ціна з ПДВ» / «ПДВ». Щоб подивитися сторінку: {@code -Dinvoice.form.out=файл.html}.
 */
@WebMvcTest(InvoicePageController.class)
@Import(SecurityConfig.class)
class InvoiceFormVatColumnsTest {

    @Autowired MockMvc mvc;

    @MockitoBean InvoiceService invoiceService;
    @MockitoBean ClientService clientService;
    @MockitoBean CatalogItemService catalogItemService;
    @MockitoBean VehicleService vehicleService;
    @MockitoBean CalibrationProtocolService calibrationProtocolService;
    @MockitoBean ContractService contractService;
    @MockitoBean PdfRenderService pdfRenderService;
    @MockitoBean FinanceSettingsService financeSettingsService;
    @MockitoBean FinanceQueryService financeQueryService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean AuthAuditRecorder authAuditRecorder;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MaintenanceModeService maintenanceModeService;
    @MockitoBean DeveloperAccounts developerAccounts;
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

    private String page(boolean vatPayer) throws Exception {
        FinanceSettings settings = new FinanceSettings();
        settings.setVatPayer(vatPayer);
        when(financeSettingsService.get()).thenReturn(settings);
        when(catalogItemService.findAll()).thenReturn(List.of());
        when(clientService.findAll()).thenReturn(List.of());
        return mvc.perform(get("/invoices/new").with(user("m@b.c").roles("MASTER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void nonVatPayerSeesPriceAndGreyVatPrice() throws Exception {
        String html = page(false);
        String out = System.getProperty("invoice.form.out");
        if (out != null) {
            Files.writeString(Path.of(out), html);
        }
        org.hamcrest.MatcherAssert.assertThat(html, allOf(
                containsString("non-vat-payer"),
                containsString("data-vat-payer=\"false\""),
                containsString(">Ціна</th>"),
                containsString("vat-cell\" style=\"width:11%\" title=\"Довідково"),
                not(containsString(">ПДВ</th>"))));
    }

    @Test
    void vatPayerKeepsVatColumns() throws Exception {
        String html = page(true);
        org.hamcrest.MatcherAssert.assertThat(html, allOf(
                not(containsString("non-vat-payer")),
                containsString(">Ціна з ПДВ</th>"),
                containsString(">ПДВ</th>")));
    }

    private String viewPage(boolean vatPayer) throws Exception {
        FinanceSettings settings = new FinanceSettings();
        settings.setVatPayer(vatPayer);
        when(financeSettingsService.get()).thenReturn(settings);
        BigDecimal sum = new BigDecimal("350.00");
        BigDecimal zero = new BigDecimal("0.00");
        InvoiceResponse invoice = new InvoiceResponse(1L, "1", LocalDateTime.of(2026, 11, 22, 10, 0),
                InvoicePaymentType.TRANSFER, 1L, null, null, null, null, null,
                List.of(new InvoiceItemResponse(1L, 1, 1L, CatalogItemType.MATERIAL, "Пломба", BigDecimal.ONE, sum, null,
                        sum, VatRate.NO_VAT, zero, sum, CatalogUnit.PIECE)),
                sum, zero, sum, null, ActStatus.NOT_CREATED);
        when(invoiceService.findById(1L)).thenReturn(invoice);
        when(clientService.findById(1L)).thenReturn(new ClientResponse(1L, "Агро", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null, null,
                null, null, null, null, null));
        when(financeQueryService.settlement(invoice)).thenReturn(
                InvoiceSettlement.of(1L, sum, zero, zero, null, LocalDate.of(2026, 11, 25)));
        return mvc.perform(get("/invoices/1").with(user("m@b.c").roles("MASTER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void viewOfNonVatPayerHasNoVat() throws Exception {
        org.hamcrest.MatcherAssert.assertThat(viewPage(false), allOf(
                containsString(">Ціна</th>"), not(containsString("ПДВ"))));
    }

    @Test
    void viewOfVatPayerKeepsVat() throws Exception {
        org.hamcrest.MatcherAssert.assertThat(viewPage(true), allOf(
                containsString(">Ціна з ПДВ</th>"), containsString(">Без ПДВ</th>"), containsString(">ПДВ</th>")));
    }
}
