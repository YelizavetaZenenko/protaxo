package com.example.protaxo.finance.dto;

/** Стан розрахунків за нарядом (розд. 6 концепції). Переплата заборонена, тож її стану немає. */
public enum SettlementStatus {

    NOT_PAID("Не оплачено", "settle-unpaid"),
    PARTIALLY_PAID("Часткова оплата", "settle-partial"),
    PAID("Оплачено", "settle-paid");

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
