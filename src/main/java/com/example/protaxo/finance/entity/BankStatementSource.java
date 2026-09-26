package com.example.protaxo.finance.entity;

/** Звідки беруться безготівкові надходження. Інтеграцій з банком поки немає — лише вручну. */
public enum BankStatementSource {

    MANUAL("Вручну");

    private final String label;

    BankStatementSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
