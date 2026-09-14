package com.example.protaxo.audit.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.entity.AuditLog;
import com.example.protaxo.audit.repository.AuditLogRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private static final Logger FILE_LOGGER = LoggerFactory.getLogger("AUDIT");
    private static final TypeReference<Map<String, String[]>> CHANGES_TYPE = new TypeReference<>() {};

    /** entityType is stored as the raw Java entity class name - translated here for display only. */
    private static final Map<String, String> ENTITY_TYPE_LABELS = Map.ofEntries(
            Map.entry("Vehicle", "Автомобіль"),
            Map.entry("Tachograph", "Тахограф"),
            Map.entry("Client", "Контрагент"),
            Map.entry("Driver", "Водій"),
            Map.entry("Contract", "Договір"),
            Map.entry("Invoice", "Наряд-заказ"),
            Map.entry("CatalogItem", "Позиція каталогу"),
            Map.entry("CalibrationProtocol", "Протокол калібрування"),
            Map.entry("User", "Користувач")
    );

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void record(AuditAction action, String entityType, Long entityId) {
        record(action, entityType, entityId, null);
    }

    /**
     * Same as {@link #record(AuditAction, String, Long)}, plus a field-level diff — {@code
     * {"fieldLabel": ["old value", "new value"]}} — so the UI can flag exactly which fields
     * changed on the most recent edit (see [[Автомобілі]]/[[Тахографи]]). Only fields that
     * actually differ should be in the map; an empty/null map is stored as no diff at all.
     */
    public void record(AuditAction action, String entityType, Long entityId, Map<String, String[]> changes) {
        String username = currentUsername();
        String changesJson = (changes == null || changes.isEmpty()) ? null : writeChanges(changes);

        AuditLog log = AuditLog.builder()
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .occurredAt(Instant.now())
                .changes(changesJson)
                .build();
        auditLogRepository.save(log);

        FILE_LOGGER.info("user={} action={} entityType={} entityId={} changes={}", username, action, entityType, entityId, changesJson);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findRecent(Pageable pageable) {
        return auditLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    /**
     * The field-level diff from the most recent update of this entity that actually changed
     * something, or an empty map if there isn't one (never edited, or every past edit was a
     * no-op diff). Used to render small "changed" badges next to fields in the [[Автомобілі]]
     * and [[Тахографи]] tables.
     */
    @Transactional(readOnly = true)
    public Map<String, String[]> findLatestChanges(String entityType, Long entityId) {
        return auditLogRepository.findFirstByEntityTypeAndEntityIdAndChangesIsNotNullOrderByOccurredAtDesc(entityType, entityId)
                .map(log -> parseChanges(log.getChanges()))
                .orElse(Map.of());
    }

    private String writeChanges(Map<String, String[]> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            FILE_LOGGER.warn("Failed to serialize audit changes: {}", e.getMessage());
            return null;
        }
    }

    /** Public so the "Журнал дій" view can render each entry's field-level diff (see audit-log/list.html). */
    public Map<String, String[]> parseChanges(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, CHANGES_TYPE);
        } catch (Exception e) {
            FILE_LOGGER.warn("Failed to deserialize audit changes: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Public so the "Журнал дій" view can show the entity type in Ukrainian (see audit-log/list.html). */
    public String translateEntityType(String entityType) {
        return ENTITY_TYPE_LABELS.getOrDefault(entityType, entityType);
    }

    /** Public so the "Журнал дій" view can show the action in Ukrainian (see audit-log/list.html). */
    public String translateAction(AuditAction action) {
        return switch (action) {
            case CREATE -> "Створено";
            case UPDATE -> "Оновлено";
            case DELETE -> "Видалено";
        };
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
