package com.example.protaxo.catalog.entity;

/**
 * Одиниця виміру товару в каталозі: штуки або метри (кабель тощо). Ціна в каталозі — за одну
 * одиницю, кількість у наряді — у цих одиницях (5 м × 100 грн = 500 грн). У послуг одиниці немає —
 * у документах вони друкуються як «послуга».
 */
public enum CatalogUnit {
    PIECE("шт"),
    METER("м");

    private final String label;

    CatalogUnit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
