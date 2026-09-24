package com.example.protaxo.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.finance.dto.FinanceForms;
import com.example.protaxo.finance.dto.InvoiceSettlement;
import com.example.protaxo.finance.dto.SettlementStatus;
import com.example.protaxo.finance.entity.FinanceAccount;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.Reconciliation;
import com.example.protaxo.finance.entity.ReconciliationStatus;
import com.example.protaxo.finance.entity.RefundKind;
import com.example.protaxo.finance.repository.FinanceOperationRepository;
import com.example.protaxo.finance.service.FinanceQueryService;
import com.example.protaxo.finance.service.FinanceService;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.invoice.service.InvoiceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Критерії приймання першої реалізації з docs/Фінансовий облік.md, розд. 15. Кожен тест у своїй
 * транзакції з відкатом — у БД нічого не лишається. Каси й рахунки створюються власні, щоб
 * залишки не залежали від даних, що вже є в базі.
 */
@SpringBootTest
@Transactional
class FinanceAcceptanceTest {

    @Autowired FinanceService financeService;
    @Autowired FinanceQueryService queryService;
    @Autowired FinanceOperationRepository operationRepository;
    @Autowired InvoiceService invoiceService;
    @Autowired ClientRepository clientRepository;
    @Autowired CatalogItemRepository catalogItemRepository;

