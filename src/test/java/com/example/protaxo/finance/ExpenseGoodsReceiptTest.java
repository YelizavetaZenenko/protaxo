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
import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.ExpenseItem;
import com.example.protaxo.finance.entity.FinanceAccount;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.repository.ExpenseItemRepository;
import com.example.protaxo.finance.service.FinanceService;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.invoice.service.InvoiceService;
import jakarta.persistence.EntityManager;
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

/** Витрата «Запчастини та матеріали» з товарами: оприбуткування на склад і скасування. Кожен тест відкочується. */
@SpringBootTest
@Transactional
class ExpenseGoodsReceiptTest {

    @Autowired FinanceService financeService;
    @Autowired CatalogItemRepository catalogItemRepository;
    @Autowired ExpenseItemRepository expenseItemRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired InvoiceService invoiceService;
    @Autowired EntityManager entityManager;

    FinanceAccount bank;
    CatalogItem oil;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        FinanceForms.Account account = new FinanceForms.Account();
        account.setName("Тестовий банк " + UUID.randomUUID());
        account.setKind(FinanceAccountKind.BANK);
        bank = financeService.createAccount(account);
        oil = catalogItemRepository.save(CatalogItem.builder().type(CatalogItemType.MATERIAL)
                .name("Олива " + UUID.randomUUID()).basePrice(new BigDecimal("400"))
                .purchasePrice(new BigDecimal("250")).stockQuantity(new BigDecimal("3")).vatRate(VatRate.VAT_20).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void goodsAreReceivedIntoStockAndNewItemsAddedToCatalog() {
        String newName = "Фільтр " + UUID.randomUUID();
        FinanceOperation expense = financeService.recordExpense(expense(
                existing(oil.getId(), "4", "270"),
                created(newName, "150", "2", "90")), null);

        // Сума витрати = 4 × 270 + 2 × 90, введена вручну сума ігнорується.
        assertThat(expense.getAmount()).isEqualByComparingTo("1260.00");

        CatalogItem oilAfter = reload(oil.getId());
        assertThat(oilAfter.getStockQuantity()).isEqualByComparingTo("7");
        assertThat(oilAfter.getPurchasePrice()).isEqualByComparingTo("270");

        List<ExpenseItem> lines = expenseItemRepository.findByOperationIdOrderByLineNumber(expense.getId());
        assertThat(lines).hasSize(2);
        CatalogItem filter = reload(lines.get(1).getCatalogItemId());
        assertThat(filter.getName()).isEqualTo(newName);
        assertThat(filter.getType()).isEqualTo(CatalogItemType.MATERIAL);
        assertThat(filter.getBasePrice()).isEqualByComparingTo("150");
        assertThat(filter.getPurchasePrice()).isEqualByComparingTo("90");
        assertThat(filter.getStockQuantity()).isEqualByComparingTo("2");
        assertThat(lines.get(1).isCreatedNew()).isTrue();
    }

    @Test
    void cancellingExpenseTakesGoodsBackOutOfStock() {
        FinanceOperation expense = financeService.recordExpense(expense(existing(oil.getId(), "4", "270")), null);

        financeService.cancel(expense.getId(), "Помилка");

        assertThat(reload(oil.getId()).getStockQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void cannotCancelWhenReceivedGoodsWereAlreadySold() {
        FinanceOperation expense = financeService.recordExpense(expense(existing(oil.getId(), "4", "270")), null);
        Client client = clientRepository.save(Client.builder().name("Клієнт " + UUID.randomUUID()).build());
        invoiceService.create(new InvoiceRequest(InvoicePaymentType.CASH, client.getId(), null, null, null, null,
                List.of(new InvoiceItemRequest(oil.getId(), new BigDecimal("6"), new BigDecimal("400")))));

        assertThatThrownBy(() -> financeService.cancel(expense.getId(), "Помилка"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void newItemNeedsSalePrice() {
        assertThatThrownBy(() -> financeService.recordExpense(expense(created("Без ціни", null, "1", "10")), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void goodsAreIgnoredForOtherCategories() {
        FinanceForms.Expense form = expense(existing(oil.getId(), "4", "270"));
        form.setCategory(ExpenseCategory.OFFICE);
        form.setAmount(new BigDecimal("50"));

        FinanceOperation expense = financeService.recordExpense(form, null);

        assertThat(expense.getAmount()).isEqualByComparingTo("50");
        assertThat(reload(oil.getId()).getStockQuantity()).isEqualByComparingTo("3");
    }

    private CatalogItem reload(Long id) {
        entityManager.flush();
        entityManager.clear();
        return catalogItemRepository.findById(id).orElseThrow();
    }

    private FinanceForms.Expense expense(FinanceForms.ExpenseLine... lines) {
        FinanceForms.Expense form = new FinanceForms.Expense();
        form.setAmount(new BigDecimal("1"));
        form.setCategory(ExpenseCategory.PARTS);
        form.setPurpose("Закупівля");
        form.setAccountId(bank.getId());
        form.setItems(List.of(lines));
        return form;
    }

    private static FinanceForms.ExpenseLine existing(Long id, String qty, String price) {
        FinanceForms.ExpenseLine line = new FinanceForms.ExpenseLine();
        line.setCatalogItemId(id);
        line.setQuantity(new BigDecimal(qty));
        line.setPurchasePrice(new BigDecimal(price));
        return line;
    }

    private static FinanceForms.ExpenseLine created(String name, String salePrice, String qty, String price) {
        FinanceForms.ExpenseLine line = new FinanceForms.ExpenseLine();
        line.setNewItemName(name);
        line.setNewItemSalePrice(salePrice == null ? null : new BigDecimal(salePrice));
        line.setQuantity(new BigDecimal(qty));
        line.setPurchasePrice(new BigDecimal(price));
        return line;
    }
}
