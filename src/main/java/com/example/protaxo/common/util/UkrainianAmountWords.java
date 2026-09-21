package com.example.protaxo.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a hryvnia amount to Ukrainian words for "сума словами" on printed documents
 * (e.g. "Тринадцять тисяч вісімсот гривень 00 копійок"). Kopecks are rendered as digits,
 * matching the convention used on real Ukrainian invoices — only the hryvnia part is spelled out.
 */
public final class UkrainianAmountWords {

    private UkrainianAmountWords() {
    }

    private static final String[] ONES_MASCULINE = {
            "", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять"
    };
    private static final String[] ONES_FEMININE = {
            "", "одна", "дві", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять"
    };
    private static final String[] TEENS = {
            "десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять",
            "п'ятнадцять", "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять"
    };
    private static final String[] TENS = {
            "", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят", "сімдесят", "вісімдесят", "дев'яносто"
    };
    private static final String[] HUNDREDS = {
            "", "сто", "двісті", "триста", "чотириста", "п'ятсот", "шістсот", "сімсот", "вісімсот", "дев'ятсот"
    };

    private static final String[] THOUSAND_FORMS = {"тисяча", "тисячі", "тисяч"};
    private static final String[] MILLION_FORMS = {"мільйон", "мільйони", "мільйонів"};
    private static final String[] BILLION_FORMS = {"мільярд", "мільярди", "мільярдів"};
    private static final String[] HRYVNIA_FORMS = {"гривня", "гривні", "гривень"};
    private static final String[] KOPECK_FORMS = {"копійка", "копійки", "копійок"};

    private static final long MAX_HRYVNIAS = 1_000_000_000_000L;

    /**
     * @throws IllegalArgumentException for a negative amount (the digit-by-digit conversion below
     *         assumes non-negative input and would otherwise silently print a malformed line like
     *         "-70" for kopecks instead of failing) or an amount at/above 1 trillion hryvnias (the
     *         group breakdown below only goes up to billions — silently truncating anything larger
     *         would print the wrong number rather than fail). Every real caller sums positive
     *         quantity×price values, so both are far outside anything this business would ever
     *         legitimately pass in.
     */
    public static String amountToWords(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Сума не може бути від'ємною: " + amount);
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        long hryvnias = normalized.longValue();
        if (hryvnias >= MAX_HRYVNIAS) {
            throw new IllegalArgumentException("Сума занадто велика для запису словами: " + amount);
        }
        int kopecks = normalized.subtract(BigDecimal.valueOf(hryvnias))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        String hryvniaWords = hryvnias == 0 ? "нуль" : integerToWords(hryvnias, true);
        String hryvniaForm = pluralForm(hryvnias, HRYVNIA_FORMS);
        String kopeckForm = pluralForm(kopecks, KOPECK_FORMS);

        return capitalize(hryvniaWords) + " " + hryvniaForm + " " + String.format("%02d", kopecks) + " " + kopeckForm;
    }

    private static String integerToWords(long n, boolean finalGroupFeminine) {
        if (n == 0) {
            return "нуль";
        }
        long billions = n / 1_000_000_000L;
        n %= 1_000_000_000L;
        long millions = n / 1_000_000L;
        n %= 1_000_000L;
        long thousands = n / 1000L;
        long remainder = n % 1000L;

        List<String> parts = new ArrayList<>();
        if (billions > 0) {
            appendGroup(parts, billions, ONES_MASCULINE);
            parts.add(pluralForm(billions, BILLION_FORMS));
        }
        if (millions > 0) {
            appendGroup(parts, millions, ONES_MASCULINE);
            parts.add(pluralForm(millions, MILLION_FORMS));
        }
        if (thousands > 0) {
            appendGroup(parts, thousands, ONES_FEMININE);
            parts.add(pluralForm(thousands, THOUSAND_FORMS));
        }
        if (remainder > 0) {
            appendGroup(parts, remainder, finalGroupFeminine ? ONES_FEMININE : ONES_MASCULINE);
        }
        return String.join(" ", parts);
    }

    private static void appendGroup(List<String> parts, long groupValue, String[] onesForm) {
        String words = convertGroup((int) groupValue, onesForm);
        if (!words.isEmpty()) {
            parts.add(words);
        }
    }

    private static String convertGroup(int number, String[] ones) {
        List<String> words = new ArrayList<>();
        int hundreds = number / 100;
        int rem = number % 100;
        if (hundreds > 0) {
            words.add(HUNDREDS[hundreds]);
        }
        if (rem >= 10 && rem < 20) {
            words.add(TEENS[rem - 10]);
        } else {
            int tens = rem / 10;
            int unit = rem % 10;
            if (tens >= 2) {
                words.add(TENS[tens]);
            }
            if (unit > 0) {
                words.add(ones[unit]);
            }
        }
        return String.join(" ", words);
    }

    private static String pluralForm(long n, String[] forms) {
        long mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 14) {
            return forms[2];
        }
        long mod10 = n % 10;
        if (mod10 == 1) {
            return forms[0];
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return forms[1];
        }
        return forms[2];
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
