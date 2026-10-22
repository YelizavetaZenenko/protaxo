package com.example.protaxo.calibration.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.calibration.dto.CalibrationLabelType;
import com.example.protaxo.calibration.dto.TachographAttributeResponse;
import com.example.protaxo.calibration.entity.TachographAttribute;
import com.example.protaxo.calibration.entity.TachographAttributeCategory;
import com.example.protaxo.calibration.repository.TachographAttributeRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.util.FieldDiff;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TachographAttributeService {

    private final TachographAttributeRepository tachographAttributeRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TachographAttributeResponse> findByCategory(TachographAttributeCategory category) {
        List<TachographAttribute> attributes = tachographAttributeRepository.findByCategoryOrderByNameAsc(category);
        if (category == TachographAttributeCategory.TIRE_SIZE) {
            attributes = attributes.stream().sorted(TIRE_SIZE_ORDER).toList();
        } else if (category == TachographAttributeCategory.INSPECTION_REASON) {
            attributes = attributes.stream().sorted(INSPECTION_REASON_ORDER).toList();
        }
        return attributes.stream()
                .map(a -> new TachographAttributeResponse(a.getId(), a.getName(), a.getLabelType()))
                .toList();
    }

    // "215/65 R15C", "315/70 R22,5", "12.00 R20" → ширина і діаметр диска
    private static final Pattern TIRE_SIZE = Pattern.compile("^(\\d+(?:[.,]\\d+)?).*?R\\s*(\\d+(?:[.,]\\d+)?)");

    /** Розміри шин — від найменшого диска (R15C) до найбільшого, в межах диска — за шириною. */
    private static final Comparator<TachographAttribute> TIRE_SIZE_ORDER = Comparator
            .comparingDouble((TachographAttribute a) -> tireSizePart(a.getName(), 2))
            .thenComparingDouble(a -> tireSizePart(a.getName(), 1))
            .thenComparing(TachographAttribute::getName);

    private static double tireSizePart(String name, int group) {
        Matcher m = TIRE_SIZE.matcher(name.trim());
        // Нестандартний запис — у кінець списку
        return m.find() ? Double.parseDouble(m.group(group).replace(',', '.')) : Double.MAX_VALUE;
    }

    private static final List<String> INSPECTION_REASONS = List.of(
            "Встановлення", "Заміна несправного тахографа", "Заміна пневматичних шин", "Зміна реєстрації ТЗ",
            "Первинна реєстрація ТЗ", "Періодичність", "Ремонт");
    private static final String OTHER_INSPECTION_REASON = "Інші підстави";

    /** Підстави — в погодженому порядку, додані вручну — після них за алфавітом, "Інші підстави" — завжди останні. */
    private static final Comparator<TachographAttribute> INSPECTION_REASON_ORDER = Comparator
            .comparingInt((TachographAttribute a) -> inspectionReasonRank(a.getName()))
            .thenComparing(TachographAttribute::getName);

    private static int inspectionReasonRank(String name) {
        if (OTHER_INSPECTION_REASON.equalsIgnoreCase(name.trim())) {
            return Integer.MAX_VALUE;
        }
        int index = INSPECTION_REASONS.indexOf(name.trim());
        return index >= 0 ? index : INSPECTION_REASONS.size();
    }

    public TachographAttributeResponse create(TachographAttributeCategory category, String name, CalibrationLabelType labelType) {
        String trimmed = name.trim();
        if (tachographAttributeRepository.existsByCategoryAndNameIgnoreCase(category, trimmed)) {
            throw new BusinessRuleException("Це значення вже є у довіднику");
        }
        TachographAttribute saved = tachographAttributeRepository.save(TachographAttribute.builder()
                .category(category)
                .name(trimmed)
                // Тип наклейки має сенс лише для моделі тахографа
                .labelType(category == TachographAttributeCategory.MODEL ? labelType : null)
                .build());
        auditLogService.record(AuditAction.CREATE, "TachographAttribute", saved.getId(), FieldDiff.created(FieldDiff.snapshot()
                .add("Довідник", switch (category) {
                    case BRAND -> "Марка тахографа";
                    case MODEL -> "Модель тахографа";
                    case TYPE -> "Тип тахографа";
                    case MANUFACTURER -> "Виробник тахографа";
                    case INSPECTION_REASON -> "Підстава для перевірки";
                    case TIRE_SIZE -> "Розмір пневматичних шин";
                })
                .add("Значення", saved.getName())
                .add("Тип наклейки", saved.getLabelType() == null ? null : saved.getLabelType().name())));
        return new TachographAttributeResponse(saved.getId(), saved.getName(), saved.getLabelType());
    }
}
