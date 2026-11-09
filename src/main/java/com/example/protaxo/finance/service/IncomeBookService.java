package com.example.protaxo.finance.service;

import com.example.protaxo.finance.dto.IncomeBook;
import com.example.protaxo.finance.entity.ExpenseCategory;
import com.example.protaxo.finance.entity.FinanceOperation;
import com.example.protaxo.finance.entity.FinanceOperationType;
import com.example.protaxo.finance.repository.FinanceOperationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Книга обліку доходів і витрат (docs/Фінансовий облік.md, розд. 22) — з нескасованих операцій
 * панелі бухгалтера: оплати клієнтів (графа 2), повернення (3), витрати за категоріями (6–9).
 * Касовий метод: дата — коли гроші фактично надійшли чи були сплачені. Переміщення, внесення й
 * вилучення власника та коригування звірок — не дохід і не витрата, у книгу не йдуть.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncomeBookService {

    private final FinanceOperationRepository operationRepository;

    public IncomeBook build(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        List<FinanceOperation> operations = operationRepository.search(
                from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                List.of(FinanceOperationType.PAYMENT, FinanceOperationType.REFUND, FinanceOperationType.EXPENSE),
                null, false);

        Map<LocalDate, Day> days = new TreeMap<>();
        BigDecimal equipment = BigDecimal.ZERO;
        // Пошук повертає найновіші першими — у книзі (і в переліку документів дня) хронологічно.
        for (FinanceOperation op : operations.reversed()) {
            LocalDate date = op.getOccurredAt().atZone(zone).toLocalDate();
            Day day = days.computeIfAbsent(date, d -> new Day());
            switch (op.getType()) {
                case PAYMENT -> {
                    day.income = day.income.add(op.getAmount());
                    day.documents.add("оплата за наряд № " + op.getInvoice().getNumber());
                }
                case REFUND -> {
                    day.refunds = day.refunds.add(op.getAmount());
                    day.documents.add("повернення за наряд № " + op.getInvoice().getNumber());
                }
                case EXPENSE -> {
                    ExpenseCategory category = op.getExpenseCategory() == null ? ExpenseCategory.OTHER : op.getExpenseCategory();
                    switch (category) {
                        // Основні засоби — не витрата дня, а амортизація; рахує бухгалтерка окремо.
                        case EQUIPMENT -> equipment = equipment.add(op.getAmount());
                        case PARTS -> day.goods = day.goods.add(op.getAmount());
                        case SALARY -> day.salary = day.salary.add(op.getAmount());
                        case TAXES -> day.taxes = day.taxes.add(op.getAmount());
                        default -> day.other = day.other.add(op.getAmount());
                    }
                    if (category != ExpenseCategory.EQUIPMENT) {
                        day.documents.add(expenseDocument(op));
                    }
                }
                default -> { }
            }
        }

        Map<YearMonth, List<IncomeBook.Row>> byMonth = new TreeMap<>();
        days.forEach((date, d) -> {
            if (!d.isEmpty()) {
                byMonth.computeIfAbsent(YearMonth.from(date), m -> new ArrayList<>()).add(new IncomeBook.Row(
                        date, d.income, d.refunds, String.join("; ", d.documents), d.goods, d.salary, d.taxes, d.other));
            }
        });
        List<IncomeBook.Month> months = new ArrayList<>();
        byMonth.forEach((month, rows) -> months.add(new IncomeBook.Month(month, rows, IncomeBook.Totals.of(rows))));
        IncomeBook.Totals total = IncomeBook.Totals.ofTotals(months.stream().map(IncomeBook.Month::totals).toList());
        return new IncomeBook(from, to, months, total, equipment);
    }

    private static String expenseDocument(FinanceOperation op) {
        String label;
        if (op.getDocumentRef() != null && !op.getDocumentRef().isBlank()) {
            label = op.getDocumentRef().trim();
        } else if (op.getComment() != null && !op.getComment().isBlank()) {
            label = op.getComment().trim();
        } else {
            label = op.getExpenseCategory() == null ? "витрата" : op.getExpenseCategory().getLabel().toLowerCase(Locale.ROOT);
        }
        if (op.getCounterparty() != null && !op.getCounterparty().isBlank()) {
            label += ", " + op.getCounterparty().trim();
        }
        return label;
    }

    private static final class Day {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal goods = BigDecimal.ZERO;
        BigDecimal salary = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        final Set<String> documents = new LinkedHashSet<>();

        boolean isEmpty() {
            return income.signum() == 0 && refunds.signum() == 0 && goods.signum() == 0
                    && salary.signum() == 0 && taxes.signum() == 0 && other.signum() == 0;
        }
    }
}
