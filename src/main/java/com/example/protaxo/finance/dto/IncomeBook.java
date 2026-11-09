package com.example.protaxo.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Книга обліку доходів і витрат ФОП на загальній системі — графи Типової форми (наказ Мінфіну
 * від 13.05.2021 № 261). Рядок — день, у який був дохід, повернення або витрата; місяці з підсумками.
 *
 * @param equipment придбане обладнання за період — у графи 6–9 не потрапляє, списується через
 *                  амортизацію (графа 10), яку рахує бухгалтерка
 */
public record IncomeBook(
        LocalDate from,
        LocalDate to,
        List<Month> months,
        Totals total,
        BigDecimal equipment
) {

    /**
     * Графи: 2 — дохід, 3 — повернення, 4 = 2 − 3, 5 — документи, 6 — товари й матеріали,
     * 7 — оплата праці, 8 — ЄСВ, податки, збори, 9 — інші витрати, 10 — амортизація (порожня),
     * 11 = 4 − (6..10); від'ємне значення — збиток, графу прокреслюють.
     */
    public record Row(LocalDate date, BigDecimal income, BigDecimal refunds, String documents,
                      BigDecimal goods, BigDecimal salary, BigDecimal taxes, BigDecimal other) {

        public BigDecimal declared() {
            return income.subtract(refunds);
        }

        public BigDecimal expenses() {
            return goods.add(salary).add(taxes).add(other);
        }

        public BigDecimal net() {
            return declared().subtract(expenses());
        }
    }

    public record Totals(BigDecimal income, BigDecimal refunds, BigDecimal goods, BigDecimal salary,
                         BigDecimal taxes, BigDecimal other) {

        public static Totals of(List<Row> rows) {
            return new Totals(
                    sum(rows.stream().map(Row::income).toList()),
                    sum(rows.stream().map(Row::refunds).toList()),
                    sum(rows.stream().map(Row::goods).toList()),
                    sum(rows.stream().map(Row::salary).toList()),
                    sum(rows.stream().map(Row::taxes).toList()),
                    sum(rows.stream().map(Row::other).toList()));
        }

        public static Totals ofTotals(List<Totals> parts) {
            return new Totals(
                    sum(parts.stream().map(Totals::income).toList()),
                    sum(parts.stream().map(Totals::refunds).toList()),
                    sum(parts.stream().map(Totals::goods).toList()),
                    sum(parts.stream().map(Totals::salary).toList()),
                    sum(parts.stream().map(Totals::taxes).toList()),
                    sum(parts.stream().map(Totals::other).toList()));
        }

        public BigDecimal declared() {
            return income.subtract(refunds);
        }

        public BigDecimal expenses() {
            return goods.add(salary).add(taxes).add(other);
        }

        public BigDecimal net() {
            return declared().subtract(expenses());
        }

        private static BigDecimal sum(List<BigDecimal> values) {
            return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record Month(YearMonth month, List<Row> rows, Totals totals) {
    }
}
