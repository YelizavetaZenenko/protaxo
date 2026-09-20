package com.example.protaxo.security.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4 / Jackson 3 — пакет тепер {@code tools.jackson.*}, не
 * {@code com.fasterxml.jackson.*} (лише jackson-annotations лишився на старому груп-ID).
 */
@Converter
public class PermissionsJsonConverter implements AttributeConverter<Map<String, Boolean>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Boolean> attribute) {
        return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
    }

    @Override
    public Map<String, Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        return MAPPER.readValue(dbData, new TypeReference<Map<String, Boolean>>() {
        });
    }
}
