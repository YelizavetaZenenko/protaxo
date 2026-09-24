package com.example.protaxo.finance.entity;

public enum PaymentMethod {

    CASH("Готівка", FinanceAccountKind.CASH),
    CARD("Картка", FinanceAccountKind.CARD),
    TRANSFER("Банківський переказ", FinanceAccountKind.BANK);

    private final String label;
    private final FinanceAccountKind accountKind;

    PaymentMethod(String label, FinanceAccountKind accountKind) {
        this.label = label;
        this.accountKind = accountKind;
    }

    public String getLabel() {
        return label;
    }

    /** Куди фізично потрапляють кошти цим способом — оплата зараховується лише на рахунок такого виду. */
    public FinanceAccountKind getAccountKind() {
        return accountKind;
    }
}
