package com.example.protaxo.finance.service;

import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.contract.service.ContractService;
import com.example.protaxo.finance.dto.SettlementAct;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.repository.FinanceOperationRepository;
import com.example.protaxo.invoice.entity.Invoice;
import com.example.protaxo.invoice.entity.InvoiceItem;
import com.example.protaxo.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Акт звірки взаєморозрахунків (docs/Фінансовий облік.md, розд. 21). Дебет — наряди контрагента
 * за датою наряду (сума до оплати) і повернення йому коштів; кредит — його оплати за датою
 * надходження. Скасовані операції й видалені наряди не враховуються.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementActService {

    private final InvoiceRepository invoiceRepository;
    private final FinanceOperationRepository operationRepository;
    private final ClientService clientService;
    private final ContractService contractService;

    public SettlementAct build(Long clientId, LocalDate from, LocalDate to) {
        var client = clientService.findById(clientId);
        String clientName = client.fullName() == null || client.fullName().isBlank() ? client.name() : client.fullName();
        ContractResponse contract = contractService.findLatestByClientId(clientId).orElse(null);

        List<SettlementAct.Row> all = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByClientIdWithItems(clientId)) {
            BigDecimal total = invoice.getItems().stream().map(InvoiceItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() == 0) {
                continue;
            }
            all.add(new SettlementAct.Row(invoice.getDocumentDate().toLocalDate(),
                    "Акт виконаних робіт № " + invoice.getNumber(), total, BigDecimal.ZERO));
        }
        for (FinanceOperation op : operationRepository.findSettlementsByClientId(clientId)) {
            LocalDate date = op.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate();
            String number = op.getInvoice().getNumber();
            if (op.getType() == FinanceOperationType.PAYMENT) {
                all.add(new SettlementAct.Row(date, "Оплата (" + op.getPaymentMethod().getLabel().toLowerCase()
                        + ") за наряд № " + number, BigDecimal.ZERO, op.getAmount()));
            } else {
                all.add(new SettlementAct.Row(date, "Повернення коштів за наряд № " + number,
                        op.getAmount(), BigDecimal.ZERO));
            }
        }
        // У межах дня спершу нарахування, потім оплати — як у звичайному акті звірки.
        all.sort(Comparator.comparing(SettlementAct.Row::date)
                .thenComparing(r -> r.debit().signum() > 0 ? 0 : 1)
                .thenComparing(SettlementAct.Row::document));

        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<SettlementAct.Row> rows = new ArrayList<>();
        for (SettlementAct.Row row : all) {
            if (row.date().isBefore(from)) {
                opening = opening.add(row.debit()).subtract(row.credit());
            } else if (!row.date().isAfter(to)) {
                rows.add(row);
                debit = debit.add(row.debit());
                credit = credit.add(row.credit());
            }
        }
        return new SettlementAct(clientId, clientName, from, to,
                contract == null ? null : contract.contractNumber(),
                contract == null ? null : contract.contractDate(),
                opening, rows, debit, credit, opening.add(debit).subtract(credit));
    }
}
