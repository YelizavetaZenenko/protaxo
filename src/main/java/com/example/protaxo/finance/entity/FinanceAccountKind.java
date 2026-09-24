package com.example.protaxo.finance.entity;

/** Вид місця обліку коштів. Картка й переказ ніколи не потрапляють у готівкову касу. */
public enum FinanceAccountKind {

    CASH("Готівкова каса"),
    BANK("Банківський рахунок"),
    CARD("Картковий термінал");

    private final String label;

    FinanceAccountKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
