package com.example.protaxo.common.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a {@code {"fieldLabel": ["old value", "new value"]}} map for {@link
 * com.example.protaxo.audit.service.AuditLogService#record(com.example.protaxo.audit.entity.AuditAction, String, Long, Map)}
 * — only fields whose old/new value actually differ end up in the map. Used to flag exactly
 * which fields changed on the most recent edit (see [[Автомобілі]]/[[Тахографи]]).
 *
 * <p>Two ways to use it: the {@link #builder()} compares old/new values pairwise; a {@link
 * Snapshot} captures an entity's labelled fields once, so the same snapshot serves CREATE (every
 * filled field, "—" → value), UPDATE ({@link #between}) and DELETE (value → "—") — see
 * [[Журнал дій (Audit Log)]].
 */
public final class FieldDiff {

    private FieldDiff() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Snapshot snapshot() {
        return new Snapshot();
    }

    /** Fields that differ between two snapshots of the same entity (before and after an edit). */
    public static Map<String, String[]> between(Snapshot before, Snapshot after) {
        Set<String> labels = new LinkedHashSet<>(before.values.keySet());
        labels.addAll(after.values.keySet());
        Builder builder = builder();
        for (String label : labels) {
            builder.add(label, before.values.get(label), after.values.get(label));
        }
        return builder.build();
    }

    /** Every filled field of a newly created entity, as "" → value. */
    public static Map<String, String[]> created(Snapshot after) {
        return between(snapshot(), after);
    }

    /** Every filled field of a deleted entity, as value → "". */
    public static Map<String, String[]> deleted(Snapshot before) {
        return between(before, snapshot());
    }

    static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return b ? "так" : "ні";
        }
        if (value instanceof BigDecimal d) {
            // 100 і 100.00 — одне й те саме значення, не зміна.
            return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        }
        return value.toString().trim();
    }

    public static final class Builder {
        private final Map<String, String[]> changes = new LinkedHashMap<>();

        public Builder add(String label, Object oldValue, Object newValue) {
            String oldStr = format(oldValue);
            String newStr = format(newValue);
            if (!Objects.equals(oldStr, newStr)) {
                changes.put(label, new String[]{oldStr, newStr});
            }
            return this;
        }

        public Map<String, String[]> build() {
            return changes;
        }
    }

    /** Labelled field values of one entity at one moment; values are formatted immediately. */
    public static final class Snapshot {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Snapshot add(String label, Object value) {
            values.put(label, format(value));
            return this;
        }
    }
}
