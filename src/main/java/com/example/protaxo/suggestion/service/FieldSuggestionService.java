package com.example.protaxo.suggestion.service;

import com.example.protaxo.suggestion.entity.FieldSuggestion;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import com.example.protaxo.suggestion.repository.FieldSuggestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldSuggestionService {

    private final FieldSuggestionRepository fieldSuggestionRepository;

    @Transactional(readOnly = true)
    public List<String> findValues(FieldSuggestionCategory category) {
        return fieldSuggestionRepository.findByCategoryOrderByValueAsc(category).stream()
                .map(FieldSuggestion::getValue)
                .toList();
    }

    public void remember(FieldSuggestionCategory category, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (!fieldSuggestionRepository.existsByCategoryAndValue(category, trimmed)) {
            fieldSuggestionRepository.save(FieldSuggestion.builder()
                    .category(category)
                    .value(trimmed)
                    .build());
        }
    }
}
