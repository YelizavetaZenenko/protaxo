package com.example.protaxo.finance.entity;

/** Система оподаткування власника сервісу — довідково для блоку «Налаштування обліку». */
public enum TaxSystem {

    SINGLE_TAX_1("Єдиний податок, 1 група"),
    SINGLE_TAX_2("Єдиний податок, 2 група"),
    SINGLE_TAX_3("Єдиний податок, 3 група"),
    GENERAL("Загальна система");

    private final String label;

    TaxSystem(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
