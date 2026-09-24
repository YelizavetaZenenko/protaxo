package com.example.protaxo.finance.entity;

public enum ExpenseCategory {

    PARTS("Запчастини та матеріали"),
    RENT("Оренда"),
    UTILITIES("Комунальні послуги"),
    SALARY("Зарплата"),
    TAXES("Податки та збори"),
    BANK_FEES("Банківські комісії"),
    EQUIPMENT("Обладнання та інструмент"),
    TRANSPORT("Транспорт і пальне"),
    OFFICE("Офіс і господарські"),
    OTHER("Інше");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