    FinanceAccount cash;
    FinanceAccount bank;
    FinanceAccount card;
    InvoiceResponse invoice;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        cash = account("Тестова каса", FinanceAccountKind.CASH);
        bank = account("Тестовий банк", FinanceAccountKind.BANK);
        card = account("Тестовий термінал", FinanceAccountKind.CARD);
        invoice = invoiceFor(new BigDecimal("2450.00"), VatRate.VAT_20);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cashPaymentWithChangeIncreasesCashOnlyByInvoiceAmount() {
        FinanceOperation payment = financeService.recordPayment(payment(invoice.id(), "2450", PaymentMethod.CASH, cash, "2500"));

        assertThat(payment.getAmount()).isEqualByComparingTo("2450");
        assertThat(payment.getChangeAmount()).isEqualByComparingTo("50");
        assertThat(balance(cash)).isEqualByComparingTo("2450");
        assertThat(settlement().status()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void partialPaymentReducesDebtAndIsStoredSeparately() {
        financeService.recordPayment(payment(invoice.id(), "1000", PaymentMethod.CASH, cash, null));
        InvoiceSettlement afterFirst = settlement();
        assertThat(afterFirst.debt()).isEqualByComparingTo("1450");
        assertThat(afterFirst.status()).isEqualTo(SettlementStatus.PARTIALLY_PAID);

        financeService.recordPayment(payment(invoice.id(), "1450", PaymentMethod.TRANSFER, bank, null));
        assertThat(settlement().status()).isEqualTo(SettlementStatus.PAID);
        assertThat(operationRepository.findByInvoiceId(invoice.id())).hasSize(2);
    }

    @Test
    void bankTransferAndCardDoNotChangeCash() {
        financeService.recordPayment(payment(invoice.id(), "1000", PaymentMethod.TRANSFER, bank, null));
        financeService.recordPayment(payment(invoice.id(), "1000", PaymentMethod.CARD, card, null));

        assertThat(balance(cash)).isEqualByComparingTo("0");
        assertThat(balance(bank)).isEqualByComparingTo("1000");
        assertThat(balance(card)).isEqualByComparingTo("1000");
    }

    @Test
    void paymentMethodMustMatchAccountKind() {
        assertThatThrownBy(() -> financeService.recordPayment(payment(invoice.id(), "100", PaymentMethod.CARD, cash, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void internalTransferIsNotRevenueAndDoesNotDoubleMoney() {
        InvoiceResponse big = invoiceFor(new BigDecimal("6000.00"), VatRate.VAT_20);
        Instant from = Instant.now().minusSeconds(60);
        financeService.recordPayment(payment(big.id(), "6000", PaymentMethod.CASH, cash, null));

        FinanceForms.Transfer transfer = new FinanceForms.Transfer();
        transfer.setFromAccountId(cash.getId());
        transfer.setToAccountId(bank.getId());
        transfer.setAmount(new BigDecimal("5000"));
        transfer.setRequestKey(UUID.randomUUID().toString());
        financeService.recordTransfer(transfer);

        assertThat(balance(cash)).isEqualByComparingTo("1000");
        assertThat(balance(bank)).isEqualByComparingTo("5000");
        assertThat(balance(cash).add(balance(bank))).isEqualByComparingTo("6000");
        var totals = queryService.totals(from, Instant.now().plusSeconds(60));
        assertThat(totals.payments()).isEqualByComparingTo("6000");
        assertThat(totals.expenses()).isEqualByComparingTo("0");
    }

    @Test
    void refundKeepsOriginalPaymentInHistory() {
        FinanceOperation payment = financeService.recordPayment(payment(invoice.id(), "2450", PaymentMethod.CASH, cash, null));

        FinanceForms.Refund refund = new FinanceForms.Refund();
        refund.setAmount(new BigDecimal("450"));
        refund.setKind(RefundKind.WORK_CANCELLED);
        refund.setReason("Скасовано частину робіт");
        refund.setRequestKey(UUID.randomUUID().toString());
        FinanceOperation saved = financeService.recordRefund(payment.getId(), refund);

        assertThat(saved.getOriginalOperation().getId()).isEqualTo(payment.getId());
        FinanceOperation original = operationRepository.findById(payment.getId()).orElseThrow();
        assertThat(original.isCancelled()).isFalse();
        assertThat(original.getAmount()).isEqualByComparingTo("2450");
        assertThat(balance(cash)).isEqualByComparingTo("2000");
        assertThat(settlement().refunded()).isEqualByComparingTo("450");
    }

    @Test
    void reconciliationDiscrepancyDoesNotSilentlyChangeCash() {
        financeService.recordPayment(payment(invoice.id(), "2450", PaymentMethod.CASH, cash, null));

        FinanceForms.Count count = new FinanceForms.Count();
        count.setAccountId(cash.getId());
        count.setActualBalance(new BigDecimal("2400"));
        Reconciliation reconciliation = financeService.recordCount(count);

        assertThat(reconciliation.getDifference()).isEqualByComparingTo("-50");
        assertThat(reconciliation.getStatus()).isEqualTo(ReconciliationStatus.OPEN);
        assertThat(balance(cash)).isEqualByComparingTo("2450");

        FinanceForms.Resolve resolve = new FinanceForms.Resolve();
        resolve.setResolutionComment("Помилка з рештою");
        resolve.setCreateAdjustment(true);
        financeService.resolve(reconciliation.getId(), resolve);
        assertThat(balance(cash)).isEqualByComparingTo("2400");
    }

    @Test
    void repeatedConfirmationDoesNotDuplicatePayment() {
        FinanceForms.Payment form = payment(invoice.id(), "1000", PaymentMethod.CASH, cash, null);
        FinanceOperation first = financeService.recordPayment(form);
        FinanceOperation second = financeService.recordPayment(form);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(operationRepository.findByInvoiceId(invoice.id())).hasSize(1);
        assertThat(balance(cash)).isEqualByComparingTo("1000");
    }

    @Test
    void vatTotalsAgreeWithLines() {
        assertThat(invoice.items().get(0).vatAmount()).isEqualByComparingTo("408.33");
        assertThat(invoice.items().get(0).amountWithoutVat()).isEqualByComparingTo("2041.67");
        assertThat(invoice.totalVat().add(invoice.totalWithoutVat())).isEqualByComparingTo(invoice.totalAmount());

        InvoiceResponse noVat = invoiceFor(new BigDecimal("100.00"), VatRate.NO_VAT);
        assertThat(noVat.totalVat()).isEqualByComparingTo("0");
        assertThat(noVat.totalWithoutVat()).isEqualByComparingTo("100");
    }

    @Test
    void overpaymentIsRejected() {
        assertThatThrownBy(() -> financeService.recordPayment(payment(invoice.id(), "2450.01", PaymentMethod.CASH, cash, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelledPaymentStaysInHistoryButRestoresDebt() {
        FinanceOperation payment = financeService.recordPayment(payment(invoice.id(), "2450", PaymentMethod.CASH, cash, null));
        financeService.cancel(payment.getId(), "Внесено помилково");

        FinanceOperation stored = operationRepository.findById(payment.getId()).orElseThrow();
        assertThat(stored.isCancelled()).isTrue();
        assertThat(stored.getCancelReason()).isEqualTo("Внесено помилково");
        assertThat(settlement().debt()).isEqualByComparingTo("2450");
        assertThat(balance(cash)).isEqualByComparingTo("0");
    }

    @Test
    void cannotSpendMoreCashThanAvailable() {
        FinanceForms.Expense expense = new FinanceForms.Expense();
        expense.setAmount(new BigDecimal("10"));
        expense.setCategory(com.example.protaxo.finance.entity.ExpenseCategory.OFFICE);
        expense.setPurpose("Папір");
        expense.setAccountId(cash.getId());
        assertThatThrownBy(() -> financeService.recordExpense(expense, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void invoiceTotalCannotDropBelowPaidAmount() {
        financeService.recordPayment(payment(invoice.id(), "2450", PaymentMethod.CASH, cash, null));
        CatalogItem item = catalogItemRepository.findById(invoice.items().get(0).catalogItemId()).orElseThrow();
        InvoiceRequest cheaper = new InvoiceRequest(InvoicePaymentType.CASH, invoice.clientId(), null, null, null, null,
                List.of(new InvoiceItemRequest(item.getId(), BigDecimal.ONE, new BigDecimal("2000"))), null);
        assertThatThrownBy(() -> invoiceService.update(invoice.id(), cheaper)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void overdueIsSeparateFromPaymentStatus() {
        InvoiceSettlement overdue = InvoiceSettlement.of(1L, new BigDecimal("100"), new BigDecimal("40"), BigDecimal.ZERO,
                LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(overdue.status()).isEqualTo(SettlementStatus.PARTIALLY_PAID);
        assertThat(overdue.overdue()).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private FinanceAccount account(String name, FinanceAccountKind kind) {
        FinanceForms.Account form = new FinanceForms.Account();
        form.setName(name);
        form.setKind(kind);
        return financeService.createAccount(form);
    }

    private InvoiceResponse invoiceFor(BigDecimal price, VatRate vatRate) {
        Client client = clientRepository.save(Client.builder().name("Тестовий клієнт " + UUID.randomUUID()).build());
        CatalogItem item = catalogItemRepository.save(CatalogItem.builder()
                .type(CatalogItemType.SERVICE).name("Тестова послуга " + UUID.randomUUID())
                .basePrice(price).vatRate(vatRate).build());
        return invoiceService.create(new InvoiceRequest(InvoicePaymentType.CASH, client.getId(), null, null, null, null,
                List.of(new InvoiceItemRequest(item.getId(), BigDecimal.ONE, price)), null));
    }

    private FinanceForms.Payment payment(Long invoiceId, String amount, PaymentMethod method, FinanceAccount account,
                                         String received) {
        FinanceForms.Payment form = new FinanceForms.Payment();
        form.setInvoiceId(invoiceId);
        form.setAmount(new BigDecimal(amount));
        form.setMethod(method);
        form.setAccountId(account.getId());
        form.setReceivedAmount(received == null ? null : new BigDecimal(received));
        form.setRequestKey(UUID.randomUUID().toString());
        return form;
    }

    private BigDecimal balance(FinanceAccount account) {
        return financeService.balanceOf(account, Instant.now().plusSeconds(1));
    }

    private InvoiceSettlement settlement() {
        return queryService.settlement(invoiceService.findById(invoice.id()));
    }
}
