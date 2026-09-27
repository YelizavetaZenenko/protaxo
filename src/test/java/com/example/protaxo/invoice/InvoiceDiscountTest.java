package com.example.protaxo.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceItemResponse;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.invoice.service.InvoiceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Знижка на рядок наряду, ПДВ після знижки та фіксований строк оплати. Кожен тест відкочується. */
@SpringBootTest
@Transactional
class InvoiceDiscountTest {

    @Autowired InvoiceService invoiceService;
    @Autowired CatalogItemRepository catalogItemRepository;
    @Autowired ClientRepository clientRepository;

    CatalogItem service;
    Client client;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        service = catalogItemRepository.save(CatalogItem.builder().type(CatalogItemType.SERVICE)
                .name("Послуга " + UUID.randomUUID()).basePrice(new BigDecimal("1200"))
                .vatRate(VatRate.VAT_20).build());
        client = clientRepository.save(Client.builder().name("Клієнт " + UUID.randomUUID()).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void discountReducesAmountAndVatIsTakenFromDiscountedAmount() {
        InvoiceResponse invoice = create(new InvoiceItemRequest(service.getId(), new BigDecimal("2"),
                new BigDecimal("1200"), new BigDecimal("10")));

        InvoiceItemResponse item = invoice.items().get(0);
        assertThat(item.discountPercent()).isEqualByComparingTo("10");
        assertThat(item.amount()).isEqualByComparingTo("2160.00");
        assertThat(item.vatAmount()).isEqualByComparingTo("360.00");
        assertThat(item.amountWithoutVat()).isEqualByComparingTo("1800.00");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("2160.00");
    }

    @Test
    void noDiscountByDefault() {
        InvoiceResponse invoice = create(new InvoiceItemRequest(service.getId(), BigDecimal.ONE, new BigDecimal("1200")));

        assertThat(invoice.items().get(0).discountPercent()).isEqualByComparingTo("0");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void discountOver100IsRejected() {
        assertThatThrownBy(() -> create(new InvoiceItemRequest(service.getId(), BigDecimal.ONE,
                new BigDecimal("1200"), new BigDecimal("150"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void paymentDueDateIsAlways15DaysAfterDocumentDate() {
        InvoiceResponse invoice = create(new InvoiceItemRequest(service.getId(), BigDecimal.ONE, new BigDecimal("1200")));

        assertThat(invoice.paymentDueDate()).isEqualTo(invoice.documentDate().toLocalDate().plusDays(15));
    }

    private InvoiceResponse create(InvoiceItemRequest item) {
        return invoiceService.create(new InvoiceRequest(InvoicePaymentType.CASH, client.getId(), null, null, null, null,
                List.of(item)));
    }
}
