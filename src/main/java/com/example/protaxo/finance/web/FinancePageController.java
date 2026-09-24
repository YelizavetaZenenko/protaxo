package com.example.protaxo.finance.web;

import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.finance.dto.FinanceForms;
import com.example.protaxo.finance.dto.InvoiceSettlement;
import com.example.protaxo.finance.dto.OperationView;
import com.example.protaxo.finance.dto.SettlementStatus;
import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.FinanceAccount;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import com.example.protaxo.finance.entity.FinanceAttachment;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.RefundKind;
import com.example.protaxo.finance.service.FinanceQueryService;
import com.example.protaxo.finance.service.FinanceService;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.service.InvoiceService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Панель бухгалтера й каса (docs/Фінансовий облік.md). Доступ — SecurityConfig: усе під
 * {@code /finance} для ADMIN і ACCOUNTANT, а {@code /finance/cash} (підсумки каси) і прийом
 * оплати {@code POST /finance/payments} — також для MASTER.
 *
 * <p>Помилки бізнес-правил не "падають" на сторінку помилки: форма повертається з повідомленням
 * (flash {@code financeError}), а введені значення зберігаються.
 */
@Controller
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinancePageController {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final FinanceService financeService;
    private final FinanceQueryService queryService;
    private final InvoiceService invoiceService;
    private final ClientService clientService;

    // ================================================================== Огляд

    @GetMapping
    public String dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : today;

        List<InvoiceResponse> invoices = invoiceService.findAll();
        Map<Long, InvoiceSettlement> settlements = queryService.settlements(invoices);
        List<InvoiceSettlement> debts = settlements.values().stream()
                .filter(s -> s.debt().signum() > 0)
                .sorted(Comparator.comparing(InvoiceSettlement::overdue).reversed()
                        .thenComparing(InvoiceSettlement::debt, Comparator.reverseOrder()))
                .toList();

        model.addAttribute("balancesAt", Instant.now());
        model.addAttribute("balances", queryService.balances(Instant.now()));
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        model.addAttribute("totals", queryService.totals(startOf(periodFrom), startOf(periodTo.plusDays(1))));
        model.addAttribute("debts", debts.stream().limit(10).toList());
        model.addAttribute("debtCount", debts.size());
        model.addAttribute("debtTotal", debts.stream().map(InvoiceSettlement::debt).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("overdueTotal", debts.stream().filter(InvoiceSettlement::overdue)
                .map(InvoiceSettlement::debt).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("invoicesById", invoices.stream().collect(Collectors.toMap(InvoiceResponse::id, i -> i)));
        model.addAttribute("clientNames", clientNames());
        model.addAttribute("openReconciliations", queryService.openReconciliations());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("active", "overview");
        return "finance/dashboard";
    }

    // ================================================================== Каса (майстер)

    /**
     * Мінімальний екран для майстра: скільки надійшло в касу за день / тиждень / місяць, з
     * фільтром за способом оплати. Без боргів, витрат і налаштувань.
     */
    @GetMapping("/cash")
    public String cash(@RequestParam(defaultValue = "day") String period,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) PaymentMethod method,
                       Model model) {
        LocalDate anchor = date != null ? date : LocalDate.now();
        LocalDate from;
        LocalDate to;
        switch (period) {
            case "week" -> {
                from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                to = from.plusDays(6);
            }
            case "month" -> {
                from = anchor.withDayOfMonth(1);
                to = anchor.with(TemporalAdjusters.lastDayOfMonth());
            }
            default -> {
                period = "day";
                from = anchor;
                to = anchor;
            }
        }
        Instant start = startOf(from);
        Instant end = startOf(to.plusDays(1));
        List<OperationView> operations = queryService.search(start, end,
                        List.of(FinanceOperationType.PAYMENT, FinanceOperationType.REFUND), null, false).stream()
                .filter(op -> method == null || op.paymentMethod() == method)
                .toList();
        BigDecimal received = operations.stream().filter(op -> op.type() == FinanceOperationType.PAYMENT)
                .map(OperationView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = operations.stream().filter(op -> op.type() == FinanceOperationType.REFUND)
                .map(OperationView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("period", period);
        model.addAttribute("date", anchor);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("prevDate", shift(anchor, period, -1));
        model.addAttribute("nextDate", shift(anchor, period, 1));
        model.addAttribute("method", method);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("totals", queryService.totals(start, end));
        model.addAttribute("operations", operations);
        model.addAttribute("received", received);
        model.addAttribute("refunded", refunded);
        model.addAttribute("net", received.subtract(refunded));
        model.addAttribute("active", "cash");
        return "finance/cash";
    }

    private LocalDate shift(LocalDate anchor, String period, int direction) {
        return switch (period) {
            case "week" -> anchor.plusWeeks(direction);
            case "month" -> anchor.plusMonths(direction);
            default -> anchor.plusDays(direction);
        };
    }

    // ================================================================== Оплати

    @PostMapping("/payments")
    public String recordPayment(@ModelAttribute FinanceForms.Payment form, RedirectAttributes redirect) {
        String back = "redirect:/invoices/" + form.getInvoiceId();
        try {
            FinanceOperation payment = financeService.recordPayment(form);
            String message = "Оплату %s прийнято".formatted(money(payment.getAmount()));
            if (payment.getChangeAmount() != null && payment.getChangeAmount().signum() > 0) {
                message += " — решта клієнту " + money(payment.getChangeAmount());
            }
            redirect.addFlashAttribute("financeSuccess", message);
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("paymentForm", form);
        }
        return back;
    }

    @GetMapping("/invoices")
    public String invoices(@RequestParam(required = false) SettlementStatus status,
                           @RequestParam(defaultValue = "false") boolean overdueOnly,
                           @RequestParam(required = false) String q,
                           Model model) {
        List<InvoiceResponse> invoices = invoiceService.findAll();
        Map<Long, InvoiceSettlement> settlements = queryService.settlements(invoices);
        Map<Long, String> clientNames = clientNames();
        String needle = q == null ? null : q.trim().toLowerCase();
        List<InvoiceResponse> filtered = invoices.stream()
                .filter(inv -> status == null || settlements.get(inv.id()).status() == status)
                .filter(inv -> !overdueOnly || settlements.get(inv.id()).overdue())
                .filter(inv -> needle == null || needle.isEmpty()
                        || inv.number().contains(needle)
                        || clientNames.getOrDefault(inv.clientId(), "").toLowerCase().contains(needle))
                .sorted(Comparator.comparing(InvoiceResponse::documentDate).reversed())
                .toList();
        model.addAttribute("invoices", filtered);
        model.addAttribute("settlements", settlements);
        model.addAttribute("clientNames", clientNames);
        model.addAttribute("statuses", SettlementStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("overdueOnly", overdueOnly);
        model.addAttribute("q", q);
        model.addAttribute("sumTotal", filtered.stream().map(InvoiceResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("sumDebt", filtered.stream().map(inv -> settlements.get(inv.id()).debt()).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("active", "invoices");
        return "finance/invoices";
    }

    // ================================================================== Повернення

    @GetMapping("/payments/{id}/refund")
    public String refundForm(@PathVariable Long id, Model model) {
        OperationView payment = queryService.operation(id);
        if (!model.containsAttribute("refund")) {
            FinanceForms.Refund form = new FinanceForms.Refund();
            form.setAmount(payment.refundableAmount());
            form.setAccountId(payment.accountId());
            form.setRequestKey(newKey());
            model.addAttribute("refund", form);
        }
        model.addAttribute("payment", payment);
        model.addAttribute("refundKinds", RefundKind.values());
        model.addAttribute("accounts", queryService.activeAccounts().stream()
                .filter(a -> payment.paymentMethod() != null && a.getKind() == payment.paymentMethod().getAccountKind())
                .toList());
        model.addAttribute("active", "invoices");
        return "finance/refund-form";
    }

    @PostMapping("/payments/{id}/refund")
    public String refund(@PathVariable Long id, @ModelAttribute("refund") FinanceForms.Refund form,
                         RedirectAttributes redirect) {
        try {
            FinanceOperation refund = financeService.recordRefund(id, form);
            redirect.addFlashAttribute("financeSuccess", "Повернення %s проведено".formatted(money(refund.getAmount())));
            return "redirect:/invoices/" + refund.getInvoice().getId();
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("refund", form);
            return "redirect:/finance/payments/" + id + "/refund";
        }
    }

    // ================================================================== Журнал операцій

    @GetMapping("/operations")
    public String operations(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) FinanceOperationType type,
                             @RequestParam(required = false) Long accountId,
                             @RequestParam(defaultValue = "false") boolean hideCancelled,
                             Model model) {
        LocalDate periodFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : LocalDate.now();
        boolean includeCancelled = !hideCancelled;
        model.addAttribute("operations", queryService.search(startOf(periodFrom), startOf(periodTo.plusDays(1)),
                type == null ? null : List.of(type), accountId, includeCancelled));
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        model.addAttribute("type", type);
        model.addAttribute("accountId", accountId);
        model.addAttribute("includeCancelled", includeCancelled);
        model.addAttribute("types", FinanceOperationType.values());
        model.addAttribute("accounts", queryService.accounts());
        model.addAttribute("active", "operations");
        return "finance/operations";
    }

    /** Той самий журнал у CSV (Excel відкриває завдяки BOM і розділювачу ";"). */
    @GetMapping(value = "/operations.csv")
    public ResponseEntity<byte[]> operationsCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) FinanceOperationType type,
                                                @RequestParam(required = false) Long accountId,
                                                @RequestParam(defaultValue = "false") boolean hideCancelled) {
        LocalDate periodFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : LocalDate.now();
        List<OperationView> operations = queryService.search(startOf(periodFrom), startOf(periodTo.plusDays(1)),
                type == null ? null : List.of(type), accountId, !hideCancelled);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("№;Дата;Тип;Рахунок;Куди;Сума;Спосіб;Наряд;Категорія;Контрагент;Документ;Коментар;Автор;Скасовано;Причина скасування\n");
        for (OperationView op : operations) {
            csv.append(String.join(";",
                    String.valueOf(op.id()),
                    CSV_TIME.format(op.occurredAt().atZone(ZoneId.systemDefault())),
                    csvCell(op.type().getLabel()),
                    csvCell(op.accountName()),
                    csvCell(op.targetAccountName()),
                    (op.inflow() ? "" : "-") + op.amount().toPlainString().replace('.', ','),
                    csvCell(op.paymentMethod() == null ? null : op.paymentMethod().getLabel()),
                    csvCell(op.invoiceNumber()),
                    csvCell(op.expenseCategory() == null ? null : op.expenseCategory().getLabel()),
                    csvCell(op.counterparty()),
                    csvCell(op.documentRef()),
                    csvCell(op.comment()),
                    csvCell(op.createdByName()),
                    op.cancelled() ? CSV_TIME.format(op.cancelledAt().atZone(ZoneId.systemDefault())) : "",
                    csvCell(op.cancelReason())));
            csv.append('\n');
        }
        String fileName = "operations-%s-%s.csv".formatted(periodFrom, periodTo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + "\"";
    }

    @GetMapping("/operations/{id}/cancel")
    public String cancelForm(@PathVariable Long id, Model model) {
        model.addAttribute("operation", queryService.operation(id));
        model.addAttribute("active", "operations");
        return "finance/cancel-form";
    }

    @PostMapping("/operations/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        try {
            financeService.cancel(id, reason);
            redirect.addFlashAttribute("financeSuccess", "Операцію № %d скасовано".formatted(id));
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            return "redirect:/finance/operations/" + id + "/cancel";
        }
        return "redirect:" + safeReturn(returnTo, "/finance/operations");
    }

    // ================================================================== Витрати

    @GetMapping("/expenses")
    public String expenses(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        LocalDate periodFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : LocalDate.now();
        Instant start = startOf(periodFrom);
        Instant end = startOf(periodTo.plusDays(1));
        model.addAttribute("expenses", queryService.search(start, end, List.of(FinanceOperationType.EXPENSE), null, true));
        model.addAttribute("totals", queryService.totals(start, end));
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        model.addAttribute("active", "expenses");
        return "finance/expenses";
    }

    @GetMapping("/expenses/new")
    public String expenseForm(Model model) {
        if (!model.containsAttribute("expense")) {
            FinanceForms.Expense form = new FinanceForms.Expense();
            form.setRequestKey(newKey());
            model.addAttribute("expense", form);
        }
        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("accounts", queryService.activeAccounts());
        model.addAttribute("active", "expenses");
        return "finance/expense-form";
    }

    @PostMapping("/expenses")
    public String createExpense(@ModelAttribute("expense") FinanceForms.Expense form,
                                @RequestParam(value = "attachment", required = false) MultipartFile attachment,
                                RedirectAttributes redirect) {
        try {
            FinanceOperation expense = financeService.recordExpense(form, attachment);
            redirect.addFlashAttribute("financeSuccess", "Витрату %s збережено".formatted(money(expense.getAmount())));
            return "redirect:/finance/expenses";
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("expense", form);
            return "redirect:/finance/expenses/new";
        }
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id) {
        FinanceAttachment attachment = queryService.attachment(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(attachment.getFileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(attachment.getData());
    }

    // ================================================================== Каси й рахунки

    @GetMapping("/accounts")
    public String accounts(Model model) {
        model.addAttribute("balances", queryService.balances(Instant.now()));
        model.addAttribute("activeAccounts", queryService.activeAccounts());
        if (!model.containsAttribute("transfer")) {
            FinanceForms.Transfer transfer = new FinanceForms.Transfer();
            transfer.setRequestKey(newKey());
            model.addAttribute("transfer", transfer);
        }
        if (!model.containsAttribute("ownerMovement")) {
            FinanceForms.OwnerMovement movement = new FinanceForms.OwnerMovement();
            movement.setRequestKey(newKey());
            model.addAttribute("ownerMovement", movement);
        }
        model.addAttribute("ownerTypes", List.of(FinanceOperationType.OWNER_DEPOSIT, FinanceOperationType.OWNER_WITHDRAWAL));
        model.addAttribute("recentMovements", queryService.search(startOf(LocalDate.now().minusDays(30)),
                startOf(LocalDate.now().plusDays(1)),
                List.of(FinanceOperationType.TRANSFER, FinanceOperationType.OWNER_DEPOSIT,
                        FinanceOperationType.OWNER_WITHDRAWAL, FinanceOperationType.ADJUSTMENT_IN,
                        FinanceOperationType.ADJUSTMENT_OUT), null, true));
        model.addAttribute("active", "accounts");
        return "finance/accounts";
    }

    @PostMapping("/transfers")
    public String transfer(@ModelAttribute("transfer") FinanceForms.Transfer form, RedirectAttributes redirect) {
        try {
            FinanceOperation transfer = financeService.recordTransfer(form);
            redirect.addFlashAttribute("financeSuccess", "Переміщено %s".formatted(money(transfer.getAmount())));
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("transfer", form);
        }
        return "redirect:/finance/accounts";
    }

    @PostMapping("/owner-movements")
    public String ownerMovement(@ModelAttribute("ownerMovement") FinanceForms.OwnerMovement form, RedirectAttributes redirect) {
        try {
            FinanceOperation operation = financeService.recordOwnerMovement(form);
            redirect.addFlashAttribute("financeSuccess", "%s: %s".formatted(operation.getType().getLabel(), money(operation.getAmount())));
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("ownerMovement", form);
        }
        return "redirect:/finance/accounts";
    }

    @GetMapping("/accounts/new")
    public String newAccount(Model model) {
        if (!model.containsAttribute("account")) {
            model.addAttribute("account", new FinanceForms.Account());
        }
        model.addAttribute("kinds", FinanceAccountKind.values());
        model.addAttribute("active", "accounts");
        return "finance/account-form";
    }

    @PostMapping("/accounts")
    public String createAccount(@ModelAttribute("account") FinanceForms.Account form, RedirectAttributes redirect) {
        try {
            financeService.createAccount(form);
            redirect.addFlashAttribute("financeSuccess", "Рахунок «%s» додано".formatted(form.getName()));
            return "redirect:/finance/accounts";
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("account", form);
            return "redirect:/finance/accounts/new";
        }
    }

    @GetMapping("/accounts/{id}/edit")
    public String editAccount(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("account")) {
            FinanceAccount account = queryService.account(id);
            FinanceForms.Account form = new FinanceForms.Account();
            form.setName(account.getName());
            form.setKind(account.getKind());
            form.setOpeningBalance(account.getOpeningBalance());
            form.setActive(account.isActive());
            model.addAttribute("account", form);
        }
        model.addAttribute("editId", id);
        model.addAttribute("kinds", FinanceAccountKind.values());
        model.addAttribute("active", "accounts");
        return "finance/account-form";
    }

    @PostMapping("/accounts/{id}/edit")
    public String updateAccount(@PathVariable Long id, @ModelAttribute("account") FinanceForms.Account form,
                                RedirectAttributes redirect) {
        try {
            financeService.updateAccount(id, form);
            redirect.addFlashAttribute("financeSuccess", "Зміни збережено");
            return "redirect:/finance/accounts";
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("account", form);
            return "redirect:/finance/accounts/" + id + "/edit";
        }
    }

    // ================================================================== Звірки

    @GetMapping("/reconciliations")
    public String reconciliations(Model model) {
        List<FinanceAccount> cashAccounts = queryService.activeAccounts().stream()
                .filter(a -> a.getKind() == FinanceAccountKind.CASH)
                .toList();
        model.addAttribute("reconciliations", queryService.reconciliations());
        model.addAttribute("cashAccounts", cashAccounts);
        if (!model.containsAttribute("count")) {
            FinanceForms.Count count = new FinanceForms.Count();
            if (cashAccounts.size() == 1) {
                count.setAccountId(cashAccounts.get(0).getId());
            }
            model.addAttribute("count", count);
        }
        model.addAttribute("active", "reconciliations");
        return "finance/reconciliations";
    }

    @PostMapping("/reconciliations")
    public String count(@ModelAttribute("count") FinanceForms.Count form, RedirectAttributes redirect) {
        try {
            var reconciliation = financeService.recordCount(form);
            int sign = reconciliation.getDifference().signum();
            redirect.addFlashAttribute(sign == 0 ? "financeSuccess" : "financeError", sign == 0
                    ? "Звірка: суми збігаються"
                    : "Звірка: %s %s — розбіжність збережено, залишок за програмою не змінено"
                            .formatted(sign > 0 ? "надлишок" : "нестача", money(reconciliation.getDifference().abs())));
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("count", form);
        }
        return "redirect:/finance/reconciliations";
    }

    @GetMapping("/reconciliations/{id}/resolve")
    public String resolveForm(@PathVariable Long id, Model model) {
        model.addAttribute("reconciliation", queryService.reconciliation(id));
        if (!model.containsAttribute("resolve")) {
            model.addAttribute("resolve", new FinanceForms.Resolve());
        }
        model.addAttribute("active", "reconciliations");
        return "finance/resolve-form";
    }

    @PostMapping("/reconciliations/{id}/resolve")
    public String resolve(@PathVariable Long id, @ModelAttribute("resolve") FinanceForms.Resolve form,
                          RedirectAttributes redirect) {
        try {
            financeService.resolve(id, form);
            redirect.addFlashAttribute("financeSuccess", form.isCreateAdjustment()
                    ? "Розбіжність закрито, коригувальну операцію проведено"
                    : "Розбіжність позначено розглянутою");
            return "redirect:/finance/reconciliations";
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("financeError", ex.getMessage());
            redirect.addFlashAttribute("resolve", form);
            return "redirect:/finance/reconciliations/" + id + "/resolve";
        }
    }

    // ================================================================== допоміжне

    private Map<Long, String> clientNames() {
        return clientService.findAll().stream()
                .collect(Collectors.toMap(ClientResponse::id, ClientResponse::name, (a, b) -> a));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static final Set<String> RETURN_PREFIXES = Set.of("/finance/", "/invoices/");

    /** Лише внутрішні шляхи — щоб {@code returnTo} не став відкритим редіректом. */
    private String safeReturn(String returnTo, String fallback) {
        if (returnTo != null && !returnTo.contains("//") && RETURN_PREFIXES.stream().anyMatch(returnTo::startsWith)) {
            return returnTo;
        }
        return fallback;
    }

    private String money(BigDecimal value) {
        return String.format(Locale.forLanguageTag("uk"), "%,.2f грн", value);
    }
}
