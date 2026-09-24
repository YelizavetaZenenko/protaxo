package com.example.protaxo.stockrevision.entity;

public enum StockRevisionStatus {

    DRAFT("Чернетка"),
    COMPLETED("Проведена");

    private final String label;

    StockRevisionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
