package com.example.protaxo.stockrevision.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.catalog.entity.CatalogItem;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.repository.CatalogItemRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.common.util.FieldDiff;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.CurrentUserRoles;
import com.example.protaxo.stockrevision.dto.StockRevisionForm;
import com.example.protaxo.stockrevision.dto.StockRevisionView;
import com.example.protaxo.stockrevision.entity.StockRevision;
import com.example.protaxo.stockrevision.entity.StockRevisionItem;
import com.example.protaxo.stockrevision.entity.StockRevisionStatus;
import com.example.protaxo.stockrevision.repository.StockRevisionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ревізія складу (docs/Ревізія складу.md).
 *
 * <p>Чернетка знімає всі товари з обліковим залишком. Під час проведення облікова кількість
 * перезнімається на цей момент (між створенням чернетки й проведенням могли бути продажі),
 * різниця = фактично − за обліком, і залишок у каталозі стає фактичним. Рядки без введеної
 * фактичної кількості не чіпаються. Одночасно може бути лише одна чернетка.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockRevisionService {

    private final StockRevisionRepository revisionRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<StockRevisionView> findAll() {
        Map<String, String> names = userNames();
        return revisionRepository.findAllByOrderByCreatedAtDesc().stream().map(r -> toView(r, names)).toList();
    }

    @Transactional(readOnly = true)
    public StockRevisionView findById(Long id) {
        return toView(getOrThrow(id), userNames());
    }

    @Transactional(readOnly = true)
    public Optional<Long> findDraftId() {
        return revisionRepository.findFirstByStatus(StockRevisionStatus.DRAFT).map(StockRevision::getId);
    }

    public Long createDraft() {
        Optional<Long> existing = findDraftId();
        if (existing.isPresent()) {
            return existing.get();
        }
        StockRevision revision = StockRevision.builder()
                .status(StockRevisionStatus.DRAFT)
                .createdBy(CurrentUserRoles.username())
                .build();
        for (CatalogItem item : catalogItemRepository.findAllByOrderByIdAsc()) {
            if (item.getType() != CatalogItemType.MATERIAL || item.getStockQuantity() == null) {
                continue;
            }
            revision.getItems().add(StockRevisionItem.builder()
                    .revision(revision)
                    .catalogItemId(item.getId())
                    .itemName(item.getName())
                    .expectedQuantity(item.getStockQuantity())
                    .build());
        }
        if (revision.getItems().isEmpty()) {
            throw new BusinessRuleException("У каталозі немає товарів із залишком на складі — нічого перераховувати");
        }
        StockRevision saved = revisionRepository.save(revision);
        auditLogService.record(AuditAction.CREATE, "StockRevision", saved.getId());
        return saved.getId();
    }

    /** Зберігає введені фактичні кількості й коментарі; залишки в каталозі не змінює. */
    public void saveDraft(Long id, StockRevisionForm form) {
        StockRevision revision = requireDraft(id);
        applyForm(revision, form);
        revisionRepository.save(revision);
    }

    /**
     * Проведення: для кожного перерахованого рядка облікова кількість знімається заново,
     * фіксуються різниця й ціна, залишок у каталозі стає фактичним. Кожна зміна залишку —
     * окремий запис у журналі дій з позначкою ревізії.
     */
    public void complete(Long id, StockRevisionForm form) {
        StockRevision revision = requireDraft(id);
        applyForm(revision, form);
        if (revision.getItems().stream().noneMatch(i -> i.getActualQuantity() != null)) {
            throw new BusinessRuleException("Не введено жодної фактичної кількості — нічого проводити");
        }
        String auditLabel = "Залишок (ревізія № %d)".formatted(id);
        for (StockRevisionItem line : revision.getItems()) {
            if (line.getActualQuantity() == null) {
                continue;
            }
            Optional<CatalogItem> found = catalogItemRepository.findById(line.getCatalogItemId());
            if (found.isEmpty()) {
                line.setComment(join(line.getComment(), "позицію видалено з каталогу — залишок не змінено"));
                continue;
            }
            CatalogItem item = found.get();
            BigDecimal expected = item.getStockQuantity() == null ? BigDecimal.ZERO : item.getStockQuantity();
            line.setExpectedQuantity(expected);
            line.setDifference(line.getActualQuantity().subtract(expected));
            line.setUnitPrice(item.getBasePrice());
            if (line.getDifference().signum() != 0) {
                Map<String, String[]> changes = FieldDiff.builder()
                        .add(auditLabel, display(expected), display(line.getActualQuantity()))
                        .build();
                item.setStockQuantity(line.getActualQuantity());
                catalogItemRepository.save(item);
                auditLogService.record(AuditAction.UPDATE, "CatalogItem", item.getId(), changes);
            }
        }
        revision.setStatus(StockRevisionStatus.COMPLETED);
        revision.setCompletedAt(Instant.now());
        revision.setCompletedBy(CurrentUserRoles.username());
        revisionRepository.save(revision);
        auditLogService.record(AuditAction.UPDATE, "StockRevision", id, Map.of("Статус",
                new String[]{StockRevisionStatus.DRAFT.getLabel(), StockRevisionStatus.COMPLETED.getLabel()}));
    }

    public void deleteDraft(Long id) {
        StockRevision revision = requireDraft(id);
        revision.setDeletedAt(Instant.now());
        revisionRepository.save(revision);
        auditLogService.record(AuditAction.DELETE, "StockRevision", id);
    }

    private void applyForm(StockRevision revision, StockRevisionForm form) {
        revision.setComment(trimToNull(form.getComment()));
        Map<Long, StockRevisionForm.Line> byId = form.getLines().stream()
                .filter(l -> l.getId() != null)
                .collect(Collectors.toMap(StockRevisionForm.Line::getId, Function.identity(), (a, b) -> b));
        for (StockRevisionItem item : revision.getItems()) {
            StockRevisionForm.Line line = byId.get(item.getId());
            if (line == null) {
                continue;
            }
            BigDecimal actual = line.getActualQuantity();
            if (actual != null) {
                if (actual.signum() < 0) {
                    throw new BusinessRuleException("«%s»: фактична кількість не може бути від'ємною".formatted(item.getItemName()));
                }
                if (actual.stripTrailingZeros().scale() > 3) {
                    throw new BusinessRuleException("«%s»: не більше трьох знаків після коми".formatted(item.getItemName()));
                }
            }
            item.setActualQuantity(actual);
            item.setComment(trimToNull(line.getComment()));
        }
    }

    private StockRevision requireDraft(Long id) {
        StockRevision revision = getOrThrow(id);
        if (revision.getStatus() != StockRevisionStatus.DRAFT) {
            throw new BusinessRuleException("Ревізію вже проведено — змінювати її не можна");
        }
        return revision;
    }

    private StockRevision getOrThrow(Long id) {
        return revisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("StockRevision %d not found".formatted(id)));
    }

    private StockRevisionView toView(StockRevision r, Map<String, String> names) {
        List<StockRevisionView.Line> lines = r.getItems().stream()
                .map(i -> new StockRevisionView.Line(i.getId(), i.getCatalogItemId(), i.getItemName(),
                        i.getExpectedQuantity(), i.getActualQuantity(), i.getDifference(), i.getUnitPrice(), i.getComment()))
                .toList();
        return new StockRevisionView(r.getId(), r.getStatus(), r.getComment(), r.getCreatedAt(),
                names.getOrDefault(r.getCreatedBy(), r.getCreatedBy()), r.getCompletedAt(),
                r.getCompletedBy() == null ? null : names.getOrDefault(r.getCompletedBy(), r.getCompletedBy()),
                lines);
    }

    private Map<String, String> userNames() {
        return userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getEmail, User::getFullName, (a, b) -> a));
    }

    private String display(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String join(String existing, String note) {
        return existing == null ? note : existing + "; " + note;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
