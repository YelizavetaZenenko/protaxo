package com.example.protaxo.invoice.entity;

public enum InvoicePaymentType {

    CASH("Готівка"),
    TRANSFER("Перерахунок");

    private final String label;

    InvoicePaymentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
