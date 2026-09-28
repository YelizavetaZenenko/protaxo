package com.example.protaxo.audit.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.entity.AuditLog;
import com.example.protaxo.audit.repository.AuditLogRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
            Map.entry("Driver", "Працівник"),
            Map.entry("Contract", "Договір"),
            Map.entry("Invoice", "Наряд-заказ"),
            Map.entry("CatalogItem", "Позиція каталогу"),
            Map.entry("CalibrationProtocol", "Протокол калібрування"),
            Map.entry("User", "Користувач"),
            Map.entry("FinanceOperation", "Фінансова операція"),
            Map.entry("FinanceAccount", "Каса / рахунок"),
            Map.entry("Reconciliation", "Звірка готівки"),
            Map.entry("FinanceSettings", "Налаштування обліку"),
            Map.entry("StockRevision", "Ревізія складу"),
            Map.entry("RolePermissions", "Права ролей"),
            Map.entry("RepairWorker", "Робітник"),
            Map.entry("MasterCard", "Картка майстерні"),
            Map.entry("TachographAttribute", "Довідник тахографів"),
            Map.entry("VehicleMake", "Марка авто"),
            Map.entry("VehicleModel", "Модель авто")
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
        recordAs(currentUsername(), action, entityType, entityId, changes);
    }

    /**
     * For actions without a logged-in user in the security context — invite acceptance, password
     * reset, login/logout events — where the person is known some other way (their email).
     */
    public void recordAs(String username, AuditAction action, String entityType, Long entityId, Map<String, String[]> changes) {
        String changesJson = (changes == null || changes.isEmpty()) ? null : writeChanges(changes);
        String ipAddress = currentIpAddress();

        AuditLog log = AuditLog.builder()
                .username(username == null || username.isBlank() ? "system" : username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .occurredAt(Instant.now())
                .changes(changesJson)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(log);

        FILE_LOGGER.info("user={} ip={} action={} entityType={} entityId={} changes={}",
                log.getUsername(), ipAddress, action, entityType, entityId, changesJson);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> findRecent(Pageable pageable) {
        return auditLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    /** Filters of the "Журнал дій" page; every field is optional. Dates are inclusive, in the server's zone. */
    public record Filter(String username, AuditAction action, String entityType, Long entityId,
                         LocalDate from, LocalDate to, String query) {
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(Filter filter, Pageable pageable) {
        Specification<AuditLog> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.username() != null && !filter.username().isBlank()) {
                predicates.add(cb.equal(root.get("username"), filter.username()));
            }
            if (filter.action() != null) {
                predicates.add(cb.equal(root.get("action"), filter.action()));
            }
            if (filter.entityType() != null && !filter.entityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), filter.entityType()));
            }
            if (filter.entityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), filter.entityId()));
            }
            ZoneId zone = ZoneId.systemDefault();
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from().atStartOfDay(zone).toInstant()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), filter.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String like = "%" + filter.query().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("changes")), like),
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like)));
            }
            if (cq.getResultType() != Long.class && cq.getResultType() != long.class) {
                cq.orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return auditLogRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<String> findUsernames() {
        return auditLogRepository.findDistinctUsernames();
    }

    /** Entity types present in the log, as {raw type → Ukrainian label}, sorted by label. */
    @Transactional(readOnly = true)
    public List<Map.Entry<String, String>> findEntityTypes() {
        return auditLogRepository.findDistinctEntityTypes().stream()
                .map(type -> Map.entry(type, translateEntityType(type)))
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * The field-level diff from the most recent update of this entity that actually changed
     * something, or an empty map if there isn't one (never edited, or every past edit was a
     * no-op diff). Used to render small "changed" badges next to fields in the [[Автомобілі]]
     * and [[Тахографи]] tables.
     */
    @Transactional(readOnly = true)
    public Map<String, String[]> findLatestChanges(String entityType, Long entityId) {
        return auditLogRepository.findFirstByEntityTypeAndEntityIdAndActionAndChangesIsNotNullOrderByOccurredAtDesc(
                        entityType, entityId, AuditAction.UPDATE)
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
            case LOGIN -> "Вхід";
            case LOGIN_FAILED -> "Невдалий вхід";
            case LOGOUT -> "Вихід";
        };
    }

    /**
     * Behind Caddy the socket address is the proxy's — the real client is the first hop of
     * X-Forwarded-For, which Caddy sets itself.
     */
    private String currentIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
