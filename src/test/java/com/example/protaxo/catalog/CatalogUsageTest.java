package com.example.protaxo.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.catalog.dto.CatalogUsageRow;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.catalog.service.CatalogUsageService;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.invoice.service.InvoiceService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Звіт «Витрата товарів» (docs/Витрата товарів.md). Кожен тест відкочується. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogUsageTest {

    @Autowired CatalogUsageService usageService;
    @Autowired InvoiceService invoiceService;
    @Autowired CatalogItemRepository catalogItemRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired MockMvc mvc;

    CatalogItem oil;
    Client client;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        oil = catalogItemRepository.save(CatalogItem.builder().type(CatalogItemType.MATERIAL)
                .name("Олива " + UUID.randomUUID()).basePrice(new BigDecimal("250"))
                .stockQuantity(new BigDecimal("20")).build());
        client = clientRepository.save(Client.builder().name("Клієнт " + UUID.randomUUID()).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sumsQuantityAcrossInvoicesAndExcludesDeleted() {
        invoice("2", "250");
        invoice("1.5", "250");
        InvoiceResponse deleted = invoice("5", "250");
        invoiceService.softDelete(deleted.id());

        CatalogUsageRow row = row();
        assertThat(row.usedQuantity()).isEqualByComparingTo("3.5");
        assertThat(row.usedAmount()).isEqualByComparingTo("875");
        assertThat(row.invoiceCount()).isEqualTo(2);
        assertThat(row.currentStock()).isEqualByComparingTo("16.5");
        assertThat(usageService.lines(oil.getId(), LocalDate.now(), LocalDate.now())).hasSize(2);
    }

    @Test
    void periodOutsideUsageIsEmpty() {
        invoice("2", "250");
        LocalDate lastYear = LocalDate.now().minusYears(1);

        assertThat(usageService.usage(lastYear, lastYear, CatalogItemType.MATERIAL))
                .noneMatch(r -> r.catalogItemId().equals(oil.getId()));
    }

    @Test
    void pagesRenderForAccountantAndMaster() throws Exception {
        invoice("2", "250");
        for (String role : List.of("ROLE_ACCOUNTANT", "ROLE_MASTER")) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                            "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority(role)))));
            String html = mvc.perform(get("/catalog-items/usage").session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains(oil.getName());
            mvc.perform(get("/catalog-items/usage/" + oil.getId()).session(session)).andExpect(status().isOk());
            mvc.perform(get("/catalog-items/usage").param("type", "SERVICE").session(session)).andExpect(status().isOk());
        }
    }

    private InvoiceResponse invoice(String qty, String price) {
        return invoiceService.create(new InvoiceRequest(InvoicePaymentType.CASH, client.getId(), null, null, null, null,
                List.of(new InvoiceItemRequest(oil.getId(), new BigDecimal(qty), new BigDecimal(price))), null));
    }

    private CatalogUsageRow row() {
        return usageService.usage(LocalDate.now(), LocalDate.now(), CatalogItemType.MATERIAL).stream()
                .filter(r -> r.catalogItemId().equals(oil.getId())).findFirst().orElseThrow();
    }
}
