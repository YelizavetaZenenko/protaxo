package com.example.protaxo.security.entity;

public enum Role {

    ADMIN("Адміністратор"),
    MASTER("Майстер");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
