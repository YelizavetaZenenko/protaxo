package com.example.protaxo.finance.dto;

/** Стан розрахунків за нарядом (розд. 6 концепції). Переплата заборонена, тож її стану немає. */
public enum SettlementStatus {

    NOT_PAID("Не оплачено", "status-unpaid"),
    PARTIALLY_PAID("Часткова оплата", "status-partial"),
    PAID("Оплачено", "status-paid");

    private final String label;
    private final String cssClass;

    SettlementStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }
}
