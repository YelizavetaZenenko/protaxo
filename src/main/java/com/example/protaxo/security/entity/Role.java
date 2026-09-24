package com.example.protaxo.security.entity;

public enum Role {

    ADMIN("Адміністратор"),
    MASTER("Майстер"),
    /** Окремий акаунт "лише бухгалтерія": фінанси + ціни/залишки/ПДВ у каталозі, див. SecurityConfig. */
    ACCOUNTANT("Бухгалтер");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
