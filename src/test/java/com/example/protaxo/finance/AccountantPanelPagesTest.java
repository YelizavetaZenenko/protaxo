package com.example.protaxo.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.finance.entity.TaxSystem;
import com.example.protaxo.finance.service.FinanceSettingsService;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.ActStatus;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.invoice.service.InvoiceService;
import com.example.protaxo.finance.web.FinanceFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Вкладки панелі бухгалтера рендеряться; стан акта й налаштування обліку зберігаються. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountantPanelPagesTest {

    @Autowired MockMvc mvc;
    @Autowired InvoiceService invoiceService;
    @Autowired FinanceSettingsService settingsService;
    @Autowired ClientRepository clientRepository;
    @Autowired CatalogItemRepository catalogItemRepository;

    MockHttpSession session;
    InvoiceResponse invoice;
    String clientName;

    @BeforeEach
    void setUp() {
        session = sessionFor("ROLE_ACCOUNTANT");
        SecurityContextHolder.setContext(securityContext("ROLE_ACCOUNTANT"));
        clientName = "Панель клієнт " + UUID.randomUUID();
        Client client = clientRepository.save(Client.builder().name(clientName).build());
        CatalogItem item = catalogItemRepository.save(CatalogItem.builder()
                .type(CatalogItemType.SERVICE).name("Послуга " + UUID.randomUUID())
                .basePrice(new BigDecimal("2450")).vatRate(VatRate.VAT_20).build());
        invoice = invoiceService.create(new InvoiceRequest(InvoicePaymentType.CASH, client.getId(), null, null, null, null,
                List.of(new InvoiceItemRequest(item.getId(), BigDecimal.ONE, new BigDecimal("2450"))),
                LocalDate.now().minusDays(1)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allTabsRender() throws Exception {
        String overview = page("/finance");
        assertThat(overview).contains("Панель бухгалтера", "Контроль оплат", "Потребує уваги",
                "Налаштування обліку", clientName, "Прострочено", "+ Додати оплату");
        // Посилання на наряд є лише в таблиці (вікно оплати перелічує борги завжди).
        String rowLink = "href=\"/invoices/" + invoice.id() + "\"";
        assertThat(page("/finance/invoices?filter=overdue")).contains(rowLink);
        assertThat(page("/finance/invoices?filter=paid")).doesNotContain(rowLink);
        assertThat(page("/finance/documents")).contains("Документи за нарядами", "Акт не створено");
        assertThat(page("/finance/expenses")).contains("Витрати сервісу");
        assertThat(page("/finance/settings")).contains("Система оподаткування");
        page("/finance/accounts");
        page("/finance/operations");
        page("/finance/reconciliations");
    }

    @Test
    void actStatusChangesFromDocumentsTab() throws Exception {
        mvc.perform(post("/finance/documents/" + invoice.id() + "/act-status").session(session).with(csrf())
                        .param("status", "SIGNED").param("returnTo", "/finance/documents?pendingOnly=true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/finance/documents?pendingOnly=true"));
        assertThat(invoiceService.findById(invoice.id()).actStatus()).isEqualTo(ActStatus.SIGNED);
    }

    @Test
    void firstActPrintMarksItOnSigning() {
        invoiceService.markActPrinted(invoice.id());
        assertThat(invoiceService.findById(invoice.id()).actStatus()).isEqualTo(ActStatus.ON_SIGNING);
        invoiceService.updateActStatus(invoice.id(), ActStatus.SIGNED);
        invoiceService.markActPrinted(invoice.id());
        assertThat(invoiceService.findById(invoice.id()).actStatus()).isEqualTo(ActStatus.SIGNED);
    }

    @Test
    void settingsAreSavedAndShownInHeader() throws Exception {
        mvc.perform(post("/finance/settings").session(session).with(csrf())
                        .param("businessName", "ФОП Тестовий Т. Т.")
                        .param("taxSystem", TaxSystem.SINGLE_TAX_3.name())
                        .param("vatPayer", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(settingsService.get().getTaxSystem()).isEqualTo(TaxSystem.SINGLE_TAX_3);
        assertThat(page("/finance")).contains("ФОП Тестовий Т. Т.", "Єдиний податок, 3 група");
    }

    @Test
    void offsiteReturnIsIgnored() throws Exception {
        mvc.perform(post("/finance/documents/" + invoice.id() + "/act-status").session(session).with(csrf())
                        .param("status", "ON_SIGNING").param("returnTo", "//evil.example"))
                .andExpect(redirectedUrl("/finance/documents"));
    }

    @Test
    void formatHelpers() {
        FinanceFormat format = new FinanceFormat();
        assertThat(format.money(new BigDecimal("12450.00"))).isEqualTo("12 450 ₴");
        assertThat(format.money(new BigDecimal("12.5"))).isEqualTo("12,50 ₴");
        assertThat(format.plural(1, "наряд", "наряди", "нарядів")).isEqualTo("1 наряд");
        assertThat(format.plural(4, "наряд", "наряди", "нарядів")).isEqualTo("4 наряди");
        assertThat(format.plural(12, "наряд", "наряди", "нарядів")).isEqualTo("12 нарядів");
        assertThat(format.period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 23))).isEqualTo("01–23 вересня 2026");
    }

    @Test
    void masterCannotOpenPanel() throws Exception {
        mvc.perform(get("/finance").session(sessionFor("ROLE_MASTER"))).andExpect(status().isForbidden());
    }

    private String page(String url) throws Exception {
        return mvc.perform(get(url).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession sessionFor(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext(role));
        return session;
    }

    private SecurityContext securityContext(String role) {
        return new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority(role))));
    }
}
