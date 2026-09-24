package com.example.protaxo.common.vat;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ставка / податковий режим позиції каталогу й рядка наряду (docs/Фінансовий облік.md, розд. 11).
 * Ціна завжди вводиться З ПДВ, тому ПДВ виділяється з суми рядка: {@code amount * rate / (100 + rate)},
 * округлення HALF_UP до копійок на кожному рядку; підсумок документа = сума рядків.
 */
public enum VatRate {

    VAT_20("20%", 20),
    VAT_7("7%", 7),
    VAT_0("0%", 0),
    NO_VAT("Без ПДВ", 0);

    private final String label;
    private final int percent;

    VatRate(String label, int percent) {
        this.label = label;
        this.percent = percent;
    }

    public String getLabel() {
        return label;
    }

    public int getPercent() {
        return percent;
    }

    /** ПДВ, що міститься в сумі {@code amountWithVat}. */
    public BigDecimal vatFrom(BigDecimal amountWithVat) {
        if (percent == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amountWithVat.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100L + percent), 2, RoundingMode.HALF_UP);
    }
}
