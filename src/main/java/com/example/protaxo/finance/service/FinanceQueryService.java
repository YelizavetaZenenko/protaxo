package com.example.protaxo.finance.service;

import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.finance.dto.AccountBalanceView;
import com.example.protaxo.finance.dto.InvoiceSettlement;
import com.example.protaxo.finance.dto.OperationView;
import com.example.protaxo.finance.dto.PeriodTotals;
import com.example.protaxo.finance.dto.ReconciliationView;
import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.FinanceAccount;
import com.example.protaxo.finance.entity.FinanceAttachment;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.entity.Reconciliation;
import com.example.protaxo.finance.entity.ReconciliationStatus;
import com.example.protaxo.finance.repository.FinanceAccountRepository;
import com.example.protaxo.finance.repository.FinanceAttachmentRepository;
import com.example.protaxo.finance.repository.FinanceOperationRepository;
import com.example.protaxo.finance.repository.ReconciliationRepository;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceQueryService {

    private final FinanceOperationRepository operationRepository;
    private final FinanceAccountRepository accountRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final FinanceAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final FinanceService financeService;

    // ------------------------------------------------------------------ рахунки

    public List<FinanceAccount> accounts() {
        return accountRepository.findAllByOrderByKindAscIdAsc();
    }

    public List<FinanceAccount> activeAccounts() {
        return accountRepository.findByActiveTrueOrderByKindAscIdAsc();
    }

    public FinanceAccount account(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FinanceAccount %d not found".formatted(id)));
    }

    /** Залишки на момент {@code at} — одним проходом по агрегатах, не по рахунку за раз. */
    public List<AccountBalanceView> balances(Instant at) {
        Map<Long, BigDecimal> movement = new HashMap<>();
        for (Object[] row : operationRepository.sumBySourceAccountAndType(at)) {
            FinanceOperationType type = (FinanceOperationType) row[1];
            BigDecimal sum = (BigDecimal) row[2];
            movement.merge((Long) row[0], type.getSign() > 0 ? sum : sum.negate(), BigDecimal::add);
        }
        for (Object[] row : operationRepository.sumByTargetAccount(at)) {
            movement.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::add);
        }
        return accounts().stream()
                .map(a -> new AccountBalanceView(a.getId(), a.getName(), a.getKind(), a.isActive(), a.getOpeningBalance(),
                        a.getOpeningBalance().add(movement.getOrDefault(a.getId(), BigDecimal.ZERO))))
                .toList();
    }

    // ------------------------------------------------------------------ наряди

    /** Розрахунки для списку нарядів: суми оплат/повернень одним запитом на всі наряди. */
    public Map<Long, InvoiceSettlement> settlements(Collection<InvoiceResponse> invoices) {
        Map<Long, BigDecimal> paid = new HashMap<>();
        Map<Long, BigDecimal> refunded = new HashMap<>();
        for (Object[] row : operationRepository.sumSettlementsByInvoice()) {
            Map<Long, BigDecimal> target = row[1] == FinanceOperationType.PAYMENT ? paid : refunded;
            target.put((Long) row[0], (BigDecimal) row[2]);
        }
        LocalDate today = LocalDate.now();
        return invoices.stream().collect(Collectors.toMap(InvoiceResponse::id, inv -> InvoiceSettlement.of(inv.id(),
                inv.totalAmount(), paid.getOrDefault(inv.id(), BigDecimal.ZERO),
                refunded.getOrDefault(inv.id(), BigDecimal.ZERO), inv.paymentDueDate(), today)));
    }

    public InvoiceSettlement settlement(InvoiceResponse invoice) {
        return settlements(List.of(invoice)).get(invoice.id());
    }

    public List<OperationView> operationsForInvoice(Long invoiceId) {
        return toViews(operationRepository.findByInvoiceId(invoiceId));
    }

    // ------------------------------------------------------------------ журнал і обороти

    public List<OperationView> search(Instant from, Instant to, Collection<FinanceOperationType> types,
                                      Long accountId, boolean includeCancelled) {
        Collection<FinanceOperationType> effectiveTypes = types == null || types.isEmpty()
                ? List.of(FinanceOperationType.values()) : types;
        return toViews(operationRepository.search(from, to, effectiveTypes, accountId, includeCancelled));
    }

    public PeriodTotals totals(Instant from, Instant to) {
        List<FinanceOperation> operations = operationRepository.search(from, to,
                List.of(FinanceOperationType.values()), null, false);
        Map<PaymentMethod, BigDecimal> paymentsByMethod = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> refundsByMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            paymentsByMethod.put(method, BigDecimal.ZERO);
            refundsByMethod.put(method, BigDecimal.ZERO);
        }
        Map<ExpenseCategory, BigDecimal> expensesByCategory = new EnumMap<>(ExpenseCategory.class);
        BigDecimal payments = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal deposits = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        long paymentCount = 0;
        for (FinanceOperation op : operations) {
            BigDecimal amount = op.getAmount();
            switch (op.getType()) {
                case PAYMENT -> {
                    paymentsByMethod.merge(op.getPaymentMethod(), amount, BigDecimal::add);
                    payments = payments.add(amount);
                    paymentCount++;
                }
                case REFUND -> {
                    refundsByMethod.merge(op.getPaymentMethod(), amount, BigDecimal::add);
                    refunds = refunds.add(amount);
                }
                case EXPENSE -> {
                    expensesByCategory.merge(op.getExpenseCategory(), amount, BigDecimal::add);
                    expenses = expenses.add(amount);
                }
                case OWNER_DEPOSIT -> deposits = deposits.add(amount);
                case OWNER_WITHDRAWAL -> withdrawals = withdrawals.add(amount);
                default -> {
                    // TRANSFER і коригування за звірками не є ні надходженням, ні витратою.
                }
            }
        }
        return new PeriodTotals(paymentsByMethod, refundsByMethod, payments, refunds, paymentCount,
                expensesByCategory, expenses, deposits, withdrawals);
    }

    // ------------------------------------------------------------------ звірки

    public List<ReconciliationView> reconciliations() {
        return toReconciliationViews(reconciliationRepository.findAllDetailed());
    }

    public List<ReconciliationView> openReconciliations() {
        return toReconciliationViews(reconciliationRepository.findByStatusDetailed(ReconciliationStatus.OPEN));
    }

    public ReconciliationView reconciliation(Long id) {
        return toReconciliationViews(List.of(reconciliationRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Reconciliation %d not found".formatted(id))))).get(0);
    }

    public FinanceAttachment attachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment %d not found".formatted(id)));
    }

    public OperationView operation(Long id) {
        return toViews(List.of(operationRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Operation %d not found".formatted(id))))).get(0);
    }

    // ------------------------------------------------------------------ перетворення

    private List<OperationView> toViews(List<FinanceOperation> operations) {
        if (operations.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = userNames();
        List<Long> ids = operations.stream().map(FinanceOperation::getId).toList();
        Map<Long, Object[]> attachments = attachmentRepository.findMetaByOperationIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[1], Function.identity(), (a, b) -> a));
        return operations.stream().map(op -> {
            Object[] attachment = attachments.get(op.getId());
            BigDecimal refunded = op.getType() == FinanceOperationType.PAYMENT
                    ? operationRepository.sumRefundsOf(op.getId()) : null;
            return new OperationView(
                    op.getId(), op.getType(), op.getAccount().getId(), op.getAccount().getName(),
                    op.getTargetAccount() == null ? null : op.getTargetAccount().getName(),
                    op.getAmount(), op.getPaymentMethod(),
                    op.getInvoice() == null ? null : op.getInvoice().getId(),
                    op.getInvoice() == null ? null : op.getInvoice().getNumber(),
                    op.getOriginalOperation() == null ? null : op.getOriginalOperation().getId(),
                    op.getReceivedAmount(), op.getChangeAmount(), op.getRefundKind(), op.getExpenseCategory(),
                    op.getCounterparty(), op.getDocumentRef(), op.getComment(), op.getOccurredAt(),
                    names.getOrDefault(op.getCreatedBy(), op.getCreatedBy()),
                    op.getCancelledAt(),
                    op.getCancelledBy() == null ? null : names.getOrDefault(op.getCancelledBy(), op.getCancelledBy()),
                    op.getCancelReason(),
                    attachment == null ? null : (Long) attachment[0],
                    attachment == null ? null : (String) attachment[2],
                    refunded);
        }).toList();
    }

    private List<ReconciliationView> toReconciliationViews(List<Reconciliation> reconciliations) {
        Map<String, String> names = userNames();
        return reconciliations.stream().map(r -> new ReconciliationView(
                r.getId(), r.getAccount().getName(), r.getCountedAt(), r.getExpectedBalance(), r.getActualBalance(),
                r.getDifference(), r.getStatus(), r.getComment(),
                names.getOrDefault(r.getCreatedBy(), r.getCreatedBy()),
                r.getResolvedAt(),
                r.getResolvedBy() == null ? null : names.getOrDefault(r.getResolvedBy(), r.getResolvedBy()),
                r.getResolutionComment())).toList();
    }

    /** email → ПІБ; операції зберігають email автора (як і журнал дій), показуємо ім'я. */
    private Map<String, String> userNames() {
        return userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getEmail, User::getFullName, (a, b) -> a));
    }

    /** Поточний залишок однієї каси — для форм, де показуємо "у касі зараз". */
    public BigDecimal balanceNow(FinanceAccount account) {
        return financeService.balanceOf(account, Instant.now());
    }
}
