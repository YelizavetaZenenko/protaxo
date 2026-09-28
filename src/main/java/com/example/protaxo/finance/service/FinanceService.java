package com.example.protaxo.finance.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.catalog.service.CatalogItemService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.finance.dto.FinanceForms;
import com.example.protaxo.finance.dto.InvoiceSettlement;
import com.example.protaxo.finance.entity.FinanceAccount;
import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.ExpenseItem;
import com.example.protaxo.finance.entity.FinanceAccountKind;
import com.example.protaxo.finance.entity.FinanceAttachment;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.Reconciliation;
import com.example.protaxo.finance.entity.ReconciliationStatus;
import com.example.protaxo.finance.repository.FinanceAccountRepository;
import com.example.protaxo.finance.repository.FinanceAttachmentRepository;
import com.example.protaxo.finance.repository.ExpenseItemRepository;
import com.example.protaxo.finance.repository.FinanceOperationRepository;
import com.example.protaxo.finance.repository.ReconciliationRepository;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.invoice.entity.InvoiceItem;
import com.example.protaxo.invoice.repository.InvoiceRepository;
import com.example.protaxo.security.service.CurrentUserRoles;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Запис фінансових операцій (docs/Фінансовий облік.md). Правила:
 * <ul>
 *   <li>суми лише додатні, з точністю до копійки;</li>
 *   <li>спосіб оплати визначає вид рахунку: готівка → каса, картка → термінал, переказ → банк —
 *       картка й переказ ніколи не збільшують готівку;</li>
 *   <li>оплата не може перевищити борг за нарядом (переплати й аванси без наряду не підтримуються);</li>
 *   <li>з готівкової каси не можна видати більше, ніж у ній є;</li>
 *   <li>операції не видаляються — лише скасовуються з причиною;</li>
 *   <li>той самий {@code requestKey} повертає вже збережену операцію замість дубля.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FinanceService {

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final Set<FinanceOperationType> OWNER_TYPES =
            Set.of(FinanceOperationType.OWNER_DEPOSIT, FinanceOperationType.OWNER_WITHDRAWAL);

    private final FinanceOperationRepository operationRepository;
    private final FinanceAccountRepository accountRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final FinanceAttachmentRepository attachmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final ExpenseItemRepository expenseItemRepository;
    private final CatalogItemRepository catalogItemRepository;

    // ------------------------------------------------------------------ оплати

    public FinanceOperation recordPayment(FinanceForms.Payment form) {
        if (form.getInvoiceId() == null) {
            throw new BusinessRuleException("Не вказано наряд");
        }
        // Спершу замок, потім пошук дубля: друга з двох одночасних однакових відправок чекає тут
        // і після розблокування вже бачить збережену першою операцію.
        Invoice invoice = invoiceRepository.findByIdForUpdate(form.getInvoiceId())
                .orElseThrow(() -> new NotFoundException("Invoice %d not found".formatted(form.getInvoiceId())));
        Optional<FinanceOperation> duplicate = findDuplicate(form.getRequestKey(), FinanceOperationType.PAYMENT);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        BigDecimal amount = requirePositive(form.getAmount(), "Сума оплати");
        PaymentMethod method = form.getMethod();
        if (method == null) {
            throw new BusinessRuleException("Оберіть спосіб оплати");
        }

        InvoiceSettlement settlement = settlementOf(invoice);
        if (settlement.debt().signum() <= 0) {
            throw new BusinessRuleException("Наряд уже повністю оплачено");
        }
        if (amount.compareTo(settlement.debt()) > 0) {
            throw new BusinessRuleException("Сума оплати %s перевищує залишок боргу %s. Переплата не допускається — "
                    .formatted(money(amount), money(settlement.debt()))
                    + "для готівки вкажіть суму від клієнта в полі «Отримано від клієнта», решту буде пораховано.");
        }

        FinanceAccount account = resolveAccount(form.getAccountId(), method.getAccountKind());

        BigDecimal received = null;
        BigDecimal change = null;
        if (method == PaymentMethod.CASH && form.getReceivedAmount() != null) {
            received = scale(form.getReceivedAmount());
            if (received.compareTo(amount) < 0) {
                throw new BusinessRuleException("Отримано від клієнта (%s) менше за суму оплати (%s)"
                        .formatted(money(received), money(amount)));
            }
            change = received.subtract(amount);
        }

        FinanceOperation operation = FinanceOperation.builder()
                .type(FinanceOperationType.PAYMENT)
                .account(account)
                .amount(amount)
                .paymentMethod(method)
                .invoice(invoice)
                .receivedAmount(received)
                .changeAmount(change)
                .comment(trimToNull(form.getComment()))
                .build();
        return save(operation, form.getRequestKey());
    }

    // ------------------------------------------------------------------ повернення

    public FinanceOperation recordRefund(Long paymentId, FinanceForms.Refund form) {
        Optional<FinanceOperation> duplicate = findDuplicate(form.getRequestKey(), FinanceOperationType.REFUND);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        FinanceOperation payment = operationRepository.findDetailedById(paymentId)
                .orElseThrow(() -> new NotFoundException("Operation %d not found".formatted(paymentId)));
        if (payment.getType() != FinanceOperationType.PAYMENT || payment.isCancelled()) {
            throw new BusinessRuleException("Повернути можна лише проведену оплату");
        }
        // Той самий замок, що й для оплат: борг наряду рахується з обох видів операцій.
        invoiceRepository.findByIdForUpdate(payment.getInvoice().getId());

        BigDecimal amount = requirePositive(form.getAmount(), "Сума повернення");
        BigDecimal refundable = payment.getAmount().subtract(operationRepository.sumRefundsOf(paymentId));
        if (amount.compareTo(refundable) > 0) {
            throw new BusinessRuleException("Можна повернути не більше %s з цієї оплати".formatted(money(refundable)));
        }
        if (form.getKind() == null) {
            throw new BusinessRuleException("Оберіть причину повернення");
        }
        String reason = requireText(form.getReason(), "Опишіть причину повернення");

        FinanceAccount account = form.getAccountId() == null
                ? payment.getAccount()
                : resolveAccount(form.getAccountId(), payment.getPaymentMethod().getAccountKind());
        requireEnoughCash(account, amount);

        FinanceOperation refund = FinanceOperation.builder()
                .type(FinanceOperationType.REFUND)
                .account(account)
                .amount(amount)
                .paymentMethod(payment.getPaymentMethod())
                .invoice(payment.getInvoice())
                .originalOperation(payment)
                .refundKind(form.getKind())
                .comment(reason)
                .build();
        return save(refund, form.getRequestKey());
    }

    // ------------------------------------------------------------------ витрати

    public FinanceOperation recordExpense(FinanceForms.Expense form, MultipartFile attachment) {
        Optional<FinanceOperation> duplicate = findDuplicate(form.getRequestKey(), FinanceOperationType.EXPENSE);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        if (form.getCategory() == null) {
            throw new BusinessRuleException("Оберіть категорію витрати");
        }
        List<FinanceForms.ExpenseLine> lines = form.getCategory() == ExpenseCategory.PARTS && form.getItems() != null
                ? form.getItems().stream().filter(l -> !l.isBlank()).toList()
                : List.of();
        // Якщо вказано товари — сума витрати дорівнює їхній сумі, введене вручну значення ігнорується.
        BigDecimal amount = lines.isEmpty()
                ? requirePositive(form.getAmount(), "Сума витрати")
                : requirePositive(lines.stream().map(this::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add), "Сума витрати");
        String purpose = requireText(form.getPurpose(), "Вкажіть призначення витрати");
        FinanceAccount account = requireAccount(form.getAccountId());
        requireEnoughCash(account, amount);

        Invoice invoice = null;
        if (form.getInvoiceNumber() != null && !form.getInvoiceNumber().isBlank()) {
            String number = form.getInvoiceNumber().trim();
            invoice = invoiceRepository.findByNumber(number)
                    .orElseThrow(() -> new BusinessRuleException("Наряд № %s не знайдено".formatted(number)));
        }

        FinanceOperation expense = FinanceOperation.builder()
                .type(FinanceOperationType.EXPENSE)
                .account(account)
                .amount(amount)
                .expenseCategory(form.getCategory())
                .comment(purpose)
                .counterparty(trimToNull(form.getCounterparty()))
                .documentRef(trimToNull(form.getDocumentRef()))
                .invoice(invoice)
                .build();
        FinanceOperation saved = save(expense, form.getRequestKey());
        receiveGoods(saved.getId(), lines);
        storeAttachment(saved.getId(), attachment);
        return saved;
    }

    private BigDecimal lineAmount(FinanceForms.ExpenseLine line) {
        BigDecimal quantity = requirePositive(line.getQuantity(), "Кількість товару");
        if (line.getPurchasePrice() == null || line.getPurchasePrice().signum() < 0) {
            throw new BusinessRuleException("Вкажіть ціну закупівлі для кожного товару");
        }
        return quantity.multiply(line.getPurchasePrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Оприбуткування закупленого: залишок позиції збільшується на кількість, ціна закупівлі в
     * каталозі стає ціною цієї закупівлі. Нові позиції створюються в каталозі як товари.
     */
    private void receiveGoods(Long operationId, List<FinanceForms.ExpenseLine> lines) {
        int lineNumber = 1;
        for (FinanceForms.ExpenseLine line : lines) {
            BigDecimal quantity = line.getQuantity();
            BigDecimal purchasePrice = line.getPurchasePrice().setScale(2, RoundingMode.HALF_UP);
            boolean createdNew = line.getCatalogItemId() == null;
            CatalogItem item = createdNew ? newCatalogItem(line) : existingMaterial(line.getCatalogItemId());

            FieldDiff.Snapshot before = createdNew ? FieldDiff.snapshot() : CatalogItemService.snapshot(item);
            item.setStockQuantity(stockAfter(item, quantity));
            item.setPurchasePrice(purchasePrice);
            CatalogItem savedItem = catalogItemRepository.save(item);
            Map<String, String[]> changes = FieldDiff.between(before, CatalogItemService.snapshot(savedItem));
            changes.put("Підстава", new String[]{"", "Оприбуткування за витратою #" + operationId});
            auditLogService.record(createdNew ? AuditAction.CREATE : AuditAction.UPDATE, "CatalogItem", savedItem.getId(), changes);

            expenseItemRepository.save(ExpenseItem.builder()
                    .operationId(operationId)
                    .catalogItemId(savedItem.getId())
                    .lineNumber(lineNumber++)
                    .itemName(savedItem.getName())
                    .quantity(quantity)
                    .purchasePrice(purchasePrice)
                    .amount(lineAmount(line))
                    .createdNew(createdNew)
                    .build());
        }
    }

    private static BigDecimal stockAfter(CatalogItem item, BigDecimal received) {
        return (item.getStockQuantity() == null ? BigDecimal.ZERO : item.getStockQuantity()).add(received);
    }

    private CatalogItem existingMaterial(Long catalogItemId) {
        CatalogItem item = catalogItemRepository.findById(catalogItemId)
                .orElseThrow(() -> new BusinessRuleException("Позицію каталогу не знайдено — можливо, її видалили"));
        if (item.getType() != CatalogItemType.MATERIAL) {
            throw new BusinessRuleException("«%s» — послуга, її не можна оприбуткувати на склад".formatted(item.getName()));
        }
        return item;
    }

    private CatalogItem newCatalogItem(FinanceForms.ExpenseLine line) {
        String name = requireText(line.getNewItemName(), "Вкажіть назву нової позиції");
        if (line.getNewItemSalePrice() == null || line.getNewItemSalePrice().signum() < 0) {
            throw new BusinessRuleException("Вкажіть ціну продажу для нової позиції «%s»".formatted(name));
        }
        return CatalogItem.builder()
                .type(CatalogItemType.MATERIAL)
                .name(name)
                .basePrice(line.getNewItemSalePrice().setScale(2, RoundingMode.HALF_UP))
                .stockQuantity(BigDecimal.ZERO)
                .vatRate(VatRate.VAT_20)
                .build();
    }

    /** Скасування закупівлі списує оприбутковане назад — якщо товар ще є на складі. */
    private void reverseGoodsReceipt(Long operationId) {
        for (ExpenseItem line : expenseItemRepository.findByOperationIdOrderByLineNumber(operationId)) {
            BigDecimal stock = catalogItemRepository.findStockQuantityIncludingDeleted(line.getCatalogItemId());
            if (stock != null && stock.compareTo(line.getQuantity()) < 0) {
                throw new BusinessRuleException(("Не можна скасувати: «%s» уже продано чи списано — на складі %s, "
                        + "а закупівля була на %s").formatted(line.getItemName(),
                        stock.stripTrailingZeros().toPlainString(), line.getQuantity().stripTrailingZeros().toPlainString()));
            }
            catalogItemRepository.restoreStockQuantity(line.getCatalogItemId(), line.getQuantity().negate());
            if (stock != null) {
                Map<String, String[]> changes = FieldDiff.builder()
                        .add("Залишок", stock, stock.subtract(line.getQuantity()))
                        .add("Підстава", null, "Скасування витрати #" + operationId)
                        .build();
                auditLogService.record(AuditAction.UPDATE, "CatalogItem", line.getCatalogItemId(), changes);
            }
        }
    }

    private void storeAttachment(Long operationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessRuleException("Файл завеликий — максимум 10 МБ");
        }
        try {
            attachmentRepository.save(FinanceAttachment.builder()
                    .operationId(operationId)
                    .fileName(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename())
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .sizeBytes(file.getSize())
                    .data(file.getBytes())
                    .createdAt(Instant.now())
                    .build());
            auditLogService.record(AuditAction.UPDATE, "FinanceOperation", operationId, FieldDiff.builder()
                    .add("Вкладення", null, file.getOriginalFilename() + " (" + (file.getSize() / 1024) + " КБ)")
                    .build());
        } catch (IOException e) {
            throw new BusinessRuleException("Не вдалося прочитати файл: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ переміщення, власник

    /**
     * Внутрішнє переміщення (напр. здача готівки з каси в банк). Не є ні виручкою, ні витратою —
     * загальна сума коштів не змінюється (розд. 8 концепції).
     */
    public FinanceOperation recordTransfer(FinanceForms.Transfer form) {
        Optional<FinanceOperation> duplicate = findDuplicate(form.getRequestKey(), FinanceOperationType.TRANSFER);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        BigDecimal amount = requirePositive(form.getAmount(), "Сума переміщення");
        FinanceAccount from = requireAccount(form.getFromAccountId());
        FinanceAccount to = requireAccount(form.getToAccountId());
        if (from.getId().equals(to.getId())) {
            throw new BusinessRuleException("Рахунки «звідки» і «куди» мають відрізнятися");
        }
        requireEnoughCash(from, amount);

        FinanceOperation transfer = FinanceOperation.builder()
                .type(FinanceOperationType.TRANSFER)
                .account(from)
                .targetAccount(to)
                .amount(amount)
                .comment(trimToNull(form.getComment()))
                .build();
        return save(transfer, form.getRequestKey());
    }

    /** Внесення / вилучення коштів власником — не виручка і не операційна витрата (розд. 7). */
    public FinanceOperation recordOwnerMovement(FinanceForms.OwnerMovement form) {
        if (form.getType() == null || !OWNER_TYPES.contains(form.getType())) {
            throw new BusinessRuleException("Оберіть: внесення чи вилучення");
        }
        Optional<FinanceOperation> duplicate = findDuplicate(form.getRequestKey(), form.getType());
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        BigDecimal amount = requirePositive(form.getAmount(), "Сума");
        FinanceAccount account = requireAccount(form.getAccountId());
        if (form.getType() == FinanceOperationType.OWNER_WITHDRAWAL) {
            requireEnoughCash(account, amount);
        }
        FinanceOperation operation = FinanceOperation.builder()
                .type(form.getType())
                .account(account)
                .amount(amount)
                .comment(trimToNull(form.getComment()))
                .build();
        return save(operation, form.getRequestKey());
    }

    // ------------------------------------------------------------------ скасування

    /**
     * Сторно: операція лишається в історії з автором, часом і причиною скасування, але більше не
     * впливає на залишки й борги. Оплату з повернення скасувати не можна — спершу скасуйте повернення.
     */
    public void cancel(Long operationId, String reason) {
        FinanceOperation operation = operationRepository.findDetailedById(operationId)
                .orElseThrow(() -> new NotFoundException("Operation %d not found".formatted(operationId)));
        if (operation.isCancelled()) {
            return;
        }
        String cancelReason = requireText(reason, "Вкажіть причину скасування");
        if (operationRepository.hasActiveDependents(operationId)) {
            throw new BusinessRuleException("З цієї оплати вже є повернення — спершу скасуйте повернення");
        }
        if (operation.getInvoice() != null) {
            Invoice invoice = invoiceRepository.findByIdForUpdate(operation.getInvoice().getId())
                    .orElseThrow(() -> new NotFoundException("Invoice not found"));
            // Скасоване повернення знову рахується як сплачене — не має вийти переплата.
            if (operation.getType() == FinanceOperationType.REFUND
                    && operation.getAmount().compareTo(settlementOf(invoice).debt()) > 0) {
                throw new BusinessRuleException("Скасування цього повернення дасть переплату за нарядом — "
                        + "спершу поверніть вартість наряду до попередньої");
            }
        }
        // Скасування надходження зменшує залишок — готівки має вистачити, як і для звичайної видачі.
        if (operation.getType().getSign() > 0) {
            requireEnoughCash(operation.getAccount(), operation.getAmount());
        }
        if (operation.getTargetAccount() != null) {
            requireEnoughCash(operation.getTargetAccount(), operation.getAmount());
        }
        if (operation.getType() == FinanceOperationType.EXPENSE) {
            reverseGoodsReceipt(operationId);
        }
        operation.setCancelledAt(Instant.now());
        operation.setCancelledBy(CurrentUserRoles.username());
        operation.setCancelReason(cancelReason);
        operationRepository.save(operation);
        auditLogService.record(AuditAction.UPDATE, "FinanceOperation", operationId, FieldDiff.builder()
                .add("Статус", "Проведено", "Скасовано")
                .add("Причина скасування", null, cancelReason)
                .add("Операція", null, operation.getType().getLabel() + " на " + operation.getAmount().toPlainString() + " грн")
                .build());
    }

    // ------------------------------------------------------------------ звірки

    /**
     * Фіксує фактичну суму. Залишок за програмою НЕ змінюється: розбіжність зберігається й чекає
     * на розгляд бухгалтером (критерій 7).
     */
    public Reconciliation recordCount(FinanceForms.Count form) {
        FinanceAccount account = requireAccount(form.getAccountId());
        if (form.getActualBalance() == null || form.getActualBalance().signum() < 0) {
            throw new BusinessRuleException("Вкажіть фактичну суму (0 або більше)");
        }
        BigDecimal actual = scale(form.getActualBalance());
        Instant now = Instant.now();
        BigDecimal expected = balanceOf(account, now);
        BigDecimal difference = actual.subtract(expected);
        Reconciliation reconciliation = Reconciliation.builder()
                .account(account)
                .countedAt(now)
                .expectedBalance(expected)
                .actualBalance(actual)
                .difference(difference)
                .status(difference.signum() == 0 ? ReconciliationStatus.MATCHED : ReconciliationStatus.OPEN)
                .comment(trimToNull(form.getComment()))
                .createdBy(CurrentUserRoles.username())
                .build();
        Reconciliation saved = reconciliationRepository.save(reconciliation);
        auditLogService.record(AuditAction.CREATE, "Reconciliation", saved.getId(), FieldDiff.created(FieldDiff.snapshot()
                .add("Каса / рахунок", account.getName())
                .add("За програмою", expected)
                .add("Фактично", actual)
                .add("Розбіжність", difference)
                .add("Статус", saved.getStatus().getLabel())
                .add("Коментар", saved.getComment())));
        return saved;
    }

    public void resolve(Long reconciliationId, FinanceForms.Resolve form) {
        Reconciliation reconciliation = reconciliationRepository.findDetailedById(reconciliationId)
                .orElseThrow(() -> new NotFoundException("Reconciliation %d not found".formatted(reconciliationId)));
        if (reconciliation.getStatus() != ReconciliationStatus.OPEN) {
            throw new BusinessRuleException("Ця звірка вже не має відкритої розбіжності");
        }
        String comment = requireText(form.getResolutionComment(), "Опишіть, як розглянуто розбіжність");
        ReconciliationStatus statusBefore = reconciliation.getStatus();
        if (form.isCreateAdjustment()) {
            BigDecimal difference = reconciliation.getDifference();
            FinanceOperation adjustment = FinanceOperation.builder()
                    .type(difference.signum() > 0 ? FinanceOperationType.ADJUSTMENT_IN : FinanceOperationType.ADJUSTMENT_OUT)
                    .account(reconciliation.getAccount())
                    .amount(difference.abs())
                    .reconciliation(reconciliation)
                    .comment(comment)
                    .build();
            save(adjustment, "reconciliation-" + reconciliationId);
        }
        reconciliation.setStatus(ReconciliationStatus.RESOLVED);
        reconciliation.setResolvedAt(Instant.now());
        reconciliation.setResolvedBy(CurrentUserRoles.username());
        reconciliation.setResolutionComment(comment);
        reconciliationRepository.save(reconciliation);
        auditLogService.record(AuditAction.UPDATE, "Reconciliation", reconciliationId, FieldDiff.builder()
                .add("Статус", statusBefore.getLabel(), reconciliation.getStatus().getLabel())
                .add("Як розглянуто", null, comment)
                .add("Коригування залишку", null, form.isCreateAdjustment()
                        ? reconciliation.getDifference().toPlainString() + " грн" : "без коригування")
                .build());
    }

    // ------------------------------------------------------------------ каси й рахунки

    public FinanceAccount createAccount(FinanceForms.Account form) {
        FinanceAccount account = FinanceAccount.builder()
                .name(requireText(form.getName(), "Вкажіть назву"))
                .kind(form.getKind() == null ? FinanceAccountKind.BANK : form.getKind())
                .openingBalance(form.getOpeningBalance() == null ? BigDecimal.ZERO.setScale(2) : scale(form.getOpeningBalance()))
                .active(true)
                .build();
        if (account.getOpeningBalance().signum() < 0) {
            throw new BusinessRuleException("Початковий залишок не може бути від'ємним");
        }
        FinanceAccount saved = accountRepository.save(account);
        auditLogService.record(AuditAction.CREATE, "FinanceAccount", saved.getId(), FieldDiff.created(FieldDiff.snapshot()
                .add("Назва", saved.getName())
                .add("Вид", saved.getKind().getLabel())
                .add("Початковий залишок", saved.getOpeningBalance())
                .add("Активний", saved.isActive())));
        return saved;
    }

    /**
     * Назву й активність можна змінювати завжди; вид і початковий залишок — лише поки по рахунку
     * немає операцій, інакше це була б неконтрольована зміна вже облікованих залишків.
     */
    public void updateAccount(Long id, FinanceForms.Account form) {
        FinanceAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FinanceAccount %d not found".formatted(id)));
        String name = requireText(form.getName(), "Вкажіть назву");
        BigDecimal opening = form.getOpeningBalance() == null ? account.getOpeningBalance() : scale(form.getOpeningBalance());
        FinanceAccountKind kind = form.getKind() == null ? account.getKind() : form.getKind();
        boolean structuralChange = opening.compareTo(account.getOpeningBalance()) != 0 || kind != account.getKind();
        if (structuralChange && operationRepository.existsActiveForAccount(id)) {
            throw new BusinessRuleException("По рахунку вже є операції — вид і початковий залишок змінити не можна. "
                    + "Для виправлення залишку проведіть звірку.");
        }
        if (opening.signum() < 0) {
            throw new BusinessRuleException("Початковий залишок не може бути від'ємним");
        }
        Map<String, String[]> changes = FieldDiff.builder()
                .add("Назва", account.getName(), name)
                .add("Вид", account.getKind().getLabel(), kind.getLabel())
                .add("Активний", account.isActive(), form.isActive())
                .add("Початковий залишок", account.getOpeningBalance(), opening)
                .build();
        account.setName(name);
        account.setKind(kind);
        account.setOpeningBalance(opening);
        account.setActive(form.isActive());
        accountRepository.save(account);
        auditLogService.record(AuditAction.UPDATE, "FinanceAccount", id, changes);
    }

    // ------------------------------------------------------------------ розрахунки (читання, потрібні й тут)

    @Transactional(readOnly = true)
    public InvoiceSettlement settlementOf(Invoice invoice) {
        BigDecimal total = invoice.getItems().stream().map(InvoiceItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = operationRepository.sumForInvoice(invoice.getId(), FinanceOperationType.PAYMENT);
        BigDecimal refunded = operationRepository.sumForInvoice(invoice.getId(), FinanceOperationType.REFUND);
        return InvoiceSettlement.of(invoice.getId(), total, paid, refunded, invoice.getPaymentDueDate(), LocalDate.now());
    }

    /** Залишок рахунку на момент {@code at}: початковий + надходження − вибуття (розд. 7). */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(FinanceAccount account, Instant at) {
        BigDecimal balance = account.getOpeningBalance();
        for (Object[] row : operationRepository.sumBySourceAccountAndType(at)) {
            if (account.getId().equals(row[0])) {
                FinanceOperationType type = (FinanceOperationType) row[1];
                BigDecimal sum = (BigDecimal) row[2];
                balance = type.getSign() > 0 ? balance.add(sum) : balance.subtract(sum);
            }
        }
        for (Object[] row : operationRepository.sumByTargetAccount(at)) {
            if (account.getId().equals(row[0])) {
                balance = balance.add((BigDecimal) row[1]);
            }
        }
        return balance;
    }

    // ------------------------------------------------------------------ допоміжне

    private FinanceOperation save(FinanceOperation operation, String requestKey) {
        operation.setRequestKey(requestKey == null || requestKey.isBlank()
                ? UUID.randomUUID().toString()
                : requestKey.trim());
        operation.setOccurredAt(Instant.now());
        operation.setCreatedBy(CurrentUserRoles.username());
        FinanceOperation saved = operationRepository.save(operation);
        auditLogService.record(AuditAction.CREATE, "FinanceOperation", saved.getId(), FieldDiff.created(snapshot(saved)));
        return saved;
    }

    private static FieldDiff.Snapshot snapshot(FinanceOperation o) {
        return FieldDiff.snapshot()
                .add("Тип", o.getType().getLabel())
                .add("Сума, грн", o.getAmount())
                .add("Каса / рахунок", o.getAccount() == null ? null : o.getAccount().getName())
                .add("На рахунок", o.getTargetAccount() == null ? null : o.getTargetAccount().getName())
                .add("Спосіб оплати", o.getPaymentMethod() == null ? null : o.getPaymentMethod().getLabel())
                .add("Наряд", o.getInvoice() == null ? null : o.getInvoice().getNumber())
                .add("Отримано від клієнта", o.getReceivedAmount())
                .add("Решта", o.getChangeAmount())
                .add("Вид повернення", o.getRefundKind() == null ? null : o.getRefundKind().getLabel())
                .add("Повернення з операції", o.getOriginalOperation() == null ? null : "#" + o.getOriginalOperation().getId())
                .add("Категорія витрати", o.getExpenseCategory() == null ? null : o.getExpenseCategory().getLabel())
                .add("Контрагент", o.getCounterparty())
                .add("Документ", o.getDocumentRef())
                .add("Звірка", o.getReconciliation() == null ? null : "#" + o.getReconciliation().getId())
                .add("Коментар", o.getComment());
    }

    /**
     * Повторна відправка тієї самої форми (подвійний клік, "назад" + повторно) приносить той самий
     * ключ — повертаємо вже збережену операцію. Інший тип з тим самим ключем — підміна, відмовляємо.
     */
    private Optional<FinanceOperation> findDuplicate(String requestKey, FinanceOperationType type) {
        if (requestKey == null || requestKey.isBlank()) {
            return Optional.empty();
        }
        Optional<FinanceOperation> existing = operationRepository.findByRequestKey(requestKey.trim());
        if (existing.isPresent() && existing.get().getType() != type) {
            throw new BusinessRuleException("Цю форму вже використано для іншої операції — відкрийте її заново");
        }
        return existing;
    }

    private FinanceAccount resolveAccount(Long accountId, FinanceAccountKind requiredKind) {
        if (accountId != null) {
            FinanceAccount account = requireAccount(accountId);
            if (account.getKind() != requiredKind) {
                throw new BusinessRuleException("«%s» — це %s, а для цього способу оплати потрібен вид «%s»"
                        .formatted(account.getName(), account.getKind().getLabel().toLowerCase(), requiredKind.getLabel()));
            }
            return account;
        }
        List<FinanceAccount> candidates = accountRepository.findByKindAndActiveTrueOrderByIdAsc(requiredKind);
        if (candidates.isEmpty()) {
            throw new BusinessRuleException("Немає активного рахунку виду «%s» — додайте його в розділі «Каси й рахунки»"
                    .formatted(requiredKind.getLabel()));
        }
        if (candidates.size() > 1) {
            throw new BusinessRuleException("Оберіть, на який рахунок виду «%s» зараховано кошти".formatted(requiredKind.getLabel()));
        }
        return candidates.get(0);
    }

    private FinanceAccount requireAccount(Long accountId) {
        if (accountId == null) {
            throw new BusinessRuleException("Оберіть касу або рахунок");
        }
        FinanceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("FinanceAccount %d not found".formatted(accountId)));
        if (!account.isActive()) {
            throw new BusinessRuleException("Рахунок «%s» неактивний".formatted(account.getName()));
        }
        return account;
    }

    /** Фізичної готівки не може стати менше нуля; для банку й терміналу обмеження немає (виписка — окремо). */
    private void requireEnoughCash(FinanceAccount account, BigDecimal amount) {
        if (account.getKind() != FinanceAccountKind.CASH) {
            return;
        }
        BigDecimal available = balanceOf(account, Instant.now());
        if (amount.compareTo(available) > 0) {
            throw new BusinessRuleException("У касі «%s» лише %s — недостатньо для %s"
                    .formatted(account.getName(), money(available), money(amount)));
        }
    }

    private BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleException(field + " має бути більшою за нуль");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException(field + ": не більше двох знаків після коми");
        }
        return scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessRuleException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String money(BigDecimal value) {
        return String.format(Locale.forLanguageTag("uk"), "%,.2f грн", value);
    }
}
