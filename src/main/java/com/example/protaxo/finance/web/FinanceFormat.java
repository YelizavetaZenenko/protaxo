package com.example.protaxo.finance.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Форматування для панелі бухгалтера, доступне в шаблонах як {@code ${@financeFormat.money(x)}}:
 * «12 450 ₴» (копійки — лише коли вони є), відмінювання «1 наряд / 4 наряди / 5 нарядів» і
 * підпис періоду «01–26 вересня 2026».
 */
@Component("financeFormat")
public class FinanceFormat {

    private static final Locale UK = Locale.forLanguageTag("uk");
    private static final String[] MONTHS_GENITIVE = {"січня", "лютого", "березня", "квітня", "травня", "червня",
            "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"};
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM");

    public String money(BigDecimal value) {
        if (value == null) {
            return "0 ₴";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        boolean whole = scaled.remainder(BigDecimal.ONE).signum() == 0;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(UK);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00", symbols);
        return format.format(scaled) + " ₴";
    }

    /** Українське відмінювання за числом: plural(4, "наряд", "наряди", "нарядів") → «4 наряди». */
    public String plural(long count, String one, String few, String many) {
        long mod100 = Math.abs(count) % 100;
        long mod10 = mod100 % 10;
        String word;
        if (mod100 >= 11 && mod100 <= 14) {
            word = many;
        } else if (mod10 == 1) {
            word = one;
        } else if (mod10 >= 2 && mod10 <= 4) {
            word = few;
        } else {
            word = many;
        }
        return count + " " + word;
    }

    public String period(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "";
        }
        String toPart = "%02d %s %d".formatted(to.getDayOfMonth(), MONTHS_GENITIVE[to.getMonthValue() - 1], to.getYear());
        if (from.equals(to)) {
            return toPart;
        }
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()) {
            return "%02d–%s".formatted(from.getDayOfMonth(), toPart);
        }
        String fromPart = from.getYear() == to.getYear()
                ? "%02d %s".formatted(from.getDayOfMonth(), MONTHS_GENITIVE[from.getMonthValue() - 1])
                : "%02d %s %d".formatted(from.getDayOfMonth(), MONTHS_GENITIVE[from.getMonthValue() - 1], from.getYear());
        return fromPart + " – " + toPart;
    }

    public String dayMonth(LocalDate date) {
        return date == null ? "" : DAY_MONTH.format(date);
    }
}
