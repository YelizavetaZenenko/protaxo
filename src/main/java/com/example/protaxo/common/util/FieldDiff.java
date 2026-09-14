package com.example.protaxo.common.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a {@code {"fieldLabel": ["old value", "new value"]}} map for {@link
 * com.example.protaxo.audit.service.AuditLogService#record(com.example.protaxo.audit.entity.AuditAction, String, Long, Map)}
 * — only fields whose old/new value actually differ end up in the map. Used to flag exactly
 * which fields changed on the most recent edit (see [[Автомобілі]]/[[Тахографи]]).
 */
public final class FieldDiff {

    private FieldDiff() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, String[]> changes = new LinkedHashMap<>();

        public Builder add(String label, Object oldValue, Object newValue) {
            String oldStr = oldValue == null ? "" : oldValue.toString();
            String newStr = newValue == null ? "" : newValue.toString();
            if (!Objects.equals(oldStr, newStr)) {
                changes.put(label, new String[]{oldStr, newStr});
            }
            return this;
        }

        public Map<String, String[]> build() {
            return changes;
        }
    }
}
