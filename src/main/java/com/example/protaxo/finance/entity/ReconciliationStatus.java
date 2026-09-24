package com.example.protaxo.finance.entity;

public enum ReconciliationStatus {

    MATCHED("Збігається"),
    OPEN("Розбіжність — відкрита"),
    RESOLVED("Розбіжність — розглянута");

    private final String label;

    ReconciliationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
