package com.example.protaxo.invoice.entity;

/**
 * Стан акта виконаних робіт за нарядом (панель бухгалтера, вкладка «Документи»). Перший друк
 * акта переводить NOT_CREATED → ON_SIGNING; підписаний акт бухгалтер відмічає вручну.
 */
public enum ActStatus {

    NOT_CREATED("Акт не створено", "status-partial"),
    ON_SIGNING("Акт на підписі", "status-partial"),
    SIGNED("Документи готові", "status-paid");

    private final String label;
    private final String cssClass;

    ActStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }

    public boolean needsAttention() {
        return this != SIGNED;
    }
}
