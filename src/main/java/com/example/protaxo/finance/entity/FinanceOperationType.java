package com.example.protaxo.finance.entity;

/**
 * Тип фінансової операції. {@code sign} — як операція змінює залишок рахунку {@code account}
 * (+1 надходження, −1 вибуття). TRANSFER додатково збільшує {@code targetAccount}.
 * Виручкою є лише PAYMENT (мінус REFUND), операційною витратою — лише EXPENSE: переміщення,
 * внесення/вилучення власника й коригування за звірками ні туди, ні туди не входять (розд. 7, 14).
 */
public enum FinanceOperationType {

    PAYMENT("Оплата від клієнта", 1),
    REFUND("Повернення клієнту", -1),
    EXPENSE("Витрата", -1),
    TRANSFER("Переміщення коштів", -1),
    OWNER_DEPOSIT("Внесення коштів власником", 1),
    OWNER_WITHDRAWAL("Вилучення коштів власником", -1),
    ADJUSTMENT_IN("Коригування за звіркою (+)", 1),
    ADJUSTMENT_OUT("Коригування за звіркою (−)", -1);

    private final String label;
    private final int sign;

    FinanceOperationType(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String getLabel() {
        return label;
    }

    public int getSign() {
        return sign;
    }
}
