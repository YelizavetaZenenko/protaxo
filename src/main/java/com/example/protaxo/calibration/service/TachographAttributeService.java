package com.example.protaxo.calibration.service;

import com.example.protaxo.calibration.dto.TachographAttributeResponse;
import com.example.protaxo.calibration.entity.TachographAttribute;
import com.example.protaxo.calibration.entity.TachographAttributeCategory;
import com.example.protaxo.calibration.repository.TachographAttributeRepository;
import com.example.protaxo.common.exception.BusinessRuleException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TachographAttributeService {

    private final TachographAttributeRepository tachographAttributeRepository;

    @Transactional(readOnly = true)
    public List<TachographAttributeResponse> findByCategory(TachographAttributeCategory category) {
        return tachographAttributeRepository.findByCategoryOrderByNameAsc(category).stream()
                .map(a -> new TachographAttributeResponse(a.getId(), a.getName()))
                .toList();
    }

    public TachographAttributeResponse create(TachographAttributeCategory category, String name) {
        String trimmed = name.trim();
        if (tachographAttributeRepository.existsByCategoryAndNameIgnoreCase(category, trimmed)) {
            throw new BusinessRuleException("Це значення вже є у довіднику");
        }
        TachographAttribute saved = tachographAttributeRepository.save(TachographAttribute.builder()
                .category(category)
                .name(trimmed)
                .build());
        return new TachographAttributeResponse(saved.getId(), saved.getName());
    }
}
