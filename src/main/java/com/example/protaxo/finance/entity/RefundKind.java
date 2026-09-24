package com.example.protaxo.finance.entity;

public enum RefundKind {

    ERRONEOUS_PAYMENT("Помилкова / зайва оплата"),
    WORK_CANCELLED("Скасування робіт");

    private final String label;

    RefundKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
