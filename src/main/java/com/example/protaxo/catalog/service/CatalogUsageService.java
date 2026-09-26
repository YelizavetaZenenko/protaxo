package com.example.protaxo.catalog.service;

import com.example.protaxo.catalog.dto.CatalogUsageLine;
import com.example.protaxo.catalog.dto.CatalogUsageRow;
import com.example.protaxo.catalog.entity.CatalogItemType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Звіт «Витрата товарів» (docs/Витрата товарів.md): скільки якої позиції пішло в наряд-закази за
 * період. Нативні запити, а не JPQL: і наряд, і позицію каталогу могли м'яко видалити, а
 * {@code @SQLRestriction} на сутностях мовчки викинув би такі рядки з JOIN-у. Видалені наряди
 * не рахуються (продаж скасовано, склад повернуто), видалені позиції каталогу — рахуються.
 */
@Service
@Transactional(readOnly = true)
public class CatalogUsageService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Період — дати включно: [from 00:00, to+1 00:00). */
    public List<CatalogUsageRow> usage(LocalDate from, LocalDate to, CatalogItemType type) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT ii.catalog_item_id,
                               COALESCE(MAX(ci.name), MAX(ii.item_name)),
                               MAX(ci.type),
                               SUM(ii.quantity),
                               SUM(ii.amount),
                               COUNT(DISTINCT ii.invoice_id),
                               MAX(ci.stock_quantity),
                               BOOL_OR(ci.deleted_at IS NOT NULL)
                        FROM invoice_items ii
                        JOIN invoices i ON i.id = ii.invoice_id
                        LEFT JOIN catalog_items ci ON ci.id = ii.catalog_item_id
                        WHERE i.deleted_at IS NULL
                          AND i.document_date >= :from AND i.document_date < :to
                          AND ii.catalog_item_id IS NOT NULL
                        GROUP BY ii.catalog_item_id
                        """)
                .setParameter("from", from.atStartOfDay())
                .setParameter("to", to.plusDays(1).atStartOfDay())
                .getResultList();

        Map<Long, BigDecimal> adjustments = revisionAdjustments(from, to);
        Map<Long, CatalogUsageRow> result = new HashMap<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            Long id = ((Number) r[0]).longValue();
            CatalogItemType rowType = r[2] == null ? null : CatalogItemType.valueOf((String) r[2]);
            result.put(id, new CatalogUsageRow(id, (String) r[1], rowType, (BigDecimal) r[3], (BigDecimal) r[4],
                    ((Number) r[5]).longValue(), adjustments.get(id), (BigDecimal) r[6], Boolean.TRUE.equals(r[7])));
        }
        // Позиції, які за період не продавались, але їх коригували ревізією — теж показати.
        if (!adjustments.isEmpty()) {
            List<?> adjustedOnly = entityManager.createNativeQuery(
                            "SELECT id, name, type, stock_quantity, deleted_at IS NOT NULL FROM catalog_items WHERE id IN (:ids)")
                    .setParameter("ids", adjustments.keySet())
                    .getResultList();
            for (Object row : adjustedOnly) {
                Object[] r = (Object[]) row;
                Long id = ((Number) r[0]).longValue();
                result.putIfAbsent(id, new CatalogUsageRow(id, (String) r[1], CatalogItemType.valueOf((String) r[2]),
                        BigDecimal.ZERO, BigDecimal.ZERO, 0, adjustments.get(id), (BigDecimal) r[3], Boolean.TRUE.equals(r[4])));
            }
        }
        List<CatalogUsageRow> list = new ArrayList<>(result.values());
        list.removeIf(r -> type != null && r.type() != null && r.type() != type);
        list.sort(Comparator.comparing(CatalogUsageRow::usedAmount).reversed()
                .thenComparing(CatalogUsageRow::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Наряди, у які пішла позиція за період, від нових до старих. */
    public List<CatalogUsageLine> lines(Long catalogItemId, LocalDate from, LocalDate to) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT i.id, i.number, i.document_date, c.name, i.vehicle_name,
                               ii.quantity, ii.price, ii.amount
                        FROM invoice_items ii
                        JOIN invoices i ON i.id = ii.invoice_id
                        LEFT JOIN clients c ON c.id = i.client_id
                        WHERE i.deleted_at IS NULL
                          AND ii.catalog_item_id = :itemId
                          AND i.document_date >= :from AND i.document_date < :to
                        ORDER BY i.document_date DESC, i.id DESC
                        """)
                .setParameter("itemId", catalogItemId)
                .setParameter("from", from.atStartOfDay())
                .setParameter("to", to.plusDays(1).atStartOfDay())
                .getResultList();
        List<CatalogUsageLine> lines = new ArrayList<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            lines.add(new CatalogUsageLine(((Number) r[0]).longValue(), (String) r[1], toLocalDateTime(r[2]),
                    (String) r[3], (String) r[4], (BigDecimal) r[5], (BigDecimal) r[6], (BigDecimal) r[7]));
        }
        return lines;
    }

    /** Назва позиції (включно з видаленими) — для заголовка деталізації. */
    public String itemName(Long catalogItemId) {
        List<?> rows = entityManager.createNativeQuery("SELECT name FROM catalog_items WHERE id = :id")
                .setParameter("id", catalogItemId)
                .getResultList();
        return rows.isEmpty() ? "#" + catalogItemId : (String) rows.get(0);
    }

    /** Сума різниць проведених ревізій за період по позиціях (див. [[Ревізія складу]]). */
    private Map<Long, BigDecimal> revisionAdjustments(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT sri.catalog_item_id, SUM(sri.difference)
                        FROM stock_revision_items sri
                        JOIN stock_revisions sr ON sr.id = sri.revision_id
                        WHERE sr.status = 'COMPLETED' AND sr.deleted_at IS NULL
                          AND sr.completed_at >= :from AND sr.completed_at < :to
                          AND sri.difference IS NOT NULL AND sri.difference <> 0
                        GROUP BY sri.catalog_item_id
                        """)
                .setParameter("from", start)
                .setParameter("to", end)
                .getResultList();
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            result.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        return result;
    }

    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt;
        }
        return ((Timestamp) value).toLocalDateTime();
    }
}
