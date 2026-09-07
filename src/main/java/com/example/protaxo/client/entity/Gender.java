package com.example.protaxo.client.entity;

public enum Gender {

    MALE("Чоловіча"),
    FEMALE("Жіноча");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
